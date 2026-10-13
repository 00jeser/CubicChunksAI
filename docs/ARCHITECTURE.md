# Архитектура Cubic Chunks

Контракт мода на NeoForge 26.1. Продукт и приёмка — [TZ.md](TZ.md). Как JAR ядра попадает в мод через javaheaders — [architecture.md](architecture.md). Домен ядра — [cubicchunks-core.md](cubicchunks-core.md).

Расхождение эпика с этим файлом фиксируется в эпике (кто главнее) и закрывается спринтом, который правит этот файл.

## 1. Слои

```text
NeoForge 26.1.2 / Minecraft
        ▲ миксины и DASM (только src/main, префикс cc_)
Мод io.github.opencubicchunks.cubicchunks
        ▲ CubePos, CloPos, Coords, карты высот, WorldStyle
CubicChunksCore (JAR, javaheaders переписывает MC*)
```

Мод знает версию игры. Ядро — нет: в `CubicChunksCore/src/main` нет импортов `net.minecraft`.

| Слой | Владеет | Не владеет |
| --- | --- | --- |
| Ядро | `CubePos`, `CloPos`, `Coords`, `CubicConstants`, `EarlyConfig`, дерево `SurfaceTracker*`, `ColumnCubeMap`, `SpawnPlaceFinder`, `CubicLevelHeightAccessor.WorldStyle` | тиком, пакетами, миксинами |
| Данные куба | `CubeAccess`, `ProtoCube`, `LevelCube`, `EmptyLevelCube`, `ImposterProtoCube`, `CloAccess` | решением, грузить ли куб |
| Статусы | `CubePyramid`, `CubeStep`, `CubeStatusTasks`, `CCChunkStatusTasks` | сетью и мешем |
| Сервер | `ServerCubeCache`, `CubicChunkMap`, `CubeHolder`, `CloHolder`, `DistanceManager` через миксины, `CubicTicketStorage` | записью в регион, пока E11 не сменит `cc_save` |
| Клиент | `ClientCubeCache`, `CubicClientLevel`, `CubicLevelRenderer`, `ViewArea` | генерацией |
| Сеть | `CCNetworkHandler` и четыре `CCClientbound*` | тиком симуляции |
| Конфиг | `EarlyConfig`, `CommonConfig`, `ServerConfig` | флагом `cc_isCubic`, пока E5 не запишет его из стиля |
| NeoForge-кромка | `movetoforgesourcesetlater` (временно), source set `forge` | игровой логикой куба |

## 2. Состояние

Дерево — цель после E20. Поля, которых нет сегодня, помечены эпиком. Эпик, который вводит поле раньше, пишет в своём разделе «Состояние», чего ещё нет.

```text
Процесс
├── EarlyConfig.diameterInSections ∈ {1,2,4,8}     файл config/cubicchunks/earlyconfig.properties
├── CubicConstants.*                               производные диаметра, читаются как константы
└── CubicChunks.config → CommonConfig
    ├── verticalViewDistance = 8
    └── generateNewWorldsAsCC = true               кнопка создания мира

Мир (ServerLevel / ClientLevel, MixinLevel)
├── cc_isCubic: boolean                            сегодня не выставляется в ctor
├── worldStyle: CUBIC | HYBRID | CHUNK            E5, из ServerConfig + данных уровня
├── heightAccessor                                 ванильные getMinY/getMaxY; алиасы *BuildHeight живут до E2
└── cubeSource: ServerCubeCache | ClientCubeCache

CloPos
├── chunk(x, z)     Y-сентинел Integer.MAX_VALUE
└── cube(x, y, z)   упаковка 21 бит на ось, старшие 2 бита отличают куб от чанка

CubeAccess
├── cubePos: CubePos
├── sections: LevelChunkSection[SECTION_COUNT]
├── heightmaps                                     E9; сегодня getHeightmaps кидает, getHeight → SUPERFLAT_HEIGHT
├── blockEntities / pending                        каркас есть, тики пустые до E16
└── ticks                                          E16; registerTickContainerInLevel пустой

Сервер
├── CubicChunkMap / ChunkHolder как CloHolder и CubeHolder
├── тикеты CubicTicketStorage                      кодек сейва — E11
├── CubePyramid.CC_GENERATION_PYRAMID_CUBES
│     structure starts → load starts → references → biomes → noise
│     → surface → carvers → features → initLight → light → spawn → full
└── CC_LOADING_PYRAMID_CUBES                       чтение региона — E11

Клиент
├── ClientCubeCache.Storage                        AtomicReferenceArray<LevelCube> по кубическому радиусу
├── центр просмотра (x, y, z)                      CCClientboundSetCubeCacheCenterPacket
└── меш секции                                     ViewArea / RenderSection, E8
```

Нет в этом дереве и не появляется без нового эпика: отладочный оверлей, OptiFine-ветка, формат сейва 1.12, публичный API чужого `ChunkGenerator`.

`ChunkPos` на 26.1 — record. Упаковка чанка в `long` на стороне ядра идёт через заголовок `MCChunkPos`. Имена `toLong` / `asLong` / конструктор из `long` в заголовке не считаются вечными: E2 либо учит javaheaders переписывать их в `pack` / `unpack`, либо меняет заголовок и все вызовы в ядре одним спринтом. Два способа сразу не живут.

## 3. Поток загрузки куба

1. Игрок получает тикет. `MixinDistanceManager` / `MixinPlayerTicketTracker` ведут `CloPos`, не только `ChunkPos`, если `cc_isCubic`.
2. `MixinChunkMap` поднимает `ChunkHolder`. Для куба держатель — `CubeHolder`.
3. `MixinChunkGenerationTask` идёт по `CubePyramid`. Шаг зовёт метод `CubeStatusTasks`.
4. Несделанный шаг возвращает `passThrough` и не пишет биомы, свет и карты высот. `generateNoise` сегодня пишет синусоиду (`SUPERFLAT_HEIGHT`). `full` поднимает `ProtoCube` в `LevelCube`.
5. `MixinPlayerChunkSender` шлёт `CCClientboundLevelCubeWithLightPacket`. Имя пакета шире содержимого, пока нет света (E10) и карт высот (E9).
6. `CCNetworkHandler` принимает пакет на клиентском потоке игры. `ClientCubeCache.cc_replaceWithPacketData` кладёт `LevelCube` в `Storage`.
7. `MixinViewArea` перестраивает секции вокруг центра. Меш читает уже принятые секции и не ходит в регион и в конфиг.

Колонка в кубическом измерении (`CCClientboundLevelChunkPacket`) несёт то, что ещё нужно 2D-коду (карты высот колонки). Это не второй куб.

## 4. Поток тика

1. Ванильный тик уровня доходит до `MixinServerLevel.cc_tickClo`.
2. Если `LevelClo` — `LevelCube`, вызывается `cc_tickCube`. Сегодня тело пустое.
3. Случайные тики, block entity и расписание жидкостей включаются в E16. До этого куб не делает вид, что тикает.
4. Сущности тикаются ванильным циклом, но позиция и переход между кубами идут через `EntityCubePosGetter` / `MixinEntity` (E15). Телепорт, который считает мир колонкой фиксированной высоты, в кубическом уровне не вызывается.

## 5. Поток сохранения

Сегодня `MixinChunkMap.cc_save` возвращает `false`. `MixinChunkStorage.cc_read` возвращает пустой `Optional`, `cc_write` ничего не пишет. Это сознательный обрыв, а не «сохранение пустого куба».

Целевой поток (E11):

1. `cc_save` сериализует `LevelCube` в NBT.
2. `cc_write` кладёт NBT в 3D-регион `regionlib` по `CubePos`.
3. Выгрузка шлёт `CCClientboundForgetLevelCloPacket`.
4. Стиль `CHUNK` пишет ванильный регион колонки и не открывает 3D-регион.
5. Диаметр в сейве не совпал с `EarlyConfig` — загрузка мира падает с явной ошибкой.

Пока E11 не закрыт, выход из мира не должен создавать полузаписанный регион кубов.

## 6. Поток рендера

1. Центр — куб игрока, радиус по вертикали — `CommonConfig.verticalViewDistance`.
2. `MixinSectionOcclusionGraph` обходит секции в трёх осях.
3. `MixinSectionRenderDispatcher$RenderSection` берёт блоки из секции куба.
4. Проверка «свет включён в колонке» не решает видимость куба. Пока E10 не готов, куб рисуется с тем светом, который уже лежит в секции; подмена яркости на `MAX_LEVEL` в `MixinLevelLightEngine` снимается в E10, а не остаётся «чтобы было видно».

Экран загрузки (`CubicLevelLoadingScreen`, `WorldLoadingCubeStatusesRenderer`) читает `CloProgressListener` и не грузит кубы сам.

## 7. Модули

| Тема | Путь |
| --- | --- |
| Вход мода | `src/main/java/io/github/opencubicchunks/cubicchunks/CubicChunks.java` |
| Флаги кубичности | `CanBeCubic.java`, `MarkableAsCubic.java` |
| Конфиг | `config/CommonConfig.java`, `config/ServerConfig.java`, `CubicChunksCore/.../config/EarlyConfig.java` |
| Куб | `world/level/cube/CubeAccess.java`, `LevelCube.java`, `ProtoCube.java` |
| Статусы | `world/level/cube/status/CubeStatusTasks.java`, `CubePyramid.java` |
| Чанк внутри кубического мира | `world/level/chunk/status/CCChunkStatusTasks.java` |
| Серверные миксины | `mixin/core/common/server/level/` |
| Клиентский кэш | `client/multiplayer/ClientCubeCache.java`, `mixin/core/client/multiplayer/` |
| Рендер | `mixin/core/client/renderer/` |
| Сеть | `network/CCNetworkHandler.java` |
| DASM-наборы | `mixin/dasmsets/` |
| Плагин миксинов | `mixin/ASMConfigPlugin.java` |
| Кромка NeoForge | `movetoforgesourcesetlater/` |
| Ядро | `CubicChunksCore/src/main/java/io/github/opencubicchunks/cc_core/` |
| Старый порт, не в сборке | `src_old/` |

## 8. DASM и миксины

Ванильный метод про чанк становится методом про куб перенаправлением DASM (`@TransformFromMethod`, наборы `*redirects`), а не копией тела. Ручная копия разрешена, только если рядом комментарий, почему DASM не берёт метод, и эпик, который это снимет.

Имена членов миксина начинаются с `cc_`. Исключение — тестовые миксины (`src/test/.../mixin/test`).

Наборы:

| Набор | Смысл |
| --- | --- |
| `GlobalSet` | перенаправления, верные в любом контексте; расширяет `ForgeSet` |
| `ChunkToCubeSet` | чанк → куб |
| `ChunkToCloSet` | чанк → `CloPos` / `CloAccess` |
| `ChunkInCubicContextSet` | ванильный чанк, но мир кубический (`CCChunkStatusTasks`) |
| `SectionPosToCubeSet`, `SectionPosToChunkSet` | секция → куб или колонка |
| `ForgeSet` | типы NeoForge; живёт отдельно, когда условные наборы появятся |

`ForgeSet` сегодня расширяется `GlobalSet` всегда. Перенос в source set `forge` — E18, не тихий рефакторинг внутри другого эпика.

## 9. Граница ядра

Родитель зависит от JAR `:CubicChunksCore`, не от его `classes`. Плагин `javaheaders` (`javaHeaders.json`, маска `.*CubicChunksCore.*`) переписывает `MC*` в классы Minecraft. Тесты ядра запускает родитель после трансформа. Задача `unzipTests` в `build.gradle` включится в E2; до этого тесты ядра не считаются зелёными только потому, что подпроект помечен `enabled(false)`.

Компилировать исходники ядра прямо на classpath мода нельзя: заглушки столкнутся с настоящими классами, и трансформу не на чем работать.

## 10. Инварианты

1. Диаметр куба в секциях — степень двойки из `{1, 2, 4, 8}` и не меняется после создания мира.
2. У `CloPos` колонки координата Y равна `Integer.MAX_VALUE`. У куба такой Y запрещён.
3. Старшие два бита упакованного `long` отличают куб от чанка. `Long.MAX_VALUE` — невалидная позиция и у `CubePos`, и у `CloPos`, и у `MCChunkPos`.
4. Уровень со стилем `CHUNK` не входит в кубовый пайплайн: `cc_isCubic == false`. `HYBRID` и `CUBIC` входят (`cc_isCubic == true`); `HYBRID` дополнительно генерирует 2D-чанки.
5. `passThrough` не записывает биомы, свет, карты высот и структуры.
6. Пока E11 не закрыт, `cc_save` не пишет регион. Запись «пустого успеха» запрещена.
7. Рендер секции не читает диск и не парсит конфиг.
8. Ядро не ссылается на `net.minecraft`.
9. Члены миксинов мода имеют префикс `cc_`.
10. Упаковка `CloPos` — предел вертикали. `MAX_SUPPORTED_HEIGHT` его не расширяет.
11. Один процесс может держать кубическое и ванильное измерения. Кэш, регион и пакет выбираются по стилю измерения, не по глобальному флагу мода.
12. Ослабление инварианта делается фразой в эпике и спринтом, который обновляет этот раздел. Молча не отменяется.

## 11. Запреты

- Влить `CubicChunksCore/src/main` в source set мода.
- Считать `src_old` компилируемым кодом 26.1.
- Рисовать куб как один `Item`/`Image` GUI вместо секций `LevelCube`.
- Подменять свет константой `MAX_LEVEL` после закрытия E10.
- Вызывать I/O региона из `MixinViewArea` / `MixinLevelRenderer`.
- Делить один `ClientCubeCache.Storage` на все измерения.
- Вводить `trait` генератора «на все стили» одним интерфейсом, который прячет разницу `CUBIC` и `HYBRID`. Разница — `WorldStyle.generates2DChunks`.
- Тащить `Arc`/`Mutex` на кадр: код на Java, общее состояние куба принадлежит уровню и его кэшу, посторонний лок на рендер не добавляется.

# ТЗ — Cubic Chunks на NeoForge 26.1

Мод заменяет колонку чанка кубом и снимает ванильный потолок высоты. Этот документ — продукт: что считается готовым, чего не делаем, как принимаем. Слои и инварианты — [ARCHITECTURE.md](ARCHITECTURE.md). Нарезка — [epics/README.md](epics/README.md). Приёмка — §7, этапы — §9.

Граница Gradle-подпроекта `:CubicChunksCore` описана в [architecture.md](architecture.md) и [cubicchunks-core.md](cubicchunks-core.md). Они не заменяют этот ТЗ.

## 1. Продукт

Игрок создаёт мир с включёнными кубическими чанками, оказывается на сгенерированной поверхности около Y=64 и может уйти на миллионы блоков вверх и вниз. Мир сохраняется и поднимается после перезапуска. Измерение со стилем `CHUNK` остаётся ванильным. Клиент показывает секции кубов, свет и сущности. Сервер и интегрированный клиент говорят пакетами мода.

Вертикальный предел — упаковка `CloPos` / `CubePos` (21 бит на ось, `CubePos.MAX_COORDINATE_VALUE`), а не `CubicChunksBase.MAX_SUPPORTED_HEIGHT`. Второе число шире упаковки и само по себе мир не расширяет.

## 2. Целевая платформа

Цифры сняты 2026-10-13, не подобраны.

| Что | Версия | Откуда |
| --- | --- | --- |
| Minecraft | `26.1.2` | `gradle.properties` официального MDK [MDK-26.1.2-ModDevGradle](https://github.com/NeoForgeMDKs/MDK-26.1.2-ModDevGradle) |
| NeoForge | `26.1.2.116` | тот же MDK; последний артефакт линейки `26.1` в `https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml` (`lastUpdated` `20261011194634`). Общий `<latest>` репозитория — `26.3.0.76`, он не цель |
| ModDevGradle | `2.0.148` | `build.gradle` того же MDK; `<latest>` метаданных `net.neoforged.moddev` |
| Gradle | `9.2.1` | `gradle/wrapper/gradle-wrapper.properties` того же MDK |
| Java | 25 | комментарий MDK: «Mojang ships Java 25 to end users in 26.1.2»; [заметка NeoForge 26.1](https://neoforged.net/news/26.1release/) |
| Foojay | `1.0.0` | `settings.gradle` того же MDK |
| NeoGradle | `7.1.39` | метаданные `net.neoforged.gradle.userdev`. Не используем: репозиторий уже на `net.neoforged.moddev` |

Обфускации в 26.1 нет. Parchment из обязательной сборки убирается: MDK его не подключает, заметка NeoForge разрешает удалить блок. `ChunkPos` — record: `containing`, `pack`, `unpack` вместо конструктора из `long` и `asLong`. `GuiGraphics` в GUI переименован в `GuiGraphicsExtractor`. Шаблон `neoforge.mods.toml` MDK 26.1.2 не задаёт `modLoader` и `loaderVersion` (у обоих есть умолчания, [Mod Files](https://docs.neoforged.net/docs/gettingstarted/modfiles)).

`dasm` `3.2.0` и `javaheaders` `1.2.8` в официальном MDK нет. Их версии не выдумываются: E3 и E2 либо оставляют текущие координаты, либо записывают первый артефакт, который реально резолвится и компилируется против `26.1.2.116`.

## 3. Что есть сейчас

Репозиторий — NeoForge-порт на Minecraft `1.21.6` / NeoForge `21.6.4-beta` (`gradle.properties`). README: мод неиграбелен. `src/main` — 182 Java-файла. `src_old/` в сборку не входит (старый порт, в том числе `levelgen/` и `RegionCubeIO`). `:CubicChunksCore` — подпроект на коммите `75a1100`, Java 17, тесты подпроекта выключены.

Сборка мода: ModDevGradle `2.0.90`, Java 21, Gradle `8.14.1`, Parchment `1.21.5` / `2025.06.15`, Mixin `JAVA_21`, `dasm` `3.2.0` (`dasm` api + `dasm-neoforge`), `javaheaders` `1.2.8`. Source set `forge` объявлен, в `neoForge.mods` привязан `main` (`build.gradle`, комментарий `FIXME`). `unzipTests` и `longRunTest` закомментированы.

Уже есть каркас, не игра:

- Координаты и `CloPos` в ядре; `javaHeaders.json` подменяет `MC*` на классы 1.21.
- `LevelCube`, `ProtoCube`, `CubeAccess`, `CubePyramid`, `CubeStep`, `CubeStatusTasks`.
- DASM-наборы `GlobalSet`, `ChunkToCubeSet`, `ChunkToCloSet`, `ChunkInCubicContextSet`, `ForgeSet`, `SectionPosToCubeSet`, `SectionPosToChunkSet`.
- Миксины серверного кэша (`MixinChunkMap`, `MixinServerChunkCache`, `MixinDistanceManager`, держатели, тикеты, трекеры).
- Пакеты `CCClientboundLevelCubeWithLightPacket`, `CCClientboundLevelChunkPacket`, `CCClientboundForgetLevelCloPacket`, `CCClientboundSetCubeCacheCenterPacket` и `CCNetworkHandler`.
- Клиентский кэш `ClientCubeCache` и миксины рендера (`MixinViewArea`, `MixinLevelRenderer`, occlusion, `SectionRenderDispatcher`).
- Кнопка мира в `MixinCreateWorldScreen`, `CommonConfig`, `ServerConfig`.
- Синусоида вместо шума в `CubeStatusTasks.generateNoise` (`CubicChunks.SUPERFLAT_HEIGHT = 5`).

Сломано или выключено (файлы — якоря, не полный список TODO):

| Дыра | Где |
| --- | --- |
| Стиль мира не ставится в конструкторе уровня | `MixinServerLevel` — пустой `cc_onInit`, комментарий про dimension/config |
| Шаги генерации, кроме шума-заглушки и `full`, возвращают `passThrough` | `CubeStatusTasks`, `CCChunkStatusTasks` |
| Сохранение куба всегда `false` | `MixinChunkMap.cc_save` |
| Чтение/запись хранилища пустые | `MixinChunkStorage.cc_read`, `cc_write`, `cc_isOldChunkAround` |
| Свет — константа «колонка освещена» и `LightBlock.MAX_LEVEL` | `MixinLevelLightEngine` |
| Карты высот куба кидают `UnsupportedOperationException` либо возвращают `SUPERFLAT_HEIGHT` | `CubeAccess.getHeightmaps`, `getOrCreateHeightmapUnprimed`, `getHeight` |
| Тики куба пустые | `LevelCube.registerTickContainerInLevel`, `MixinServerLevel.cc_tickCube` |
| Пакет «со светом» света не несёт; нет карт высот и тегов block entity | `CCClientboundLevelCubeWithLightPacket`, `CCClientboundLevelCubePacketData` |
| `getMinBuildHeight` / `getMaxBuildHeight` — алиасы старых имён | `MixinLevelHeightAccessor` |
| События NeoForge лежат в `movetoforgesourcesetlater`, не в source set `forge` | `CCEventHooks`, `CCCommonHooks`, `EventConstructorDelegates` |
| `DebugVisualization` ищется рефлексией и класса нет | `CubicChunks` |
| `regionlib` и `noise` в `dependencies`, из `src/main` не вызываются | `build.gradle` |
| 12 тестовых файлов с `@Disabled`; часть серверных тестов — заглушки | `src/test` |

## 4. Цели

1. Сборка и клиент до главного меню на NeoForge `26.1.2.116`, Java 25.
2. Новый мир со стилем `CUBIC`: кубы вокруг игрока доходят до `LevelCube`, клиент рисует блоки синусоиды ещё до настоящего шума. Это этап 2, не финал.
3. После этапа 4 поверхность строится ванильным шумом и биомами, а не синусоидой.
4. После этапа 3 выход в меню и повторный вход возвращают те же блоки.
5. Игрок ходит, падает, телепортируется, видит мобов и работающий свет в радиусе вертикальной прорисовки.
6. Измерение `CHUNK` в том же процессе не идёт через кубовый пайплайн.
7. `./gradlew test` зелёный на тестах, которые эпик объявил включёнными. `@Disabled` без ссылки на эпик не остаётся.

## 5. Не-цели

- Линейки NeoForge `26.2` и `26.3`, переход на NeoGradle.
- Загрузчик Fabric и каталог `src_old` как исходник, который «просто включить».
- OptiFine и миксины `src_old/.../mixin/optifine`.
- Перенос сейвов Cubic Chunks 1.12, 1.17 и текущего недописанного 1.21.6. Миры 26.1 — новые.
- Возврат `:CubicChunksCore` в git-сабмодуль или слияние его исходников в source set мода.
- Отдельный отладочный оверлей `DebugVisualization`. Системные свойства `cubicchunks.debug.*` не обязаны что-то рисовать.
- Совместимость чужих генераторов чанков как публичный API. Заметка `ModOverworldChunkGeneratorsandCC.md` описывает 1.17 и контрактом 26.1 не является.
- Горизонтальная бесконечность сверх ванильной: её даёт сама игра.
- Смена `CUBE_DIAMETER_IN_SECTIONS` у уже созданного мира. Несовпадение диаметра и сейва — ошибка загрузки, не миграция.

## 6. Модель, которую нельзя склеить

| Ось | Значения | Не путать с |
| --- | --- | --- |
| `WorldStyle` | `CUBIC` (кубы, без 2D-генерации), `HYBRID` (кубы и ванильная 2D-генерация), `CHUNK` (ваниль) | флагом `cc_isCubic`: у `HYBRID` он тоже истинный |
| `CommonConfig.generateNewWorldsAsCC` | кнопка создания мира, пишет `config/cubicchunks_common.toml` | `ServerConfig`: стиль по измерению в `serverconfig/cubicchunks.toml` |
| `CloPos` | чанк `(x, z)` или куб `(x, y, z)` | `ChunkPos` и `CubePos` по отдельности |
| Колонка 16×16 | карта высот и `ColumnCubeMap` | куб: у куба много секций и своя позиция Y |
| Пакет колонки | `CCClientboundLevelChunkPacket` | пакет куба `CCClientboundLevelCubeWithLightPacket` |
| `EarlyConfig` | диаметр куба в секциях, процесс, до мира | `CommonConfig` / `ServerConfig` |

Диаметр секции — 16. Диаметр куба в секциях — 1, 2, 4 или 8, по умолчанию 2 (`EarlyConfig`, `CubicConstants`).

## 7. Приёмка

Ручной сценарий финала (эпик E20 гоняет его целиком; ранние эпики гоняют свой префикс):

1. `./gradlew compileJava test` на Java 25 заканчивается успешно. В логе теста нет необъяснённого `@Disabled`.
2. Клиент доходит до главного меню на NeoForge `26.1.2.116`.
3. Создание мира: переключатель кубических чанков включён. Игрок стоит на камне/поверхности, Y не зажат ванильным минимумом и максимумом измерения.
4. Под игроком и над ним, за пределами ванильной высоты, есть загруженные кубы (лог статуса или отладочный счётчик кэша, не визуальная догадка).
5. Выход в меню и повторный вход: блок, поставленный игроком на \|Y\| > ванильной высоты, на месте.
6. Ночь и пещера: свет не равен константе `LightBlock.MAX_LEVEL` на каждом блоке.
7. Моб спавнится, получает урон от падения, телепорт `/tp` на Y=10000 не выкидывает за мир.
8. Второе измерение, прописанное как `CHUNK` в `ServerConfig`, грузит ванильные колонны. Кубический оверворлд в том же сервере не ломается.
9. Ванильный мир без мода, открытый с модом при `generateNewWorldsAsCC=false` и стиле `CHUNK`, открывается как ванильный.

**Не требуется для закрытия ТЗ:** миграция старых сейвов, Fabric, OptiFine, чужие генераторы, отладочный оверлей, линейка 26.2+.

## 8. Закрытые решения

Одно решение на спор. Отвергнутое в эпик не возвращать.

1. Цель — `26.1.2` / NeoForge `26.1.2.116`, плагин `net.neoforged.moddev` `2.0.148`. NeoGradle не подключаем.
2. Parchment из `neoForge {}` удаляется в E1.
3. `neoforge.mods.toml` приводится к шаблону MDK 26.1.2: без `modLoader` и без `loaderVersion`. Блоки `[[mixins]]` и `[[dasm]]` остаются.
4. Диаметр куба неизменен для сейва. Менять его можно только до первого создания мира.
5. Формат куба на диске — 3D-регион через уже объявленный `io.github.opencubicchunks:regionlib`. Ванильный `RegionFileStorage` остаётся для стиля `CHUNK`. Ориентир поведения — `src_old/.../world/storage/RegionCubeIO.java`, пакет там старый (`cubicchunks.regionlib`) и вслепую не копируется.
6. `HYBRID` входит в продукт (E12), это не синоним `CUBIC`.
7. `src_old` — справочник поведения, не каталог к переносу пакетом.

## 9. Этапы

Жёсткий порядок — этапы 1 и 2 (эпики E1–E8). Дальше параллелить можно только так, как написано в шапке эпика.

| Этап | Эпики | Наблюдаемый результат |
| --- | --- | --- |
| 1. Порт собирается | E1–E4 | Тулчейн 26.1.2, ядро и DASM/Mixin компилируются, `compileJava` мода и невыключенные тесты зелёные, клиент до меню |
| 2. Кубический мир виден | E5–E8 | Стиль включается, синусоида доезжает до клиента и рисуется. Сохранение всё ещё выключено |
| 3. Мир переживает перезапуск | E9–E11 | Карты высот, свет, регионы кубов. `cc_save` больше не константа `false` |
| 4. Ванильная генерация | E12–E14 | Шум, биомы, поверхность, карверы, фичи, структуры. Синусоида удалена |
| 5. Играбельность | E15–E17 | Сущности, столкновения, тики, block entity, POI, спавн |
| 6. Стык с NeoForge и приёмка | E18–E20 | События, экран создания мира на API 26.1, сценарий §7 |

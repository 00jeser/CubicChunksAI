# CubicChunksCore

Версионно-независимая библиотека Cubic Chunks. Импортирована из [OpenCubicChunks/CubicChunksCore](https://github.com/OpenCubicChunks/CubicChunksCore), коммит `75a1100c8a6d47f02bde8515d4a7de207f04be36` (15 июня 2025, «Use CubicChunksCore's checkstyle config, not CubicChunks' config»). Лицензия: `CubicChunksCore/LICENSE` (MIT).

Пакет: `io.github.opencubicchunks.cc_core`.

## Домен

Мир Cubic Chunks состоит из кубов. Куб — это куб из секций Minecraft. Секция всегда 16×16×16 блоков. Сколько секций вдоль ребра куба, задаёт ранний конфиг; допустимы только степени двойки 1, 2, 4 и 8. По умолчанию ребро — 2 секции, то есть 32 блока.

По вертикали мир не ограничен высотой ванильного `LevelHeightAccessor`. Карта высот и поиск спавна умеют работать на диапазоне, который покрывает почти весь `int`.

Один и тот же код должен жить и в моде под конкретную версию Minecraft, и в тестах без игры. Поэтому типы Minecraft (`BlockPos`, `ChunkPos`, `Entity`, `BlockState`, хранилище битов) представлены заглушками `MC*`. Плагин javaheaders в моде подменяет их настоящими классами по таблице `javaHeaders.json`.

## Модель данных

### Размер куба — `CubicConstants` и `EarlyConfig`

`EarlyConfig.getDiameterInSections()` читает `config/cubicchunks/earlyconfig.properties`, ключ `CUBE_DIAMETER_IN_SECTIONS`. Если файла нет, возвращается 2 и файл не создаётся: настройка не должна включаться случайно. Значение вне `{1, 2, 4, 8}` — ошибка.

От диаметра в секциях считаются производные константы:

| Константа | Смысл |
| --- | --- |
| `SECTION_DIAMETER` | 16, сторона секции в блоках |
| `DIAMETER_IN_SECTIONS` | сторона куба в секциях |
| `SECTION_COUNT` | число секций в кубе, диаметр³ |
| `CHUNK_COUNT` | число колонок в кубе, диаметр² |
| `DIAMETER_IN_BLOCKS` | сторона куба в блоках |
| `BLOCK_COUNT` | число блоков в кубе |
| `SIZE_BITS` | log2 стороны в блоках |

`CubicChunksBase` держит общие пределы и идентификатор: `MAX_SUPPORTED_HEIGHT` / `MIN_SUPPORTED_HEIGHT` (±`Integer.MAX_VALUE / 2`), `SEA_LEVEL = 64`, `MODID = "cubicchunks"`, `PROTOCOL_VERSION = "0"`, флаг `OPTIMIZED_CUBELOAD` (выключен).

### Позиция куба — `CubePos`

`CubePos` — неизменяемая тройка целых координат куба. Наследует заглушку `MCVec3i`. Фабрики: `of(x, y, z)`, конструктор из `long`, конструктор из `MCBlockPos` (блок переводится в куб через `Coords.blockToCube`).

Упаковка в `long` — 21 бит на ось. Два старших бита отличают куб от чанка: XOR этих битов равен 1 у куба (`0b01` или `0b10`) и 0 у чанка. Перед записью старшие два бита инвертируются, иначе `Long.MAX_VALUE` был бы валидной позицией (−1, −1, −1). `INVALID_CUBE_POS` и `CloPos.INVALID_CLO_POS` равны `Long.MAX_VALUE`. Ванильный `ChunkPos.INVALID_CHUNK_POS` — другой маркер, `pack(1875066, 1875066)`, и в заголовок не копируется.

`MAX_COORDINATE_VALUE` — максимальная координата куба, которую можно упаковать (блок 33 554 431, переведённый в куб). `ZERO` — куб (0, 0, 0). `getChessboardDistance` — чебышёвское расстояние до другого куба.

Интерфейс `Cube` — любой объект, у которого есть `getCubePos()`.

### Чанк или куб — `CloPos`

`CloPos` (chunk-like object position) — одна позиция, которая бывает либо колонкой (чанком), либо кубом. У колонки координата Y равна `Integer.MAX_VALUE`; такой Y запрещён для куба.

Фабрики: `cube(x, y, z)`, `cube(CubePos)`, `cube(MCBlockPos)`, `chunk(x, z)`, `chunk(MCChunkPos)`, `section(MCSectionPos)` (секция переводится в содержащий её куб).

Упакованный `long` колонки совпадает с ванильным `ChunkPos.pack` (`MCChunkPos.pack(int, int)`, обратно `MCChunkPos.unpack(long)`). Куб пишет `CubePos.asLong`. Методы `chunkAsLong` и `cubeAsLong` пишут соответствующий формат. Разбор идёт обратно в `CubePos` или `MCChunkPos`. `CloPos.toLong()` остаётся отдельным методом для DASM и не является упаковкой чанка.

### Заглушки Minecraft — пакет `minecraft`

Каждый класс помечен `@Header` и не содержит реализации:

| Заглушка | Ванильный класс после трансформа |
| --- | --- |
| `MCBitStorage` | `net.minecraft.util.SimpleBitStorage` |
| `MCBlockGetter` | `BlockGetter` |
| `MCBlockPos` | `BlockPos` |
| `MCBlockState` | `BlockState` |
| `MCChunkPos` | `ChunkPos` |
| `MCEntity` | `Entity` |
| `MCLevelHeightAccessor` | `LevelHeightAccessor` |
| `MCSectionPos` | `SectionPos` |
| `MCVec3i` | `Vec3i` |

Таблица соответствий — корневой `javaHeaders.json`. Плагин сводит методы по имени: у `MCChunkPos` это `pack` / `unpack` / `getX` / `getZ`, у `MCLevelHeightAccessor` — `getMinY` / `getMaxY`. Поля record `ChunkPos` закрыты, а одноимённый аксессор javaheaders переписывает в `getfield`, поэтому координаты экземпляра читаются через публичные `pack()` и `getX` / `getZ`. Пока трансформ не применён, вызов метода заглушки невозможен: конструкторы бросают `IllegalStateException`, методы объявлены `native`.

### Карта высот

Высота поверхности хранится деревом, а не плоским массивом на весь мир. Узел `SurfaceTrackerNode` покрывает колонку 16×16 блоков и диапазон Y, который растёт со масштабом.

- Масштаб 0 покрывает `DIAMETER_IN_BLOCKS` блоков по вертикали.
- У обычного узла 16 детей (`NODE_COUNT_BITS = 4`). У корня 2 ребёнка.
- `MAX_SCALE = 6` покрывает примерно от −2²⁸ до 2²⁸.
- Высоты лежат в `MCBitStorage`. Грязные колонки отмечены битовым набором `dirtyPositions`: `getHeight(x, z)` пересчитывает высоту, если колонка грязная.
- Старший бит `heightmapTypeAndRequiresSave` — флаг «нужно сохранить», остальные биты — тип карты высот.
- `SurfaceTrackerLeaf` — лист, читает блоки. `SurfaceTrackerBranch` — внутренний узел. `InterleavedHeightmapStorage` укладывает несколько карт в одно хранилище битов.

`HeightmapStorage` сохраняет и загружает узел по координатам секции, типу карты, масштабу и `scaledY`. `HeightmapSource` — источник сырой высоты для пересчёта узла.

`ColumnCubeMap` помнит, какие Y кубов загружены в одной колонке (`ConcurrentHashMap`-множество). `ColumnCubeMapGetter` отдаёт эту карту по координатам колонки. По ней решается, есть ли над колонкой загруженный куб (освещение, клиентский трекер поверхности).

### Стиль мира — `CubicLevelHeightAccessor`

Мир сообщает, как он устроен:

| `WorldStyle` | Кубический | Генерирует 2D-чанки |
| --- | --- | --- |
| `CUBIC` | да | нет |
| `HYBRID` | да | да (ванильная генерация, но высота не ограничена) |
| `CHUNK` | нет | да (ванильный мир) |

`isCubic()` и `generates2DChunks()` читают стиль. `setWorldStyle` в ядре пустой: мод переопределяет его там, где стиль реально хранится.

## Функции

### `Coords`

Переводы между блоком, секцией, кубом и локальной координатой внутри куба. Параметр `val` — одна ось. Параметр `pos` типа `long` — уже упакованная позиция.

Основные группы:

- блок ↔ секция ↔ куб: `blockToCube`, `blockToSection`, `sectionToCube`, `cubeToSection`, `cubeToMinBlock`, `sectionToMinBlock`, `blockToLocal`;
- индекс секции или колонки внутри куба: `columnToColumnIndex`, маски из `getPosToIndexMask`;
- проверки границ и «нет высоты»: константа `NO_HEIGHT = Integer.MIN_VALUE + 32`.

Логарифмы диаметров кэшируются. Диаметр — степень двойки, поэтому сдвиги заменяют деление.

### Поиск спавна — `SpawnPlaceFinder`

`getTopBlockBisect` ищет поверхность бинарным поиском по вертикали, а не сканированием всего столбца. Предикаты `isValidSpawnBlock` и `isEmptyCollision` приходят снаружи (они зависят от версии Minecraft). Над точкой спавна требуется `MIN_FREE_SPACE_SPAWN = 32` пустых блока. Если диапазон не находится, метод пишет в лог `CubicChunksBase.LOGGER` и возвращает `null`.

### Множество точек — `Int3HashSet`

Хеш-множество троек `int`, рассчитанное на запросы в соседних координатах. Ключ и значение лежат в off-heap памяти через `PlatformDependent` (Netty). Младшие 2 бита каждой оси выбирают флаг внутри бакета, остальные — бакет. Не потокобезопасно: параллельное использование может уронить JVM. Реализует `AutoCloseable` и обязано освобождать память.

### Прочее

- `MathUtil` — `log2`, вспомогательная арифметика для координат и дерева высот.
- `Bits` — операции над битами.
- `AddressTools` — упаковка координат в адреса хранилища.
- `ExecutorUtils`, `CCForkJoinWorkerThreadFactory` — пулы потоков ядра.
- `ChunkIoMainThreadTaskUtils` — задачи ввода-вывода чанков, которые обязаны выполниться на главном потоке.
- `Utils` — мелкие общие помощники.
- Аннотации: `UsedFromASM` (метод или поле трогает ASM/DASM, сигнатуру нельзя менять молча), `Public` (мод открывает член наружу), `MethodsReturnNonnullByDefault`.

## Тесты

Тесты лежат в `CubicChunksCore/src/test` и покрывают `CubePos`, `CloPos`, `Coords`, `Int3HashSet` и узлы карты высот. Задача `test` подпроекта выключена: без классов Minecraft после трансформа javaheaders они не запускаются. JAR с классификатором `tests` собирает задача `testsJar` и отдаёт его родителю конфигурацией `testArchivesOutput`. Родитель распаковывает этот JAR задачей `unzipTests` (без заглушек `minecraft/**`) и запускает тесты ядра своим `test`.

# Серверная загрузка куба

Как кубическое измерение доводит куб до `LevelCube`. Контракт — [ARCHITECTURE.md](ARCHITECTURE.md) §3. Сеть и клиентский кэш — E7, свет — E10, запись региона — E11, замена синусоиды — E12.

## Пирамида

`CubePyramid.initCubePyramids` — DASM-копия `<clinit>` ванильной `ChunkPyramid`. В рантайме статический инициализатор не бросает `DasmFailedToApply`: исходник до трансформации по-прежнему кидает это исключение, это заглушка, а не рабочий путь.

У каждого индекса `ChunkStatus` 26.1.2 есть `CubeStep`. Блоки пишет только `generateNoise`. Остальные шаги, кроме `full`, остаются `passThrough`: биомы, поверхность, карверы, фичи, `initializeLight` и `light` ничего не записывают. `full` — нативный трансформ `ChunkStatusTasks.full` и собирает `LevelCube`.

## Тикеты

На `cc_isCubic` игрок ставит тикет куба `CloPos.section`, а не `ChunkPos` с потерянным Y. `PLAYER_SIMULATION` вешается на этот `long`. `PLAYER_LOADING` для кубов дополнительно режется вертикальным радиусом `CommonConfig.getVerticalViewDistance()`: секции переводятся в кубы через `Coords.sectionToCubeCeil`. Горизонталь остаётся ванильной дистанцией просмотра. На стиле `CHUNK` вертикальный фильтр не включается, тикеты остаются колоночными.

Поиск спавна (`PlayerSpawnFinder.scheduleCandidate`) на кубическом уровне кладёт `SPAWN_SEARCH` на куб: X/Z из колонки кандидата, Y из предложенной точки спавна. Колоночный тикет этот Y не хранит.

## Кэш

`ServerCubeCache.cc_getCube` доводит держатель до запрошенного статуса. `generateNoise` заполняет `SMOOTH_STONE` синусоидой от `CubicChunks.SUPERFLAT_HEIGHT` (амплитуда 20). Шаг `full` логирует `LevelCube full at {} count={}` и увеличивает `FullCubeTracker`.

`cc_save` возвращает `false`. Каталог измерения не получает 3D-регион (`region3d`, `cubes`, `*.3dr`).

## Тик и локальная сложность

`cc_tickCube` делает случайные тики блока и жидкости по секциям куба. Осадки не тикаются: им нужны карты высот (E9). Block entity, запланированные тики и POI остаются в E16, `registerTickContainerInLevel` пустой.

`ServerLevel.getCurrentDifficultyAt` на кубическом уровне читает `inhabitedTime` куба в статусе `FULL`, а не колонки.

## Что остаётся следующим эпикам

- Пакет и клиентский кэш — [client-cache.md](client-cache.md).
- Рендер — E8.
- Карты высот и осадки — E9.
- Свет и `getCubeForLighting` — E10.
- Запись и чтение региона — E11.
- Замена синусоиды и 2D-генерация `HYBRID` — E12.
- Расписание тиков, block entity, POI — E16.
- Спавн мобов — E17.
- Экран мира — E19.

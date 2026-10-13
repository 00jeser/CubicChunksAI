# E6 — Серверная загрузка куба

**Этап ТЗ:** 2. Кубический мир виден  
**Статус:** planned  
**Зависимости:** [E5](e5-world-style.md)  
**Следующий:** [E7](e7-network-cache.md)

После флага стиля. Сеть и рендер не начинать, пока куб не доходит до `LevelCube` на сервере.

---

## Цель

В измерении `CUBIC` игрок порождает тикеты кубов, `CubePyramid` доводит их до `full`, `generateNoise` заполняет синусоиду, в серверном кэше лежит `LevelCube`.

---

## Scope

- Путь тикета: `MixinDistanceManager`, `MixinPlayerTicketTracker`, `MixinChunkTracker` для `CloPos` куба.
- `MixinChunkMap` / `CubeHolder` / `MixinChunkGenerationTask` до статуса `full`.
- `CubePyramid` инициализируется DASM, а не падает `DasmFailedToApply`.
- `ServerCubeCache` отдаёт куб по `CubePos`.
- Лог или счётчик: число `LevelCube` в радиусе игрока.

---

## Out of scope

- Отправка пакета — [E7](e7-network-cache.md). `MixinPlayerChunkSender` можно поправить, только если без этого генерация не завершается; содержимое пакета не обогащать.
- Свет, карты высот, сейв — E9–E11. `cc_save` остаётся `false`. `initializeLight` / `light` остаются `passThrough`.
- Замена синусоиды — [E12](e12-noise-biomes.md).
- `getCubeForLighting` — [E10](e10-lighting.md).
- Тик куба — [E16](e16-ticks-poi.md).

---

## Архитектура эпика

### Решение

Сервер считает куб готовым, когда шаг `full` вернул `LevelCube`. Пустой `passThrough` на биомах и свете не блокирует этот переход: так устроен текущий пайплайн, и этап 2 ТЗ на этом стоит.

### Состояние к концу E6

```text
ServerCubeCache
└── LevelCube на CloPos вокруг игрока
    ├── секции с SMOOTH_STONE по синусоиде
    └── статус full

CubePyramid
├── CC_GENERATION_PYRAMID_CUBES
└── CC_LOADING_PYRAMID_CUBES     шаги чтения есть, регион пустой до E11
```

Нет: записи региона, клиента с кубом, настоящих биомов.

### Поток загрузки

1. Стиль `CUBIC` (E5). Игрок ставит тикет куба, не только колонки.
2. `DistanceManager` поднимает держатель с `CloPos.cube`.
3. `ChunkGenerationTask` берёт шаг из `CubePyramid.getStepTo`.
4. `CubeStatusTasks.generateNoise` пишет синусоиду от `SUPERFLAT_HEIGHT`.
5. `full` строит `LevelCube`.
6. `cc_save` не вызывается как успех. Выгрузка без записи допустима.

На стиле `CHUNK` шаги 2–5 не выполняются (инвариант E5).

### Модули

| Тема | Путь |
| --- | --- |
| Кэш | `server/level/ServerCubeCache.java`, `mixin/.../MixinServerChunkCache.java` |
| Карта | `mixin/.../MixinChunkMap.java`, `server/level/CubicChunkMap.java` |
| Держатель | `server/level/CubeHolder.java`, `mixin/.../MixinChunkHolder.java` |
| Задача | `mixin/.../MixinChunkGenerationTask.java` |
| Пирамида | `world/level/cube/status/CubePyramid.java`, `CubeStatusTasks.java` |
| Тикеты | `mixin/.../MixinDistanceManager.java`, `world/level/CubicTicketStorage.java` |

### Инварианты E6

1. `passThrough` не пишет блоки. Блоки пишет только `generateNoise`.
2. `cc_save` возвращает `false`.
3. `CHUNK`-измерение не создаёт `LevelCube`.
4. `DasmFailedToApply` из `<clinit>` пирамиды — провал эпика, не «рантайм потом».

---

## Спринты

```text
S1 пирамида жива → S2 тикеты куба → S3 LevelCube в кэше
```

### Sprint 6.1 — Пирамида

| ID | Задача | Результат |
| --- | --- | --- |
| E6-S1-1 | Добиться применения `CubePyramid.initCubePyramids` | статический инициализатор не кидает `DasmFailedToApply` |
| E6-S1-2 | Сверить индексы `ChunkStatus` 26.1.2 с `getStepTo` | у каждого статуса генерации есть `CubeStep` |
| E6-S1-3 | Оставить `passThrough` на всех шагах, кроме `generateNoise` и `full` | diff этих методов пустой по смыслу |

**DoD спринта:** юнит или интеграционный тест поднимает пирамиду и видит шаг `noise` и шаг `full`.

### Sprint 6.2 — Тикеты

| ID | Задача | Результат |
| --- | --- | --- |
| E6-S2-1 | Вести тикет игрока как куб, когда `cc_isCubic` | `CloPos` в трекере, не `ChunkPos` с потерянным Y |
| E6-S2-2 | Радиус по вертикали брать из `CommonConfig.getVerticalViewDistance` | смена 8 → 2 в тесте меняет число тикетов |
| E6-S2-3 | На `CHUNK` оставить ванильный тикет колонки | тест двух стилей из E5 |

**DoD спринта:** вокруг тестового игрока есть тикеты с `CloPos.isCube()`. Регрессия E5: `CHUNK` без кубовых тикетов.

### Sprint 6.3 — Кэш

| ID | Задача | Результат |
| --- | --- | --- |
| E6-S3-1 | Довести один куб под игроком до `LevelCube` | `IntegrationTestServerCubeCache` перестаёт быть списком TODO на этот путь |
| E6-S3-2 | Проверить блоки синусоиды: камень на `SUPERFLAT_HEIGHT` и воздух выше амплитуды | тест `getBlockState` |
| E6-S3-3 | Не включать запись | `cc_save` по-прежнему `false`; регион кубов не появляется в каталоге мира |

**DoD спринта:** интеграционный тест сервера видит `LevelCube` и камень. Клиент не обязателен.

---

## Критерии приёмки эпика

1. Запуск выделенного сервера или game test: в логе стиль `CUBIC`, в кэше есть `LevelCube`.
2. Блок синусоиды читается через `CubeAccess`.
3. Каталог мира не содержит нового 3D-региона кубов.
4. **Регрессия:** E4 тесты, E5 два стиля. `CHUNK`-измерение без `LevelCube`.
5. **Документация:** ARCHITECTURE §3 шаг 4 совпадает с фактом (синусоида до E12).

**Не требуется в E6:** клиентский кэш, картинка, свет, сейв.

---

## Риски и решения

| Риск | Решение |
| --- | --- |
| `full` падает, потому что DASM ждёт свет и карты высот | S6.1: шаг остаётся трансформом; недостающие вызовы внутри — заглушки E9/E10, не отмена `full` |
| Тикет куба зацикливается с тикетом колонки | S6.2: у куба Y из `CloPos`, колонка не получает тот же `long` |
| Тест вешает сервер, как `TestCubicServerLevel` | S6.3 чинит зависание точечно; не помечать весь кэш `@Disabled` |

---

## Definition of Done

- Спринты 6.1–6.3 Done
- Приёмка прогнана
- ARCHITECTURE §3 уточнён фактом синусоиды
- Регрессия E4–E5 цела

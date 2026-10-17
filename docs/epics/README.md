# Эпики Cubic Chunks

Порт на NeoForge `26.1.2.116` и доведение мода до приёмки [TZ.md](../TZ.md) §7. Контракт состояния — [ARCHITECTURE.md](../ARCHITECTURE.md).

Статус нового эпика — `planned`. `done` ставит агент реализации, когда приёмка эпика пройдена. Номера не переиспользовать.

Жёсткий порядок — E1–E8 (ТЗ §9, этапы 1–2). Дальше две цепочки можно вести параллельно, если соблюдены конфликты файлов в шапке эпика.

## Граф

```text
E1 → E2 → E3 → E4 → E5 → E6 → E7 → E8 → E9 → E10 → E11
                      │         │              │
                      │         │              ├──────────────┐
                      │         └→ E12 → E14 → E13            │
                      │                        │              │
                      └→ E19                   │              │
                                               ▼              ▼
                                   E8 → E15 → E16 → E18      │
                                          │                  │
                              E9 + E12 + E15 → E17           │
                                               │             │
                   E11 + E13 + E16 + E17 + E18 + E19 ────────┴→ E20
```

`CubeStatusTasks` делят по методам: E12 — `generateBiomes` и `generateNoise`, E14 — `generateStructure*`, E13 — `generateSurface`, `generateCarvers`, `generateFeatures`, E10 — `initializeLight` и `light`. Чужой метод не переписывать.

## Таблица

| Эпик | Статус | Этап ТЗ | Цель в одну строку | Зависит от |
| --- | --- | --- | --- | --- |
| [E1 — Тулчейн 26.1.2](e1-toolchain.md) | done | 1 | Сборка резолвит NeoForge `26.1.2.116`, Java 25, Gradle 9.2.1 | — |
| [E2 — Заголовки ядра](e2-core-headers.md) | done | 1 | `MC*` и тесты ядра совпадают с API 26.1 | E1 |
| [E3 — DASM и Mixin](e3-dasm-mixin.md) | done | 1 | Наборы и миксины грузятся на 26.1, версии `dasm` записаны по факту сборки | E2 |
| [E4 — Сигнатуры ванили](e4-signature-port.md) | done | 1 | `compileJava` и невыключенные тесты зелёные, клиент до меню | E3 |
| [E5 — Стиль мира](e5-world-style.md) | done | 2 | `cc_isCubic` берётся из `WorldStyle`, измерение `CHUNK` остаётся ванильным | E4 |
| [E6 — Серверная загрузка](e6-server-load.md) | done | 2 | Куб вокруг игрока доходит до `LevelCube` с синусоидой | E5 |
| [E7 — Сеть и клиентский кэш](e7-network-cache.md) | done | 2 | Клиент принимает куб в `ClientCubeCache.Storage` | E6 |
| [E8 — Рендер секций](e8-render.md) | done | 2 | Синусоида видна в мире | E7 |
| [E9 — Карты высот](e9-heightmaps.md) | planned | 3 | `CubeAccess` не кидает на heightmap, колонка знает кубы | E8 |
| [E10 — Свет](e10-lighting.md) | planned | 3 | Яркость не подменена `LightBlock.MAX_LEVEL` | E9 |
| [E11 — Сохранение](e11-persistence.md) | planned | 3 | Куб читается из 3D-региона после перезапуска | E10 |
| [E12 — Шум и биомы](e12-noise-biomes.md) | planned | 4 | Синусоида заменена шумом; `HYBRID` гоняет ванильный генератор | E8 |
| [E13 — Поверхность и фичи](e13-surface-features.md) | planned | 4 | Поверхность, карверы и фичи пишут блоки куба | E14 |
| [E14 — Структуры](e14-structures.md) | planned | 4 | Старты и ссылки структур живут на кубе | E12 |
| [E15 — Сущности](e15-entities.md) | planned | 5 | Столкновения, телепорт и переход между кубами | E8 |
| [E16 — Тики, block entity, POI](e16-ticks-poi.md) | planned | 5 | Куб тикает блоки, сущности блоков и POI | E15, E11 |
| [E17 — Спавн](e17-spawn.md) | planned | 5 | Игрок и мобы появляются на поверхности, а не в пустоте | E9, E12, E15 |
| [E18 — События NeoForge](e18-neoforge-events.md) | planned | 6 | Хуки NeoForge видят куб; source set `forge` подключён | E16 |
| [E19 — Экран мира и конфиг](e19-world-ui.md) | planned | 6 | Переключатель кубических чанков работает на экране 26.1 | E5 |
| [E20 — Приёмка](e20-acceptance.md) | planned | 6 | Сценарий ТЗ §7 пройден, ванильный мир не сломан | E11, E13, E16, E17, E18, E19 |

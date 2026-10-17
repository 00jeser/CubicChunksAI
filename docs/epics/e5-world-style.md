# E5 — Стиль мира

**Этап ТЗ:** 2. Кубический мир виден  
**Статус:** done  
**Зависимости:** [E4](e4-signature-port.md)  
**Следующий:** [E6](e6-server-load.md)

После меню. Параллелить с E6 нельзя: E6 читает флаг, который ставит этот эпик. С E19 общий `CommonConfig` — E19 идёт после E5.

---

## Цель

У каждого `ServerLevel` есть `WorldStyle`. `CUBIC` и `HYBRID` дают `cc_isCubic == true`, `CHUNK` даёт `false`. Новый мир по умолчанию кубический. Ванильное измерение в том же процессе не входит в кубовый пайплайн.

---

## Scope

- Запись стиля в конструкторе `MixinServerLevel`.
- Чтение `ServerConfig.getWorldStyle(dimension)`.
- Связь с `CommonConfig.shouldGenerateNewWorldsAsCC()` для нового мира.
- Хранение стиля в данных уровня, чтобы перезапуск не брал только глобальный toml.
- Лог одной строки при создании уровня: измерение и стиль.

---

## Out of scope

- Настоящая 2D-генерация `HYBRID` — [E12](e12-noise-biomes.md). Здесь стиль только записывается; `generates2DChunks` начинает влиять на задачи чанка в E12.
- Кнопка на новом экране — [E19](e19-world-ui.md). Существующий сеттер `cc_setCubicChunks` не ломать.
- Генерация кубов — [E6](e6-server-load.md).
- Миграция старых миров. Нет данных стиля и `generateNewWorldsAsCC=false` — стиль `CHUNK`.

---

## Продуктовая модель

```text
Кнопка «Cubic Chunks»  →  CommonConfig.generateNewWorldsAsCC
Сервер нового мира     →  если true, defaultWorldStyle иначе CHUNK
ServerConfig.overrides →  измерение → CUBIC | HYBRID | CHUNK
ServerLevel            →  worldStyle
                        →  cc_isCubic = style != CHUNK
```

`HYBRID` не равен `CUBIC`: оба кубические, но `WorldStyle.generates2DChunks()` истинен только у `HYBRID` и `CHUNK`.

---

## Архитектура эпика

### Решение

Источник истины стиля измерения — данные уровня. `ServerConfig` задаёт значение при создании и перекрытия по id измерения. Глобальный флаг мода не подменяет уже записанный стиль.

### Состояние к концу E5

```text
ServerLevel
├── worldStyle: WorldStyle
└── cc_isCubic: boolean          true ⇔ style != CHUNK

Level data
└── cubicchunks:world_style      строка CUBIC | HYBRID | CHUNK

ServerConfig
├── defaultWorldStyle
└── overrides: dimension → style
```

Нет: кубов в кэше. Флаг может быть истинным, а пайплайн ещё падать — это E6.

### Поток старта уровня

1. `cc_onInit` читает данные уровня.
2. Если ключа нет — это новый уровень: стиль из `ServerConfig.getWorldStyle`, а для совсем нового мира без serverconfig — из `shouldGenerateNewWorldsAsCC()` (`true` → `CUBIC`, `false` → `CHUNK`).
3. Перекрытие `ServerConfig` для id измерения побеждает умолчание, но не затирает уже записанный ключ при следующем входе.
4. `cc_setCubic()` вызывается только для `CUBIC` и `HYBRID`.
5. В лог пишется `dimension=<id> style=<style> cubic=<bool>`.

Порядок «записанный ключ важнее toml» — чтобы смена default в конфиге не перекраивала старый мир. Смена стиля существующего мира в этом эпике не делается.

### Модули

| Тема | Путь |
| --- | --- |
| Конструктор | `mixin/core/common/server/level/MixinServerLevel.java` |
| Флаг | `MarkableAsCubic.java`, поле на `MixinLevel` |
| Стили | `CubicChunksCore/.../world/CubicLevelHeightAccessor.java` |
| Конфиг сервера | `config/ServerConfig.java` |
| Конфиг клиента/общий | `config/CommonConfig.java` |
| Уровень | `world/level/CubicLevel.java` |

### Инварианты E5

1. ARCHITECTURE §10.4: `CHUNK` не входит в кубовый пайплайн.
2. ARCHITECTURE §10.11: стиль у измерения, не у процесса. Два уровня в одном сервере могут различаться.
3. `HYBRID` не схлопывается в `CUBIC` в сохранённом ключе.
4. Пустой `cc_tickCube` не заполняется.

---

## Спринты

```text
S1 поле стиля → S2 запись в данные уровня → S3 два измерения в одном сервере
```

### Sprint 5.1 — Поле и чтение конфига

| ID | Задача | Результат |
| --- | --- | --- |
| E5-S1-1 | Добавить `worldStyle` на кубический уровень | геттер на `CubicLevel` |
| E5-S1-2 | В `cc_onInit` выставить стиль из `ServerConfig` | лог строки старта |
| E5-S1-3 | Вызвать `cc_setCubic()` для стилей, отличных от `CHUNK` | `cc_isCubic()` совпадает с правилом |

**DoD спринта:** юнит-тест на три стиля. Мир ещё можно не запускать. Регрессия E4: меню на месте.

### Sprint 5.2 — Данные уровня

| ID | Задача | Результат |
| --- | --- | --- |
| E5-S2-1 | Записать стиль в данные уровня при первом создании | ключ переживает перезапуск сервера в тесте |
| E5-S2-2 | При повторном входе не читать default поверх ключа | тест: toml говорит `CHUNK`, сейв говорит `CUBIC`, остаётся `CUBIC` |
| E5-S2-3 | Новый мир при `generateNewWorldsAsCC=false` получает `CHUNK` | тест конфига |

**DoD спринта:** стиль не прыгает между запусками. Сохранение кубов по-прежнему выключено — пишется только ключ уровня ванильным путём данных мира.

### Sprint 5.3 — Два измерения

| ID | Задача | Результат |
| --- | --- | --- |
| E5-S3-1 | Поднять тестовый сервер с оверворлдом `CUBIC` и измерением из списка `vanillaDimensions` | в логе две строки с разными `cubic=` |
| E5-S3-2 | Проверить, что `MixinChunkMap` на `CHUNK`-уровне не зовёт `cc_prepareTickingChunk` | тест или точка лога; кубовый путь не обязателен быть успешным |
| E5-S3-3 | Обновить ARCHITECTURE §2: `cc_isCubic` больше не «сегодня не выставляется» | абзац совпадает с кодом |

**DoD спринта:** два стиля в одном процессе различимы по логу. Крах генерации куба допустим и уходит в E6.

---

## Критерии приёмки эпика

1. Новый мир с кнопкой по умолчанию пишет в лог `style=CUBIC cubic=true`.
2. Мир с `generateNewWorldsAsCC=false` пишет `style=CHUNK cubic=false`.
3. Повторный вход не меняет стиль.
4. **Регрессия:** E4 меню и `test` зелёные. `cc_save` всё ещё `false`.
5. **Документация:** ARCHITECTURE §2 обновлён.

**Не требуется в E5:** видимые блоки, `HYBRID`-генерация, экран 26.1.

---

## Риски и решения

| Риск | Решение |
| --- | --- |
| Данные уровня 26.1 больше не `ServerLevelData` в том виде, как в миксине | S5.2 берёт фактический API сохранения данных, ключ остаётся один |
| `ServerConfig` падает на кривой строке стиля | S5.1: сообщение с именем ключа, уровень не стартует молча как `CUBIC` |
| Кнопка и serverconfig противоречат друг другу | Модель эпика: кнопка влияет только на мир без записанного ключа |

---

## Definition of Done

- Спринты 5.1–5.3 Done
- Приёмка прогнана
- ARCHITECTURE §2 обновлён
- Регрессия E4 цела

## Итог

Стиль пишется в конструкторе `ServerLevel` сразу после `super()`: инициализаторы `MixinLevel` уже прошли, а `ServerChunkCache` ещё не читал `cc_isCubic`. `CTOR_HEAD` для этого поздно — поля миксина сбрасываются в `super()`.

Ключ `cubicchunks:world_style` пишется тем же NBT-конвертом, что `SavedDataStorage` (`data` + `DataVersion`), напрямую через `NbtIo`. Живой `SavedDataStorage.encode` в тесте упирается в реестры; кодек стиля их не требует.

Два измерения проверяются двумя `ServerLevel` в одном процессе и одной папке мира, не отдельным dedicated-сервером. `MixinChunkMap` не зовёт `cc_prepareTickingChunk` на стиле `CHUNK`: предикат `CubicTicking.preparesTickingCubes`. Сам кубовый тик по-прежнему пустой.

`ClientLevel` стиль не получает — клиентский кэш в E7. `generates2DChunks` на задачи чанка не влияет — E12. Экран и полировка ошибки `ServerConfig` — E19: кривая строка уже бросает `IllegalArgumentException` с именем ключа, файл toml при чтении не переписывается. `cc_tickCube` пустой, `cc_save` возвращает `false`. Миграции старых миров нет: нет ключа и `generateNewWorldsAsCC=false` — стиль `CHUNK`.

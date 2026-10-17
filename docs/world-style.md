# Стиль мира

Как измерение становится кубическим. Контракт — [ARCHITECTURE.md](ARCHITECTURE.md) §2 и §10.4. Экран кнопки — эпик E19, генерация кубов — E6, 2D-генерация `HYBRID` — E12.

## Домен

`CubicLevelHeightAccessor.WorldStyle`:

| Стиль | `isCubic()` | `generates2DChunks()` | `cc_isCubic` |
| --- | --- | --- | --- |
| `CUBIC` | да | нет | `true` |
| `HYBRID` | да | да | `true` |
| `CHUNK` | нет | да | `false` |

`HYBRID` не записывается как `CUBIC`. Оба входят в кубовый пайплайн, но 2D-чанки генерирует только `HYBRID` (это включится в E12). `CHUNK` остаётся ванильным измерением в том же процессе.

## Модель данных

Ключ измерения — `cubicchunks:world_style`. Это `SavedDataType`, файл

`<папка измерения>/data/cubicchunks/world_style.dat`.

NBT-соединение `data.style` — строка `CUBIC`, `HYBRID` или `CHUNK`. Кубы этим ключом не пишутся: `cc_save` по-прежнему возвращает `false`.

Рядом, не вместо ключа:

| Что | Где | На что влияет |
| --- | --- | --- |
| `generateNewWorldsAsCC` | `config/cubicchunks_common.toml`, кнопка создания мира | только измерение без ключа |
| `defaultWorldStyle` | `serverconfig/cubicchunks.toml` | умолчание нового измерения, если кнопка включена |
| `cubicDimensions`, `hybridDimensions`, `vanillaDimensions` | тот же toml | перекрытие по id измерения; сильнее умолчания и кнопки, слабее уже записанного ключа |

`VANILLA` в toml — то же, что `CHUNK`. В сохранённом ключе такого алиаса нет. Кривая строка стиля бросает `IllegalArgumentException` с именем ключа (`worldStyles.defaultWorldStyle` или `cubicchunks:world_style`). Уровень не стартует молча как `CUBIC`.

## Как выбирается стиль

`MixinServerLevel.cc_onInit` вызывается сразу после конструктора `Level` и до `ServerChunkCache`, потому что кэш чанков читает `cc_isCubic` в своём конструкторе.

1. `CubicWorldStyleData.read` читает ключ измерения.
2. Ключа нет — `WorldStyleSelector.select`:
   - нет serverconfig: `shouldGenerateNewWorldsAsCC()` → `CUBIC`, иначе `CHUNK`;
   - serverconfig есть и для id есть перекрытие — `ServerConfig.getWorldStyle`;
   - перекрытия нет и кнопка включена — `defaultWorldStyle`;
   - перекрытия нет и кнопка выключена — `CHUNK`.
3. Ключа не было — `CubicWorldStyleData.write` пишет NBT в тот же файл и с тем же конвертом, что `SavedDataStorage` (`data` + `DataVersion`). Кодек стиля не ходит в реестры.
4. Поле `MixinLevel.cc_worldStyle` и геттер `CubicLevel.cc_worldStyle()`.
5. Для `CUBIC` и `HYBRID` вызывается `cc_setCubic()`. Для `CHUNK` флаг остаётся `false`.
6. Лог: `dimension=<id> style=<style> cubic=<bool>`.

`MixinChunkMap.cc_onVanillaPrepareTickingChunk` зовёт `cc_prepareTickingChunk` только если `CubicTicking.preparesTickingCubes`: уровень кубический и позиция — куб. На стиле `CHUNK` ванильный путь тика не подменяется.

Клиентский `Level` получает стиль пакетом `CCClientboundLevelInfoPacket` (E7), до конструктора `ClientChunkCache`. `CUBIC` и `HYBRID` ставят `cc_isCubic`. `CHUNK` и отсутствующий пакет оставляют флаг ложным, кубовый кэш не создаётся. Общий флаг мода по-прежнему не включает кубы на всех уровнях процесса.

## Что сознательно не сделано

- Настоящая генерация `HYBRID` и влияние `generates2DChunks` на задачи чанка — E12.
- Кнопка на экране 26.1 и момент записи общего toml — E19. Сеттер `cc_setCubicChunks` на месте.
- Загрузка кубов и случайный тик — [server-load.md](server-load.md). Block entity и запланированные тики — E16.
- Миграция старых миров отдельным проходом. Нет ключа и `generateNewWorldsAsCC=false` — стиль `CHUNK`.

# E7 — Сеть и клиентский кэш

**Этап ТЗ:** 2. Кубический мир виден  
**Статус:** done  
**Зависимости:** [E6](e6-server-load.md)  
**Следующий:** [E8](e8-render.md)

После `LevelCube` на сервере. Рендер не начинает работу, пока куб не лежит в `ClientCubeCache.Storage`.

---

## Цель

Сервер шлёт куб игроку, клиент кладёт `LevelCube` в `ClientCubeCache` и забывает его по пакету выгрузки. Пакет по-прежнему без света и без карт высот.

---

## Scope

- `MixinPlayerChunkSender` для куба.
- `CCClientboundLevelCubeWithLightPacket` и `CCClientboundLevelCubePacketData`: секции блоков.
- `CCClientboundSetCubeCacheCenterPacket` и `cc_updateViewCenter`.
- `CCClientboundForgetLevelCloPacket`.
- `CCClientboundLevelChunkPacket` только если колонке в кубическом мире ещё нужен отдельный пакет; не превращать его во второй куб.
- Снятие `UnsupportedOperationException` в клиентском drop, когда есть тест пакета.
- Снятие `@Disabled` у сетевых тестов, которым мешали только клиентские миксины.

---

## Out of scope

- Поле света в пакете — [E10](e10-lighting.md). Имя класса не переименовывать в этом эпике.
- `long[]` карт высот — [E9](e9-heightmaps.md). Пустая карта, как в текущем TODO, остаётся явной.
- Теги block entity — [E16](e16-ticks-poi.md).
- Меш — [E8](e8-render.md).

---

## Продуктовая модель

| Пакет | Несёт | Не несёт в E7 |
| --- | --- | --- |
| `CCClientboundLevelCubeWithLightPacket` | `CubePos`, секции блоков | свет, карты высот, block entity |
| `CCClientboundSetCubeCacheCenterPacket` | центр кубического вида | радиус (радиус — конфиг) |
| `CCClientboundForgetLevelCloPacket` | `CloPos` куба или колонки | причину выгрузки |
| `CCClientboundLevelChunkPacket` | `ChunkPos` колонки | блоки куба |

---

## Архитектура эпика

### Решение

Один куб — один пакет секций. Клиент не достраивает блоки сам. Обработчик `CCNetworkHandler` остаётся на главном потоке игры.

### Состояние к концу E7

```text
ClientCubeCache.Storage
├── AtomicReferenceArray<LevelCube>
├── центр (x, y, z)
└── радиус из verticalViewDistance

Пакет куба
├── pos: CubePos
└── секции блоков
    нет: light, heightmaps, block-entity tags
```

Нет: меша, записи на диск.

### Поток сети

1. `LevelCube` готов (E6). `MixinPlayerChunkSender` выбирает пакет куба, если `CloPos` — куб.
2. `CCClientboundLevelCubePacketData` пишет секции в буфер.
3. Клиентский handler зовёт `cc_replaceWithPacketData`.
4. `Storage` публикует куб по координате.
5. Выгрузка шлёт `CCClientboundForgetLevelCloPacket`, `cc_drop` убирает куб и не кидает исключение.
6. Сдвиг игрока шлёт центр. Кубы вне радиуса забываются.

### Модули

| Тема | Путь |
| --- | --- |
| Отправка | `mixin/core/common/server/network/MixinPlayerChunkSender.java` |
| Регистрация | `network/CCNetworkHandler.java` |
| Пакеты | `network/CCClientboundLevelCubeWithLightPacket.java`, `CCClientboundLevelCubePacketData.java`, `CCClientboundForgetLevelCloPacket.java`, `CCClientboundSetCubeCacheCenterPacket.java` |
| Кэш | `client/multiplayer/ClientCubeCache.java`, `mixin/core/client/multiplayer/MixinClientChunkCache.java` |
| Тесты | `src/test/java/.../test/network/`, `test/client/multiplayer/TestClientCubeCache.java` |

### Инварианты E7

1. Обработчик пакета не читает регион и не генерирует блоки.
2. `CHUNK`-игрок не получает пакет куба.
3. Пустая карта высот не притворяется посчитанной: комментарий E9 остаётся, потребитель heightmap в рендере не требуется до E8/E9.
4. Исключение «пока нет теста пакета» в drop снимается только вместе с тестом.

---

## Спринты

```text
S1 секции в буфер → S2 центр и забывание → S3 тесты без @Disabled
```

### Sprint 7.1 — Пакет секций

| ID | Задача | Результат |
| --- | --- | --- |
| E7-S1-1 | Писать секции `LevelCube` в `CCClientboundLevelCubePacketData` | размер буфера зависит от числа непустых секций |
| E7-S1-2 | На клиенте собирать `LevelCube` с теми же блоками | тест кодека круг |
| E7-S1-3 | Включить отправку из `MixinPlayerChunkSender` для куба | серверный интеграционный тест видит пакет в шпионе или в тестовом соединении |

**DoD спринта:** синусоида, прочитанная на клиенте из пакета, совпадает с серверным кубом. Света в буфере нет.

### Sprint 7.2 — Центр и выгрузка

| ID | Задача | Результат |
| --- | --- | --- |
| E7-S2-1 | Шлёпать `CCClientboundSetCubeCacheCenterPacket` при смене куба игрока | `cc_updateViewCenter` двигает окно |
| E7-S2-2 | Реализовать `cc_drop` без `UnsupportedOperationException` | куб исчезает из `Storage` |
| E7-S2-3 | Забыть кубы вне радиуса | тест: радиус 2, куб на расстоянии 4 отсутствует |

**DoD спринта:** после пакета forget в кэше нет куба. Регрессия E6: серверный `LevelCube` на это не пропадает.

### Sprint 7.3 — Тесты, которые были выключены

| ID | Задача | Результат |
| --- | --- | --- |
| E7-S3-1 | Снять `@Disabled` с `TestCCClientboundLevelCubeWithLightPacket`, `TestCCClientboundSetCubeCacheCenterPacket`, `TestCCClientboundForgetLevelCloPacket`, если их держали только клиентские миксины | тесты зелёные |
| E7-S3-2 | Если миксины в юните по-прежнему не применяются — оставить `@Disabled` со ссылкой на этот эпик и покрыть тот же контракт интеграционным тестом | нет молчаливого пропуска |
| E7-S3-3 | Обновить ARCHITECTURE §3 шаг 5: пакет без света до E10 | текст совпадает |

**DoD спринта:** контракт пакета покрыт зелёным тестом. `./gradlew test` зелёный.

---

## Критерии приёмки эпика

1. Игрок на тестовом сервере получает куб: клиентский кэш содержит `LevelCube` с камнем синусоиды.
2. Уход радиуса убирает куб из кэша.
3. В дампе пакета нет световых массивов.
4. **Регрессия:** E6 серверный кэш, E5 `CHUNK` без пакета куба, E4 меню.
5. **Документация:** ARCHITECTURE §3.

**Не требуется в E7:** картинка в мире, свет, карты высот, block entity.

---

## Риски и решения

| Риск | Решение |
| --- | --- |
| `CustomPacketPayload` 26.1 требует иной регистратор | чинится в E4; если хвост остался — S7.1 правит только регистрацию этих четырёх типов |
| Пакет колонки и пакет куба путают `long` | S7.1: тип пакета разный, `CloPos` разбирается своими старшими битами |
| Drop без теста снова кидает | S7.2 и S7.3 в одном эпике, не разносить |

---

## Definition of Done

- Спринты 7.1–7.3 Done
- Приёмка прогнана
- ARCHITECTURE §3 обновлён
- Регрессия E4–E6 цела

## Итог

Сервер на кубическом уровне шлёт `CCClientboundLevelCubeWithLightPacket`: в буфере `CubePos` и байты секций, хвоста со светом нет. Клиент собирает `LevelCube` с теми же блоками синусоиды. Пустая карта высот и пустой потребитель block entity остаются явными (E9, E16). `queueLightUpdate` ничего не рисует: меш — E8.

`CCClientboundSetCubeCacheCenterPacket` уже уходил из `MixinChunkMap`. `cc_updateViewCenter` двигает окно и снимает кубы вне радиуса. `cc_drop` куб из `Storage` убирает и исключения не бросает. Серверный куб при этом не очищается.

Стиль `ClientLevel` приходит пакетом `CCClientboundLevelInfoPacket` перед логином и респавном и применяется после `Level.<init>`, до `ClientChunkCache`. `CHUNK` кубовый кэш не создаёт и пакет куба не получает.

Радиус `Storage` — чебышёвский, большее из ванильного `calculateStorageRange(viewDistance)` и той же формулы от `verticalViewDistance`. Отдельной вертикальной оси у массива нет. Тест «радиус 2, расстояние 4» идёт через `Storage`, а не через конфиг по умолчанию.

Обработчики трёх пакетов куба, центра и forget зелёные: мок `ClientChunkCache` реализует `ClientCubeCache`, клиентский миксин для этого не нужен. `TestClientCubeCache` остаётся `@Disabled`: в юнит-тестах dist dedicated server клиентские миксины не применяются. Тот же контракт секций, drop, радиуса и покрытия секций колонками закрывает `TestClientCubeNetwork`. `cc_replaceBiomes` по-прежнему кидает `UnsupportedOperationException`: теста пакета биомов нет (E12).

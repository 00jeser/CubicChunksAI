# Клиентский кэш куба

Как куб с сервера попадает в `ClientCubeCache`. Контракт — [ARCHITECTURE.md](ARCHITECTURE.md) §3. Рендер — E8, карты высот — E9, свет — E10.

## Стиль клиента

`MixinPlayerList` шлёт `CCClientboundLevelInfoPacket` сразу перед `ClientboundLoginPacket` и `ClientboundRespawnPacket`. Обработчик кладёт `WorldStyle` в одноразовую очередь. `MixinClientLevel.cc_onInit` читает её после `Level.<init>` и до `new ClientChunkCache`.

`CUBIC` и `HYBRID` вызывают `cc_setCubic()`. `CHUNK` и пустая очередь оставляют флаг ложным: кубовое хранилище не создаётся, `MixinPlayerChunkSender` пакет куба не шлёт.

## Пакет куба

`CCClientboundLevelCubeWithLightPacket` несёт `CubePos` и секции блоков. Размер буфера — сумма `LevelChunkSection.getSerializedSize()`. Световых массивов в дампе нет. Имя класса остаётся до E10.

Обработчик зовёт `cc_replaceWithPacketData` с пустой картой высот и пустым потребителем block entity. Блоки читаются из буфера, регион и генератор не вызываются. `queueLightUpdate` зовёт `CubicSectionMesh.onCubeReady`: меш секций куба перестраивается из кэша. Световых массивов в пакете нет (E10).

Колонка в кубическом мире — отдельный `CCClientboundLevelChunkPacket` с одним `ChunkPos`. Блоков куба в нём нет.

## Кэш

`ClientCubeCache.Storage` — кубический массив `LevelCube`. Радиус чебышёвский: большее из ванильного `calculateStorageRange(viewDistance)` и той же формулы от `CommonConfig.verticalViewDistance`. Пакет центра радиус не несёт.

`cc_updateViewCenter` записывает центр и забывает кубы, которые вышли из радиуса. `cc_drop` снимает куб, если он ещё в окне, и не бросает исключение. Серверный `LevelCube` при этом не меняется.

`cc_replaceBiomes` по-прежнему кидает `UnsupportedOperationException`: пакета биомов нет (E12).

## Что остаётся следующим эпикам

- `long[]` карт высот в пакете — E9.
- Световые массивы и снятие `MAX_LEVEL` в `MixinLevelLightEngine` — E10. Меш уже читает блоки секции.
- Теги block entity — E16.

# Документация Cubic Chunks

Мод заменяет колонки фиксированной высоты кубами. Целевая платформа порта — NeoForge `26.1.2.116` и Minecraft `26.1.2`. Сборка на этой версии. Сервер кладёт `LevelCube` в кэш и шлёт его клиенту. Клиент хранит куб в `ClientCubeCache`. Картинки в мире ещё нет (E8).

| Документ | О чём |
| --- | --- |
| [ТЗ](TZ.md) | Цели, не-цели, приёмка §7, этапы §9 |
| [Архитектура](ARCHITECTURE.md) | Слои, состояние, потоки тика и загрузки, инварианты §10–11 |
| [Стиль мира](world-style.md) | `WorldStyle`, ключ `cubicchunks:world_style`, выбор `CUBIC` / `HYBRID` / `CHUNK` |
| [Серверная загрузка](server-load.md) | Тикеты куба, синусоида, `LevelCube` в серверном кэше |
| [Клиентский кэш](client-cache.md) | Пакет секций, `ClientCubeCache`, стиль клиента |
| [Эпики](epics/README.md) | E1–E20, граф, статусы |
| [Сборка ядра](architecture.md) | Почему `:CubicChunksCore` — отдельный JAR и как его переписывает javaheaders |
| [CubicChunksCore](cubicchunks-core.md) | Домен и модель данных версионно-независимого ядра |

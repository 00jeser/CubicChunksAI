# E2 — Заголовки ядра под API 26.1

**Этап ТЗ:** 1. Порт собирается  
**Статус:** done  
**Зависимости:** [E1](e1-toolchain.md)  
**Следующий:** [E3](e3-dasm-mixin.md)

Только после зелёного assemble ядра на Java 25. С модом пересекается в `javaHeaders.json` и `MixinLevelHeightAccessor`.

---

## Цель

После javaheaders заглушки `MC*` совпадают с классами Minecraft 26.1.2, тесты `CubePos`, `CloPos`, `Coords` и карты высот из JAR ядра проходят в тестовом source set мода.

---

## Scope

- `MCChunkPos`: `pack` / `unpack` вместо конструктора из `long` и `asLong` / `toLong`, либо карта переименований в трансформе — один способ.
- `MCLevelHeightAccessor`: имена, которые ждёт ядро, без алиасов в моде.
- Удаление `MixinLevelHeightAccessor`.
- Включение `unzipTests` и прогон тестов ядра родителем.
- Версия плагина `io.github.opencubicchunks.javaheaders`, если `1.2.8` не трансформирует JAR на Java 25.

---

## Out of scope

- Игровой `cc_isCubic` и стили — [E5](e5-world-style.md).
- Подключение `SurfaceTracker*` к `LevelCube` — [E9](e9-heightmaps.md).
- Смена схемы упаковки `CloPos` (21 бит, старшие два бита).
- Компиляция миксинов мода — [E4](e4-signature-port.md).

---

## Архитектура эпика

### Решение

Один контракт упаковки чанка. Если javaheaders умеет переименовать `MCChunkPos.toLong()` в `ChunkPos.pack()` и конструктор `long` в `ChunkPos.unpack`, заголовок ядра не меняется. Если не умеет — меняется заголовок и все вызовы в `CubicChunksCore` в том же спринте. Оба способа сразу не оставлять.

`MixinLevelHeightAccessor` уходит. Ядро читает те методы `LevelHeightAccessor`, которые есть в 26.1.2 (`getMinY` / `getMaxY` уже тени в этом миксине). Алиасы `getMinBuildHeight` / `getMaxBuildHeight` не восстанавливать, если ваниль их удалила.

### Состояние к концу E2

```text
MCChunkPos
├── x, z
├── фабрика из int
└── pack / unpack          одно имя на сторону ядра и на сторону игры

MCLevelHeightAccessor
└── методы высоты 26.1.2   без класса-алиаса в моде

Тесты ядра
└── классы из testArchivesOutput распакованы в test source set мода
```

Нет: привязки `WorldStyle` к `ServerLevel`.

### Поток трансформа

1. `:CubicChunksCore:assemble` пишет JAR заглушек.
2. javaheaders по `javaHeaders.json` подменяет `MC*` на классы 26.1.2.
3. `compileJava` мода видит уже подменённые типы в зависимостях ядра. Сам мод ещё может не компилироваться.
4. `unzipTests` кладёт тесты ядра в тестовый вывод мода, и `test` их запускает.

### Модули

| Тема | Путь |
| --- | --- |
| Заголовок чанка | `CubicChunksCore/src/main/java/io/github/opencubicchunks/cc_core/minecraft/MCChunkPos.java` |
| Высота | `.../minecraft/MCLevelHeightAccessor.java` |
| Упаковка | `.../world/level/CloPos.java`, `.../api/CubePos.java` |
| Таблица замен | `javaHeaders.json` |
| Алиас, который снять | `src/main/java/.../mixin/core/common/world/level/MixinLevelHeightAccessor.java` |
| Распаковка тестов | `build.gradle`, задача `unzipTests` |

### Инварианты E2

1. ARCHITECTURE §10.2 и §10.3 (сентинел Y и старшие биты) не меняются.
2. ARCHITECTURE §10.8: исходники ядра не импортируют `net.minecraft`.
3. `INVALID_*` по-прежнему `Long.MAX_VALUE`.
4. Снятие `MixinLevelHeightAccessor` не возвращает старые имена в ядро «на всякий случай».

---

## Спринты

```text
S1 контракт MCChunkPos → S2 высота → S3 тесты ядра в моде
```

### Sprint 2.1 — Упаковка ChunkPos

| ID | Задача | Результат |
| --- | --- | --- |
| E2-S1-1 | Снять с исходников 26.1.2 сигнатуры `ChunkPos.pack` и `ChunkPos.unpack` | таблица «метод заголовка → метод ванили» в коммите эпика |
| E2-S1-2 | Выбрать один способ: переименование в трансформе или правка `MCChunkPos` и вызовов | в ядре нет одновременно `toLong` и `pack` |
| E2-S1-3 | Прогнать `CloPos` / `CubePos` на границах: `(0,0)`, отрицательный Z, `INVALID` | тесты ядра, которые уже есть в `CubicChunksCore/src/test`, согласованы с новым именем |

**DoD спринта:** упакованный `long` колонки на 26.1 совпадает с `ChunkPos.pack` для тех же x, z. Куб по-прежнему отличается старшими битами.

### Sprint 2.2 — Высота без алиаса

| ID | Задача | Результат |
| --- | --- | --- |
| E2-S2-1 | Сверить `MCLevelHeightAccessor` с `LevelHeightAccessor` 26.1.2 | лишних методов в заголовке нет |
| E2-S2-2 | Удалить `MixinLevelHeightAccessor` | миксин больше не в конфиге `core` |
| E2-S2-3 | Поправить комментарий `FIXME temporary hack` — класса нет | поиск по репозиторию не находит `getMinBuildHeight` как алиас мода |

**DoD спринта:** сборка ядра зелёная, миксина-алиаса нет. Если ваниль оставила `getMinBuildHeight`, заголовок вызывает его напрямую, а миксин всё равно удалён.

### Sprint 2.3 — Тесты ядра после трансформа

| ID | Задача | Результат |
| --- | --- | --- |
| E2-S3-1 | Включить `unzipTests` и повесить `test` на него | закомментированный блок `build.gradle` снова задача |
| E2-S3-2 | Убрать причину «java modules complaining», из-за которой задачу выключили, точечно (раскладка class-файлов, не отключение модулей всей игры) | `test` ядра стартует |
| E2-S3-3 | Если `javaheaders` `1.2.8` не работает на Java 25 — записать версию плагина, которая трансформирует JAR, ссылкой на репозиторий артефакта | `build.gradle` содержит эту версию и больше никакую |

**DoD спринта:** тесты `Coords`, `CubePos`, `CloPos` из ядра зелёные внутри `./gradlew test` мода. Тесты мода, которые не компилируются, ещё можно исключить списком — это долг E4, список лежит в описании коммита.

---

## Критерии приёмки эпика

1. Для трёх пар `(x,z)`, включая отрицательный Z, `CloPos.chunk(x,z)` упаковывается в тот же `long`, что `ChunkPos.pack`.
2. `Long.MAX_VALUE` не декодируется как куб и не декодируется как чанк.
3. Класса `MixinLevelHeightAccessor` нет.
4. **Регрессия:** E1 assemble ядра остаётся зелёным; исходники ядра без `net.minecraft`.
5. **Документация:** `docs/cubicchunks-core.md` называет актуальные методы упаковки; ARCHITECTURE §2 больше не говорит «имена toLong не вечные» как открытый вопрос — там записан выбранный способ.

**Не требуется в E2:** запуск клиента, генерация, свет.

---

## Риски и решения

| Риск | Решение |
| --- | --- |
| javaheaders не умеет заменить конструктор на статический `unpack` | S2.1 меняет заголовок, не плодит второй класс |
| `unzipTests` снова ломает модульный путь | S2.3 чинит раскладку; не помечать тесты ядра вечно выключенными |
| Ваниль переименовала `getMinY` | S2.2 берёт имя из исходников 26.1.2, не из миксина 1.21.6 |

---

## Definition of Done

- Спринты 2.1–2.3 Done
- Приёмка прогнана
- ARCHITECTURE §2 и `docs/cubicchunks-core.md` описывают один контракт упаковки
- Регрессия E1 (JAR без Minecraft) цела

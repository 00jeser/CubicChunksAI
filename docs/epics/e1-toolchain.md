# E1 — Тулчейн NeoForge 26.1.2

**Этап ТЗ:** 1. Порт собирается  
**Статус:** done  
**Зависимости:** нет  
**Следующий:** [E2](e2-core-headers.md)

E1–E4 идут строго по цепочке. Параллелить не с чем.

---

## Цель

Проект резолвит NeoForge `26.1.2.116` и собирает JAR `:CubicChunksCore` на Java 25 и Gradle 9.2.1. `compileJava` мода в этом эпике может падать.

---

## Scope

- Версии из ТЗ §2 в `gradle.properties`, `build.gradle`, wrapper, `settings.gradle`.
- Toolchain 25 у мода и у `CubicChunksCore/build.gradle`.
- Снятие Parchment.
- `neoforge.mods.toml` по шаблону MDK 26.1.2.
- Свойство геймтестов `neoforge.enabledGameTestNamespaces`.
- `mixinGen.defaultCompatibilityLevel = JAVA_25`.

---

## Out of scope

- Сигнатуры `ChunkPos`, миксины, пакеты — [E4](e4-signature-port.md).
- Заголовки `MC*` и `unzipTests` — [E2](e2-core-headers.md).
- Версия `dasm` — [E3](e3-dasm-mixin.md).
- Плагин `net.neoforged.gradle.userdev` `7.1.39`. Оставляем `net.neoforged.moddev`.
- Линейки `26.2` и `26.3`.
- Поднятие `regionlib` и `noise` — [E11](e11-persistence.md) и [E12](e12-noise-biomes.md).

---

## Архитектура эпика

### Решение

Версии пишутся буквально из ТЗ §2. Диапазон Minecraft — `[26.1.2]`, как в MDK, не «любая 26.1».

### Состояние к концу E1

```text
Сборка
├── minecraft_version = 26.1.2
├── minecraft_version_range = [26.1.2]
├── neo_version = 26.1.2.116
├── neo_version_range = [26.1.2.116,)
├── Java toolchain = 25                 мод и :CubicChunksCore
├── Gradle = 9.2.1
├── ModDevGradle = 2.0.148
├── foojay = 1.0.0
└── parchment — нет
```

Нет: зелёного `compileJava` мода, новой версии `dasm` и `javaheaders`.

### Поток сборки

1. Wrapper качает Gradle 9.2.1.
2. Foojay `1.0.0` выдаёт JDK 25 обоим проектам.
3. `neoForge.version` тянет артефакт `26.1.2.116`.
4. `processResources` раскрывает `neoforge.mods.toml`.
5. `:CubicChunksCore:assemble` пишет JAR. Классы ядра по-прежнему без `net.minecraft`.

### Модули

| Тема | Путь |
| --- | --- |
| Версии | `gradle.properties` |
| Плагин и toolchain мода | `build.gradle` |
| Wrapper | `gradle/wrapper/gradle-wrapper.properties` |
| Репозитории плагинов | `settings.gradle` |
| Метаданные мода | `src/main/resources/META-INF/neoforge.mods.toml` |
| Toolchain ядра | `CubicChunksCore/build.gradle` |

### Инварианты E1

1. Зависимость на ядро остаётся JAR с атрибутом `LibraryElements.JAR` (ARCHITECTURE §9).
2. В toml нет `modLoader` и `loaderVersion`. Ключ `loader_version_range` из `gradle.properties` больше не подставляется.
3. Блоки `[[mixins]]` (`cubicchunks.mixins.core.json`, `cubicchunks.mixins.access.json`) и `[[dasm]]` (`cubicchunks.dasm.json`) сохраняются.
4. `src_old` по-прежнему не source set.

---

## Спринты

```text
S1 версии → S2 toml и runs → S3 ядро на Java 25
```

### Sprint 1.1 — Зафиксировать версии

| ID | Задача | Результат |
| --- | --- | --- |
| E1-S1-1 | Записать версии ТЗ §2 в `gradle.properties` и id плагина в `build.gradle` | `neo_version=26.1.2.116`, `net.neoforged.moddev` `2.0.148` |
| E1-S1-2 | Поставить wrapper `gradle-9.2.1-bin.zip` | `./gradlew --version` печатает 9.2.1 |
| E1-S1-3 | Поднять foojay до `1.0.0` | `settings.gradle` совпадает с MDK по версии плагина |
| E1-S1-4 | Удалить `parchment { }` и ключи `parchment_minecraft_version`, `parchment_mappings_version` | в репозитории нет parchment как зависимости сборки |

**DoD спринта:** `./gradlew help` скачивает плагины. Падение `compileJava` мода допустимо.

Не делать: не чинить ошибки компиляции мода в этом спринте.

### Sprint 1.2 — Метаданные и запуски

| ID | Задача | Результат |
| --- | --- | --- |
| E1-S2-1 | Переписать `neoforge.mods.toml` по шаблону MDK 26.1.2, сохранив миксины и dasm | нет `modLoader` / `loaderVersion`; лицензия и зависимости на месте |
| E1-S2-2 | Заменить `forge.enabledGameTestNamespaces` на `neoforge.enabledGameTestNamespaces` | client, server, gameTestServer |
| E1-S2-3 | Поставить `defaultCompatibilityLevel` mixinGen в `JAVA_25` | строка в `build.gradle` |
| E1-S2-4 | Убрать `loader_version_range` из `replaceProperties`, если ключ больше не встречается | `processResources` не требует неизвестное свойство |

**DoD спринта:** `processResources` раскрывает toml. В собранном тексте версии равны ТЗ §2.

### Sprint 1.3 — Ядро на Java 25

| ID | Задача | Результат |
| --- | --- | --- |
| E1-S3-1 | Сменить toolchain `CubicChunksCore/build.gradle` с 17 на 25 | один JDK на всё дерево |
| E1-S3-2 | Собрать `:CubicChunksCore:assemble` | JAR с манифестом `FMLModType: GAMELIBRARY` |
| E1-S3-3 | Поправить `docs/architecture.md`: toolchain ядра 25, родитель целится в 26.1.2 | документ сборки не врёт про 17 и 1.21.6 как цель |

**DoD спринта:** assemble ядра зелёный. `javap` класса `MCChunkPos` из JAR не показывает `net.minecraft`.

---

## Критерии приёмки эпика

1. `gradle.properties` содержит `minecraft_version=26.1.2` и `neo_version=26.1.2.116`.
2. `./gradlew :CubicChunksCore:assemble` завершается успешно на JDK 25.
3. В `build/resources` (или выводе `processResources`) нет `loaderVersion` и нет Parchment.
4. **Регрессия:** маска javaheaders `.*CubicChunksCore.*` и `transitive = false` не тронуты.
5. **Документация:** `docs/architecture.md` обновлён; ARCHITECTURE §10 не менялся.

**Не требуется в E1:** зелёный `compileJava` мода, запуск клиента, тесты.

---

## Риски и решения

| Риск | Решение |
| --- | --- |
| ModDevGradle `2.0.148` отвергает старый синтаксис `neoForge.runs` | S1.2: подогнать блок к `build.gradle` MDK, версию плагина не понижать |
| Foojay не отдаёт JDK 25 | S1.3: toolchain остаётся 25; в лог сборки пишется фактический путь к JDK, версия в ТЗ не меняется |
| Раскрытие toml падает на `${loader_version_range}` | S1.2-4 снимает ключ |

---

## Definition of Done

- Спринты 1.1–1.3 Done
- Приёмка прогнана
- `docs/architecture.md` отражает Java 25 и цель 26.1.2
- Регрессия границы JAR ядра цела

## Поздняя правка: JAR мода самодостаточен

`implementation` ядра кладёт linked-JAR только на dev-classpath. В `build/libs/cubicchunks-*.jar` классов `cc_core` не было, и обычная установка NeoForge падала с `NoClassDefFoundError`. Задача `jar` теперь вкладывает `configurations.coreHeaderJar` (это javaheaders-linked JAR, не сырой header) в тот же модуль. Заглушки `cc_core/minecraft/**` не входят. Вложенный jarJar ядра не используется: второй JAR с тем же пакетом FML режет слой. `regionlib` и `noise` в этот JAR не кладутся: текущий байткод их не вызывает, версии поднимают E11 и E12.

`jarJar` зависит от `patchDasmNeoforge`. Без этой связи первый `./gradlew build` на чистом дереве падает: трансформ javaheaders требует уже существующий `build/dasm/dasm-neoforge-3.2.0-fml.jar`. Для чужих JAR трансформ копирует вход в свой выход, а не объявляет вход готовым выходом.

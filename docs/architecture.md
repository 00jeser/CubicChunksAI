# Архитектура сборки

Контракт слоёв, состояния мира и инвариантов — [ARCHITECTURE.md](ARCHITECTURE.md). Этот файл — только граница Gradle-подпроекта `:CubicChunksCore`.

## Домен

Minecraft хранит мир колонками: чанк 16×16 блоков по горизонтали и фиксированный диапазон секций по вертикали. Cubic Chunks заменяет эту модель кубами — кубическими группами секций. Игрок, генерация, освещение и сохранение мира обращаются к позиции куба так же, как ванильный код обращается к позиции чанка.

Код делится на две части:

- **CubicChunks** (`src/`) — мод под NeoForge. Миксины, DASM-наборы и пакеты мира подменяют ванильные классы Minecraft 26.1.2. Этот код знает про конкретную версию игры.
- **CubicChunksCore** (`CubicChunksCore/`) — версионно-независимая библиотека. В ней координаты, константы размера куба, карта высот и заглушки типов Minecraft. Она не ссылается на классы `net.minecraft`.

Старый порт лежит в `src_old/` и в сборку не входит.

## Почему ядро — отдельный Gradle-подпроект

Исторически `CubicChunksCore` подключался git-сабмодулем (`https://github.com/OpenCubicChunks/CubicChunksCore`, ветка `dev`) и включался в сборку строкой `include ':CubicChunksCore'` в `settings.gradle`. Maven-координаты и composite build (`includeBuild`) для него не использовались. Сабмодуль был закреплён на коммите `75a1100c8a6d47f02bde8515d4a7de207f04be36`.

Исходники этого коммита теперь лежат в репозитории (история сохранена через `git subtree`). `.gitmodules` и gitlink удалены. Внешнего артефакта CubicChunksCore сборка не запрашивает.

Подпроект оставлен отдельным, а не влит в source set мода, потому что граница между ними — не организационная, а трансформационная:

1. Классы `io.github.opencubicchunks.cc_core.minecraft.MC*` помечены `@Header` из `javaheaders-api`. Это заглушки (`native`-методы, конструкторы, которые бросают исключение). Их компилируют без Minecraft.
2. Корневой проект зависит от **JAR** подпроекта, а не от его `classes`:

   ```gradle
   implementation(project(":CubicChunksCore")) {
       attributes {
           attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR))
       }
       transitive = false
   }
   ```

3. Плагин `javaheaders` (`setAcceptedJars(".*CubicChunksCore.*")`, конфиг `javaHeaders.json`) переписывает заглушки на настоящие классы Minecraft: `MCBlockPos` → `BlockPos`, `MCChunkPos` → `ChunkPos` и так далее. Имя JAR содержит `CubicChunksCore`, поэтому трансформ его находит.
4. Тесты ядра выключены внутри подпроекта: в его JAR нет классов Minecraft. Он публикует классификатор `tests` в конфигурацию `testArchivesOutput`. Родитель забирает её через `extraTests`. Распаковка этих тестов в тестовый source set мода сейчас закомментирована.

Если скомпилировать исходники ядра прямо на classpath мода, заглушки столкнутся с настоящими классами Minecraft, а трансформ javaheaders не к чему будет применяться.

`compileJava` и `processResources` мода зависят от `CubicChunksCore:assemble`. IntelliJ при синхронизации тоже собирает этот JAR (`ideSyncTask`).

## Сборка подпроекта

`CubicChunksCore/build.gradle` — тот же скрипт, что в upstream-коммите. Он не портировался на NeoForge.

- Java toolchain 25. Мод собирается toolchain 25. Оба резолвятся через Foojay (`settings.gradle`).
- Зависимости `compileOnly`: `javaheaders-api` (JitPack `3e193a8`), Guava, Log4j, Netty, fastutil, jsr305, JetBrains annotations. В рантайм мода они не транзитятся (`transitive = false`).
- `generatePackageInfo` дописывает отсутствующие `package-info.java` с `@ParametersAreNonnullByDefault` и `@MethodsReturnNonnullByDefault`. В импортированном дереве эти файлы уже есть.
- JAR получает манифест `FMLModType: GAMELIBRARY`.
- Checkstyle подпроекта читает `CubicChunksCore/config/checkstyle/checkstyle.xml`, а не конфиг мода.
- Spotless мода намеренно не форматирует `CubicChunksCore/` и `src_old/`.

Каталоги `CubicChunksCore/buildSrc`, `gradle/` и `.github/` приехали вместе с upstream-историей. Gradle исполняет только корневой `buildSrc`. Вложенный `buildSrc` в сборке мода не участвует: классы вроде `GeneratePackageInfo` подпроект берёт из корневого `buildSrc`, сигнатура `generateFiles(SourceSet)` совпадает.

Лицензия ядра — MIT, файл `CubicChunksCore/LICENSE`. Отдельные файлы (например `Coords.java`) дополнительно содержат копирайт OpenCubicChunks 2015–2019. Лицензия мода тоже MIT (`LICENSE` в корне).

## Что мод берёт из ядра

Актуальный код в `src/main` импортирует в основном:

- `CubePos` и `Coords` — позиция куба и перевод координат;
- `CloPos` — позиция «чанк или куб» в DASM-наборах и миксинах хранилища, тикетов, сети;
- аннотации `Public` и `MethodsReturnNonnullByDefault`.

Карта высот, `ColumnCubeMap`, `SpawnPlaceFinder` и `Int3HashSet` живут в ядре и используются более старым кодом в `src_old/`. Они импортированы целиком, потому что это часть того же коммита, а не потому что текущий NeoForge-порт уже все их вызывает.

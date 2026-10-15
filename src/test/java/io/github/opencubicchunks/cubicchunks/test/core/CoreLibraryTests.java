package io.github.opencubicchunks.cubicchunks.test.core;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * E2 кладёт тесты ядра в gamelibrary, а не в папку мода: иначе пакет {@code cc_core} рвётся.
 * Jupiter сканирует только мод, поэтому классы ядра перечислены явно.
 */
@Suite
@SelectClasses({ io.github.opencubicchunks.cc_core.world.level.CloPosTest.class, io.github.opencubicchunks.cc_core.utils.CubePosTest.class,
    io.github.opencubicchunks.cc_core.chunk.util.CubePosTest.class, io.github.opencubicchunks.cc_core.utils.CoordsTest.class,
    io.github.opencubicchunks.cc_core.utils.Int3HashSetTest.class, io.github.opencubicchunks.cc_core.levelgen.heightmap.HeightmapStorageTest.class,
    io.github.opencubicchunks.cc_core.levelgen.heightmap.SurfaceTrackerBranchTest.class,
    io.github.opencubicchunks.cc_core.levelgen.heightmap.SurfaceTrackerLeafTest.class,
    io.github.opencubicchunks.cc_core.levelgen.heightmap.SurfaceTrackerNodesTest.class })
public class CoreLibraryTests {}

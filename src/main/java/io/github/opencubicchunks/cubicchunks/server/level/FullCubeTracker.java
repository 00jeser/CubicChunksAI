package io.github.opencubicchunks.cubicchunks.server.level;

import java.util.concurrent.atomic.AtomicInteger;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.CubicChunks;

/**
 * Counts {@link io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube} instances that reached status {@code full}.
 */
public final class FullCubeTracker {
    private static final AtomicInteger FULL_LEVEL_CUBES = new AtomicInteger();

    private FullCubeTracker() {}

    public static void onFull(CubePos pos) {
        int count = FULL_LEVEL_CUBES.incrementAndGet();
        CubicChunks.LOGGER.info("LevelCube full at {} count={}", pos, count);
    }

    public static int count() {
        return FULL_LEVEL_CUBES.get();
    }
}

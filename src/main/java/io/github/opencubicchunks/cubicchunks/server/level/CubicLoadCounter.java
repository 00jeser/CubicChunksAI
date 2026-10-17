package io.github.opencubicchunks.cubicchunks.server.level;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import net.minecraft.world.level.ChunkPos;

/**
 * Ключ для {@code ChunkLoadCounter}: у держателя куба колонковый {@code ChunkPos} пустой.
 * Две половины {@link CubePos#asLong()} снова собираются {@link ChunkPos#pack()} в тот же long.
 */
public final class CubicLoadCounter {
    private CubicLoadCounter() {}

    public static ChunkPos columnKey(@Nullable ChunkPos column, @Nullable CubePos cube) {
        if (column != null) {
            return column;
        }
        if (cube == null) {
            return new ChunkPos(Integer.MIN_VALUE, Integer.MIN_VALUE);
        }
        long packed = cube.asLong();
        return new ChunkPos((int) packed, (int) (packed >>> Integer.SIZE));
    }
}

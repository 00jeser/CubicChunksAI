package io.github.opencubicchunks.cubicchunks.world.level;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.Coords;
import net.minecraft.core.BlockPos;

/**
 * Vertical limit of a cubic level. Vanilla {@code minY}/{@code maxY} stay on the height accessor so section arrays and
 * light do not resize. Commands and {@code hasChunksAt} ask this instead.
 */
public final class CubicBuildHeight {
    private CubicBuildHeight() {}

    /** {@code true} when the block's cube fits in {@link CubePos} packing. */
    public static boolean containsBlockY(int blockY) {
        return CubePos.of(0, Coords.blockToCube(blockY), 0).isValid();
    }

    public static boolean contains(BlockPos pos) {
        return containsBlockY(pos.getY());
    }
}

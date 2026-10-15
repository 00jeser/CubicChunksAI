package io.github.opencubicchunks.cubicchunks.world.level;

import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.minecraft.core.BlockPos;

public interface CubicLevel extends CubicLevelAccessor {
    /**
     * Стиль этого уровня. У {@code ServerLevel} его записывает конструктор.
     * {@code CHUNK} не входит в кубовый пайплайн.
     */
    CubicLevelHeightAccessor.WorldStyle cc_worldStyle();

    LevelCube cc_getCubeAt(BlockPos blockPos);

    LevelCube cc_getCube(int x, int y, int z);
}

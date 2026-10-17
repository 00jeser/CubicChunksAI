package io.github.opencubicchunks.cubicchunks.test.server.level;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.server.level.CubicLoadCounter;
import io.github.opencubicchunks.cubicchunks.testutils.BaseTest;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

public class TestCubicLoadCounter extends BaseTest {
    @Test
    public void columnKeyKeepsARealColumnAndSeparatesCubes() {
        ChunkPos column = new ChunkPos(3, 4);
        assertThat(CubicLoadCounter.columnKey(column, CubePos.of(1, 2, 3))).isEqualTo(column);

        ChunkPos lower = CubicLoadCounter.columnKey(null, CubePos.of(-7, 0, 9));
        ChunkPos upper = CubicLoadCounter.columnKey(null, CubePos.of(-7, 1, 9));
        assertThat(lower.pack()).isEqualTo(CubePos.of(-7, 0, 9).asLong());
        assertThat(upper.pack()).isNotEqualTo(lower.pack());
    }
}

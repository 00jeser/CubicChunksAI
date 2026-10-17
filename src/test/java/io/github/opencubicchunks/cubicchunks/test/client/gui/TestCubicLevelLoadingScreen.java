package io.github.opencubicchunks.cubicchunks.test.client.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.client.gui.screens.CloPosProgress;
import io.github.opencubicchunks.cubicchunks.client.gui.screens.CubicLevelLoadingScreen;
import io.github.opencubicchunks.cubicchunks.server.level.progress.StoringCloProgressListener;
import io.github.opencubicchunks.cubicchunks.testutils.BaseTest;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.Test;

public class TestCubicLevelLoadingScreen extends BaseTest {
    @Test
    public void cloPosProgressDoesNotReadColumns() {
        ChunkLoadStatusView progress = new CloOnlyStatus();
        assertDoesNotThrow(() -> CubicLevelLoadingScreen.columnStatusCounts(progress));
        assertThat(sum(CubicLevelLoadingScreen.columnStatusCounts(progress))).isZero();
        int[] cubes = CubicLevelLoadingScreen.cubeStatusCounts(progress);
        assertThat(cubes[ChunkStatus.FULL.getIndex()]).isEqualTo(1);
        assertThat(CubicLevelLoadingScreen.columnStatus(progress, 0, 0)).isNull();
    }

    @Test
    public void columnProgressStillCountsColumns() {
        ChunkLoadStatusView progress = new ColumnStatus();
        int[] columns = CubicLevelLoadingScreen.columnStatusCounts(progress);
        assertThat(columns[ChunkStatus.NOISE.getIndex()]).isGreaterThan(0);
        assertThat(sum(CubicLevelLoadingScreen.cubeStatusCounts(progress))).isZero();
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    private static final class CloOnlyStatus implements ChunkLoadStatusView, StoringCloProgressListener, CloPosProgress {
        @Override public boolean cc_columnsAvailable() {
            return false;
        }

        @Override public void moveTo(ResourceKey<Level> dimension, ChunkPos centerChunk) {
            throw new IllegalStateException("CloPos progress has no column center");
        }

        @Override public @Nullable ChunkStatus get(int x, int z) {
            throw new IllegalStateException("CloPos progress is not a column");
        }

        @Override public int radius() {
            return 1;
        }

        @Override public @Nullable ChunkStatus cc_getStatus(int cubeX, int cubeY, int cubeZ) {
            if (cubeX == 0 && cubeY == 0 && cubeZ == 0) {
                return ChunkStatus.FULL;
            }
            return null;
        }
    }

    private static final class ColumnStatus implements ChunkLoadStatusView {
        @Override public void moveTo(ResourceKey<Level> dimension, ChunkPos centerChunk) {}

        @Override public @Nullable ChunkStatus get(int x, int z) {
            return ChunkStatus.NOISE;
        }

        @Override public int radius() {
            return 0;
        }
    }
}

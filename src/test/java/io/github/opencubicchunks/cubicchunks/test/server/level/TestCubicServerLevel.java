package io.github.opencubicchunks.cubicchunks.test.server.level;

import static io.github.opencubicchunks.cubicchunks.testutils.Misc.setupServerLevel;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.server.level.CubicServerLevel;
import io.github.opencubicchunks.cubicchunks.server.level.ServerCubeCache;
import io.github.opencubicchunks.cubicchunks.testutils.BaseTest;
import io.github.opencubicchunks.cubicchunks.testutils.CloseableReference;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * This test class is for testing {@link io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level.MixinServerLevel}
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestCubicServerLevel extends BaseTest {
    // TODO: Phase 3 - This needs a more rigorous test down the line when we actually care about entities
    @Test
    public void testVanillaSpawningAllowed() throws Exception {
        try (CloseableReference<ServerLevel> serverLevelReference = setupServerLevel()) {
            assertFalse(serverLevelReference.value().areEntitiesActuallyLoadedAndTicking(new ChunkPos(0, 0)));
        }
    }

    // TODO: Phase 3 - This is part of the neoforge API and will need a more rigorous test when we need to support their API
    @Test
    public void testVanillaInvalidateCapabilities() throws Exception {
        try (CloseableReference<ServerLevel> serverLevelReference = setupServerLevel()) {
            serverLevelReference.value().invalidateCapabilities(new ChunkPos(0, 0));
        }
    }

    // TODO: Stub.
    @Test
    public void testVanillaTickChunk() throws Exception {
        try (CloseableReference<ServerLevel> serverLevelReference = setupServerLevel()) {
            serverLevelReference.value().tickChunk(new LevelChunk(serverLevelReference.value(), new ChunkPos(0, 0)), 10);
        }
    }

    // TODO: Stub. This test hangs. Maybe due to ForcedChunksSavedData?
    @Test
    public void testVanillaSetChunkForced() throws Exception {
        try (CloseableReference<ServerLevel> serverLevelReference = setupServerLevel()) {
            serverLevelReference.value().setChunkForced(0, 0, true);
        }
    }

    // TODO: Stub.
    @Test
    public void testCubeRandomTick() throws Exception {
        try (CloseableReference<ServerLevel> serverLevelReference = setupServerLevel()) {
            ServerLevel level = serverLevelReference.value();
            LevelCube cube = new LevelCube(level, CubePos.of(0, 1, 0));
            ((CubicServerLevel) level).cc_tickCube(cube, 4);
        }
    }

    @Test
    public void testCubeLocalDifficulty() throws Exception {
        try (CloseableReference<ServerLevel> serverLevelReference = setupServerLevel()) {
            ServerLevel level = spy(serverLevelReference.value());
            // The test level's registry and environment attributes are deep stubs. Clock and moon stay fixed so inhabited time is the only input.
            doReturn(Difficulty.NORMAL).when(level).getDifficulty();
            doReturn(0L).when(level).getOverworldClockTime();
            doReturn(0.0F).when(level).getMoonBrightness(any());
            var cube = ((ServerCubeCache) level.getChunkSource()).cc_getCube(0, 0, 0, ChunkStatus.FULL, true);
            assertInstanceOf(LevelCube.class, cube);
            long inhabited = 3_600_000L;
            cube.setInhabitedTime(0L);
            float fresh = level.getCurrentDifficultyAt(BlockPos.ZERO).getEffectiveDifficulty();
            cube.setInhabitedTime(inhabited);
            float lived = level.getCurrentDifficultyAt(BlockPos.ZERO).getEffectiveDifficulty();
            assertTrue(lived > fresh);
        }
    }

    @Test
    public void testVanillaIsPositionEntityTicking() throws Exception {
        try (CloseableReference<ServerLevel> serverLevelReference = setupServerLevel()) {
            assertFalse(serverLevelReference.value().isPositionEntityTicking(BlockPos.ZERO));
        }
    }
}

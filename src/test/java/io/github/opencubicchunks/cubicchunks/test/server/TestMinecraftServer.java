package io.github.opencubicchunks.cubicchunks.test.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.testutils.BaseTest;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * A live {@code IntegratedServer} still cannot be constructed in this harness. Spawn search itself is covered by
 * {@code IntegrationTestServerCubeCache.spawnSearchTicketKeepsCubeY}. These tests lock the cube coordinates that
 * {@code MixinPlayerSpawnFinder} and {@code MixinMinecraftServer.prepareLevels} use.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestMinecraftServer extends BaseTest {
    @Test
    public void testSetInitialSpawnVanilla() {
        BlockPos suggestion = new BlockPos(16, 40, -16);
        ChunkPos chunk = new ChunkPos(net.minecraft.core.SectionPos.blockToSectionCoord(suggestion.getX()),
                net.minecraft.core.SectionPos.blockToSectionCoord(suggestion.getZ()));
        CloPos cube = CloPos.cube(Coords.sectionToCube(chunk.x()), Coords.blockToCube(suggestion.getY()), Coords.sectionToCube(chunk.z()));
        assertTrue(cube.isCube());
        assertEquals(Coords.blockToCube(suggestion.getY()), cube.getY());
    }

    @Test
    void testPrepareLevelsVanilla() {
        int sectionRadius = 10;
        int cubeRadius = Coords.sectionToCubeCeil(sectionRadius);
        int cubeDiameter = cubeRadius * 2 + 1;
        int chunkDiameter = cubeDiameter * CubicConstants.DIAMETER_IN_SECTIONS;
        int expected = cubeDiameter * cubeDiameter * cubeDiameter + chunkDiameter * chunkDiameter;
        assertTrue(expected > sectionRadius * 2);
    }
}

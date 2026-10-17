package io.github.opencubicchunks.cubicchunks.integrationtest.server.level;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.mixin.test.common.server.level.ChunkMapTestAccess;
import io.github.opencubicchunks.cubicchunks.mixin.test.common.server.level.GenerationChunkHolderTestAccess;
import io.github.opencubicchunks.cubicchunks.server.level.CloHolder;
import io.github.opencubicchunks.cubicchunks.server.level.CubeLevel;
import io.github.opencubicchunks.cubicchunks.server.level.ServerCubeCache;
import io.github.opencubicchunks.cubicchunks.testutils.BaseTest;
import io.github.opencubicchunks.cubicchunks.testutils.CloseableReference;
import io.github.opencubicchunks.cubicchunks.testutils.DummyChunkProgressListener;
import io.github.opencubicchunks.cubicchunks.testutils.Misc;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mockito;

public class IntegrationTestCubicChunkMap extends BaseTest {
    private CloseableReference<ServerChunkCache> createServerChunkCache(boolean vanillaTest, RegistryAccess registryAccess)
            throws IOException, NoSuchFieldException, IllegalAccessException {
        HolderGetter<Biome> biome = registryAccess.lookupOrThrow(Registries.BIOME);
        HolderGetter<StructureSet> structureSet = registryAccess.lookupOrThrow(Registries.STRUCTURE_SET);
        HolderGetter<PlacedFeature> placedFeature = registryAccess.lookupOrThrow(Registries.PLACED_FEATURE);
        ChunkGenerator flatLevelSource = new FlatLevelSource(FlatLevelGeneratorSettings.getDefault(biome, structureSet, placedFeature));

        // Distance manager is responsible for updating chunk levels; we do this manually for testing
        var distanceManagerMockedConstruction = Mockito.mockConstruction(ChunkMap.DistanceManager.class,
                withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS));
        ServerLevel serverLevelMock;
        try (var ignored = Mockito.mockConstruction(ServerChunkCache.class, withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS))) {
            // Server level
            serverLevelMock = mock(withSettings().defaultAnswer(Mockito.RETURNS_DEEP_STUBS).extraInterfaces(CanBeCubic.class));
        }
        if (!vanillaTest) {
            var f = serverLevelMock.getClass().getSuperclass().getSuperclass().getDeclaredField("cc_isCubic");
            f.setAccessible(true);
            f.set(serverLevelMock, true);
            when(((CanBeCubic) serverLevelMock).cc_isCubic()).thenReturn(true);
        }
        when(serverLevelMock.getHeight()).thenReturn(384);
        when(serverLevelMock.getSectionsCount()).thenReturn(24);
        when(serverLevelMock.registryAccess()).thenReturn(registryAccess);
        // We seem to need an actual directory, not a mock
        LevelStorageSource.LevelStorageAccess levelStorageAccessMock = mock(Mockito.RETURNS_DEEP_STUBS);
        when(levelStorageAccessMock.getDimensionPath(any())).thenReturn(Files.createTempDirectory("cc_test"));
        var serverChunkCache = new ServerChunkCache(serverLevelMock, levelStorageAccessMock, mock(Mockito.RETURNS_DEEP_STUBS),
                mock(Mockito.RETURNS_DEEP_STUBS),
                // We run everything on the main thread as Mockito has race conditions when multiple threads call into it
                // (which occurs when using RETURNS_DEEP_STUBS)
                Runnable::run, flatLevelSource, 10, // server view distance
                10, // simulation distance
                false, // sync - not relevant for tests; false should be faster
                new DummyChunkProgressListener(), mock(Mockito.RETURNS_DEEP_STUBS));
        var f = serverLevelMock.getClass().getSuperclass().getDeclaredField("chunkSource");
        f.setAccessible(true);
        f.set(serverLevelMock, serverChunkCache);
        when(serverLevelMock.getChunkSource()).thenReturn(serverChunkCache);
        return new CloseableReference<>(serverChunkCache, distanceManagerMockedConstruction);
    }

    /**
     * Load a single chunk at full status
     */
    @ExtendWith(EphemeralTestServerProvider.class)
    @Test
    public void singleFullChunkVanilla(MinecraftServer server) throws Exception {
        try (var serverChunkCacheRef = createServerChunkCache(false, server.registryAccess())) {
            var serverChunkCache = serverChunkCacheRef.value();
            var chunkMap = serverChunkCache.chunkMap;

            var centerLevel = ChunkLevel.byStatus(ChunkStatus.FULL);

            var radius = ChunkLevel.MAX_LEVEL - centerLevel;

            ChunkHolder centerHolder = null;

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    var holder = ((ChunkMapTestAccess) chunkMap).invokeUpdateChunkScheduling(ChunkPos.pack(x, z),
                            centerLevel + Math.max(Math.abs(x), Math.abs(z)), null, ChunkLevel.MAX_LEVEL + 1);
                    ((GenerationChunkHolderTestAccess) holder).invokeUpdateHighestAllowedStatus(chunkMap);
                    if (x == 0 && z == 0) {
                        centerHolder = holder;
                    }
                }
            }

            var future = chunkMap.prepareAccessibleChunk(centerHolder);

            while (!(future.isDone() || future.isCompletedExceptionally())) {
                serverChunkCache.pollTask();
            }
            var result = future.get();
            assertTrue(result.isSuccess(), () -> "Full chunk future ChunkResult should be successful, but was " + result.getError());
            assertInstanceOf(LevelChunk.class, result.orElse(null));
        }
    }

    /**
     * Load a single chunk at full status
     */
    @ExtendWith(EphemeralTestServerProvider.class)
    @Test
    public void singleFullChunk(MinecraftServer server) throws Exception {
        try (var serverChunkCacheRef = createServerChunkCache(false, server.registryAccess())) {
            var serverChunkCache = serverChunkCacheRef.value();
            var chunkMap = serverChunkCache.chunkMap;

            var centerLevel = ChunkLevel.byStatus(ChunkStatus.FULL);

            var radius = ChunkLevel.MAX_LEVEL - centerLevel;

            ChunkHolder centerHolder = null;

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    var holder = ((ChunkMapTestAccess) chunkMap).invokeUpdateChunkScheduling(ChunkPos.pack(x, z),
                            centerLevel + Math.max(Math.abs(x), Math.abs(z)), null, ChunkLevel.MAX_LEVEL + 1);
                    ((GenerationChunkHolderTestAccess) holder).invokeUpdateHighestAllowedStatus(chunkMap);
                    if (x == 0 && z == 0) {
                        centerHolder = holder;
                    }
                }
            }

            var future = chunkMap.prepareAccessibleChunk(centerHolder);

            while (!(future.isDone() || future.isCompletedExceptionally())) {
                serverChunkCache.pollTask();
            }
            var result = future.get();
            assertTrue(result.isSuccess(), () -> "Full chunk future ChunkResult should be successful, but was " + result.getError());
            assertInstanceOf(LevelChunk.class, result.orElse(null));
        }
    }

    /**
     * One cube at {@link ChunkStatus#FULL}, plus the columns generation actually created.
     * Pre-scheduling every cube out to {@link CubeLevel#MAX_LEVEL} fills the 2GB test heap once those cubes store blocks.
     */
    @Test
    public void singleFullCube() throws Exception {
        var randomState = Mockito.mockStatic(RandomState.class, withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS));
        try (randomState; var serverChunkCacheRef = createNoiseCubeCache()) {
            var serverChunkCache = serverChunkCacheRef.value();
            var chunkMap = serverChunkCache.chunkMap;
            var cube = ((ServerCubeCache) serverChunkCache).cc_getCube(0, 0, 0, ChunkStatus.FULL, true);
            assertInstanceOf(LevelCube.class, cube);
            assertTrue(cube.getPersistedStatus().isOrAfter(ChunkStatus.FULL));
            for (int sectionZ = 0; sectionZ < CubicConstants.DIAMETER_IN_SECTIONS; sectionZ++) {
                for (int sectionX = 0; sectionX < CubicConstants.DIAMETER_IN_SECTIONS; sectionX++) {
                    var holder = chunkMap.getVisibleChunkIfPresent(ChunkPos.pack(sectionX, sectionZ));
                    int finalSectionX = sectionX;
                    int finalSectionZ = sectionZ;
                    assertTrue(holder != null && holder.getPersistedStatus().isOrAfter(ChunkStatus.FULL),
                            () -> "Chunks intersecting the center cube should be at full status, but " + finalSectionX + ", " + finalSectionZ
                                    + " has status " + (holder == null ? "missing" : holder.getPersistedStatus()));
                }
            }
            assertChunkCubeLoadOrder(chunkMap, new HashMap<>(), new ArrayList<>());
        }
    }

    private CloseableReference<ServerChunkCache> createNoiseCubeCache() throws IOException, NoSuchFieldException, IllegalAccessException {
        NoiseBasedChunkGenerator generator = mock(NoiseBasedChunkGenerator.class);
        when(generator.generatorSettings()).thenReturn(mock());
        ServerLevel serverLevelMock;
        try (var ignored = Mockito.mockConstruction(ServerChunkCache.class, withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS))) {
            serverLevelMock = mock(withSettings().defaultAnswer(Mockito.RETURNS_DEEP_STUBS).extraInterfaces(CanBeCubic.class));
        }
        var cubicFlag = serverLevelMock.getClass().getSuperclass().getSuperclass().getDeclaredField("cc_isCubic");
        cubicFlag.setAccessible(true);
        cubicFlag.set(serverLevelMock, true);
        when(((CanBeCubic) serverLevelMock).cc_isCubic()).thenReturn(true);
        var blockPalette = Misc.palettedContainerFactory();
        when(serverLevelMock.palettedContainerFactory()).thenReturn(blockPalette);
        when(serverLevelMock.getHeight()).thenReturn(384);
        when(serverLevelMock.getSectionsCount()).thenReturn(24);
        LevelStorageSource.LevelStorageAccess levelStorageAccessMock = mock(Mockito.RETURNS_DEEP_STUBS);
        when(levelStorageAccessMock.getDimensionPath(any())).thenReturn(Files.createTempDirectory("cc_test"));
        var serverChunkCache = new ServerChunkCache(serverLevelMock, levelStorageAccessMock, mock(Mockito.RETURNS_DEEP_STUBS),
                mock(Mockito.RETURNS_DEEP_STUBS), Runnable::run, generator, 10, 10, false, (pos, status) -> {}, mock(Mockito.RETURNS_DEEP_STUBS));
        var chunkSource = serverLevelMock.getClass().getSuperclass().getDeclaredField("chunkSource");
        chunkSource.setAccessible(true);
        chunkSource.set(serverLevelMock, serverChunkCache);
        when(serverLevelMock.getChunkSource()).thenReturn(serverChunkCache);
        return new CloseableReference<>(serverChunkCache);
    }

    private static void assertChunkCubeLoadOrder(ChunkMap chunkMap, Map<CloPos, List<ChunkHolder>> chunksByCubeColumn, List<ChunkHolder> cubes) {
        Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleCloMap = ((ChunkMapTestAccess) chunkMap).visibleChunkMap();
        chunksByCubeColumn.clear();
        cubes.clear();

        // collect cube and chunk holders
        visibleCloMap.forEach((cloPosLong, cloHolder) -> {
            CloPos cloPos = ((CloHolder) cloHolder).cc_getCloPos();
            if (cloPos.isChunk()) {
                chunksByCubeColumn.computeIfAbsent(cloPos.correspondingCubeCloPos(0), p -> new ArrayList<>()).add(cloHolder);
            } else {
                cubes.add(cloHolder);
            }
        });

        // For each cube assert that its chunks exist and are of sufficient status
        cubes.forEach(cubeHolder -> {
            CloPos cubeCloPos = ((CloHolder) cubeHolder).cc_getCloPos();
            List<ChunkHolder> chunksInCubeColumn = chunksByCubeColumn.get(cubeCloPos.correspondingCubeCloPos(0));
            assertNotNull(chunksInCubeColumn, () -> "cube " + cubeCloPos + " has no column chunks");
            chunksInCubeColumn.forEach(chunkHolder -> assertChunkHolderValidForCubeHolder(chunkHolder, cubeHolder));
        });
    }

    public static void assertChunkHolderValidForCubeHolder(ChunkHolder chunkHolder, ChunkHolder cubeHolder) {
        ChunkStatus cubeStatus = cubeHolder.getPersistedStatus();
        ChunkStatus chunkStatus = chunkHolder.getPersistedStatus();

        // if chunk status is null, cube status must also be null.
        if (chunkStatus == null) {
            assertNull(cubeStatus, () -> String.format("Chunk (%s) has status null is lower than cube (%s) at status %s",
                    ((CloHolder) chunkHolder).cc_getCloPos(), ((CloHolder) cubeHolder).cc_getCloPos(), cubeStatus));
            return;
        }

        // if the cube status is null, any value for the chunk status is valid.
        if (cubeStatus == null) {
            return;
        }

        // Neither are null, assert that statuses are valid.
        assertTrue(chunkStatus.isOrAfter(cubeStatus), () -> String.format("Chunk (%s) at status %s is lower than cube %s at status %s",
                ((CloHolder) chunkHolder).cc_getCloPos(), chunkStatus, ((CloHolder) cubeHolder).cc_getCloPos(), cubeStatus));
    }
}

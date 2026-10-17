package io.github.opencubicchunks.cubicchunks.testutils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import com.mojang.serialization.Lifecycle;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

// TODO maybe split things off of this class instead of dumping everything in here?
public class Misc {
    private static PalettedContainerFactory palettedContainerFactory;

    private Misc() {}

    /**
     * E4: секции куба строятся из {@link PalettedContainerFactory}, а биомы больше не лежат в {@code BuiltInRegistries}.
     * Для set/get блоков достаточно одного держателя биома и настоящей палитры состояний.
     */
    public static PalettedContainerFactory palettedContainerFactory() {
        if (palettedContainerFactory == null) {
            Strategy<BlockState> blockStates = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
            BlockState air = Blocks.AIR.defaultBlockState();
            MappedRegistry<Biome> biomes = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
            ResourceKey<Biome> plains = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("minecraft", "plains"));
            Holder.Reference<Biome> holder = Registry.registerForHolder(biomes, plains, mock(Biome.class));
            Strategy<Holder<Biome>> biomeStrategy = Strategy.createForBiomes(biomes.asHolderIdMap());
            palettedContainerFactory = new PalettedContainerFactory(blockStates, air, PalettedContainer.codecRW(BlockState.CODEC, blockStates, air),
                    biomeStrategy, holder, PalettedContainer.codecRO(biomes.holderByNameCodec(), biomeStrategy, holder));
        }
        return palettedContainerFactory;
    }

    public static int chebyshevDistance(Vec3i a, Vec3i b) {
        return Math.max(Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())), Math.abs(a.getZ() - b.getZ()));
    }

    public static int chebyshevDistance(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.z() - b.z()));
    }

    public static CloseableReference<ServerLevel> setupServerLevel() {
        try {
            return setupServerLevel(Files.createTempDirectory("cc_test"), null);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @param dimension измерение уровня. {@code null} — прежняя заглушка без настоящего id (один путь оверворлда).
     */
    public static CloseableReference<ServerLevel> setupServerLevel(Path worldRoot, @Nullable ResourceKey<Level> dimension) {
        MockedStatic<RandomState> randomStateMockedStatic = mockRandomState();
        try {
            return new CloseableReference<>(newServerLevel(worldRoot, dimension), randomStateMockedStatic);
        } catch (RuntimeException | Error ex) {
            randomStateMockedStatic.close();
            throw ex;
        }
    }

    /**
     * Несколько измерений одного мира. Один мок {@link RandomState} на все уровни: два статических мока в одном потоке нельзя.
     */
    public static CloseableReference<List<ServerLevel>> setupServerLevels(Path worldRoot, List<ResourceKey<Level>> dimensions) {
        MockedStatic<RandomState> randomStateMockedStatic = mockRandomState();
        try {
            List<ServerLevel> levels = new ArrayList<>();
            for (ResourceKey<Level> dimension : dimensions) {
                levels.add(newServerLevel(worldRoot, dimension));
            }
            return new CloseableReference<>(levels, randomStateMockedStatic);
        } catch (RuntimeException | Error ex) {
            randomStateMockedStatic.close();
            throw ex;
        }
    }

    private static MockedStatic<RandomState> mockRandomState() {
        return Mockito.mockStatic(RandomState.class, withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS));
    }

    private static ServerLevel newServerLevel(Path worldRoot, @Nullable ResourceKey<Level> dimension) {
        ChunkGenerator noiseBasedChunkGeneratorMock = mock(ChunkGenerator.class, withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS));
        when(noiseBasedChunkGeneratorMock.createBiomes(any(), any(), any(), any()))
                .thenAnswer(i -> CompletableFuture.completedFuture(i.getArguments()[4]));
        when(noiseBasedChunkGeneratorMock.fillFromNoise(any(), any(), any(), any()))
                .thenAnswer(i -> CompletableFuture.completedFuture(i.getArguments()[4]));
        LevelStem levelStemMock = mock(Mockito.RETURNS_DEEP_STUBS);
        when(levelStemMock.type().value().height()).thenReturn(384);
        LevelStorageSource.LevelStorageAccess levelStorageAccessMock = mock(Mockito.RETURNS_DEEP_STUBS);
        if (dimension == null) {
            when(levelStorageAccessMock.getDimensionPath(any()))
                    .thenReturn(worldRoot.resolve("dimensions").resolve("minecraft").resolve("overworld"));
        } else {
            when(levelStorageAccessMock.getDimensionPath(any())).thenAnswer(invocation -> {
                ResourceKey<Level> key = invocation.getArgument(0);
                Identifier id = key.identifier();
                return worldRoot.resolve("dimensions").resolve(id.getNamespace()).resolve(id.getPath());
            });
        }
        when(levelStorageAccessMock.getLevelPath(any(LevelResource.class)))
                .thenAnswer(invocation -> worldRoot.resolve(invocation.<LevelResource>getArgument(0).id()));
        ResourceKey<Level> dimensionKey = dimension == null ? mock(RETURNS_DEEP_STUBS) : dimension;
        return new ServerLevel(mock(RETURNS_DEEP_STUBS),
                // We run everything on the main thread as Mockito has race conditions when multiple threads call into it
                // (which occurs when using RETURNS_DEEP_STUBS)
                Runnable::run, levelStorageAccessMock, mock(RETURNS_DEEP_STUBS), dimensionKey, levelStemMock,
                // E4: ChunkProgressListener больше не аргумент конструктора ServerLevel.
                false, 0, List.of(), false);
    }

    /**
     * Generate a LevelCube filled with random stone and dirt blocks
     */
    public static LevelCube generateRandomLevelCube(Level level, CubePos pos, Random random) {
        var factory = palettedContainerFactory();
        when(level.palettedContainerFactory()).thenReturn(factory);
        var cube = new LevelCube(level, pos);
        for (int i = 0; i < CubicConstants.SECTION_COUNT; i++) {
            var section = cube.getSection(i);
            for (int x = 0; x < CubicConstants.SECTION_DIAMETER; x++) {
                for (int y = 0; y < CubicConstants.SECTION_DIAMETER; y++) {
                    for (int z = 0; z < CubicConstants.SECTION_DIAMETER; z++) {
                        if (random.nextBoolean()) {
                            section.setBlockState(x, y, z, random.nextBoolean() ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState(),
                                    false);
                        }
                    }
                }
            }
        }
        return cube;
    }

    public static <T> void assertDeepEquals(@Nullable T actual, @Nullable T expected) {
        assertThat(actual).usingRecursiveComparison().usingOverriddenEquals().isEqualTo(expected);
    }
}

package io.github.opencubicchunks.cubicchunks.mixin.core.client.multiplayer;

import static io.github.opencubicchunks.cc_core.CubicChunksBase.LOGGER;
import static io.github.opencubicchunks.cc_core.utils.Coords.cubeToSection;
import static io.github.opencubicchunks.cc_core.utils.Coords.sectionToCubeCeil;

import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import io.github.notstirred.dasm.api.annotations.Dasm;
import io.github.notstirred.dasm.api.annotations.redirect.redirects.AddFieldToSets;
import io.github.notstirred.dasm.api.annotations.selector.Ref;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.client.multiplayer.ClientCubeCache;
import io.github.opencubicchunks.cubicchunks.client.multiplayer.CubicClientLevel;
import io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.chunk.MixinChunkSource;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCubeSet;
import io.github.opencubicchunks.cubicchunks.world.level.cube.EmptyLevelCube;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The vanilla {@link ClientChunkCache} class stores all loaded chunks on the client and has methods to update and unload them, as well as change the
 * center and range of the chunk storage. This mixin adds versions of these methods for cubes, meaning that this class now stores both cubes and
 * chunks.
 */
@Dasm(value = ChunkToCubeSet.class, target = @Ref(ClientChunkCache.class))
@Mixin(ClientChunkCache.class)
public abstract class MixinClientChunkCache extends MixinChunkSource implements ClientCubeCache {
    @AddFieldToSets(containers = ChunkToCubeSet.ClientChunkCache_redirects.class, field = "storage:Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;")
    volatile ClientCubeCache.Storage cc_cubeStorage;

    private LevelCube cc_emptyCube;

    @Shadow @Final ClientLevel level;

    /**
     * Initialize cube storage and the empty cube if the level is cubic
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void cc_onConstruct(ClientLevel level, int viewDistance, CallbackInfo ci) {
        if (((CanBeCubic) level).cc_isCubic()) {
            cc_emptyCube = new EmptyLevelCube(level, CubePos.of(0, 0, 0),
                    level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS));
            int cubeRadius = cc_cubeStorageRadius(viewDistance);
            cc_cubeStorage = new ClientCubeCache.Storage(cubeRadius, level);
            // TODO we could redirect the initial construction instead of immediately resizing. doesn't really matter
            updateViewRadius(ClientCubeCache.chunkViewDistanceCovering(cubeRadius));
        }
    }

    private static boolean cc_isValidCube(@Nullable LevelCube chunk, int x, int y, int z) {
        if (chunk == null) {
            return false;
        } else {
            CubePos cubePos = chunk.cc_getCloPos().cubePos();
            return cubePos.getX() == x && cubePos.getY() == y && cubePos.getZ() == z;
        }
    }

    @Override public void cc_drop(CubePos chunkPos) {
        if (this.cc_cubeStorage.inRange(chunkPos.getX(), chunkPos.getY(), chunkPos.getZ())) {
            int i = this.cc_cubeStorage.getIndex(chunkPos.getX(), chunkPos.getY(), chunkPos.getZ());
            LevelCube levelCube = this.cc_cubeStorage.getChunk(i);
            if (cc_isValidCube(levelCube, chunkPos.getX(), chunkPos.getY(), chunkPos.getZ())) {
                // TODO event hook
//                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkEvent.Unload(levelCube));
                this.cc_cubeStorage.drop(i, levelCube);
            }
        }
    }

    @Override public LevelCube cc_cubeForMesh(int cubeX, int cubeY, int cubeZ) {
        LevelCube cached = ClientCubeCache.super.cc_cubeForMesh(cubeX, cubeY, cubeZ);
        return cached != null ? cached : this.cc_emptyCube;
    }

    @Override public @Nullable LevelCube cc_getCube(int chunkX, int chunkY, int chunkZ, ChunkStatus requiredStatus, boolean load) {
        if (this.cc_cubeStorage.inRange(chunkX, chunkY, chunkZ)) {
            LevelCube levelCube = this.cc_cubeStorage.getChunk(this.cc_cubeStorage.getIndex(chunkX, chunkY, chunkZ));
            if (cc_isValidCube(levelCube, chunkX, chunkY, chunkZ)) {
                return levelCube;
            }
        }

        return load ? this.cc_emptyCube : null;
    }

    @Override public void cc_replaceBiomes(int x, int y, int z, FriendlyByteBuf buffer) {
        if (true) {
            // TODO (P2)
            throw new UnsupportedOperationException("don't remove this exception until packet integration tests are added for this method");
        }
        if (!this.cc_cubeStorage.inRange(x, y, z)) {
            LOGGER.warn("Ignoring cube since it's not in the view range: {}, {}, {}", x, y, z);
        } else {
            int i = this.cc_cubeStorage.getIndex(x, y, z);
            LevelCube levelCube = this.cc_cubeStorage.chunks.get(i);
            if (!cc_isValidCube(levelCube, x, y, z)) {
                LOGGER.warn("Ignoring cube since it's not present: {}, {}, {}", x, y, z);
            } else {
                levelCube.replaceBiomes(buffer);
            }
        }
    }

    @Override public @Nullable LevelCube cc_replaceWithPacketData(
            int x, int y, int z, FriendlyByteBuf buffer, Map<Heightmap.Types, long[]> map,
            Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer
    ) {
        if (!this.cc_cubeStorage.inRange(x, y, z)) {
            LOGGER.warn("Ignoring cube since it's not in the view range: {}, {}, {}", x, y, z);
            return null;
        } else {
            int i = this.cc_cubeStorage.getIndex(x, y, z);
            LevelCube levelCube = this.cc_cubeStorage.chunks.get(i);
            CubePos cubePos = CubePos.of(x, y, z);
            if (!cc_isValidCube(levelCube, x, y, z)) {
                levelCube = new LevelCube(this.level, cubePos);
                levelCube.replaceWithPacketData(buffer, map, consumer);
                this.cc_cubeStorage.replace(i, levelCube);
            } else {
                levelCube.replaceWithPacketData(buffer, map, consumer);
                this.cc_cubeStorage.refreshEmptySections(levelCube);
            }

            ((CubicClientLevel) this.level).cc_onCubeLoaded(cubePos);
            // TODO event hook
//            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkEvent.Load(levelCube, false));
            return levelCube;
        }
    }

    @Shadow public abstract void updateViewCenter(int x, int z);

    @Override public void cc_updateViewCenter(int x, int y, int z) {
        this.cc_cubeStorage.setViewCenter(x, y, z);
        this.updateViewCenter(cubeToSection(x, 0), cubeToSection(z, 0));
    }

    @Shadow public abstract void updateViewRadius(int viewDistance);

    @Override public void cc_updateViewRadius(int viewDistance) {
        int i = this.cc_cubeStorage.cubeRadius;
        int j = cc_cubeStorageRadius(viewDistance);
        if (i != j) {
            ClientCubeCache.Storage storage = new ClientCubeCache.Storage(j, this.level);
            storage.viewCenterX = this.cc_cubeStorage.viewCenterX;
            storage.viewCenterY = this.cc_cubeStorage.viewCenterY;
            storage.viewCenterZ = this.cc_cubeStorage.viewCenterZ;

            for (int k = 0; k < this.cc_cubeStorage.chunks.length(); ++k) {
                LevelCube levelCube = this.cc_cubeStorage.chunks.get(k);
                if (levelCube != null) {
                    CubePos cubePos = levelCube.cc_getCloPos().cubePos();
                    if (storage.inRange(cubePos.getX(), cubePos.getY(), cubePos.getZ())) {
                        storage.replace(storage.getIndex(cubePos.getX(), cubePos.getY(), cubePos.getZ()), levelCube);
                    }
                }
            }
            this.cc_cubeStorage = storage;
        }
        updateViewRadius(ClientCubeCache.chunkViewDistanceCovering(j));
    }

    @Shadow private static int calculateStorageRange(int viewDistance) {
        throw new IllegalStateException("mixin failed to apply");
    }

    /**
     * Чебышёвский радиус на все оси: большее из ванильной дистанции просмотра и {@code verticalViewDistance}. Пакет центра радиус не несёт.
     */
    private static int cc_cubeStorageRadius(int horizontalViewDistance) {
        int horizontalRadius = calculateStorageRange(horizontalViewDistance);
        int verticalRadius = calculateStorageRange(sectionToCubeCeil(CubicChunks.config().getVerticalViewDistance()));
        return Math.max(horizontalRadius, verticalRadius);
    }

    // TODO gatherStats (only used for debug)

    @Override public int cc_getLoadedCubeCount() {
        return this.cc_cubeStorage.chunkCount;
    }
}

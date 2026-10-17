package io.github.opencubicchunks.cubicchunks.mixin.core.client.multiplayer;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor.WorldStyle;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.client.multiplayer.ClientCubeCache;
import io.github.opencubicchunks.cubicchunks.client.multiplayer.CubicClientLevel;
import io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.MixinLevel;
import io.github.opencubicchunks.cubicchunks.network.ClientWorldStyle;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel extends MixinLevel implements CubicClientLevel {
    @Shadow @Final private ClientChunkCache chunkSource;

    // После Level.<init> поля миксина уже стоят, а ClientChunkCache ещё не читал cc_isCubic.
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;<init>(Lnet/minecraft/world/level/storage/WritableLevelData;"
            + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/core/Holder;ZZJI)V", shift = At.Shift.AFTER))
    private void cc_onInit(
            ClientPacketListener connection, ClientLevel.ClientLevelData levelData, ResourceKey<Level> dimension, Holder<DimensionType> dimensionType,
            int viewDistance, int serverSimulationDistance, LevelRenderer levelRenderer, boolean isDebug, long biomeZoomSeed, int seaLevel,
            CallbackInfo ci
    ) {
        WorldStyle style = ClientWorldStyle.take();
        this.cc_worldStyle = style;
        if (style != WorldStyle.CHUNK) {
            this.cc_setCubic();
        }
        CubicChunks.LOGGER.info("client dimension={} style={} cubic={}", dimension.identifier(), style.name(), style != WorldStyle.CHUNK);
    }

    @Override public boolean cc_hasCube(int cubeX, int cubeY, int cubeZ) {
        return true;
    }

    // TODO should eventually be DASM once BlockTintCache and entity storage are actually done in CC
    public void cc_onCubeLoaded(CubePos cubePos) {
//        this.tintCaches.forEach((p_194154_, p_194155_) -> p_194155_.invalidateForChunk(chunkPos.x, chunkPos.z));
//        this.entityStorage.startTicking(chunkPos);
    }

    @Override public ClientCubeCache cc_getCubeSource() {
        return ((ClientCubeCache) this.chunkSource);
    }

    // TODO: comments below don't account for 1.20.4->1.21.5 changes; will need to check for other methods that need CC changes

    // unload
    // TODO: Phase 2 - this interacts with the lighting engine and will need to change to support cubes

    // onCubeLoaded
    // TODO: Phase 3 - we will need to interact with entityStorage correctly at a per-cube level
}

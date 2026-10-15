package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import java.util.List;
import java.util.concurrent.Executor;

import io.github.notstirred.dasm.api.annotations.Dasm;
import io.github.notstirred.dasm.api.annotations.redirect.redirects.AddMethodToSets;
import io.github.notstirred.dasm.api.annotations.redirect.redirects.AddTransformToSets;
import io.github.notstirred.dasm.api.annotations.selector.Ref;
import io.github.notstirred.dasm.api.annotations.transform.TransformFromMethod;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor.WorldStyle;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.config.ServerConfig;
import io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.MixinLevel;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCloSet;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCubeSet;
import io.github.opencubicchunks.cubicchunks.server.level.CubicServerLevel;
import io.github.opencubicchunks.cubicchunks.server.level.ServerCubeCache;
import io.github.opencubicchunks.cubicchunks.world.level.CubicWorldStyleData;
import io.github.opencubicchunks.cubicchunks.world.level.WorldStyleSelector;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.LevelClo;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeSource;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Dasm(value = ChunkToCloSet.class, target = @Ref(ServerLevel.class))
@Mixin(ServerLevel.class)
public abstract class MixinServerLevel extends MixinLevel implements CubicServerLevel {
    @Shadow @Final private ServerChunkCache chunkSource;

    // После super(): инициализаторы полей MixinLevel уже отработали, а ServerChunkCache ещё не читал cc_isCubic.
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;<init>(Lnet/minecraft/world/level/storage/WritableLevelData;"
            + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/core/Holder;ZZJI)V", shift = At.Shift.AFTER))
    private void cc_onInit(
            MinecraftServer server, Executor dispatcher, LevelStorageSource.LevelStorageAccess levelStorageAccess, ServerLevelData serverLevelData,
            ResourceKey<Level> dimension, LevelStem levelStem, boolean isDebug, long biomeZoomSeed, List customSpawners, boolean tickTime,
            CallbackInfo ci
    ) {
        var dimensionPath = levelStorageAccess.getDimensionPath(dimension);
        WorldStyle saved = CubicWorldStyleData.read(dimensionPath);
        ServerConfig serverConfig = ServerConfig.load(levelStorageAccess.getLevelPath(LevelResource.ROOT));
        boolean generateNewWorldsAsCC = CubicChunks.config().shouldGenerateNewWorldsAsCC();
        WorldStyle style = WorldStyleSelector.select(saved, generateNewWorldsAsCC, serverConfig, dimension);
        if (saved == null) {
            CubicWorldStyleData.write(dimensionPath, style);
        }
        this.cc_worldStyle = style;
        if (style != WorldStyle.CHUNK) {
            this.cc_setCubic();
        }
        CubicChunks.LOGGER.info("dimension={} style={} cubic={}", dimension.identifier(), style.name(), style != WorldStyle.CHUNK);
    }

    @Override public ServerCubeCache cc_getCubeSource() {
        return ((ServerCubeCache) this.chunkSource);
    }

    @AddTransformToSets(ChunkToCloSet.ServerLevel_redirects.class)
    @TransformFromMethod("startTickingChunk(Lnet/minecraft/world/level/chunk/LevelChunk;)V")
    public native void cc_startTickingClo(LevelClo chunk);

    @AddMethodToSets(containers = ChunkToCloSet.ServerLevel_redirects.class, method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V")
    public void cc_tickClo(LevelClo levelClo, int randomTickSpeed) {
        if (levelClo instanceof LevelCube levelCube) {
            cc_tickCube(levelCube, randomTickSpeed);
        } else {
            // TODO (P2) chunk ticking for anything that still needs to happen on chunks (probably just the forge event for mods?)
        }
    }

    /**
     * Random block and fluid ticks over the cube's sections. Precipitation stays out: it reads heightmaps (E9). Block entities, scheduled ticks
     * and POI stay in E16. {@link LevelCube#registerTickContainerInLevel} stays empty for the same reason.
     */
    @AddMethodToSets(containers = ChunkToCubeSet.ServerLevel_redirects.class, method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V")
    public void cc_tickCube(LevelCube levelCube, int randomTickSpeed) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("tickBlocks");
        if (randomTickSpeed > 0) {
            LevelChunkSection[] sections = levelCube.getSections();
            CubePos cubePos = levelCube.cc_getCubePos();
            ServerLevel level = (ServerLevel) (Object) this;
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (!section.isRandomlyTicking()) {
                    continue;
                }
                int sectionX = Coords.cubeToSection(cubePos.getX(), Coords.indexToX(sectionIndex));
                int sectionY = Coords.cubeToSection(cubePos.getY(), Coords.indexToY(sectionIndex));
                int sectionZ = Coords.cubeToSection(cubePos.getZ(), Coords.indexToZ(sectionIndex));
                int minX = SectionPos.sectionToBlockCoord(sectionX);
                int minY = SectionPos.sectionToBlockCoord(sectionY);
                int minZ = SectionPos.sectionToBlockCoord(sectionZ);
                for (int tickIndex = 0; tickIndex < randomTickSpeed; tickIndex++) {
                    BlockPos pos = level.getBlockRandomPos(minX, minY, minZ, SectionPos.SECTION_MASK);
                    profiler.push("randomTick");
                    BlockState blockState = section.getBlockState(pos.getX() - minX, pos.getY() - minY, pos.getZ() - minZ);
                    if (blockState.isRandomlyTicking()) {
                        blockState.randomTick(level, pos, level.getRandom());
                    }
                    FluidState fluidState = blockState.getFluidState();
                    if (fluidState.isRandomlyTicking()) {
                        fluidState.randomTick(level, pos, level.getRandom());
                    }
                    profiler.pop();
                }
            }
        }
        profiler.pop();
    }

    @Inject(method = "getCurrentDifficultyAt", at = @At("HEAD"), cancellable = true)
    private void cc_cubeDifficulty(BlockPos pos, CallbackInfoReturnable<DifficultyInstance> cir) {
        if (!this.cc_isCubic) {
            return;
        }
        long localTime = 0L;
        float moonBrightness = 0.0F;
        CubeAccess cube = ((CubeSource) this.getChunkSource()).cc_getCube(Coords.blockToCube(pos.getX()), Coords.blockToCube(pos.getY()),
                Coords.blockToCube(pos.getZ()), ChunkStatus.FULL, false);
        if (cube != null) {
            localTime = cube.getInhabitedTime();
            moonBrightness = ((ServerLevel) (Object) this).getMoonBrightness(pos);
        }
        cir.setReturnValue(new DifficultyInstance(this.getDifficulty(), this.getOverworldClockTime(), localTime, moonBrightness));
    }

    // TODO (P2) waitForChunkAndEntities

    // TODO: comments below don't account for 1.20.4->1.21.5 changes; will need to check for other methods that need CC changes

    // TODO: phase 3 - isNaturalSpawningAllowed

    // TODO: phase 3 - invalidateCapabilites, neoforge api

    // TODO: phase 4 - setCubeForced - new function

    // TODO: saveDebugReport - mixins, debug only, low priority, if we really really really really need it

    // TODO: phase 2 - isPositionEntityTicking - mixin

}

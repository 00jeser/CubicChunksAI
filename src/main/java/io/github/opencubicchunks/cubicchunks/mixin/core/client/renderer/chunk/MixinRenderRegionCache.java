package io.github.opencubicchunks.cubicchunks.mixin.core.client.renderer.chunk;

import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.client.multiplayer.ClientCubeCache;
import io.github.opencubicchunks.cubicchunks.client.renderer.chunk.CCSectionCopy;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// TODO rewrite javadoc for 1.21.6
/**
 * The vanilla {@link RenderRegionCache} caches {@link SectionCopy}s while rebuilding
 * {@link SectionRenderDispatcher.RenderSection}s
 * and has a method for creating a {@link RenderSectionRegion} for a given {@link SectionPos}.
 * <p/>
 * We simply modify it to get section data from cubes instead of chunks in cubic levels.
 */
@Mixin(RenderRegionCache.class)
public abstract class MixinRenderRegionCache {
    // Note the order of the individual sectionPos axes is weird (x,z,y) due to it being based on usage order within the vanilla lambda
    // To avoid brittleness we ignore these parameters and use the captured sectionPos long instead.
    @Inject(method = "lambda$getSectionDataCopy$0", at = @At("HEAD"), cancellable = true)
    private static void cc_onGetSectionDataCopy(
            Level level, int unusedSectionX, int unusedSectionZ, int unusedSectionY, long sectionPosLong, CallbackInfoReturnable<SectionCopy> cir
    ) {
        if (((CanBeCubic) level).cc_isCubic()) {
            var sectionPos = SectionPos.of(sectionPosLong);
            // load=false: промах кэша — пустой куб, не колонка и не cc_read.
            LevelCube cube = ((ClientCubeCache) ((ClientLevel) level).getChunkSource()).cc_cubeForMesh(Coords.sectionToCube(sectionPos.getX()),
                    Coords.sectionToCube(sectionPos.getY()), Coords.sectionToCube(sectionPos.getZ()));
            cir.setReturnValue(CCSectionCopy.cc_create(cube, Coords.sectionToIndex(sectionPos.getX(), sectionPos.getY(), sectionPos.getZ())));
        }
    }
}

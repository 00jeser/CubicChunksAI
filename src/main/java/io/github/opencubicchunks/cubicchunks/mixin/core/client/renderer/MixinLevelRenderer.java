package io.github.opencubicchunks.cubicchunks.mixin.core.client.renderer;

import io.github.notstirred.dasm.api.annotations.Dasm;
import io.github.notstirred.dasm.api.annotations.selector.Ref;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.client.renderer.CubeOcclusionNotifier;
import io.github.opencubicchunks.cubicchunks.client.renderer.CubicLevelRenderer;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCubeSet;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Dasm(value = ChunkToCubeSet.class, target = @Ref(LevelRenderer.class))
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer implements CubicLevelRenderer {
    @Shadow private SectionOcclusionGraph sectionOcclusionGraph;

    /**
     * DASM-копия {@code onChunkReadyToRender} не применяется: Mixin пропускает её как {@code @Final}.
     * Соседей отмечает сам граф.
     */
    @Override public void cc_onCubeReadyToRender(CubePos cubePos) {
        ((CubeOcclusionNotifier) (Object) this.sectionOcclusionGraph).cc_onCubeReadyToRender(cubePos);
    }
}

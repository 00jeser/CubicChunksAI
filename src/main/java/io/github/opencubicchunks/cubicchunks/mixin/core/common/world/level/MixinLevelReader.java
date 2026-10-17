package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level;

import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.world.level.CubicBuildHeight;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevelReader;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Cube equivalents of LevelReader chunk methods are implemented as defaults in CubicLevelReader
@Mixin(LevelReader.class)
public interface MixinLevelReader extends CubicLevelReader {
    /**
     * Vanilla returns false when Y is outside the dimension height, before it looks at columns. A cube at Y=-10000 is
     * then "not loaded" for every caller of this method. Horizontal presence stays the vanilla column check.
     */
    @Inject(method = "hasChunksAt(IIIIII)Z", at = @At("HEAD"), cancellable = true)
    private void cc_onHasChunksAt(int x0, int y0, int z0, int x1, int y1, int z1, CallbackInfoReturnable<Boolean> cir) {
        if (!(this instanceof CanBeCubic cubic) || !cubic.cc_isCubic()) {
            return;
        }
        if (!CubicBuildHeight.containsBlockY(y0) || !CubicBuildHeight.containsBlockY(y1)) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(((LevelReader) (Object) this).hasChunksAt(x0, z0, x1, z1));
    }
}

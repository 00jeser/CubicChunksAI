package io.github.opencubicchunks.cubicchunks.mixin.core.common.world.level.cube;

import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// E4: require = 0, потому что DASM 3.2.0 не вешает applicator на FMLMixinService (NeoForge 26).
// Редирект применяется, если тело метода уже скопировано; иначе класс грузится без падения.
@Mixin(LevelCube.class)
public abstract class MixinLevelCube {
    /**
     * Redirect to use cube section indexing instead of chunk section indexing
     */
    @Dynamic @Redirect(method = "getBlockState", at = @At(value = "INVOKE", target = "Lio/github/opencubicchunks/cubicchunks/world/level/cube/LevelCube;getSectionIndex(I)I"), require = 0)
    private int cc_onGetBlockState_SectionIndex(LevelCube instance, int y, BlockPos pos) {
        return Coords.blockToIndex(pos);
    }

    /**
     * Redirect to use cube section indexing instead of chunk section indexing
     */
    @Dynamic @Redirect(method = "getFluidState(III)Lnet/minecraft/world/level/material/FluidState;", at = @At(value = "INVOKE", target = "Lio/github/opencubicchunks/cubicchunks"
            + "/world/level/cube/LevelCube;getSectionIndex(I)I"), require = 0)
    private int cc_onGetFluidState_SectionIndex(LevelCube instance, int yUnused, int x, int y, int z) {
        return Coords.blockToIndex(x, y, z);
    }
}

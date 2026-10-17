package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.opencubicchunks.cubicchunks.server.level.CubicLoadCounter;
import io.github.opencubicchunks.cubicchunks.server.level.GenerationCloHolder;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLoadCounter;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Подготовка спавна считает держатели {@code FULL}. {@code getPos()} сидит в лямбдах {@code track} и у куба возвращает null.
 */
@Mixin(ChunkLoadCounter.class)
public class MixinChunkLoadCounter {
    @WrapOperation(method = "lambda$track$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkHolder;getPos()Lnet/minecraft/world/level/ChunkPos;"))
    private static ChunkPos cc_columnKeyBeforeTickets(ChunkHolder holder, Operation<ChunkPos> original) {
        return columnKey(holder, original);
    }

    @WrapOperation(method = "lambda$track$1", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkHolder;getPos()Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos cc_columnKeyAfterTickets(ChunkHolder holder, Operation<ChunkPos> original) {
        return columnKey(holder, original);
    }

    private static ChunkPos columnKey(ChunkHolder holder, Operation<ChunkPos> original) {
        return CubicLoadCounter.columnKey(original.call(holder), ((GenerationCloHolder) holder).cc_getCubePos());
    }
}

package io.github.opencubicchunks.cubicchunks.mixin.core.common.util.debug;

import io.github.opencubicchunks.cubicchunks.world.level.chunklike.LevelClo;
import net.minecraft.util.debug.LevelDebugSynchronizers;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * DASM вызывает {@code registerChunk(LevelClo)} из {@code ChunkMap.onChunkReadyToSend}.
 * У ванили есть только вариант с {@link LevelChunk}. Куб в колоночные debug-подписки не входит.
 */
@Mixin(LevelDebugSynchronizers.class)
public abstract class MixinLevelDebugSynchronizers {
    @Shadow public abstract void registerChunk(LevelChunk chunk);

    public void registerChunk(LevelClo clo) {
        if (clo instanceof LevelChunk chunk) {
            this.registerChunk(chunk);
        }
    }
}

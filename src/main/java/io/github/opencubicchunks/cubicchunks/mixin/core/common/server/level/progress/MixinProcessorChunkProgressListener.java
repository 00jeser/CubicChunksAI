package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level.progress;

import javax.annotation.Nullable;

import io.github.notstirred.dasm.api.annotations.Dasm;
import io.github.notstirred.dasm.api.annotations.selector.Ref;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCloSet;
import io.github.opencubicchunks.cubicchunks.server.level.progress.CloProgressListener;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;

// E4: ProcessorChunkProgressListener снят. LevelLoadListener — интерфейс; сборка слушателей — compose (E6).
@Dasm(value = ChunkToCloSet.class, target = @Ref(LevelLoadListener.class))
@Mixin(LevelLoadListener.class)
public interface MixinProcessorChunkProgressListener extends CloProgressListener {
    @Override default void cc_updateSpawnPos(CloPos center) {}

    @Override default void cc_onStatusChange(CloPos chunkPosition, @Nullable ChunkStatus newStatus) {}
}

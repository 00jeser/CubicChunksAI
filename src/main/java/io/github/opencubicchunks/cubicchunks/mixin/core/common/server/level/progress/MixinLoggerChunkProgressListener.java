package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level.progress;

import javax.annotation.Nullable;

import io.github.notstirred.dasm.api.annotations.Dasm;
import io.github.notstirred.dasm.api.annotations.selector.Ref;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCloSet;
import io.github.opencubicchunks.cubicchunks.server.level.progress.CloProgressListener;
import net.minecraft.server.level.progress.LoggingLevelLoadListener;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;

// E4: LoggerChunkProgressListener снят. updateSpawnPos и onStatusChange в 26.1.2 нет (E6).
@Dasm(value = ChunkToCloSet.class, target = @Ref(LoggingLevelLoadListener.class))
@Mixin(LoggingLevelLoadListener.class)
public abstract class MixinLoggerChunkProgressListener implements CloProgressListener {
    @Override public void cc_updateSpawnPos(CloPos center) {}

    @Override public void cc_onStatusChange(CloPos chunkPosition, @Nullable ChunkStatus newStatus) {}
}

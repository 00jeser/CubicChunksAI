package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level.progress;

import javax.annotation.Nullable;

import io.github.notstirred.dasm.api.annotations.Dasm;
import io.github.notstirred.dasm.api.annotations.selector.Ref;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCloSet;
import io.github.opencubicchunks.cubicchunks.server.level.progress.CloProgressListener;
import io.github.opencubicchunks.cubicchunks.server.level.progress.StoringCloProgressListener;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;

// E4: StoringChunkProgressListener снят. ChunkLoadStatusView — интерфейс; колонки читает он (E8).
@Dasm(value = ChunkToCloSet.class, target = @Ref(ChunkLoadStatusView.class))
@Mixin(ChunkLoadStatusView.class)
public interface MixinStoringChunkProgressListener extends CloProgressListener, StoringCloProgressListener {
    @Override default void cc_updateSpawnPos(CloPos center) {}

    @Override default void cc_onStatusChange(CloPos chunkPosition, @Nullable ChunkStatus newStatus) {}

    @Override @Nullable default ChunkStatus cc_getStatus(int cubeX, int cubeY, int cubeZ) {
        return null;
    }
}

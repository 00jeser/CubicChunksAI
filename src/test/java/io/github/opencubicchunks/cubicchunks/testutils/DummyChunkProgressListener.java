package io.github.opencubicchunks.cubicchunks.testutils;

import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;

/** E4: ChunkProgressListener нет в 26.1.2; слушатель статуса чанка — ChunkStatusUpdateListener. */
public class DummyChunkProgressListener implements ChunkStatusUpdateListener {
    @Override public void onChunkStatusChange(ChunkPos pos, FullChunkStatus chunkStatus) {}
}

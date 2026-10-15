package io.github.opencubicchunks.cubicchunks.server.level;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.CloAccess;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public interface CubicChunkMap {
    ChunkGenerationTask cc_scheduleGenerationTask(ChunkStatus chunkStatus, CubePos cubePos);

    void cc_onFullChunkStatusChange(CubePos cubePos, FullChunkStatus fullChunkStatus);

    boolean cc_isChunkTracked(ServerPlayer player, int x, int y, int z);

    CompletableFuture<ChunkResult<List<CloAccess>>> cc_cubeRangeFuture(ChunkHolder holder, int radius, IntFunction<ChunkStatus> statusByRadius);
}

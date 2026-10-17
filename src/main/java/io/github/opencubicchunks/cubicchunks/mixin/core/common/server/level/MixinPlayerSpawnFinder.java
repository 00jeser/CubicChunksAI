package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import java.util.concurrent.CompletableFuture;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.server.level.ServerCubeCache;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Spawn search tickets keep the suggested block Y as a cube. A column ticket would drop that Y.
 */
@Mixin(PlayerSpawnFinder.class)
public class MixinPlayerSpawnFinder {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private BlockPos spawnSuggestion;

    @WrapOperation(method = "scheduleCandidate", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;addTicketAndLoadWithRadius("
            + "Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;I)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<?> cc_spawnSearchLoadsCube(
            ServerChunkCache cache, TicketType type, ChunkPos pos, int radius, Operation<CompletableFuture<?>> original
    ) {
        if (!((CanBeCubic) this.level).cc_isCubic()) {
            return original.call(cache, type, pos, radius);
        }
        CloPos cube = CloPos.cube(Coords.sectionToCube(pos.x()), Coords.blockToCube(this.spawnSuggestion.getY()), Coords.sectionToCube(pos.z()));
        return ((ServerCubeCache) cache).cc_addTicketAndLoadWithRadius(type, cube, radius);
    }
}

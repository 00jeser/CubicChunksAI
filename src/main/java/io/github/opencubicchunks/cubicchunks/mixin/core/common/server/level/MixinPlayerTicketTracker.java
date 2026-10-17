package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.level;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.DistanceManagerAccess;
import io.github.opencubicchunks.cubicchunks.server.level.CloTaskDispatcher;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ThrottlingChunkTaskDispatcher;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DistanceManager.PlayerTicketTracker.class)
public abstract class MixinPlayerTicketTracker extends MixinFixedPlayerDistanceChunkTracker {
    @SuppressWarnings("target")
    @Shadow @Final DistanceManager this$0;

    /**
     * Cube positions that currently hold a {@link TicketType#PLAYER_LOADING} ticket. Vanilla's {@code saw} flag is the chebyshev level, which
     * ignores a separate vertical radius, so the set is the record of tickets this tracker actually added.
     */
    private final LongSet cc_playerCubeTickets = new LongOpenHashSet();

    /**
     * This modifies the lambda inside Distance.this.ticketDispatcher.onLevelChange to use a CloPos instead of a ChunkPos.
     */
    @WrapWithCondition(method = "runAllUpdates", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ThrottlingChunkTaskDispatcher;onLevelChange(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/IntSupplier;ILjava/util/function/IntConsumer;)V"))
    private boolean cc_onRunAllUpdates(
            ThrottlingChunkTaskDispatcher instance, ChunkPos chunkPos, IntSupplier intSupplier, int i, IntConsumer intConsumer
    ) {
        if (!cc_isCubic) {
            return true;
        }
        ((CloTaskDispatcher) ((DistanceManagerAccess) this$0).cc_ticketDispatcher()).cc_onLevelChange(CloPos.fromLong(chunkPos.pack()), intSupplier,
                i, intConsumer);
        return false;
    }

    /**
     * Player-loading tickets for cubes stay inside {@link io.github.opencubicchunks.cubicchunks.config.CommonConfig#getVerticalViewDistance()}.
     * Horizontal reach stays the vanilla view distance. Column keys keep the original method.
     */
    @Inject(method = "onLevelChange(JIZZ)V", at = @At("HEAD"), cancellable = true)
    private void cc_onCubePlayerTicket(long key, int level, boolean saw, boolean sees, CallbackInfo ci) {
        if (!cc_isCubic || !CloPos.isCube(key)) {
            return;
        }
        ci.cancel();
        boolean had = this.cc_playerCubeTickets.contains(key);
        boolean want = sees && this.cc_withinVerticalRadius(key, level);
        if (want) {
            this.cc_playerCubeTickets.add(key);
        } else {
            this.cc_playerCubeTickets.remove(key);
        }
        if (had == want) {
            return;
        }
        DistanceManagerAccess access = (DistanceManagerAccess) this.this$0;
        Ticket ticket = new Ticket(TicketType.PLAYER_LOADING, DistanceManagerAccess.cc_playerTicketLevel());
        if (want) {
            access.cc_ticketDispatcher().submit(() -> access.cc_mainThreadExecutor().execute(() -> {
                if (this.cc_playerCubeTickets.contains(key)) {
                    access.cc_ticketStorage().addTicket(key, ticket);
                    access.cc_ticketsToRelease().add(key);
                } else {
                    access.cc_ticketDispatcher().release(key, () -> {}, false);
                }
            }), key, () -> level);
        } else {
            access.cc_ticketDispatcher().release(key,
                    () -> access.cc_mainThreadExecutor().execute(() -> access.cc_ticketStorage().removeTicket(key, ticket)), true);
        }
    }

    private boolean cc_withinVerticalRadius(long pos, int level) {
        if (level == 0) {
            return true;
        }
        int verticalRadius = Coords.sectionToCubeCeil(Math.max(0, CubicChunks.config().getVerticalViewDistance()));
        int x = CloPos.extractX(pos);
        int y = CloPos.extractY(pos);
        int z = CloPos.extractZ(pos);
        for (Long2ByteMap.Entry entry : this.chunks.long2ByteEntrySet()) {
            long player = entry.getLongKey();
            if (entry.getByteValue() != 0 || !CloPos.isCube(player)) {
                continue;
            }
            int horizontal = Math.max(Math.abs(x - CloPos.extractX(player)), Math.abs(z - CloPos.extractZ(player)));
            int vertical = Math.abs(y - CloPos.extractY(player));
            if (vertical <= verticalRadius && horizontal <= level) {
                return true;
            }
        }
        return false;
    }
}

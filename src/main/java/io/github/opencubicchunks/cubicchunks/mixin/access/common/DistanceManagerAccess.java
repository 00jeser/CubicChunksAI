package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import java.util.concurrent.Executor;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ThrottlingChunkTaskDispatcher;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DistanceManager.class)
public interface DistanceManagerAccess {
    @Accessor("ticketDispatcher") ThrottlingChunkTaskDispatcher cc_ticketDispatcher();

    @Accessor("ticketStorage") TicketStorage cc_ticketStorage();

    @Accessor("mainThreadExecutor") Executor cc_mainThreadExecutor();

    @Accessor("ticketsToRelease") LongSet cc_ticketsToRelease();

    @Accessor("PLAYER_TICKET_LEVEL") static int cc_playerTicketLevel() {
        throw new AssertionError();
    }
}

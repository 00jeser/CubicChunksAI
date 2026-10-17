package io.github.opencubicchunks.cubicchunks.mixin.test.common.server.network;

import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerChunkSender.class)
public interface PlayerChunkSenderTestAccess {
    @Dynamic @Invoker(value = "cc_sendCube", remap = false) static void invokeCcSendCube(ServerGamePacketListenerImpl listener, ServerLevel level, LevelCube cube) {
        throw new AssertionError();
    }

    @Dynamic @Invoker(value = "cc_sendChunk", remap = false) static void invokeCcSendChunk(ServerGamePacketListenerImpl listener, ServerLevel level, LevelChunk chunk) {
        throw new AssertionError();
    }
}

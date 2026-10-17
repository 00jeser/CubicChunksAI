package io.github.opencubicchunks.cubicchunks.network;

import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor.WorldStyle;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

/**
 * Стиль измерения. Уходит сразу перед {@code ClientboundLoginPacket} и {@code ClientboundRespawnPacket}, потому что те конструируют
 * {@code ClientLevel}.
 */
public record CCClientboundLevelInfoPacket(String worldStyle) implements CustomPacketPayload {
    public static final Type<CCClientboundLevelInfoPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(CubicChunks.MODID, "level_info"));

    public static final StreamCodec<FriendlyByteBuf, CCClientboundLevelInfoPacket> STREAM_CODEC = StreamCodec
            .of((buffer, packet) -> buffer.writeUtf(packet.worldStyle), buffer -> new CCClientboundLevelInfoPacket(buffer.readUtf()));

    public CCClientboundLevelInfoPacket(WorldStyle style) {
        this(style.name());
    }

    @Override public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static class Handler implements IPayloadHandler<CCClientboundLevelInfoPacket> {
        @Override public void handle(CCClientboundLevelInfoPacket payload, IPayloadContext context) {
            ClientWorldStyle.queue(WorldStyle.valueOf(payload.worldStyle));
        }
    }
}

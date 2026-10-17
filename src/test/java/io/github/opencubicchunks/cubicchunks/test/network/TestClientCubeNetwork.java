package io.github.opencubicchunks.cubicchunks.test.network;

import static io.github.opencubicchunks.cc_core.utils.Coords.cubeToSection;
import static io.github.opencubicchunks.cc_core.utils.Coords.sectionToCubeCeil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor.WorldStyle;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.client.multiplayer.ClientCubeCache;
import io.github.opencubicchunks.cubicchunks.mixin.test.common.server.network.PlayerChunkSenderTestAccess;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundLevelChunkPacket;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundLevelCubeWithLightPacket;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundLevelInfoPacket;
import io.github.opencubicchunks.cubicchunks.network.ClientWorldStyle;
import io.github.opencubicchunks.cubicchunks.testutils.BaseTest;
import io.github.opencubicchunks.cubicchunks.testutils.Misc;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Контракт E7 без клиентского рендера: секции куба, отсутствие света, радиус, отправка и стиль клиента.
 */
public class TestClientCubeNetwork extends BaseTest {
    @AfterEach
    public void clearQueuedStyle() {
        ClientWorldStyle.take();
    }

    @Test
    @SuppressWarnings("checkstyle:MagicNumber")
    public void sineRoundTripHasNoLightAndDependsOnSectionBytes() {
        var level = cubicLevel();
        var server = new LevelCube(level, CubePos.of(0, 0, 0));
        fillSine(server);
        var empty = new LevelCube(level, CubePos.of(1, 0, 0));

        var packet = new CCClientboundLevelCubeWithLightPacket(server);
        var emptyPacket = new CCClientboundLevelCubeWithLightPacket(empty);
        assertTrue(encodedSize(packet) > encodedSize(emptyPacket), "buffer grows when a section is not empty");
        assertSectionBytesOnly(packet, server);

        var client = new LevelCube(level, server.cc_getCubePos());
        client.replaceWithPacketData(packet.cubeData().getReadBuffer(), Map.of(), tag -> {});
        assertSameBlocks(server, client);

        BlockState stone = Blocks.SMOOTH_STONE.defaultBlockState();
        boolean serverStillHasStone = false;
        CubePos pos = server.cc_getCubePos();
        for (int x = pos.minCubeX(); x <= pos.maxCubeX() && !serverStillHasStone; x++) {
            for (int y = pos.minCubeY(); y <= pos.maxCubeY() && !serverStillHasStone; y++) {
                for (int z = pos.minCubeZ(); z <= pos.maxCubeZ(); z++) {
                    if (stone.equals(server.getBlockState(new BlockPos(x, y, z)))) {
                        serverStillHasStone = true;
                        break;
                    }
                }
            }
        }
        assertTrue(serverStillHasStone, "encoding the packet leaves the server cube in place");
    }

    @Test
    @SuppressWarnings("checkstyle:MagicNumber")
    public void movingCenterForgetsCubeOutsideRadius() {
        var level = cubicLevel();
        var storage = new ClientCubeCache.Storage(2, level);
        var cube = new LevelCube(level, CubePos.of(0, 0, 0));
        storage.replace(storage.getIndex(0, 0, 0), cube);
        assertFalse(storage.inRange(4, 0, 0));

        storage.setViewCenter(4, 0, 0);
        assertEquals(0, storage.chunkCount);
        assertFalse(storage.inRange(0, 0, 0));

        var kept = new LevelCube(level, CubePos.of(4, 0, 0));
        storage.replace(storage.getIndex(4, 0, 0), kept);
        storage.setViewCenter(4, 0, 0);
        assertEquals(1, storage.chunkCount);

        var server = new LevelCube(level, CubePos.of(0, 0, 0));
        fillSine(server);
        BlockPos sample = new BlockPos(0, server.cc_getCubePos().minCubeY(), 0);
        BlockState before = server.getBlockState(sample);
        var clientStorage = new ClientCubeCache.Storage(2, level);
        var client = new LevelCube(level, server.cc_getCubePos());
        client.replaceWithPacketData(new CCClientboundLevelCubeWithLightPacket(server).cubeData().getReadBuffer(), Map.of(), tag -> {});
        clientStorage.replace(clientStorage.getIndex(0, 0, 0), client);
        clientStorage.drop(clientStorage.getIndex(0, 0, 0), client);
        assertEquals(0, clientStorage.chunkCount);
        assertEquals(before, server.getBlockState(sample));
    }

    @Test
    public void senderEmitsCubePacketAndChunkStyleDoesNot() {
        var level = cubicLevel();
        var cube = new LevelCube(level, CubePos.of(2, -1, 3));
        ServerGamePacketListenerImpl listener = mock(ServerGamePacketListenerImpl.class);
        List<CustomPacketPayload> sent = new ArrayList<>();
        try (MockedStatic<PacketDistributor> packets = mockStatic(PacketDistributor.class)) {
            packets.when(() -> PacketDistributor.sendToPlayer(nullable(ServerPlayer.class), any(), any(CustomPacketPayload[].class)))
                    .thenAnswer(invocation -> {
                        sent.add(invocation.getArgument(1));
                        return null;
                    });
            PlayerChunkSenderTestAccess.invokeCcSendCube(listener, mock(ServerLevel.class), cube);
            PlayerChunkSenderTestAccess.invokeCcSendChunk(listener, mock(ServerLevel.class), chunkColumn());

            ServerPlayer cubicPlayer = mock(Mockito.RETURNS_DEEP_STUBS);
            when(((CanBeCubic) cubicPlayer.level()).cc_isCubic()).thenReturn(true);
            new PlayerChunkSender(false).sendNextChunks(cubicPlayer);

            ServerPlayer chunkPlayer = mock(Mockito.RETURNS_DEEP_STUBS);
            when(((CanBeCubic) chunkPlayer.level()).cc_isCubic()).thenReturn(false);
            try {
                new PlayerChunkSender(false).sendNextChunks(chunkPlayer);
            } catch (RuntimeException vanillaContinued) {
                // Ванильный хвост sendNextChunks на моке может упасть после раннего выхода кубической ветки.
            }
        }
        assertEquals(1, sent.stream().filter(payload -> payload instanceof CCClientboundLevelCubeWithLightPacket).count());
        assertTrue(sent.stream().anyMatch(payload -> payload instanceof CCClientboundLevelChunkPacket));
        assertFalse(sent.stream()
                .anyMatch(payload -> payload instanceof CCClientboundLevelCubeWithLightPacket packet && !packet.pos().equals(cube.cc_getCubePos())));
    }

    @Test
    @SuppressWarnings("checkstyle:MagicNumber")
    public void chunkSectionsStayInsideChunkViewDistance() {
        int verticalSections = CubicChunks.config().getVerticalViewDistance();
        for (int viewDistance = 2; viewDistance <= 8; viewDistance++) {
            int horizontal = Math.max(2, viewDistance) + 3;
            int vertical = Math.max(2, sectionToCubeCeil(verticalSections)) + 3;
            int cubeRadius = Math.max(horizontal, vertical);
            int chunkRadius = Math.max(2, ClientCubeCache.chunkViewDistanceCovering(cubeRadius)) + 3;
            int center = 77;
            for (int dx = -cubeRadius; dx <= cubeRadius; dx++) {
                for (int section = 0; section < CubicConstants.DIAMETER_IN_SECTIONS; section++) {
                    int sectionX = cubeToSection(center + dx, section);
                    int chunkCenter = cubeToSection(center, 0);
                    assertTrue(Math.abs(sectionX - chunkCenter) <= chunkRadius);
                }
            }
        }
    }

    @Test
    public void levelInfoPacketQueuesStyleOnce() {
        var packet = new CCClientboundLevelInfoPacket(WorldStyle.HYBRID);
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        CCClientboundLevelInfoPacket.STREAM_CODEC.encode(buf, packet);
        assertEquals(packet, CCClientboundLevelInfoPacket.STREAM_CODEC.decode(buf));
        assertFalse(buf.isReadable());

        new CCClientboundLevelInfoPacket.Handler().handle(packet, mock(IPayloadContext.class));
        assertEquals(WorldStyle.HYBRID, ClientWorldStyle.take());
        assertEquals(WorldStyle.CHUNK, ClientWorldStyle.take());
    }

    private static net.minecraft.client.multiplayer.ClientLevel cubicLevel() {
        var level = mock(net.minecraft.client.multiplayer.ClientLevel.class, Mockito.RETURNS_DEEP_STUBS);
        var factory = Misc.palettedContainerFactory();
        when(level.palettedContainerFactory()).thenReturn(factory);
        when(((CanBeCubic) level).cc_isCubic()).thenReturn(true);
        when(level.getHeight()).thenReturn(384);
        when(level.getSectionsCount()).thenReturn(24);
        return level;
    }

    private static LevelChunk chunkColumn() {
        LevelChunk chunk = mock(LevelChunk.class);
        when(chunk.getPos()).thenReturn(new net.minecraft.world.level.ChunkPos(3, -1));
        return chunk;
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static void fillSine(LevelCube cube) {
        int amplitude = 20;
        BlockState stone = Blocks.SMOOTH_STONE.defaultBlockState();
        CubePos cubePos = cube.cc_getCubePos();
        int minY = cubePos.minCubeY();
        int maxY = Math.min(cubePos.maxCubeY(), CubicChunks.SUPERFLAT_HEIGHT + amplitude);
        int cubeX = cubePos.minCubeX();
        int cubeZ = cubePos.minCubeZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int x = 0; x < CubicConstants.DIAMETER_IN_BLOCKS; x++) {
                for (int z = 0; z < CubicConstants.DIAMETER_IN_BLOCKS; z++) {
                    if (y + Math.round((amplitude * (Math.sin((x + cubeX) / 8.0 + (z + cubeZ) / 21.0) + Math.cos((z + cubeZ) / 13.0)))
                            / 2.0) <= CubicChunks.SUPERFLAT_HEIGHT) {
                        cube.setBlockState(pos.set(x + cubeX, y, z + cubeZ), stone, 0);
                    }
                }
            }
        }
    }

    private static void assertSameBlocks(LevelCube server, LevelCube client) {
        CubePos pos = server.cc_getCubePos();
        for (int x = pos.minCubeX(); x <= pos.maxCubeX(); x++) {
            for (int y = pos.minCubeY(); y <= pos.maxCubeY(); y++) {
                for (int z = pos.minCubeZ(); z <= pos.maxCubeZ(); z++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    assertEquals(server.getBlockState(blockPos), client.getBlockState(blockPos));
                }
            }
        }
    }

    private static int encodedSize(CCClientboundLevelCubeWithLightPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CCClientboundLevelCubeWithLightPacket.STREAM_CODEC.encode(buf, packet);
        return buf.readableBytes();
    }

    private static void assertSectionBytesOnly(CCClientboundLevelCubeWithLightPacket packet, LevelCube cube) {
        int sectionBytes = 0;
        for (LevelChunkSection section : cube.getSections()) {
            sectionBytes += section.getSerializedSize();
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CCClientboundLevelCubeWithLightPacket.STREAM_CODEC.encode(buf, packet);
        assertEquals(cube.cc_getCubePos().asLong(), buf.readLong());
        assertEquals(sectionBytes, buf.readVarInt());
        buf.skipBytes(sectionBytes);
        assertFalse(buf.isReadable(), "packet dump has no light arrays after the sections");
    }
}

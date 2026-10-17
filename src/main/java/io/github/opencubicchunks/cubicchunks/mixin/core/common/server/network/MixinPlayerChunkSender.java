package io.github.opencubicchunks.cubicchunks.mixin.core.common.server.network;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

import io.github.notstirred.dasm.api.annotations.Dasm;
import io.github.notstirred.dasm.api.annotations.redirect.redirects.AddMethodToSets;
import io.github.notstirred.dasm.api.annotations.selector.Ref;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.level.CloPos;
import io.github.opencubicchunks.cubicchunks.CanBeCubic;
import io.github.opencubicchunks.cubicchunks.mixin.dasmsets.ChunkToCloSet;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundForgetLevelCloPacket;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundLevelChunkPacket;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundLevelCubeWithLightPacket;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevel;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.LevelClo;
import io.github.opencubicchunks.cubicchunks.world.level.cube.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.cube.LevelCube;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Dasm(value = ChunkToCloSet.class, target = @Ref(PlayerChunkSender.class))
@Mixin(PlayerChunkSender.class)
public class MixinPlayerChunkSender {
    @Shadow private int unacknowledgedBatches;

    @Shadow private int maxUnacknowledgedBatches;

    @Shadow private float desiredChunksPerTick;

    @Shadow private float batchQuota;

    @Shadow @Final private LongSet pendingChunks;
    @Shadow @Final private boolean memoryConnection;

    /** Кубики, пакет которых уже ушёл этому игроку. Трекер DASM не ставит их в {@code pendingChunks}. */
    @Unique
    private final LongSet cc_sentCubes = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    /** Сколько кубов вокруг игрока класть в очередь, если трекер DASM их туда не положил. */
    @Unique
    private static final int CC_ENQUEUE_RADIUS = 2;

    /** {@code @TransformFromMethod} этой сигнатуры пропускается как {@code @Final}. Тело — то же, что у колонки: ключ в очередь. */
    public void cc_markCloPendingToSend(LevelClo clo) {
        this.pendingChunks.add(clo.cc_getCloPos().toLong());
    }

    /**
     * DASM переписывает {@code ChunkMap.markChunkPendingToSend} так, что он зовёт этот оверлоад с {@link LevelClo}.
     * У ванили есть только вариант с {@link LevelChunk}.
     */
    public void markChunkPendingToSend(LevelClo clo) {
        this.cc_markCloPendingToSend(clo);
    }

    @AddMethodToSets(containers = ChunkToCloSet.PlayerChunkSender_redirects.class, method = "dropChunk(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/ChunkPos;)V")
    public void cc_dropClo(ServerPlayer player, CloPos cloPos) {
        if (!this.pendingChunks.remove(cloPos.toLong()) && player.isAlive()) {
            PacketDistributor.sendToPlayer(player, new CCClientboundForgetLevelCloPacket(cloPos));
        }
    }

    @Inject(method = "sendNextChunks", at = @At(value = "HEAD"), cancellable = true)
    private void cc_onSendNextChunks(ServerPlayer player, CallbackInfo ci) {
        if (!((CanBeCubic) player.level()).cc_isCubic()) {
            return;
        }

        ci.cancel();

        if (this.unacknowledgedBatches < this.maxUnacknowledgedBatches) {
            float f = Math.max(1.0F, this.desiredChunksPerTick);
            this.batchQuota = Math.min(this.batchQuota + this.desiredChunksPerTick, f);
            if (!(this.batchQuota < 1.0F)) {
                this.cc_enqueueCubesAround(player);
                if (!this.pendingChunks.isEmpty()) {
                    ServerLevel serverlevel = player.level();
                    ChunkMap chunkmap = serverlevel.getChunkSource().chunkMap;
                    if (chunkmap == null) {
                        return;
                    }
                    List<LevelClo> list = this.cc_collectChunksToSend(chunkmap, CloPos.cube(Coords.blockToCube(player.blockPosition().getX()),
                            Coords.blockToCube(player.blockPosition().getY()), Coords.blockToCube(player.blockPosition().getZ())));
                    this.cc_sendBatch(player, serverlevel, list);
                }
            }
        }
    }

    @Unique
    private void cc_sendBatch(ServerPlayer player, ServerLevel level, List<LevelClo> list) {
        if (list.isEmpty()) {
            return;
        }
        ServerGamePacketListenerImpl packetListener = player.connection;
        ++this.unacknowledgedBatches;
        packetListener.send(ClientboundChunkBatchStartPacket.INSTANCE);
        for (LevelClo levelClo : list) {
            if (levelClo instanceof LevelChunk chunk) {
                cc_sendChunk(packetListener, level, chunk);
            }
        }
        for (LevelClo levelClo : list) {
            if (levelClo instanceof LevelCube cube) {
                cc_sendCube(packetListener, level, cube);
            }
        }
        packetListener.send(new ClientboundChunkBatchFinishedPacket(list.size()));
        this.batchQuota -= (float) list.size();
    }

    @Unique
    private static void cc_sendCube(ServerGamePacketListenerImpl packetListener, ServerLevel level, LevelCube cube) {
        PacketDistributor.sendToPlayer(packetListener.player, new CCClientboundLevelCubeWithLightPacket(cube));

        // ChunkPos chunkpos = chunk.getPos();

        // TODO :: Probably never (its for vanilla debug tools)
        // DebugPackets.sendPoiPacketsForChunk(level, chunkpos);

        // TODO P3 :: We need our own fireCubeSent event for this
        // net.neoforged.neoforge.event.EventHooks.fireChunkSent(packetListener.player, chunk, level);
    }

    @Unique
    private static void cc_sendChunk(ServerGamePacketListenerImpl packetListener, ServerLevel level, LevelChunk chunk) {
        PacketDistributor.sendToPlayer(packetListener.player, new CCClientboundLevelChunkPacket(chunk.getPos()));

        // ChunkPos chunkpos = chunk.getPos();

        // TODO :: Probably never (its for vanilla debug tools)
        // DebugPackets.sendPoiPacketsForChunk(level, chunkpos);

        // TODO P3 :: We need our own fireChunkSent event for this
        // net.neoforged.neoforge.event.EventHooks.fireChunkSent(packetListener.player, chunk, level);
    }

    private void cc_enqueueCubesAround(ServerPlayer player) {
        int centerX = Coords.blockToCube(player.blockPosition().getX());
        int centerY = Coords.blockToCube(player.blockPosition().getY());
        int centerZ = Coords.blockToCube(player.blockPosition().getZ());
        for (int dx = -CC_ENQUEUE_RADIUS; dx <= CC_ENQUEUE_RADIUS; dx++) {
            for (int dy = -CC_ENQUEUE_RADIUS; dy <= CC_ENQUEUE_RADIUS; dy++) {
                for (int dz = -CC_ENQUEUE_RADIUS; dz <= CC_ENQUEUE_RADIUS; dz++) {
                    long key = CloPos.cube(centerX + dx, centerY + dy, centerZ + dz).toLong();
                    if (this.cc_sentCubes.contains(key)) {
                        continue;
                    }
                    this.pendingChunks.add(key);
                }
            }
        }
    }

    /**
     * Сбор пакета. DASM-копия {@code collectChunksToSend} пропускается как {@code @Final}.
     * Ключ куба режется через {@link CloPos#isCube(long)}, колонка — через {@code getChunkToSend}.
     */
    private List<LevelClo> cc_collectChunksToSend(ChunkMap chunkMap, CloPos cloPos) {
        int maxBatch = Math.max(1, (int) this.batchQuota);
        if (this.memoryConnection) {
            maxBatch = Math.max(maxBatch, this.pendingChunks.size());
        }
        List<Long> keys = new ArrayList<>();
        for (Long key : this.pendingChunks) {
            keys.add(key);
            if (keys.size() >= maxBatch) {
                break;
            }
        }
        ServerLevel level = cc_chunkMapLevel(chunkMap);
        List<LevelClo> clos = new ArrayList<>();
        for (long key : keys) {
            LevelClo clo = cc_resolve(level, chunkMap, key);
            if (clo == null) {
                continue;
            }
            this.pendingChunks.remove(key);
            if (CloPos.isCube(key)) {
                this.cc_sentCubes.add(key);
            }
            clos.add(clo);
        }
        return clos;
    }

    private static LevelClo cc_resolve(ServerLevel level, ChunkMap chunkMap, long key) {
        if (CloPos.isCube(key)) {
            CloPos pos = CloPos.fromLong(key);
            CubeAccess cube = ((CubicLevel) level).cc_getCube(pos.getX(), pos.getY(), pos.getZ(), ChunkStatus.FULL, false);
            if (cube instanceof LevelCube levelCube) {
                return levelCube;
            }
            return null;
        }
        LevelChunk chunk = chunkMap.getChunkToSend(key);
        return (LevelClo) chunk;
    }

    private static ServerLevel cc_chunkMapLevel(ChunkMap chunkMap) {
        try {
            Field field = ChunkMap.class.getDeclaredField("level");
            field.setAccessible(true);
            return (ServerLevel) field.get(chunkMap);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // FIXME these should probably have some kind of reasonable sort order - at the very least, chunks before cubes
    @Dynamic @Redirect(method = "cc_collectChunksToSend", at = @At(ordinal = 0, value = "INVOKE", target = "Ljava/util/Comparator;comparingInt(Ljava/util/function/ToIntFunction;)Ljava/util/Comparator;"), require = 0)
    private Comparator<Long> cc_onCollectChunksToSend_comparator1(ToIntFunction<Long> keyExtractor) {
        return (a, b) -> 0;
    }

    @Dynamic @Redirect(method = "cc_collectChunksToSend", at = @At(ordinal = 1, value = "INVOKE", target = "Ljava/util/Comparator;comparingInt(Ljava/util/function/ToIntFunction;)Ljava/util/Comparator;"), require = 0)
    private Comparator<LevelClo> cc_onCollectChunksToSend_comparator2(ToIntFunction<LevelClo> keyExtractor) {
        return (a, b) -> 0;
    }
}

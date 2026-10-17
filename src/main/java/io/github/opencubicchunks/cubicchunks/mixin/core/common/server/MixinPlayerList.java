package io.github.opencubicchunks.cubicchunks.mixin.core.common.server;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor.WorldStyle;
import io.github.opencubicchunks.cubicchunks.network.CCClientboundLevelInfoPacket;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevel;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Пакет стиля уходит раньше логина и респавна: оба пакета на клиенте создают {@code ClientLevel}, а кэш читает {@code cc_isCubic} в своём
 * конструкторе.
 */
@Mixin(PlayerList.class)
public class MixinPlayerList {
    @Inject(method = "placeNewPlayer", at = @At(value = "NEW", target = "net/minecraft/network/protocol/game/ClientboundLoginPacket"))
    private void cc_beforeLogin(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        cc_sendLevelInfo(player);
    }

    @Inject(method = "respawn", at = @At(value = "NEW", target = "net/minecraft/network/protocol/game/ClientboundRespawnPacket"))
    private void cc_beforeRespawn(
            ServerPlayer oldPlayer, boolean keepAllPlayerData, Entity.RemovalReason reason, CallbackInfoReturnable<ServerPlayer> cir,
            @Local(ordinal = 1) ServerPlayer newPlayer
    ) {
        cc_sendLevelInfo(newPlayer);
    }

    private static void cc_sendLevelInfo(ServerPlayer player) {
        WorldStyle style = ((CubicLevel) player.level()).cc_worldStyle();
        PacketDistributor.sendToPlayer(player, new CCClientboundLevelInfoPacket(style));
    }
}

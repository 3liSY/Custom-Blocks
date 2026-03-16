package com.hangman.mixin;

import com.hangman.server.game.HangmanGameManager;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
    private void hangman_freezeMovement(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (!HangmanGameManager.get().isFrozen(player.getUuid())) return;

        // Allow head rotation (yaw/pitch changes) but block position changes
        if (packet.isChangePosition()) {
            // Cancel positional movement, teleport back to keep client in sync
            ci.cancel();
            player.networkHandler.requestTeleport(
                player.getX(), player.getY(), player.getZ(),
                packet.getYaw(player.getYaw()),   // allow yaw
                packet.getPitch(player.getPitch()) // allow pitch
            );
        }
        // Non-position packets (rotation only) pass through freely
    }
}

package com.example.playerhighlight.mixin;

import com.example.playerhighlight.PvpTrackerClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tracks outgoing PvP attacks from the local player.
 */
@Mixin(PlayerEntity.class)
public class PlayerAttackPvpMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void onAttack(Entity target, CallbackInfo ci) {
        if (!(target instanceof PlayerEntity targetPlayer)) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity localPlayer = client != null ? client.player : null;
        if (localPlayer == null) {
            return;
        }

        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self != localPlayer) {
            return;
        }
        if (targetPlayer == localPlayer) {
            return;
        }

        PvpTrackerClient.recordOutgoingAttack(localPlayer, targetPlayer);
    }
}


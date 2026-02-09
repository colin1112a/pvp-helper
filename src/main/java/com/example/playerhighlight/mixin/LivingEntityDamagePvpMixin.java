package com.example.playerhighlight.mixin;

import com.example.playerhighlight.PvpTrackerClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tracks incoming PvP hits for the local player.
 */
@Mixin(LivingEntity.class)
public class LivingEntityDamagePvpMixin {

    @Inject(method = "damage", at = @At("RETURN"))
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir == null || !Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity localPlayer = client != null ? client.player : null;
        if (localPlayer == null) {
            return;
        }

        LivingEntity self = (LivingEntity) (Object) this;
        if (self != localPlayer) {
            return;
        }

        Entity attacker = source != null ? source.getAttacker() : null;
        if (!(attacker instanceof PlayerEntity playerAttacker)) {
            return;
        }
        if (playerAttacker == localPlayer) {
            return;
        }

        PvpTrackerClient.recordIncomingHit(localPlayer, playerAttacker, source, amount);
    }
}


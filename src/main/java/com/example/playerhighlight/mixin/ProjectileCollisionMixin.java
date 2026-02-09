package com.example.playerhighlight.mixin;

import com.example.playerhighlight.ProjectileTrackerClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.*;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 服务器 bug 兼容：部分服务器会让箭在落地后持续弹跳不消失。
 *
 * 在首次方块碰撞时立即标记为落地，避免被反复记录/再次追踪。
 */
@Mixin(ProjectileEntity.class)
public class ProjectileCollisionMixin {

    @Inject(method = "onCollision", at = @At("HEAD"))
    private void onCollision(HitResult hitResult, CallbackInfo ci) {
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        Entity self = (Entity) (Object) this;
        boolean isTrackable = self instanceof ArrowEntity
                || self instanceof SpectralArrowEntity
                || self instanceof TridentEntity
                || self instanceof SmallFireballEntity;
        if (!isTrackable) {
            return;
        }

        ProjectileTrackerClient.markProjectileAsLanded(self.getUuid(), hitResult.getPos());
    }
}


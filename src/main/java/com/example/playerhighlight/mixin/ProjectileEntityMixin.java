package com.example.playerhighlight.mixin;

import com.example.playerhighlight.ProjectileTrackerClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 弹道实体 Mixin - 启用发光效果
 *
 * 使追踪的弹道显示白色轮廓
 * 复用 PlayerEntityMixin 的模式
 */
@Mixin(Entity.class)
public class ProjectileEntityMixin {

    /**
     * 注入 isGlowing() 方法
     * 让追踪的弹道返回 true，启用发光效果
     */
    @Inject(method = "isGlowing()Z", at = @At("HEAD"), cancellable = true)
    private void onProjectileIsGlowing(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;

        // 检查是否是可追踪的弹道类型
        boolean isProjectile = self instanceof ArrowEntity
                || self instanceof SpectralArrowEntity
                || self instanceof TridentEntity
                || self instanceof SmallFireballEntity;

        // 如果是被追踪的弹道，启用发光
        if (isProjectile && ProjectileTrackerClient.isProjectileTracked(self.getUuid())) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 注入 getTeamColorValue() 方法
     * 设置发光颜色为白色
     */
    @Inject(method = "getTeamColorValue()I", at = @At("HEAD"), cancellable = true)
    private void onProjectileGetTeamColorValue(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;

        boolean isProjectile = self instanceof ArrowEntity
                || self instanceof SpectralArrowEntity
                || self instanceof TridentEntity
                || self instanceof SmallFireballEntity;

        // 如果是被追踪的弹道，设置白色轮廓
        if (isProjectile && ProjectileTrackerClient.isProjectileTracked(self.getUuid())) {
            cir.setReturnValue(0xFFFFFF);  // 白色
        }
    }
}

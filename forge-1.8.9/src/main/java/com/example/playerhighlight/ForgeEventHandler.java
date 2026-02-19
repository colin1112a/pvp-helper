package com.example.playerhighlight;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * 统一事件处理器 — 替代所有 Fabric Mixin
 *
 * 注册到 MinecraftForge.EVENT_BUS
 */
public class ForgeEventHandler {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        // TAB 键检测
        KeyBindHandler.tick();

        // 弹道追踪 tick
        ProjectileTrackerClient.tick(mc);

        // 弓预瞄 tick
        BowPreviewClient.tick(mc);

        // 刷新校准参数到磁盘
        DynamicProjectileRegistry.getInstance().flushIfDue();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        float partialTicks = event.partialTicks;

        // ESP 轮廓线渲染
        EspRenderer.render(partialTicks);

        // 轨迹线 + 落点 + 弓预瞄渲染
        TrajectoryRenderer.render(partialTicks);
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        // 替代 LivingEntityDamagePvpMixin：记录本地玩家受到的来自其他玩家的伤害
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }

        if (event.entity != mc.thePlayer) {
            return;
        }

        DamageSource source = event.source;
        if (source == null) {
            return;
        }

        // 检查伤害来源是否为玩家
        if (source.getEntity() instanceof EntityPlayer) {
            EntityPlayer attacker = (EntityPlayer) source.getEntity();
            if (attacker != mc.thePlayer) {
                PvpTrackerClient.recordIncomingHit(mc.thePlayer, attacker, source, event.ammount);
            }
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        // 替代 PlayerAttackPvpMixin：记录本地玩家对其他玩家的攻击
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }

        if (event.entityPlayer != mc.thePlayer) {
            return;
        }

        if (event.target instanceof EntityPlayer) {
            EntityPlayer victim = (EntityPlayer) event.target;
            PvpTrackerClient.recordOutgoingAttack(mc.thePlayer, victim);
        }
    }
}

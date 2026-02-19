package com.example.playerhighlight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

import java.util.Collection;

/**
 * ESP 轮廓线渲染器
 *
 * 使用 GL11 固定管线在 RenderWorldLastEvent 中绘制：
 * 1. 玩家 ESP：遍历世界玩家，绘制 AABB 轮廓线
 * 2. 弹道 ESP：遍历被追踪弹道，绘制白色轮廓线
 */
public class EspRenderer {

    /**
     * 在 RenderWorldLastEvent 中调用
     */
    public static void render(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        RenderManager renderManager = mc.getRenderManager();
        double renderPosX = renderManager.viewerPosX;
        double renderPosY = renderManager.viewerPosY;
        double renderPosZ = renderManager.viewerPosZ;

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        // 1. 玩家 ESP
        if (PlayerHighlightMod.isHighlightEnabled()) {
            GL11.glLineWidth(2.0f);
            for (Object obj : mc.theWorld.playerEntities) {
                EntityPlayer player = (EntityPlayer) obj;
                if (player == mc.thePlayer && mc.gameSettings.thirdPersonView == 0) {
                    continue;
                }
                if (!RenderHelper.shouldRenderOutline(player)) {
                    continue;
                }

                int color = RenderHelper.getPlayerOutlineColor(player);
                float r = ((color >> 16) & 0xFF) / 255.0F;
                float g = ((color >> 8) & 0xFF) / 255.0F;
                float b = (color & 0xFF) / 255.0F;

                AxisAlignedBB box = getInterpolatedBoundingBox(player, partialTicks, renderPosX, renderPosY, renderPosZ);
                RenderGlobal.drawOutlinedBoundingBox(box, (int)(r * 255), (int)(g * 255), (int)(b * 255), 255);
            }
        }

        // 2. 弹道 ESP
        if (PlayerHighlightMod.isProjectilePredictionEnabled()) {
            GL11.glLineWidth(1.5f);
            Collection<TrackedProjectile> projectiles = ProjectileTrackerClient.getActiveProjectiles();
            for (TrackedProjectile tracked : projectiles) {
                Entity entity = findEntityByUuid(mc, tracked.getUuid());
                if (entity == null || entity.isDead) {
                    continue;
                }

                AxisAlignedBB box = getInterpolatedBoundingBox(entity, partialTicks, renderPosX, renderPosY, renderPosZ);
                // 白色轮廓
                RenderGlobal.drawOutlinedBoundingBox(box, 255, 255, 255, 200);
            }
        }

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private static AxisAlignedBB getInterpolatedBoundingBox(Entity entity, float partialTicks,
                                                             double renderPosX, double renderPosY, double renderPosZ) {
        double dx = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - renderPosX;
        double dy = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - renderPosY;
        double dz = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - renderPosZ;

        AxisAlignedBB entityBox = entity.getEntityBoundingBox();
        return entityBox.offset(
                -entity.posX + dx,
                -entity.posY + dy,
                -entity.posZ + dz
        );
    }

    private static Entity findEntityByUuid(Minecraft mc, java.util.UUID uuid) {
        if (mc.theWorld == null || uuid == null) {
            return null;
        }
        for (Object obj : mc.theWorld.loadedEntityList) {
            Entity entity = (Entity) obj;
            if (uuid.equals(entity.getUniqueID())) {
                return entity;
            }
        }
        return null;
    }
}

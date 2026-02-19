package com.example.playerhighlight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.Collection;
import java.util.List;

/**
 * 轨迹线 + 落点标记 + 弓预瞄渲染器
 *
 * 合并原 LandingPointRenderer + BowPreviewRenderer
 * 在 RenderWorldLastEvent 中调用
 */
public class TrajectoryRenderer {

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

        ModConfig config = ModConfig.getInstance();
        double alertRange = config.getNearbyWarningRange();
        Vec3 playerPos = mc.thePlayer.getPositionVector();

        // 1. 已发射弹道的轨迹线和落点
        if (PlayerHighlightMod.isProjectilePredictionEnabled()) {
            Collection<TrackedProjectile> projectiles = ProjectileTrackerClient.getActiveProjectiles();
            for (TrackedProjectile tracked : projectiles) {
                ProjectilePrediction prediction = tracked.getPrediction();
                if (prediction == null) continue;

                Vec3 landingPos = prediction.landingPos;
                boolean isNear = landingPos.distanceTo(playerPos) <= alertRange;

                float r = 1.0f;
                float g = isNear ? 0.0f : 1.0f;
                float b = 0.0f;
                float a = 0.8f;

                // 轨迹线
                if (PlayerHighlightMod.isTrajectoryLineEnabled()) {
                    GL11.glLineWidth(4.0f);
                    drawTrajectoryLine(prediction.trajectoryPoints, isNear,
                            renderPosX, renderPosY, renderPosZ, 0.4f);
                }

                // 落点方块轮廓
                drawLandingBox(landingPos, r, g, b, a, renderPosX, renderPosY, renderPosZ);
            }
        }

        // 2. 弓预瞄
        if (config.isBowPreviewEnabled()) {
            BowPreviewClient.BowPreviewState state = BowPreviewClient.getCurrent();
            if (state != null && state.prediction != null) {
                ProjectilePrediction prediction = state.prediction;
                Vec3 landingPos = prediction.landingPos;
                boolean isNear = landingPos.distanceTo(playerPos) <= alertRange;

                if (config.isBowPreviewTrajectoryEnabled()) {
                    GL11.glLineWidth(3.0f);
                    drawTrajectoryLine(prediction.trajectoryPoints, isNear,
                            renderPosX, renderPosY, renderPosZ, 0.2f);
                }

                if (config.isBowPreviewLandingMarkerEnabled()) {
                    float r = 1.0f;
                    float g = isNear ? 0.0f : 1.0f;
                    float b = 0.0f;
                    float a = 0.85f;
                    drawLandingBox(landingPos, r, g, b, a, renderPosX, renderPosY, renderPosZ);
                }
            }
        }

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    /**
     * 绘制轨迹线（GL_LINES 渐变色）
     */
    private static void drawTrajectoryLine(List<Vec3> trajectoryPoints, boolean isNear,
                                            double renderPosX, double renderPosY, double renderPosZ,
                                            float startRedBase) {
        if (trajectoryPoints == null || trajectoryPoints.size() < 2) {
            return;
        }

        int totalPoints = trajectoryPoints.size();

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        for (int i = 0; i < totalPoints - 1; i++) {
            Vec3 start = trajectoryPoints.get(i);
            Vec3 end = trajectoryPoints.get(i + 1);

            float progress = (float) i / (totalPoints - 1);

            float startR = startRedBase + progress * (1.0f - startRedBase);
            float startG = isNear ? (0.9f * (1 - progress)) : (0.9f + progress * 0.1f);
            float startB = 1.0f * (1 - progress);
            float alpha = 0.9f;

            float endProgress = (float) (i + 1) / (totalPoints - 1);
            float endR = startRedBase + endProgress * (1.0f - startRedBase);
            float endG = isNear ? (0.9f * (1 - endProgress)) : (0.9f + endProgress * 0.1f);
            float endB = 1.0f * (1 - endProgress);

            int sR = (int)(startR * 255); int sG2 = (int)(startG * 255); int sB = (int)(startB * 255); int sA = (int)(alpha * 255);
            int eR = (int)(endR * 255); int eG2 = (int)(endG * 255); int eB = (int)(endB * 255);

            wr.pos(start.xCoord - renderPosX, start.yCoord - renderPosY, start.zCoord - renderPosZ)
                    .color(sR, sG2, sB, sA).endVertex();
            wr.pos(end.xCoord - renderPosX, end.yCoord - renderPosY, end.zCoord - renderPosZ)
                    .color(eR, eG2, eB, sA).endVertex();

            // 偏移线段增加可见性
            double offset = 0.02;
            wr.pos(start.xCoord + offset - renderPosX, start.yCoord - renderPosY, start.zCoord - renderPosZ)
                    .color(sR, sG2, sB, (int)(alpha * 0.7f * 255)).endVertex();
            wr.pos(end.xCoord + offset - renderPosX, end.yCoord - renderPosY, end.zCoord - renderPosZ)
                    .color(eR, eG2, eB, (int)(alpha * 0.7f * 255)).endVertex();

            wr.pos(start.xCoord - renderPosX, start.yCoord - renderPosY, start.zCoord + offset - renderPosZ)
                    .color(sR, sG2, sB, (int)(alpha * 0.7f * 255)).endVertex();
            wr.pos(end.xCoord - renderPosX, end.yCoord - renderPosY, end.zCoord + offset - renderPosZ)
                    .color(eR, eG2, eB, (int)(alpha * 0.7f * 255)).endVertex();
        }

        tessellator.draw();
    }

    /**
     * 绘制落点方块轮廓
     */
    private static void drawLandingBox(Vec3 center, float r, float g, float b, float a,
                                        double renderPosX, double renderPosY, double renderPosZ) {
        double size = 0.5;
        AxisAlignedBB box = new AxisAlignedBB(
                center.xCoord - size - renderPosX,
                center.yCoord - size - renderPosY,
                center.zCoord - size - renderPosZ,
                center.xCoord + size - renderPosX,
                center.yCoord + size - renderPosY,
                center.zCoord + size - renderPosZ
        );

        GL11.glLineWidth(2.0f);
        RenderGlobal.drawOutlinedBoundingBox(box, (int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255));
    }
}

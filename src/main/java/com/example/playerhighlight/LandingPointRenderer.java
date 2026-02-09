package com.example.playerhighlight;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 落点标记和轨迹线渲染器
 *
 * 在预测的落点位置绘制方块轮廓标记
 * 同时绘制弹道轨迹线
 * 红色表示危险（警告范围内），黄色表示安全
 */
public class LandingPointRenderer {

    /**
     * 初始化渲染器
     */
    public static void initialize() {
        PlayerHighlightClient.LOGGER.info("Landing Point Renderer initializing...");

        WorldRenderEvents.AFTER_ENTITIES.register(LandingPointRenderer::renderLandingPoints);

        PlayerHighlightClient.LOGGER.info("Landing Point Renderer initialized!");
    }

    /**
     * 渲染所有落点标记和轨迹线
     */
    private static void renderLandingPoints(WorldRenderContext context) {
        // 如果弹道预测功能关闭，跳过渲染
        if (!PlayerHighlightClient.isProjectilePredictionEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        double alertRange = ModConfig.getInstance().getNearbyWarningRange();

        MatrixStack matrices = context.matrixStack();
        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();

        // 准备渲染状态
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(4.0f);  // 增加线宽到4像素

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        matrices.push();

        // 移动到相对于摄像机的坐标系
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        // 渲染每个追踪弹道（包括击中后3秒内的弹道）
        for (TrackedProjectile tracked : ProjectileTrackerClient.getActiveProjectiles()) {
            ProjectilePrediction prediction = tracked.getPrediction();
            Vec3d landingPos = prediction.landingPos;
            boolean isNear = landingPos.distanceTo(client.player.getPos()) <= alertRange;

            // 颜色：红色（危险）或黄色（安全）
            float r = 1.0f;
            float g = isNear ? 0.0f : 1.0f;
            float b = 0.0f;
            float a = 0.8f;

            // 1. 绘制轨迹线（如果启用）
            if (PlayerHighlightClient.isTrajectoryLineEnabled()) {
                drawTrajectoryLine(matrices, buffer, prediction.trajectoryPoints, isNear);
            }

            // 2. 绘制落点方块轮廓
            drawBox(matrices, buffer, landingPos, r, g, b, a);
        }

        tessellator.draw();

        matrices.pop();

        // 恢复渲染状态
        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * 绘制轨迹线
     * 使用渐变颜色和多重绘制增加可见性
     */
    private static void drawTrajectoryLine(MatrixStack matrices, BufferBuilder buffer,
                                            List<Vec3d> trajectoryPoints, boolean isNear) {
        if (trajectoryPoints == null || trajectoryPoints.size() < 2) {
            return;
        }

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int totalPoints = trajectoryPoints.size();

        for (int i = 0; i < totalPoints - 1; i++) {
            Vec3d start = trajectoryPoints.get(i);
            Vec3d end = trajectoryPoints.get(i + 1);

            // 计算渐变进度 (0.0 到 1.0)
            float progress = (float) i / (totalPoints - 1);

            // 颜色渐变：青色 -> 红/黄
            // 起点：亮青色 (0.4, 0.9, 1.0)
            // 终点：红色（危险）或黄色（安全）
            float startR = 0.4f + progress * 0.6f;
            float startG = isNear ? (0.9f * (1 - progress)) : (0.9f + progress * 0.1f);
            float startB = 1.0f * (1 - progress);
            float alpha = 0.9f;  // 增加透明度到0.9

            float endProgress = (float) (i + 1) / (totalPoints - 1);
            float endR = 0.4f + endProgress * 0.6f;
            float endG = isNear ? (0.9f * (1 - endProgress)) : (0.9f + endProgress * 0.1f);
            float endB = 1.0f * (1 - endProgress);
            float endAlpha = 0.9f;

            // 绘制主线段
            buffer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z)
                    .color(startR, startG, startB, alpha).next();
            buffer.vertex(matrix, (float) end.x, (float) end.y, (float) end.z)
                    .color(endR, endG, endB, endAlpha).next();

            // 绘制偏移线段增加粗细（沿X轴微小偏移）
            double offset = 0.02;
            buffer.vertex(matrix, (float) (start.x + offset), (float) start.y, (float) start.z)
                    .color(startR, startG, startB, alpha * 0.7f).next();
            buffer.vertex(matrix, (float) (end.x + offset), (float) end.y, (float) end.z)
                    .color(endR, endG, endB, endAlpha * 0.7f).next();

            // 绘制偏移线段（沿Z轴微小偏移）
            buffer.vertex(matrix, (float) start.x, (float) start.y, (float) (start.z + offset))
                    .color(startR, startG, startB, alpha * 0.7f).next();
            buffer.vertex(matrix, (float) end.x, (float) end.y, (float) (end.z + offset))
                    .color(endR, endG, endB, endAlpha * 0.7f).next();
        }
    }

    /**
     * 绘制方块轮廓
     */
    private static void drawBox(MatrixStack matrices, BufferBuilder buffer, Vec3d center,
                                 float r, float g, float b, float a) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // 方块大小：以 center 为中心的 1x1x1 方块
        float size = 0.5f;
        float minX = (float) center.x - size;
        float minY = (float) center.y - size;
        float minZ = (float) center.z - size;
        float maxX = (float) center.x + size;
        float maxY = (float) center.y + size;
        float maxZ = (float) center.z + size;

        // 绘制12条边（立方体有12条边）

        // 底面4条边
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).next();
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).next();

        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).next();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).next();

        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).next();
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).next();

        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).next();
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).next();

        // 顶面4条边
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).next();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).next();

        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).next();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).next();

        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).next();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).next();

        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).next();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).next();

        // 垂直4条边
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).next();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).next();

        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).next();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).next();

        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).next();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).next();

        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).next();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).next();
    }
}

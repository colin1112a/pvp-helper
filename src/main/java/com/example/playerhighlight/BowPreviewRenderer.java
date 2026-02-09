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
 * 弓箭预瞄预测渲染器
 *
 * 拉弓时绘制“未射出箭矢”的预测轨迹与落点标记。
 */
public final class BowPreviewRenderer {
    private BowPreviewRenderer() {
    }

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(BowPreviewRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }

        ModConfig config = ModConfig.getInstance();
        if (!config.isBowPreviewEnabled()) {
            return;
        }

        BowPreviewClient.BowPreviewState state = BowPreviewClient.getCurrent();
        if (state == null || state.prediction == null) {
            return;
        }

        ProjectilePrediction prediction = state.prediction;
        Vec3d landingPos = prediction.landingPos;

        double alertRange = ModConfig.getInstance().getNearbyWarningRange();
        boolean isNear = landingPos.distanceTo(client.player.getPos()) <= alertRange;

        MatrixStack matrices = context.matrixStack();
        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(3.0f);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        if (config.isBowPreviewTrajectoryEnabled()) {
            drawTrajectoryLine(matrices, buffer, prediction.trajectoryPoints, isNear);
        }
        if (config.isBowPreviewLandingMarkerEnabled()) {
            float r = 1.0f;
            float g = isNear ? 0.0f : 1.0f;
            float b = 0.0f;
            float a = 0.85f;
            drawBox(matrices, buffer, landingPos, r, g, b, a);
        }

        tessellator.draw();

        matrices.pop();

        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

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

            float progress = (float) i / (totalPoints - 1);

            // 起点：更亮的青色，终点：红/黄（nearby 警告一致）
            float startR = 0.2f + progress * 0.8f;
            float startG = isNear ? (0.95f * (1 - progress)) : (0.95f);
            float startB = 1.0f * (1 - progress);
            float alpha = 0.85f;

            float endProgress = (float) (i + 1) / (totalPoints - 1);
            float endR = 0.2f + endProgress * 0.8f;
            float endG = isNear ? (0.95f * (1 - endProgress)) : (0.95f);
            float endB = 1.0f * (1 - endProgress);
            float endAlpha = 0.85f;

            buffer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z)
                    .color(startR, startG, startB, alpha).next();
            buffer.vertex(matrix, (float) end.x, (float) end.y, (float) end.z)
                    .color(endR, endG, endB, endAlpha).next();
        }
    }

    private static void drawBox(MatrixStack matrices, BufferBuilder buffer, Vec3d center,
                                float r, float g, float b, float a) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float size = 0.5f;
        float minX = (float) center.x - size;
        float minY = (float) center.y - size;
        float minZ = (float) center.z - size;
        float maxX = (float) center.x + size;
        float maxY = (float) center.y + size;
        float maxZ = (float) center.z + size;

        // 底面
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).next();
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).next();

        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).next();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).next();

        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).next();
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).next();

        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).next();
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).next();

        // 顶面
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).next();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).next();

        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).next();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).next();

        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).next();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).next();

        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).next();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).next();

        // 垂直边
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


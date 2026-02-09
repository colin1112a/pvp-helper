package com.example.playerhighlight;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * 弓箭预瞄预测（未射出前）
 *
 * 玩家拉弓时，基于当前视角与拉弓进度预测箭矢轨迹与落点，用于 PVP 预判。
 */
public final class BowPreviewClient {
    private BowPreviewClient() {
    }

    private static volatile BowPreviewState current;

    // 缓存：输入变化不大时不重算（避免每帧/每tick重复模拟）
    private static float lastYaw = Float.NaN;
    private static float lastPitch = Float.NaN;
    private static float lastPull = Float.NaN;
    private static Vec3d lastPos = null;
    private static Vec3d lastShooterVel = null;
    private static boolean lastOnGround = false;
    private static String lastTypeId = null;
    private static long previewInaccuracySeed = 0L;

    // 触发重算阈值
    private static final float EPS_YAW_PITCH = 0.25f;
    private static final float EPS_PULL = 0.01f;
    private static final double EPS_POS_SQ = 0.05 * 0.05;
    private static final double EPS_VEL_SQ = 0.01 * 0.01;
    private static final float VANILLA_BOW_DIVERGENCE = 1.0f;

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(BowPreviewClient::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            clear();
            return;
        }

        ModConfig config = ModConfig.getInstance();
        if (!config.isBowPreviewEnabled()) {
            clear();
            return;
        }

        PlayerEntity player = client.player;
        if (!player.isUsingItem()) {
            clear();
            return;
        }

        ItemStack active = player.getActiveItem();
        if (active == null || active.isEmpty() || !active.isOf(Items.BOW)) {
            clear();
            return;
        }

        int useTicks = active.getMaxUseTime() - player.getItemUseTimeLeft();
        float pullProgress = BowItem.getPullProgress(useTicks);
        if (pullProgress < 0.10f) {
            clear();
            return;
        }

        String typeId = BowEnchantmentDetector.detectBowTypeId(active);
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        Vec3d pos = player.getPos();
        Vec3d shooterVel = player.getVelocity();
        boolean onGround = player.isOnGround();

        if (!shouldRecalculate(typeId, yaw, pitch, pullProgress, pos, shooterVel, onGround)) {
            return;
        }

        if (current == null) {
            previewInaccuracySeed = createPreviewSeed(player.getUuid(), client.world.getTime());
        }

        Vec3d startPos = new Vec3d(player.getX(), player.getEyeY() - 0.1, player.getZ());
        Vec3d direction = player.getRotationVec(1.0f);
        Vec3d shotDirection = config.isBowPreviewSimulateInaccuracy()
                ? ProjectileLaunchMath.applyDivergence(direction, VANILLA_BOW_DIVERGENCE, previewInaccuracySeed)
                : direction;
        double speed = pullProgress * 3.0; // Vanilla bow: speed = pullProgress * 3.0
        double addY = onGround ? 0.0 : shooterVel.y;
        Vec3d initialVel = shotDirection.multiply(speed).add(shooterVel.x, addY, shooterVel.z);

        ProjectilePrediction prediction = PhysicsSimulator.predictTrajectoryFromState(
                startPos,
                initialVel,
                typeId,
                client.world,
                config.getBowPreviewMaxTicks(),
                player,
                false
        );

        current = new BowPreviewState(typeId, pullProgress, startPos, initialVel, prediction);

        lastTypeId = typeId;
        lastYaw = yaw;
        lastPitch = pitch;
        lastPull = pullProgress;
        lastPos = pos;
        lastShooterVel = shooterVel;
        lastOnGround = onGround;
    }

    private static long createPreviewSeed(UUID uuid, long worldTime) {
        long u1 = uuid != null ? uuid.getMostSignificantBits() : 0L;
        long u2 = uuid != null ? uuid.getLeastSignificantBits() : 0L;
        return u1 ^ u2 ^ worldTime ^ System.nanoTime();
    }

    private static boolean shouldRecalculate(String typeId, float yaw, float pitch, float pull, Vec3d pos,
                                             Vec3d shooterVel, boolean onGround) {
        if (current == null) {
            return true;
        }
        if (lastTypeId == null || !lastTypeId.equals(typeId)) {
            return true;
        }
        if (Math.abs(yaw - lastYaw) > EPS_YAW_PITCH || Math.abs(pitch - lastPitch) > EPS_YAW_PITCH) {
            return true;
        }
        if (Math.abs(pull - lastPull) > EPS_PULL) {
            return true;
        }
        if (onGround != lastOnGround) {
            return true;
        }
        if (lastPos == null || pos == null) {
            return true;
        }
        if (lastPos.squaredDistanceTo(pos) > EPS_POS_SQ) {
            return true;
        }
        if (lastShooterVel == null || shooterVel == null) {
            return true;
        }
        return lastShooterVel.squaredDistanceTo(shooterVel) > EPS_VEL_SQ;
    }

    private static void clear() {
        current = null;
        lastTypeId = null;
        lastYaw = Float.NaN;
        lastPitch = Float.NaN;
        lastPull = Float.NaN;
        lastPos = null;
        lastShooterVel = null;
        lastOnGround = false;
        previewInaccuracySeed = 0L;
    }

    public static BowPreviewState getCurrent() {
        return current;
    }

    public static final class BowPreviewState {
        public final String typeId;
        public final float pullProgress;
        public final Vec3d startPos;
        public final Vec3d initialVel;
        public final ProjectilePrediction prediction;

        BowPreviewState(String typeId, float pullProgress, Vec3d startPos, Vec3d initialVel, ProjectilePrediction prediction) {
            this.typeId = typeId;
            this.pullProgress = pullProgress;
            this.startPos = startPos;
            this.initialVel = initialVel;
            this.prediction = prediction;
        }
    }
}

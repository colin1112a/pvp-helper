package com.example.playerhighlight;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;

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

    // 缓存：输入变化不大时不重算
    private static float lastYaw = Float.NaN;
    private static float lastPitch = Float.NaN;
    private static float lastPull = Float.NaN;
    private static Vec3 lastPos = null;
    private static Vec3 lastShooterVel = null;
    private static boolean lastOnGround = false;
    private static String lastTypeId = null;
    private static long previewInaccuracySeed = 0L;

    // 触发重算阈值
    private static final float EPS_YAW_PITCH = 0.25f;
    private static final float EPS_PULL = 0.01f;
    private static final double EPS_POS_SQ = 0.05 * 0.05;
    private static final double EPS_VEL_SQ = 0.01 * 0.01;
    private static final float VANILLA_BOW_DIVERGENCE = 1.0f;

    /**
     * 初始化（Forge: 不注册事件，tick 由 ForgeEventHandler 调用）
     */
    public static void initialize() {
        // No-op for Forge; tick is called externally
    }

    /**
     * 每 tick 调用（由 ForgeEventHandler 调用）
     */
    public static void tick(Minecraft mc) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            clear();
            return;
        }

        ModConfig config = ModConfig.getInstance();
        if (!config.isBowPreviewEnabled()) {
            clear();
            return;
        }

        EntityPlayer player = mc.thePlayer;
        if (!player.isUsingItem()) {
            clear();
            return;
        }

        ItemStack active = player.getItemInUse();
        if (active == null || active.getItem() != Items.bow) {
            clear();
            return;
        }

        int useTicks = player.getItemInUseDuration();
        float pullProgress = getBowPullProgress(useTicks);
        if (pullProgress < 0.10f) {
            clear();
            return;
        }

        String typeId = BowEnchantmentDetector.detectBowTypeId(active);
        float yaw = player.rotationYaw;
        float pitch = player.rotationPitch;
        Vec3 pos = player.getPositionVector();
        Vec3 shooterVel = new Vec3(player.motionX, player.motionY, player.motionZ);
        boolean onGround = player.onGround;

        if (!shouldRecalculate(typeId, yaw, pitch, pullProgress, pos, shooterVel, onGround)) {
            return;
        }

        if (current == null) {
            previewInaccuracySeed = createPreviewSeed(player.getUniqueID(), mc.theWorld.getTotalWorldTime());
        }


        Vec3 startPos = new Vec3(player.posX, player.posY + player.getEyeHeight() - 0.1, player.posZ);
        Vec3 direction = player.getLookVec();
        Vec3 shotDirection = config.isBowPreviewSimulateInaccuracy()
                ? ProjectileLaunchMath.applyDivergence(direction, VANILLA_BOW_DIVERGENCE, previewInaccuracySeed)
                : direction;
        double speed = pullProgress * 3.0;
        double addY = onGround ? 0.0 : shooterVel.yCoord;
        Vec3 initialVel = new Vec3(
                shotDirection.xCoord * speed + shooterVel.xCoord,
                shotDirection.yCoord * speed + addY,
                shotDirection.zCoord * speed + shooterVel.zCoord
        );

        ProjectilePrediction prediction = PhysicsSimulator.predictTrajectoryFromState(
                startPos,
                initialVel,
                typeId,
                mc.theWorld,
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

    /**
     * Manual bow pull progress calculation (1.8.9 has no BowItem.getPullProgress)
     */
    private static float getBowPullProgress(int useTicks) {
        float f = (float) useTicks / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        if (f > 1.0F) {
            f = 1.0F;
        }
        return f;
    }

    private static long createPreviewSeed(UUID uuid, long worldTime) {
        long u1 = uuid != null ? uuid.getMostSignificantBits() : 0L;
        long u2 = uuid != null ? uuid.getLeastSignificantBits() : 0L;
        return u1 ^ u2 ^ worldTime ^ System.nanoTime();
    }


    private static boolean shouldRecalculate(String typeId, float yaw, float pitch, float pull, Vec3 pos,
                                             Vec3 shooterVel, boolean onGround) {
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
        if (lastPos.squareDistanceTo(pos) > EPS_POS_SQ) {
            return true;
        }
        if (lastShooterVel == null || shooterVel == null) {
            return true;
        }
        return lastShooterVel.squareDistanceTo(shooterVel) > EPS_VEL_SQ;
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
        public final Vec3 startPos;
        public final Vec3 initialVel;
        public final ProjectilePrediction prediction;

        BowPreviewState(String typeId, float pullProgress, Vec3 startPos, Vec3 initialVel, ProjectilePrediction prediction) {
            this.typeId = typeId;
            this.pullProgress = pullProgress;
            this.startPos = startPos;
            this.initialVel = initialVel;
            this.prediction = prediction;
        }
    }
}

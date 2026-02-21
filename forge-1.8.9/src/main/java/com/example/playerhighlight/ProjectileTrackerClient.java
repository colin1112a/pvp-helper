package com.example.playerhighlight;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弹道追踪客户端 - 主协调器
 *
 * 负责检测、追踪和管理所有弹道实体
 */
public class ProjectileTrackerClient {

    // 配置常量
    private static final int MAX_TRACKED_PROJECTILES = 50;
    private static final int MAX_SIMULATION_TICKS = 200;
    private static final int CLEANUP_INTERVAL = 20;

    // 追踪状态
    private static final Map<UUID, TrackedProjectile> activeProjectiles = new ConcurrentHashMap<UUID, TrackedProjectile>();
    private static final Set<UUID> alertedProjectiles = new HashSet<UUID>();
    private static final Map<UUID, Long> ignoredProjectilesUntil = new ConcurrentHashMap<UUID, Long>();
    private static int tickCounter = 0;

    // 学习样本过滤
    private static final int ARROW_SPAM_WINDOW_TICKS = 60;
    private static final int ARROW_SPAM_THRESHOLD = 5;
    private static final int ARROW_SUPPRESS_TICKS = 10 * 60 * 20;
    private static final double FULLY_DRAWN_ARROW_SPEED_SQ = 2.75 * 2.75;
    private static final Map<UUID, Long> arrowLearningSuppressedUntilTick = new ConcurrentHashMap<UUID, Long>();
    private static final Map<UUID, ArrowSpamWindow> arrowSpamWindows = new ConcurrentHashMap<UUID, ArrowSpamWindow>();

    // 射手推断
    private static final Map<UUID, PlayerSnapshot> lastPlayerSnapshots = new ConcurrentHashMap<UUID, PlayerSnapshot>();

    private static final class ArrowShotEntry {
        final long tick;
        final UUID projectileUuid;

        ArrowShotEntry(long tick, UUID projectileUuid) {
            this.tick = tick;
            this.projectileUuid = projectileUuid;
        }
    }

    private static final class ArrowSpamWindow {
        final ArrayDeque<ArrowShotEntry> entries = new ArrayDeque<ArrowShotEntry>();
    }

    private static class PlayerSnapshot {
        final String name;
        final int entityId;
        final Vec3 position;  // 眼睛位置
        final Vec3 lookDirection;
        final long timestamp;

        PlayerSnapshot(String name, int entityId, Vec3 eyePosition, Vec3 lookDirection) {
            this.name = name;
            this.entityId = entityId;
            this.position = eyePosition;
            this.lookDirection = lookDirection;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * 初始化弹道追踪系统（Forge: 不注册事件，tick 由 ForgeEventHandler 调用）
     */
    public static void initialize() {
        PlayerHighlightMod.LOGGER.info("Projectile Tracker initializing...");
        PlayerHighlightMod.LOGGER.info("Projectile Tracker initialized!");
    }

    /**
     * 每 tick 调用（由 ForgeEventHandler 调用）
     */
    public static void tick(Minecraft mc) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        if (!PlayerHighlightMod.isProjectilePredictionEnabled()) {
            if (!activeProjectiles.isEmpty()) {
                activeProjectiles.clear();
                alertedProjectiles.clear();
            }
            return;
        }

        tickCounter++;

        updateTrackedProjectiles(mc);

        if (tickCounter % CLEANUP_INTERVAL == 0) {
            cleanupDeadProjectiles(mc);
        }
    }

    /**
     * 更新追踪的弹道列表
     */
    private static void updateTrackedProjectiles(Minecraft mc) {
        pruneIgnored();
        updatePlayerSnapshots(mc);

        Set<UUID> currentProjectiles = new HashSet<UUID>();

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (isTrackableProjectile(entity)) {
                UUID id = entity.getUniqueID();
                if (isIgnored(id)) {
                    continue;
                }
                currentProjectiles.add(id);

                if (!activeProjectiles.containsKey(id)) {
                    if (activeProjectiles.size() < MAX_TRACKED_PROJECTILES) {
                        trackNewProjectile(mc, entity);
                    }
                } else {
                    TrackedProjectile tracked = activeProjectiles.get(id);
                    tracked.updateLastSeen();

                    if (!tracked.hasLanded()) {
                        TrajectoryRecorder.recordPosition(id, entity.getPositionVector());
                    }
                }
            }
        }

        for (TrackedProjectile tracked : activeProjectiles.values()) {
            if (!currentProjectiles.contains(tracked.getUuid()) && !tracked.hasLanded()) {
                markProjectileAsLanded(tracked.getUuid(), null);
            }
        }
    }

    private static void updatePlayerSnapshots(Minecraft mc) {
        if (mc.theWorld == null) return;

        for (Object obj : mc.theWorld.playerEntities) {
            EntityPlayer player = (EntityPlayer) obj;
            Vec3 lookDir = player.getLookVec();
            Vec3 eyePos = new Vec3(player.posX, player.posY + (double) player.getEyeHeight(), player.posZ);
            PlayerSnapshot snapshot = new PlayerSnapshot(
                    player.getName(),
                    player.getEntityId(),
                    eyePos,
                    lookDir
            );
            lastPlayerSnapshots.put(player.getUniqueID(), snapshot);
        }

        long currentTime = System.currentTimeMillis();
        java.util.Iterator<Map.Entry<UUID, PlayerSnapshot>> it = lastPlayerSnapshots.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PlayerSnapshot> entry = it.next();
            if ((currentTime - entry.getValue().timestamp) > 5000) {
                it.remove();
            }
        }
    }

    private static void trackNewProjectile(Minecraft mc, Entity projectile) {
        String shooterName = getShooterName(projectile);
        boolean localPlayerShot = isLocalPlayerShot(mc, projectile);
        String typeId = detectProjectileType(projectile);

        if (shouldStartCalibrationRecording(mc, projectile, typeId)) {
            TrajectoryRecorder.startRecording(
                projectile.getUniqueID(),
                typeId,
                projectile.getPositionVector(),
                new Vec3(projectile.motionX, projectile.motionY, projectile.motionZ),
                localPlayerShot
            );
        }

        ProjectilePrediction prediction = PhysicsSimulator.predictTrajectory(
                projectile,
                typeId,
                mc.theWorld,
                MAX_SIMULATION_TICKS
        );

        Vec3 playerPos = mc.thePlayer.getPositionVector();
        double distanceToLanding = prediction.landingPos.distanceTo(playerPos);
        double alertRange = ModConfig.getInstance().getNearbyWarningRange();
        boolean inRange = distanceToLanding <= alertRange;

        TrackedProjectile tracked = new TrackedProjectile(
                projectile.getUniqueID(),
                getProjectileName(projectile),
                shooterName,
                typeId,
                prediction,
                inRange
        );

        activeProjectiles.put(projectile.getUniqueID(), tracked);

        if (!alertedProjectiles.contains(projectile.getUniqueID())) {
            sendAlertMessage(mc, tracked);
            alertedProjectiles.add(projectile.getUniqueID());
        }
    }

    private static void sendAlertMessage(Minecraft mc, TrackedProjectile tracked) {
        String projectileType = tracked.getType();
        String shooterName = tracked.getShooterName();
        Vec3 landing = tracked.getPrediction().landingPos;
        double alertRange = ModConfig.getInstance().getNearbyWarningRange();
        boolean isNear = landing.distanceTo(mc.thePlayer.getPositionVector()) <= alertRange;

        String base = "[Projectile Alert] " + projectileType + " from " + shooterName
                + " | Landing: " + String.format("(%.1f, %.1f, %.1f)", landing.xCoord, landing.yCoord, landing.zCoord);
        if (isNear) {
            base = base + " [NEARBY WARNING]";
        }

        mc.thePlayer.addChatMessage(new ChatComponentText(base));
    }

    private static void cleanupDeadProjectiles(Minecraft mc) {
        long currentTime = System.currentTimeMillis();
        java.util.Iterator<Map.Entry<UUID, TrackedProjectile>> it = activeProjectiles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TrackedProjectile> entry = it.next();
            if (entry.getValue().isExpired(currentTime)) {
                alertedProjectiles.remove(entry.getKey());
                it.remove();
            }
        }

        TrajectoryRecorder.cleanup();
        CalibrationSystem.flushPendingLocalShots();
        pruneArrowLearningSuppression(mc);
    }

    private static boolean shouldStartCalibrationRecording(Minecraft mc, Entity projectile, String typeId) {
        if (mc == null || mc.theWorld == null || projectile == null) {
            return false;
        }
        if ("FIREBALL".equals(typeId)) {
            return false;
        }
        if (!BowEnchantmentDetector.TYPE_ARROW.equals(typeId)) {
            return true;
        }

        EntityPlayer shooter = getOwnerPlayer(projectile);
        if (shooter == null) {
            return false;
        }

        long nowTick = mc.theWorld.getTotalWorldTime();
        UUID shooterUuid = shooter.getUniqueID();

        if (isArrowLearningSuppressed(shooterUuid, nowTick)) {
            return false;
        }

        if (registerArrowShotAndMaybeSuppress(shooterUuid, projectile.getUniqueID(), nowTick)) {
            return false;
        }

        if (!isFullyDrawnBowArrow(projectile)) {
            return false;
        }

        return true;
    }

    private static EntityPlayer getOwnerPlayer(Entity projectile) {
        Entity owner = null;
        if (projectile instanceof EntityArrow) {
            owner = ((EntityArrow) projectile).shootingEntity;
        } else if (projectile instanceof EntityFireball) {
            owner = ((EntityFireball) projectile).shootingEntity;
        }
        if (owner instanceof EntityPlayer) {
            return (EntityPlayer) owner;
        }

        // 离线模式下 shootingEntity 经常为 null，回退到位置推断
        return inferOwnerPlayerFromPosition(projectile);
    }

    /**
     * 当 shootingEntity 为 null 时，通过位置和方向推断射手玩家实体。
     * 用于校准过滤（需要知道射手是玩家而非怪物）。
     */
    private static EntityPlayer inferOwnerPlayerFromPosition(Entity projectile) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            return null;
        }

        Vec3 projectilePos = projectile.getPositionVector();
        double vx = projectile.motionX;
        double vy = projectile.motionY;
        double vz = projectile.motionZ;
        double speedSq = vx * vx + vy * vy + vz * vz;
        if (speedSq < 1.0e-9) {
            return null;
        }
        double speed = Math.sqrt(speedSq);
        Vec3 projectileDir = new Vec3(vx / speed, vy / speed, vz / speed);

        EntityPlayer bestPlayer = null;
        double bestScore = Double.MAX_VALUE;

        for (Object obj : mc.theWorld.playerEntities) {
            EntityPlayer player = (EntityPlayer) obj;
            Vec3 eyePos = new Vec3(player.posX, player.posY + (double) player.getEyeHeight(), player.posZ);
            double distance = eyePos.distanceTo(projectilePos);
            if (distance > 5.0) continue;

            Vec3 lookDir = player.getLookVec();
            double dot = lookDir.dotProduct(projectileDir);
            double angle = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
            if (angle > Math.toRadians(30.0)) continue;

            double score = distance * 0.5 + angle * 3.0;
            if (score < bestScore) {
                bestScore = score;
                bestPlayer = player;
            }
        }

        return bestScore <= 3.0 ? bestPlayer : null;
    }

    private static boolean isFullyDrawnBowArrow(Entity projectile) {
        if (projectile instanceof EntityArrow) {
            EntityArrow arrow = (EntityArrow) projectile;
            if (arrow.getIsCritical()) {
                return true;
            }
            double vx = projectile.motionX;
            double vy = projectile.motionY;
            double vz = projectile.motionZ;
            double speedSq = vx * vx + vy * vy + vz * vz;
            return speedSq >= FULLY_DRAWN_ARROW_SPEED_SQ;
        }
        return false;
    }

    private static boolean isArrowLearningSuppressed(UUID shooterUuid, long nowTick) {
        Long until = arrowLearningSuppressedUntilTick.get(shooterUuid);
        if (until == null) {
            return false;
        }
        if (nowTick >= until.longValue()) {
            arrowLearningSuppressedUntilTick.remove(shooterUuid);
            arrowSpamWindows.remove(shooterUuid);
            return false;
        }
        return true;
    }

    private static void pruneArrowLearningSuppression(Minecraft mc) {
        if (mc == null || mc.theWorld == null) {
            return;
        }
        long nowTick = mc.theWorld.getTotalWorldTime();
        java.util.Iterator<Map.Entry<UUID, Long>> it1 = arrowLearningSuppressedUntilTick.entrySet().iterator();
        while (it1.hasNext()) {
            if (nowTick >= it1.next().getValue().longValue()) {
                it1.remove();
            }
        }
        java.util.Iterator<Map.Entry<UUID, ArrowSpamWindow>> it2 = arrowSpamWindows.entrySet().iterator();
        while (it2.hasNext()) {
            Map.Entry<UUID, ArrowSpamWindow> entry = it2.next();
            ArrowSpamWindow window = entry.getValue();
            if (window == null) {
                it2.remove();
                continue;
            }
            pruneArrowSpamWindow(window, nowTick);
            if (window.entries.isEmpty()) {
                it2.remove();
            }
        }
    }

    private static boolean registerArrowShotAndMaybeSuppress(UUID shooterUuid, UUID projectileUuid, long nowTick) {
        ArrowSpamWindow window = arrowSpamWindows.get(shooterUuid);
        if (window == null) {
            window = new ArrowSpamWindow();
            arrowSpamWindows.put(shooterUuid, window);
        }
        pruneArrowSpamWindow(window, nowTick);

        window.entries.addLast(new ArrowShotEntry(nowTick, projectileUuid));
        if (window.entries.size() < ARROW_SPAM_THRESHOLD) {
            return false;
        }

        arrowLearningSuppressedUntilTick.put(shooterUuid, Long.valueOf(nowTick + ARROW_SUPPRESS_TICKS));
        for (ArrowShotEntry entry : window.entries) {
            TrajectoryRecorder.discardRecording(entry.projectileUuid);
        }
        window.entries.clear();
        return true;
    }

    private static void pruneArrowSpamWindow(ArrowSpamWindow window, long nowTick) {
        if (window == null) {
            return;
        }
        long minTick = nowTick - ARROW_SPAM_WINDOW_TICKS;
        while (!window.entries.isEmpty()) {
            ArrowShotEntry first = window.entries.peekFirst();
            if (first == null || first.tick >= minTick) {
                break;
            }
            window.entries.removeFirst();
        }
    }

    private static boolean isIgnored(UUID uuid) {
        Long until = ignoredProjectilesUntil.get(uuid);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until.longValue()) {
            ignoredProjectilesUntil.remove(uuid);
            return false;
        }
        return true;
    }

    private static void pruneIgnored() {
        long now = System.currentTimeMillis();
        java.util.Iterator<Map.Entry<UUID, Long>> it = ignoredProjectilesUntil.entrySet().iterator();
        while (it.hasNext()) {
            if (now >= it.next().getValue().longValue()) {
                it.remove();
            }
        }
    }

    public static void markProjectileAsLanded(UUID uuid, Vec3 landingPos) {
        TrackedProjectile tracked = activeProjectiles.get(uuid);
        if (tracked != null && !tracked.hasLanded()) {
            tracked.markAsLanded();
            TrajectoryRecorder.markLanded(uuid, landingPos);
        }
        ignoredProjectilesUntil.put(uuid, Long.valueOf(System.currentTimeMillis() + 120000));
    }

    public static boolean isProjectileTracked(UUID uuid) {
        return activeProjectiles.containsKey(uuid);
    }

    public static Collection<TrackedProjectile> getActiveProjectiles() {
        return activeProjectiles.values();
    }

    private static boolean isTrackableProjectile(Entity entity) {
        return entity instanceof EntityArrow
                || entity instanceof EntitySmallFireball
                || entity instanceof EntityLargeFireball;
    }

    private static String getProjectileName(Entity projectile) {
        if (projectile instanceof EntityArrow) return "Arrow";
        if (projectile instanceof EntitySmallFireball) return "Blaze Fireball";
        if (projectile instanceof EntityLargeFireball) return "Ghast Fireball";
        return "Unknown Projectile";
    }

    private static String getShooterName(Entity projectile) {
        Entity owner = null;

        if (projectile instanceof EntityArrow) {
            owner = ((EntityArrow) projectile).shootingEntity;
        } else if (projectile instanceof EntityFireball) {
            owner = ((EntityFireball) projectile).shootingEntity;
        }

        // 如果 owner 存在且是玩家，直接获取名称
        if (owner instanceof EntityPlayer) {
            return owner.getName();
        }

        // owner 为 null 或非玩家时（离线模式常见），通过位置和方向推断射手
        return inferShooterFromPosition(projectile);
    }

    private static boolean isLocalPlayerShot(Minecraft mc, Entity projectile) {
        if (mc == null || mc.thePlayer == null) {
            return false;
        }

        Entity owner = null;
        if (projectile instanceof EntityArrow) {
            owner = ((EntityArrow) projectile).shootingEntity;
        } else if (projectile instanceof EntityFireball) {
            owner = ((EntityFireball) projectile).shootingEntity;
        }

        if (owner == null) {
            // 离线模式下 shootingEntity 经常为 null，回退到位置+方向推断
            return inferIsLocalPlayerShot(mc, projectile);
        }

        // 优先 UUID 比较；离线模式下 UUID 可能不一致，回退到 Entity ID 比较
        if (owner.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
            return true;
        }
        if (owner.getEntityId() == mc.thePlayer.getEntityId()) {
            return true;
        }

        return false;
    }

    /**
     * 当 shootingEntity 为 null 时，通过位置和方向推断弹射物是否由本地玩家射出。
     * 条件：弹射物在本地玩家 2 格内 且 方向与玩家视线夹角 < 15°
     */
    private static boolean inferIsLocalPlayerShot(Minecraft mc, Entity projectile) {
        EntityPlayer player = mc.thePlayer;
        Vec3 playerPos = new Vec3(player.posX, player.posY + (double) player.getEyeHeight(), player.posZ);
        Vec3 projPos = projectile.getPositionVector();
        double distance = playerPos.distanceTo(projPos);
        if (distance > 2.0) {
            return false;
        }

        Vec3 playerLook = player.getLookVec();
        double vx = projectile.motionX;
        double vy = projectile.motionY;
        double vz = projectile.motionZ;
        double speedSq = vx * vx + vy * vy + vz * vz;
        if (speedSq < 1.0e-9) {
            return false;
        }
        double speed = Math.sqrt(speedSq);
        Vec3 projDir = new Vec3(vx / speed, vy / speed, vz / speed);
        double dot = playerLook.dotProduct(projDir);
        double angle = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
        // 15° 以内视为本地玩家射出
        return angle < Math.toRadians(15.0);
    }

    private static String inferShooterFromPosition(Entity projectile) {
        Vec3 projectilePos = projectile.getPositionVector();
        Vec3 rawVel = new Vec3(projectile.motionX, projectile.motionY, projectile.motionZ);
        double speedSq = rawVel.xCoord * rawVel.xCoord + rawVel.yCoord * rawVel.yCoord + rawVel.zCoord * rawVel.zCoord;
        if (speedSq < 1.0e-9) {
            return "Unknown";
        }
        Vec3 projectileVelocity = rawVel.normalize();

        String bestMatch = "Unknown";
        double bestScore = Double.MAX_VALUE;

        for (PlayerSnapshot snapshot : lastPlayerSnapshots.values()) {
            // snapshot.position 已经是眼睛位置
            double distance = snapshot.position.distanceTo(projectilePos);

            // 弹道必须在玩家附近（5格内，缩小范围提高精度）
            if (distance > 5.0) continue;

            double dotProduct = snapshot.lookDirection.dotProduct(projectileVelocity);
            double angle = Math.acos(Math.max(-1.0, Math.min(1.0, dotProduct)));

            // 角度超过 30° 直接排除
            if (angle > Math.toRadians(30.0)) continue;

            double score = distance * 0.5 + angle * 3.0;

            if (score < bestScore) {
                bestScore = score;
                bestMatch = snapshot.name;
            }
        }

        if (bestScore > 3.0) {
            return "Unknown";
        }

        return bestMatch;
    }

    private static String detectProjectileType(Entity projectile) {
        if (projectile instanceof EntitySmallFireball) {
            return "FIREBALL";
        } else if (projectile instanceof EntityLargeFireball) {
            return "FIREBALL";
        } else if (projectile instanceof EntityArrow) {
            return BowEnchantmentDetector.detectBowTypeId(projectile);
        } else {
            return "UNKNOWN";
        }
    }
}

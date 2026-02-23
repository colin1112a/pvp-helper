package com.example.playerhighlight;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弹道追踪客户端 - 主协调器
 *
 * 负责检测、追踪和管理所有弹道实体
 * 复用 PlayerHighlightClient 的事件注册模式
 */
public class ProjectileTrackerClient {

    // 配置常量
    private static final int MAX_TRACKED_PROJECTILES = 50;  // 最多追踪50个弹道
    private static final int MAX_SIMULATION_TICKS = 200;  // 最大模拟时间（约10秒）
    private static final int CLEANUP_INTERVAL = 20;  // 清理间隔（1秒）

    // 追踪状态
    private static final Map<UUID, TrackedProjectile> activeProjectiles = new ConcurrentHashMap<>();
    private static final Set<UUID> alertedProjectiles = new HashSet<>();
    private static final Map<UUID, Long> ignoredProjectilesUntil = new ConcurrentHashMap<>();
    private static int tickCounter = 0;

    // 学习样本过滤：仅满弓样本 + 排除怪物箭 + “箭雨玩家”限流（避免校准被异常样本带偏）
    private static final int ARROW_SPAM_WINDOW_TICKS = 60;  // 3秒
    private static final int ARROW_SPAM_THRESHOLD = 5;  // 3秒>=5支箭视为异常
    private static final int ARROW_SUPPRESS_TICKS = 10 * 60 * 20;  // 10分钟
    // “满弓”判定：原版满弓初速约 3.0；考虑到客户端扫描发生在 END_CLIENT_TICK，
    // 弹道可能已被空气阻力/重力更新过 1 次，因此用稍低阈值避免误判为“未拉满”。
    private static final double FULLY_DRAWN_ARROW_SPEED_SQ = 2.75 * 2.75;
    // shooterUuid -> suppressUntilWorldTick
    private static final Map<UUID, Long> arrowLearningSuppressedUntilTick = new ConcurrentHashMap<>();
    private static final Map<UUID, ArrowSpamWindow> arrowSpamWindows = new ConcurrentHashMap<>();

    private static final class ArrowShotEntry {
        final long tick;
        final UUID projectileUuid;

        ArrowShotEntry(long tick, UUID projectileUuid) {
            this.tick = tick;
            this.projectileUuid = projectileUuid;
        }
    }

    private static final class ArrowSpamWindow {
        final ArrayDeque<ArrowShotEntry> entries = new ArrayDeque<>();
    }

    // NEARBY 警报冷却：同一射手 3 秒内只发一条 NEARBY 聊天警告
    private static final long NEARBY_ALERT_COOLDOWN_MS = 3000;
    private static final Map<String, Long> nearbyAlertCooldowns = new ConcurrentHashMap<>();

    // 射手推断：记录每个玩家上次看到的位置和视线方向
    private static final Map<UUID, PlayerSnapshot> lastPlayerSnapshots = new ConcurrentHashMap<>();

    // 记录玩家快照（用于推断射手）— position 存储眼睛位置
    private static class PlayerSnapshot {
        final String name;
        final int entityId;
        final Vec3d position;  // 眼睛位置
        final Vec3d lookDirection;
        final long timestamp;

        PlayerSnapshot(String name, int entityId, Vec3d eyePosition, Vec3d lookDirection) {
            this.name = name;
            this.entityId = entityId;
            this.position = eyePosition;
            this.lookDirection = lookDirection;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * 初始化弹道追踪系统
     */
    public static void initialize() {
        PlayerHighlightClient.LOGGER.info("Projectile Tracker initializing...");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) {
                return;
            }

            // 如果弹道预测功能被关闭，清空追踪列表并跳过
            if (!PlayerHighlightClient.isProjectilePredictionEnabled()) {
                if (!activeProjectiles.isEmpty()) {
                    activeProjectiles.clear();
                    alertedProjectiles.clear();
                }
                return;
            }

            tickCounter++;

            // 更新追踪的弹道
            updateTrackedProjectiles(client);

            // 定期清理过期数据
            if (tickCounter % CLEANUP_INTERVAL == 0) {
                cleanupDeadProjectiles(client);
            }
        });

        PlayerHighlightClient.LOGGER.info("Projectile Tracker initialized!");
    }

    /**
     * 更新追踪的弹道列表
     */
    private static void updateTrackedProjectiles(MinecraftClient client) {
        pruneIgnored();

        // 更新所有玩家的快照
        updatePlayerSnapshots(client);

        // 标记所有当前存在于世界中的弹道
        Set<UUID> currentProjectiles = new HashSet<>();

        // 扫描世界中的所有实体
        for (Entity entity : client.world.getEntities()) {
            if (isTrackableProjectile(entity)) {
                UUID id = entity.getUuid();
                if (isIgnored(id)) {
                    continue;
                }
                currentProjectiles.add(id);

                if (!activeProjectiles.containsKey(id)) {
                    // 发现新弹道
                    if (activeProjectiles.size() < MAX_TRACKED_PROJECTILES) {
                        trackNewProjectile(client, entity);
                    }
                } else {
                    // 更新已有弹道的最后见到时间
                    TrackedProjectile tracked = activeProjectiles.get(id);
                    tracked.updateLastSeen();

                    // 记录当前位置（用于校准）
                    if (!tracked.hasLanded()) {
                        TrajectoryRecorder.recordPosition(id, entity.getPos());
                    }
                }
            }
        }

        // 检查已追踪的弹道是否仍在世界中，如果不在则标记为已击中（仅触发一次）
        for (TrackedProjectile tracked : activeProjectiles.values()) {
            if (!currentProjectiles.contains(tracked.getUuid()) && !tracked.hasLanded()) {
                markProjectileAsLanded(tracked.getUuid(), null);
            }
        }
    }

    /**
     * 更新所有玩家的位置快照
     */
    private static void updatePlayerSnapshots(MinecraftClient client) {
        if (client.world == null) return;

        for (PlayerEntity player : client.world.getPlayers()) {
            Vec3d lookDir = player.getRotationVec(1.0f);
            Vec3d eyePos = player.getPos().add(0, player.getStandingEyeHeight(), 0);
            PlayerSnapshot snapshot = new PlayerSnapshot(
                    player.getName().getString(),
                    player.getId(),
                    eyePos,
                    lookDir
            );
            lastPlayerSnapshots.put(player.getUuid(), snapshot);
        }

        // 清理超过5秒的旧快照
        long currentTime = System.currentTimeMillis();
        lastPlayerSnapshots.entrySet().removeIf(entry ->
                (currentTime - entry.getValue().timestamp) > 5000
        );
    }

    /**
     * 开始追踪新弹道
     */
    private static void trackNewProjectile(MinecraftClient client, Entity projectile) {
        // 立即获取射手名称（在弹道刚创建时owner最有可能有效）
        String shooterName = getShooterName(projectile);
        boolean localPlayerShot = isLocalPlayerShot(client, projectile);

        // 检测弹道类型（使用新的动态系统）
        String typeId = detectProjectileType(projectile);

        // 开始记录实际轨迹（用于校准）：按规则过滤样本
        if (shouldStartCalibrationRecording(client, projectile, typeId)) {
            TrajectoryRecorder.startRecording(
                projectile.getUuid(),
                typeId,
                projectile.getPos(),
                projectile.getVelocity(),
                localPlayerShot
            );
        }

        // 计算轨迹预测
        ProjectilePrediction prediction = PhysicsSimulator.predictTrajectory(
                projectile,
                typeId,
                client.world,
                MAX_SIMULATION_TICKS
        );

        // 检查落点是否靠近玩家
        Vec3d playerPos = client.player.getPos();
        double distanceToLanding = prediction.landingPos.distanceTo(playerPos);
        double alertRange = ModConfig.getInstance().getNearbyWarningRange();
        boolean inRange = distanceToLanding <= alertRange;

        // 创建追踪记录（缓存射手名称和类型）
        TrackedProjectile tracked = new TrackedProjectile(
                projectile.getUuid(),
                getProjectileName(projectile),
                shooterName,
                typeId,
                prediction,
                inRange
        );

        activeProjectiles.put(projectile.getUuid(), tracked);

        // 发送警报消息（每个弹道只发送一次）
        if (!alertedProjectiles.contains(projectile.getUuid())) {
            sendAlertMessage(client, tracked);
            alertedProjectiles.add(projectile.getUuid());
        }
    }

    /**
     * 发送警报消息
     *
     * 非危险弹道（非 NEARBY）：使用 actionbar 显示，自动覆盖上一条，不占聊天栏。
     * 危险弹道（NEARBY WARNING）：保留聊天栏显示，但同一射手 3 秒内只发一条。
     */
    private static void sendAlertMessage(MinecraftClient client, TrackedProjectile tracked) {
        String projectileTypeKey = tracked.getType();
        String shooterName = tracked.getShooterName();
        Vec3d landing = tracked.getPrediction().landingPos;
        double alertRange = ModConfig.getInstance().getNearbyWarningRange();
        boolean isNear = landing.distanceTo(client.player.getPos()) <= alertRange;

        String coordStr = String.format("(%.1f, %.1f, %.1f)", landing.x, landing.y, landing.z);

        if (!isNear) {
            // 非危险弹道：actionbar 显示（自动覆盖上一条）
            Text actionbarMsg = Text.translatable("playerhighlight.alert.actionbar",
                    Text.translatable(projectileTypeKey), shooterName, coordStr);
            client.player.sendMessage(actionbarMsg, true);
            return;
        }

        // 危险弹道（NEARBY）：聊天栏显示，同一射手 3 秒冷却
        long now = System.currentTimeMillis();
        Long lastTime = nearbyAlertCooldowns.get(shooterName);
        if (lastTime != null && (now - lastTime) < NEARBY_ALERT_COOLDOWN_MS) {
            return;
        }
        nearbyAlertCooldowns.put(shooterName, now);

        Text message = Text.translatable("playerhighlight.alert.message",
                Text.translatable(projectileTypeKey),
                Text.literal(shooterName).styled(style -> style.withColor(0x5555FF)),
                Text.literal(coordStr).styled(style -> style.withColor(0x55FF55)));
        message = ((net.minecraft.text.MutableText) message)
                .append(Text.translatable("playerhighlight.alert.nearby_warning")
                        .styled(style -> style.withColor(0xFF5555)));

        client.player.sendMessage(message, false);
    }

    /**
     * 清理过期的弹道记录
     */
    private static void cleanupDeadProjectiles(MinecraftClient client) {
        long currentTime = System.currentTimeMillis();
        activeProjectiles.entrySet().removeIf(entry -> {
            boolean shouldRemove = entry.getValue().isExpired(currentTime);
            if (shouldRemove) {
                alertedProjectiles.remove(entry.getKey());
            }
            return shouldRemove;
        });

        // 清理轨迹记录器
        TrajectoryRecorder.cleanup();

        // 处理本地玩家射击的“重复实体/无形墙”情况：延迟选择更可靠的那一个样本进入学习
        CalibrationSystem.flushPendingLocalShots();

        // 清理“箭雨玩家”限流状态
        pruneArrowLearningSuppression(client);
    }

    /**
     * 是否应当开始记录该弹道用于校准学习。
     *
     * <p>规则：</p>
     * <ol>
     *   <li>仅对“满弓”的箭样本记录学习（优先 critical=true；否则按初速近似判定）</li>
     *   <li>怪物箭不记录（仅记录 owner 为玩家的箭）</li>
     *   <li>若玩家 3 秒内射出 >= 5 支箭，屏蔽该玩家 10 分钟（并丢弃窗口内记录，避免污染）</li>
     * </ol>
     */
    private static boolean shouldStartCalibrationRecording(MinecraftClient client, Entity projectile, String typeId) {
        if (client == null || client.world == null || projectile == null) {
            return false;
        }
        if ("FIREBALL".equals(typeId)) {
            return false;
        }
        if (!BowEnchantmentDetector.TYPE_ARROW.equals(typeId)) {
            return true;
        }

        // 只记录玩家射出的箭（怪物箭/未知 owner 直接跳过）
        PlayerEntity shooter = getOwnerPlayer(projectile);
        if (shooter == null) {
            return false;
        }

        long nowTick = client.world.getTime();
        UUID shooterUuid = shooter.getUuid();

        if (isArrowLearningSuppressed(shooterUuid, nowTick)) {
            return false;
        }

        // 箭雨限流：如果该玩家短时间内连续射出过多箭，视为异常并屏蔽（满弓/非满弓都会触发）
        if (registerArrowShotAndMaybeSuppress(shooterUuid, projectile.getUuid(), nowTick)) {
            return false;
        }

        // 只记录满弓样本：优先使用 critical 标记；若服务器/同步导致 critical 未及时生效，则用初速近似判断
        if (!isFullyDrawnBowArrow(projectile)) {
            return false;
        }

        return true;
    }

    private static PlayerEntity getOwnerPlayer(Entity projectile) {
        Entity owner = null;
        if (projectile instanceof ProjectileEntity proj) {
            owner = proj.getOwner();
        } else if (projectile instanceof AbstractFireballEntity fireball) {
            owner = fireball.getOwner();
        }
        if (owner instanceof PlayerEntity player) {
            return player;
        }

        // 离线模式下 getOwner() 经常返回 null，回退到位置推断
        return inferOwnerPlayerFromPosition(projectile);
    }

    /**
     * 当 getOwner() 返回 null 时，通过位置和方向推断射手玩家实体。
     * 用于校准过滤（需要知道射手是玩家而非怪物）。
     */
    private static PlayerEntity inferOwnerPlayerFromPosition(Entity projectile) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return null;
        }

        Vec3d projectilePos = projectile.getPos();
        Vec3d rawVelocity = projectile.getVelocity();
        if (rawVelocity.lengthSquared() < 1.0e-9) {
            return null;
        }
        Vec3d projectileDir = rawVelocity.normalize();

        PlayerEntity bestPlayer = null;
        double bestScore = Double.MAX_VALUE;

        for (PlayerEntity player : client.world.getPlayers()) {
            Vec3d eyePos = player.getPos().add(0, player.getStandingEyeHeight(), 0);
            double distance = eyePos.distanceTo(projectilePos);
            if (distance > 5.0) continue;

            Vec3d lookDir = player.getRotationVec(1.0f);
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
        if (projectile instanceof PersistentProjectileEntity persistent) {
            if (persistent.isCritical()) {
                return true;
            }

            Vec3d velocity = persistent.getVelocity();
            return velocity != null && velocity.lengthSquared() >= FULLY_DRAWN_ARROW_SPEED_SQ;
        }
        return false;
    }

    private static boolean isArrowLearningSuppressed(UUID shooterUuid, long nowTick) {
        Long until = arrowLearningSuppressedUntilTick.get(shooterUuid);
        if (until == null) {
            return false;
        }
        if (nowTick >= until) {
            arrowLearningSuppressedUntilTick.remove(shooterUuid);
            arrowSpamWindows.remove(shooterUuid);
            return false;
        }
        return true;
    }

    private static void pruneArrowLearningSuppression(MinecraftClient client) {
        if (client == null || client.world == null) {
            return;
        }
        long nowTick = client.world.getTime();
        arrowLearningSuppressedUntilTick.entrySet().removeIf(e -> nowTick >= e.getValue());
        arrowSpamWindows.entrySet().removeIf(entry -> {
            ArrowSpamWindow window = entry.getValue();
            if (window == null) {
                return true;
            }
            pruneArrowSpamWindow(window, nowTick);
            return window.entries.isEmpty();
        });
    }

    /**
     * 记录一次“玩家射出的箭”，并判断是否需要触发屏蔽。
     *
     * @return 是否应屏蔽（当前箭也应当跳过记录）
     */
    private static boolean registerArrowShotAndMaybeSuppress(UUID shooterUuid, UUID projectileUuid, long nowTick) {
        ArrowSpamWindow window = arrowSpamWindows.computeIfAbsent(shooterUuid, k -> new ArrowSpamWindow());
        pruneArrowSpamWindow(window, nowTick);

        window.entries.addLast(new ArrowShotEntry(nowTick, projectileUuid));
        if (window.entries.size() < ARROW_SPAM_THRESHOLD) {
            return false;
        }

        // 触发屏蔽：把窗口内所有记录丢弃，避免污染学习
        arrowLearningSuppressedUntilTick.put(shooterUuid, nowTick + ARROW_SUPPRESS_TICKS);
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
        if (System.currentTimeMillis() >= until) {
            ignoredProjectilesUntil.remove(uuid);
            return false;
        }
        return true;
    }

    private static void pruneIgnored() {
        long now = System.currentTimeMillis();
        ignoredProjectilesUntil.entrySet().removeIf(e -> now >= e.getValue());
    }

    /**
     * 标记弹道已落地/已结束，并进入“忽略列表”防止服务器 bug 导致的反复弹跳被重复追踪。
     *
     * @param uuid 弹道 UUID
     * @param landingPos 可选：实际碰撞点；为 null 时 TrajectoryRecorder 会使用最后记录点
     */
    public static void markProjectileAsLanded(UUID uuid, Vec3d landingPos) {
        TrackedProjectile tracked = activeProjectiles.get(uuid);
        if (tracked != null && !tracked.hasLanded()) {
            tracked.markAsLanded();
            TrajectoryRecorder.markLanded(uuid, landingPos);
        }

        // 忽略一段时间：避免“落地后不停弹跳”的箭被清理后再次当作新弹道追踪
        ignoredProjectilesUntil.put(uuid, System.currentTimeMillis() + 120000);
    }

    /**
     * 检查弹道是否被追踪（供 Mixin 使用）
     * 击中后继续显示3秒直到清理
     */
    public static boolean isProjectileTracked(UUID uuid) {
        return activeProjectiles.containsKey(uuid);
    }

    /**
     * 获取所有活动的弹道列表（供渲染器使用）
     */
    public static Collection<TrackedProjectile> getActiveProjectiles() {
        return activeProjectiles.values();
    }

    /**
     * 判断实体是否是可追踪的弹道
     */
    private static boolean isTrackableProjectile(Entity entity) {
        return entity instanceof ArrowEntity
                || entity instanceof SpectralArrowEntity
                || entity instanceof TridentEntity
                || entity instanceof SmallFireballEntity   // 烈焰弹
                || entity instanceof FireballEntity;       // 恶魂火球
    }

    /**
     * 获取弹道类型翻译键
     */
    private static String getProjectileName(Entity projectile) {
        if (projectile instanceof ArrowEntity) return "playerhighlight.projectile.arrow";
        if (projectile instanceof SpectralArrowEntity) return "playerhighlight.projectile.spectral_arrow";
        if (projectile instanceof TridentEntity) return "playerhighlight.projectile.trident";
        if (projectile instanceof SmallFireballEntity) return "playerhighlight.projectile.blaze_fireball";
        if (projectile instanceof FireballEntity) return "playerhighlight.projectile.ghast_fireball";
        return "playerhighlight.projectile.unknown";
    }

    /**
     * 获取射手名字（支持离线模式服务器）
     *
     * 在弹道刚创建时调用此方法，确保获取到最准确的射手信息。
     *
     * 离线（非正版）服务器中 getOwner() 经常返回 null，因为服务器分配的
     * 玩家 UUID 与弹射物记录的 owner UUID 不一致。此时回退到基于位置+方向的推断。
     */
    private static String getShooterName(Entity projectile) {
        Entity owner = null;

        // 处理箭类和三叉戟 (ProjectileEntity)
        if (projectile instanceof ProjectileEntity proj) {
            owner = proj.getOwner();
        }
        // 处理火球类 (AbstractFireballEntity)
        else if (projectile instanceof AbstractFireballEntity fireball) {
            owner = fireball.getOwner();
        }

        // 如果 owner 存在且是玩家，直接获取名称
        if (owner instanceof PlayerEntity) {
            return owner.getName().getString();
        }

        // owner 为 null 或非玩家时（离线模式常见），通过位置和方向推断射手
        return inferShooterFromPosition(projectile);
    }

    private static boolean isLocalPlayerShot(MinecraftClient client, Entity projectile) {
        if (client == null || client.player == null) {
            return false;
        }

        Entity owner = null;
        if (projectile instanceof ProjectileEntity proj) {
            owner = proj.getOwner();
        } else if (projectile instanceof AbstractFireballEntity fireball) {
            owner = fireball.getOwner();
        }

        if (owner == null) {
            // 离线模式下 getOwner() 经常返回 null，回退到位置+方向推断
            return inferIsLocalPlayerShot(client, projectile);
        }

        // 优先 UUID 比较；离线模式下 UUID 可能不一致，回退到 Entity ID 比较
        if (owner.getUuid().equals(client.player.getUuid())) {
            return true;
        }
        if (owner.getId() == client.player.getId()) {
            return true;
        }

        return false;
    }

    /**
     * 当 getOwner() 返回 null 时，通过位置和方向推断弹射物是否由本地玩家射出。
     * 条件：弹射物在本地玩家 2 格内 且 方向与玩家视线夹角 < 15°
     */
    private static boolean inferIsLocalPlayerShot(MinecraftClient client, Entity projectile) {
        PlayerEntity player = client.player;
        double distance = player.getPos().distanceTo(projectile.getPos());
        if (distance > 2.0) {
            return false;
        }

        Vec3d playerLook = player.getRotationVec(1.0f);
        Vec3d projVel = projectile.getVelocity();
        if (projVel.lengthSquared() < 1.0e-9) {
            return false;
        }
        Vec3d projDir = projVel.normalize();
        double dot = playerLook.dotProduct(projDir);
        double angle = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
        // 15° 以内视为本地玩家射出
        return angle < Math.toRadians(15.0);
    }

    /**
     * 通过弹道位置和速度推断射手
     * 适用于离线模式服务器 getOwner() 返回 null 的情况
     */
    private static String inferShooterFromPosition(Entity projectile) {
        Vec3d projectilePos = projectile.getPos();
        Vec3d rawVelocity = projectile.getVelocity();
        if (rawVelocity.lengthSquared() < 1.0e-9) {
            return "Unknown";
        }
        Vec3d projectileVelocity = rawVelocity.normalize();

        String bestMatch = "Unknown";
        double bestScore = Double.MAX_VALUE;

        // 遍历所有玩家快照，找到最可能的射手
        for (PlayerSnapshot snapshot : lastPlayerSnapshots.values()) {
            // snapshot.position 已经是眼睛位置
            double distance = snapshot.position.distanceTo(projectilePos);

            // 弹道必须在玩家附近（5格内，缩小范围提高精度）
            if (distance > 5.0) continue;

            // 计算玩家视线方向和弹道方向的夹角
            double dotProduct = snapshot.lookDirection.dotProduct(projectileVelocity);
            double angle = Math.acos(Math.max(-1.0, Math.min(1.0, dotProduct)));

            // 角度超过 30° 直接排除
            if (angle > Math.toRadians(30.0)) continue;

            // 计算综合分数（距离 + 角度偏差）
            double score = distance * 0.5 + angle * 3.0;

            if (score < bestScore) {
                bestScore = score;
                bestMatch = snapshot.name;
            }
        }

        // 如果最佳匹配分数太差，返回 Unknown
        if (bestScore > 3.0) {
            return "Unknown";
        }

        return bestMatch;
    }

    /**
     * 检测弹道类型（使用动态系统）
     */
    private static String detectProjectileType(Entity projectile) {
        if (projectile instanceof SmallFireballEntity) {
            return "FIREBALL";
        } else if (projectile instanceof FireballEntity) {
            return "FIREBALL";
        } else if (projectile instanceof TridentEntity) {
            return "TRIDENT";
        } else if (projectile instanceof ArrowEntity || projectile instanceof SpectralArrowEntity) {
            // 箭类：使用弓附魔检测器
            return BowEnchantmentDetector.detectBowTypeId(projectile);
        } else {
            return "UNKNOWN";
        }
    }
}

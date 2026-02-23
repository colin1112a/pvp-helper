package com.example.playerhighlight;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弹道校准系统（改进版）
 *
 * 通过对比预测轨迹与实际轨迹，自动调整物理参数
 *
 * 改进点：
 * 1. 闭式解估计：从实际轨迹直接估计 drag 与 gravity（最小二乘）
 * 2. 基础清洗：过滤低水平速度与离群点（碰撞/网络抖动）
 * 3. EMA 平滑：用指数滑动平均更新参数，避免抖动
 */
public class CalibrationSystem {

    private static final double ERROR_THRESHOLD = 2.0;
    private static final long CALIBRATION_MESSAGE_COOLDOWN_MS = 10000;
    private static final double MIN_PARAM_CHANGE = 0.001;
    private static final double MIN_SAMPLE_DISTANCE = 2.5;

    // 闭式解与清洗参数
    private static final int MIN_PAIRS_FOR_ESTIMATE = 6;
    private static final int TRIM_TAIL_TICKS = 2;
    private static final double MIN_HORIZ_SPEED_SQ = 1.0e-4;
    private static final double DRAG_OUTLIER_THRESHOLD = 0.02;
    private static final double GRAVITY_OUTLIER_THRESHOLD = 0.02;

    // 异常样本拒绝
    private static final int REJECT_OUTLIER_MIN_SAMPLES = 100;
    private static final double REJECT_OUTLIER_MULTIPLIER = 3.0;
    private static final double REJECT_OUTLIER_MIN_ERROR = 3.0;

    // EMA 平滑系数
    private static final double EMA_ALPHA = 0.20;
    private static final double EMA_ALPHA_INITIAL = 0.35;

    // 参数范围
    private static final double MIN_GRAVITY = 0.0;
    private static final double MAX_GRAVITY = 0.2;
    private static final double MIN_DRAG = 0.90;
    private static final double MAX_DRAG = 1.0;

    // 为每种弹道类型存储样本
    private static final Map<String, List<CalibrationSample>> samples = new ConcurrentHashMap<String, List<CalibrationSample>>();

    // 每种类型的上次提示时间（用于节流）
    private static final Map<String, Long> lastCalibrationMessageTime = new ConcurrentHashMap<String, Long>();

    // 是否启用自动校准
    private static boolean autoCalibrationEnabled = true;

    // 调试模式
    private static boolean debugMode = false;

    // 异常样本拒绝消息冷却
    private static final long OUTLIER_REJECTED_MESSAGE_COOLDOWN_MS = 10000;
    private static final Map<String, Long> lastOutlierRejectedMessageMs = new ConcurrentHashMap<String, Long>();

    // 本地玩家射击的"重复箭实体"缓冲
    private static final long LOCAL_SHOT_BUCKET_MS = 1000;
    private static final long LOCAL_SHOT_IDLE_FLUSH_MS = 2500;
    private static final long LOCAL_SHOT_MAX_WAIT_MS = 15000;
    private static final double LOCAL_SHOT_MIN_DISTANCE_TO_AUTO_PROCESS = 15.0;
    private static final double LOCAL_SHOT_GHOST_MIN_DISTANCE_MAX = 8.0;
    private static final double LOCAL_SHOT_GHOST_DISTANCE_DELTA_MIN = 10.0;
    private static final long ABNORMAL_LOCAL_SHOT_MESSAGE_COOLDOWN_MS = 10000;
    private static final Map<Long, PendingLocalShotGroup> pendingLocalShots = new ConcurrentHashMap<Long, PendingLocalShotGroup>();
    private static final Map<String, Long> lastAbnormalLocalShotMessageMs = new ConcurrentHashMap<String, Long>();

    /**
     * 处理记录的轨迹，计算误差并更新参数
     */
    public static void processRecording(TrajectoryRecorder.RecordedTrajectory recording) {
        String typeId = recording.typeId;
        if (recording.getActualLandingPos() == null || recording.positions.size() < 2) {
            return;
        }

        int flightTicks = estimateFlightTicks(recording.positions);
        if (flightTicks < 6) {
            return;
        }

        List<Vec3> actualPositions = new ArrayList<Vec3>(flightTicks + 1);
        for (int i = 0; i <= flightTicks && i < recording.positions.size(); i++) {
            actualPositions.add(recording.positions.get(i));
        }

        Vec3 effectiveLanding = actualPositions.get(actualPositions.size() - 1);

        double flightDistance = recording.initialPos.distanceTo(effectiveLanding);

        if (flightDistance < MIN_SAMPLE_DISTANCE) {
            return;
        }

        DynamicProjectileRegistry registry = DynamicProjectileRegistry.getInstance();
        DynamicProjectileRegistry.ProjectileTypeData typeData = registry.getOrCreateType(typeId);

        double mse = calculateTrajectoryMse(
            recording.initialPos,
            recording.initialVel,
            actualPositions,
            typeData.getGravity(),
            typeData.getDrag()
        );
        double error = Math.sqrt(mse);

        int currentSamples = typeData.getSampleCount();
        if (currentSamples > REJECT_OUTLIER_MIN_SAMPLES) {
            double currentAvgError = typeData.getAvgError();
            double rejectThreshold = Math.max(REJECT_OUTLIER_MIN_ERROR, currentAvgError * REJECT_OUTLIER_MULTIPLIER);
            if (error > rejectThreshold) {
                sendOutlierRejectedMessage(typeId, error, currentAvgError, flightDistance);
                return;
            }
        }

        ParameterEstimate estimate = estimateParametersFromTrajectory(actualPositions);

        List<CalibrationSample> typeSamples = samples.get(typeId);
        if (typeSamples == null) {
            typeSamples = new ArrayList<CalibrationSample>();
            samples.put(typeId, typeSamples);
        }
        typeSamples.add(new CalibrationSample(error, flightDistance, flightTicks, estimate));

        if (typeSamples.size() > 30) {
            typeSamples.remove(0);
        }

        double weight = Math.min(2.0, Math.max(0.5, flightDistance / 20.0));
        typeData.recordErrorSample(error, weight);
        registry.markDirty();
        double avgError = typeData.getAvgError();
        int totalSamples = typeData.getSampleCount();

        if (debugMode) {
            sendDebugMessage(StatCollector.translateToLocalFormatted(
                "playerhighlight.calibration.debug_trajectory",
                BowEnchantmentDetector.getDisplayName(typeId),
                String.format("%.2f", error),
                String.format("%.2f", avgError),
                String.valueOf(totalSamples),
                String.format("%.1f", flightDistance)
            ));
        }

        if (autoCalibrationEnabled && estimate != null) {
            applyEmaUpdate(typeId, estimate, totalSamples, flightDistance, typeData);
        }
    }

    /**
     * 轨迹记录完成入口：对本地玩家射出的箭做"去重/挑选最可靠样本"。
     */
    public static void onRecordingComplete(TrajectoryRecorder.RecordedTrajectory recording) {
        if (recording == null || !recording.isValid()) {
            return;
        }
        if (!recording.localPlayerShot) {
            processRecording(recording);
            return;
        }

        if (isAbnormalLocalRecording(recording)) {
            emitAbnormalLocalShotMessage(recording);
            return;
        }

        long key = localShotKey(recording);
        PendingLocalShotGroup group = pendingLocalShots.get(key);
        if (group == null) {
            group = new PendingLocalShotGroup();
            pendingLocalShots.put(key, group);
        }
        double distance = recording.initialPos.distanceTo(recording.getActualLandingPos());

        group.count++;
        long now = System.currentTimeMillis();
        if (group.firstSeenMs == 0L) {
            group.firstSeenMs = now;
        }
        group.lastUpdateMs = now;

        group.minDistance = Math.min(group.minDistance, distance);
        group.maxDistance = Math.max(group.maxDistance, distance);

        if (group.best == null || distance > group.bestDistance) {
            group.best = recording;
            group.bestDistance = distance;
        }

        if (group.count >= 2 && isGhostLocalShot(group)) {
            pendingLocalShots.remove(key);
            return;
        }

        if (group.count >= 2 && group.best != null) {
            pendingLocalShots.remove(key);
            processRecording(group.best);
        }
    }

    /**
     * 定期刷新本地射击缓冲。
     */
    public static void flushPendingLocalShots() {
        final long now = System.currentTimeMillis();
        Iterator<Map.Entry<Long, PendingLocalShotGroup>> it = pendingLocalShots.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, PendingLocalShotGroup> entry = it.next();
            PendingLocalShotGroup group = entry.getValue();
            if (group == null || group.best == null) {
                it.remove();
                continue;
            }

            if (group.count >= 2 && isGhostLocalShot(group)) {
                it.remove();
                continue;
            }

            boolean idle = (now - group.lastUpdateMs) >= LOCAL_SHOT_IDLE_FLUSH_MS;
            boolean timeout = (now - group.firstSeenMs) >= LOCAL_SHOT_MAX_WAIT_MS;

            if (!idle && !timeout) {
                continue;
            }

            if (group.bestDistance >= LOCAL_SHOT_MIN_DISTANCE_TO_AUTO_PROCESS || timeout) {
                if (timeout && group.bestDistance < LOCAL_SHOT_MIN_DISTANCE_TO_AUTO_PROCESS) {
                    it.remove();
                    continue;
                }
                processRecording(group.best);
                it.remove();
            }
        }
    }

    private static long localShotKey(TrajectoryRecorder.RecordedTrajectory recording) {
        long bucket = recording.startTime / LOCAL_SHOT_BUCKET_MS;
        double lenSq = recording.initialVel.xCoord * recording.initialVel.xCoord
                + recording.initialVel.yCoord * recording.initialVel.yCoord
                + recording.initialVel.zCoord * recording.initialVel.zCoord;
        Vec3 dir = lenSq > 1.0e-9
                ? recording.initialVel.normalize()
                : new Vec3(0, 0, 0);
        int dx = (int) Math.round(dir.xCoord * 20);
        int dy = (int) Math.round(dir.yCoord * 20);
        int dz = (int) Math.round(dir.zCoord * 20);
        int dirHash = ((dx & 0xFF) << 16) | ((dy & 0xFF) << 8) | (dz & 0xFF);
        int typeHash = recording.typeId != null ? (recording.typeId.hashCode() & 0xFFFF) : 0;
        return (bucket << 32) ^ (((long) dirHash) << 16) ^ (long) typeHash;
    }

    private static boolean isGhostLocalShot(PendingLocalShotGroup group) {
        if (group == null || group.count < 2) {
            return false;
        }
        double minD = group.minDistance;
        double maxD = group.maxDistance;
        if (Double.isNaN(minD) || Double.isInfinite(minD) || Double.isNaN(maxD) || Double.isInfinite(maxD)) {
            return true;
        }
        return minD <= LOCAL_SHOT_GHOST_MIN_DISTANCE_MAX && (maxD - minD) >= LOCAL_SHOT_GHOST_DISTANCE_DELTA_MIN;
    }

    private static boolean isAbnormalLocalRecording(TrajectoryRecorder.RecordedTrajectory recording) {
        double initialSpeed = recording.initialVel.lengthVector();
        if (initialSpeed < 2.5) {
            return false;
        }

        Vec3 start = recording.initialPos;
        Vec3 landing = recording.getActualLandingPos();
        if (landing == null) {
            return true;
        }

        double ddx = landing.xCoord - start.xCoord;
        double ddz = landing.zCoord - start.zCoord;
        double horizontalDist = Math.sqrt(ddx * ddx + ddz * ddz);
        double flightDist = start.distanceTo(landing);
        int ticks = recording.getTickCount();

        if (ticks >= 12 && horizontalDist <= 1.5) {
            double verticalDrop = start.yCoord - landing.yCoord;
            if (verticalDrop >= 1.5) {
                int slideTicks = 0;
                int sampleTicks = 0;
                List<Vec3> positions = recording.positions;
                int begin = Math.min(5, Math.max(0, positions.size() - 2));
                for (int i = begin + 1; i < positions.size(); i++) {
                    Vec3 p0 = positions.get(i - 1);
                    Vec3 p1 = positions.get(i);
                    double pdx = p1.xCoord - p0.xCoord;
                    double pdz = p1.zCoord - p0.zCoord;
                    double hSq = pdx * pdx + pdz * pdz;
                    sampleTicks++;
                    if (hSq < 0.002 * 0.002) {
                        slideTicks++;
                    }
                }
                if (sampleTicks > 0 && ((double) slideTicks / sampleTicks) >= 0.60) {
                    return true;
                }
            }
        }

        return flightDist <= 8.0 && ticks >= 20;
    }

    private static void emitAbnormalLocalShotMessage(TrajectoryRecorder.RecordedTrajectory recording) {
        if (recording == null || recording.typeId == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastAbnormalLocalShotMessageMs.get(recording.typeId);
        if (last != null && now - last < ABNORMAL_LOCAL_SHOT_MESSAGE_COOLDOWN_MS) {
            return;
        }
        lastAbnormalLocalShotMessageMs.put(recording.typeId, now);

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }

        double dist = recording.initialPos.distanceTo(recording.getActualLandingPos());
        String displayName = BowEnchantmentDetector.getDisplayName(recording.typeId);
        mc.thePlayer.addChatMessage(new ChatComponentText(StatCollector.translateToLocalFormatted(
                "playerhighlight.calibration.abnormal_local_shot",
                displayName,
                String.format("%.1f", dist),
                String.valueOf(recording.getTickCount())
        )));
    }

    /**
     * 应用 EMA 平滑更新（避免参数抖动）
     */
    private static void applyEmaUpdate(String typeId, ParameterEstimate estimate, int sampleCount,
                                       double flightDistance,
                                       DynamicProjectileRegistry.ProjectileTypeData typeData) {
        double currentGravity = typeData.getGravity();
        double currentDrag = typeData.getDrag();

        double baseAlpha = sampleCount < 3 ? EMA_ALPHA_INITIAL : EMA_ALPHA;
        double distanceFactor = clamp(flightDistance / 30.0, 0.25, 1.0);
        int pairs = Math.min(estimate.dragPairsUsed, estimate.gravityPairsUsed);
        double pairsFactor = clamp(pairs / 12.0, 0.40, 1.0);
        double alpha = baseAlpha * distanceFactor * pairsFactor;
        double newGravity = currentGravity + (estimate.gravity - currentGravity) * alpha;
        double newDrag = currentDrag + (estimate.drag - currentDrag) * alpha;

        // 限制范围
        newGravity = clamp(newGravity, MIN_GRAVITY, MAX_GRAVITY);
        newDrag = clamp(newDrag, MIN_DRAG, MAX_DRAG);

        typeData.updateParams(newGravity, newDrag);
        DynamicProjectileRegistry.getInstance().markDirty();

        double gravityChange = Math.abs(newGravity - currentGravity);
        double dragChange = Math.abs(newDrag - currentDrag);

        if ((gravityChange > MIN_PARAM_CHANGE || dragChange > MIN_PARAM_CHANGE)
            && shouldEmitCalibrationMessage(typeId, currentDrag, currentGravity, newDrag, newGravity)) {
            String message = StatCollector.translateToLocalFormatted(
                "playerhighlight.calibration.param_update",
                BowEnchantmentDetector.getDisplayName(typeId),
                String.format("%.4f", currentGravity), String.format("%.4f", newGravity),
                String.format("%.4f", currentDrag), String.format("%.4f", newDrag),
                String.valueOf(estimate.dragPairsUsed),
                String.valueOf(estimate.gravityPairsUsed)
            );
            if (debugMode) {
                sendDebugMessage(message);
            }
            PlayerHighlightMod.LOGGER.info(message);
        }
    }

    /**
     * 计算预测轨迹与真实轨迹的均方误差（MSE）
     */
    private static double calculateTrajectoryMse(Vec3 initialPos, Vec3 initialVel,
                                                 List<Vec3> actualPositions,
                                                 double gravity, double drag) {
        if (actualPositions.isEmpty()) {
            return 0.0;
        }

        Vec3 currentPos = initialPos;
        Vec3 currentVel = initialVel;

        double sumSq = 0.0;
        int count = 0;

        for (int i = 0; i < actualPositions.size(); i++) {
            Vec3 actual = actualPositions.get(i);
            sumSq += currentPos.squareDistanceTo(actual);
            count++;

            if (i == actualPositions.size() - 1) {
                break;
            }

            // 先位移（使用当前速度），再阻力/重力更新速度
            currentPos = currentPos.addVector(currentVel.xCoord, currentVel.yCoord, currentVel.zCoord);
            currentVel = new Vec3(currentVel.xCoord * drag, currentVel.yCoord * drag, currentVel.zCoord * drag);
            currentVel = currentVel.addVector(0, -gravity, 0);
        }

        return count > 0 ? (sumSq / count) : 0.0;
    }

    /**
     * 从真实轨迹估计物理参数（闭式解 + 基础清洗）
     */
    private static ParameterEstimate estimateParametersFromTrajectory(List<Vec3> positions) {
        if (positions.size() < (MIN_PAIRS_FOR_ESTIMATE + 3)) {
            return null;
        }

        int endExclusive = Math.max(0, positions.size() - TRIM_TAIL_TICKS);
        if (endExclusive < (MIN_PAIRS_FOR_ESTIMATE + 3)) {
            return null;
        }

        // velocities[i] = p[i+1] - p[i]
        int velCount = endExclusive - 1;
        Vec3[] velocities = new Vec3[velCount];
        for (int i = 0; i < velCount; i++) {
            Vec3 p0 = positions.get(i);
            Vec3 p1 = positions.get(i + 1);
            velocities[i] = p1.subtract(p0);
        }

        // 收集每对速度的 drag 候选值
        List<Double> dragCandidates = new ArrayList<Double>();
        double[] hSpeedSq = new double[velCount - 1];
        double[] pairDrag = new double[velCount - 1];

        for (int i = 0; i < velCount - 1; i++) {
            Vec3 v0 = velocities[i];
            Vec3 v1 = velocities[i + 1];

            double h0sq = v0.xCoord * v0.xCoord + v0.zCoord * v0.zCoord;
            if (h0sq < MIN_HORIZ_SPEED_SQ) {
                hSpeedSq[i] = 0.0;
                pairDrag[i] = Double.NaN;
                continue;
            }

            double d = (v1.xCoord * v0.xCoord + v1.zCoord * v0.zCoord) / h0sq;
            hSpeedSq[i] = h0sq;
            pairDrag[i] = d;
            dragCandidates.add(d);
        }

        if (dragCandidates.size() < MIN_PAIRS_FOR_ESTIMATE) {
            return null;
        }

        double medianDrag = median(dragCandidates);

        // 1) 最小二乘估计 drag
        double numerator = 0.0;
        double denominator = 0.0;
        int dragPairsUsed = 0;

        for (int i = 0; i < velCount - 1; i++) {
            double d = pairDrag[i];
            if (Double.isNaN(d)) continue;
            if (d < MIN_DRAG || d > MAX_DRAG) continue;
            if (Math.abs(d - medianDrag) > DRAG_OUTLIER_THRESHOLD) continue;

            Vec3 v0 = velocities[i];
            Vec3 v1 = velocities[i + 1];
            numerator += (v1.xCoord * v0.xCoord + v1.zCoord * v0.zCoord);
            denominator += hSpeedSq[i];
            dragPairsUsed++;
        }

        if (dragPairsUsed < MIN_PAIRS_FOR_ESTIMATE || denominator <= 0.0) {
            return null;
        }

        double drag = clamp(numerator / denominator, MIN_DRAG, MAX_DRAG);

        // 2) 估计 gravity
        List<Double> gravityCandidates = new ArrayList<Double>();
        for (int i = 0; i < velCount - 1; i++) {
            double d = pairDrag[i];
            if (Double.isNaN(d)) continue;
            if (d < MIN_DRAG || d > MAX_DRAG) continue;
            if (Math.abs(d - medianDrag) > DRAG_OUTLIER_THRESHOLD) continue;

            Vec3 v0 = velocities[i];
            Vec3 v1 = velocities[i + 1];
            double g = drag * v0.yCoord - v1.yCoord;
            if (g >= MIN_GRAVITY && g <= MAX_GRAVITY) {
                gravityCandidates.add(g);
            }
        }

        if (gravityCandidates.isEmpty()) {
            return null;
        }

        double medianGravity = median(gravityCandidates);
        double gravitySum = 0.0;
        int gravityPairsUsed = 0;
        for (double g : gravityCandidates) {
            if (Math.abs(g - medianGravity) <= GRAVITY_OUTLIER_THRESHOLD) {
                gravitySum += g;
                gravityPairsUsed++;
            }
        }

        if (gravityPairsUsed == 0) {
            return null;
        }

        double gravity = clamp(gravitySum / gravityPairsUsed, MIN_GRAVITY, MAX_GRAVITY);
        return new ParameterEstimate(gravity, drag, dragPairsUsed, gravityPairsUsed);
    }

    private static boolean shouldEmitCalibrationMessage(String typeId,
                                                        double oldDrag, double oldGravity,
                                                        double newDrag, double newGravity) {
        List<CalibrationSample> typeSamples = samples.get(typeId);
        if (typeSamples != null && !typeSamples.isEmpty()) {
            CalibrationSample last = typeSamples.get(typeSamples.size() - 1);
            if (last.rmse < ERROR_THRESHOLD) {
                return false;
            }
        }

        long now = System.currentTimeMillis();
        Long last = lastCalibrationMessageTime.get(typeId);
        long lastTime = last != null ? last : 0L;
        if (now - lastTime < CALIBRATION_MESSAGE_COOLDOWN_MS) {
            return false;
        }
        lastCalibrationMessageTime.put(typeId, now);
        return true;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }

        double[] arr = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            arr[i] = values.get(i);
        }
        Arrays.sort(arr);
        int mid = arr.length / 2;
        if (arr.length % 2 == 0) {
            return (arr[mid - 1] + arr[mid]) / 2.0;
        }
        return arr[mid];
    }

    /**
     * 估计弹射物"仍在飞行"的 tick 数，剔除命中后静止阶段
     */
    private static int estimateFlightTicks(List<Vec3> positions) {
        final double stillThresholdSq = 0.01 * 0.01;
        int stillCount = 0;

        for (int i = 1; i < positions.size(); i++) {
            double deltaSq = positions.get(i).squareDistanceTo(positions.get(i - 1));
            if (deltaSq < stillThresholdSq) {
                stillCount++;
                if (stillCount >= 3) {
                    return Math.max(0, i - stillCount);
                }
            } else {
                stillCount = 0;
            }
        }

        return positions.size() - 1;
    }

    /**
     * 发送调试消息
     */
    private static void sendDebugMessage(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(message));
        }
    }

    /**
     * 发送异常样本拒绝消息（红色），10 秒冷却
     */
    private static void sendOutlierRejectedMessage(String typeId, double error, double avgError, double distance) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastOutlierRejectedMessageMs.get(typeId);
        long lastTime = last != null ? last.longValue() : 0L;
        if (now - lastTime < OUTLIER_REJECTED_MESSAGE_COOLDOWN_MS) {
            return;
        }
        lastOutlierRejectedMessageMs.put(typeId, Long.valueOf(now));

        String displayName = BowEnchantmentDetector.getDisplayName(typeId);
        String message = StatCollector.translateToLocalFormatted(
                "playerhighlight.calibration.outlier_rejected",
                displayName,
                String.format("%.2f", error),
                String.format("%.2f", avgError),
                String.format("%.1f", distance)
        );
        ChatComponentText text = new ChatComponentText(message);
        text.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED));
        mc.thePlayer.addChatMessage(text);
    }

    // Getters and setters
    public static void setAutoCalibrationEnabled(boolean enabled) {
        autoCalibrationEnabled = enabled;
    }

    public static boolean isAutoCalibrationEnabled() {
        return autoCalibrationEnabled;
    }

    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
    }

    public static boolean isDebugMode() {
        return debugMode;
    }

    public static int getSampleCount(String typeId) {
        DynamicProjectileRegistry.ProjectileTypeData data =
                DynamicProjectileRegistry.getInstance().getOrCreateType(typeId);
        return data.getSampleCount();
    }

    /**
     * 清空样本数据
     */
    public static void clearSamples(String typeId) {
        samples.remove(typeId);
        lastCalibrationMessageTime.remove(typeId);
        DynamicProjectileRegistry.getInstance().clearSamples(typeId);
    }

    public static void clearAllSamples() {
        samples.clear();
        lastCalibrationMessageTime.clear();
        DynamicProjectileRegistry.getInstance().clearAllSamples();
    }

    /**
     * 清空某个类型的学习数据（样本 + 统计 + 参数），并移除持久化状态。
     */
    public static void resetTypeLearning(String typeId) {
        samples.remove(typeId);
        lastCalibrationMessageTime.remove(typeId);
        DynamicProjectileRegistry.getInstance().resetTypeLearning(typeId);
    }

    /**
     * 校准样本数据类
     */
    private static class CalibrationSample {
        final double rmse;
        final double flightDistance;
        final int tickCount;
        final ParameterEstimate estimate;

        CalibrationSample(double rmse, double flightDistance, int tickCount, ParameterEstimate estimate) {
            this.rmse = rmse;
            this.flightDistance = flightDistance;
            this.tickCount = tickCount;
            this.estimate = estimate;
        }
    }

    private static class ParameterEstimate {
        final double gravity;
        final double drag;
        final int dragPairsUsed;
        final int gravityPairsUsed;

        ParameterEstimate(double gravity, double drag, int dragPairsUsed, int gravityPairsUsed) {
            this.gravity = gravity;
            this.drag = drag;
            this.dragPairsUsed = dragPairsUsed;
            this.gravityPairsUsed = gravityPairsUsed;
        }
    }

    private static class PendingLocalShotGroup {
        long firstSeenMs;
        long lastUpdateMs;
        int count;
        TrajectoryRecorder.RecordedTrajectory best;
        double bestDistance;
        double minDistance = Double.POSITIVE_INFINITY;
        double maxDistance = 0.0;
    }
}
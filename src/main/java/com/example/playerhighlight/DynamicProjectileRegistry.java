package com.example.playerhighlight;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态弹道类型注册表
 *
 * 自动检测和注册新的弹道类型
 * 每种类型维护独立的物理参数和校准数据
 */
public class DynamicProjectileRegistry {

    // 单例
    private static DynamicProjectileRegistry INSTANCE;

    private static final double MIN_PERSISTED_GRAVITY = 0.0;
    private static final double MAX_PERSISTED_GRAVITY = 0.2;
    private static final double MIN_PERSISTED_DRAG_CALIBRATED = 0.90;
    private static final double MIN_PERSISTED_DRAG_GENERIC = 0.0;
    private static final double MAX_PERSISTED_DRAG = 1.0;

    // 类型参数存储：typeId -> ProjectileTypeData
    private final Map<String, ProjectileTypeData> typeRegistry = new ConcurrentHashMap<>();

    // 已通知用户的类型（避免重复提示）
    private final Map<String, Boolean> notifiedTypes = new ConcurrentHashMap<>();

    private final Map<String, CalibrationPersistence.PersistedTypeState> persisted = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;
    private volatile long dirtySinceMs = 0L;

    private DynamicProjectileRegistry() {
        persisted.putAll(CalibrationPersistence.load());

        // 注册固定类型（三叉戟、火球）
        ModConfig config = ModConfig.getInstance();
        registerFixedType("TRIDENT", config.getTridentGravity(), config.getTridentDrag());
        registerFixedType("FIREBALL", config.getFireballGravity(), config.getFireballDrag());
    }

    public static DynamicProjectileRegistry getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DynamicProjectileRegistry();
        }
        return INSTANCE;
    }

    /**
     * 获取或创建弹道类型的物理参数
     */
    public ProjectileTypeData getOrCreateType(String typeId) {
        if (typeId == null || typeId.isEmpty()) {
            typeId = "UNKNOWN";
        }
        final String finalTypeId = typeId;
        return typeRegistry.computeIfAbsent(finalTypeId, id -> {
            // 新类型，创建并初始化
            ProjectileTypeData data = createNewType(id);

            // 发送通知（仅一次）
            if (!notifiedTypes.containsKey(id)) {
                notifyNewTypeDiscovered(id);
                notifiedTypes.put(id, true);
            }

            return data;
        });
    }

    /**
     * 创建新类型并估算初始参数
     */
    private ProjectileTypeData createNewType(String typeId) {
        double initialGravity;
        double initialDrag;

        ModConfig config = ModConfig.getInstance();

        // 根据类型估算初始参数
        if (BowEnchantmentDetector.TYPE_ARROW.equals(typeId)) {
            // 箭类：使用基础物理参数（gravity/drag 会通过校准自动优化）
            initialGravity = config.getArrowGravity();
            initialDrag = config.getArrowDrag();
        } else if (typeId.equals("TRIDENT")) {
            initialGravity = config.getTridentGravity();
            initialDrag = config.getTridentDrag();
        } else if (typeId.equals("FIREBALL")) {
            initialGravity = config.getFireballGravity();  // 火球通常是直线
            initialDrag = config.getFireballDrag();
        } else {
            // 未知类型，使用默认值
            initialGravity = config.getArrowGravity();
            initialDrag = config.getArrowDrag();
        }

        ProjectileTypeData data = new ProjectileTypeData(typeId, initialGravity, initialDrag);
        applyPersistedIfPresent(data);
        PlayerHighlightClient.LOGGER.info("Registered new projectile type: {} (G={}, D={})",
                typeId, initialGravity, initialDrag);

        markDirty();
        return data;
    }

    /**
     * 注册固定类型（不会触发新类型通知）
     */
    private void registerFixedType(String typeId, double gravity, double drag) {
        ProjectileTypeData data = new ProjectileTypeData(typeId, gravity, drag);
        applyPersistedIfPresent(data);
        typeRegistry.put(typeId, data);
        notifiedTypes.put(typeId, true);  // 标记为已通知，避免后续触发
    }

    /**
     * 通知玩家发现新弹道类型
     */
    private void notifyNewTypeDiscovered(String typeId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            String displayName = BowEnchantmentDetector.getDisplayName(typeId);
            String message = String.format("[Projectile Tracker] New projectile type detected: %s", displayName);

            client.player.sendMessage(
                    Text.literal(message).styled(style -> style.withColor(0x55FF55)),
                    false
            );
        }
    }

    /**
     * 获取所有已注册类型的ID列表
     */
    public Iterable<String> getAllTypeIds() {
        return typeRegistry.keySet();
    }

    public Map<String, ProjectileTypeData> getRegisteredTypesSnapshot() {
        return new TreeMap<>(typeRegistry);
    }

    public void markDirty() {
        dirty = true;
        dirtySinceMs = System.currentTimeMillis();
    }

    public void flushIfDue() {
        if (!dirty) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - dirtySinceMs < 1000) {
            return;
        }
        flushNow();
    }

    public void flushNow() {
        Map<String, CalibrationPersistence.PersistedTypeState> snapshot = new TreeMap<>();
        for (Map.Entry<String, ProjectileTypeData> entry : typeRegistry.entrySet()) {
            ProjectileTypeData data = entry.getValue();
            CalibrationPersistence.PersistedTypeState state = new CalibrationPersistence.PersistedTypeState();
            state.gravity = data.getGravity();
            state.drag = data.getDrag();
            state.sampleCount = data.getSampleCount();
            state.errorWeightedSum = data.getErrorWeightedSum();
            state.errorWeightSum = data.getErrorWeightSum();
            state.avgError = data.getAvgError();
            snapshot.put(entry.getKey(), state);
        }
        CalibrationPersistence.save(snapshot);
        persisted.clear();
        persisted.putAll(snapshot);
        dirty = false;
    }

    private void applyPersistedIfPresent(ProjectileTypeData data) {
        CalibrationPersistence.PersistedTypeState state = persisted.get(data.getTypeId());
        if (state == null) {
            return;
        }
        double minDrag = "FIREBALL".equals(data.getTypeId()) ? MIN_PERSISTED_DRAG_GENERIC : MIN_PERSISTED_DRAG_CALIBRATED;
        double gravity = clampFinite(state.gravity, MIN_PERSISTED_GRAVITY, MAX_PERSISTED_GRAVITY, data.getGravity());
        double drag = clampFinite(state.drag, minDrag, MAX_PERSISTED_DRAG, data.getDrag());
        if (Math.abs(gravity - state.gravity) > 1.0e-9 || Math.abs(drag - state.drag) > 1.0e-9) {
            PlayerHighlightClient.LOGGER.warn(
                    "Clamped persisted calibration params for {}: G {} -> {} | D {} -> {}",
                    data.getTypeId(),
                    state.gravity,
                    gravity,
                    state.drag,
                    drag
            );
        }
        data.updateParams(gravity, drag);
        data.setCalibrationAggregates(state.sampleCount, state.errorWeightedSum, state.errorWeightSum);
    }

    private static double clampFinite(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 获取已注册类型数量
     */
    public int getRegisteredTypeCount() {
        return typeRegistry.size();
    }

    /**
     * 清空某个类型的校准样本
     */
    public void clearSamples(String typeId) {
        ProjectileTypeData data = typeRegistry.get(typeId);
        if (data != null) {
            data.resetCalibration();
            markDirty();
        }
    }

    /**
     * 清空所有类型的校准样本
     */
    public void clearAllSamples() {
        for (ProjectileTypeData data : typeRegistry.values()) {
            data.resetCalibration();
        }
        markDirty();
    }

    /**
     * 重置某个类型的“学习数据”：统计清零，并把物理参数重置为默认估算值，同时移除持久化状态。
     */
    public void resetTypeLearning(String typeId) {
        if (typeId == null || typeId.isEmpty()) {
            return;
        }

        ModConfig config = ModConfig.getInstance();
        double gravity;
        double drag;

        if (BowEnchantmentDetector.TYPE_ARROW.equals(typeId)) {
            gravity = config.getArrowGravity();
            drag = config.getArrowDrag();
        } else if (typeId.equals("TRIDENT")) {
            gravity = config.getTridentGravity();
            drag = config.getTridentDrag();
        } else if (typeId.equals("FIREBALL")) {
            gravity = config.getFireballGravity();
            drag = config.getFireballDrag();
        } else {
            gravity = config.getArrowGravity();
            drag = config.getArrowDrag();
        }

        ProjectileTypeData data = typeRegistry.get(typeId);
        if (data == null) {
            data = new ProjectileTypeData(typeId, gravity, drag);
            typeRegistry.put(typeId, data);
            notifiedTypes.put(typeId, true);
        } else {
            data.updateParams(gravity, drag);
        }
        data.resetCalibration();

        persisted.remove(typeId);
        markDirty();
    }

    /**
     * 弹道类型数据类
     */
    public static class ProjectileTypeData {
        private final String typeId;
        private double gravity;
        private double drag;

        // 校准统计
        private int sampleCount = 0;
        private double avgError = 0.0;
        private double errorWeightedSum = 0.0;
        private double errorWeightSum = 0.0;

        public ProjectileTypeData(String typeId, double gravity, double drag) {
            this.typeId = typeId;
            this.gravity = gravity;
            this.drag = drag;
        }

        // Getters
        public String getTypeId() { return typeId; }
        public double getGravity() { return gravity; }
        public double getDrag() { return drag; }
        public int getSampleCount() { return sampleCount; }
        public double getAvgError() { return avgError; }
        public double getErrorWeightedSum() { return errorWeightedSum; }
        public double getErrorWeightSum() { return errorWeightSum; }

        // Update methods
        public void updateParams(double newGravity, double newDrag) {
            this.gravity = newGravity;
            this.drag = newDrag;
        }

        public void recordErrorSample(double rmse, double weight) {
            if (!Double.isFinite(rmse) || !Double.isFinite(weight) || weight <= 0.0) {
                return;
            }
            errorWeightedSum += rmse * weight;
            errorWeightSum += weight;
            sampleCount += 1;
            avgError = errorWeightSum > 0 ? (errorWeightedSum / errorWeightSum) : 0.0;
        }

        public void setCalibrationAggregates(int sampleCount, double errorWeightedSum, double errorWeightSum) {
            if (sampleCount < 0 || !Double.isFinite(errorWeightedSum) || !Double.isFinite(errorWeightSum)) {
                return;
            }
            this.sampleCount = sampleCount;
            this.errorWeightedSum = errorWeightedSum;
            this.errorWeightSum = errorWeightSum;
            this.avgError = errorWeightSum > 0 ? (errorWeightedSum / errorWeightSum) : 0.0;
        }

        public void resetCalibration() {
            this.sampleCount = 0;
            this.avgError = 0.0;
            this.errorWeightedSum = 0.0;
            this.errorWeightSum = 0.0;
        }
    }
}

package com.example.playerhighlight;

import java.util.UUID;

/**
 * 追踪的弹道数据持有类
 *
 * 存储单个弹道的追踪信息，包括UUID、类型、预测结果等
 */
public class TrackedProjectile {
    /** 过期时间：10秒未见则清理 */
    private static final long EXPIRY_TIME_MS = 10000;
    /** 击中后清理时间：3秒 */
    private static final long LANDED_CLEANUP_TIME_MS = 3000;

    private final UUID uuid;
    private final String type;
    private final String shooterName;
    private final String typeId;  // 弹道类型ID（如"ARROW"、"FIREBALL"）
    private final ProjectilePrediction prediction;
    private final boolean isNearPlayer;
    private long lastSeenTime;
    private boolean hasLanded;
    private long landedTime;

    /**
     * 构造函数
     *
     * @param uuid 弹道实体的UUID
     * @param type 弹道类型显示名（如 "Arrow", "Blaze Fireball"）
     * @param shooterName 射手名称（在创建时缓存）
     * @param typeId 弹道类型ID（如"ARROW"、"FIREBALL"）
     * @param prediction 轨迹预测结果
     * @param isNearPlayer 落点是否在警告范围内（创建时快照）
     */
    public TrackedProjectile(UUID uuid, String type, String shooterName, String typeId, ProjectilePrediction prediction, boolean isNearPlayer) {
        this.uuid = uuid;
        this.type = type;
        this.shooterName = shooterName;
        this.typeId = typeId;
        this.prediction = prediction;
        this.isNearPlayer = isNearPlayer;
        this.lastSeenTime = System.currentTimeMillis();
        this.hasLanded = false;
        this.landedTime = 0;
    }

    /**
     * 更新最后见到时间
     */
    public void updateLastSeen() {
        this.lastSeenTime = System.currentTimeMillis();
    }

    /**
     * 标记弹道已击中
     */
    public void markAsLanded() {
        if (!this.hasLanded) {
            this.hasLanded = true;
            this.landedTime = System.currentTimeMillis();
        }
    }

    /**
     * 判断是否已过期
     *
     * @param currentTime 当前时间（毫秒）
     * @return 是否过期
     */
    public boolean isExpired(long currentTime) {
        // 如果已击中，检查击中后是否超过3秒
        if (hasLanded) {
            return (currentTime - landedTime) > LANDED_CLEANUP_TIME_MS;
        }
        // 否则检查是否10秒未见
        return (currentTime - lastSeenTime) > EXPIRY_TIME_MS;
    }

    // Getters
    public UUID getUuid() {
        return uuid;
    }

    public String getType() {
        return type;
    }

    public String getTypeId() {
        return typeId;
    }

    public String getShooterName() {
        return shooterName;
    }

    public ProjectilePrediction getPrediction() {
        return prediction;
    }

    public boolean isNearPlayer() {
        return isNearPlayer;
    }

    public boolean hasLanded() {
        return hasLanded;
    }
}

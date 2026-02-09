package com.example.playerhighlight;

import net.minecraft.util.math.Vec3d;
import java.util.List;

/**
 * 存储弹道预测结果的数据类
 *
 * 包含预测的落点位置、飞行时间、轨迹点等信息
 */
public class ProjectilePrediction {
    /** 预测的落点位置 */
    public final Vec3d landingPos;

    /** 到达落点的时间（tick数） */
    public final int ticksToLand;

    /** 轨迹路径点列表（用于轨迹线渲染） */
    public final List<Vec3d> trajectoryPoints;

    /** 是否碰撞到方块 */
    public final boolean hitBlock;

    /** 计算耗时（纳秒） */
    public final long calculationTimeNs;

    /**
     * 构造函数
     *
     * @param landingPos 预测落点位置
     * @param ticksToLand 飞行时间（tick）
     * @param trajectoryPoints 轨迹点列表
     * @param hitBlock 是否碰撞方块
     * @param calculationTimeNs 计算耗时（纳秒）
     */
    public ProjectilePrediction(Vec3d landingPos, int ticksToLand,
                                List<Vec3d> trajectoryPoints, boolean hitBlock,
                                long calculationTimeNs) {
        this.landingPos = landingPos;
        this.ticksToLand = ticksToLand;
        this.trajectoryPoints = trajectoryPoints;
        this.hitBlock = hitBlock;
        this.calculationTimeNs = calculationTimeNs;
    }

    /**
     * 获取计算耗时（毫秒）
     */
    public double getCalculationTimeMs() {
        return calculationTimeNs / 1_000_000.0;
    }
}

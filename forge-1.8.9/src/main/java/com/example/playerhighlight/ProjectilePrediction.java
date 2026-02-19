package com.example.playerhighlight;

import net.minecraft.util.Vec3;
import java.util.List;

/**
 * 存储弹道预测结果的数据类
 */
public class ProjectilePrediction {
    public final Vec3 landingPos;
    public final int ticksToLand;
    public final List<Vec3> trajectoryPoints;
    public final boolean hitBlock;
    public final long calculationTimeNs;

    public ProjectilePrediction(Vec3 landingPos, int ticksToLand,
                                List<Vec3> trajectoryPoints, boolean hitBlock,
                                long calculationTimeNs) {
        this.landingPos = landingPos;
        this.ticksToLand = ticksToLand;
        this.trajectoryPoints = trajectoryPoints;
        this.hitBlock = hitBlock;
        this.calculationTimeNs = calculationTimeNs;
    }

    public double getCalculationTimeMs() {
        return calculationTimeNs / 1000000.0;
    }
}

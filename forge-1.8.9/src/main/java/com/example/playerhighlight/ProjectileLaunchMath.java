package com.example.playerhighlight;

import net.minecraft.util.Vec3;
import java.util.Random;

public final class ProjectileLaunchMath {
    private ProjectileLaunchMath() {}

    private static final double DIVERGENCE_GAUSSIAN_SCALE = 0.0075;

    public static Vec3 applyDivergence(Vec3 direction, float divergence, long seed) {
        double lenSq = direction.xCoord * direction.xCoord + direction.yCoord * direction.yCoord + direction.zCoord * direction.zCoord;
        if (direction == null || lenSq < 1.0e-12) {
            return direction;
        }
        if (!(divergence > 0.0f)) {
            return direction;
        }

        Random random = new Random(seed);
        double scale = DIVERGENCE_GAUSSIAN_SCALE * (double) divergence;

        Vec3 base = direction.normalize();
        Vec3 inaccurate = new Vec3(
                base.xCoord + random.nextGaussian() * scale,
                base.yCoord + random.nextGaussian() * scale,
                base.zCoord + random.nextGaussian() * scale
        );

        double inaccLenSq = inaccurate.xCoord * inaccurate.xCoord + inaccurate.yCoord * inaccurate.yCoord + inaccurate.zCoord * inaccurate.zCoord;
        return inaccLenSq < 1.0e-12 ? base : inaccurate;
    }
}

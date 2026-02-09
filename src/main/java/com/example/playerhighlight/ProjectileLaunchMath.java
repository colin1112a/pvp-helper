package com.example.playerhighlight;

import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Vanilla-like launch math helpers.
 *
 * Used by pre-shoot preview (no entity exists yet), where we must emulate the
 * inaccuracy (divergence) applied by {@code ProjectileEntity#setVelocity}.
 */
public final class ProjectileLaunchMath {
    private ProjectileLaunchMath() {
    }

    /**
     * Vanilla constant: gaussian scale used by projectile divergence.
     */
    private static final double DIVERGENCE_GAUSSIAN_SCALE = 0.0075;

    /**
     * Applies vanilla-like divergence (random Gaussian offset) to a direction vector.
     *
     * <p>Vanilla logic (simplified): normalize, add (gaussian * 0.0075 * divergence) to each component,
     * then multiply by speed (no re-normalization after the Gaussian add).</p>
     *
     * @param direction direction vector (will be normalized)
     * @param divergence divergence (inaccuracy) parameter, e.g. bow uses 1.0
     * @param seed deterministic seed for preview stability
     */
    public static Vec3d applyDivergence(Vec3d direction, float divergence, long seed) {
        if (direction == null || direction.lengthSquared() < 1.0e-12) {
            return direction;
        }
        if (!(divergence > 0.0f)) {
            return direction;
        }

        Random random = new Random(seed);
        double scale = DIVERGENCE_GAUSSIAN_SCALE * (double) divergence;

        Vec3d base = direction.normalize();
        Vec3d inaccurate = new Vec3d(
                base.x + random.nextGaussian() * scale,
                base.y + random.nextGaussian() * scale,
                base.z + random.nextGaussian() * scale
        );

        // Keep as-is (no normalize) to match vanilla's slight speed variance.
        return inaccurate.lengthSquared() < 1.0e-12 ? base : inaccurate;
    }
}

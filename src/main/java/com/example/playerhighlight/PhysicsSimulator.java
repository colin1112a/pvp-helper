package com.example.playerhighlight;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 物理模拟器 - 用于预测弹道轨迹
 *
 * 使用可配置的物理模型模拟重力、空气阻力等效果
 * 目标：<100ms 完成单次预测
 * 支持实时参数校准
 */
public class PhysicsSimulator {

    // 优化参数
    private static final int RAYCAST_INTERVAL = 1;  // 每 tick 检测一次碰撞（提升落点精度）
    private static final int TRAJECTORY_SAVE_INTERVAL = 3;  // 每3 tick 存储一个轨迹点
    private static final double MIN_VELOCITY_SQUARED = 0.001;  // 速度阈值
    private static final double LOW_SPEED_GROUND_CHECK_DISTANCE = 0.5;
    private static final double LOW_SPEED_GROUND_SNAP_EPSILON = 0.06;

    /**
     * 预测弹道轨迹
     *
     * @param projectile 弹道实体
     * @param typeId 弹道类型ID（如"ARROW"、"TRIDENT"）
     * @param world 世界对象
     * @param maxTicks 最大模拟时间（tick）
     * @return 预测结果
     */
    public static ProjectilePrediction predictTrajectory(Entity projectile, String typeId, World world, int maxTicks) {
        return predictTrajectoryFromState(
                projectile.getPos(),
                projectile.getVelocity(),
                typeId,
                world,
                maxTicks,
                projectile,
                projectile.hasNoGravity()
        );
    }

    /**
     * 从给定初始状态预测弹道轨迹（用于未生成实体的“预瞄预测”等场景）
     */
    public static ProjectilePrediction predictTrajectoryFromState(Vec3d pos, Vec3d velocity, String typeId,
                                                                  World world, int maxTicks,
                                                                  Entity raycastEntity,
                                                                  boolean noGravity) {
        long startTime = System.nanoTime();

        if (typeId == null || typeId.isEmpty()) {
            typeId = "UNKNOWN";
        }

        // 从动态注册表获取物理参数
        DynamicProjectileRegistry registry = DynamicProjectileRegistry.getInstance();
        DynamicProjectileRegistry.ProjectileTypeData typeData = registry.getOrCreateType(typeId);
        double gravity = noGravity ? 0.0 : typeData.getGravity();
        double drag = typeData.getDrag();
        ModConfig config = ModConfig.getInstance();
        boolean simulateFluidDrag = config.isSimulateFluidDrag();
        boolean arrowLike = BowEnchantmentDetector.TYPE_ARROW.equals(typeId);

        // 模拟轨迹
        int expectedPoints = (maxTicks / TRAJECTORY_SAVE_INTERVAL) + 3;
        List<Vec3d> trajectory = new ArrayList<>(expectedPoints);
        trajectory.add(pos);

        Vec3d currentPos = pos;
        double velX = velocity.x;
        double velY = velocity.y;
        double velZ = velocity.z;
        boolean hitBlock = false;
        int ticksSimulated = 0;

        for (int tick = 0; tick < maxTicks; tick++) {
            ticksSimulated = tick + 1;

            // Minecraft projectile movement is: move by current velocity, then apply drag/gravity to velocity.
            Vec3d nextPos = new Vec3d(
                    currentPos.x + velX,
                    currentPos.y + velY,
                    currentPos.z + velZ
            );

            // 每 tick 检测一次碰撞（提升落点精度）
            if (tick % RAYCAST_INTERVAL == 0) {
                BlockHitResult hit = world.raycast(new RaycastContext(
                        currentPos,
                        nextPos,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        raycastEntity
                ));

                if (hit.getType() != HitResult.Type.MISS) {
                    // 碰撞到方块
                    nextPos = hit.getPos();
                    hitBlock = true;
                    currentPos = nextPos;
                    trajectory.add(nextPos);
                    break;
                }
            }

            currentPos = nextPos;

            // 每隔 TRAJECTORY_SAVE_INTERVAL tick 存储一个轨迹点
            if (tick % TRAJECTORY_SAVE_INTERVAL == 0) {
                trajectory.add(currentPos);
            }

            // 应用空气/流体阻力（影响下一 tick 速度）
            double tickDrag = drag;
            if (simulateFluidDrag && arrowLike && isInAnyFluid(world, currentPos)) {
                tickDrag = config.getArrowWaterDrag();
            }
            velX *= tickDrag;
            velY = velY * tickDrag - gravity;
            velZ *= tickDrag;

            // 速度过低时提前终止（性能优化）
            // 注意：不能在半空中仅因速度低就终止，否则会导致“向上射箭”落点预测停在半空。
            double speedSq = velX * velX + velY * velY + velZ * velZ;
            if (speedSq < MIN_VELOCITY_SQUARED) {
                Vec3d snapped = trySnapToNearbyGround(world, raycastEntity, currentPos);
                if (snapped != null) {
                    currentPos = snapped;
                    trajectory.add(currentPos);
                    break;
                }
            }
        }

        long endTime = System.nanoTime();
        long calculationTime = endTime - startTime;

        return new ProjectilePrediction(
                currentPos,
                ticksSimulated,
                trajectory,
                hitBlock,
                calculationTime
        );
    }

    private static boolean isInAnyFluid(World world, Vec3d pos) {
        if (world == null || pos == null) {
            return false;
        }
        BlockPos blockPos = BlockPos.ofFloored(pos);
        return !world.getFluidState(blockPos).isEmpty();
    }

    private static Vec3d trySnapToNearbyGround(World world, Entity raycastEntity, Vec3d pos) {
        BlockHitResult downHit = world.raycast(new RaycastContext(
                pos,
                pos.subtract(0, LOW_SPEED_GROUND_CHECK_DISTANCE, 0),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                raycastEntity
        ));
        if (downHit.getType() == HitResult.Type.MISS) {
            return null;
        }

        double verticalGap = pos.y - downHit.getPos().y;
        if (verticalGap <= LOW_SPEED_GROUND_SNAP_EPSILON) {
            return downHit.getPos();
        }

        return null;
    }
}

package com.example.playerhighlight;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
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
    private static final int RAYCAST_INTERVAL = 1;
    private static final int TRAJECTORY_SAVE_INTERVAL = 3;
    private static final double MIN_VELOCITY_SQUARED = 0.001;
    private static final double LOW_SPEED_GROUND_CHECK_DISTANCE = 0.5;
    private static final double LOW_SPEED_GROUND_SNAP_EPSILON = 0.06;

    /**
     * 预测弹道轨迹
     */
    public static ProjectilePrediction predictTrajectory(Entity projectile, String typeId, World world, int maxTicks) {
        return predictTrajectoryFromState(
                projectile.getPositionVector(),
                new Vec3(projectile.motionX, projectile.motionY, projectile.motionZ),
                typeId,
                world,
                maxTicks,
                projectile,
                false
        );
    }

    /**
     * 从给定初始状态预测弹道轨迹（用于未生成实体的"预瞄预测"等场景）
     */
    public static ProjectilePrediction predictTrajectoryFromState(
            Vec3 pos, Vec3 velocity, String typeId,
            World world, int maxTicks,
            Entity raycastEntity,
            boolean noGravity) {
        long startTime = System.nanoTime();

        if (typeId == null || typeId.isEmpty()) {
            typeId = "UNKNOWN";
        }

        DynamicProjectileRegistry registry = DynamicProjectileRegistry.getInstance();
        DynamicProjectileRegistry.ProjectileTypeData typeData = registry.getOrCreateType(typeId);
        double gravity = noGravity ? 0.0 : typeData.getGravity();
        double drag = typeData.getDrag();
        ModConfig config = ModConfig.getInstance();
        boolean simulateFluidDrag = config.isSimulateFluidDrag();
        boolean arrowLike = BowEnchantmentDetector.TYPE_ARROW.equals(typeId);

        int expectedPoints = (maxTicks / TRAJECTORY_SAVE_INTERVAL) + 3;
        List<Vec3> trajectory = new ArrayList<Vec3>(expectedPoints);
        trajectory.add(pos);

        Vec3 currentPos = pos;
        double velX = velocity.xCoord;
        double velY = velocity.yCoord;
        double velZ = velocity.zCoord;
        boolean hitBlock = false;
        int ticksSimulated = 0;

        for (int tick = 0; tick < maxTicks; tick++) {
            ticksSimulated = tick + 1;

            Vec3 nextPos = new Vec3(
                    currentPos.xCoord + velX,
                    currentPos.yCoord + velY,
                    currentPos.zCoord + velZ
            );

            if (tick % RAYCAST_INTERVAL == 0) {
                MovingObjectPosition hit = world.rayTraceBlocks(currentPos, nextPos);
                if (hit != null) {
                    nextPos = hit.hitVec;
                    hitBlock = true;
                    currentPos = nextPos;
                    trajectory.add(nextPos);
                    break;
                }
            }

            currentPos = nextPos;

            if (tick % TRAJECTORY_SAVE_INTERVAL == 0) {
                trajectory.add(currentPos);
            }

            double tickDrag = drag;
            if (simulateFluidDrag && arrowLike && isInAnyFluid(world, currentPos)) {
                tickDrag = config.getArrowWaterDrag();
            }
            velX *= tickDrag;
            velY = velY * tickDrag - gravity;
            velZ *= tickDrag;

            double speedSq = velX * velX + velY * velY + velZ * velZ;
            if (speedSq < MIN_VELOCITY_SQUARED) {
                Vec3 snapped = trySnapToNearbyGround(world, currentPos);
                if (snapped != null) {
                    currentPos = snapped;
                    trajectory.add(currentPos);
                    break;
                }
            }
        }

        long endTime = System.nanoTime();
        return new ProjectilePrediction(currentPos, ticksSimulated, trajectory, hitBlock, endTime - startTime);
    }

    private static boolean isInAnyFluid(World world, Vec3 pos) {
        if (world == null || pos == null) {
            return false;
        }
        BlockPos blockPos = new BlockPos(pos.xCoord, pos.yCoord, pos.zCoord);
        Block block = world.getBlockState(blockPos).getBlock();
        return block == Blocks.water || block == Blocks.flowing_water
                || block == Blocks.lava || block == Blocks.flowing_lava;
    }

    private static Vec3 trySnapToNearbyGround(World world, Vec3 pos) {
        Vec3 downTarget = new Vec3(pos.xCoord, pos.yCoord - LOW_SPEED_GROUND_CHECK_DISTANCE, pos.zCoord);
        MovingObjectPosition downHit = world.rayTraceBlocks(pos, downTarget);
        if (downHit == null) {
            return null;
        }
        double verticalGap = pos.yCoord - downHit.hitVec.yCoord;
        if (verticalGap <= LOW_SPEED_GROUND_SNAP_EPSILON) {
            return downHit.hitVec;
        }
        return null;
    }
}

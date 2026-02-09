package com.example.playerhighlight;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实际轨迹记录器
 *
 * 记录弹射物的真实飞行路径，用于校准系统对比预测精度
 */
public class TrajectoryRecorder {

    private static final int MAX_RECORDING_TICKS = 400;  // 最多记录400 tick（上抛/长距离飞行也能覆盖）
    private static final double STILL_THRESHOLD_SQ = 0.01 * 0.01;
    private static final int STILL_TICKS_TO_FINALIZE = 4;
    private static final ConcurrentHashMap<UUID, RecordedTrajectory> recordings = new ConcurrentHashMap<>();

    /**
     * 开始记录弹射物轨迹
     */
    public static void startRecording(UUID projectileId, String typeId,
                                       Vec3d initialPos, Vec3d initialVel,
                                       boolean localPlayerShot) {
        RecordedTrajectory recording = new RecordedTrajectory(typeId, initialPos, initialVel, localPlayerShot);
        recordings.put(projectileId, recording);
    }

    /**
     * 更新弹射物位置（每tick调用）
     */
    public static void recordPosition(UUID projectileId, Vec3d position) {
        RecordedTrajectory recording = recordings.get(projectileId);
        if (recording != null && !recording.isComplete()) {
            recording.addPosition(position);
        }
    }

    /**
     * 标记弹射物已落地
     */
    public static void markLanded(UUID projectileId, Vec3d landingPos) {
        RecordedTrajectory recording = recordings.get(projectileId);
        if (recording != null) {
            if (recording.isComplete()) {
                return;
            }

            Vec3d resolvedLandingPos = landingPos != null ? landingPos : recording.getLastRecordedPos();
            if (resolvedLandingPos == null) {
                return;
            }

            recording.markComplete(resolvedLandingPos);

            // 自动触发校准
            if (recording.isValid()) {
                CalibrationSystem.onRecordingComplete(recording);
            }
        }
    }

    /**
     * 获取记录（供调试用）
     */
    public static RecordedTrajectory getRecording(UUID projectileId) {
        return recordings.get(projectileId);
    }

    /**
     * 丢弃某个弹道的记录（用于样本过滤/限流）。
     *
     * <p>丢弃后将不会进入校准学习。</p>
     */
    public static void discardRecording(UUID projectileId) {
        if (projectileId == null) {
            return;
        }
        recordings.remove(projectileId);
    }

    /**
     * 清理过期记录
     */
    public static void cleanup() {
        long currentTime = System.currentTimeMillis();
        recordings.entrySet().removeIf(entry ->
            (currentTime - entry.getValue().startTime) > 120000  // 120秒后清理：避免箭停在方块里导致样本被提前丢弃
        );
    }

    /**
     * 记录的轨迹数据
     */
    public static class RecordedTrajectory {
        public final String typeId;  // 弹道类型ID（如"ARROW"、"TRIDENT"）
        public final Vec3d initialPos;
        public final Vec3d initialVel;
        public final List<Vec3d> positions;
        public final long startTime;
        public final boolean localPlayerShot;

        private Vec3d actualLandingPos = null;
        private boolean complete = false;
        private int tickCount = 0;
        private int stillCount = 0;

        public RecordedTrajectory(String typeId, Vec3d initialPos, Vec3d initialVel, boolean localPlayerShot) {
            this.typeId = typeId;
            this.initialPos = initialPos;
            this.initialVel = initialVel;
            this.positions = new ArrayList<>();
            this.startTime = System.currentTimeMillis();
            this.localPlayerShot = localPlayerShot;
            this.positions.add(initialPos);
        }

        public void addPosition(Vec3d pos) {
            if (tickCount < MAX_RECORDING_TICKS) {
                Vec3d prev = positions.isEmpty() ? null : positions.get(positions.size() - 1);
                positions.add(pos);
                tickCount++;

                if (prev != null) {
                    double deltaSq = pos.squaredDistanceTo(prev);
                    if (deltaSq < STILL_THRESHOLD_SQ) {
                        stillCount++;
                    } else {
                        stillCount = 0;
                    }

                    // 对“卡在方块里不消失”的箭，检测到静止后直接完成记录并触发校准
                    if (!complete && stillCount >= STILL_TICKS_TO_FINALIZE) {
                        markComplete(pos);
                        if (isValid()) {
                            CalibrationSystem.onRecordingComplete(this);
                        }
                    }
                }
            } else {
                complete = true;  // 超时，停止记录
            }
        }

        public void markComplete(Vec3d landingPos) {
            if (this.complete) {
                return;
            }
            this.actualLandingPos = landingPos;
            this.complete = true;
        }

        public boolean isComplete() {
            return complete;
        }

        public Vec3d getLastRecordedPos() {
            if (positions.isEmpty()) {
                return null;
            }
            return positions.get(positions.size() - 1);
        }

        /**
         * 判断记录是否有效（用于校准）
         *
         * 有效条件：
         * 1. 已完成
         * 2. 至少飞行了10 tick
         * 3. 有明确的落点
         */
        public boolean isValid() {
            return complete && tickCount >= 6 && actualLandingPos != null;
        }

        public Vec3d getActualLandingPos() {
            return actualLandingPos;
        }

        public int getTickCount() {
            return tickCount;
        }
    }

    /**
     * 清空所有记录
     */
    public static void clearAll() {
        recordings.clear();
    }

    /**
     * 获取当前记录数量
     */
    public static int getRecordingCount() {
        return recordings.size();
    }
}

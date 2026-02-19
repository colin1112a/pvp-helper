package com.example.playerhighlight;

import net.minecraft.util.Vec3;

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
    private static final ConcurrentHashMap<UUID, RecordedTrajectory> recordings = new ConcurrentHashMap<UUID, RecordedTrajectory>();

    /**
     * 开始记录弹射物轨迹
     */
    public static void startRecording(UUID projectileId, String typeId,
                                       Vec3 initialPos, Vec3 initialVel,
                                       boolean localPlayerShot) {
        RecordedTrajectory recording = new RecordedTrajectory(typeId, initialPos, initialVel, localPlayerShot);
        recordings.put(projectileId, recording);
    }

    /**
     * 更新弹射物位置（每tick调用）
     */
    public static void recordPosition(UUID projectileId, Vec3 position) {
        RecordedTrajectory recording = recordings.get(projectileId);
        if (recording != null && !recording.isComplete()) {
            recording.addPosition(position);
        }
    }

    /**
     * 标记弹射物已落地
     */
    public static void markLanded(UUID projectileId, Vec3 landingPos) {
        RecordedTrajectory recording = recordings.get(projectileId);
        if (recording != null) {
            if (recording.isComplete()) {
                return;
            }

            Vec3 resolvedLandingPos = landingPos != null ? landingPos : recording.getLastRecordedPos();
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
        java.util.Iterator<java.util.Map.Entry<UUID, RecordedTrajectory>> it = recordings.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<UUID, RecordedTrajectory> entry = it.next();
            if ((currentTime - entry.getValue().startTime) > 120000) {
                it.remove();
            }
        }
    }

    /**
     * 记录的轨迹数据
     */
    public static class RecordedTrajectory {
        public final String typeId;
        public final Vec3 initialPos;
        public final Vec3 initialVel;
        public final List<Vec3> positions;
        public final long startTime;
        public final boolean localPlayerShot;

        private Vec3 actualLandingPos = null;
        private boolean complete = false;
        private int tickCount = 0;
        private int stillCount = 0;

        public RecordedTrajectory(String typeId, Vec3 initialPos, Vec3 initialVel, boolean localPlayerShot) {
            this.typeId = typeId;
            this.initialPos = initialPos;
            this.initialVel = initialVel;
            this.positions = new ArrayList<Vec3>();
            this.startTime = System.currentTimeMillis();
            this.localPlayerShot = localPlayerShot;
            this.positions.add(initialPos);
        }

        public void addPosition(Vec3 pos) {
            if (tickCount < MAX_RECORDING_TICKS) {
                Vec3 prev = positions.isEmpty() ? null : positions.get(positions.size() - 1);
                positions.add(pos);
                tickCount++;

                if (prev != null) {
                    double deltaSq = pos.squareDistanceTo(prev);
                    if (deltaSq < STILL_THRESHOLD_SQ) {
                        stillCount++;
                    } else {
                        stillCount = 0;
                    }

                    // 对"卡在方块里不消失"的箭，检测到静止后直接完成记录并触发校准
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

        public void markComplete(Vec3 landingPos) {
            if (this.complete) {
                return;
            }
            this.actualLandingPos = landingPos;
            this.complete = true;
        }

        public boolean isComplete() {
            return complete;
        }

        public Vec3 getLastRecordedPos() {
            if (positions.isEmpty()) {
                return null;
            }
            return positions.get(positions.size() - 1);
        }

        /**
         * 判断记录是否有效（用于校准）
         */
        public boolean isValid() {
            return complete && tickCount >= 6 && actualLandingPos != null;
        }

        public Vec3 getActualLandingPos() {
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

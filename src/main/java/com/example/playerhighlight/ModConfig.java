package com.example.playerhighlight;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Mod配置管理类
 *
 * 存储和管理玩家透视和弹道预测功能的开关状态
 */
public class ModConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("playerhighlight.properties");

    private static ModConfig INSTANCE;

    // 配置项
    private boolean playerHighlightEnabled = true;
    private boolean projectilePredictionEnabled = true;
    private boolean trajectoryLineEnabled = true;
    private boolean bowPreviewEnabled = true;
    private boolean bowPreviewTrajectoryEnabled = true;
    private boolean bowPreviewLandingMarkerEnabled = true;
    private boolean bowPreviewSimulateInaccuracy = true;
    private double nearbyWarningRange = 20.0;
    private int bowPreviewMaxTicks = 200;

    // 物理参数配置
    private double arrowGravity = 0.05;
    private double arrowDrag = 0.99;
    private boolean simulateFluidDrag = true;
    private double arrowWaterDrag = 0.6;
    private double tridentGravity = 0.05;
    private double tridentDrag = 0.99;
    private double fireballGravity = 0.0;
    private double fireballDrag = 1.0;

    // 校准系统配置
    private boolean autoCalibrationEnabled = true;
    private boolean debugMode = false;

    /**
     * 获取配置实例
     */
    public static ModConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    /**
     * 加载配置文件
     */
    private static ModConfig load() {
        ModConfig config = new ModConfig();

        if (Files.exists(CONFIG_PATH)) {
            boolean shouldSave = false;
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                Properties props = new Properties();
                props.load(in);

                config.playerHighlightEnabled = Boolean.parseBoolean(
                        props.getProperty("playerHighlightEnabled", "true"));
                config.projectilePredictionEnabled = Boolean.parseBoolean(
                        props.getProperty("projectilePredictionEnabled", "true"));
                config.trajectoryLineEnabled = Boolean.parseBoolean(
                        props.getProperty("trajectoryLineEnabled", "true"));
                config.bowPreviewEnabled = Boolean.parseBoolean(
                        props.getProperty("bowPreviewEnabled", "true"));
                config.bowPreviewTrajectoryEnabled = Boolean.parseBoolean(
                        props.getProperty("bowPreviewTrajectoryEnabled", "true"));
                config.bowPreviewLandingMarkerEnabled = Boolean.parseBoolean(
                        props.getProperty("bowPreviewLandingMarkerEnabled", "true"));
                config.bowPreviewSimulateInaccuracy = Boolean.parseBoolean(
                        props.getProperty("bowPreviewSimulateInaccuracy", "true"));
                // 预瞄最大模拟 tick：对非法/越界值进行修正并写回配置文件
                String rawPreviewTicks = props.getProperty("bowPreviewMaxTicks");
                int loadedPreviewTicks = 200;
                if (rawPreviewTicks != null) {
                    try {
                        loadedPreviewTicks = Integer.parseInt(rawPreviewTicks.trim());
                    } catch (NumberFormatException e) {
                        shouldSave = true;
                        loadedPreviewTicks = 200;
                    }
                }
                int clampedPreviewTicks = clampPreviewTicks(loadedPreviewTicks);
                if (clampedPreviewTicks != loadedPreviewTicks) {
                    shouldSave = true;
                }
                config.bowPreviewMaxTicks = clampedPreviewTicks;

                // 加载物理参数
                double arrowGravity = clampGravity(parseDouble(props, "arrowGravity", 0.05));
                double arrowDrag = clampCalibrationDrag(parseDouble(props, "arrowDrag", 0.99));
                boolean simulateFluidDrag = Boolean.parseBoolean(props.getProperty("simulateFluidDrag", "true"));
                double arrowWaterDrag = clampArrowWaterDrag(parseDouble(props, "arrowWaterDrag", 0.6));
                double tridentGravity = clampGravity(parseDouble(props, "tridentGravity", 0.05));
                double tridentDrag = clampCalibrationDrag(parseDouble(props, "tridentDrag", 0.99));
                double fireballGravity = clampGravity(parseDouble(props, "fireballGravity", 0.0));
                double fireballDrag = clampDrag01(parseDouble(props, "fireballDrag", 0.95));

                config.arrowGravity = arrowGravity;
                config.arrowDrag = arrowDrag;
                config.simulateFluidDrag = simulateFluidDrag;
                config.arrowWaterDrag = arrowWaterDrag;
                config.tridentGravity = tridentGravity;
                config.tridentDrag = tridentDrag;
                config.fireballGravity = fireballGravity;
                config.fireballDrag = fireballDrag;

                shouldSave |= didChange(props, "arrowGravity", arrowGravity);
                shouldSave |= didChange(props, "arrowDrag", arrowDrag);
                shouldSave |= didChange(props, "arrowWaterDrag", arrowWaterDrag);
                shouldSave |= didChange(props, "tridentGravity", tridentGravity);
                shouldSave |= didChange(props, "tridentDrag", tridentDrag);
                shouldSave |= didChange(props, "fireballGravity", fireballGravity);
                shouldSave |= didChange(props, "fireballDrag", fireballDrag);
                shouldSave |= !rawBoolEquals(props, "simulateFluidDrag", simulateFluidDrag);
                shouldSave |= !rawBoolEquals(props, "bowPreviewSimulateInaccuracy", config.bowPreviewSimulateInaccuracy);
                shouldSave |= props.getProperty("arrowWaterDrag") == null;

                // 加载校准配置
                config.autoCalibrationEnabled = Boolean.parseBoolean(
                        props.getProperty("autoCalibrationEnabled", "true"));
                config.debugMode = Boolean.parseBoolean(
                        props.getProperty("debugMode", "false"));

                // 警告范围（格）：对非法/越界值进行修正并写回配置文件
                String rawNearbyRange = props.getProperty("nearbyWarningRange");
                double loadedNearbyRange = 20.0;
                if (rawNearbyRange != null) {
                    try {
                        loadedNearbyRange = Double.parseDouble(rawNearbyRange);
                        if (!Double.isFinite(loadedNearbyRange)) {
                            shouldSave = true;
                            loadedNearbyRange = 20.0;
                        }
                    } catch (NumberFormatException e) {
                        shouldSave = true;
                        loadedNearbyRange = 20.0;
                    }
                }

                double clampedNearbyRange = clampRange(loadedNearbyRange);
                double roundedNearbyRange = Math.round(clampedNearbyRange);
                if (Math.abs(roundedNearbyRange - loadedNearbyRange) > 0.0001) {
                    shouldSave = true;
                }
                config.nearbyWarningRange = roundedNearbyRange;
            } catch (IOException e) {
                PlayerHighlightClient.LOGGER.error("Failed to load config", e);
            }
            if (shouldSave) {
                config.save();
            }
        } else {
            config.save();
        }

        return config;
    }

    /**
     * 保存配置文件
     */
    public void save() {
        try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
            Properties props = new Properties();
            props.setProperty("playerHighlightEnabled", String.valueOf(playerHighlightEnabled));
            props.setProperty("projectilePredictionEnabled", String.valueOf(projectilePredictionEnabled));
            props.setProperty("trajectoryLineEnabled", String.valueOf(trajectoryLineEnabled));
            props.setProperty("nearbyWarningRange", String.valueOf(nearbyWarningRange));
            props.setProperty("bowPreviewEnabled", String.valueOf(bowPreviewEnabled));
            props.setProperty("bowPreviewTrajectoryEnabled", String.valueOf(bowPreviewTrajectoryEnabled));
            props.setProperty("bowPreviewLandingMarkerEnabled", String.valueOf(bowPreviewLandingMarkerEnabled));
            props.setProperty("bowPreviewSimulateInaccuracy", String.valueOf(bowPreviewSimulateInaccuracy));
            props.setProperty("bowPreviewMaxTicks", String.valueOf(bowPreviewMaxTicks));

            // 保存物理参数
            props.setProperty("arrowGravity", String.valueOf(arrowGravity));
            props.setProperty("arrowDrag", String.valueOf(arrowDrag));
            props.setProperty("simulateFluidDrag", String.valueOf(simulateFluidDrag));
            props.setProperty("arrowWaterDrag", String.valueOf(arrowWaterDrag));
            props.setProperty("tridentGravity", String.valueOf(tridentGravity));
            props.setProperty("tridentDrag", String.valueOf(tridentDrag));
            props.setProperty("fireballGravity", String.valueOf(fireballGravity));
            props.setProperty("fireballDrag", String.valueOf(fireballDrag));

            // 保存校准配置
            props.setProperty("autoCalibrationEnabled", String.valueOf(autoCalibrationEnabled));
            props.setProperty("debugMode", String.valueOf(debugMode));

            props.store(out, "Player Highlight Mod Configuration");
        } catch (IOException e) {
            PlayerHighlightClient.LOGGER.error("Failed to save config", e);
        }
    }

    private static double parseDouble(Properties props, String key, double defaultValue) {
        String value = props.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean didChange(Properties props, String key, double nowValue) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return false;
        }
        try {
            double parsed = Double.parseDouble(raw);
            if (!Double.isFinite(parsed)) {
                return true;
            }
            return Math.abs(parsed - nowValue) > 1.0e-9;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static double clampRange(double value) {
        if (!Double.isFinite(value)) {
            return 20.0;
        }
        // 合理范围：1~128格
        return Math.max(1.0, Math.min(128.0, value));
    }

    private static int clampPreviewTicks(int ticks) {
        // 合理范围：1~400 tick
        return Math.max(1, Math.min(400, ticks));
    }

    private static double clampGravity(double value) {
        if (!Double.isFinite(value)) {
            return 0.05;
        }
        return Math.max(0.0, Math.min(0.2, value));
    }

    private static double clampCalibrationDrag(double value) {
        if (!Double.isFinite(value)) {
            return 0.99;
        }
        return Math.max(0.90, Math.min(1.0, value));
    }

    private static double clampDrag01(double value) {
        if (!Double.isFinite(value)) {
            return 0.99;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clampArrowWaterDrag(double value) {
        if (!Double.isFinite(value)) {
            return 0.6;
        }
        return Math.max(0.05, Math.min(1.0, value));
    }

    private static boolean rawBoolEquals(Properties props, String key, boolean nowValue) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return false;
        }
        return Boolean.parseBoolean(raw) == nowValue;
    }

    // Getters and Setters

    public boolean isPlayerHighlightEnabled() {
        return playerHighlightEnabled;
    }

    public void setPlayerHighlightEnabled(boolean enabled) {
        this.playerHighlightEnabled = enabled;
        save();
    }

    public boolean isProjectilePredictionEnabled() {
        return projectilePredictionEnabled;
    }

    public void setProjectilePredictionEnabled(boolean enabled) {
        this.projectilePredictionEnabled = enabled;
        save();
    }

    public boolean isTrajectoryLineEnabled() {
        return trajectoryLineEnabled;
    }

    public void setTrajectoryLineEnabled(boolean enabled) {
        this.trajectoryLineEnabled = enabled;
        save();
    }

    public boolean isBowPreviewEnabled() {
        return bowPreviewEnabled;
    }

    public void setBowPreviewEnabled(boolean enabled) {
        this.bowPreviewEnabled = enabled;
        save();
    }

    public boolean isBowPreviewTrajectoryEnabled() {
        return bowPreviewTrajectoryEnabled;
    }

    public void setBowPreviewTrajectoryEnabled(boolean enabled) {
        this.bowPreviewTrajectoryEnabled = enabled;
        save();
    }

    public boolean isBowPreviewLandingMarkerEnabled() {
        return bowPreviewLandingMarkerEnabled;
    }

    public void setBowPreviewLandingMarkerEnabled(boolean enabled) {
        this.bowPreviewLandingMarkerEnabled = enabled;
        save();
    }

    public boolean isBowPreviewSimulateInaccuracy() {
        return bowPreviewSimulateInaccuracy;
    }

    public void setBowPreviewSimulateInaccuracy(boolean enabled) {
        this.bowPreviewSimulateInaccuracy = enabled;
        save();
    }

    public int getBowPreviewMaxTicks() {
        return bowPreviewMaxTicks;
    }

    public void setBowPreviewMaxTicks(int maxTicks) {
        int clamped = clampPreviewTicks(maxTicks);
        if (this.bowPreviewMaxTicks == clamped) {
            return;
        }
        this.bowPreviewMaxTicks = clamped;
        save();
    }

    public double getNearbyWarningRange() {
        return nearbyWarningRange;
    }

    public void setNearbyWarningRange(double range) {
        double clamped = clampRange(range);
        // UI 使用整数格；避免拖动滑条时频繁写盘
        clamped = Math.round(clamped);
        if (Math.abs(this.nearbyWarningRange - clamped) < 0.0001) {
            return;
        }
        this.nearbyWarningRange = clamped;
        save();
    }

    // 物理参数 getters/setters

    public double getArrowGravity() {
        return arrowGravity;
    }

    public void setArrowGravity(double arrowGravity) {
        this.arrowGravity = clampGravity(arrowGravity);
        save();
    }

    public double getArrowDrag() {
        return arrowDrag;
    }

    public void setArrowDrag(double arrowDrag) {
        this.arrowDrag = clampCalibrationDrag(arrowDrag);
        save();
    }

    public boolean isSimulateFluidDrag() {
        return simulateFluidDrag;
    }

    public void setSimulateFluidDrag(boolean enabled) {
        this.simulateFluidDrag = enabled;
        save();
    }

    public double getArrowWaterDrag() {
        return arrowWaterDrag;
    }

    public void setArrowWaterDrag(double arrowWaterDrag) {
        this.arrowWaterDrag = clampArrowWaterDrag(arrowWaterDrag);
        save();
    }

    public double getTridentGravity() {
        return tridentGravity;
    }

    public void setTridentGravity(double tridentGravity) {
        this.tridentGravity = clampGravity(tridentGravity);
        save();
    }

    public double getTridentDrag() {
        return tridentDrag;
    }

    public void setTridentDrag(double tridentDrag) {
        this.tridentDrag = clampCalibrationDrag(tridentDrag);
        save();
    }

    public double getFireballGravity() {
        return fireballGravity;
    }

    public void setFireballGravity(double fireballGravity) {
        this.fireballGravity = clampGravity(fireballGravity);
        save();
    }

    public double getFireballDrag() {
        return fireballDrag;
    }

    public void setFireballDrag(double fireballDrag) {
        this.fireballDrag = clampDrag01(fireballDrag);
        save();
    }

    // 校准配置 getters/setters

    public boolean isAutoCalibrationEnabled() {
        return autoCalibrationEnabled;
    }

    public void setAutoCalibrationEnabled(boolean enabled) {
        this.autoCalibrationEnabled = enabled;
        save();
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean enabled) {
        this.debugMode = enabled;
        save();
    }
}

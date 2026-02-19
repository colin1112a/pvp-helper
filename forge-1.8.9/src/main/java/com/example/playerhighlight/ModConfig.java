package com.example.playerhighlight;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ModConfig {
    private static Path getConfigPath() {
        File configDir = new File(Minecraft.getMinecraft().mcDataDir, "config");
        return configDir.toPath().resolve("playerhighlight.properties");
    }

    private static ModConfig INSTANCE;

    private boolean playerHighlightEnabled = true;
    private boolean projectilePredictionEnabled = true;
    private boolean trajectoryLineEnabled = true;
    private boolean bowPreviewEnabled = true;
    private boolean bowPreviewTrajectoryEnabled = true;
    private boolean bowPreviewLandingMarkerEnabled = true;
    private boolean bowPreviewSimulateInaccuracy = true;
    private double nearbyWarningRange = 20.0;
    private int bowPreviewMaxTicks = 200;

    private double arrowGravity = 0.05;
    private double arrowDrag = 0.99;
    private boolean simulateFluidDrag = true;
    private double arrowWaterDrag = 0.6;
    private double fireballGravity = 0.0;
    private double fireballDrag = 1.0;

    private boolean autoCalibrationEnabled = true;
    private boolean debugMode = false;

    public static ModConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    private static ModConfig load() {
        ModConfig config = new ModConfig();
        Path configPath = getConfigPath();

        if (Files.exists(configPath)) {
            boolean shouldSave = false;
            try {
                InputStream in = Files.newInputStream(configPath);
                try {
                    Properties props = new Properties();
                    props.load(in);

                    config.playerHighlightEnabled = Boolean.parseBoolean(props.getProperty("playerHighlightEnabled", "true"));
                    config.projectilePredictionEnabled = Boolean.parseBoolean(props.getProperty("projectilePredictionEnabled", "true"));
                    config.trajectoryLineEnabled = Boolean.parseBoolean(props.getProperty("trajectoryLineEnabled", "true"));
                    config.bowPreviewEnabled = Boolean.parseBoolean(props.getProperty("bowPreviewEnabled", "true"));
                    config.bowPreviewTrajectoryEnabled = Boolean.parseBoolean(props.getProperty("bowPreviewTrajectoryEnabled", "true"));
                    config.bowPreviewLandingMarkerEnabled = Boolean.parseBoolean(props.getProperty("bowPreviewLandingMarkerEnabled", "true"));
                    config.bowPreviewSimulateInaccuracy = Boolean.parseBoolean(props.getProperty("bowPreviewSimulateInaccuracy", "true"));

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

                    double arrowGravity = clampGravity(parseDouble(props, "arrowGravity", 0.05));
                    double arrowDrag = clampCalibrationDrag(parseDouble(props, "arrowDrag", 0.99));
                    boolean simulateFluidDrag = Boolean.parseBoolean(props.getProperty("simulateFluidDrag", "true"));
                    double arrowWaterDrag = clampArrowWaterDrag(parseDouble(props, "arrowWaterDrag", 0.6));
                    double fireballGravity = clampGravity(parseDouble(props, "fireballGravity", 0.0));
                    double fireballDrag = clampDrag01(parseDouble(props, "fireballDrag", 0.95));

                    config.arrowGravity = arrowGravity;
                    config.arrowDrag = arrowDrag;
                    config.simulateFluidDrag = simulateFluidDrag;
                    config.arrowWaterDrag = arrowWaterDrag;
                    config.fireballGravity = fireballGravity;
                    config.fireballDrag = fireballDrag;

                    config.autoCalibrationEnabled = Boolean.parseBoolean(props.getProperty("autoCalibrationEnabled", "true"));
                    config.debugMode = Boolean.parseBoolean(props.getProperty("debugMode", "false"));

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
                } finally {
                    in.close();
                }
            } catch (IOException e) {
                PlayerHighlightMod.LOGGER.error("Failed to load config", e);
            }
            if (shouldSave) {
                config.save();
            }
        } else {
            config.save();
        }

        return config;
    }

    public void save() {
        Path configPath = getConfigPath();
        try {
            Files.createDirectories(configPath.getParent());
            OutputStream out = Files.newOutputStream(configPath);
            try {
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
                props.setProperty("arrowGravity", String.valueOf(arrowGravity));
                props.setProperty("arrowDrag", String.valueOf(arrowDrag));
                props.setProperty("simulateFluidDrag", String.valueOf(simulateFluidDrag));
                props.setProperty("arrowWaterDrag", String.valueOf(arrowWaterDrag));
                props.setProperty("fireballGravity", String.valueOf(fireballGravity));
                props.setProperty("fireballDrag", String.valueOf(fireballDrag));
                props.setProperty("autoCalibrationEnabled", String.valueOf(autoCalibrationEnabled));
                props.setProperty("debugMode", String.valueOf(debugMode));
                props.store(out, "Player Highlight Mod Configuration");
            } finally {
                out.close();
            }
        } catch (IOException e) {
            PlayerHighlightMod.LOGGER.error("Failed to save config", e);
        }
    }

    private static double parseDouble(Properties props, String key, double defaultValue) {
        String value = props.getProperty(key);
        if (value == null) return defaultValue;
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double clampRange(double value) {
        if (!Double.isFinite(value)) return 20.0;
        return Math.max(1.0, Math.min(128.0, value));
    }

    private static int clampPreviewTicks(int ticks) {
        return Math.max(1, Math.min(400, ticks));
    }

    private static double clampGravity(double value) {
        if (!Double.isFinite(value)) return 0.05;
        return Math.max(0.0, Math.min(0.2, value));
    }

    private static double clampCalibrationDrag(double value) {
        if (!Double.isFinite(value)) return 0.99;
        return Math.max(0.90, Math.min(1.0, value));
    }

    private static double clampDrag01(double value) {
        if (!Double.isFinite(value)) return 0.99;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clampArrowWaterDrag(double value) {
        if (!Double.isFinite(value)) return 0.6;
        return Math.max(0.05, Math.min(1.0, value));
    }

    // Getters and setters
    public boolean isPlayerHighlightEnabled() { return playerHighlightEnabled; }
    public void setPlayerHighlightEnabled(boolean enabled) { this.playerHighlightEnabled = enabled; save(); }
    public boolean isProjectilePredictionEnabled() { return projectilePredictionEnabled; }
    public void setProjectilePredictionEnabled(boolean enabled) { this.projectilePredictionEnabled = enabled; save(); }
    public boolean isTrajectoryLineEnabled() { return trajectoryLineEnabled; }
    public void setTrajectoryLineEnabled(boolean enabled) { this.trajectoryLineEnabled = enabled; save(); }
    public boolean isBowPreviewEnabled() { return bowPreviewEnabled; }
    public void setBowPreviewEnabled(boolean enabled) { this.bowPreviewEnabled = enabled; save(); }
    public boolean isBowPreviewTrajectoryEnabled() { return bowPreviewTrajectoryEnabled; }
    public void setBowPreviewTrajectoryEnabled(boolean enabled) { this.bowPreviewTrajectoryEnabled = enabled; save(); }
    public boolean isBowPreviewLandingMarkerEnabled() { return bowPreviewLandingMarkerEnabled; }
    public void setBowPreviewLandingMarkerEnabled(boolean enabled) { this.bowPreviewLandingMarkerEnabled = enabled; save(); }
    public boolean isBowPreviewSimulateInaccuracy() { return bowPreviewSimulateInaccuracy; }
    public void setBowPreviewSimulateInaccuracy(boolean enabled) { this.bowPreviewSimulateInaccuracy = enabled; save(); }
    public int getBowPreviewMaxTicks() { return bowPreviewMaxTicks; }
    public void setBowPreviewMaxTicks(int maxTicks) {
        int clamped = clampPreviewTicks(maxTicks);
        if (this.bowPreviewMaxTicks == clamped) return;
        this.bowPreviewMaxTicks = clamped;
        save();
    }
    public double getNearbyWarningRange() { return nearbyWarningRange; }
    public void setNearbyWarningRange(double range) {
        double clamped = clampRange(range);
        clamped = Math.round(clamped);
        if (Math.abs(this.nearbyWarningRange - clamped) < 0.0001) return;
        this.nearbyWarningRange = clamped;
        save();
    }
    public double getArrowGravity() { return arrowGravity; }
    public void setArrowGravity(double v) { this.arrowGravity = clampGravity(v); save(); }
    public double getArrowDrag() { return arrowDrag; }
    public void setArrowDrag(double v) { this.arrowDrag = clampCalibrationDrag(v); save(); }
    public boolean isSimulateFluidDrag() { return simulateFluidDrag; }
    public void setSimulateFluidDrag(boolean enabled) { this.simulateFluidDrag = enabled; save(); }
    public double getArrowWaterDrag() { return arrowWaterDrag; }
    public void setArrowWaterDrag(double v) { this.arrowWaterDrag = clampArrowWaterDrag(v); save(); }
    public double getFireballGravity() { return fireballGravity; }
    public void setFireballGravity(double v) { this.fireballGravity = clampGravity(v); save(); }
    public double getFireballDrag() { return fireballDrag; }
    public void setFireballDrag(double v) { this.fireballDrag = clampDrag01(v); save(); }
    public boolean isAutoCalibrationEnabled() { return autoCalibrationEnabled; }
    public void setAutoCalibrationEnabled(boolean enabled) { this.autoCalibrationEnabled = enabled; save(); }
    public boolean isDebugMode() { return debugMode; }
    public void setDebugMode(boolean enabled) { this.debugMode = enabled; save(); }
}

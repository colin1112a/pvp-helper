package com.example.playerhighlight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

final class CalibrationPersistence {
    private static Path getCalibrationPath() {
        File configDir = new File(net.minecraft.client.Minecraft.getMinecraft().mcDataDir, "config");
        return configDir.toPath().resolve("playerhighlight-calibration.json");
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_SCHEMA = 2;

    private CalibrationPersistence() {}

    static Map<String, PersistedTypeState> load() {
        Path calibrationPath = getCalibrationPath();
        if (!Files.exists(calibrationPath)) {
            return new HashMap<String, PersistedTypeState>();
        }
        try {
            Reader reader = Files.newBufferedReader(calibrationPath);
            try {
                PersistedRoot root = GSON.fromJson(reader, PersistedRoot.class);
                if (root == null || root.types == null) {
                    return new HashMap<String, PersistedTypeState>();
                }
                if (root.schema != CURRENT_SCHEMA) {
                    PlayerHighlightMod.LOGGER.info(
                            "Ignoring calibration state from {} due to schema mismatch (found {}, expected {})",
                            calibrationPath, root.schema, CURRENT_SCHEMA);
                    return new HashMap<String, PersistedTypeState>();
                }
                return new HashMap<String, PersistedTypeState>(root.types);
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            PlayerHighlightMod.LOGGER.warn("Failed to load calibration state from {}", calibrationPath, e);
            return new HashMap<String, PersistedTypeState>();
        } catch (JsonSyntaxException e) {
            PlayerHighlightMod.LOGGER.warn("Failed to load calibration state from {}", calibrationPath, e);
            return new HashMap<String, PersistedTypeState>();
        }
    }

    static void save(Map<String, PersistedTypeState> types) {
        Path calibrationPath = getCalibrationPath();
        try {
            Files.createDirectories(calibrationPath.getParent());
            Path tmp = calibrationPath.resolveSibling(calibrationPath.getFileName().toString() + ".tmp");
            Writer writer = Files.newBufferedWriter(tmp);
            try {
                PersistedRoot root = new PersistedRoot();
                root.schema = CURRENT_SCHEMA;
                root.types = new HashMap<String, PersistedTypeState>(types);
                GSON.toJson(root, writer);
            } finally {
                writer.close();
            }
            Files.move(tmp, calibrationPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            PlayerHighlightMod.LOGGER.warn("Failed to save calibration state to {}", calibrationPath, e);
        }
    }

    static final class PersistedTypeState {
        double gravity;
        double drag;
        int sampleCount;
        double errorWeightedSum;
        double errorWeightSum;
        double avgError;
    }

    private static final class PersistedRoot {
        int schema;
        Map<String, PersistedTypeState> types;
    }
}

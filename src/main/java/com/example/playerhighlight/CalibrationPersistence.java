package com.example.playerhighlight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

final class CalibrationPersistence {
    private static final Path CALIBRATION_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("playerhighlight-calibration.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_SCHEMA = 2;

    private CalibrationPersistence() {
    }

    static Map<String, PersistedTypeState> load() {
        if (!Files.exists(CALIBRATION_PATH)) {
            return new HashMap<>();
        }
        try (Reader reader = Files.newBufferedReader(CALIBRATION_PATH)) {
            PersistedRoot root = GSON.fromJson(reader, PersistedRoot.class);
            if (root == null || root.types == null) {
                return new HashMap<>();
            }
            if (root.schema != CURRENT_SCHEMA) {
                PlayerHighlightClient.LOGGER.info(
                        "Ignoring calibration state from {} due to schema mismatch (found {}, expected {})",
                        CALIBRATION_PATH,
                        root.schema,
                        CURRENT_SCHEMA
                );
                return new HashMap<>();
            }
            return new HashMap<>(root.types);
        } catch (IOException | JsonSyntaxException e) {
            PlayerHighlightClient.LOGGER.warn("Failed to load calibration state from {}", CALIBRATION_PATH, e);
            return new HashMap<>();
        }
    }

    static void save(Map<String, PersistedTypeState> types) {
        PersistedRoot root = new PersistedRoot();
        root.schema = CURRENT_SCHEMA;
        root.types = new HashMap<>(types);

        try {
            Files.createDirectories(CALIBRATION_PATH.getParent());
            Path tmp = CALIBRATION_PATH.resolveSibling(CALIBRATION_PATH.getFileName().toString() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                GSON.toJson(root, writer);
            }
            Files.move(tmp, CALIBRATION_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            PlayerHighlightClient.LOGGER.warn("Failed to save calibration state to {}", CALIBRATION_PATH, e);
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

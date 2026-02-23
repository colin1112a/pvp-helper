package com.example.playerhighlight;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public final class BowStatusCommand {
    private BowStatusCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("bowstatus")
                .then(ClientCommandManager.literal("reset")
                    .then(ClientCommandManager.argument("type", StringArgumentType.word())
                        .executes(ctx -> {
                            String type = StringArgumentType.getString(ctx, "type");
                            resetType(ctx.getSource(), type);
                            return 1;
                        })
                    )
                )
                .executes(ctx -> {
                    FabricClientCommandSource source = ctx.getSource();
                    DynamicProjectileRegistry registry = DynamicProjectileRegistry.getInstance();
                    Map<String, DynamicProjectileRegistry.ProjectileTypeData> types = registry.getRegisteredTypesSnapshot();

                    if (types.isEmpty()) {
                        source.sendFeedback(Text.translatable("playerhighlight.bowstatus.no_types"));
                        return 1;
                    }

                    List<Map.Entry<String, DynamicProjectileRegistry.ProjectileTypeData>> entries = new ArrayList<>(types.entrySet());
                    entries.sort(Comparator.comparing(Map.Entry::getKey));

                    source.sendFeedback(Text.translatable("playerhighlight.bowstatus.registered_types", String.valueOf(entries.size())));
                    for (Map.Entry<String, DynamicProjectileRegistry.ProjectileTypeData> entry : entries) {
                        String typeId = entry.getKey();
                        DynamicProjectileRegistry.ProjectileTypeData data = entry.getValue();

                        Text line = Text.translatable("playerhighlight.bowstatus.type_line",
                                BowEnchantmentDetector.getDisplayName(typeId),
                                String.valueOf(data.getSampleCount()),
                                String.format("%.2f", data.getAvgError()),
                                String.format("%.4f", data.getGravity()),
                                String.format("%.4f", data.getDrag())
                        );
                        source.sendFeedback(line);
                    }

                    return 1;
                })
        ));
    }

    private static void resetType(FabricClientCommandSource source, String type) {
        if (type == null || type.isBlank()) {
            source.sendFeedback(Text.translatable("playerhighlight.bowstatus.usage"));
            return;
        }

        String key = type.trim().toLowerCase(Locale.ROOT);
        List<String> ids = new ArrayList<>();

        switch (key) {
            case "arrow" -> ids.add(BowEnchantmentDetector.TYPE_ARROW);
            case "trident" -> ids.add("TRIDENT");
            case "fireball" -> ids.add("FIREBALL");
            case "all" -> {
                ids.add(BowEnchantmentDetector.TYPE_ARROW);
                ids.add("TRIDENT");
                ids.add("FIREBALL");

                DynamicProjectileRegistry registry = DynamicProjectileRegistry.getInstance();
                for (String typeId : registry.getRegisteredTypesSnapshot().keySet()) {
                    if (!ids.contains(typeId)) {
                        ids.add(typeId);
                    }
                }
            }
            default -> {
                source.sendFeedback(Text.translatable("playerhighlight.bowstatus.unknown_type", type));
                source.sendFeedback(Text.translatable("playerhighlight.bowstatus.usage"));
                return;
            }
        }

        for (String typeId : ids) {
            CalibrationSystem.resetTypeLearning(typeId);
        }

        DynamicProjectileRegistry.getInstance().flushNow();
        source.sendFeedback(Text.translatable("playerhighlight.bowstatus.reset_done", String.join(", ", ids)));
    }
}

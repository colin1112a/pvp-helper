package com.example.playerhighlight;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class LookPvpCommand {
    private LookPvpCommand() {
    }

    private static final int MAX_PRINT_HITS = 10;

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("lookpvp")
                        .executes(LookPvpCommand::execute)
        ));
    }

    private static int execute(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            source.sendFeedback(Text.translatable("playerhighlight.lookpvp.not_in_world"));
            return 1;
        }

        PvpTrackerClient.PvpSession session = PvpTrackerClient.getMostRecentSession();
        if (session == null) {
            source.sendFeedback(Text.translatable("playerhighlight.lookpvp.no_data"));
            return 1;
        }

        String opponentName = session.getOpponentName();
        UUID opponentUuid = session.opponentUuid;
        String shortUuid = shortUuid(opponentUuid);

        long nowMs = System.currentTimeMillis();
        long nowTick = client.world.getTime();
        double secondsAgo = session.lastInteractionMs > 0 ? (nowMs - session.lastInteractionMs) / 1000.0 : Double.NaN;
        long tickDelta = (session.lastInteractionTick > 0 && nowTick >= session.lastInteractionTick)
                ? (nowTick - session.lastInteractionTick)
                : -1L;

        source.sendFeedback(Text.translatable("playerhighlight.lookpvp.opponent",
                opponentName, shortUuid,
                String.format(Locale.ROOT, "%.2f", clampNonNegative(secondsAgo)),
                String.valueOf(tickDelta)));

        PlayerEntity opponentEntity = opponentUuid != null ? findLoadedPlayerByUuid(client, opponentUuid) : null;
        if (opponentEntity != null) {
            double currentDist = opponentEntity.getPos().distanceTo(client.player.getPos());
            source.sendFeedback(Text.translatable("playerhighlight.lookpvp.current_distance",
                    String.format(Locale.ROOT, "%.3f", currentDist)));
        } else {
            source.sendFeedback(Text.translatable("playerhighlight.lookpvp.opponent_not_loaded"));
        }

        List<PvpTrackerClient.IncomingHit> incoming = session.getIncomingHitsNewestFirst();
        printIncomingSummary(source, session, incoming);
        printIncomingDetails(source, incoming, nowMs);

        List<PvpTrackerClient.OutgoingAttack> outgoing = session.getOutgoingAttacksNewestFirst();
        printOutgoingSummary(source, session, outgoing);

        source.sendFeedback(Text.translatable("playerhighlight.lookpvp.note"));
        return 1;
    }

    private static void printIncomingSummary(FabricClientCommandSource source, PvpTrackerClient.PvpSession session,
                                             List<PvpTrackerClient.IncomingHit> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            source.sendFeedback(Text.translatable("playerhighlight.lookpvp.incoming_none"));
            return;
        }

        double minCenter = Double.POSITIVE_INFINITY;
        double maxCenter = 0.0;
        double sumCenter = 0.0;
        double minBox = Double.POSITIVE_INFINITY;
        double maxBox = 0.0;
        double sumBox = 0.0;
        double minReach = Double.POSITIVE_INFINITY;
        double maxReach = 0.0;
        double sumReach = 0.0;
        int count = 0;

        for (PvpTrackerClient.IncomingHit hit : incoming) {
            if (hit == null || hit.distance == null) {
                continue;
            }
            double center = hit.distance.centerDistance;
            double box = hit.distance.boxDistance;
            double reach = hit.distance.eyeToTargetBoxDistance;
            if (!Double.isFinite(center) || !Double.isFinite(box) || !Double.isFinite(reach)) {
                continue;
            }
            count++;
            minCenter = Math.min(minCenter, center);
            maxCenter = Math.max(maxCenter, center);
            sumCenter += center;
            minBox = Math.min(minBox, box);
            maxBox = Math.max(maxBox, box);
            sumBox += box;
            minReach = Math.min(minReach, reach);
            maxReach = Math.max(maxReach, reach);
            sumReach += reach;
        }

        PvpTrackerClient.IncomingHit last = incoming.get(0);
        double lastCenter = last != null && last.distance != null ? last.distance.centerDistance : Double.NaN;
        double lastBox = last != null && last.distance != null ? last.distance.boxDistance : Double.NaN;
        double lastReach = last != null && last.distance != null ? last.distance.eyeToTargetBoxDistance : Double.NaN;
        double avgCenter = count > 0 ? (sumCenter / count) : Double.NaN;
        double avgBox = count > 0 ? (sumBox / count) : Double.NaN;
        double avgReach = count > 0 ? (sumReach / count) : Double.NaN;

        source.sendFeedback(Text.translatable("playerhighlight.lookpvp.incoming_summary",
                String.valueOf(session != null ? session.incomingHitCountTotal : incoming.size()),
                String.valueOf(incoming.size()),
                String.format(Locale.ROOT, "%.3f", lastReach),
                String.format(Locale.ROOT, "%.3f", lastCenter),
                String.format(Locale.ROOT, "%.3f", lastBox)));

        if (count > 0 && minCenter < Double.POSITIVE_INFINITY && minBox < Double.POSITIVE_INFINITY && minReach < Double.POSITIVE_INFINITY) {
            source.sendFeedback(Text.translatable("playerhighlight.lookpvp.incoming_stats",
                    String.format(Locale.ROOT, "%.3f", minReach),
                    String.format(Locale.ROOT, "%.3f", avgReach),
                    String.format(Locale.ROOT, "%.3f", maxReach),
                    String.format(Locale.ROOT, "%.3f", minCenter),
                    String.format(Locale.ROOT, "%.3f", avgCenter),
                    String.format(Locale.ROOT, "%.3f", maxCenter),
                    String.format(Locale.ROOT, "%.3f", minBox),
                    String.format(Locale.ROOT, "%.3f", avgBox),
                    String.format(Locale.ROOT, "%.3f", maxBox)));
        }
    }

    private static void printIncomingDetails(FabricClientCommandSource source, List<PvpTrackerClient.IncomingHit> incoming, long nowMs) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }

        int printed = 0;
        for (PvpTrackerClient.IncomingHit hit : incoming) {
            if (hit == null || hit.distance == null) {
                continue;
            }
            double secondsAgo = (nowMs - hit.timeMs) / 1000.0;
            source.sendFeedback(Text.translatable("playerhighlight.lookpvp.incoming_detail",
                    String.format(Locale.ROOT, "%.2f", clampNonNegative(secondsAgo)),
                    String.format(Locale.ROOT, "%.2f", hit.damageAmount),
                    String.format(Locale.ROOT, "%.3f", hit.distance.eyeToTargetBoxDistance),
                    String.format(Locale.ROOT, "%.3f", hit.distance.centerDistance),
                    String.format(Locale.ROOT, "%.3f", hit.distance.horizontalDistance),
                    String.format(Locale.ROOT, "%.3f", hit.distance.eyeDistance),
                    String.format(Locale.ROOT, "%.3f", hit.distance.boxDistance),
                    safeString(hit.damageName),
                    safeString(hit.directSourceTypeId),
                    safeString(hit.opponentMainHandItemId)));
            printed++;
            if (printed >= MAX_PRINT_HITS) {
                break;
            }
        }
    }

    private static void printOutgoingSummary(FabricClientCommandSource source, PvpTrackerClient.PvpSession session,
                                             List<PvpTrackerClient.OutgoingAttack> outgoing) {
        if (outgoing == null || outgoing.isEmpty()) {
            source.sendFeedback(Text.translatable("playerhighlight.lookpvp.outgoing_none"));
            return;
        }

        PvpTrackerClient.OutgoingAttack last = outgoing.get(0);
        double lastCenter = last != null && last.distance != null ? last.distance.centerDistance : Double.NaN;
        source.sendFeedback(Text.translatable("playerhighlight.lookpvp.outgoing_summary",
                String.valueOf(session != null ? session.outgoingAttackCountTotal : outgoing.size()),
                String.valueOf(outgoing.size()),
                String.format(Locale.ROOT, "%.3f", lastCenter),
                last != null ? safeString(last.myMainHandItemId) : "unknown"));
    }

    private static String shortUuid(UUID uuid) {
        if (uuid == null) {
            return "unknown";
        }
        String raw = uuid.toString();
        return raw.length() >= 8 ? raw.substring(0, 8) : raw;
    }

    private static double clampNonNegative(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, value);
    }

    private static String safeString(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static PlayerEntity findLoadedPlayerByUuid(MinecraftClient client, UUID uuid) {
        if (client == null || client.world == null || uuid == null) {
            return null;
        }
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player != null && uuid.equals(player.getUuid())) {
                return player;
            }
        }
        return null;
    }

}

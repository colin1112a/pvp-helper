package com.example.playerhighlight;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StatCollector;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class LookPvpCommand extends CommandBase {

    private static final int MAX_PRINT_HITS = 10;

    @Override
    public String getCommandName() {
        return "lookpvp";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/lookpvp";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            sender.addChatMessage(new ChatComponentText(StatCollector.translateToLocal("playerhighlight.lookpvp.not_in_world")));
            return;
        }

        PvpTrackerClient.PvpSession session = PvpTrackerClient.getMostRecentSession();
        if (session == null) {
            sender.addChatMessage(new ChatComponentText(StatCollector.translateToLocal("playerhighlight.lookpvp.no_data")));
            return;
        }

        String opponentName = session.getOpponentName();
        UUID opponentUuid = session.opponentUuid;
        String shortUuid = shortUuid(opponentUuid);

        long nowMs = System.currentTimeMillis();
        long nowTick = mc.theWorld.getTotalWorldTime();
        double secondsAgo = session.lastInteractionMs > 0 ? (nowMs - session.lastInteractionMs) / 1000.0 : Double.NaN;
        long tickDelta = (session.lastInteractionTick > 0 && nowTick >= session.lastInteractionTick)
                ? (nowTick - session.lastInteractionTick) : -1L;

        sender.addChatMessage(new ChatComponentText(StatCollector.translateToLocalFormatted(
                "playerhighlight.lookpvp.opponent",
                opponentName, shortUuid,
                String.format(Locale.ROOT, "%.2f", clampNonNegative(secondsAgo)),
                String.valueOf(tickDelta)
        )));

        EntityPlayer opponentEntity = opponentUuid != null ? findLoadedPlayerByUuid(mc, opponentUuid) : null;
        if (opponentEntity != null) {
            double currentDist = opponentEntity.getPositionVector().distanceTo(mc.thePlayer.getPositionVector());
            sender.addChatMessage(new ChatComponentText(StatCollector.translateToLocalFormatted(
                    "playerhighlight.lookpvp.current_distance",
                    String.format(Locale.ROOT, "%.3f", currentDist))));
        } else {
            sender.addChatMessage(new ChatComponentText(StatCollector.translateToLocal("playerhighlight.lookpvp.opponent_not_loaded")));
        }

        List<PvpTrackerClient.IncomingHit> incoming = session.getIncomingHitsNewestFirst();
        printIncomingSummary(sender, session, incoming);
        printIncomingDetails(sender, incoming, nowMs);

        List<PvpTrackerClient.OutgoingAttack> outgoing = session.getOutgoingAttacksNewestFirst();
        printOutgoingSummary(sender, session, outgoing);

        sender.addChatMessage(new ChatComponentText(StatCollector.translateToLocal("playerhighlight.lookpvp.note")));
    }

    private void printIncomingSummary(ICommandSender sender, PvpTrackerClient.PvpSession session,
                                      List<PvpTrackerClient.IncomingHit> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            sender.addChatMessage(new ChatComponentText(StatCollector.translateToLocal("playerhighlight.lookpvp.incoming_none")));
            return;
        }

        double minCenter = Double.POSITIVE_INFINITY, maxCenter = 0.0, sumCenter = 0.0;
        double minBox = Double.POSITIVE_INFINITY, maxBox = 0.0, sumBox = 0.0;
        double minReach = Double.POSITIVE_INFINITY, maxReach = 0.0, sumReach = 0.0;
        int count = 0;

        for (PvpTrackerClient.IncomingHit hit : incoming) {
            if (hit == null || hit.distance == null) continue;
            double center = hit.distance.centerDistance;
            double box = hit.distance.boxDistance;
            double reach = hit.distance.eyeToTargetBoxDistance;
            if (!Double.isFinite(center) || !Double.isFinite(box) || !Double.isFinite(reach)) continue;
            count++;
            minCenter = Math.min(minCenter, center); maxCenter = Math.max(maxCenter, center); sumCenter += center;
            minBox = Math.min(minBox, box); maxBox = Math.max(maxBox, box); sumBox += box;
            minReach = Math.min(minReach, reach); maxReach = Math.max(maxReach, reach); sumReach += reach;
        }

        PvpTrackerClient.IncomingHit last = incoming.get(0);
        double lastCenter = last != null && last.distance != null ? last.distance.centerDistance : Double.NaN;
        double lastBox = last != null && last.distance != null ? last.distance.boxDistance : Double.NaN;
        double lastReach = last != null && last.distance != null ? last.distance.eyeToTargetBoxDistance : Double.NaN;
        double avgCenter = count > 0 ? (sumCenter / count) : Double.NaN;
        double avgBox = count > 0 ? (sumBox / count) : Double.NaN;
        double avgReach = count > 0 ? (sumReach / count) : Double.NaN;

        sender.addChatMessage(new ChatComponentText(StatCollector.translateToLocalFormatted(
                "playerhighlight.lookpvp.incoming_summary",
                String.valueOf(session != null ? session.incomingHitCountTotal : incoming.size()),
                String.valueOf(incoming.size()),
                String.format(Locale.ROOT, "%.3f", lastReach),
                String.format(Locale.ROOT, "%.3f", lastCenter),
                String.format(Locale.ROOT, "%.3f", lastBox))));

        if (count > 0 && minCenter < Double.POSITIVE_INFINITY) {
            sender.addChatMessage(new ChatComponentText(StatCollector.translateToLocalFormatted(
                    "playerhighlight.lookpvp.incoming_stats",
                    String.format(Locale.ROOT, "%.3f", minReach),
                    String.format(Locale.ROOT, "%.3f", avgReach),
                    String.format(Locale.ROOT, "%.3f", maxReach),
                    String.format(Locale.ROOT, "%.3f", minCenter),
                    String.format(Locale.ROOT, "%.3f", avgCenter),
                    String.format(Locale.ROOT, "%.3f", maxCenter),
                    String.format(Locale.ROOT, "%.3f", minBox),
                    String.format(Locale.ROOT, "%.3f", avgBox),
                    String.format(Locale.ROOT, "%.3f", maxBox))));
        }
    }

    private void printIncomingDetails(ICommandSender sender, List<PvpTrackerClient.IncomingHit> incoming, long nowMs) {
        if (incoming == null || incoming.isEmpty()) return;
        int printed = 0;
        for (PvpTrackerClient.IncomingHit hit : incoming) {
            if (hit == null || hit.distance == null) continue;
            double secondsAgo = (nowMs - hit.timeMs) / 1000.0;
            sender.addChatMessage(new ChatComponentText(StatCollector.translateToLocalFormatted(
                    "playerhighlight.lookpvp.incoming_detail",
                    String.format(Locale.ROOT, "%.2f", clampNonNegative(secondsAgo)),
                    String.format(Locale.ROOT, "%.2f", hit.damageAmount),
                    String.format(Locale.ROOT, "%.3f", hit.distance.eyeToTargetBoxDistance),
                    String.format(Locale.ROOT, "%.3f", hit.distance.centerDistance),
                    String.format(Locale.ROOT, "%.3f", hit.distance.horizontalDistance),
                    String.format(Locale.ROOT, "%.3f", hit.distance.eyeDistance),
                    String.format(Locale.ROOT, "%.3f", hit.distance.boxDistance),
                    safeString(hit.damageName),
                    safeString(hit.directSourceTypeId),
                    safeString(hit.opponentMainHandItemId))));
            printed++;
            if (printed >= MAX_PRINT_HITS) break;
        }
    }

    private void printOutgoingSummary(ICommandSender sender, PvpTrackerClient.PvpSession session,
                                      List<PvpTrackerClient.OutgoingAttack> outgoing) {
        if (outgoing == null || outgoing.isEmpty()) {
            sender.addChatMessage(new ChatComponentText(StatCollector.translateToLocal("playerhighlight.lookpvp.outgoing_none")));
            return;
        }
        PvpTrackerClient.OutgoingAttack last = outgoing.get(0);
        double lastCenter = last != null && last.distance != null ? last.distance.centerDistance : Double.NaN;
        sender.addChatMessage(new ChatComponentText(StatCollector.translateToLocalFormatted(
                "playerhighlight.lookpvp.outgoing_summary",
                String.valueOf(session != null ? session.outgoingAttackCountTotal : outgoing.size()),
                String.valueOf(outgoing.size()),
                String.format(Locale.ROOT, "%.3f", lastCenter),
                last != null ? safeString(last.myMainHandItemId) : "unknown")));
    }

    private static String shortUuid(UUID uuid) {
        if (uuid == null) return "unknown";
        String raw = uuid.toString();
        return raw.length() >= 8 ? raw.substring(0, 8) : raw;
    }

    private static double clampNonNegative(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, value);
    }

    private static String safeString(String value) {
        return value == null || value.trim().isEmpty() ? "unknown" : value;
    }

    private static EntityPlayer findLoadedPlayerByUuid(Minecraft mc, UUID uuid) {
        if (mc == null || mc.theWorld == null || uuid == null) return null;
        for (Object obj : mc.theWorld.playerEntities) {
            EntityPlayer player = (EntityPlayer) obj;
            if (player != null && uuid.equals(player.getUniqueID())) {
                return player;
            }
        }
        return null;
    }
}

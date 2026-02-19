package com.example.playerhighlight;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BowStatusCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "bowstatus";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/bowstatus [reset <arrow|fireball|all>]";
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
        if (args.length >= 2 && "reset".equalsIgnoreCase(args[0])) {
            resetType(sender, args[1]);
            return;
        }

        DynamicProjectileRegistry registry = DynamicProjectileRegistry.getInstance();
        Map<String, DynamicProjectileRegistry.ProjectileTypeData> types = registry.getRegisteredTypesSnapshot();

        if (types.isEmpty()) {
            sender.addChatMessage(new ChatComponentText("[BowStatus] No projectile types registered yet."));
            return;
        }

        List<Map.Entry<String, DynamicProjectileRegistry.ProjectileTypeData>> entries =
                new ArrayList<Map.Entry<String, DynamicProjectileRegistry.ProjectileTypeData>>(types.entrySet());
        java.util.Collections.sort(entries, new Comparator<Map.Entry<String, DynamicProjectileRegistry.ProjectileTypeData>>() {
            @Override
            public int compare(Map.Entry<String, DynamicProjectileRegistry.ProjectileTypeData> a,
                               Map.Entry<String, DynamicProjectileRegistry.ProjectileTypeData> b) {
                return a.getKey().compareTo(b.getKey());
            }
        });

        sender.addChatMessage(new ChatComponentText("[BowStatus] Registered types: " + entries.size()));
        for (Map.Entry<String, DynamicProjectileRegistry.ProjectileTypeData> entry : entries) {
            String typeId = entry.getKey();
            DynamicProjectileRegistry.ProjectileTypeData data = entry.getValue();

            String displayName = BowEnchantmentDetector.getDisplayName(typeId);
            String line = String.format(
                    "%s | samples=%d avgRMSE=%.2f | G=%.4f D=%.4f",
                    displayName,
                    data.getSampleCount(),
                    data.getAvgError(),
                    data.getGravity(),
                    data.getDrag()
            );
            sender.addChatMessage(new ChatComponentText(line));
        }
    }

    private void resetType(ICommandSender sender, String type) {
        if (type == null || type.trim().isEmpty()) {
            sender.addChatMessage(new ChatComponentText("[BowStatus] Usage: /bowstatus reset <arrow|fireball|all>"));
            return;
        }

        String key = type.trim().toLowerCase(Locale.ROOT);
        List<String> ids = new ArrayList<String>();

        if ("arrow".equals(key)) {
            ids.add(BowEnchantmentDetector.TYPE_ARROW);
        } else if ("fireball".equals(key)) {
            ids.add("FIREBALL");
        } else if ("all".equals(key)) {
            ids.add(BowEnchantmentDetector.TYPE_ARROW);
            ids.add("FIREBALL");
            DynamicProjectileRegistry registry = DynamicProjectileRegistry.getInstance();
            for (String typeId : registry.getRegisteredTypesSnapshot().keySet()) {
                if (!ids.contains(typeId)) {
                    ids.add(typeId);
                }
            }
        } else {
            sender.addChatMessage(new ChatComponentText("[BowStatus] Unknown type: " + type));
            sender.addChatMessage(new ChatComponentText("[BowStatus] Usage: /bowstatus reset <arrow|fireball|all>"));
            return;
        }

        for (String typeId : ids) {
            CalibrationSystem.resetTypeLearning(typeId);
        }

        DynamicProjectileRegistry.getInstance().flushNow();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(ids.get(i));
        }
        sender.addChatMessage(new ChatComponentText("[BowStatus] Reset learning for: " + sb.toString()));
    }
}

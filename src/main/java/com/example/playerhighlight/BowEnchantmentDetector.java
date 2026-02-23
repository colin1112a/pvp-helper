package com.example.playerhighlight;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.SpectralArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

/**
 * 弓/箭类型检测器
 *
 * 原版下，弓的力量等附魔不影响箭的飞行物理（drag/gravity），因此不再按附魔分桶学习。
 */
public final class BowEnchantmentDetector {
    private BowEnchantmentDetector() {
    }

    /**
     * 箭（弓/弩）统一类型：用于 gravity/drag 学习与预瞄预测。
     */
    public static final String TYPE_ARROW = "ARROW";

    /**
     * 从弓/弩物品本身生成类型ID（用于“未射出前”的预瞄预测）。
     */
    public static String detectBowTypeId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "UNKNOWN";
        }
        if (stack.isOf(Items.BOW) || stack.isOf(Items.CROSSBOW)) {
            return TYPE_ARROW;
        }
        return "UNKNOWN";
    }

    /**
     * 检测箭实体对应的类型ID（不按附魔区分）。
     */
    public static String detectBowTypeId(Entity projectile) {
        if (projectile instanceof ArrowEntity || projectile instanceof SpectralArrowEntity) {
            return TYPE_ARROW;
        }
        return "UNKNOWN";
    }

    /**
     * 获取类型的显示名称（可翻译）
     */
    public static Text getDisplayName(String typeId) {
        if (typeId == null || typeId.isEmpty()) {
            return Text.translatable("playerhighlight.type.unknown");
        }
        return switch (typeId) {
            case TYPE_ARROW -> Text.translatable("playerhighlight.type.arrow");
            case "TRIDENT" -> Text.translatable("playerhighlight.type.trident");
            case "FIREBALL" -> Text.translatable("playerhighlight.type.fireball");
            default -> Text.literal(typeId.replace("_", " "));
        };
    }
}

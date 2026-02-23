package com.example.playerhighlight;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/**
 * 弓/箭类型检测器
 *
 * 原版下，弓的力量等附魔不影响箭的飞行物理（drag/gravity），因此不再按附魔分桶学习。
 */
public final class BowEnchantmentDetector {
    private BowEnchantmentDetector() {
    }

    /**
     * 箭（弓）统一类型：用于 gravity/drag 学习与预瞄预测。
     */
    public static final String TYPE_ARROW = "ARROW";

    /**
     * 从弓物品本身生成类型ID（用于"未射出前"的预瞄预测）。
     */
    public static String detectBowTypeId(ItemStack stack) {
        if (stack == null) {
            return "UNKNOWN";
        }
        if (stack.getItem() == Items.bow) {
            return TYPE_ARROW;
        }
        return "UNKNOWN";
    }

    /**
     * 检测箭实体对应的类型ID（不按附魔区分）。
     */
    public static String detectBowTypeId(Entity projectile) {
        if (projectile instanceof EntityArrow) {
            return TYPE_ARROW;
        }
        return "UNKNOWN";
    }

    /**
     * 获取类型的显示名称（可翻译）
     */
    public static String getDisplayName(String typeId) {
        if (typeId == null || typeId.isEmpty()) {
            return StatCollector.translateToLocal("playerhighlight.type.unknown");
        }
        if (TYPE_ARROW.equals(typeId)) {
            return StatCollector.translateToLocal("playerhighlight.type.arrow");
        } else if ("FIREBALL".equals(typeId)) {
            return StatCollector.translateToLocal("playerhighlight.type.fireball");
        } else {
            return typeId.replace("_", " ");
        }
    }
}

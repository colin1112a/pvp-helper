package com.example.playerhighlight;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;

public class RenderHelper {

    public static int getPlayerOutlineColor(EntityPlayer player) {
        ItemStack helmet = player.getCurrentArmor(3); // 3 = helmet slot

        if (helmet != null && helmet.getItem() == Items.leather_helmet) {
            if (helmet.getItem() instanceof ItemArmor) {
                int color = ((ItemArmor) helmet.getItem()).getColor(helmet);
                // ItemArmor.getColor returns -1 if not dyed
                if (color != -1) {
                    return color;
                }
            }
        }

        return 0xFFFFFF;
    }
}

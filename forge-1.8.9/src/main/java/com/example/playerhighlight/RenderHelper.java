package com.example.playerhighlight;

import net.minecraft.client.Minecraft;
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

    public static float[] colorToFloatArray(int color) {
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        return new float[]{r, g, b, 1.0F};
    }

    public static boolean shouldRenderOutline(EntityPlayer player) {
        Minecraft mc = Minecraft.getMinecraft();

        if (player == mc.thePlayer && mc.gameSettings.thirdPersonView == 0) {
            return false;
        }

        return PlayerHighlightMod.isHighlightEnabled();
    }
}

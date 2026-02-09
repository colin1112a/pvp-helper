package com.example.playerhighlight;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeableArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class RenderHelper {

	/**
	 * Get the outline color for a player based on their leather helmet
	 * @param player The player to check
	 * @return RGB color as int (0xRRGGBB)
	 */
	public static int getPlayerOutlineColor(PlayerEntity player) {
		// Get helmet from player's armor
		ItemStack helmet = player.getInventory().getArmorStack(3); // 3 = helmet slot

		// Check if wearing leather helmet with custom color
		if (!helmet.isEmpty() && helmet.getItem() == Items.LEATHER_HELMET) {
			if (helmet.getItem() instanceof DyeableArmorItem dyeableItem) {
				// Get the color from the helmet, default to brown leather color if not dyed
				int color = dyeableItem.getColor(helmet);
				return color;
			}
		}

		// Default to white if no leather helmet
		return 0xFFFFFF;
	}

	/**
	 * Convert RGB int color to float array [r, g, b, a]
	 */
	public static float[] colorToFloatArray(int color) {
		float r = ((color >> 16) & 0xFF) / 255.0F;
		float g = ((color >> 8) & 0xFF) / 255.0F;
		float b = (color & 0xFF) / 255.0F;
		return new float[]{r, g, b, 1.0F};
	}

	/**
	 * Check if we should render outline for a player
	 */
	public static boolean shouldRenderOutline(PlayerEntity player) {
		MinecraftClient client = MinecraftClient.getInstance();

		// Don't render outline for the local player in first person
		if (player == client.player && client.options.getPerspective().isFirstPerson()) {
			return false;
		}

		// Only render if highlight is enabled
		return PlayerHighlightClient.isHighlightEnabled();
	}
}

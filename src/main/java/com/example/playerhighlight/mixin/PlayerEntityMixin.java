package com.example.playerhighlight.mixin;

import com.example.playerhighlight.PlayerHighlightClient;
import com.example.playerhighlight.RenderHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class PlayerEntityMixin {

	/**
	 * Make players glow when highlight is enabled
	 */
	@Inject(method = "isGlowing()Z", at = @At("HEAD"), cancellable = true)
	private void onIsGlowing(CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (PlayerHighlightClient.isHighlightEnabled() && self instanceof PlayerEntity) {
			cir.setReturnValue(true);
		}
	}

	/**
	 * Set custom outline color based on leather helmet dye color
	 */
	@Inject(method = "getTeamColorValue()I", at = @At("HEAD"), cancellable = true)
	private void onGetTeamColorValue(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (PlayerHighlightClient.isHighlightEnabled() && self instanceof PlayerEntity player) {
			int color = RenderHelper.getPlayerOutlineColor(player);
			cir.setReturnValue(color);
		}
	}
}

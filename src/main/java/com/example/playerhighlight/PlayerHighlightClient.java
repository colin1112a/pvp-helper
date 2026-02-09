package com.example.playerhighlight;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

	public class PlayerHighlightClient implements ClientModInitializer {
		public static final Logger LOGGER = LoggerFactory.getLogger("playerhighlight");
		private static volatile boolean highlightEnabled = false;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Player Highlight mod initializing...");

		// 加载配置
		ModConfig config = ModConfig.getInstance();

		// Best-effort flush on exit to persist learned params
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				DynamicProjectileRegistry.getInstance().flushNow();
			} catch (Throwable t) {
				LOGGER.warn("Failed to flush calibration state on shutdown", t);
			}
		}, "playerhighlight-calibration-flush"));

		// 设置校准系统配置
		CalibrationSystem.setAutoCalibrationEnabled(config.isAutoCalibrationEnabled());
		CalibrationSystem.setDebugMode(config.isDebugMode());
		LOGGER.info("Calibration system initialized: auto={}, debug={}",
			config.isAutoCalibrationEnabled(), config.isDebugMode());

		// Register tick event to check if TAB key is held down
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.getWindow() != null) {
				// Check if TAB key is currently pressed
				boolean tabPressed = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_TAB);
				highlightEnabled = tabPressed;
			}

			// Flush learned parameters to disk (debounced)
			DynamicProjectileRegistry.getInstance().flushIfDue();
		});

			LOGGER.info("Player Highlight mod initialized!");

			// Register client commands
			BowStatusCommand.register();
			LookPvpCommand.register();

			// Initialize projectile tracking system
			ProjectileTrackerClient.initialize();
			LandingPointRenderer.initialize();
			BowPreviewClient.initialize();
			BowPreviewRenderer.initialize();
		}

	/**
	 * 检查玩家透视是否启用（配置开关 && TAB键按下）
	 */
	public static boolean isHighlightEnabled() {
		return highlightEnabled && ModConfig.getInstance().isPlayerHighlightEnabled();
	}

	/**
	 * 检查弹道预测是否启用
	 */
	public static boolean isProjectilePredictionEnabled() {
		return ModConfig.getInstance().isProjectilePredictionEnabled();
	}

	/**
	 * 检查弹道轨迹线是否启用
	 */
	public static boolean isTrajectoryLineEnabled() {
		return ModConfig.getInstance().isTrajectoryLineEnabled();
	}
}

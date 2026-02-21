package com.example.playerhighlight;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "playerhighlight", version = "0.1.0", name = "Player Highlight", clientSideOnly = true)
public class PlayerHighlightMod {

    public static final Logger LOGGER = LogManager.getLogger("playerhighlight");

    private static volatile boolean highlightEnabled = false;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Player Highlight mod pre-initializing...");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("Player Highlight mod initializing...");

        // 加载配置
        ModConfig config = ModConfig.getInstance();

        // 退出时保存校准数据
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DynamicProjectileRegistry.getInstance().flushNow();
                } catch (Throwable t) {
                    LOGGER.warn("Failed to flush calibration state on shutdown", t);
                }
            }
        }, "playerhighlight-calibration-flush"));

        // 设置校准系统配置
        CalibrationSystem.setAutoCalibrationEnabled(config.isAutoCalibrationEnabled());
        CalibrationSystem.setDebugMode(config.isDebugMode());
        LOGGER.info("Calibration system initialized: auto={}, debug={}",
                config.isAutoCalibrationEnabled(), config.isDebugMode());

        // 注册事件处理器
        MinecraftForge.EVENT_BUS.register(new ForgeEventHandler());

        // 注册客户端命令
        ClientCommandHandler.instance.registerCommand(new BowStatusCommand());
        ClientCommandHandler.instance.registerCommand(new LookPvpCommand());

        LOGGER.info("Player Highlight mod initialized!");
    }

    public static boolean isHighlightEnabled() {
        return highlightEnabled && ModConfig.getInstance().isPlayerHighlightEnabled();
    }

    public static void setHighlightEnabled(boolean enabled) {
        highlightEnabled = enabled;
    }

    public static boolean isProjectilePredictionEnabled() {
        return ModConfig.getInstance().isProjectilePredictionEnabled();
    }

    public static boolean isTrajectoryLineEnabled() {
        return ModConfig.getInstance().isTrajectoryLineEnabled();
    }
}

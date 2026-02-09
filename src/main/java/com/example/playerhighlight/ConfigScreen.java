package com.example.playerhighlight;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

 /**
  * Mod配置屏幕
  *
  * 提供玩家透视、弹道预测与警告范围等设置界面
  */
public class ConfigScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_SPACING = 24;
    private static final double WARNING_RANGE_MIN = 1.0;
    private static final double WARNING_RANGE_MAX = 128.0;

    private final Screen parent;
    private final ModConfig config;

    private ButtonWidget playerHighlightButton;
    private ButtonWidget projectilePredictionButton;
    private ButtonWidget trajectoryLineButton;
    private ButtonWidget bowPreviewButton;
    private ButtonWidget bowPreviewTrajectoryButton;
    private ButtonWidget bowPreviewLandingMarkerButton;
    private ButtonWidget bowPreviewInaccuracyButton;
    private ButtonWidget simulateFluidDragButton;
    private SliderWidget nearbyWarningRangeSlider;

    public ConfigScreen(Screen parent) {
        super(Text.literal("Player Highlight Settings"));
        this.parent = parent;
        this.config = ModConfig.getInstance();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 4;
        int x = centerX - (BUTTON_WIDTH / 2);
        int y = startY;

        // 玩家透视开关按钮
        playerHighlightButton = ButtonWidget.builder(
                        getPlayerHighlightText(),
                        button -> {
                            config.setPlayerHighlightEnabled(!config.isPlayerHighlightEnabled());
                            button.setMessage(getPlayerHighlightText());
                        })
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addDrawableChild(playerHighlightButton);

        // 弹道预测开关按钮
        y += ROW_SPACING;
        projectilePredictionButton = ButtonWidget.builder(
                        getProjectilePredictionText(),
                        button -> {
                            config.setProjectilePredictionEnabled(!config.isProjectilePredictionEnabled());
                            button.setMessage(getProjectilePredictionText());
                        })
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addDrawableChild(projectilePredictionButton);

        // 弹道轨迹线开关按钮
        y += ROW_SPACING;
        trajectoryLineButton = ButtonWidget.builder(
                        getTrajectoryLineText(),
                        button -> {
                            config.setTrajectoryLineEnabled(!config.isTrajectoryLineEnabled());
                            button.setMessage(getTrajectoryLineText());
                        })
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addDrawableChild(trajectoryLineButton);

        // 弓箭预瞄预测开关按钮
        y += ROW_SPACING;
        bowPreviewButton = ButtonWidget.builder(
                        getBowPreviewText(),
                        button -> {
                            config.setBowPreviewEnabled(!config.isBowPreviewEnabled());
                            button.setMessage(getBowPreviewText());
                        })
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addDrawableChild(bowPreviewButton);

        // 弓箭预瞄轨迹线开关按钮
        y += ROW_SPACING;
        bowPreviewTrajectoryButton = ButtonWidget.builder(
                        getBowPreviewTrajectoryText(),
                        button -> {
                            config.setBowPreviewTrajectoryEnabled(!config.isBowPreviewTrajectoryEnabled());
                            button.setMessage(getBowPreviewTrajectoryText());
                        })
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addDrawableChild(bowPreviewTrajectoryButton);

        // 弓箭预瞄落点标记开关按钮
        y += ROW_SPACING;
        bowPreviewLandingMarkerButton = ButtonWidget.builder(
                        getBowPreviewLandingMarkerText(),
                        button -> {
                            config.setBowPreviewLandingMarkerEnabled(!config.isBowPreviewLandingMarkerEnabled());
                            button.setMessage(getBowPreviewLandingMarkerText());
                        })
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addDrawableChild(bowPreviewLandingMarkerButton);

        // 弓箭预瞄：模拟发射随机偏移（divergence）
        y += ROW_SPACING;
        bowPreviewInaccuracyButton = ButtonWidget.builder(
                        getBowPreviewInaccuracyText(),
                        button -> {
                            config.setBowPreviewSimulateInaccuracy(!config.isBowPreviewSimulateInaccuracy());
                            button.setMessage(getBowPreviewInaccuracyText());
                        })
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addDrawableChild(bowPreviewInaccuracyButton);

        // 弹道预测：模拟流体减速（箭进水/熔岩）
        y += ROW_SPACING;
        simulateFluidDragButton = ButtonWidget.builder(
                        getSimulateFluidDragText(),
                        button -> {
                            config.setSimulateFluidDrag(!config.isSimulateFluidDrag());
                            button.setMessage(getSimulateFluidDragText());
                        })
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addDrawableChild(simulateFluidDragButton);

        // 警告范围滑条（影响落点标记颜色与 [NEARBY WARNING]）
        y += ROW_SPACING;
        nearbyWarningRangeSlider = new NearbyWarningRangeSlider(x, y, BUTTON_WIDTH, BUTTON_HEIGHT);
        this.addDrawableChild(nearbyWarningRangeSlider);

        // 返回按钮
        y += ROW_SPACING + 10;
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Done"),
                        button -> this.close())
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private Text getPlayerHighlightText() {
        boolean enabled = config.isPlayerHighlightEnabled();
        return Text.literal("Player Highlight: " + (enabled ? "§aON" : "§cOFF"));
    }

    private Text getProjectilePredictionText() {
        boolean enabled = config.isProjectilePredictionEnabled();
        return Text.literal("Projectile Prediction: " + (enabled ? "§aON" : "§cOFF"));
    }

    private Text getTrajectoryLineText() {
        boolean enabled = config.isTrajectoryLineEnabled();
        return Text.literal("Trajectory Line: " + (enabled ? "§aON" : "§cOFF"));
    }

    private Text getBowPreviewText() {
        boolean enabled = config.isBowPreviewEnabled();
        return Text.literal("Bow Preview: " + (enabled ? "§aON" : "§cOFF"));
    }

    private Text getBowPreviewTrajectoryText() {
        boolean enabled = config.isBowPreviewTrajectoryEnabled();
        return Text.literal("Bow Preview Line: " + (enabled ? "§aON" : "§cOFF"));
    }

    private Text getBowPreviewLandingMarkerText() {
        boolean enabled = config.isBowPreviewLandingMarkerEnabled();
        return Text.literal("Bow Preview Marker: " + (enabled ? "§aON" : "§cOFF"));
    }

    private Text getBowPreviewInaccuracyText() {
        boolean enabled = config.isBowPreviewSimulateInaccuracy();
        return Text.literal("Bow Preview Inaccuracy: " + (enabled ? "§aON" : "§cOFF"));
    }

    private Text getSimulateFluidDragText() {
        boolean enabled = config.isSimulateFluidDrag();
        return Text.literal("Fluid Drag Simulation: " + (enabled ? "§aON" : "§cOFF"));
    }

    private class NearbyWarningRangeSlider extends SliderWidget {
        NearbyWarningRangeSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Text.empty(), rangeToSliderValue(config.getNearbyWarningRange()));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.literal("Warning Range: " + (int) getRange()));
        }

        @Override
        protected void applyValue() {
            config.setNearbyWarningRange(getRange());
        }

        private double getRange() {
            double raw = WARNING_RANGE_MIN + this.value * (WARNING_RANGE_MAX - WARNING_RANGE_MIN);
            return Math.round(raw);
        }
    }

    private static double rangeToSliderValue(double range) {
        double clamped = Math.max(WARNING_RANGE_MIN, Math.min(WARNING_RANGE_MAX, range));
        return (clamped - WARNING_RANGE_MIN) / (WARNING_RANGE_MAX - WARNING_RANGE_MIN);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        int startY = this.height / 4;
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Hold TAB to highlight. Configure options below."),
                this.width / 2, startY - 30, 0xAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}

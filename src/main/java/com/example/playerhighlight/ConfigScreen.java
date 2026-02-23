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
        super(Text.translatable("playerhighlight.config.title"));
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
                        Text.translatable("playerhighlight.config.done"),
                        button -> this.close())
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private Text getToggleText(String key, boolean enabled) {
        return Text.translatable(key, Text.translatable(enabled ? "playerhighlight.config.on" : "playerhighlight.config.off"));
    }

    private Text getPlayerHighlightText() {
        return getToggleText("playerhighlight.config.player_highlight", config.isPlayerHighlightEnabled());
    }

    private Text getProjectilePredictionText() {
        return getToggleText("playerhighlight.config.projectile_prediction", config.isProjectilePredictionEnabled());
    }

    private Text getTrajectoryLineText() {
        return getToggleText("playerhighlight.config.trajectory_line", config.isTrajectoryLineEnabled());
    }

    private Text getBowPreviewText() {
        return getToggleText("playerhighlight.config.bow_preview", config.isBowPreviewEnabled());
    }

    private Text getBowPreviewTrajectoryText() {
        return getToggleText("playerhighlight.config.bow_preview_line", config.isBowPreviewTrajectoryEnabled());
    }

    private Text getBowPreviewLandingMarkerText() {
        return getToggleText("playerhighlight.config.bow_preview_marker", config.isBowPreviewLandingMarkerEnabled());
    }

    private Text getBowPreviewInaccuracyText() {
        return getToggleText("playerhighlight.config.bow_preview_inaccuracy", config.isBowPreviewSimulateInaccuracy());
    }

    private Text getSimulateFluidDragText() {
        return getToggleText("playerhighlight.config.fluid_drag", config.isSimulateFluidDrag());
    }

    private class NearbyWarningRangeSlider extends SliderWidget {
        NearbyWarningRangeSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Text.empty(), rangeToSliderValue(config.getNearbyWarningRange()));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.translatable("playerhighlight.config.warning_range", String.valueOf((int) getRange())));
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
                Text.translatable("playerhighlight.config.hint"),
                this.width / 2, startY - 30, 0xAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}

package com.jelly.farmhelper.fabric.ui;

import com.jelly.farmhelper.fabric.feature.module.UsageStatsFeatureModule;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class HudSettingsScreen extends BaseConfigScreen {
    private TextFieldWidget leaveTimeField;
    private TextFieldWidget statusHudXField;
    private TextFieldWidget statusHudYField;
    private TextFieldWidget profitHudXField;
    private TextFieldWidget profitHudYField;
    private TextFieldWidget debugHudXField;
    private TextFieldWidget debugHudYField;

    public HudSettingsScreen(Screen parent) {
        super(parent, Text.literal("FarmHelper - HUD / Misc"));
    }

    @Override
    protected Text subtitleText() {
        return Text.literal("HUD visibility, tracker stats, and client behavior toggles.");
    }

    @Override
    protected void init() {
        int leftX = width / 2 - 210;
        int rightX = width / 2 + 10;
        int y = 36;
        int row = 22;

        addToggleButton(leftX, y, 190, "Enable Status HUD", () -> config.enableStatusHud, () -> config.enableStatusHud = !config.enableStatusHud);
        addToggleButton(rightX, y, 190, "Streamer Mode", () -> config.streamerMode, () -> config.streamerMode = !config.streamerMode);
        y += row;

        addToggleButton(leftX, y, 190, "Enable Profit HUD", () -> config.enableProfitHud, () -> config.enableProfitHud = !config.enableProfitHud);
        addToggleButton(rightX, y, 190, "Enable Debug HUD", () -> config.enableDebugHudOverlay, () -> config.enableDebugHudOverlay = !config.enableDebugHudOverlay);
        y += row;

        addToggleButton(leftX, y, 190, "Debug Mode", () -> config.debugMode, () -> config.debugMode = !config.debugMode);
        addToggleButton(rightX, y, 190, "Show HUD outside garden", () -> config.showStatusHudOutsideGarden, () -> config.showStatusHudOutsideGarden = !config.showStatusHudOutsideGarden);
        y += row;

        addToggleButton(leftX, y, 190, "Reset stats on disable", () -> config.resetStatsBetweenDisabling, () -> config.resetStatsBetweenDisabling = !config.resetStatsBetweenDisabling);
        addToggleButton(rightX, y, 190, "Send analytics data", () -> config.sendAnalyticData, () -> config.sendAnalyticData = !config.sendAnalyticData);
        y += row;

        addToggleButton(leftX, y, 190, "Performance mode", () -> config.performanceMode, () -> {
            config.performanceMode = !config.performanceMode;
            setFeatureEnabled("performance_mode", config.performanceMode);
        });
        addToggleButton(rightX, y, 190, "PiP mode", () -> config.pipMode, () -> {
            config.pipMode = !config.pipMode;
            setFeatureEnabled("pip_mode", config.pipMode);
        });
        y += row;

        addToggleButton(leftX, y, 190, "Auto ungrab mouse", () -> config.autoUngrabMouse, () -> {
            config.autoUngrabMouse = !config.autoUngrabMouse;
            setFeatureEnabled("ungrab_mouse", config.autoUngrabMouse);
        });
        addToggleButton(rightX, y, 190, "Leave timer enabled", () -> config.leaveTimerEnabled, () -> {
            config.leaveTimerEnabled = !config.leaveTimerEnabled;
            setFeatureEnabled("leave_timer", config.leaveTimerEnabled);
        });
        y += row;

        leaveTimeField = addIntegerField(
                leftX,
                y,
                190,
                "Leave timer minutes",
                config.leaveTimeMinutes,
                1,
                360,
                value -> config.leaveTimeMinutes = value
        );
        addToggleButton(rightX, y, 190, "Show stats title", () -> config.showStatsTitle, () -> config.showStatsTitle = !config.showStatsTitle);
        y += row;

        statusHudXField = addIntegerField(
                leftX,
                y,
                190,
                "Status HUD X",
                config.statusHudX,
                0,
                4000,
                value -> config.statusHudX = value
        );
        statusHudYField = addIntegerField(
                rightX,
                y,
                190,
                "Status HUD Y",
                config.statusHudY,
                0,
                4000,
                value -> config.statusHudY = value
        );
        y += row;

        profitHudXField = addIntegerField(
                leftX,
                y,
                190,
                "Profit HUD X",
                config.profitHudX,
                0,
                4000,
                value -> config.profitHudX = value
        );
        profitHudYField = addIntegerField(
                rightX,
                y,
                190,
                "Profit HUD Y",
                config.profitHudY,
                0,
                4000,
                value -> config.profitHudY = value
        );
        y += row;

        addSimpleButton(leftX, y, 390, "Open HUD Editor (Drag & Drop)", btn -> client.setScreen(new HudEditorScreen(this)));
        y += row;

        debugHudXField = addIntegerField(
                leftX,
                y,
                190,
                "Debug HUD X",
                config.debugHudX,
                0,
                4000,
                value -> config.debugHudX = value
        );
        debugHudYField = addIntegerField(
                rightX,
                y,
                190,
                "Debug HUD Y",
                config.debugHudY,
                0,
                4000,
                value -> config.debugHudY = value
        );
        y += row;

        addToggleButton(leftX, y, 190, "Show session stats", () -> config.showStatsSession, () -> config.showStatsSession = !config.showStatsSession);
        addToggleButton(rightX, y, 190, "Show failsafe stats", () -> config.showStatsFailsafes, () -> config.showStatsFailsafes = !config.showStatsFailsafes);
        y += row;

        addToggleButton(leftX, y, 190, "Show 24h stats", () -> config.showStats24H, () -> config.showStats24H = !config.showStats24H);
        addToggleButton(rightX, y, 190, "Show 7d stats", () -> config.showStats7D, () -> config.showStats7D = !config.showStats7D);
        y += row;

        addToggleButton(leftX, y, 190, "Show 30d stats", () -> config.showStats30D, () -> config.showStats30D = !config.showStats30D);
        addToggleButton(rightX, y, 190, "Show lifetime stats", () -> config.showStatsLifetime, () -> config.showStatsLifetime = !config.showStatsLifetime);
        y += row;

        addSimpleButton(leftX, y, 190, "Reset Session Stats", btn -> UsageStatsFeatureModule.resetSession());
        addToggleButton(rightX, y, 190, "Change window title", () -> config.changeWindowTitle, () -> config.changeWindowTitle = !config.changeWindowTitle);
        y += row + 8;

        addSimpleButton(width / 2 - 120, y, 240, "Back", btn -> close());
    }
}

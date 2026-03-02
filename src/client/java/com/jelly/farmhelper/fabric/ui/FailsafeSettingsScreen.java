package com.jelly.farmhelper.fabric.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class FailsafeSettingsScreen extends BaseConfigScreen {
    private TextFieldWidget stopDelayField;
    private TextFieldWidget bpsThresholdField;
    private TextFieldWidget detectionWindowField;
    private TextFieldWidget reconnectDelayField;
    private TextFieldWidget reconnectAttemptsField;
    private TextFieldWidget reactionMinField;
    private TextFieldWidget reactionMaxField;

    public FailsafeSettingsScreen(Screen parent) {
        super(parent, Text.literal("FarmHelper - Failsafe Settings"));
    }

    @Override
    protected Text subtitleText() {
        return Text.literal("Detectors, reconnect policy, and automated reaction timings.");
    }

    @Override
    protected void init() {
        int leftX = width / 2 - 210;
        int rightX = width / 2 + 10;
        int y = 36;
        int row = 22;

        addToggleButton(leftX, y, 190, "Enable failsafes", () -> config.enableFailsafes, () -> config.enableFailsafes = !config.enableFailsafes);
        addToggleButton(rightX, y, 190, "Popup notifications", () -> config.popUpNotifications, () -> config.popUpNotifications = !config.popUpNotifications);
        y += row;

        addToggleButton(leftX, y, 190, "Auto alt-tab", () -> config.autoAltTab, () -> config.autoAltTab = !config.autoAltTab);
        addToggleButton(rightX, y, 190, "Disable-only action", () -> config.failsafeActionDisableOnly, () -> config.failsafeActionDisableOnly = !config.failsafeActionDisableOnly);
        y += row;

        addToggleButton(leftX, y, 190, "Auto warp world change", () -> config.autoWarpOnWorldChange, () -> config.autoWarpOnWorldChange = !config.autoWarpOnWorldChange);
        addToggleButton(rightX, y, 190, "Auto reconnect", () -> config.autoReconnect, () -> {
            config.autoReconnect = !config.autoReconnect;
            setFeatureEnabled("auto_reconnect", config.autoReconnect);
        });
        y += row;

        addToggleButton(leftX, y, 190, "Pause on guest arrival", () -> config.pauseOnGuestArrival, () -> config.pauseOnGuestArrival = !config.pauseOnGuestArrival);
        addToggleButton(rightX, y, 190, "Auto evacuate reboot", () -> config.autoEvacuateOnServerReboot, () -> config.autoEvacuateOnServerReboot = !config.autoEvacuateOnServerReboot);
        y += row;

        addToggleButton(leftX, y, 190, "Enable BPS check", () -> config.enableBpsCheck, () -> config.enableBpsCheck = !config.enableBpsCheck);
        addToggleButton(rightX, y, 190, "Packet-level checks", () -> config.enablePacketFailsafeChecks, () -> config.enablePacketFailsafeChecks = !config.enablePacketFailsafeChecks);
        y += row;

        addToggleButton(leftX, y, 190, "Jacob failsafe (strict)", () -> config.enableJacobFailsafe, () -> config.enableJacobFailsafe = !config.enableJacobFailsafe);
        addToggleButton(rightX, y, 190, "Enable failsafe sound", () -> config.enableFailsafeSound, () -> config.enableFailsafeSound = !config.enableFailsafeSound);
        y += row;

        addToggleButton(leftX, y, 190, "Restart after failsafe", () -> config.restartAfterFailsafe, () -> config.restartAfterFailsafe = !config.restartAfterFailsafe);
        addToggleButton(rightX, y, 190, "Always tp to garden", () -> config.alwaysTeleportToGarden, () -> config.alwaysTeleportToGarden = !config.alwaysTeleportToGarden);
        y += row;

        addToggleButton(leftX, y, 190, "Send failsafe chat msg", () -> config.sendFailsafeChatMessage, () -> config.sendFailsafeChatMessage = !config.sendFailsafeChatMessage);
        addToggleButton(rightX, y, 190, "Custom failsafe reactions", () -> config.enableCustomFailsafeReactions, () -> config.enableCustomFailsafeReactions = !config.enableCustomFailsafeReactions);
        y += row;

        addToggleButton(leftX, y, 190, "Failsafe 2nd message", () -> config.customFailsafeSendSecondMessage, () -> config.customFailsafeSendSecondMessage = !config.customFailsafeSendSecondMessage);
        addToggleButton(rightX, y, 190, "Reaction warp to garden", () -> config.customFailsafeWarpToGarden, () -> config.customFailsafeWarpToGarden = !config.customFailsafeWarpToGarden);
        y += row;

        stopDelayField = addIntegerField(
                leftX, y, 190,
                "Stop Delay (ms)",
                config.failsafeStopDelayMs,
                1000,
                7500,
                value -> config.failsafeStopDelayMs = value
        );
        bpsThresholdField = addFloatField(
                rightX, y, 190,
                "Min BPS",
                config.minBpsThreshold,
                5f,
                15f,
                value -> config.minBpsThreshold = value
        );
        y += row;

        detectionWindowField = addIntegerField(
                leftX, y, 190,
                "Detection Window (ms)",
                config.detectionTimeWindowMs,
                50,
                4000,
                value -> config.detectionTimeWindowMs = value
        );
        reconnectDelayField = addIntegerField(
                rightX, y, 190,
                "Reconnect Delay (s)",
                config.autoReconnectDelaySeconds,
                1,
                60,
                value -> config.autoReconnectDelaySeconds = value
        );
        y += row;

        reconnectAttemptsField = addIntegerField(
                leftX, y, 190,
                "Reconnect Max Attempts",
                config.autoReconnectMaxAttempts,
                1,
                10,
                value -> config.autoReconnectMaxAttempts = value
        );
        reactionMinField = addIntegerField(
                rightX, y, 190,
                "Reaction Min (ms)",
                config.customFailsafeReactionMinMs,
                200,
                6000,
                value -> {
                    config.customFailsafeReactionMinMs = value;
                    if (config.customFailsafeReactionMaxMs < config.customFailsafeReactionMinMs) {
                        config.customFailsafeReactionMaxMs = config.customFailsafeReactionMinMs + 100;
                        if (reactionMaxField != null) {
                            reactionMaxField.setText(String.valueOf(config.customFailsafeReactionMaxMs));
                        }
                    }
                }
        );
        y += row;

        reactionMaxField = addIntegerField(
                leftX, y, 190,
                "Reaction Max (ms)",
                config.customFailsafeReactionMaxMs,
                300,
                7000,
                value -> config.customFailsafeReactionMaxMs = Math.max(value, config.customFailsafeReactionMinMs + 100)
        );
        y += row + 8;

        addSimpleButton(width / 2 - 120, y, 240, "Back", btn -> close());
    }
}

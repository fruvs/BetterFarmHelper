package com.jelly.farmhelper.fabric.ui;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class DiscordSettingsScreen extends BaseConfigScreen {
    private TextFieldWidget webhookUrlField;
    private TextFieldWidget statusIntervalField;

    public DiscordSettingsScreen(Screen parent) {
        super(parent, Text.literal("FarmHelper - Discord/Webhook"));
    }

    @Override
    protected Text subtitleText() {
        return Text.literal("Webhook endpoint, debug-log controls, and notification routing.");
    }

    @Override
    protected void init() {
        int x = width / 2 - 190;
        int y = 36;
        int rowWidth = 380;
        int row = 22;

        addToggleButton(x, y, rowWidth, "Enable Webhook", () -> config.enableWebhook, () -> config.enableWebhook = !config.enableWebhook);
        y += row;
        addToggleButton(x, y, rowWidth, "Send Logs", () -> config.sendWebhookLogs, () -> config.sendWebhookLogs = !config.sendWebhookLogs);
        y += row;
        addToggleButton(x, y, rowWidth, "Send Status Updates", () -> config.sendStatusUpdates, () -> config.sendStatusUpdates = !config.sendStatusUpdates);
        y += row;
        addToggleButton(x, y, rowWidth, "Send Macro Enable/Disable Logs", () -> config.sendMacroEnableDisableLogs, () -> config.sendMacroEnableDisableLogs = !config.sendMacroEnableDisableLogs);
        y += row;
        addToggleButton(x, y, rowWidth, "Send Failsafe Logs", () -> config.sendFailsafeLogs, () -> config.sendFailsafeLogs = !config.sendFailsafeLogs);
        y += row;
        addToggleButton(x, y, rowWidth, "Send Debug Logs", () -> config.sendWebhookDebugLogs, () -> config.sendWebhookDebugLogs = !config.sendWebhookDebugLogs);
        y += row;
        addToggleButton(x, y, rowWidth, "Write Debug Logs To File", () -> config.writeDebugLogsToFile, () -> config.writeDebugLogsToFile = !config.writeDebugLogsToFile);
        y += row;
        addToggleButton(x, y, rowWidth, "Send Visitors Macro Logs", () -> config.sendVisitorsMacroLogs, () -> config.sendVisitorsMacroLogs = !config.sendVisitorsMacroLogs);
        y += row;
        addToggleButton(x, y, rowWidth, "Ping everyone on visitors logs", () -> config.pingEveryoneOnVisitorsMacroLogs, () -> config.pingEveryoneOnVisitorsMacroLogs = !config.pingEveryoneOnVisitorsMacroLogs);
        y += row;

        statusIntervalField = addIntegerField(
                x,
                y,
                rowWidth,
                "Status interval (minutes)",
                config.statusUpdateIntervalMinutes,
                1,
                60,
                value -> config.statusUpdateIntervalMinutes = value
        );
        y += row + 2;

        webhookUrlField = addTextField(
                x,
                y,
                rowWidth,
                "Webhook URL",
                config.webhookUrl,
                1000,
                value -> config.webhookUrl = value.trim()
        );
        y += row + 6;

        addSimpleButton(width / 2 - 120, y, 110, "Back", btn -> close());
        addSimpleButton(width / 2 + 10, y, 110, "Disable Remote Ctrl", btn -> {
            config.enableRemoteControl = false;
            btn.setMessage(Text.literal("Remote Ctrl: OFF"));
        });
        y += row;
        addSimpleButton(width / 2 - 190, y, 380, "Send Current Debug Log", btn ->
                FarmHelperFabric.getWebhookService().sendCurrentDebugLog("ui button"));
        y += row;
        addSimpleButton(width / 2 - 190, y, 380, "Print Local Debug Log Path", btn ->
                com.jelly.farmhelper.fabric.util.Chat.info("Debug log: " + FarmHelperFabric.getWebhookService().getDebugLogFile()));
    }

}

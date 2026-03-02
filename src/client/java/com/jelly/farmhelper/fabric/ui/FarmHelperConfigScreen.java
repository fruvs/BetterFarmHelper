package com.jelly.farmhelper.fabric.ui;

import com.jelly.farmhelper.fabric.macro.LegacyMacroType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FarmHelperConfigScreen extends BaseConfigScreen {
    private static final class SectionHint {
        private final int x;
        private final int y;
        private final String text;

        private SectionHint(int x, int y, String text) {
            this.x = x;
            this.y = y;
            this.text = text;
        }
    }

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int quickPanelX;
    private int quickPanelY;
    private int quickPanelWidth;
    private int quickPanelHeight;
    private int sectionsPanelX;
    private int sectionsPanelY;
    private int sectionsPanelWidth;
    private int sectionsPanelHeight;

    private final List<SectionHint> sectionHints = new ArrayList<>();
    private CyclingButtonWidget<LegacyMacroType> macroTypeSelector;
    private ButtonWidget farmingSpeedButton;

    public FarmHelperConfigScreen(Screen parent) {
        super(parent, Text.literal("FarmHelper Configuration"));
    }

    @Override
    protected boolean usesCustomChrome() {
        return true;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(980, width - 26);
        panelHeight = Math.min(620, height - 26);
        panelX = width / 2 - panelWidth / 2;
        panelY = height / 2 - panelHeight / 2;

        quickPanelX = panelX + 14;
        quickPanelY = panelY + 44;
        quickPanelWidth = Math.min(410, panelWidth - 42);
        quickPanelHeight = panelHeight - 58;

        sectionsPanelX = quickPanelX + quickPanelWidth + 12;
        sectionsPanelY = quickPanelY;
        sectionsPanelWidth = panelX + panelWidth - 14 - sectionsPanelX;
        sectionsPanelHeight = quickPanelHeight;

        sectionHints.clear();
        macroTypeSelector = null;
        farmingSpeedButton = null;

        buildTopBar();
        buildQuickStart();
        buildSections();
        refreshLabels();
    }

    private void buildTopBar() {
        int buttonY = panelY + 12;
        addSimpleButton(panelX + panelWidth - 198, buttonY, 88, "Save", btn -> com.jelly.farmhelper.fabric.FarmHelperFabric.getConfigManager().save());
        addSimpleButton(panelX + panelWidth - 104, buttonY, 88, "Close", btn -> close());
    }

    private void buildQuickStart() {
        int left = quickPanelX + 12;
        int right = left + 194;
        int width = 186;
        int y = quickPanelY + 30;

        macroTypeSelector = addSelector(
                left,
                y,
                quickPanelWidth - 24,
                "Macro Type",
                Arrays.asList(LegacyMacroType.values()),
                () -> config.macroType,
                selected -> config.macroType = selected,
                LegacyMacroType::getDisplayName
        );
        y += 30;

        addToggleButton(left, y, width, "Always hold W", () -> config.alwaysHoldW, () -> config.alwaysHoldW = !config.alwaysHoldW);
        addToggleButton(right, y, width, "Use custom speed", () -> config.useCustomFarmingSpeed, () -> config.useCustomFarmingSpeed = !config.useCustomFarmingSpeed);
        y += 24;

        addSimpleButton(left, y, width, "Open Macro Page", btn -> client.setScreen(new MacroSettingsScreen(this)));
        farmingSpeedButton = addSimpleButton(right, y, width, "", btn -> {
            config.farmingSpeed = wrap(config.farmingSpeed + 5, 1, 500);
            refreshLabels();
        });
        y += 30;

        addToggleButton(left, y, width, "Enable failsafes", () -> config.enableFailsafes, () -> config.enableFailsafes = !config.enableFailsafes);
        addToggleButton(right, y, width, "Auto alt-tab", () -> config.autoAltTab, () -> config.autoAltTab = !config.autoAltTab);
        y += 24;

        addSimpleButton(left, y, width, "Open Failsafe Page", btn -> client.setScreen(new FailsafeSettingsScreen(this)));
        addToggleButton(right, y, width, "Restart after failsafe", () -> config.restartAfterFailsafe, () -> config.restartAfterFailsafe = !config.restartAfterFailsafe);
        y += 30;

        addToggleButton(left, y, width, "Enable webhook", () -> config.enableWebhook, () -> config.enableWebhook = !config.enableWebhook);
        addSimpleButton(right, y, width, "Discord/Webhook", btn -> client.setScreen(new DiscordSettingsScreen(this)));
        y += 30;

        addSimpleButton(left, y, width * 2 + 8, "Keybinds (Minecraft Controls)", btn ->
                client.setScreen(new ControlsOptionsScreen(this, client.options)));
    }

    private void buildSections() {
        int x = sectionsPanelX + 12;
        int y = sectionsPanelY + 30;
        int w = sectionsPanelWidth - 24;

        addSectionButton(x, y, w, "Macro & Movement", "Crop type, movement profile, rewarp, pitch/yaw tuning.", btn ->
                client.setScreen(new MacroSettingsScreen(this)));
        y += 56;
        addSectionButton(x, y, w, "Failsafes", "Detectors, auto-alt-tab, reconnect, custom reactions.", btn ->
                client.setScreen(new FailsafeSettingsScreen(this)));
        y += 56;
        addSectionButton(x, y, w, "Automation Modules", "Auto sell, pest tools, visitors, cookie, bazaar, etc.", btn ->
                client.setScreen(new AutomationSettingsScreen(this)));
        y += 56;
        addSectionButton(x, y, w, "Feature Toggles", "Enable or disable each behavior module directly.", btn ->
                client.setScreen(new FeatureToggleScreen(this, 0)));
        y += 56;
        addSectionButton(x, y, w, "HUD & Misc", "Status HUD visibility, stats, ungrab/perf related options.", btn ->
                client.setScreen(new HudSettingsScreen(this)));
        y += 56;
        addSectionButton(x, y, w, "Discord / Webhook", "Webhook URL, debug log upload, status updates.", btn ->
                client.setScreen(new DiscordSettingsScreen(this)));
    }

    private void addSectionButton(int x, int y, int width, String title, String description, ButtonWidget.PressAction action) {
        addSimpleButton(x, y, width, title, action);
        sectionHints.add(new SectionHint(x + 4, y + 22, description));
    }

    private void refreshLabels() {
        if (macroTypeSelector != null) {
            macroTypeSelector.setValue(config.macroType);
        }
        if (farmingSpeedButton != null) {
            farmingSpeedButton.setMessage(Text.literal("Farming Speed: " + config.farmingSpeed));
        }
    }

    @Override
    protected void renderDecorations(DrawContext context, int mouseX, int mouseY, float delta) {
        int shell = 0xE910141A;
        int border = 0xA5495666;
        int panel = 0xD0141A24;
        int panelAlt = 0xC9101620;
        int titleColor = 0xFFE9EEF8;
        int subColor = 0xFFABB7C8;
        int accent = 0xFF5BA4FF;

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, shell);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 1, border);
        context.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, border);
        context.fill(panelX, panelY, panelX + 1, panelY + panelHeight, border);
        context.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, border);

        context.fill(quickPanelX, quickPanelY, quickPanelX + quickPanelWidth, quickPanelY + quickPanelHeight, panel);
        context.fill(sectionsPanelX, sectionsPanelY, sectionsPanelX + sectionsPanelWidth, sectionsPanelY + sectionsPanelHeight, panelAlt);

        context.drawText(textRenderer, Text.literal("FarmHelper Control Center"), panelX + 16, panelY + 16, titleColor, false);
        context.drawText(textRenderer, Text.literal("Simple start points and full pages grouped by purpose."), panelX + 16, panelY + 28, subColor, false);

        context.drawText(textRenderer, Text.literal("Quick Start"), quickPanelX + 12, quickPanelY + 12, accent, false);
        context.drawText(textRenderer, Text.literal("Most-used settings in one place"), quickPanelX + 12, quickPanelY + 22, subColor, false);

        context.drawText(textRenderer, Text.literal("All Settings Pages"), sectionsPanelX + 12, sectionsPanelY + 12, accent, false);
        context.drawText(textRenderer, Text.literal("Open the dedicated page for deeper options"), sectionsPanelX + 12, sectionsPanelY + 22, subColor, false);

        for (SectionHint hint : sectionHints) {
            context.drawText(textRenderer, Text.literal(hint.text), hint.x, hint.y, subColor, false);
        }
    }

    private int wrap(int value, int min, int max) {
        if (value > max) {
            return min;
        }
        if (value < min) {
            return max;
        }
        return value;
    }
}

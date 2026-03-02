package com.jelly.farmhelper.fabric.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class AutomationSettingsScreen extends BaseConfigScreen {
    private TextFieldWidget petSwapperNameField;

    public AutomationSettingsScreen(Screen parent) {
        super(parent, Text.literal("FarmHelper - Automation Modules"));
    }

    @Override
    protected Text subtitleText() {
        return Text.literal("Visitors, pests, scheduler, bazaar, cookie, sell, and utility automation.");
    }

    @Override
    protected void init() {
        int leftX = width / 2 - 210;
        int rightX = width / 2 + 10;
        int y = 36;
        int row = 22;

        addToggleButton(leftX, y, 190, "Enable Scheduler", () -> config.enableScheduler, () -> {
            config.enableScheduler = !config.enableScheduler;
            setFeatureEnabled("scheduler", config.enableScheduler);
        });
        addToggleButton(rightX, y, 190, "Pause Scheduler in Jacob", () -> config.pauseSchedulerDuringJacobsContest, () -> config.pauseSchedulerDuringJacobsContest = !config.pauseSchedulerDuringJacobsContest);
        y += row;

        addIntegerField(leftX, y, 190, "Farming Minutes", config.schedulerFarmingTimeMinutes, 1, 300, value -> config.schedulerFarmingTimeMinutes = value);
        addIntegerField(rightX, y, 190, "Break Minutes", config.schedulerBreakTimeMinutes, 1, 120, value -> config.schedulerBreakTimeMinutes = value);
        y += row;

        addIntegerField(leftX, y, 190, "Farming Random (min)", config.schedulerFarmingTimeRandomnessMinutes, 0, 15, value -> config.schedulerFarmingTimeRandomnessMinutes = value);
        addIntegerField(rightX, y, 190, "Break Random (min)", config.schedulerBreakTimeRandomnessMinutes, 0, 15, value -> config.schedulerBreakTimeRandomnessMinutes = value);
        y += row;

        addToggleButton(leftX, y, 190, "Wait break until rewarp", () -> config.schedulerWaitUntilRewarp, () -> config.schedulerWaitUntilRewarp = !config.schedulerWaitUntilRewarp);
        addToggleButton(rightX, y, 190, "Disconnect during break", () -> config.schedulerDisconnectDuringBreak, () -> config.schedulerDisconnectDuringBreak = !config.schedulerDisconnectDuringBreak);
        y += row;

        addIntegerField(leftX, y, 190, "Rewarp Wait Timeout (s)", config.schedulerWaitForRewarpTimeoutSeconds, 10, 600, value -> config.schedulerWaitForRewarpTimeoutSeconds = value);
        addToggleButton(rightX, y, 190, "Reset scheduler on disable", () -> config.schedulerResetOnDisable, () -> config.schedulerResetOnDisable = !config.schedulerResetOnDisable);
        y += row;

        addToggleButton(leftX, y, 190, "Visitors Macro", () -> config.visitorsMacro, () -> {
            config.visitorsMacro = !config.visitorsMacro;
            setFeatureEnabled("visitors_macro", config.visitorsMacro);
        });
        addToggleButton(rightX, y, 190, "Pests Destroyer", () -> config.enablePestsDestroyer, () -> {
            config.enablePestsDestroyer = !config.enablePestsDestroyer;
            setFeatureEnabled("pests_destroyer", config.enablePestsDestroyer);
        });
        y += row;

        addToggleButton(leftX, y, 190, "Auto Sell", () -> config.enableAutoSell, () -> {
            config.enableAutoSell = !config.enableAutoSell;
            setFeatureEnabled("auto_sell", config.enableAutoSell);
        });
        addToggleButton(rightX, y, 190, "Auto Composter", () -> config.autoComposter, () -> {
            config.autoComposter = !config.autoComposter;
            setFeatureEnabled("auto_composter", config.autoComposter);
        });
        y += row;

        addToggleButton(leftX, y, 190, "Auto Pest Exchange", () -> config.autoPestExchange, () -> {
            config.autoPestExchange = !config.autoPestExchange;
            setFeatureEnabled("auto_pest_exchange", config.autoPestExchange);
        });
        addToggleButton(rightX, y, 190, "Auto God Pot", () -> config.autoGodPot, () -> {
            config.autoGodPot = !config.autoGodPot;
            setFeatureEnabled("auto_god_pot", config.autoGodPot);
        });
        y += row;

        addToggleButton(leftX, y, 190, "Auto Repellent", () -> config.autoRepellent, () -> {
            config.autoRepellent = !config.autoRepellent;
            setFeatureEnabled("auto_repellent", config.autoRepellent);
        });
        addToggleButton(rightX, y, 190, "Leave Timer", () -> config.leaveTimerEnabled, () -> {
            config.leaveTimerEnabled = !config.leaveTimerEnabled;
            setFeatureEnabled("leave_timer", config.leaveTimerEnabled);
        });
        y += row;

        addToggleButton(leftX, y, 190, "Auto Cookie", () -> config.autoCookie, () -> {
            config.autoCookie = !config.autoCookie;
            setFeatureEnabled("auto_cookie", config.autoCookie);
        });
        addToggleButton(rightX, y, 190, "Auto Bazaar", () -> config.autoBazaar, () -> {
            config.autoBazaar = !config.autoBazaar;
            setFeatureEnabled("auto_bazaar", config.autoBazaar);
        });
        y += row;

        addToggleButton(leftX, y, 190, "Pet Swapper", () -> config.enablePetSwapper, () -> {
            config.enablePetSwapper = !config.enablePetSwapper;
            setFeatureEnabled("pet_swapper", config.enablePetSwapper);
        });
        addToggleButton(rightX, y, 190, "Plot Cleaning Helper", () -> config.plotCleaningHelper, () -> {
            config.plotCleaningHelper = !config.plotCleaningHelper;
            setFeatureEnabled("plot_cleaning_helper", config.plotCleaningHelper);
        });
        y += row;

        addIntegerField(leftX, y, 190, "Visitors Min Queue", config.visitorsMacroMinVisitors, 1, 20, value -> config.visitorsMacroMinVisitors = value);
        addIntegerField(rightX, y, 190, "Pests Trigger Count", config.startKillingPestsAt, 1, 20, value -> config.startKillingPestsAt = value);
        y += row;

        addIntegerField(leftX, y, 190, "Visitors Per Cycle", config.visitorsMacroMaxVisitorsPerCycle, 1, 10, value -> config.visitorsMacroMaxVisitorsPerCycle = value);
        addIntegerField(rightX, y, 190, "Visitors Retry Limit", config.visitorsMacroRetryLimit, 1, 8, value -> config.visitorsMacroRetryLimit = value);
        y += row;

        addIntegerField(leftX, y, 190, "AutoSell Inventory %", config.inventoryFullRatio, 40, 100, value -> config.inventoryFullRatio = value);
        addIntegerField(rightX, y, 190, "AutoSell Cooldown (s)", config.autoSellCommandCooldownSeconds, 1, 30, value -> config.autoSellCommandCooldownSeconds = value);
        y += row;

        addIntegerField(leftX, y, 190, "Pests Max Passes", config.pestsDestroyerMaxPasses, 1, 10, value -> config.pestsDestroyerMaxPasses = value);
        addIntegerField(rightX, y, 190, "Pests Retry Limit", config.pestsDestroyerRetryLimit, 1, 10, value -> config.pestsDestroyerRetryLimit = value);
        y += row;

        addToggleButton(leftX, y, 190, "Pest Tracers", () -> config.pestsTracers, () -> config.pestsTracers = !config.pestsTracers);
        addToggleButton(rightX, y, 190, "Pest Hitboxes", () -> config.pestsHighlightBox, () -> config.pestsHighlightBox = !config.pestsHighlightBox);
        y += row;

        addIntegerField(leftX, y, 190, "Composter X", config.composterX, -300, 300, value -> config.composterX = value);
        addIntegerField(rightX, y, 190, "Composter Y", config.composterY, -64, 320, value -> config.composterY = value);
        y += row;

        addIntegerField(leftX, y, 190, "Composter Z", config.composterZ, -300, 300, value -> config.composterZ = value);
        addIntegerField(rightX, y, 190, "Pest Desk X", config.pestExchangeDeskX, -300, 300, value -> config.pestExchangeDeskX = value);
        y += row;

        addIntegerField(leftX, y, 190, "Pest Desk Y", config.pestExchangeDeskY, -64, 320, value -> config.pestExchangeDeskY = value);
        addIntegerField(rightX, y, 190, "Pest Desk Z", config.pestExchangeDeskZ, -300, 300, value -> config.pestExchangeDeskZ = value);
        y += row;

        addIntegerField(leftX, y, 190, "God Pot Check (min)", config.autoGodPotCheckMinutes, 5, 120, value -> config.autoGodPotCheckMinutes = value);
        addIntegerField(rightX, y, 190, "Repellent Check (min)", config.autoRepellentCheckMinutes, 5, 120, value -> config.autoRepellentCheckMinutes = value);
        y += row;

        addIntegerField(leftX, y, 190, "Cookie Check (min)", config.autoCookieCheckMinutes, 5, 240, value -> config.autoCookieCheckMinutes = value);
        addIntegerField(rightX, y, 190, "AutoBazaar Timeout (s)", config.autoBazaarActionSeconds, 8, 90, value -> config.autoBazaarActionSeconds = value);
        y += row;

        addIntegerField(leftX, y, 190, "PestEx Retry Limit", config.autoPestExchangeRetryLimit, 1, 10, value -> config.autoPestExchangeRetryLimit = value);
        addToggleButton(rightX, y, 190, "PestEx only relevant start", () -> config.autoPestExchangeOnlyStartRelevant, () -> config.autoPestExchangeOnlyStartRelevant = !config.autoPestExchangeOnlyStartRelevant);
        y += row;

        addIntegerField(leftX, y, 190, "Plot Clean Radius", config.plotCleaningScanRadius, 2, 8, value -> config.plotCleaningScanRadius = value);
        petSwapperNameField = new TextFieldWidget(textRenderer, rightX, y, 190, 20, Text.literal("Pet Swapper Target"));
        petSwapperNameField.setText(config.petSwapperName == null ? "" : config.petSwapperName);
        petSwapperNameField.setMaxLength(64);
        petSwapperNameField.setSuggestion("Pet Swapper Target");
        petSwapperNameField.setChangedListener(value -> {
            config.petSwapperName = value == null ? "" : value.trim();
            com.jelly.farmhelper.fabric.FarmHelperFabric.getConfigManager().save();
        });
        addDrawableChild(petSwapperNameField);
        y += row + 8;

        addSimpleButton(width / 2 - 120, y, 240, "Back", btn -> close());
    }
}

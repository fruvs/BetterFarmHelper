package com.jelly.farmhelper.fabric.ui;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.feature.FeatureCatalog;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

public class FeatureToggleScreen extends BaseConfigScreen {
    private static final int PAGE_SIZE = 10;
    private final int pageIndex;

    public FeatureToggleScreen(Screen parent, int pageIndex) {
        super(parent, Text.literal("FarmHelper - Feature Toggles"));
        this.pageIndex = Math.max(0, pageIndex);
    }

    @Override
    protected Text subtitleText() {
        return Text.literal("Global behavior switches. Use pages to browse all modules.");
    }

    @Override
    protected void init() {
        List<FeatureCatalog.FeatureDefinition> all = FeatureCatalog.DEFINITIONS;
        int maxPages = Math.max(1, (int) Math.ceil(all.size() / (double) PAGE_SIZE));
        int clampedPage = Math.min(pageIndex, maxPages - 1);
        int start = clampedPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, all.size());

        int x = width / 2 - 190;
        int y = 34;
        int row = 22;

        for (int i = start; i < end; i++) {
            FeatureCatalog.FeatureDefinition definition = all.get(i);
            boolean enabled = config.featureToggles.getOrDefault(definition.id(), false);
            String label = definition.displayName() + ": " + (enabled ? "ON" : "OFF");
            addSimpleButton(x, y, 380, label, btn -> {
                boolean newValue = !config.featureToggles.getOrDefault(definition.id(), false);
                syncConfigForFeature(definition.id(), newValue);
                setFeatureEnabled(definition.id(), newValue);
                btn.setMessage(Text.literal(definition.displayName() + ": " + (newValue ? "ON" : "OFF")));
            });
            y += row;
        }

        int footerY = height - 36;
        addSimpleButton(width / 2 - 190, footerY, 120, "Prev Page", btn -> {
            if (clampedPage > 0) {
                client.setScreen(new FeatureToggleScreen(parent, clampedPage - 1));
            }
        });
        addSimpleButton(width / 2 - 60, footerY, 120, "Back", btn -> close());
        addSimpleButton(width / 2 + 70, footerY, 120, "Next Page", btn -> {
            if (clampedPage + 1 < maxPages) {
                client.setScreen(new FeatureToggleScreen(parent, clampedPage + 1));
            }
        });
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int maxPages = Math.max(1, (int) Math.ceil(FeatureCatalog.DEFINITIONS.size() / (double) PAGE_SIZE));
        int clampedPage = Math.min(pageIndex, maxPages - 1);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Page " + (clampedPage + 1) + " / " + maxPages),
                width / 2, height - 52, 0xAAAAAA);
    }

    private void syncConfigForFeature(String id, boolean enabled) {
        switch (id) {
            case "anti_stuck" -> config.antiStuckEnabled = enabled;
            case "auto_bazaar" -> config.autoBazaar = enabled;
            case "auto_sprayonator" -> config.autoSprayonator = enabled;
            case "auto_wardrobe" -> config.autoWardrobe = enabled;
            case "bps_tracker" -> config.bpsTrackerEnabled = enabled;
            case "desync_checker" -> config.checkDesync = enabled;
            case "freelook" -> config.freelook = enabled;
            case "lag_detector" -> config.lagDetectorEnabled = enabled;
            case "mov_rec_player" -> config.movementRecorderEnabled = enabled;
            case "scheduler" -> config.enableScheduler = enabled;
            case "leave_timer" -> config.leaveTimerEnabled = enabled;
            case "auto_reconnect" -> config.autoReconnect = enabled;
            case "auto_sell" -> config.enableAutoSell = enabled;
            case "auto_cookie" -> config.autoCookie = enabled;
            case "visitors_macro" -> config.visitorsMacro = enabled;
            case "pests_destroyer" -> config.enablePestsDestroyer = enabled;
            case "pest_farmer" -> config.pestFarmer = enabled;
            case "pests_destroyer_track" -> config.pestsDestroyerOnTheTrack = enabled;
            case "auto_composter" -> config.autoComposter = enabled;
            case "auto_pest_exchange" -> config.autoPestExchange = enabled;
            case "auto_god_pot" -> config.autoGodPot = enabled;
            case "auto_repellent" -> config.autoRepellent = enabled;
            case "pet_swapper" -> config.enablePetSwapper = enabled;
            case "plot_cleaning_helper" -> config.plotCleaningHelper = enabled;
            case "profit_calculator" -> config.profitCalculatorEnabled = enabled;
            case "proxy" -> config.proxyEnabled = enabled;
            case "rancher_speed_setter" -> config.autoSetRancherSpeed = enabled;
            case "performance_mode" -> config.performanceMode = enabled;
            case "pip_mode" -> config.pipMode = enabled;
            case "ungrab_mouse" -> config.autoUngrabMouse = enabled;
            default -> {
            }
        }
    }
}

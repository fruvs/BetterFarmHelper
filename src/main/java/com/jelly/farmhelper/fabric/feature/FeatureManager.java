package com.jelly.farmhelper.fabric.feature;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.module.AutoComposterFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.AutoCookieFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.AutoGodPotFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.AutoSprayonatorFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.AutoWardrobeFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.AutoBazaarFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.AutoPestExchangeFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.AutoReconnectFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.AutoRepellentFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.AutoSellFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.AntiStuckFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.BpsTrackerFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.DesyncCheckerFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.FreelookFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.LagDetectorFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.LeaveTimerFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.MovRecPlayerFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.MacroExclusiveFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.PassiveFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.PerformanceModeFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.PestFarmerFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.PestsDestroyerTrackFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.PipModeFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.PetSwapperFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.PestsDestroyerFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.PlotCleaningHelperFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.ProfitCalculatorFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.ProxyFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.RancherSpeedSetterFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.SchedulerFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.UngrabMouseFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.UsageStatsFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.VisitorsFeatureModule;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class FeatureManager {
    private final Map<String, Feature> features = new LinkedHashMap<>();

    public void bootstrap() {
        features.clear();
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        for (FeatureCatalog.FeatureDefinition definition : FeatureCatalog.DEFINITIONS) {
            boolean enabledByConfig = config.featureToggles.getOrDefault(
                    definition.id(),
                    defaultEnabled(definition.id(), config)
            );
            config.featureToggles.put(definition.id(), enabledByConfig);
            register(createModule(definition, enabledByConfig));
        }
        FarmHelperFabric.LOGGER.info("Feature manager bootstrapped");
    }

    public void register(Feature feature) {
        features.put(feature.id(), feature);
        if (feature.enabled()) {
            feature.onEnable();
        }
    }

    public Collection<Feature> all() {
        return features.values();
    }

    public Optional<Feature> get(String id) {
        return Optional.ofNullable(features.get(id));
    }

    public boolean setFeatureEnabled(String id, boolean enabled) {
        Feature feature = features.get(id);
        if (feature == null) {
            return false;
        }
        feature.setEnabled(enabled);
        FarmHelperFabric.getConfigManager().getConfig().featureToggles.put(id, enabled);
        return true;
    }

    public void syncWithConfig() {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        for (FeatureCatalog.FeatureDefinition definition : FeatureCatalog.DEFINITIONS) {
            boolean enabled = config.featureToggles.getOrDefault(
                    definition.id(),
                    defaultEnabled(definition.id(), config)
            );
            Feature feature = features.get(definition.id());
            if (feature != null) {
                feature.setEnabled(enabled);
            }
        }
    }

    public void tickEnabledFeatures(FeatureRuntimeState runtime) {
        for (Feature feature : features.values()) {
            if (feature.enabled()) {
                feature.onTick(runtime);
            }
        }
    }

    public void onChatMessage(String message) {
        for (Feature feature : features.values()) {
            if (feature.enabled()) {
                feature.onChatMessage(message);
            }
        }
    }

    public void onDisconnect() {
        for (Feature feature : features.values()) {
            if (feature.enabled()) {
                feature.onDisconnect();
            }
        }
    }

    public void cancelMacroExclusiveActions(String reason) {
        for (Feature feature : features.values()) {
            if (!feature.enabled()) {
                continue;
            }
            if (feature instanceof MacroExclusiveFeatureModule macroExclusive) {
                macroExclusive.cancelActiveAction(reason);
            }
        }
    }

    private Feature createModule(FeatureCatalog.FeatureDefinition definition, boolean enabled) {
        return switch (definition.id()) {
            case "anti_stuck" -> new AntiStuckFeatureModule(enabled);
            case "auto_bazaar" -> new AutoBazaarFeatureModule(enabled);
            case "auto_composter" -> new AutoComposterFeatureModule(enabled);
            case "auto_cookie" -> new AutoCookieFeatureModule(enabled);
            case "auto_god_pot" -> new AutoGodPotFeatureModule(enabled);
            case "auto_pest_exchange" -> new AutoPestExchangeFeatureModule(enabled);
            case "auto_sprayonator" -> new AutoSprayonatorFeatureModule(enabled);
            case "auto_wardrobe" -> new AutoWardrobeFeatureModule(enabled);
            case "bps_tracker" -> new BpsTrackerFeatureModule(enabled);
            case "desync_checker" -> new DesyncCheckerFeatureModule(enabled);
            case "freelook" -> new FreelookFeatureModule(enabled);
            case "lag_detector" -> new LagDetectorFeatureModule(enabled);
            case "mov_rec_player" -> new MovRecPlayerFeatureModule(enabled);
            case "performance_mode" -> new PerformanceModeFeatureModule(enabled);
            case "pest_farmer" -> new PestFarmerFeatureModule(enabled);
            case "scheduler" -> new SchedulerFeatureModule(enabled);
            case "leave_timer" -> new LeaveTimerFeatureModule(enabled);
            case "auto_reconnect" -> new AutoReconnectFeatureModule(enabled);
            case "auto_repellent" -> new AutoRepellentFeatureModule(enabled);
            case "usage_stats_tracker" -> new UsageStatsFeatureModule(enabled);
            case "auto_sell" -> new AutoSellFeatureModule(enabled);
            case "visitors_macro" -> new VisitorsFeatureModule(enabled);
            case "pests_destroyer" -> new PestsDestroyerFeatureModule(enabled);
            case "pests_destroyer_track" -> new PestsDestroyerTrackFeatureModule(enabled);
            case "pet_swapper" -> new PetSwapperFeatureModule(enabled);
            case "pip_mode" -> new PipModeFeatureModule(enabled);
            case "plot_cleaning_helper" -> new PlotCleaningHelperFeatureModule(enabled);
            case "profit_calculator" -> new ProfitCalculatorFeatureModule(enabled);
            case "proxy" -> new ProxyFeatureModule(enabled);
            case "rancher_speed_setter" -> new RancherSpeedSetterFeatureModule(enabled);
            case "ungrab_mouse" -> new UngrabMouseFeatureModule(enabled);
            default -> new PassiveFeatureModule(definition.id(), definition.displayName(), enabled);
        };
    }

    private boolean defaultEnabled(String id, FarmHelperConfig config) {
        return switch (id) {
            case "auto_bazaar" -> config.autoBazaar;
            case "scheduler" -> config.enableScheduler;
            case "leave_timer" -> config.leaveTimerEnabled;
            case "auto_reconnect" -> config.autoReconnect;
            case "anti_stuck" -> config.antiStuckEnabled;
            case "auto_sell" -> config.enableAutoSell;
            case "auto_cookie" -> config.autoCookie;
            case "auto_sprayonator" -> config.autoSprayonator;
            case "auto_wardrobe" -> config.autoWardrobe;
            case "bps_tracker" -> config.bpsTrackerEnabled;
            case "desync_checker" -> config.checkDesync;
            case "freelook" -> config.freelook;
            case "lag_detector" -> config.lagDetectorEnabled;
            case "mov_rec_player" -> config.movementRecorderEnabled;
            case "visitors_macro" -> config.visitorsMacro;
            case "pests_destroyer" -> config.enablePestsDestroyer;
            case "pests_destroyer_track" -> config.pestsDestroyerOnTheTrack;
            case "pest_farmer" -> config.pestFarmer;
            case "auto_composter" -> config.autoComposter;
            case "auto_pest_exchange" -> config.autoPestExchange;
            case "auto_god_pot" -> config.autoGodPot;
            case "auto_repellent" -> config.autoRepellent;
            case "pet_swapper" -> config.enablePetSwapper;
            case "plot_cleaning_helper" -> config.plotCleaningHelper;
            case "performance_mode" -> config.performanceMode;
            case "pip_mode" -> config.pipMode;
            case "profit_calculator" -> config.profitCalculatorEnabled;
            case "proxy" -> config.proxyEnabled;
            case "rancher_speed_setter" -> config.autoSetRancherSpeed || config.useCustomFarmingSpeed;
            case "ungrab_mouse" -> config.autoUngrabMouse;
            default -> false;
        };
    }
}

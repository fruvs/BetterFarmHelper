package com.jelly.farmhelper.fabric.feature;

import java.util.List;

public final class FeatureCatalog {
    private FeatureCatalog() {
    }

    public static final List<FeatureDefinition> DEFINITIONS = List.of(
            new FeatureDefinition("anti_stuck", "Anti Stuck"),
            new FeatureDefinition("auto_bazaar", "Auto Bazaar"),
            new FeatureDefinition("auto_composter", "Auto Composter"),
            new FeatureDefinition("auto_cookie", "Auto Cookie"),
            new FeatureDefinition("auto_god_pot", "Auto God Pot"),
            new FeatureDefinition("auto_pest_exchange", "Auto Pest Exchange"),
            new FeatureDefinition("auto_reconnect", "Auto Reconnect"),
            new FeatureDefinition("auto_repellent", "Auto Repellent"),
            new FeatureDefinition("auto_sell", "Auto Sell"),
            new FeatureDefinition("auto_sprayonator", "Auto Sprayonator"),
            new FeatureDefinition("auto_wardrobe", "Auto Wardrobe"),
            new FeatureDefinition("bps_tracker", "BPS Tracker"),
            new FeatureDefinition("ban_info_ws", "Ban Info Analytics"),
            new FeatureDefinition("desync_checker", "Desync Checker"),
            new FeatureDefinition("freelook", "Freelook"),
            new FeatureDefinition("lag_detector", "Lag Detector"),
            new FeatureDefinition("leave_timer", "Leave Timer"),
            new FeatureDefinition("mov_rec_player", "Movement Recorder"),
            new FeatureDefinition("performance_mode", "Performance Mode"),
            new FeatureDefinition("pest_farmer", "Pest Farmer"),
            new FeatureDefinition("pests_destroyer", "Pests Destroyer"),
            new FeatureDefinition("pests_destroyer_track", "Pests Destroyer On Track"),
            new FeatureDefinition("pet_swapper", "Pet Swapper"),
            new FeatureDefinition("pip_mode", "PiP Mode"),
            new FeatureDefinition("plot_cleaning_helper", "Plot Cleaning Helper"),
            new FeatureDefinition("profit_calculator", "Profit Calculator"),
            new FeatureDefinition("proxy", "Proxy"),
            new FeatureDefinition("rancher_speed_setter", "Rancher Speed Setter"),
            new FeatureDefinition("scheduler", "Scheduler"),
            new FeatureDefinition("ungrab_mouse", "Ungrab Mouse"),
            new FeatureDefinition("usage_stats_tracker", "Usage Stats Tracker"),
            new FeatureDefinition("visitors_macro", "Visitors Macro")
    );

    public static boolean isValidId(String id) {
        return DEFINITIONS.stream().anyMatch(definition -> definition.id().equals(id));
    }

    public record FeatureDefinition(String id, String displayName) {
    }
}

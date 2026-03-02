package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;
import com.jelly.farmhelper.fabric.macro.MacroState;

public class PestsDestroyerTrackFeatureModule extends MacroExclusiveFeatureModule {
    private static final String PEST_NAMES =
            "beetle,cricket,earthworm,fly,locust,mite,mosquito,moth,rat,slug,praying mantis,firefly,dragonfly,pest";

    private long lastTrackAttemptTick = -1L;

    public PestsDestroyerTrackFeatureModule(boolean enabled) {
        super("pests_destroyer_track", "Pests Destroyer On Track", enabled);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        lastTrackAttemptTick = -1L;
    }

    @Override
    public void onDisconnect() {
        super.onDisconnect();
        lastTrackAttemptTick = -1L;
    }

    @Override
    public void cancelActiveAction(String reason) {
        super.cancelActiveAction(reason);
        lastTrackAttemptTick = -1L;
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.pestsDestroyerOnTheTrack || runtime.activeFailsafe.isPresent()) {
            return;
        }
        if (!runtime.macroToggled || runtime.macroState != MacroState.FARMING) {
            return;
        }
        if (runtime.networkLagging || runtime.screenOpen) {
            return;
        }

        if (isActionRunning()) {
            if (shouldEndAction(runtime.tickCount)) {
                endTimedAction("pest track attempt complete");
                lastTrackAttemptTick = runtime.tickCount;
            }
            return;
        }

        long cooldown = Math.max(20L, config.pestsDestroyerOnTrackPersistTicks);
        if (lastTrackAttemptTick > 0 && runtime.tickCount - lastTrackAttemptTick < cooldown) {
            return;
        }

        if (!beginTimedAction(runtime.tickCount, 90L, "track pest near lane")) {
            return;
        }
        double radius = Math.max(3.5, config.pestsDestroyerOnTrackRadius);
        queueSelectHotbarItem("vacuum", runtime.tickCount);
        queueFlyToEntity(
                PEST_NAMES,
                radius,
                70L,
                runtime.tickCount
        );
        queueUseHeldItem(runtime.tickCount);
        queueAttackNearestEntity(
                PEST_NAMES,
                radius,
                70L,
                runtime.tickCount
        );
    }
}

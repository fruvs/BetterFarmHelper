package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;
import com.jelly.farmhelper.fabric.macro.MacroState;

public class DesyncCheckerFeatureModule extends MacroExclusiveFeatureModule {
    private long lastPauseTick = -1L;

    public DesyncCheckerFeatureModule(boolean enabled) {
        super("desync_checker", "Desync Checker", enabled);
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.checkDesync || runtime.activeFailsafe.isPresent()) {
            return;
        }

        if (isActionRunning()) {
            if (shouldEndAction(runtime.tickCount)) {
                endTimedAction("desync pause complete");
                lastPauseTick = runtime.tickCount;
            }
            return;
        }

        if (!runtime.macroToggled || runtime.macroState != MacroState.FARMING) {
            return;
        }
        if (runtime.networkLagging || runtime.screenOpen) {
            return;
        }
        if (lastPauseTick > 0 && runtime.tickCount - lastPauseTick < 160L) {
            return;
        }

        int stationaryThreshold = Math.max(40, config.desyncStationaryTicks);
        float lowBpsThreshold = Math.max(0.4f, config.minBpsThreshold * 0.45f);
        if (runtime.stationaryTicks < stationaryThreshold || runtime.horizontalSpeedBps > lowBpsThreshold) {
            return;
        }

        long pauseTicks = Math.max(40L, config.desyncPauseDelayMs / 50L);
        if (beginTimedAction(runtime.tickCount, pauseTicks, "possible crop desync")) {
            FarmHelperFabric.getWebhookService().sendFeatureLog(
                    "Desync Checker paused macro for " + config.desyncPauseDelayMs + "ms due to persistent stationary state"
            );
        }
    }
}

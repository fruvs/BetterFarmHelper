package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

public class PerformanceModeFeatureModule extends AbstractFeatureModule {
    private boolean applied;

    public PerformanceModeFeatureModule(boolean enabled) {
        super("performance_mode", "Performance Mode", enabled);
    }

    @Override
    public void onDisable() {
        if (applied) {
            FarmHelperFabric.getClientActionQueue().enqueueSetPerformanceMode(false, 30, 2, 0L);
            applied = false;
        }
    }

    @Override
    public void onDisconnect() {
        if (applied) {
            FarmHelperFabric.getClientActionQueue().enqueueSetPerformanceMode(false, 30, 2, 0L);
            applied = false;
        }
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        boolean shouldApply = config.performanceMode && runtime.macroToggled && runtime.inWorld;
        if (shouldApply == applied) {
            return;
        }
        FarmHelperFabric.getClientActionQueue().enqueueSetPerformanceMode(
                shouldApply,
                config.performanceModeMaxFps,
                config.performanceModeViewDistance,
                runtime.tickCount
        );
        applied = shouldApply;
    }
}

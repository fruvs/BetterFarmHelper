package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

public class PipModeFeatureModule extends AbstractFeatureModule {
    private boolean applied;

    public PipModeFeatureModule(boolean enabled) {
        super("pip_mode", "PiP Mode", enabled);
    }

    @Override
    public void onDisable() {
        if (applied) {
            FarmHelperFabric.getClientActionQueue().enqueueSetPipMode(false, 0L);
            applied = false;
        }
    }

    @Override
    public void onDisconnect() {
        if (applied) {
            FarmHelperFabric.getClientActionQueue().enqueueSetPipMode(false, 0L);
            applied = false;
        }
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        boolean shouldApply = config.pipMode && runtime.macroToggled && runtime.inWorld;
        if (shouldApply == applied) {
            return;
        }
        FarmHelperFabric.getClientActionQueue().enqueueSetPipMode(shouldApply, runtime.tickCount);
        applied = shouldApply;
    }
}

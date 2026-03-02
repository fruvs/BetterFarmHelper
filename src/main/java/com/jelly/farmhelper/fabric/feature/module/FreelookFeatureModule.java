package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

public class FreelookFeatureModule extends AbstractFeatureModule {
    private boolean applied;

    public FreelookFeatureModule(boolean enabled) {
        super("freelook", "Freelook", enabled);
    }

    @Override
    public void onDisable() {
        if (applied) {
            FarmHelperFabric.getClientActionQueue().enqueueSetFreelookMode(false, 0L);
            applied = false;
        }
    }

    @Override
    public void onDisconnect() {
        if (applied) {
            FarmHelperFabric.getClientActionQueue().enqueueSetFreelookMode(false, 0L);
            applied = false;
        }
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        boolean shouldApply = config.freelook && runtime.inWorld;
        if (shouldApply == applied) {
            return;
        }
        FarmHelperFabric.getClientActionQueue().enqueueSetFreelookMode(shouldApply, runtime.tickCount);
        applied = shouldApply;
    }
}

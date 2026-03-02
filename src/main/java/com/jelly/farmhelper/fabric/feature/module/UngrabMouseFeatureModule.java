package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

public class UngrabMouseFeatureModule extends AbstractFeatureModule {
    private boolean applied;

    public UngrabMouseFeatureModule(boolean enabled) {
        super("ungrab_mouse", "Ungrab Mouse", enabled);
    }

    @Override
    public void onDisable() {
        if (applied) {
            FarmHelperFabric.getClientActionQueue().enqueueSetMouseUngrab(false, 0L);
            applied = false;
        }
    }

    @Override
    public void onDisconnect() {
        if (applied) {
            FarmHelperFabric.getClientActionQueue().enqueueSetMouseUngrab(false, 0L);
            applied = false;
        }
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        boolean shouldApply = config.autoUngrabMouse && runtime.macroToggled && runtime.inWorld;
        if (shouldApply == applied) {
            return;
        }
        FarmHelperFabric.getClientActionQueue().enqueueSetMouseUngrab(shouldApply, runtime.tickCount);
        applied = shouldApply;
    }
}

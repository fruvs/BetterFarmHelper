package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

public class PassiveFeatureModule extends AbstractFeatureModule {
    public PassiveFeatureModule(String id, String name, boolean enabled) {
        super(id, name, enabled);
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        // Placeholder for modules that are not behavior-ported yet.
    }
}

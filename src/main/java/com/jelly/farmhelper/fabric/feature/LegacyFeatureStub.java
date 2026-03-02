package com.jelly.farmhelper.fabric.feature;

import com.jelly.farmhelper.fabric.FarmHelperFabric;

public class LegacyFeatureStub implements Feature {
    private final String id;
    private final String displayName;
    private boolean enabled;

    public LegacyFeatureStub(String id, String displayName, boolean enabled) {
        this.id = id;
        this.displayName = displayName;
        this.enabled = enabled;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        FarmHelperFabric.LOGGER.info("Feature {} {}", displayName, enabled ? "enabled" : "disabled");
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        // Placeholder feature until full behavior is ported.
    }
}

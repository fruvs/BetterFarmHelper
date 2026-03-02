package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.feature.Feature;

public abstract class AbstractFeatureModule implements Feature {
    private final String id;
    private final String name;
    private boolean enabled;

    protected AbstractFeatureModule(String id, String name, boolean enabled) {
        this.id = id;
        this.name = name;
        this.enabled = enabled;
    }

    @Override
    public String id() {
        return id;
    }

    public String name() {
        return name;
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
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
        FarmHelperFabric.LOGGER.info("Feature module {} {}", name, enabled ? "enabled" : "disabled");
    }
}

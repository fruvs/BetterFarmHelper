package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

public class LeaveTimerFeatureModule extends AbstractFeatureModule {
    public LeaveTimerFeatureModule(boolean enabled) {
        super("leave_timer", "Leave Timer", enabled);
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        if (!FarmHelperFabric.getConfigManager().getConfig().leaveTimerEnabled) {
            return;
        }
        if (!runtime.macroToggled) {
            return;
        }

        long maxTicks = Math.max(1, FarmHelperFabric.getConfigManager().getConfig().leaveTimeMinutes) * 60L * 20L;
        if (runtime.macroRuntimeTicks >= maxTicks) {
            FarmHelperFabric.getMacroController().disable();
        }
    }
}

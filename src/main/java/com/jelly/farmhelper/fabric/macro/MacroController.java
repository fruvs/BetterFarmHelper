package com.jelly.farmhelper.fabric.macro;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.feature.module.SchedulerFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.UsageStatsFeatureModule;

public class MacroController {
    private boolean toggled;
    private boolean pausedByFeature;
    private MacroState state = MacroState.STOPPED;
    private long runtimeTicks;
    private long pausedByFailsafeTicks;

    public void toggle() {
        if (toggled) {
            disableByUser();
        } else {
            enable();
        }
    }

    public void enable() {
        toggled = true;
        pausedByFeature = false;
        state = MacroState.STARTING;
        runtimeTicks = 0;
        pausedByFailsafeTicks = 0;
        UsageStatsFeatureModule.resetSession();
        FarmHelperFabric.LOGGER.info("Macro enabled");
        FarmHelperFabric.getWebhookService().onMacroToggled(true);
        FarmHelperFabric.getWebhookService().debugTrace("macro", "enabled by user/system");
    }

    public void disable() {
        disableInternal(false);
    }

    public void disableByUser() {
        disableInternal(true);
    }

    private void disableInternal(boolean userInitiated) {
        if (userInitiated) {
            SchedulerFeatureModule.cancelPendingResume();
            FarmHelperFabric.getFailsafeManager().onMacroStoppedByUser();
        }
        boolean wasRunning = toggled || state != MacroState.STOPPED;
        toggled = false;
        pausedByFeature = false;
        state = MacroState.STOPPED;
        pausedByFailsafeTicks = 0;
        FarmHelperFabric.getFeatureManager().cancelMacroExclusiveActions("macro disabled");
        FarmHelperFabric.getClientActionQueue().clear();
        if (wasRunning) {
            FarmHelperFabric.LOGGER.info("Macro disabled");
            FarmHelperFabric.getWebhookService().onMacroToggled(false);
            FarmHelperFabric.getWebhookService().debugTrace("macro", "disabled by user/system");
        }
    }

    public void pauseForFeature(String reason) {
        if (!toggled) {
            return;
        }
        if (pausedByFeature) {
            return;
        }
        pausedByFeature = true;
        state = MacroState.PAUSED_BY_FEATURE;
        FarmHelperFabric.LOGGER.info("Macro paused by feature: {}", reason);
    }

    public void resumeFromFeature(String reason) {
        if (!toggled) {
            return;
        }
        if (!pausedByFeature) {
            return;
        }
        pausedByFeature = false;
        state = MacroState.STARTING;
        FarmHelperFabric.LOGGER.info("Macro resumed after feature pause: {}", reason);
    }

    public void tick() {
        if (!toggled) {
            return;
        }

        if (pausedByFeature) {
            state = MacroState.PAUSED_BY_FEATURE;
            return;
        }

        if (FarmHelperFabric.getFailsafeManager().hasActiveFailsafe()) {
            state = MacroState.PAUSED_BY_FAILSAFE;
            pausedByFailsafeTicks++;
            return;
        }

        if (state == MacroState.STARTING || state == MacroState.PAUSED_BY_FAILSAFE) {
            state = MacroState.FARMING;
        }

        runtimeTicks++;
    }

    public boolean isToggled() {
        return toggled;
    }

    public MacroState getState() {
        return state;
    }

    public long getRuntimeTicks() {
        return runtimeTicks;
    }

    public long getPausedByFailsafeTicks() {
        return pausedByFailsafeTicks;
    }

    public boolean isPausedByFeature() {
        return pausedByFeature;
    }
}

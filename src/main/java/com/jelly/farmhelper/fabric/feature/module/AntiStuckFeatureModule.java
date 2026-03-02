package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

public class AntiStuckFeatureModule extends MacroExclusiveFeatureModule {
    private int attempts;
    private long lastAttemptTick = -1L;
    private boolean warpQueuedThisCycle;

    public AntiStuckFeatureModule(boolean enabled) {
        super("anti_stuck", "Anti Stuck", enabled);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        attempts = 0;
        warpQueuedThisCycle = false;
    }

    @Override
    public void onDisconnect() {
        super.onDisconnect();
        attempts = 0;
        warpQueuedThisCycle = false;
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.antiStuckEnabled || !runtime.macroToggled || runtime.activeFailsafe.isPresent()) {
            if (!runtime.macroToggled) {
                attempts = 0;
                warpQueuedThisCycle = false;
            }
            return;
        }
        if (runtime.networkLagging || runtime.screenOpen) {
            return;
        }

        if (isActionRunning()) {
            if (shouldEndAction(runtime.tickCount)) {
                endTimedAction("unstick attempt finished");
                lastAttemptTick = runtime.tickCount;
                if (warpQueuedThisCycle) {
                    attempts = 0;
                    warpQueuedThisCycle = false;
                }
            }
            return;
        }

        int stationaryThreshold = Math.max(20, config.antiStuckStationaryTicks);
        if (runtime.stationaryTicks < stationaryThreshold) {
            attempts = Math.max(0, attempts - 1);
            warpQueuedThisCycle = false;
            return;
        }
        if (lastAttemptTick > 0 && runtime.tickCount - lastAttemptTick < 60L) {
            return;
        }

        attempts++;
        int maxAttemptsBeforeWarp = Math.max(1, config.antiStuckTriesUntilWarp);
        if (attempts >= maxAttemptsBeforeWarp) {
            if (beginTimedAction(runtime.tickCount, 120L, "unstick fallback warp")) {
                queueCommand("/warp garden", runtime.tickCount);
                FarmHelperFabric.getFailsafeManager().suppressPacketChecks(140L, 100L, 40L, "anti stuck recovery warp");
                warpQueuedThisCycle = true;
            }
            return;
        }

        if (!beginTimedAction(runtime.tickCount, 120L, "unstick movement attempt")) {
            return;
        }

        double yawRad = Math.toRadians(runtime.yaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = forwardZ;
        double rightZ = -forwardX;

        double side = attempts % 2 == 0 ? 1.25 : -1.25;
        double back = -1.2;
        double x = runtime.posX + rightX * side + forwardX * back;
        double z = runtime.posZ + rightZ * side + forwardZ * back;

        queueMoveTo(x, runtime.posY, z, 0.8, 60L, runtime.tickCount);
        queueMoveTo(runtime.posX, runtime.posY, runtime.posZ, 0.8, 45L, runtime.tickCount);
    }
}

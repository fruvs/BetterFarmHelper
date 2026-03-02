package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

public class LagDetectorFeatureModule extends AbstractFeatureModule {
    private static volatile boolean lagging;
    private static volatile long lastLagTick = -1L;
    private static volatile long millisSincePacket;
    private static volatile float estimatedTps = 20f;

    public LagDetectorFeatureModule(boolean enabled) {
        super("lag_detector", "Lag Detector", enabled);
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onDisconnect() {
        reset();
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.lagDetectorEnabled) {
            reset();
            return;
        }
        millisSincePacket = runtime.millisSinceWorldTimePacket;
        estimatedTps = runtime.estimatedServerTps;
        lagging = runtime.networkLagging;
        if (runtime.networkLagging) {
            lastLagTick = runtime.tickCount;
        }
    }

    public static boolean isLagging() {
        return lagging;
    }

    public static boolean wasRecentlyLagging(long nowTick, long withinTicks) {
        return lastLagTick > 0 && nowTick - lastLagTick <= Math.max(1L, withinTicks);
    }

    public static long getMillisSincePacket() {
        return millisSincePacket;
    }

    public static float getEstimatedTps() {
        return estimatedTps;
    }

    private void reset() {
        lagging = false;
        lastLagTick = -1L;
        millisSincePacket = 0L;
        estimatedTps = 20f;
    }
}

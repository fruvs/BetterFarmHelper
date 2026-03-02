package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;
import com.jelly.farmhelper.fabric.macro.MacroState;

import java.util.ArrayDeque;

public class BpsTrackerFeatureModule extends AbstractFeatureModule {
    private record Sample(long tick, double bps) {
    }

    private static final ArrayDeque<Sample> SAMPLES = new ArrayDeque<>();
    private static volatile boolean paused = true;
    private static volatile float currentBps;
    private static volatile float averageBps;

    public BpsTrackerFeatureModule(boolean enabled) {
        super("bps_tracker", "BPS Tracker", enabled);
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
        FarmHelperConfig config = com.jelly.farmhelper.fabric.FarmHelperFabric.getConfigManager().getConfig();
        if (!config.bpsTrackerEnabled || runtime.macroState != MacroState.FARMING || runtime.networkLagging || runtime.screenOpen) {
            paused = true;
            trim(runtime.tickCount);
            return;
        }

        paused = false;
        currentBps = (float) Math.max(0.0, runtime.horizontalSpeedBps);
        SAMPLES.addLast(new Sample(runtime.tickCount, currentBps));
        trim(runtime.tickCount);
        averageBps = computeAverage();
    }

    public static float getCurrentBps() {
        return currentBps;
    }

    public static float getAverageBps() {
        return averageBps;
    }

    public static boolean isPaused() {
        return paused;
    }

    private void reset() {
        SAMPLES.clear();
        paused = true;
        currentBps = 0f;
        averageBps = 0f;
    }

    private void trim(long nowTick) {
        while (!SAMPLES.isEmpty() && nowTick - SAMPLES.peekFirst().tick > 200L) {
            SAMPLES.pollFirst();
        }
    }

    private float computeAverage() {
        if (SAMPLES.isEmpty()) {
            return 0f;
        }
        double sum = 0.0;
        int count = 0;
        for (Sample sample : SAMPLES) {
            sum += sample.bps;
            count++;
        }
        if (count == 0) {
            return 0f;
        }
        return (float) (sum / count);
    }
}

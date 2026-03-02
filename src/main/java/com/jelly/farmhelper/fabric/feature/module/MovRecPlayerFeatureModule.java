package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

import java.util.ArrayDeque;

public class MovRecPlayerFeatureModule extends AbstractFeatureModule {
    private static final ArrayDeque<String> PLAY_REQUESTS = new ArrayDeque<>();
    private static volatile boolean stopRequested;

    public MovRecPlayerFeatureModule(boolean enabled) {
        super("mov_rec_player", "Movement Recorder Player", enabled);
    }

    public static void playRandomRecording(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return;
        }
        synchronized (PLAY_REQUESTS) {
            PLAY_REQUESTS.addLast(pattern.trim());
        }
    }

    public static void stopPlayback() {
        stopRequested = true;
    }

    @Override
    public void onDisable() {
        stopRequested = true;
    }

    @Override
    public void onDisconnect() {
        stopRequested = true;
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.movementRecorderEnabled) {
            return;
        }

        if (stopRequested) {
            FarmHelperFabric.getClientActionQueue().enqueueStopMovementRecording(runtime.tickCount);
            stopRequested = false;
        }

        String request = null;
        synchronized (PLAY_REQUESTS) {
            if (!PLAY_REQUESTS.isEmpty()) {
                request = PLAY_REQUESTS.pollFirst();
            }
        }
        if (request != null) {
            FarmHelperFabric.getClientActionQueue().enqueuePlayMovementRecording(request, runtime.tickCount);
        }
    }
}

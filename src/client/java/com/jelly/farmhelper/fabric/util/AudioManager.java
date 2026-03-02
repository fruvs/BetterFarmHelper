package com.jelly.farmhelper.fabric.util;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

public final class AudioManager {
    private static final AudioManager INSTANCE = new AudioManager();

    private boolean alertActive;
    private long lastPingTick = -20L;
    private int remainingPings;

    private AudioManager() {
    }

    public static AudioManager getInstance() {
        return INSTANCE;
    }

    public void playFailsafeAlert() {
        alertActive = true;
        remainingPings = 20;
        lastPingTick = -20L;
    }

    public void stop() {
        alertActive = false;
        remainingPings = 0;
    }

    public boolean isAlertActive() {
        return alertActive;
    }

    public void tick(long tickCount) {
        if (!alertActive) {
            return;
        }
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.enableFailsafeSound) {
            stop();
            return;
        }
        if (remainingPings <= 0) {
            stop();
            return;
        }
        if (tickCount - lastPingTick < 10L) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getSoundManager() != null) {
            client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f));
        }
        lastPingTick = tickCount;
        remainingPings--;
    }
}

package com.jelly.farmhelper.fabric.util;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;

public final class AudioManager {
    private static final AudioManager INSTANCE = new AudioManager();

    private boolean alertActive;
    private long lastPingTick = -20L;
    private boolean playPlaceSoundNext = true;

    private AudioManager() {
    }

    public static AudioManager getInstance() {
        return INSTANCE;
    }

    public void playFailsafeAlert() {
        alertActive = true;
        lastPingTick = -20L;
        playPlaceSoundNext = true;
    }

    public void stop() {
        alertActive = false;
    }

    public boolean isAlertActive() {
        return alertActive;
    }

    public void tick(long tickCount) {
        if (!alertActive) {
            return;
        }
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.enableFailsafeSound || !config.enableFailsafeAnvilAlert) {
            return;
        }
        int intervalTicks = Math.max(1, config.failsafeAnvilAlertIntervalTicks);
        if (tickCount - lastPingTick < intervalTicks) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getSoundManager() != null) {
            float volume = MathHelper.clamp(config.failsafeAnvilAlertVolume, 0.1f, 2.0f);
            if (playPlaceSoundNext) {
                client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_ANVIL_PLACE, volume));
            } else {
                client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_ANVIL_LAND, volume));
            }
            playPlaceSoundNext = !playPlaceSoundNext;
        }
        lastPingTick = tickCount;
    }
}

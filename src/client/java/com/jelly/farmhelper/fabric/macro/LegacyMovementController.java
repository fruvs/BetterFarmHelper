package com.jelly.farmhelper.fabric.macro;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import net.minecraft.client.MinecraftClient;

interface LegacyMovementController {
    void updateState(
            MinecraftClient client,
            FarmHelperConfig config,
            int forwardTicks,
            int sideTicks,
            boolean holdAttack
    );

    void invokeState(
            MinecraftClient client,
            FarmHelperConfig config,
            int forwardTicks,
            int sideTicks,
            boolean holdAttack
    );
}

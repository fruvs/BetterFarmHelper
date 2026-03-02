package com.jelly.farmhelper.fabric.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class Chat {
    private static final String PREFIX = "[FarmHelper] ";

    private Chat() {
    }

    public static void info(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(PREFIX + message), false);
        }
    }
}

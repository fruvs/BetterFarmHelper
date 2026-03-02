package com.jelly.farmhelper.fabric.ui;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.feature.module.AutoReconnectFeatureModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class AutoReconnectOverlayRenderer {
    private AutoReconnectOverlayRenderer() {
    }

    public static void render(DrawContext context, int width) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (context == null || client == null || client.textRenderer == null) {
            return;
        }
        if (!FarmHelperFabric.getConfigManager().getConfig().autoReconnect || !AutoReconnectFeatureModule.hasPendingReconnect()) {
            return;
        }

        long nowTick = System.currentTimeMillis() / 50L;
        long remainingTicks = AutoReconnectFeatureModule.getRemainingReconnectTicks(nowTick);
        int attempt = AutoReconnectFeatureModule.getCurrentAttempt();
        int maxAttempts = AutoReconnectFeatureModule.getMaxAttemptsSnapshot();
        String text = "FarmHelper AutoReconnect: " + formatTicks(remainingTicks)
                + " (" + attempt + "/" + maxAttempts + ")";

        int textWidth = client.textRenderer.getWidth(text);
        int x = Math.max(6, (width - textWidth) / 2);
        int y = 6;
        context.fill(x - 4, y - 3, x + textWidth + 4, y + 11, 0xA0000000);
        context.drawText(client.textRenderer, text, x, y, 0xFF55AAFF, true);
    }

    private static String formatTicks(long ticks) {
        long totalSeconds = Math.max(0L, ticks) / 20L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}

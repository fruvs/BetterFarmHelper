package com.jelly.farmhelper.fabric.hud;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.Optional;

public final class FailsafeBannerRenderer {
    private FailsafeBannerRenderer() {
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (context == null || client == null || client.textRenderer == null) {
            return;
        }

        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.enableFailsafeBanner) {
            return;
        }

        Optional<FailsafeType> activeFailsafe = FarmHelperFabric.getFailsafeManager().getActiveFailsafe();
        if (activeFailsafe.isEmpty()) {
            return;
        }

        String failsafeName = prettify(activeFailsafe.get().name());
        String reason = FarmHelperFabric.getFailsafeManager().getActiveReason();
        if (reason == null) {
            reason = "";
        }
        if (!reason.isBlank()) {
            reason = client.textRenderer.trimToWidth(reason, Math.max(120, client.getWindow().getScaledWidth() - 120));
        }

        String title = "FAILSAFE TRIGGERED: " + failsafeName;
        int width = client.getWindow().getScaledWidth();
        int titleWidth = client.textRenderer.getWidth(title);
        int reasonWidth = config.failsafeBannerShowReason && !reason.isBlank() ? client.textRenderer.getWidth(reason) : 0;
        int panelWidth = Math.min(width - 24, Math.max(titleWidth, reasonWidth) + 28);
        int panelHeight = config.failsafeBannerShowReason && !reason.isBlank() ? 30 : 20;

        int x = (width - panelWidth) / 2;
        int y = 18;

        long pulse = (System.currentTimeMillis() / 350L) % 2L;
        int background = pulse == 0L ? 0xD08A1C14 : 0xD09A2A1A;
        int border = 0xFFF06D52;

        context.fill(x, y, x + panelWidth, y + panelHeight, background);
        context.fill(x, y, x + panelWidth, y + 1, border);
        context.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, border);
        context.fill(x, y, x + 1, y + panelHeight, border);
        context.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, border);

        int titleX = x + (panelWidth - titleWidth) / 2;
        context.drawText(client.textRenderer, title, titleX, y + 4, 0xFFFFFFFF, true);
        if (panelHeight > 20) {
            int reasonX = x + (panelWidth - reasonWidth) / 2;
            context.drawText(client.textRenderer, Text.literal(reason), reasonX, y + 16, 0xFFFFD7A5, false);
        }
    }

    private static String prettify(String value) {
        String text = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        String[] words = text.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }
        return builder.isEmpty() ? value : builder.toString();
    }
}

package com.jelly.farmhelper.fabric.hud;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.FarmHelperFabricClient;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.module.ProfitCalculatorFeatureModule;
import com.jelly.farmhelper.fabric.state.GameStateHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class ProfitHudRenderer {
    private ProfitHudRenderer() {
    }

    public static void render(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return;
        }
        if (client.getDebugHud().shouldShowDebugHud()) {
            return;
        }
        if (FarmHelperFabricClient.getGameStateHandler().getLocation() != GameStateHandler.Location.GARDEN) {
            return;
        }

        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.enableProfitHud || !config.profitCalculatorEnabled) {
            return;
        }

        int x = Math.max(4, config.profitHudX);
        int y = Math.max(4, config.profitHudY);
        int color = 0xFFF7DCA4;
        String header = "Profit HUD";
        String totalLine = String.format(Locale.US, "Total: $%,.0f", ProfitCalculatorFeatureModule.getTotalProfitCoins());
        String hourlyLine = String.format(Locale.US, "Hourly: $%,.0f/h", ProfitCalculatorFeatureModule.getHourlyProfitCoins());
        String pricingMode = ProfitCalculatorFeatureModule.getBazaarPricingMode();
        String pricingLine = "Pricing: " + pricingMode;

        Map<String, Long> tracked = ProfitCalculatorFeatureModule.getTrackedCounts();
        List<Map.Entry<String, Long>> topEntries = tracked.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed())
                .limit(3)
                .collect(Collectors.toList());

        int width = client.textRenderer.getWidth(header);
        width = Math.max(width, client.textRenderer.getWidth(totalLine));
        width = Math.max(width, client.textRenderer.getWidth(hourlyLine));
        width = Math.max(width, client.textRenderer.getWidth(pricingLine));
        for (Map.Entry<String, Long> entry : topEntries) {
            width = Math.max(width, client.textRenderer.getWidth(entry.getKey() + ": " + entry.getValue()));
        }
        int lines = 4 + topEntries.size();
        int panelWidth = width + 10;
        int panelHeight = lines * 10 + 8;
        drawContext.fill(x - 4, y - 4, x - 4 + panelWidth, y - 4 + panelHeight, 0x78101824);
        drawContext.fill(x - 5, y - 5, x - 4 + panelWidth + 1, y - 4, 0xA03A4A64);
        drawContext.fill(x - 5, y - 4 + panelHeight, x - 4 + panelWidth + 1, y - 3 + panelHeight, 0xA03A4A64);

        drawContext.drawText(client.textRenderer, header, x, y, 0xFF8CD5A8, true);
        y += 10;
        drawContext.drawText(client.textRenderer, totalLine, x, y, color, true);
        y += 10;
        drawContext.drawText(client.textRenderer, hourlyLine, x, y, color, true);
        y += 10;
        drawContext.drawText(client.textRenderer, pricingLine, x, y,
                "live".equalsIgnoreCase(pricingMode) ? 0xFF55FF55 : 0xFFFFAA00, true);
        y += 10;
        for (Map.Entry<String, Long> entry : topEntries) {
            drawContext.drawText(client.textRenderer, entry.getKey() + ": " + entry.getValue(), x, y, 0xFFFFFFFF, true);
            y += 10;
        }
    }
}

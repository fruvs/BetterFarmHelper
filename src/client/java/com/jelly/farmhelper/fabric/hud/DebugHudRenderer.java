package com.jelly.farmhelper.fabric.hud;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.FarmHelperFabricClient;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;
import com.jelly.farmhelper.fabric.state.GameStateHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.Locale;

public final class DebugHudRenderer {
    private DebugHudRenderer() {
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
        if (!config.enableDebugHudOverlay && !config.debugMode) {
            return;
        }

        RuntimeSnapshot snapshot = FarmHelperFabricClient.getRuntimeSnapshot();
        int x = Math.max(4, config.debugHudX);
        int y = Math.max(4, config.debugHudY);
        String line1 = String.format(Locale.US, "Pos: %.2f %.2f %.2f", snapshot.posX, snapshot.posY, snapshot.posZ);
        String line2 = String.format(Locale.US, "Rot: yaw %.1f pitch %.1f (d %.2f/%.2f)", snapshot.yaw, snapshot.pitch, snapshot.yawDelta, snapshot.pitchDelta);
        String line3 = String.format(Locale.US, "Vel: %.3f %.3f %.3f", snapshot.velocityX, snapshot.verticalVelocity, snapshot.velocityZ);
        String line4 = String.format(Locale.US, "BPS %.2f | TPS %.1f | Lag %s", snapshot.horizontalSpeedBps, snapshot.estimatedServerTps, snapshot.networkLagging ? "YES" : "NO");
        String line5 = "Queue " + FarmHelperFabric.getClientActionQueue().size() + " | Screen \"" + snapshot.screenTitle + "\"";
        String line6 = String.format(
                Locale.US,
                "Vacuum %.1fm | DPS %.0f | Tracker %.1fs",
                snapshot.vacuumRange,
                snapshot.vacuumDps,
                snapshot.vacuumTrackerCooldownSeconds
        );
        String line7 = "Plots current=" + snapshot.currentPlot + " gui=" + snapshot.guiInfestedPlot + " most=" + snapshot.mostInfestedPlot + " pests=" + snapshot.pestsInTablist;
        int width = Math.max(client.textRenderer.getWidth("Debug HUD"), client.textRenderer.getWidth(line1));
        width = Math.max(width, client.textRenderer.getWidth(line2));
        width = Math.max(width, client.textRenderer.getWidth(line3));
        width = Math.max(width, client.textRenderer.getWidth(line4));
        width = Math.max(width, client.textRenderer.getWidth(line5));
        width = Math.max(width, client.textRenderer.getWidth(line6));
        width = Math.max(width, client.textRenderer.getWidth(line7));
        int panelWidth = width + 10;
        int panelHeight = 8 * 10 + 8;
        drawContext.fill(x - 4, y - 4, x - 4 + panelWidth, y - 4 + panelHeight, 0x78101824);
        drawContext.fill(x - 5, y - 5, x - 4 + panelWidth + 1, y - 4, 0xA03A4A64);
        drawContext.fill(x - 5, y - 4 + panelHeight, x - 4 + panelWidth + 1, y - 3 + panelHeight, 0xA03A4A64);

        drawContext.drawText(client.textRenderer, "Debug HUD", x, y, 0xFFFFAA55, true);
        y += 10;
        drawContext.drawText(client.textRenderer, line1, x, y, 0xFFFFFFFF, true);
        y += 10;
        drawContext.drawText(client.textRenderer, line2, x, y, 0xFFFFFFFF, true);
        y += 10;
        drawContext.drawText(client.textRenderer, line3, x, y, 0xFFFFFFFF, true);
        y += 10;
        drawContext.drawText(client.textRenderer, line4, x, y, snapshot.networkLagging ? 0xFFFF5555 : 0xFFFFFFFF, true);
        y += 10;
        drawContext.drawText(client.textRenderer, line5, x, y, 0xFFFFFFFF, true);
        y += 10;
        drawContext.drawText(client.textRenderer, line6, x, y, 0xFF9FE2FF, true);
        y += 10;
        drawContext.drawText(client.textRenderer, line7, x, y, 0xFFD6E8FF, true);
    }
}

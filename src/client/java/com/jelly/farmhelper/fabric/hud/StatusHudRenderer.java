package com.jelly.farmhelper.fabric.hud;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.FarmHelperFabricClient;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.module.AutoReconnectFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.BpsTrackerFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.LagDetectorFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.SchedulerFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.UsageStatsFeatureModule;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.state.GameStateHandler;
import com.jelly.farmhelper.fabric.util.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

public final class StatusHudRenderer {
    private StatusHudRenderer() {
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
        boolean macroToggled = FarmHelperFabric.getMacroController().isToggled();
        boolean reconnectPending = AutoReconnectFeatureModule.hasPendingReconnect();
        if (!config.enableStatusHud) {
            if (macroToggled || reconnectPending) {
                renderCompactIndicator(drawContext, client);
            }
            return;
        }

        int x = Math.max(4, config.statusHudX);
        int y = Math.max(4, config.statusHudY);
        int color = 0xFFFFFFFF;

        drawContext.drawText(client.textRenderer,
                "FarmHelper Fabric", x, y, 0xFF55FF55, true);
        y += 10;

        drawContext.drawText(client.textRenderer,
                "Macro: " + FarmHelperFabric.getMacroController().getState(), x, y, color, true);
        y += 10;

        drawContext.drawText(client.textRenderer,
                "Runtime ticks: " + FarmHelperFabric.getMacroController().getRuntimeTicks(), x, y, color, true);
        y += 10;
        drawContext.drawText(client.textRenderer,
                "Macro type: " + config.macroType,
                x, y, color, true);
        y += 10;

        SchedulerFeatureModule.RuntimeState schedulerState = SchedulerFeatureModule.getRuntimeState();
        long schedulerRemainingTicks = SchedulerFeatureModule.getRuntimeRemainingTicks();
        drawContext.drawText(client.textRenderer,
                "Scheduler: " + schedulerState + " (" + formatTicks(schedulerRemainingTicks) + ")",
                x, y, schedulerState == SchedulerFeatureModule.RuntimeState.BREAK ? 0xFF55AAFF : color, true);
        y += 10;

        if (reconnectPending) {
            long remaining = AutoReconnectFeatureModule.getRemainingReconnectTicks(System.currentTimeMillis() / 50L);
            drawContext.drawText(client.textRenderer,
                    "Reconnect: " + formatTicks(remaining) + " ("
                            + AutoReconnectFeatureModule.getCurrentAttempt() + "/"
                            + AutoReconnectFeatureModule.getMaxAttemptsSnapshot() + ")",
                    x, y, 0xFFFFAA55, true);
            y += 10;
        }

        String failsafe = FarmHelperFabric.getFailsafeManager().getActiveFailsafe().map(Enum::name).orElse("NONE");
        drawContext.drawText(client.textRenderer,
                "Failsafe: " + failsafe, x, y, failsafe.equals("NONE") ? color : 0xFFFF5555, true);
        y += 10;
        if (!failsafe.equals("NONE")) {
            drawContext.drawText(client.textRenderer,
                    "Reason: " + FarmHelperFabric.getFailsafeManager().getActiveReason(), x, y, 0xFFFFAA55, true);
            y += 10;
        }

        drawContext.drawText(client.textRenderer,
                "Pattern: " + FarmHelperFabric.getConfigManager().getConfig().macroPattern
                        + " Dir: " + FarmHelperFabricClient.getMovementMacroExecutor().getDirectionName(),
                x, y, color, true);
        y += 10;

        drawContext.drawText(client.textRenderer,
                "Stationary ticks: " + FarmHelperFabricClient.getGameStateTracker().getStationaryTicks(), x, y, color, true);
        y += 10;
        drawContext.drawText(client.textRenderer,
                "Location: " + FarmHelperFabricClient.getGameStateHandler().getLocation()
                        + " Plot: " + FarmHelperFabricClient.getGameStateHandler().getCurrentPlotName(),
                x, y, color, true);
        y += 10;
        drawContext.drawText(client.textRenderer,
                "Pests: " + FarmHelperFabricClient.getGameStateHandler().getTotalPests()
                        + " (top plot " + FarmHelperFabricClient.getGameStateHandler().getMostInfestedPlot() + ")",
                x, y, color, true);
        y += 10;

        drawContext.drawText(client.textRenderer,
                String.format("BPS: %.1f (avg %.1f)%s",
                        BpsTrackerFeatureModule.getCurrentBps(),
                        BpsTrackerFeatureModule.getAverageBps(),
                        BpsTrackerFeatureModule.isPaused() ? " paused" : ""),
                x, y, color, true);
        y += 10;

        drawContext.drawText(client.textRenderer,
                String.format("TPS: %.1f  Lag: %s (%dms)",
                        LagDetectorFeatureModule.getEstimatedTps(),
                        LagDetectorFeatureModule.isLagging() ? "YES" : "NO",
                        LagDetectorFeatureModule.getMillisSincePacket()),
                x, y, LagDetectorFeatureModule.isLagging() ? 0xFFFFAA00 : color, true);
        y += 10;

        if (config.showStatsTitle) {
            drawContext.drawText(client.textRenderer, "Usage Stats", x, y, 0xFFF7DCA4, true);
            y += 10;
        }
        if (config.showStatsSession) {
            drawContext.drawText(client.textRenderer,
                    "Session: " + UsageStatsFeatureModule.formatTicksAsHours(UsageStatsFeatureModule.getSessionMacroTicks()),
                    x, y, color, true);
            y += 10;
        }
        if (config.showStats24H) {
            drawContext.drawText(client.textRenderer,
                    "24h: " + UsageStatsFeatureModule.formatHours(UsageStatsFeatureModule.getActiveHoursInWindow(24L * 60L * 60L * 1000L)),
                    x, y, 0xFF55FF55, true);
            y += 10;
        }
        if (config.showStats7D) {
            drawContext.drawText(client.textRenderer,
                    "7d: " + UsageStatsFeatureModule.formatHours(UsageStatsFeatureModule.getActiveHoursInWindow(7L * 24L * 60L * 60L * 1000L)),
                    x, y, 0xFF55FF55, true);
            y += 10;
        }
        if (config.showStats30D) {
            drawContext.drawText(client.textRenderer,
                    "30d: " + UsageStatsFeatureModule.formatHours(UsageStatsFeatureModule.getActiveHoursInWindow(30L * 24L * 60L * 60L * 1000L)),
                    x, y, 0xFF55FF55, true);
            y += 10;
        }
        if (config.showStatsLifetime) {
            drawContext.drawText(client.textRenderer,
                    "Lifetime: " + UsageStatsFeatureModule.formatTicksAsHours(UsageStatsFeatureModule.getLifetimeMacroTicks()),
                    x, y, 0xFF55FF55, true);
            y += 10;
        }
        if (config.showStatsFailsafes) {
            drawContext.drawText(client.textRenderer,
                    "Failsafes: session " + UsageStatsFeatureModule.getSessionFailsafes()
                            + " / lifetime " + UsageStatsFeatureModule.getLifetimeFailsafeTriggers(),
                    x, y, 0xFFFFAA55, true);
        }
        RenderUtils.renderWorldTextAsHud(drawContext);
    }

    private static void renderCompactIndicator(DrawContext drawContext, MinecraftClient client) {
        MacroState state = FarmHelperFabric.getMacroController().getState();
        String line = "FarmHelper [" + state.name() + "] " + FarmHelperFabricClient.getMovementMacroExecutor().getDirectionName();
        int textWidth = client.textRenderer.getWidth(line);
        int x = Math.max(4, (client.getWindow().getScaledWidth() - textWidth) / 2);
        int y = 6;
        int color = state == MacroState.FARMING ? 0xFF55FF55 : 0xFFFFAA55;
        drawContext.drawText(client.textRenderer, Text.literal(line), x, y, color, true);
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

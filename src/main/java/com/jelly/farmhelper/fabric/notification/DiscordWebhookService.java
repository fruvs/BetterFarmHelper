package com.jelly.farmhelper.fabric.notification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiscordWebhookService {
    private static final long DEBUG_FLUSH_INTERVAL_MS = 3L * 60L * 1000L;
    private static final long DEBUG_MAX_FILE_BYTES = 9L * 1024L * 1024L;
    private static final int DISCORD_CHUNK_CHARS = 1800;
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "farmhelper-webhook");
        thread.setDaemon(true);
        return thread;
    });

    private final Object debugLock = new Object();
    private final StringBuilder debugBuffer = new StringBuilder(16_384);
    private long lastDebugFlushMs;
    private long lastHeartbeatMs;
    private final Path debugRootDir = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("farmhelper");
    private final Path debugArchiveDir = debugRootDir.resolve("archive");
    private final Path debugFile = debugRootDir.resolve("farmhelper-debug-current.log");

    public void onMacroToggled(boolean enabled) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.sendMacroEnableDisableLogs) {
            return;
        }
        sendLog("Macro " + (enabled ? "enabled" : "disabled"), false);
    }

    public void onFailsafeTriggered(FailsafeType type, String reason) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.sendFailsafeLogs) {
            return;
        }
        sendLog("Failsafe triggered: " + type.name() + " | " + reason, false);
    }

    public void onFailsafeCleared(FailsafeType type) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.sendFailsafeLogs) {
            return;
        }
        sendLog("Failsafe cleared: " + type.name(), false);
    }

    public void sendStatusUpdate(MacroState state, long runtimeTicks, int stationaryTicks, String activeFailsafe) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.sendStatusUpdates) {
            return;
        }
        String line = "Status | state=" + state + " runtime_ticks=" + runtimeTicks
                + " stationary_ticks=" + stationaryTicks + " failsafe=" + activeFailsafe;
        sendLog(line, false);
    }

    public void onVisitorEvent(String message, boolean pingEveryone) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.sendVisitorsMacroLogs) {
            return;
        }
        sendLog("Visitors: " + message, pingEveryone);
    }

    public void sendFeatureLog(String message) {
        sendLog("Feature: " + message, false);
        debugTrace("feature", message);
    }

    public void debugTrace(String source, String message) {
        appendDebugLine("INFO", source, message, false, true);
    }

    public void debugCritical(String source, String message) {
        appendDebugLine("CRITICAL", source, message, true, true);
    }

    public void tickDebug(RuntimeSnapshot snapshot, FeatureRuntimeState featureState, FarmHelperConfig config, int actionQueueSize) {
        if (snapshot == null || config == null || (!config.sendWebhookDebugLogs && !config.writeDebugLogsToFile)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastHeartbeatMs >= HEARTBEAT_INTERVAL_MS) {
            lastHeartbeatMs = now;
            appendDebugLine("HEARTBEAT", "runtime", buildHeartbeat(snapshot, featureState, config, actionQueueSize), false, true);
        }
        if (now - lastDebugFlushMs >= DEBUG_FLUSH_INTERVAL_MS || isDebugFileNearLimit()) {
            flushDebugLog("periodic");
        }
    }

    public void sendCurrentDebugLog(String reason) {
        flushDebugLog(reason == null ? "manual" : reason);
    }

    private void sendLog(String message, boolean pingEveryone) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        appendDebugLine("INFO", "webhook", message, false, false);
        if (!config.enableWebhook || !config.sendWebhookLogs) {
            return;
        }

        String webhookUrl = config.webhookUrl == null ? "" : config.webhookUrl.trim();
        if (webhookUrl.isEmpty() || !webhookUrl.startsWith("https://")) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("username", "FarmHelper Fabric");
        payload.addProperty("content", (pingEveryone ? "@everyone " : "") + message);
        sendJson(webhookUrl, payload);
    }

    private void appendDebugLine(String level, String source, String message, boolean critical, boolean includeInWebhookBuffer) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        boolean writeToFile = config.writeDebugLogsToFile;
        boolean sendToWebhook = config.sendWebhookDebugLogs && includeInWebhookBuffer;
        if (!writeToFile && !sendToWebhook) {
            return;
        }
        String line = "[" + TS_FORMAT.format(Instant.now()) + "] "
                + "[" + sanitize(level) + "]"
                + "[" + sanitize(source) + "] "
                + sanitize(message);
        synchronized (debugLock) {
            if (sendToWebhook) {
                debugBuffer.append(line).append('\n');
            }
            if (writeToFile) {
                writeDebugFileLine(line);
            }
        }
        if (critical && config.sendWebhookDebugLogs) {
            sendDebugEmbed("Critical Debug Event", line, 0xE74C3C);
        }
    }

    private void flushDebugLog(String reason) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.enableWebhook || !config.sendWebhookDebugLogs) {
            return;
        }

        String webhookUrl = config.webhookUrl == null ? "" : config.webhookUrl.trim();
        if (webhookUrl.isEmpty() || !webhookUrl.startsWith("https://")) {
            return;
        }

        String payloadText;
        synchronized (debugLock) {
            if (debugBuffer.isEmpty()) {
                return;
            }
            payloadText = debugBuffer.toString();
            debugBuffer.setLength(0);
            lastDebugFlushMs = System.currentTimeMillis();
        }

        String title = "FarmHelper Debug Log Flush (" + sanitize(reason) + ")";
        sendDebugPayloadInChunks(webhookUrl, title, payloadText, 0x3498DB);
    }

    private String buildHeartbeat(RuntimeSnapshot snapshot, FeatureRuntimeState featureState, FarmHelperConfig config, int actionQueueSize) {
        String failsafe = featureState == null || featureState.activeFailsafe == null || featureState.activeFailsafe.isEmpty()
                ? "none"
                : featureState.activeFailsafe.get().name();
        return String.format(Locale.US,
                "macro=%s state=%s rtTicks=%d pos=(%.2f,%.2f,%.2f) yaw=%.1f pitch=%.1f queue=%d screen=\"%s\" failsafe=%s nearSpawn=%s nearRewarp=%s "
                        + "vacuum[range=%.1f dps=%.0f cd=%.1fs] cfg[macroType=%s pattern=%s scheduler=%s visitors=%s pests=%s pestExchange=%s bazaar=%s autoSell=%s failsafe=%s rewarpPts=%d]",
                snapshot.macroToggled,
                snapshot.macroState,
                snapshot.macroRuntimeTicks,
                snapshot.posX, snapshot.posY, snapshot.posZ,
                snapshot.yaw, snapshot.pitch,
                actionQueueSize,
                sanitize(snapshot.screenTitle),
                failsafe,
                snapshot.nearSpawnPoint,
                snapshot.nearRewarpPoint,
                snapshot.vacuumRange,
                snapshot.vacuumDps,
                snapshot.vacuumTrackerCooldownSeconds,
                config.macroType,
                config.macroPattern,
                config.enableScheduler,
                config.visitorsMacro,
                config.enablePestsDestroyer,
                config.autoPestExchange,
                config.autoBazaar,
                config.enableAutoSell,
                config.enableFailsafes,
                config.rewarpPoints == null ? 0 : config.rewarpPoints.size()
        );
    }

    private boolean isDebugFileNearLimit() {
        try {
            if (!Files.exists(debugFile)) {
                return false;
            }
            return Files.size(debugFile) >= DEBUG_MAX_FILE_BYTES;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void writeDebugFileLine(String line) {
        try {
            Files.createDirectories(debugRootDir);
            Files.createDirectories(debugArchiveDir);
            if (Files.exists(debugFile) && Files.size(debugFile) >= DEBUG_MAX_FILE_BYTES) {
                rotateDebugFile();
            }
            Files.writeString(
                    debugFile,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            FarmHelperFabric.LOGGER.warn("Failed writing debug log file", e);
        }
    }

    private void rotateDebugFile() {
        try {
            if (!Files.exists(debugFile)) {
                return;
            }
            String suffix = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault()).format(Instant.now());
            Path rotated = debugArchiveDir.resolve("farmhelper-debug-" + suffix + ".log");
            Files.move(debugFile, rotated, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            FarmHelperFabric.LOGGER.warn("Failed rotating debug log file", e);
        }
    }

    public Path getDebugLogDirectory() {
        return debugRootDir;
    }

    public Path getDebugLogFile() {
        return debugFile;
    }

    private void sendDebugEmbed(String title, String text, int color) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        String webhookUrl = config.webhookUrl == null ? "" : config.webhookUrl.trim();
        if (!config.enableWebhook || !config.sendWebhookDebugLogs || webhookUrl.isBlank()) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("username", "FarmHelper Debug");
        JsonArray embeds = new JsonArray();
        JsonObject embed = new JsonObject();
        embed.addProperty("title", truncate(title, 240));
        embed.addProperty("description", "```" + truncate(text, 3900) + "```");
        embed.addProperty("color", color);
        embeds.add(embed);
        payload.add("embeds", embeds);
        sendJson(webhookUrl, payload);
    }

    private void sendDebugPayloadInChunks(String webhookUrl, String title, String payloadText, int color) {
        String normalized = payloadText == null ? "" : payloadText;
        if (normalized.isBlank()) {
            return;
        }
        int index = 0;
        int part = 1;
        while (index < normalized.length()) {
            int end = Math.min(normalized.length(), index + DISCORD_CHUNK_CHARS);
            String chunk = normalized.substring(index, end);
            JsonObject payload = new JsonObject();
            payload.addProperty("username", "FarmHelper Debug");
            JsonArray embeds = new JsonArray();
            JsonObject embed = new JsonObject();
            embed.addProperty("title", truncate(title + " [part " + part + "]", 240));
            embed.addProperty("description", "```" + truncate(chunk, 3900) + "```");
            embed.addProperty("color", color);
            embeds.add(embed);
            payload.add("embeds", embeds);
            sendJson(webhookUrl, payload);
            index = end;
            part++;
        }
    }

    private void sendJson(String webhookUrl, JsonObject payload) {
        executor.submit(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    FarmHelperFabric.LOGGER.warn("Webhook send returned status {}: {}", status, response.body());
                }
            } catch (IOException | InterruptedException | IllegalArgumentException e) {
                FarmHelperFabric.LOGGER.warn("Failed to send webhook message", e);
            }
        });
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\n", " ").replace("\r", " ").trim();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 3) + "...";
    }
}

package com.jelly.farmhelper.fabric.feature.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Locale;

public class UsageStatsFeatureModule extends AbstractFeatureModule {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STATS_PATH = FabricLoader.getInstance().getConfigDir().resolve("farmhelper-fabric-stats.json");
    private static final long SAMPLE_INTERVAL_TICKS = 20L * 60L;
    private static final int MAX_SAMPLES = 12_000;

    private static volatile long lifetimeMacroTicks;
    private static volatile long lifetimeSessionStarts;
    private static volatile long lifetimeFailsafeTriggers;
    private static volatile long sessionMacroTicks;
    private static volatile long sessionFailsafes;
    private static volatile long lastPersistAtTick;
    private static volatile long lastSampleAtTick;
    private static final ArrayDeque<StatSample> SAMPLES = new ArrayDeque<>();
    private static volatile boolean loaded;

    public UsageStatsFeatureModule(boolean enabled) {
        super("usage_stats_tracker", "Usage Stats Tracker", enabled);
        ensureLoaded();
    }

    @Override
    public void onEnable() {
        ensureLoaded();
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        ensureLoaded();
        if (runtime.macroToggled) {
            lifetimeMacroTicks++;
            sessionMacroTicks++;
            if (runtime.macroRuntimeTicks <= 2) {
                lifetimeSessionStarts++;
            }
        }
        if (runtime.tickCount - lastSampleAtTick >= SAMPLE_INTERVAL_TICKS) {
            lastSampleAtTick = runtime.tickCount;
            addSample(new StatSample(System.currentTimeMillis(), lifetimeMacroTicks, lifetimeFailsafeTriggers));
        }
        if (runtime.tickCount - lastPersistAtTick >= SAMPLE_INTERVAL_TICKS * 5L) {
            lastPersistAtTick = runtime.tickCount;
            persist();
        }
    }

    @Override
    public void onDisconnect() {
        persist();
    }

    @Override
    public void onDisable() {
        persist();
    }

    public static void recordFailsafeTriggered(FailsafeType type) {
        ensureLoaded();
        lifetimeFailsafeTriggers++;
        sessionFailsafes++;
        addSample(new StatSample(System.currentTimeMillis(), lifetimeMacroTicks, lifetimeFailsafeTriggers));
    }

    public static long getLifetimeMacroTicks() {
        ensureLoaded();
        return lifetimeMacroTicks;
    }

    public static long getSessionMacroTicks() {
        return sessionMacroTicks;
    }

    public static long getLifetimeSessionStarts() {
        ensureLoaded();
        return lifetimeSessionStarts;
    }

    public static long getLifetimeFailsafeTriggers() {
        ensureLoaded();
        return lifetimeFailsafeTriggers;
    }

    public static long getSessionFailsafes() {
        return sessionFailsafes;
    }

    public static double getActiveHoursInWindow(long windowMillis) {
        ensureLoaded();
        if (SAMPLES.isEmpty()) {
            return 0.0;
        }
        long now = System.currentTimeMillis();
        StatSample baseline = null;
        StatSample latest = null;
        for (StatSample sample : SAMPLES) {
            if (sample == null) {
                continue;
            }
            if (now - sample.epochMs <= windowMillis) {
                baseline = sample;
                break;
            }
        }
        for (StatSample sample : SAMPLES) {
            latest = sample;
        }
        if (baseline == null || latest == null) {
            return 0.0;
        }
        long tickDelta = Math.max(0L, latest.macroTicks - baseline.macroTicks);
        return tickDelta / 20.0 / 3600.0;
    }

    public static double getFailsafesInWindow(long windowMillis) {
        ensureLoaded();
        if (SAMPLES.isEmpty()) {
            return 0.0;
        }
        long now = System.currentTimeMillis();
        StatSample baseline = null;
        StatSample latest = null;
        for (StatSample sample : SAMPLES) {
            if (sample == null) {
                continue;
            }
            if (now - sample.epochMs <= windowMillis) {
                baseline = sample;
                break;
            }
        }
        for (StatSample sample : SAMPLES) {
            latest = sample;
        }
        if (baseline == null || latest == null) {
            return 0.0;
        }
        return Math.max(0L, latest.failsafeCount - baseline.failsafeCount);
    }

    public static String formatHours(double hours) {
        return String.format(Locale.US, "%.1fh", Math.max(0.0, hours));
    }

    public static String formatTicksAsHours(long ticks) {
        return formatHours(Math.max(0L, ticks) / 20.0 / 3600.0);
    }

    public static void resetSession() {
        sessionMacroTicks = 0L;
        sessionFailsafes = 0L;
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.exists(STATS_PATH)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(STATS_PATH)) {
            PersistedStats persisted = GSON.fromJson(reader, PersistedStats.class);
            if (persisted == null) {
                return;
            }
            lifetimeMacroTicks = Math.max(0L, persisted.lifetimeMacroTicks);
            lifetimeSessionStarts = Math.max(0L, persisted.lifetimeSessionStarts);
            lifetimeFailsafeTriggers = Math.max(0L, persisted.lifetimeFailsafeTriggers);
            if (persisted.samples != null) {
                for (StatSample sample : persisted.samples) {
                    if (sample == null) {
                        continue;
                    }
                    SAMPLES.addLast(sample);
                }
                trimSamples();
            }
        } catch (IOException e) {
            FarmHelperFabric.LOGGER.debug("Failed loading usage stats", e);
        }
    }

    private static synchronized void persist() {
        ensureLoaded();
        try {
            Files.createDirectories(STATS_PATH.getParent());
            PersistedStats persisted = new PersistedStats();
            persisted.lifetimeMacroTicks = lifetimeMacroTicks;
            persisted.lifetimeSessionStarts = lifetimeSessionStarts;
            persisted.lifetimeFailsafeTriggers = lifetimeFailsafeTriggers;
            persisted.samples = new java.util.ArrayList<>(SAMPLES);
            try (Writer writer = Files.newBufferedWriter(STATS_PATH)) {
                GSON.toJson(persisted, writer);
            }
        } catch (IOException e) {
            FarmHelperFabric.LOGGER.debug("Failed persisting usage stats", e);
        }
    }

    private static synchronized void addSample(StatSample sample) {
        if (sample == null) {
            return;
        }
        SAMPLES.addLast(sample);
        trimSamples();
    }

    private static void trimSamples() {
        while (SAMPLES.size() > MAX_SAMPLES) {
            SAMPLES.pollFirst();
        }
        long cutoff = System.currentTimeMillis() - (35L * 24L * 60L * 60L * 1000L);
        while (!SAMPLES.isEmpty() && SAMPLES.peekFirst().epochMs < cutoff) {
            SAMPLES.pollFirst();
        }
    }

    public static final class StatSample {
        public long epochMs;
        public long macroTicks;
        public long failsafeCount;

        public StatSample() {
        }

        public StatSample(long epochMs, long macroTicks, long failsafeCount) {
            this.epochMs = epochMs;
            this.macroTicks = macroTicks;
            this.failsafeCount = failsafeCount;
        }
    }

    private static final class PersistedStats {
        long lifetimeMacroTicks;
        long lifetimeSessionStarts;
        long lifetimeFailsafeTriggers;
        java.util.List<StatSample> samples;
    }
}

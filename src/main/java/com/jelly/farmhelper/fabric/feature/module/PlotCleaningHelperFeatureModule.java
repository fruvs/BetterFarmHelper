package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

import java.util.Locale;

public class PlotCleaningHelperFeatureModule extends MacroExclusiveFeatureModule {
    private enum State {
        IDLE,
        CLEAN_SWEEP,
        FINISH
    }

    private static final String SCYTHE_BLOCK_HINTS = "grass,flower,leaves,fern,vine,bush,double_plant";
    private static final String WOOD_BLOCK_HINTS = "log,wood,stem,hyphae";
    private static final String STONE_BLOCK_HINTS = "stone,cobble,slab,deepslate";

    private State state = State.IDLE;
    private long stateSinceTick = 0L;
    private boolean cleanupActivitySeen;
    private long lastSweepTick = -1L;
    private long nextMineTick = -1L;

    public PlotCleaningHelperFeatureModule(boolean enabled) {
        super("plot_cleaning_helper", "Plot Cleaning Helper", enabled);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        cleanupActivitySeen = false;
        resetState();
    }

    @Override
    public void onDisconnect() {
        super.onDisconnect();
        cleanupActivitySeen = false;
        resetState();
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.plotCleaningHelper) {
            resetState();
            return;
        }
        if (runtime.activeFailsafe.isPresent() || !runtime.macroToggled) {
            return;
        }

        if (!isActionRunning()) {
            tryStart(runtime, config);
            return;
        }

        if (shouldEndAction(runtime.tickCount)) {
            endTimedAction("plot cleaning timeout");
            lastSweepTick = runtime.tickCount;
            resetState();
            return;
        }

        tickState(runtime, config);
    }

    private void tryStart(FeatureRuntimeState runtime, FarmHelperConfig config) {
        if (config.plotCleaningOnlyDuringCleanup && !cleanupActivitySeen) {
            return;
        }
        long cooldownTicks = Math.max(80L, secondsToTicks(config.plotCleaningActionSeconds));
        if (lastSweepTick > 0 && runtime.tickCount - lastSweepTick < cooldownTicks) {
            return;
        }

        if (!beginTimedAction(runtime.tickCount, cooldownTicks + 160L, "plot cleanup sweep")) {
            return;
        }
        setState(State.CLEAN_SWEEP, runtime.tickCount);
        nextMineTick = runtime.tickCount;
    }

    private void tickState(FeatureRuntimeState runtime, FarmHelperConfig config) {
        switch (state) {
            case CLEAN_SWEEP -> {
                if (runtime.tickCount >= nextMineTick) {
                    double radius = Math.max(2.0, Math.min(8.0, config.plotCleaningScanRadius));
                    queueMineNearestBlock(SCYTHE_BLOCK_HINTS, radius, 100L, runtime.tickCount);
                    queueUseHeldItem(runtime.tickCount);
                    if (config.plotCleaningBreakWood) {
                        queueMineNearestBlock(WOOD_BLOCK_HINTS, radius, 110L, runtime.tickCount);
                    }
                    if (config.plotCleaningBreakStone) {
                        queueMineNearestBlock(STONE_BLOCK_HINTS, radius, 110L, runtime.tickCount);
                    }
                    nextMineTick = runtime.tickCount + 8L;
                }

                if (ticksInState(runtime.tickCount) >= secondsToTicks(Math.max(4, config.plotCleaningActionSeconds))) {
                    setState(State.FINISH, runtime.tickCount);
                }
            }
            case FINISH -> {
                lastSweepTick = runtime.tickCount;
                endTimedAction("plot cleaning sweep complete");
                resetState();
            }
            case IDLE -> {
            }
        }
    }

    @Override
    public void onChatMessage(String message) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.plotCleaningHelper || message == null || message.isBlank()) {
            return;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("cleanup")
                && (normalized.contains("started")
                || normalized.contains("begin")
                || normalized.contains("active"))) {
            cleanupActivitySeen = true;
            return;
        }
        if (normalized.contains("cleanup")
                && (normalized.contains("ended")
                || normalized.contains("finished")
                || normalized.contains("complete"))) {
            cleanupActivitySeen = false;
        }
    }

    private long ticksInState(long nowTick) {
        return Math.max(0L, nowTick - stateSinceTick);
    }

    private void setState(State next, long nowTick) {
        state = next;
        stateSinceTick = nowTick;
    }

    private void resetState() {
        state = State.IDLE;
        stateSinceTick = 0L;
        nextMineTick = -1L;
    }
}

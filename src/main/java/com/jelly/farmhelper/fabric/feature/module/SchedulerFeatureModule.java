package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;
import com.jelly.farmhelper.fabric.macro.MacroState;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class SchedulerFeatureModule extends AbstractFeatureModule {
    public enum RuntimeState {
        FARMING,
        BREAK_PENDING_REWARP,
        BREAK,
        DISABLED
    }

    private enum CycleState {
        FARMING,
        BREAK_PENDING_REWARP,
        BREAK
    }

    private CycleState cycleState = CycleState.FARMING;
    private long cycleStartTick = 0L;
    private long pendingBreakSinceTick = 0L;
    private long targetFarmingTicks = 0L;
    private long targetBreakTicks = 0L;
    private boolean inJacobContest;
    private boolean pausedMacroForBreak;
    private static volatile boolean cancelPendingResume;
    private static volatile RuntimeState runtimeState = RuntimeState.DISABLED;
    private static volatile long runtimeRemainingTicks;

    public SchedulerFeatureModule(boolean enabled) {
        super("scheduler", "Scheduler", enabled);
    }

    @Override
    public void onEnable() {
        runtimeState = RuntimeState.FARMING;
        runtimeRemainingTicks = 0L;
        resetCycle(true);
    }

    @Override
    public void onDisable() {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (config.schedulerResetOnDisable) {
            resetCycle(true);
        }
        runtimeState = RuntimeState.DISABLED;
        runtimeRemainingTicks = 0L;
    }

    @Override
    public void onDisconnect() {
        // Keep deterministic reset behavior across world changes.
        resetCycle(true);
        runtimeState = RuntimeState.FARMING;
        runtimeRemainingTicks = 0L;
    }

    @Override
    public void onChatMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("jacob's contest")
                || normalized.contains("starts in")
                || normalized.contains("contest has started")) {
            inJacobContest = true;
            return;
        }
        if (normalized.contains("contest has ended")
                || normalized.contains("jacob's contest is over")
                || normalized.contains("new jacob contest")) {
            inJacobContest = false;
        }
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        if (cancelPendingResume) {
            pausedMacroForBreak = false;
            cancelPendingResume = false;
        }
        inJacobContest = runtime.jacobContestActive;
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.enableScheduler || !runtime.inWorld) {
            runtimeState = RuntimeState.DISABLED;
            runtimeRemainingTicks = 0L;
            return;
        }
        if (!runtime.macroToggled && !pausedMacroForBreak) {
            runtimeState = RuntimeState.DISABLED;
            runtimeRemainingTicks = 0L;
            return;
        }
        if (cycleStartTick == 0L) {
            resetCycle(false);
            cycleStartTick = runtime.tickCount;
        }
        if (targetFarmingTicks <= 0L) {
            targetFarmingTicks = computePhaseTicks(config.schedulerFarmingTimeMinutes, config.schedulerFarmingTimeRandomnessMinutes);
        }
        if (targetBreakTicks <= 0L) {
            targetBreakTicks = computePhaseTicks(config.schedulerBreakTimeMinutes, config.schedulerBreakTimeRandomnessMinutes);
        }

        long now = runtime.tickCount;
        long elapsed = now - cycleStartTick;

        if (cycleState == CycleState.FARMING) {
            runtimeState = RuntimeState.FARMING;
            runtimeRemainingTicks = Math.max(0L, targetFarmingTicks - elapsed);
            if (config.pauseSchedulerDuringJacobsContest && inJacobContest) {
                cycleStartTick = now;
                return;
            }
            if (elapsed >= targetFarmingTicks) {
                if (config.schedulerWaitUntilRewarp && !runtime.nearRewarpPoint) {
                    cycleState = CycleState.BREAK_PENDING_REWARP;
                    pendingBreakSinceTick = now;
                    return;
                }
                enterBreak(now, config);
            }
            return;
        }

        if (cycleState == CycleState.BREAK_PENDING_REWARP) {
            runtimeState = RuntimeState.BREAK_PENDING_REWARP;
            long timeoutTicks = Math.max(20L, config.schedulerWaitForRewarpTimeoutSeconds) * 20L;
            runtimeRemainingTicks = Math.max(0L, timeoutTicks - (now - pendingBreakSinceTick));
            if (runtime.nearRewarpPoint || now - pendingBreakSinceTick >= timeoutTicks) {
                enterBreak(now, config);
            }
            return;
        }

        runtimeState = RuntimeState.BREAK;
        runtimeRemainingTicks = Math.max(0L, targetBreakTicks - elapsed);
        if (cycleState == CycleState.BREAK && elapsed >= targetBreakTicks) {
            cycleState = CycleState.FARMING;
            cycleStartTick = now;
            targetFarmingTicks = computePhaseTicks(config.schedulerFarmingTimeMinutes, config.schedulerFarmingTimeRandomnessMinutes);
            if (pausedMacroForBreak && !FarmHelperFabric.getMacroController().isToggled()) {
                FarmHelperFabric.getMacroController().enable();
            } else if (FarmHelperFabric.getMacroController().getState() != MacroState.FARMING) {
                FarmHelperFabric.getMacroController().resumeFromFeature("scheduler break finished");
            }
            pausedMacroForBreak = false;
        }
    }

    private void enterBreak(long now, FarmHelperConfig config) {
        cycleState = CycleState.BREAK;
        cycleStartTick = now;
        pendingBreakSinceTick = 0L;
        targetBreakTicks = computePhaseTicks(config.schedulerBreakTimeMinutes, config.schedulerBreakTimeRandomnessMinutes);
        if (FarmHelperFabric.getMacroController().isToggled()) {
            pausedMacroForBreak = true;
            FarmHelperFabric.getMacroController().disable();
        } else {
            pausedMacroForBreak = false;
        }
        if (config.schedulerDisconnectDuringBreak) {
            FarmHelperFabric.getClientActionQueue().enqueueCommand("/lobby", now);
        }
    }

    private void resetCycle(boolean clearContestFlag) {
        cycleState = CycleState.FARMING;
        cycleStartTick = 0L;
        pendingBreakSinceTick = 0L;
        targetFarmingTicks = 0L;
        targetBreakTicks = 0L;
        pausedMacroForBreak = false;
        runtimeState = RuntimeState.FARMING;
        runtimeRemainingTicks = 0L;
        if (clearContestFlag) {
            inJacobContest = false;
        }
    }

    private long computePhaseTicks(int baseMinutes, int randomMinutes) {
        int base = Math.max(1, baseMinutes);
        int random = Math.max(0, randomMinutes);
        int delta = random == 0 ? 0 : ThreadLocalRandom.current().nextInt(random + 1);
        return (long) (base + delta) * 60L * 20L;
    }

    public static RuntimeState getRuntimeState() {
        return runtimeState;
    }

    public static long getRuntimeRemainingTicks() {
        return Math.max(0L, runtimeRemainingTicks);
    }

    public static void cancelPendingResume() {
        cancelPendingResume = true;
    }
}

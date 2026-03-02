package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

import java.util.Locale;

public class AutoRepellentFeatureModule extends MacroExclusiveFeatureModule {
    private enum State {
        IDLE,
        OPEN_DESK,
        WAIT_DESK,
        BUY_OR_PICK_REPELLENT,
        CLOSE_MENU,
        USE_REPELLENT,
        FINISH
    }

    private State state = State.IDLE;
    private long stateSinceTick = 0L;
    private boolean repellentExpired;
    private long lastRepellentTick = -1L;

    public AutoRepellentFeatureModule(boolean enabled) {
        super("auto_repellent", "Auto Repellent", enabled);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        repellentExpired = false;
        resetState();
    }

    @Override
    public void onDisconnect() {
        super.onDisconnect();
        repellentExpired = false;
        resetState();
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.autoRepellent) {
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
            endTimedAction("repellent timeout");
            lastRepellentTick = runtime.tickCount;
            resetState();
            return;
        }

        tickState(runtime, config);
    }

    private void tryStart(FeatureRuntimeState runtime, FarmHelperConfig config) {
        if (runtime.pestRepellentActive) {
            repellentExpired = false;
            lastRepellentTick = runtime.tickCount;
            return;
        }
        if (lastRepellentTick <= 0) {
            lastRepellentTick = runtime.tickCount;
        }
        long intervalTicks = secondsToTicks(Math.max(1, config.autoRepellentCheckMinutes) * 60);
        boolean periodicCheckDue = runtime.tickCount - lastRepellentTick >= intervalTicks;
        if (!repellentExpired && !periodicCheckDue) {
            return;
        }

        if (!beginTimedAction(runtime.tickCount, 220L, "refresh pest repellent")) {
            return;
        }
        setState(State.OPEN_DESK, runtime.tickCount);
        FarmHelperFabric.getWebhookService().sendFeatureLog(
                "Auto Repellent cycle started (" + (config.pestRepellentType ? "MAX" : "NORMAL") + ")"
        );
    }

    private void tickState(FeatureRuntimeState runtime, FarmHelperConfig config) {
        switch (state) {
            case OPEN_DESK -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueCommand("/desk", runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 20L) {
                    setState(State.WAIT_DESK, runtime.tickCount);
                }
            }
            case WAIT_DESK -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueWaitForScreen("desk", 140L, 5, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 30L) {
                    setState(State.BUY_OR_PICK_REPELLENT, runtime.tickCount);
                }
            }
            case BUY_OR_PICK_REPELLENT -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueClickSlotMatching(
                            config.pestRepellentType
                                    ? "name:repellent max;lore:pest"
                                    : "name:repellent;lore:pest",
                            80L,
                            5,
                            runtime.tickCount
                    );
                    queueClickSlotMatching("name:repellent;lore:active", 60L, 4, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 25L) {
                    setState(State.CLOSE_MENU, runtime.tickCount);
                }
            }
            case CLOSE_MENU -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueCloseScreen(runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 10L) {
                    setState(State.USE_REPELLENT, runtime.tickCount);
                }
            }
            case USE_REPELLENT -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueUseHeldItem(runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 10L) {
                    setState(State.FINISH, runtime.tickCount);
                }
            }
            case FINISH -> {
                lastRepellentTick = runtime.tickCount;
                repellentExpired = false;
                endTimedAction("repellent cycle complete");
                resetState();
            }
            case IDLE -> {
            }
        }
    }

    @Override
    public void onChatMessage(String message) {
        if (!FarmHelperFabric.getConfigManager().getConfig().autoRepellent || message == null || message.isBlank()) {
            return;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("pest repellent")
                && (normalized.contains("expired")
                || normalized.contains("ran out")
                || normalized.contains("not active"))) {
            repellentExpired = true;
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
    }
}

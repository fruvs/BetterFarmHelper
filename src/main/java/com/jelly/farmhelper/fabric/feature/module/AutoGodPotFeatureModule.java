package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

import java.util.Locale;

public class AutoGodPotFeatureModule extends MacroExclusiveFeatureModule {
    private enum State {
        IDLE,
        OPEN_SOURCE,
        WAIT_MENU,
        PICKUP_POTION,
        CLOSE_MENU,
        USE_POTION,
        FINISH
    }

    private State state = State.IDLE;
    private long stateSinceTick = 0L;
    private boolean godPotExpired;
    private long lastActivationTick = -1L;

    public AutoGodPotFeatureModule(boolean enabled) {
        super("auto_god_pot", "Auto God Pot", enabled);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        godPotExpired = false;
        resetState();
    }

    @Override
    public void onDisconnect() {
        super.onDisconnect();
        godPotExpired = false;
        resetState();
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.autoGodPot) {
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
            endTimedAction("god pot timeout");
            lastActivationTick = runtime.tickCount;
            resetState();
            return;
        }

        tickState(runtime, config);
    }

    private void tryStart(FeatureRuntimeState runtime, FarmHelperConfig config) {
        if (runtime.godPotionActive) {
            godPotExpired = false;
            lastActivationTick = runtime.tickCount;
            return;
        }
        if (lastActivationTick <= 0) {
            lastActivationTick = runtime.tickCount;
        }
        long intervalTicks = secondsToTicks(Math.max(1, config.autoGodPotCheckMinutes) * 60);
        boolean periodicCheckDue = runtime.tickCount - lastActivationTick >= intervalTicks;
        if (!godPotExpired && !periodicCheckDue) {
            return;
        }

        if (!beginTimedAction(runtime.tickCount, 260L, "refresh god pot")) {
            return;
        }
        setState(State.OPEN_SOURCE, runtime.tickCount);
        FarmHelperFabric.getWebhookService().sendFeatureLog("Auto God Pot cycle started");
    }

    private void tickState(FeatureRuntimeState runtime, FarmHelperConfig config) {
        switch (state) {
            case OPEN_SOURCE -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    if (config.autoGodPotFromBackpack) {
                        queueCommand("/bp", runtime.tickCount);
                    } else {
                        queueCommand("/ah", runtime.tickCount);
                    }
                }
                if (ticksInState(runtime.tickCount) >= 20L) {
                    setState(State.WAIT_MENU, runtime.tickCount);
                }
            }
            case WAIT_MENU -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueWaitForScreen(config.autoGodPotFromBackpack ? "backpack" : "auction", 140L, 5, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 30L) {
                    setState(State.PICKUP_POTION, runtime.tickCount);
                }
            }
            case PICKUP_POTION -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueClickSlotMatching("name:god pot;lore:hours", 80L, 5, runtime.tickCount);
                    queueClickSlotMatching("name:god potion;lore:buff", 80L, 5, runtime.tickCount);
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
                    setState(State.USE_POTION, runtime.tickCount);
                }
            }
            case USE_POTION -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueUseHeldItem(runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 10L) {
                    setState(State.FINISH, runtime.tickCount);
                }
            }
            case FINISH -> {
                lastActivationTick = runtime.tickCount;
                godPotExpired = false;
                endTimedAction("god pot cycle complete");
                resetState();
            }
            case IDLE -> {
            }
        }
    }

    @Override
    public void onChatMessage(String message) {
        if (!FarmHelperFabric.getConfigManager().getConfig().autoGodPot || message == null || message.isBlank()) {
            return;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if ((normalized.contains("god potion") || normalized.contains("god pot"))
                && (normalized.contains("expired")
                || normalized.contains("ran out")
                || normalized.contains("no longer active"))) {
            godPotExpired = true;
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

package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;
import com.jelly.farmhelper.fabric.macro.MacroState;

import java.util.Locale;

public class RancherSpeedSetterFeatureModule extends MacroExclusiveFeatureModule {
    private enum State {
        IDLE,
        SEND_COMMAND,
        WAIT_CONFIRM,
        FINISH
    }

    private State state = State.IDLE;
    private long stateSinceTick;
    private long lastAttemptTick = -1L;
    private boolean confirmed;
    private boolean commandWithValueSent;

    public RancherSpeedSetterFeatureModule(boolean enabled) {
        super("rancher_speed_setter", "Rancher Speed Setter", enabled);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetState();
    }

    @Override
    public void onDisconnect() {
        super.onDisconnect();
        resetState();
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if ((!config.autoSetRancherSpeed && !config.useCustomFarmingSpeed)
                || runtime.activeFailsafe.isPresent()
                || !runtime.macroToggled
                || runtime.macroState != MacroState.FARMING) {
            return;
        }

        if (!isActionRunning()) {
            long intervalTicks = secondsToTicks(Math.max(1, config.rancherSpeedCheckMinutes) * 60);
            if (lastAttemptTick > 0 && runtime.tickCount - lastAttemptTick < intervalTicks) {
                return;
            }
            if (!beginTimedAction(runtime.tickCount, 220L, "set rancher speed")) {
                return;
            }
            confirmed = false;
            commandWithValueSent = false;
            setState(State.SEND_COMMAND, runtime.tickCount);
            return;
        }

        if (shouldEndAction(runtime.tickCount)) {
            endTimedAction("rancher speed timeout");
            lastAttemptTick = runtime.tickCount;
            resetState();
            return;
        }
        tickState(runtime, config);
    }

    @Override
    public void onChatMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("rancher")
                && (normalized.contains("set")
                || normalized.contains("speed"))) {
            confirmed = true;
        }
        if (normalized.contains("usage: /setmaxspeed") || normalized.contains("open your rancher")) {
            commandWithValueSent = false;
        }
    }

    private void tickState(FeatureRuntimeState runtime, FarmHelperConfig config) {
        switch (state) {
            case SEND_COMMAND -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    if (!commandWithValueSent) {
                        queueCommand("/setmaxspeed " + Math.max(100, config.farmingSpeed), runtime.tickCount);
                        commandWithValueSent = true;
                    } else {
                        queueCommand("/setmaxspeed", runtime.tickCount);
                    }
                }
                if (ticksInState(runtime.tickCount) >= 16L) {
                    setState(State.WAIT_CONFIRM, runtime.tickCount);
                }
            }
            case WAIT_CONFIRM -> {
                if (confirmed) {
                    setState(State.FINISH, runtime.tickCount);
                    return;
                }
                if (ticksInState(runtime.tickCount) >= 80L && commandWithValueSent) {
                    commandWithValueSent = false;
                    setState(State.SEND_COMMAND, runtime.tickCount);
                } else if (ticksInState(runtime.tickCount) >= 120L) {
                    setState(State.FINISH, runtime.tickCount);
                }
            }
            case FINISH -> {
                endTimedAction("rancher speed flow done");
                lastAttemptTick = runtime.tickCount;
                resetState();
            }
            case IDLE -> {
            }
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
        commandWithValueSent = false;
        confirmed = false;
    }
}

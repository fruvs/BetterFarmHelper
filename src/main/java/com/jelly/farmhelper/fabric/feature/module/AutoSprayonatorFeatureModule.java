package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

import java.util.Locale;

public class AutoSprayonatorFeatureModule extends MacroExclusiveFeatureModule {
    private enum State {
        IDLE,
        SELECT_AND_SPRAY,
        WAIT_RESULT,
        BUY_MATERIAL,
        FINISH
    }

    private State state = State.IDLE;
    private long stateSinceTick;
    private long lastCycleTick = -1L;
    private long pendingPurchaseRequest = -1L;
    private boolean sprayedRecently;
    private boolean missingMaterial;
    private boolean permanentFailure;

    public AutoSprayonatorFeatureModule(boolean enabled) {
        super("auto_sprayonator", "Auto Sprayonator", enabled);
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
        if (!config.autoSprayonator || runtime.activeFailsafe.isPresent()) {
            return;
        }
        if (!runtime.macroToggled || runtime.macroState != com.jelly.farmhelper.fabric.macro.MacroState.FARMING) {
            return;
        }

        if (!isActionRunning()) {
            tryStart(runtime, config);
            return;
        }
        if (shouldEndAction(runtime.tickCount)) {
            endTimedAction("sprayonator timeout");
            lastCycleTick = runtime.tickCount;
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
        if (normalized.startsWith("sprayonator!")
                || normalized.contains("plot was sprayed with that item recently")
                || normalized.contains("was sprayed")) {
            sprayedRecently = true;
        }
        if (normalized.startsWith("you don't have any ")) {
            missingMaterial = true;
        }
        if (normalized.contains("cannot find sprayonator")
                || normalized.contains("cannot use this item")) {
            permanentFailure = true;
        }
    }

    private void tryStart(FeatureRuntimeState runtime, FarmHelperConfig config) {
        long intervalTicks = secondsToTicks(Math.max(1, config.autoSprayonatorCheckMinutes) * 60);
        if (lastCycleTick > 0 && runtime.tickCount - lastCycleTick < intervalTicks) {
            return;
        }
        long budget = Math.max(160L, secondsToTicks(config.autoSprayonatorActionSeconds));
        if (!beginTimedAction(runtime.tickCount, budget + 200L, "sprayonator refresh")) {
            return;
        }
        state = State.SELECT_AND_SPRAY;
        stateSinceTick = runtime.tickCount;
        sprayedRecently = false;
        missingMaterial = false;
        permanentFailure = false;
        pendingPurchaseRequest = -1L;
    }

    private void tickState(FeatureRuntimeState runtime, FarmHelperConfig config) {
        switch (state) {
            case SELECT_AND_SPRAY -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueSelectHotbarItem("sprayonator", runtime.tickCount);
                    queueUseHeldItem(runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 16L) {
                    setState(State.WAIT_RESULT, runtime.tickCount);
                }
            }
            case WAIT_RESULT -> {
                if (permanentFailure || sprayedRecently) {
                    setState(State.FINISH, runtime.tickCount);
                    return;
                }
                if (missingMaterial && config.autoSprayonatorAutoBuyItem) {
                    setState(State.BUY_MATERIAL, runtime.tickCount);
                    return;
                }
                if (ticksInState(runtime.tickCount) >= 80L) {
                    setState(State.FINISH, runtime.tickCount);
                }
            }
            case BUY_MATERIAL -> {
                if (pendingPurchaseRequest <= 0L) {
                    pendingPurchaseRequest = AutoBazaarFeatureModule.queueBuy(
                            config.autoSprayonatorMaterial,
                            Math.max(1, config.autoSprayonatorAutoBuyAmount),
                            0
                    );
                    if (pendingPurchaseRequest <= 0L) {
                        setState(State.FINISH, runtime.tickCount);
                        return;
                    }
                }
                var status = AutoBazaarFeatureModule.requestStatus(pendingPurchaseRequest);
                if (status.isEmpty() || status.get() == AutoBazaarFeatureModule.RequestStatus.QUEUED
                        || status.get() == AutoBazaarFeatureModule.RequestStatus.RUNNING) {
                    return;
                }
                if (status.get() == AutoBazaarFeatureModule.RequestStatus.SUCCEEDED) {
                    sprayedRecently = false;
                    missingMaterial = false;
                    pendingPurchaseRequest = -1L;
                    setState(State.SELECT_AND_SPRAY, runtime.tickCount);
                } else {
                    setState(State.FINISH, runtime.tickCount);
                }
            }
            case FINISH -> {
                endTimedAction("sprayonator cycle completed");
                lastCycleTick = runtime.tickCount;
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
        pendingPurchaseRequest = -1L;
        sprayedRecently = false;
        missingMaterial = false;
        permanentFailure = false;
    }
}

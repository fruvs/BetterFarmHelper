package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

import java.util.Locale;

public class PetSwapperFeatureModule extends MacroExclusiveFeatureModule {
    private enum State {
        IDLE,
        OPEN_PETS,
        WAIT_PETS_SCREEN,
        SELECT_TARGET_PET,
        CLOSE_SCREEN,
        FINISH
    }

    private State state = State.IDLE;
    private long stateSinceTick = 0L;
    private long lastSwapTick = -1L;
    private boolean petSwappedForContest;
    private String pendingPetName = "";

    public PetSwapperFeatureModule(boolean enabled) {
        super("pet_swapper", "Pet Swapper", enabled);
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
        if (!config.enablePetSwapper) {
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
            endTimedAction("pet swap timeout");
            lastSwapTick = runtime.tickCount;
            resetState();
            return;
        }

        tickState(runtime);
    }

    private void tryStart(FeatureRuntimeState runtime, FarmHelperConfig config) {
        String target = pendingPetName == null ? "" : pendingPetName.trim();
        if (target.isEmpty()) {
            return;
        }
        long cooldownTicks = Math.max(80L, secondsToTicks(config.petSwapperActionSeconds));
        if (lastSwapTick > 0 && runtime.tickCount - lastSwapTick < cooldownTicks) {
            return;
        }

        if (!beginTimedAction(runtime.tickCount, cooldownTicks + 220L, "pet swap")) {
            return;
        }

        setState(State.OPEN_PETS, runtime.tickCount);
        FarmHelperFabric.getWebhookService().sendFeatureLog("Pet Swapper cycle started for " + target);
    }

    private void tickState(FeatureRuntimeState runtime) {
        switch (state) {
            case OPEN_PETS -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueCommand("/pets", runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 18L) {
                    setState(State.WAIT_PETS_SCREEN, runtime.tickCount);
                }
            }
            case WAIT_PETS_SCREEN -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueWaitForScreen("pets", 140L, 4, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 24L) {
                    setState(State.SELECT_TARGET_PET, runtime.tickCount);
                }
            }
            case SELECT_TARGET_PET -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    String target = pendingPetName == null ? "" : pendingPetName.trim();
                    queueClickSlotMatching("name:" + target, 100L, 6, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 20L) {
                    setState(State.CLOSE_SCREEN, runtime.tickCount);
                }
            }
            case CLOSE_SCREEN -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueCloseScreen(runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 8L) {
                    setState(State.FINISH, runtime.tickCount);
                }
            }
            case FINISH -> {
                String activeTarget = pendingPetName == null ? "" : pendingPetName.trim();
                FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
                if (!activeTarget.isEmpty() && activeTarget.equalsIgnoreCase(config.petSwapperName)) {
                    petSwappedForContest = true;
                }
                if (!activeTarget.isEmpty()
                        && !config.petSwapperRestoreName.isBlank()
                        && activeTarget.equalsIgnoreCase(config.petSwapperRestoreName)) {
                    petSwappedForContest = false;
                }
                pendingPetName = "";
                lastSwapTick = runtime.tickCount;
                endTimedAction("pet swap complete");
                resetTransientState();
            }
            case IDLE -> {
            }
        }
    }

    @Override
    public void onChatMessage(String message) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.enablePetSwapper || message == null || message.isBlank()) {
            return;
        }
        String normalized = message.toLowerCase(Locale.ROOT);

        if (normalized.contains("jacob")
                && (normalized.contains("contest has started") || normalized.contains("contest started"))) {
            if (!config.petSwapperName.isBlank()) {
                pendingPetName = config.petSwapperName.trim();
            }
            return;
        }

        if (normalized.contains("jacob")
                && (normalized.contains("contest has ended") || normalized.contains("contest ended"))) {
            if (config.petSwapperSwapBackAfterContest
                    && petSwappedForContest
                    && !config.petSwapperRestoreName.isBlank()) {
                pendingPetName = config.petSwapperRestoreName.trim();
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

    private void resetTransientState() {
        state = State.IDLE;
        stateSinceTick = 0L;
    }

    private void resetState() {
        resetTransientState();
        petSwappedForContest = false;
        pendingPetName = "";
    }
}

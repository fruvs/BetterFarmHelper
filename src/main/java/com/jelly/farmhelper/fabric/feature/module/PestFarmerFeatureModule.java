package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;
import com.jelly.farmhelper.fabric.macro.MacroState;

import java.util.Locale;

public class PestFarmerFeatureModule extends MacroExclusiveFeatureModule {
    private enum State {
        IDLE,
        SWAP_BIOHAZARD,
        WAIT_BIOHAZARD_SWAP,
        SET_SPAWN,
        WAIT_SET_SPAWN_CONFIRM,
        KILL_PESTS,
        WARP_BACK_TO_GARDEN,
        WAIT_WARP_BACK,
        SWAP_FERMENTO,
        WAIT_FERMENTO_SWAP,
        FINISH
    }

    private static final String PEST_NAMES =
            "beetle,cricket,earthworm,fly,locust,mite,mosquito,moth,rat,slug,praying mantis,firefly,dragonfly";

    private State state = State.IDLE;
    private long stateSinceTick;
    private long pestSpawnedAtMs = -1L;
    private long lastRunTick = -1L;
    private long wardrobeRequestId = -1L;
    private long pestSweepUntilTick = -1L;
    private long spawnCommandAtTick = -1L;
    private long warpCommandAtTick = -1L;
    private long lastParticleProbeTick = -1L;
    private int retries;
    private boolean spawnConfirmed;
    private boolean spawnRejected;
    private boolean warpBackConfirmed;
    private boolean warpBackRejected;
    private boolean vacuumUseHeld;
    private double activeVacuumRange = 5.0;

    public PestFarmerFeatureModule(boolean enabled) {
        super("pest_farmer", "Pest Farmer", enabled);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        releaseVacuumUse(0L);
        resetState();
        spawnConfirmed = false;
        spawnRejected = false;
        warpBackConfirmed = false;
        warpBackRejected = false;
    }

    @Override
    public void onDisconnect() {
        super.onDisconnect();
        releaseVacuumUse(0L);
        resetState();
        spawnConfirmed = false;
        spawnRejected = false;
        warpBackConfirmed = false;
        warpBackRejected = false;
    }

    @Override
    public void cancelActiveAction(String reason) {
        super.cancelActiveAction(reason);
        lastRunTick = -1L;
        releaseVacuumUse(0L);
        spawnConfirmed = false;
        spawnRejected = false;
        warpBackConfirmed = false;
        warpBackRejected = false;
        resetState();
    }

    @Override
    public void onChatMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("yuck!")
                || normalized.contains("eww!")
                || normalized.contains("gross!")
                || normalized.contains("pest has spawned")
                || normalized.contains("pests are eating")) {
            pestSpawnedAtMs = System.currentTimeMillis();
        }
        if (normalized.contains("your spawn location has been set")) {
            spawnConfirmed = true;
            spawnRejected = false;
        }
        if (normalized.contains("you cannot set your spawn here")
                || normalized.contains("cannot set spawn")
                || normalized.contains("spawn point is obstructed")) {
            spawnRejected = true;
            spawnConfirmed = false;
        }
        if (normalized.contains("warped to your garden")
                || normalized.contains("teleported to your garden")
                || normalized.contains("sending to server garden")) {
            warpBackConfirmed = true;
            warpBackRejected = false;
        }
        if (normalized.contains("cannot warp right now")
                || normalized.contains("you can't warp right now")
                || normalized.contains("you cannot use this command while")
                || normalized.contains("this command is on cooldown")) {
            warpBackRejected = true;
            warpBackConfirmed = false;
        }
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.pestFarmer || runtime.activeFailsafe.isPresent()) {
            if (isActionRunning()) {
                releaseVacuumUse(runtime.tickCount);
                endTimedAction("pest farmer paused");
                resetState();
            }
            return;
        }
        if (!runtime.macroToggled || runtime.macroState != MacroState.FARMING) {
            if (isActionRunning()) {
                releaseVacuumUse(runtime.tickCount);
                endTimedAction("pest farmer stopped");
                resetState();
            }
            return;
        }

        if (!isActionRunning()) {
            tryStart(runtime, config);
            return;
        }
        if (shouldEndAction(runtime.tickCount)) {
            releaseVacuumUse(runtime.tickCount);
            endTimedAction("pest farmer timeout");
            lastRunTick = runtime.tickCount;
            resetState();
            return;
        }
        tickState(runtime, config);
    }

    private void tryStart(FeatureRuntimeState runtime, FarmHelperConfig config) {
        if (pestSpawnedAtMs <= 0) {
            return;
        }
        if (lastRunTick > 0 && runtime.tickCount - lastRunTick < 200L) {
            return;
        }
        long waitMs = Math.max(1, config.pestFarmerWaitSeconds) * 1000L;
        if (System.currentTimeMillis() - pestSpawnedAtMs < waitMs) {
            return;
        }

        long budget = secondsToTicks(Math.max(12, config.pestFarmerWaitSeconds + 16));
        if (!beginTimedAction(runtime.tickCount, budget, "pest farmer handling")) {
            return;
        }
        setState(State.SWAP_BIOHAZARD, runtime.tickCount);
        wardrobeRequestId = -1L;
        retries = 0;
        spawnConfirmed = false;
        spawnRejected = false;
        warpBackConfirmed = false;
        warpBackRejected = false;
        pestSweepUntilTick = -1L;
        spawnCommandAtTick = -1L;
        warpCommandAtTick = -1L;
    }

    private void tickState(FeatureRuntimeState runtime, FarmHelperConfig config) {
        switch (state) {
            case SWAP_BIOHAZARD -> {
                if (config.pestFarmerBiohazardSlot >= 1
                        && config.pestFarmerBiohazardSlot <= 18
                        && config.autoWardrobe
                        && wardrobeRequestId <= 0L) {
                    String equipment = config.pestFarmerSwapEquipment ? config.pestFarmerBiohazardEquipment : "";
                    wardrobeRequestId = AutoWardrobeFeatureModule.queueSwap(config.pestFarmerBiohazardSlot, equipment);
                    setState(State.WAIT_BIOHAZARD_SWAP, runtime.tickCount);
                } else {
                    setState(State.SET_SPAWN, runtime.tickCount);
                }
            }
            case WAIT_BIOHAZARD_SWAP -> {
                if (wardrobeRequestId <= 0L) {
                    setState(State.SET_SPAWN, runtime.tickCount);
                    return;
                }
                var status = AutoWardrobeFeatureModule.requestStatus(wardrobeRequestId);
                if (status.isPresent() && status.get() == AutoWardrobeFeatureModule.RequestStatus.SUCCEEDED) {
                    wardrobeRequestId = -1L;
                    retries = 0;
                    setState(State.SET_SPAWN, runtime.tickCount);
                } else if (status.isPresent() && status.get() == AutoWardrobeFeatureModule.RequestStatus.FAILED) {
                    if (retries++ < 1) {
                        wardrobeRequestId = AutoWardrobeFeatureModule.queueSwap(config.pestFarmerBiohazardSlot, config.pestFarmerBiohazardEquipment);
                    } else {
                        wardrobeRequestId = -1L;
                        retries = 0;
                        setState(State.SET_SPAWN, runtime.tickCount);
                    }
                } else if (ticksInState(runtime.tickCount) > 140L) {
                    if (retries++ < 1) {
                        wardrobeRequestId = AutoWardrobeFeatureModule.queueSwap(config.pestFarmerBiohazardSlot, config.pestFarmerBiohazardEquipment);
                        setState(State.WAIT_BIOHAZARD_SWAP, runtime.tickCount);
                    } else {
                        wardrobeRequestId = -1L;
                        retries = 0;
                        setState(State.SET_SPAWN, runtime.tickCount);
                    }
                }
            }
            case SET_SPAWN -> {
                if (config.pestFarmerSetSpawn) {
                    if (runtime.screenOpen) {
                        if (ticksInState(runtime.tickCount) % 12L == 0L) {
                            queueCloseScreen(runtime.tickCount);
                        }
                        return;
                    }
                    if (spawnCommandAtTick < 0L) {
                        spawnConfirmed = false;
                        spawnRejected = false;
                        queueCommand("/setspawn", runtime.tickCount);
                        spawnCommandAtTick = runtime.tickCount;
                    }
                    if (runtime.tickCount - spawnCommandAtTick >= 8L) {
                        retries = 0;
                        setState(State.WAIT_SET_SPAWN_CONFIRM, runtime.tickCount);
                    }
                    return;
                }
                setState(State.KILL_PESTS, runtime.tickCount);
            }
            case WAIT_SET_SPAWN_CONFIRM -> {
                if (spawnConfirmed) {
                    retries = 0;
                    setState(State.KILL_PESTS, runtime.tickCount);
                    return;
                }
                if (spawnRejected) {
                    if (retries++ < 1) {
                        spawnRejected = false;
                        spawnCommandAtTick = -1L;
                        setState(State.SET_SPAWN, runtime.tickCount);
                    } else {
                        setState(State.KILL_PESTS, runtime.tickCount);
                    }
                    return;
                }
                if (ticksInState(runtime.tickCount) >= 70L) {
                    setState(State.KILL_PESTS, runtime.tickCount);
                }
            }
            case KILL_PESTS -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    pestSweepUntilTick = runtime.tickCount + Math.max(80L, secondsToTicks(Math.max(3, config.pestFarmerWaitSeconds)));
                    if (config.pestFarmerKillPests) {
                        queueSelectHotbarItem("vacuum", runtime.tickCount);
                        activeVacuumRange = resolveVacuumRangeFromRuntime(runtime);
                        setVacuumUse(runtime.tickCount, true);
                        queueAttackNearestEntity(PEST_NAMES, Math.max(3.8, activeVacuumRange), 90L, runtime.tickCount);
                    }
                }
                if (config.pestFarmerKillPests && ticksInState(runtime.tickCount) > 0 && ticksInState(runtime.tickCount) % 18L == 0) {
                    queueAttackNearestEntity(PEST_NAMES, Math.max(3.8, activeVacuumRange), 90L, runtime.tickCount);
                }
                if (config.pestFarmerKillPests
                        && ticksInState(runtime.tickCount) > 0
                        && ticksInState(runtime.tickCount) % 24L == 0
                        && runtime.tickCount - lastParticleProbeTick >= 24L) {
                    queueTapAttackKey(runtime.tickCount);
                    lastParticleProbeTick = runtime.tickCount;
                }
                if (runtime.tickCount >= pestSweepUntilTick) {
                    retries = 0;
                    setState(State.WARP_BACK_TO_GARDEN, runtime.tickCount);
                }
            }
            case WARP_BACK_TO_GARDEN -> {
                if (runtime.screenOpen && ticksInState(runtime.tickCount) % 12L == 0L) {
                    queueCloseScreen(runtime.tickCount);
                    return;
                }
                if (warpCommandAtTick < 0L || (warpBackRejected && runtime.tickCount - warpCommandAtTick >= 20L && retries < 2)) {
                    queueCommand("/warp garden", runtime.tickCount);
                    FarmHelperFabric.getFailsafeManager().suppressPacketChecks(120L, 80L, 40L, "pest farmer return warp");
                    warpCommandAtTick = runtime.tickCount;
                    warpBackRejected = false;
                }
                if (runtime.tickCount - warpCommandAtTick >= 12L) {
                    setState(State.WAIT_WARP_BACK, runtime.tickCount);
                }
            }
            case WAIT_WARP_BACK -> {
                if (warpBackConfirmed) {
                    retries = 0;
                    warpBackConfirmed = false;
                    setState(State.SWAP_FERMENTO, runtime.tickCount);
                    return;
                }
                if (warpBackRejected) {
                    if (retries++ < 2) {
                        setState(State.WARP_BACK_TO_GARDEN, runtime.tickCount);
                    } else {
                        retries = 0;
                        setState(State.SWAP_FERMENTO, runtime.tickCount);
                    }
                    return;
                }
                if (ticksInState(runtime.tickCount) >= 50L) {
                    retries = 0;
                    setState(State.SWAP_FERMENTO, runtime.tickCount);
                }
            }
            case SWAP_FERMENTO -> {
                if (config.pestFarmerFermentoSlot >= 1
                        && config.pestFarmerFermentoSlot <= 18
                        && config.autoWardrobe) {
                    retries = 0;
                    String equipment = config.pestFarmerSwapEquipment ? config.pestFarmerFermentoEquipment : "";
                    wardrobeRequestId = AutoWardrobeFeatureModule.queueSwap(config.pestFarmerFermentoSlot, equipment);
                    setState(State.WAIT_FERMENTO_SWAP, runtime.tickCount);
                } else {
                    setState(State.FINISH, runtime.tickCount);
                }
            }
            case WAIT_FERMENTO_SWAP -> {
                if (wardrobeRequestId <= 0L) {
                    setState(State.FINISH, runtime.tickCount);
                    return;
                }
                var status = AutoWardrobeFeatureModule.requestStatus(wardrobeRequestId);
                if (status.isPresent() && status.get() == AutoWardrobeFeatureModule.RequestStatus.SUCCEEDED) {
                    wardrobeRequestId = -1L;
                    retries = 0;
                    setState(State.FINISH, runtime.tickCount);
                } else if (status.isPresent() && status.get() == AutoWardrobeFeatureModule.RequestStatus.FAILED) {
                    if (retries++ < 1) {
                        String equipment = config.pestFarmerSwapEquipment ? config.pestFarmerFermentoEquipment : "";
                        wardrobeRequestId = AutoWardrobeFeatureModule.queueSwap(config.pestFarmerFermentoSlot, equipment);
                    } else {
                        wardrobeRequestId = -1L;
                        retries = 0;
                        setState(State.FINISH, runtime.tickCount);
                    }
                } else if (ticksInState(runtime.tickCount) > 140L) {
                    if (retries++ < 1) {
                        String equipment = config.pestFarmerSwapEquipment ? config.pestFarmerFermentoEquipment : "";
                        wardrobeRequestId = AutoWardrobeFeatureModule.queueSwap(config.pestFarmerFermentoSlot, equipment);
                        setState(State.WAIT_FERMENTO_SWAP, runtime.tickCount);
                    } else {
                        wardrobeRequestId = -1L;
                        retries = 0;
                        setState(State.FINISH, runtime.tickCount);
                    }
                }
            }
            case FINISH -> {
                releaseVacuumUse(runtime.tickCount);
                endTimedAction("pest farmer cycle complete");
                lastRunTick = runtime.tickCount;
                pestSpawnedAtMs = -1L;
                spawnConfirmed = false;
                spawnRejected = false;
                warpBackConfirmed = false;
                warpBackRejected = false;
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
        if (state == State.KILL_PESTS && next != State.KILL_PESTS) {
            releaseVacuumUse(nowTick);
        }
        if (next != State.SET_SPAWN) {
            spawnCommandAtTick = -1L;
        }
        if (next != State.WARP_BACK_TO_GARDEN) {
            warpCommandAtTick = -1L;
        }
        state = next;
        stateSinceTick = nowTick;
    }

    private void resetState() {
        state = State.IDLE;
        stateSinceTick = 0L;
        wardrobeRequestId = -1L;
        pestSweepUntilTick = -1L;
        spawnCommandAtTick = -1L;
        warpCommandAtTick = -1L;
        lastParticleProbeTick = -1L;
        retries = 0;
        activeVacuumRange = 5.0;
        vacuumUseHeld = false;
    }

    private double resolveVacuumRangeFromRuntime(FeatureRuntimeState runtime) {
        if (runtime != null && runtime.vacuumRange > 0) {
            return runtime.vacuumRange;
        }
        return 5.0;
    }

    private void setVacuumUse(long tick, boolean enabled) {
        if (vacuumUseHeld == enabled) {
            return;
        }
        queueSetUseKey(enabled, tick);
        vacuumUseHeld = enabled;
    }

    private void releaseVacuumUse(long tick) {
        if (!vacuumUseHeld) {
            return;
        }
        queueSetUseKey(false, tick);
        vacuumUseHeld = false;
    }
}

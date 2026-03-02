package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

import java.util.Locale;

public class AutoComposterFeatureModule extends MacroExclusiveFeatureModule {
    private enum State {
        IDLE,
        TELEPORT_TO_BARN,
        MOVE_TO_COMPOSTER,
        INTERACT_COMPOSTER,
        WAIT_MENU,
        REFILL_RESOURCES,
        CLOSE_MENU,
        FINISH
    }

    private static final String COMPOSTER_ENTITY_NAMES = "composter";

    private State state = State.IDLE;
    private long stateSinceTick = 0L;
    private boolean composterAttentionNeeded;
    private long lastComposterTick = -1L;

    public AutoComposterFeatureModule(boolean enabled) {
        super("auto_composter", "Auto Composter", enabled);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        composterAttentionNeeded = false;
        resetState();
    }

    @Override
    public void onDisconnect() {
        super.onDisconnect();
        composterAttentionNeeded = false;
        resetState();
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.autoComposter) {
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
            endTimedAction("composter timeout");
            lastComposterTick = runtime.tickCount;
            resetState();
            return;
        }

        tickState(runtime, config);
    }

    private void tryStart(FeatureRuntimeState runtime, FarmHelperConfig config) {
        if (lastComposterTick <= 0) {
            lastComposterTick = runtime.tickCount;
        }
        long periodicCheckTicks = 30L * 60L * 20L;
        boolean periodicCheckDue = runtime.tickCount - lastComposterTick >= periodicCheckTicks;
        if (!composterAttentionNeeded && !periodicCheckDue) {
            return;
        }

        if (!beginTimedAction(runtime.tickCount, 360L, "check composter")) {
            return;
        }

        setState(State.TELEPORT_TO_BARN, runtime.tickCount);
        FarmHelperFabric.getWebhookService().sendFeatureLog("Auto Composter cycle started");
    }

    private void tickState(FeatureRuntimeState runtime, FarmHelperConfig config) {
        switch (state) {
            case TELEPORT_TO_BARN -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    if (config.autoComposterAutosellBeforeFilling && config.enableAutoSell) {
                        queueCommand(config.autoSellMarketTypeNpc ? "/trades" : "/bz", runtime.tickCount);
                    }
                    queueCommand("/tptoplot barn", runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 30L) {
                    setState(State.MOVE_TO_COMPOSTER, runtime.tickCount);
                }
            }
            case MOVE_TO_COMPOSTER -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueMoveTo(
                            config.composterX + 0.5,
                            config.composterY + 0.1,
                            config.composterZ + 0.5,
                            1.8,
                            220L,
                            runtime.tickCount
                    );
                    queueMoveToEntity(COMPOSTER_ENTITY_NAMES, 4.0, 200L, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 90L) {
                    setState(State.INTERACT_COMPOSTER, runtime.tickCount);
                }
            }
            case INTERACT_COMPOSTER -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueInteractNearestEntity(COMPOSTER_ENTITY_NAMES, 4.0, 120L, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 25L) {
                    setState(State.WAIT_MENU, runtime.tickCount);
                }
            }
            case WAIT_MENU -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueWaitForScreen("composter", 180L, 5, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 35L) {
                    setState(State.REFILL_RESOURCES, runtime.tickCount);
                }
            }
            case REFILL_RESOURCES -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueClickSlotMatching("name:refill;lore:composter", 70L, 4, runtime.tickCount);
                    queueClickSlotMatching("name:organic;lore:matter", 70L, 4, runtime.tickCount);
                    queueClickSlotMatching("name:fuel;lore:composter", 70L, 4, runtime.tickCount);
                    queueClickSlotMatching("name:collect;lore:output", 70L, 3, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 35L) {
                    setState(State.CLOSE_MENU, runtime.tickCount);
                }
            }
            case CLOSE_MENU -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueCloseScreen(runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 10L) {
                    setState(State.FINISH, runtime.tickCount);
                }
            }
            case FINISH -> {
                composterAttentionNeeded = false;
                lastComposterTick = runtime.tickCount;
                endTimedAction("composter cycle complete");
                resetState();
            }
            case IDLE -> {
            }
        }
    }

    @Override
    public void onChatMessage(String message) {
        if (!FarmHelperFabric.getConfigManager().getConfig().autoComposter || message == null || message.isBlank()) {
            return;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("composter")
                && (normalized.contains("out of fuel")
                || normalized.contains("out of organic matter")
                || normalized.contains("needs fuel")
                || normalized.contains("needs organic matter"))) {
            composterAttentionNeeded = true;
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

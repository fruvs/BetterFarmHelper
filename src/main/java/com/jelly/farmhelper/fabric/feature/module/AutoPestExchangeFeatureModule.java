package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoPestExchangeFeatureModule extends MacroExclusiveFeatureModule {
    private enum State {
        IDLE,
        TELEPORT_TO_DESK,
        MOVE_TO_DESK,
        INTERACT_PHILLIP,
        WAIT_MENU,
        EMPTY_VACUUM,
        WAIT_CONFIRM_CHAT,
        CLOSE_MENU,
        FINISH
    }

    private static final Pattern PESTS_PATTERN = Pattern.compile("(\\d+)\\s+pests?", Pattern.CASE_INSENSITIVE);
    private static final String PHILLIP_NAMES = "phillip,philip";

    private State state = State.IDLE;
    private long stateSinceTick = 0L;
    private int vacuumPests;
    private long lastExchangeTick = -1L;
    private int retries;
    private String lastFailureReason = "";
    private boolean exchangeConfirmed;
    private boolean exchangeRejected;
    private boolean phillipLocked;

    public AutoPestExchangeFeatureModule(boolean enabled) {
        super("auto_pest_exchange", "Auto Pest Exchange", enabled);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        vacuumPests = 0;
        resetState();
    }

    @Override
    public void onDisconnect() {
        super.onDisconnect();
        vacuumPests = 0;
        resetState();
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.autoPestExchange) {
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
            fail("pest exchange timeout");
            endTimedAction("pest exchange timeout");
            lastExchangeTick = runtime.tickCount;
            resetState();
            return;
        }

        if (phillipLocked) {
            fail("phillip not unlocked");
            if (state != State.FINISH) {
                setState(State.FINISH, runtime.tickCount, true);
            }
            return;
        }
        if (exchangeRejected) {
            fail(lastFailureReason.isBlank() ? "exchange rejected" : lastFailureReason);
            if (state != State.FINISH) {
                setState(State.FINISH, runtime.tickCount, true);
            }
            return;
        }

        tickState(runtime, config);
    }

    private void tryStart(FeatureRuntimeState runtime, FarmHelperConfig config) {
        if (vacuumPests < Math.max(1, config.autoPestExchangeMinPests)) {
            return;
        }
        if (config.autoPestExchangeOnlyStartRelevant && !(runtime.nearSpawnPoint || runtime.nearRewarpPoint)) {
            return;
        }

        long actionTicks = Math.max(120L, secondsToTicks(config.autoPestExchangeActionSeconds));
        if (lastExchangeTick > 0 && runtime.tickCount - lastExchangeTick < actionTicks) {
            return;
        }

        int retryLimit = Math.max(1, config.autoPestExchangeRetryLimit);
        long actionBudget = actionTicks + (long) retryLimit * 120L + 260L;
        if (!beginTimedAction(runtime.tickCount, actionBudget, "exchange vacuum pests")) {
            return;
        }

        retries = 0;
        exchangeConfirmed = false;
        exchangeRejected = false;
        phillipLocked = false;
        lastFailureReason = "";
        setState(State.TELEPORT_TO_DESK, runtime.tickCount, true);
        if (config.logAutoPestExchangeEvents) {
            FarmHelperFabric.getWebhookService().sendFeatureLog(
                    "Auto Pest Exchange started with " + vacuumPests + " vacuum pests"
            );
        }
    }

    private void tickState(FeatureRuntimeState runtime, FarmHelperConfig config) {
        switch (state) {
            case TELEPORT_TO_DESK -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueCommand("/tptoplot barn", runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 30L) {
                    setState(State.MOVE_TO_DESK, runtime.tickCount, true);
                }
            }
            case MOVE_TO_DESK -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueMoveTo(
                            config.pestExchangeDeskX + 0.5,
                            config.pestExchangeDeskY + 0.1,
                            config.pestExchangeDeskZ + 0.5,
                            1.8,
                            220L,
                            runtime.tickCount
                    );
                    queueMoveToEntity(PHILLIP_NAMES, 4.0, 200L, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 80L) {
                    setState(State.INTERACT_PHILLIP, runtime.tickCount, true);
                }
            }
            case INTERACT_PHILLIP -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueInteractNearestEntity(PHILLIP_NAMES, 4.2, 140L, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 26L) {
                    setState(State.WAIT_MENU, runtime.tickCount, true);
                }
            }
            case WAIT_MENU -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueWaitForScreen("pest", 160L, 5, runtime.tickCount);
                }
                if (runtime.screenOpen && runtime.screenTitle != null
                        && runtime.screenTitle.toLowerCase(Locale.ROOT).contains("pest")) {
                    setState(State.EMPTY_VACUUM, runtime.tickCount, true);
                    return;
                }
                if (ticksInState(runtime.tickCount) >= 36L) {
                    if (retry(config, runtime.tickCount, State.INTERACT_PHILLIP, "pesthunter menu did not open")) {
                        return;
                    }
                    setState(State.FINISH, runtime.tickCount, true);
                }
            }
            case EMPTY_VACUUM -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueClickSlotMatching("name:empty vacuum bag;lore:pests", 80L, 6, runtime.tickCount);
                    queueClickSlotMatching("name:empty;lore:vacuum", 80L, 4, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 20L) {
                    setState(State.WAIT_CONFIRM_CHAT, runtime.tickCount, true);
                }
            }
            case WAIT_CONFIRM_CHAT -> {
                if (exchangeConfirmed) {
                    setState(State.CLOSE_MENU, runtime.tickCount, true);
                    return;
                }
                if (ticksInState(runtime.tickCount) >= 70L) {
                    if (retry(config, runtime.tickCount, State.INTERACT_PHILLIP, "exchange confirmation not received")) {
                        return;
                    }
                    fail("exchange confirmation timeout");
                    setState(State.CLOSE_MENU, runtime.tickCount, true);
                }
            }
            case CLOSE_MENU -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueCloseScreen(runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 10L) {
                    setState(State.FINISH, runtime.tickCount, true);
                }
            }
            case FINISH -> {
                if (exchangeConfirmed) {
                    vacuumPests = 0;
                }
                endTimedAction("pest exchange complete");
                lastExchangeTick = runtime.tickCount;
                resetState();
            }
            case IDLE -> {
            }
        }
    }

    private boolean retry(FarmHelperConfig config, long nowTick, State fallbackState, String reason) {
        int retryLimit = Math.max(1, config.autoPestExchangeRetryLimit);
        if (retries < retryLimit) {
            retries++;
            setState(fallbackState, nowTick, false);
            return true;
        }
        fail(reason);
        return false;
    }

    private void fail(String reason) {
        boolean alreadyFailed = exchangeRejected;
        if (lastFailureReason.isBlank()) {
            lastFailureReason = reason == null ? "unknown error" : reason;
        }
        if (!alreadyFailed && FarmHelperFabric.getConfigManager().getConfig().logAutoPestExchangeEvents) {
            FarmHelperFabric.getWebhookService().sendFeatureLog(
                    "Auto Pest Exchange failed: " + lastFailureReason
            );
        }
        exchangeRejected = true;
    }

    @Override
    public void onChatMessage(String message) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.autoPestExchange || message == null || message.isBlank()) {
            return;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (!(normalized.contains("vacuum")
                || normalized.contains("pest exchange")
                || normalized.contains("pests in bag")
                || normalized.contains("phillip"))) {
            return;
        }

        Matcher matcher = PESTS_PATTERN.matcher(normalized);
        if (matcher.find()) {
            try {
                vacuumPests = Math.max(vacuumPests, Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // Keep previous value.
            }
        }

        if (normalized.contains("thanks for the")
                || normalized.contains("emptied the vacuum")
                || normalized.contains("emptied your vacuum")
                || normalized.contains("already emptied the vacuum")
                || normalized.contains("you've exchanged enough pests")) {
            exchangeConfirmed = true;
            exchangeRejected = false;
            return;
        }

        if (normalized.contains("you haven't unlocked")
                || normalized.contains("take this skymart vacuum")
                || normalized.contains("pest outbreak around here")) {
            phillipLocked = true;
            return;
        }

        if (normalized.contains("failed to empty")
                || normalized.contains("cannot exchange")
                || normalized.contains("cannot use this command")
                || normalized.contains("you cannot do that right now")
                || normalized.contains("not enough pests")) {
            exchangeRejected = true;
            lastFailureReason = normalized;
        }
    }

    private long ticksInState(long nowTick) {
        return Math.max(0L, nowTick - stateSinceTick);
    }

    private void setState(State next, long nowTick, boolean resetRetries) {
        state = next;
        stateSinceTick = nowTick;
        if (resetRetries && next == State.TELEPORT_TO_DESK) {
            retries = 0;
        }
    }

    private void resetState() {
        state = State.IDLE;
        stateSinceTick = 0L;
        retries = 0;
        exchangeConfirmed = false;
        exchangeRejected = false;
        phillipLocked = false;
        lastFailureReason = "";
    }
}

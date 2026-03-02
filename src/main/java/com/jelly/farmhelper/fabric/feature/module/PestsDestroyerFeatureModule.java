package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;
import com.jelly.farmhelper.fabric.state.RuntimeGuards;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PestsDestroyerFeatureModule extends MacroExclusiveFeatureModule {
    private enum State {
        IDLE,
        CHECK_START_POINT,
        TRAVEL_TO_BARN,
        SWAP_ARMOR,
        OPEN_DESK,
        OPEN_PLOTS,
        TELEPORT_TO_PLOT,
        WAIT_FOR_TELEPORT,
        TRACK_CLUSTER,
        MOVE_TO_PEST_ZONE,
        HUNT_PESTS,
        VERIFY_REMAINING,
        FINISH
    }

    private static final Pattern PEST_COUNT_PATTERN = Pattern.compile("(\\d+)\\s+pests?", Pattern.CASE_INSENSITIVE);
    private static final String PEST_NAMES_CSV = "beetle,cricket,earthworm,fly,locust,mite,mosquito,moth,rat,slug,praying mantis,firefly,dragonfly";
    private static final long VACUUM_COOLDOWN_BACKOFF_MS = 4500L;

    /** Max ticks to wait for a GUI screen to appear after sending a command. */
    private static final long SCREEN_WAIT_TIMEOUT_TICKS = 80L;
    /** Min ticks to wait after screen opens before clicking inside it. */
    private static final long SCREEN_CLICK_DELAY_TICKS = 8L;
    /** Max ticks to wait for the queue to drain after clicking slots. */
    private static final long QUEUE_DRAIN_TIMEOUT_TICKS = 60L;
    /** Max ticks to wait for teleport to complete. */
    private static final long TELEPORT_WAIT_TICKS = 50L;

    private State state = State.IDLE;
    private long stateSinceTick = 0L;
    private int queuedPests;
    private long lastActionTick = -1L;
    private long lastVacuumUseTick = -1L;
    private long nextHuntTick = -1L;
    private long vacuumCooldownUntilMs = 0L;
    private int passCount;
    private int stateRetries;
    private int plotResolveRetries;
    private int teleportRetries;
    private int cycleFailures;
    private int targetPlot;
    private int lastKnownInfestedPlot = -1;
    private boolean seenKillSignalThisPass;
    private boolean manualTriggerRequested;
    private double activeVacuumRange = 5.0;
    private long lastParticleProbeTick = -1L;
    private boolean vacuumUseHeld;

    public PestsDestroyerFeatureModule(boolean enabled) {
        super("pests_destroyer", "Pests Destroyer", enabled);
    }

    public void requestManualTrigger() {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        int threshold = Math.max(1, config.startKillingPestsAt);
        queuedPests = Math.max(queuedPests, threshold);
        manualTriggerRequested = true;
        RuntimeGuards.clearGlobalStopLatch();
        lastActionTick = -1L;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        queuedPests = 0;
        manualTriggerRequested = false;
        releaseVacuumUse(0L);
        resetState();
    }

    @Override
    public void onDisconnect() {
        super.onDisconnect();
        queuedPests = 0;
        manualTriggerRequested = false;
        releaseVacuumUse(0L);
        resetState();
    }

    @Override
    public void cancelActiveAction(String reason) {
        super.cancelActiveAction(reason);
        queuedPests = 0;
        manualTriggerRequested = false;
        lastActionTick = -1L;
        releaseVacuumUse(0L);
        resetState();
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.enablePestsDestroyer) {
            if (isActionRunning()) {
                endTimedAction("pests destroyer disabled");
            }
            releaseVacuumUse(runtime.tickCount);
            resetState();
            return;
        }
        queuedPests = Math.max(queuedPests, Math.max(0, runtime.pestsInTablist));
        if (runtime.mostInfestedPlot > 0) {
            lastKnownInfestedPlot = runtime.mostInfestedPlot;
        }
        if (runtime.guiInfestedPlot > 0) {
            lastKnownInfestedPlot = runtime.guiInfestedPlot;
        }
        if (runtime.activeFailsafe.isPresent()) {
            if (isActionRunning()) {
                releaseVacuumUse(runtime.tickCount);
            }
            return;
        }

        if (!isActionRunning()) {
            tryStart(runtime, config);
            return;
        }

        if (shouldEndAction(runtime.tickCount)) {
            endTimedAction("pests timeout");
            lastActionTick = runtime.tickCount;
            queuedPests = 0;
            manualTriggerRequested = false;
            releaseVacuumUse(runtime.tickCount);
            closeScreenIfOpen(runtime, runtime.tickCount);
            resetState();
            return;
        }

        tickState(runtime, config);
    }

    private void tryStart(FeatureRuntimeState runtime, FarmHelperConfig config) {
        if (RuntimeGuards.isGlobalStopLatched() && !manualTriggerRequested) {
            return;
        }
        boolean canRun = runtime.macroToggled || config.pestsDestroyerAfkInfiniteMode || manualTriggerRequested;
        if (!canRun) {
            return;
        }
        if (!manualTriggerRequested
                && config.pestsDestroyerStartOnlyOnRewarpOrSpawn
                && !(runtime.nearSpawnPoint || runtime.nearRewarpPoint)) {
            return;
        }

        queuedPests = Math.max(queuedPests, Math.max(0, runtime.pestsInTablist));
        int threshold = Math.max(1, config.startKillingPestsAt);
        if (!manualTriggerRequested && queuedPests < threshold) {
            return;
        }

        long actionTicks = Math.max(secondsToTicks(config.pestsDestroyerActionSeconds), 180L);
        if (lastActionTick > 0 && runtime.tickCount - lastActionTick < actionTicks) {
            return;
        }

        int maxPasses = Math.max(1, config.pestsDestroyerMaxPasses);
        long actionBudget = actionTicks + (long) maxPasses * 120L + 260L;
        if (!beginTimedAction(runtime.tickCount, actionBudget, "pest threshold reached")) {
            return;
        }

        passCount = 0;
        stateRetries = 0;
        plotResolveRetries = 0;
        teleportRetries = 0;
        cycleFailures = 0;
        targetPlot = -1;
        seenKillSignalThisPass = false;
        manualTriggerRequested = false;
        setState(State.CHECK_START_POINT, runtime.tickCount, true);
        FarmHelperFabric.getWebhookService().sendFeatureLog(
                "Pests Destroyer cycle started with queued=" + queuedPests + ", maxPasses=" + maxPasses
        );
    }

    private void tickState(FeatureRuntimeState runtime, FarmHelperConfig config) {
        switch (state) {
            case CHECK_START_POINT -> {
                if (isEntryTick(runtime.tickCount)) {
                    if (config.pestsDestroyerStartOnlyOnRewarpOrSpawn
                            && !(runtime.nearSpawnPoint || runtime.nearRewarpPoint)) {
                        queueCommand("/warp garden", runtime.tickCount);
                    }
                }
                if (ticksInState(runtime.tickCount) >= 20L) {
                    setState(State.TRAVEL_TO_BARN, runtime.tickCount, true);
                }
            }
            case TRAVEL_TO_BARN -> {
                if (isEntryTick(runtime.tickCount)) {
                    if (runtime.currentPlot != 0) {
                        queueCommand("/tptoplot barn", runtime.tickCount);
                    }
                }
                if (ticksInState(runtime.tickCount) >= 30L) {
                    setState(State.SWAP_ARMOR, runtime.tickCount, true);
                }
            }
            case SWAP_ARMOR -> {
                if (isEntryTick(runtime.tickCount)) {
                    if (config.pestFarmerSwapEquipment || config.autoWardrobe) {
                        int preferredSlot = Math.max(1, config.autoWardrobePreferredSlot);
                        if (config.pestFarmerBiohazardSlot > 0) {
                            preferredSlot = config.pestFarmerBiohazardSlot;
                        }
                        queueCommand("/wardrobe", runtime.tickCount);
                        queueWaitForScreen("wardrobe", 90L, 2, runtime.tickCount);
                        queueClickSlotMatching("name:" + preferredSlot, 70L, 2, runtime.tickCount);
                        queueCloseScreen(runtime.tickCount);
                    }
                }
                // Wait for queue to drain before moving on.
                if (ticksInState(runtime.tickCount) >= 10L && isQueueEmpty()) {
                    setState(State.OPEN_DESK, runtime.tickCount, true);
                } else if (ticksInState(runtime.tickCount) >= 80L) {
                    // Timeout — force advance.
                    closeScreenIfOpen(runtime, runtime.tickCount);
                    setState(State.OPEN_DESK, runtime.tickCount, true);
                }
            }
            case OPEN_DESK -> {
                // If we already know the infested plot from tablist, skip the desk entirely.
                if (runtime.mostInfestedPlot > 0 || runtime.guiInfestedPlot > 0 || lastKnownInfestedPlot > 0) {
                    setState(State.TELEPORT_TO_PLOT, runtime.tickCount, true);
                    return;
                }

                if (isEntryTick(runtime.tickCount)) {
                    // Clear any leftover actions to avoid queue confusion.
                    FarmHelperFabric.getClientActionQueue().clear();
                    queueCommand("/desk", runtime.tickCount);
                }

                // Reactively wait for the Desk GUI to appear. Don't transition on a timer;
                // wait until we actually see the screen, mimicking the old 1.8.9 approach.
                boolean deskOpen = isScreenOpen(runtime, "desk");

                if (deskOpen && ticksInState(runtime.tickCount) >= SCREEN_CLICK_DELAY_TICKS) {
                    // Desk screen is open, proceed to click Configure Plots.
                    setState(State.OPEN_PLOTS, runtime.tickCount, true);
                    return;
                }

                if (ticksInState(runtime.tickCount) >= SCREEN_WAIT_TIMEOUT_TICKS) {
                    closeScreenIfOpen(runtime, runtime.tickCount);
                    int retryLimit = Math.max(2, config.pestsDestroyerRetryLimit);
                    if (stateRetries < retryLimit) {
                        stateRetries++;
                        FarmHelperFabric.getWebhookService().debugTrace(
                                "feature",
                                "pests_destroyer desk screen timeout, retry " + stateRetries + "/" + retryLimit
                        );
                        setState(State.OPEN_DESK, runtime.tickCount, false);
                    } else {
                        FarmHelperFabric.getWebhookService().sendFeatureLog(
                                "Pests Destroyer: desk screen never appeared after " + retryLimit + " retries"
                        );
                        setState(State.FINISH, runtime.tickCount, true);
                    }
                }
            }
            case OPEN_PLOTS -> {
                if (runtime.guiInfestedPlot > 0) {
                    lastKnownInfestedPlot = runtime.guiInfestedPlot;
                    closeScreenIfOpen(runtime, runtime.tickCount);
                    setState(State.TELEPORT_TO_PLOT, runtime.tickCount, true);
                    return;
                }
                // Open the Configure Plots page once, then wait for runtime GUI parsing to resolve the target plot.
                if (isEntryTick(runtime.tickCount)) {
                    FarmHelperFabric.getClientActionQueue().clear();
                    queueClickSlotMatching("name:configure plots", 60L, 3, runtime.tickCount);
                }

                boolean configurePlotsOpen = isScreenOpen(runtime, "configure plots");
                if (!configurePlotsOpen && ticksInState(runtime.tickCount) >= SCREEN_WAIT_TIMEOUT_TICKS) {
                    int retryLimit = Math.max(1, config.pestsDestroyerRetryLimit);
                    if (stateRetries < retryLimit) {
                        stateRetries++;
                        FarmHelperFabric.getWebhookService().debugTrace(
                                "feature",
                                "pests_destroyer configure plots screen timeout, retry " + stateRetries + "/" + retryLimit
                        );
                        setState(State.OPEN_DESK, runtime.tickCount, false);
                    } else {
                        FarmHelperFabric.getWebhookService().sendFeatureLog(
                                "Pests Destroyer: configure plots screen did not open after " + retryLimit + " retries"
                        );
                        setState(State.FINISH, runtime.tickCount, true);
                    }
                    return;
                }

                // Give GUI parsing a short window to resolve infested plots from card lore.
                if (configurePlotsOpen && ticksInState(runtime.tickCount) >= QUEUE_DRAIN_TIMEOUT_TICKS) {
                    closeScreenIfOpen(runtime, runtime.tickCount);
                    setState(State.TELEPORT_TO_PLOT, runtime.tickCount, true);
                }
            }
            case TELEPORT_TO_PLOT -> {
                if (isEntryTick(runtime.tickCount)) {
                    targetPlot = resolveTargetPlot(runtime);
                    FarmHelperFabric.getWebhookService().debugTrace(
                            "feature",
                            "pests_destroyer target plot resolved=" + targetPlot
                                    + " runtimeMost=" + runtime.mostInfestedPlot
                                    + " guiMost=" + runtime.guiInfestedPlot
                                    + " currentPlot=" + runtime.currentPlot
                                    + " queuedPests=" + queuedPests
                                    + " plotResolveRetries=" + plotResolveRetries
                    );
                    if (targetPlot > 0) {
                        stateRetries = 0;
                        plotResolveRetries = 0;
                        teleportRetries = 0;
                        if (runtime.currentPlot == targetPlot) {
                            setState(State.TRACK_CLUSTER, runtime.tickCount, true);
                            return;
                        }
                        queueCommand("/plottp " + targetPlot, runtime.tickCount);
                        setState(State.WAIT_FOR_TELEPORT, runtime.tickCount, true);
                    } else if (runtime.currentPlot > 0) {
                        // Desk interaction may have teleported us even if tablist parsing lags behind.
                        setState(State.TRACK_CLUSTER, runtime.tickCount, true);
                    } else {
                        int retryLimit = Math.max(1, config.pestsDestroyerRetryLimit);
                        if (plotResolveRetries < retryLimit) {
                            plotResolveRetries++;
                            // Clear the queue before retrying desk to avoid stale actions.
                            FarmHelperFabric.getClientActionQueue().clear();
                            FarmHelperFabric.getWebhookService().debugTrace(
                                    "feature",
                                    "pests_destroyer plot resolve retry " + plotResolveRetries + "/" + retryLimit
                            );
                            setState(State.OPEN_DESK, runtime.tickCount, false);
                        } else {
                            FarmHelperFabric.getClientActionQueue().clear();
                            FarmHelperFabric.getWebhookService().sendFeatureLog(
                                    "Pests Destroyer: unable to resolve infested plot after " + retryLimit
                                            + " retries; ending cycle"
                            );
                            setState(State.FINISH, runtime.tickCount, true);
                        }
                    }
                }
            }
            case WAIT_FOR_TELEPORT -> {
                if (ticksInState(runtime.tickCount) >= 10L && targetPlot > 0 && runtime.currentPlot == targetPlot) {
                    setState(State.TRACK_CLUSTER, runtime.tickCount, true);
                    return;
                }

                // If teleport did not complete in time, retry limited times before aborting the cycle.
                if (ticksInState(runtime.tickCount) >= TELEPORT_WAIT_TICKS) {
                    if (targetPlot > 0 && runtime.currentPlot != targetPlot) {
                        int retryLimit = Math.max(1, config.pestsDestroyerRetryLimit);
                        if (teleportRetries < retryLimit) {
                            teleportRetries++;
                            queueCommand("/plottp " + targetPlot, runtime.tickCount);
                            FarmHelperFabric.getWebhookService().debugTrace(
                                    "feature",
                                    "pests_destroyer teleport retry " + teleportRetries + "/" + retryLimit
                                            + " targetPlot=" + targetPlot + " currentPlot=" + runtime.currentPlot
                            );
                            setState(State.WAIT_FOR_TELEPORT, runtime.tickCount, false);
                        } else {
                            FarmHelperFabric.getWebhookService().sendFeatureLog(
                                    "Pests Destroyer: failed to teleport to plot " + targetPlot
                                            + " after " + retryLimit + " retries"
                            );
                            setState(State.FINISH, runtime.tickCount, true);
                        }
                        return;
                    }
                    setState(State.TRACK_CLUSTER, runtime.tickCount, true);
                }
            }
            case TRACK_CLUSTER -> {
                if (isEntryTick(runtime.tickCount)) {
                    double radius = Math.max(8.0, config.pestsDestroyerOnTrackRadius);
                    if (config.pestsDestroyerOnTheTrack) {
                        queueFlyToEntity(PEST_NAMES_CSV, radius, 170L, runtime.tickCount);
                    } else {
                        queueMoveToEntity(PEST_NAMES_CSV, radius, 170L, runtime.tickCount);
                    }
                }
                if (ticksInState(runtime.tickCount) >= 36L) {
                    setState(State.MOVE_TO_PEST_ZONE, runtime.tickCount, true);
                }
            }
            case MOVE_TO_PEST_ZONE -> {
                if (isEntryTick(runtime.tickCount)) {
                    double approachRadius = Math.max(9.0, config.pestsDestroyerOnTrackRadius + 1.5);
                    if (config.pestsDestroyerOnTheTrack) {
                        queueFlyToEntity(PEST_NAMES_CSV, approachRadius, 220L, runtime.tickCount);
                    } else {
                        queueMoveToEntity(PEST_NAMES_CSV, approachRadius, 220L, runtime.tickCount);
                    }
                }
                if (ticksInState(runtime.tickCount) >= 70L) {
                    setState(State.HUNT_PESTS, runtime.tickCount, true);
                }
            }
            case HUNT_PESTS -> {
                if (isEntryTick(runtime.tickCount)) {
                    passCount++;
                    seenKillSignalThisPass = false;
                    nextHuntTick = runtime.tickCount;
                    queueSelectHotbarItem("vacuum", runtime.tickCount);
                    setVacuumUse(runtime.tickCount, true);
                    activeVacuumRange = resolveActiveVacuumRange(runtime, config);
                    if (config.debugMode) {
                        FarmHelperFabric.getWebhookService().debugTrace(
                                "feature",
                                String.format(
                                        Locale.US,
                                        "pests_destroyer hunt pass=%d range=%.2f dps=%.1f trackerCd=%.2fs",
                                        passCount,
                                        activeVacuumRange,
                                        runtime.vacuumDps,
                                        runtime.vacuumTrackerCooldownSeconds
                                )
                        );
                    }
                }

                long vacuumIntervalTicks = resolveVacuumIntervalTicks(runtime, config);
                boolean cooldownElapsed = System.currentTimeMillis() >= vacuumCooldownUntilMs;
                boolean queueReady = FarmHelperFabric.getClientActionQueue().size() <= 1;
                boolean intervalElapsed = lastVacuumUseTick < 0 || runtime.tickCount - lastVacuumUseTick >= vacuumIntervalTicks;
                if (runtime.tickCount >= nextHuntTick && cooldownElapsed && queueReady && intervalElapsed) {
                    queueAttackNearestEntity(
                            PEST_NAMES_CSV,
                            Math.max(3.8, activeVacuumRange),
                            Math.max(100L, vacuumIntervalTicks * 4L),
                            runtime.tickCount
                    );
                    lastVacuumUseTick = runtime.tickCount;
                    nextHuntTick = runtime.tickCount + vacuumIntervalTicks;
                } else if (runtime.tickCount >= nextHuntTick) {
                    nextHuntTick = runtime.tickCount + 6L;
                }
                if (!seenKillSignalThisPass
                        && queueReady
                        && runtime.tickCount - lastParticleProbeTick >= Math.max(4L, Math.round(Math.max(0.2, runtime.vacuumTrackerCooldownSeconds) * 20.0))
                        && (lastVacuumUseTick < 0 || runtime.tickCount - lastVacuumUseTick >= 10L)) {
                    queueTapAttackKey(runtime.tickCount);
                    lastParticleProbeTick = runtime.tickCount;
                }

                long huntTicks = secondsToTicks(Math.max(4, config.pestsDestroyerActionSeconds));
                if (ticksInState(runtime.tickCount) >= huntTicks) {
                    setState(State.VERIFY_REMAINING, runtime.tickCount, true);
                }
            }
            case VERIFY_REMAINING -> {
                if (isEntryTick(runtime.tickCount)) {
                    if (queuedPests <= 0) {
                        setState(State.FINISH, runtime.tickCount, true);
                        return;
                    }
                    if (runtime.pestsInTablist == 0) {
                        queuedPests = 0;
                        setState(State.FINISH, runtime.tickCount, true);
                        return;
                    }
                    int maxPasses = Math.max(1, config.pestsDestroyerMaxPasses);
                    if (passCount >= maxPasses) {
                        setState(State.FINISH, runtime.tickCount, true);
                        return;
                    }
                    if (runtime.mostInfestedPlot > 0
                            && runtime.mostInfestedPlot != runtime.currentPlot
                            && runtime.pestsInTablist > 0) {
                        setState(State.TELEPORT_TO_PLOT, runtime.tickCount, false);
                        return;
                    }

                    if (!seenKillSignalThisPass) {
                        int retryLimit = Math.max(1, config.pestsDestroyerRetryLimit);
                        if (stateRetries < retryLimit) {
                            stateRetries++;
                            setState(State.MOVE_TO_PEST_ZONE, runtime.tickCount, false);
                            return;
                        }
                        cycleFailures++;
                    } else {
                        stateRetries = 0;
                    }

                    setState(State.MOVE_TO_PEST_ZONE, runtime.tickCount, true);
                }
            }
            case FINISH -> {
                queuedPests = Math.max(queuedPests, Math.max(0, runtime.pestsInTablist));
                if (queuedPests > 0) {
                    FarmHelperFabric.getWebhookService().sendFeatureLog(
                            "Pests Destroyer finished after " + passCount + " pass(es), remaining estimate=" + queuedPests
                    );
                }
                queuedPests = Math.max(0, queuedPests);
                closeScreenIfOpen(runtime, runtime.tickCount);
                endTimedAction("pests cycle complete");
                lastActionTick = runtime.tickCount;
                resetState();
            }
            case IDLE -> {
            }
        }
    }

    @Override
    public void onChatMessage(String message) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.enablePestsDestroyer || message == null || message.isBlank()) {
            return;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("cooldown") && (normalized.contains("vacuum") || state == State.HUNT_PESTS)) {
            vacuumCooldownUntilMs = Math.max(vacuumCooldownUntilMs, System.currentTimeMillis() + VACUUM_COOLDOWN_BACKOFF_MS);
        }
        if (!normalized.contains("pest")) {
            return;
        }

        Matcher matcher = PEST_COUNT_PATTERN.matcher(normalized);
        if (matcher.find()) {
            try {
                queuedPests = Math.max(queuedPests, Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                queuedPests++;
            }
        } else if (normalized.contains("killed") || normalized.contains("defeated") || normalized.contains("sucked up")) {
            queuedPests = Math.max(0, queuedPests - 1);
            seenKillSignalThisPass = true;
        } else if (normalized.contains("vacuumed") || normalized.contains("caught")) {
            queuedPests = Math.max(0, queuedPests - 1);
            seenKillSignalThisPass = true;
        } else if (normalized.contains("no pests left") || normalized.contains("there are no pests")) {
            queuedPests = 0;
        } else {
            queuedPests++;
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private long ticksInState(long nowTick) {
        return Math.max(0L, nowTick - stateSinceTick);
    }

    private boolean isEntryTick(long nowTick) {
        return ticksInState(nowTick) <= 1L;
    }

    private void setState(State next, long nowTick, boolean resetRetries) {
        State previous = state;
        if (state == State.HUNT_PESTS && next != State.HUNT_PESTS) {
            releaseVacuumUse(nowTick);
        }
        state = next;
        stateSinceTick = nowTick;
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (config.debugMode && previous != next) {
            FarmHelperFabric.getWebhookService().debugTrace(
                    "feature-state",
                    "pests_destroyer " + previous + " -> " + next
                            + " retries=" + stateRetries
                            + " plot=" + targetPlot
                            + " queued=" + queuedPests
                            + " queueSize=" + FarmHelperFabric.getClientActionQueue().size()
            );
        }
        if (resetRetries) {
            stateRetries = 0;
        }
    }

    private void resetState() {
        state = State.IDLE;
        stateSinceTick = 0L;
        nextHuntTick = -1L;
        lastVacuumUseTick = -1L;
        vacuumCooldownUntilMs = 0L;
        passCount = 0;
        stateRetries = 0;
        plotResolveRetries = 0;
        teleportRetries = 0;
        cycleFailures = 0;
        targetPlot = -1;
        seenKillSignalThisPass = false;
        manualTriggerRequested = false;
        activeVacuumRange = 5.0;
        lastParticleProbeTick = -1L;
        vacuumUseHeld = false;
    }

    /**
     * Reactively checks whether a handled screen with the given title keyword is currently open.
     * Uses FeatureRuntimeState.screenTitle instead of MinecraftClient (which is client-only).
     */
    private boolean isScreenOpen(FeatureRuntimeState runtime, String titleKeyword) {
        if (runtime == null || !runtime.screenOpen || runtime.screenTitle == null) {
            return false;
        }
        return runtime.screenTitle.toLowerCase(Locale.ROOT)
                .contains(titleKeyword.toLowerCase(Locale.ROOT));
    }

    /** Checks whether the action queue has no pending actions. */
    private boolean isQueueEmpty() {
        return FarmHelperFabric.getClientActionQueue().size() == 0;
    }

    /**
     * Queues a CLOSE_CURRENT_SCREEN action if a screen appears to be open.
     * Uses the action queue so it doesn't require client-only imports.
     */
    private void closeScreenIfOpen(FeatureRuntimeState runtime, long tick) {
        if (runtime != null && runtime.screenOpen) {
            queueCloseScreen(tick);
        }
    }

    private double resolveActiveVacuumRange(FeatureRuntimeState runtime, FarmHelperConfig config) {
        if (runtime != null && runtime.vacuumRange > 0.0) {
            return runtime.vacuumRange;
        }
        if (config == null) {
            return 5.0;
        }
        double trackedRadius = Math.max(3.5, config.pestsDestroyerOnTrackRadius);
        return Math.max(5.0, trackedRadius - 1.5);
    }

    private int resolveTargetPlot(FeatureRuntimeState runtime) {
        if (runtime == null) {
            return lastKnownInfestedPlot > 0 ? lastKnownInfestedPlot : -1;
        }
        if (runtime.mostInfestedPlot > 0) {
            return runtime.mostInfestedPlot;
        }
        if (lastKnownInfestedPlot > 0) {
            return lastKnownInfestedPlot;
        }
        return -1;
    }

    private long resolveVacuumIntervalTicks(FeatureRuntimeState runtime, FarmHelperConfig config) {
        int configuredTrackPersist = config == null ? 30 : Math.max(20, config.pestsDestroyerOnTrackPersistTicks);
        long trackerCooldownTicks = runtime == null
                ? 20L
                : Math.max(4L, Math.round(Math.max(0.2, runtime.vacuumTrackerCooldownSeconds) * 20.0));
        long persistDriven = Math.max(12L, configuredTrackPersist / 2L);
        return Math.max(12L, Math.max(persistDriven, trackerCooldownTicks));
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

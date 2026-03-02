package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VisitorsFeatureModule extends MacroExclusiveFeatureModule {
    private enum State {
        IDLE,
        OPEN_VISITOR_MENU,
        MOVE_TO_VISITOR,
        INTERACT_VISITOR,
        WAIT_VISITOR_SCREEN,
        EVALUATE_VISITOR,
        OPEN_BAZAAR_BUY,
        BUY_REQUIREMENTS,
        RETURN_TO_VISITOR,
        HANDLE_OFFER,
        HANDLE_REJECT,
        CLOSE_SCREEN,
        NEXT_VISITOR,
        FINISH
    }

    private static final Pattern VISITOR_COUNT_PATTERN = Pattern.compile("(\\d+)\\s+visitors?", Pattern.CASE_INSENSITIVE);
    private static final Pattern VISITOR_NAME_PATTERN = Pattern.compile(
            "(?:new\\s+visitor|visitor\\s+arrived|visitor)\\s*[:\\-]?\\s*([a-z0-9 '\\-]{2,})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NPC_PREFIX_PATTERN = Pattern.compile("\\[npc]\\s+([^:]+):", Pattern.CASE_INSENSITIVE);
    private static final Pattern COINS_PATTERN = Pattern.compile("([0-9][0-9,]*)\\s+coins", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEED_ITEM_PATTERN = Pattern.compile("(?:need|requires?)\\s+(\\d+)x?\\s+([a-z0-9 '\\-]{2,})", Pattern.CASE_INSENSITIVE);

    private State state = State.IDLE;
    private long stateSinceTick = 0L;
    private long pendingVisitors;
    private long lastServeTick = -1L;
    private int stateRetries;
    private int cycleFailures;
    private int servedThisCycle;
    private int cycleTargetVisitors;
    private String currentVisitorName = "";
    private final ArrayDeque<String> visitorNameQueue = new ArrayDeque<>();
    private final List<String> denyList = new ArrayList<>();
    private final List<String> allowList = new ArrayList<>();
    private boolean shouldRejectVisitor;
    private boolean activeOfferLooksExpensive;
    private boolean attemptedAutoBuyThisVisitor;
    private String requestedItemName = "";
    private int requestedItemCount;
    private int availableRequestedItemCount;
    private boolean missingRequestedItems;

    public VisitorsFeatureModule(boolean enabled) {
        super("visitors_macro", "Visitors Macro", enabled);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        pendingVisitors = 0L;
        visitorNameQueue.clear();
        resetState();
    }

    @Override
    public void onDisconnect() {
        super.onDisconnect();
        pendingVisitors = 0L;
        visitorNameQueue.clear();
        resetState();
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.visitorsMacro) {
            resetState();
            return;
        }
        if (runtime.activeFailsafe.isPresent()) {
            return;
        }

        if (!isActionRunning()) {
            tryStart(runtime, config);
            return;
        }

        if (shouldEndAction(runtime.tickCount)) {
            endTimedAction("visitors timeout");
            lastServeTick = runtime.tickCount;
            resetState();
            return;
        }

        if (runtime.screenOpen && runtime.screenTitle != null && !runtime.screenTitle.isBlank()) {
            String title = runtime.screenTitle.toLowerCase(Locale.ROOT);
            if (title.contains("visitor")) {
                // Keeps flow consistent even when GUI opens slower/faster than expected.
                if (state == State.WAIT_VISITOR_SCREEN && ticksInState(runtime.tickCount) >= 8L) {
                    setState(State.EVALUATE_VISITOR, runtime.tickCount);
                }
            }
        }

        tickState(runtime, config);
    }

    private void tryStart(FeatureRuntimeState runtime, FarmHelperConfig config) {
        boolean canRun = runtime.macroToggled || config.visitorsMacroAfkInfiniteMode;
        if (!canRun) {
            return;
        }
        if (pendingVisitors < Math.max(1, config.visitorsMacroMinVisitors)) {
            return;
        }

        long cooldownTicks = Math.max(secondsToTicks(config.visitorsMacroActionSeconds), 120L);
        if (lastServeTick > 0 && runtime.tickCount - lastServeTick < cooldownTicks) {
            return;
        }

        parseFilters(config);
        cycleFailures = 0;
        servedThisCycle = 0;
        stateRetries = 0;
        cycleTargetVisitors = Math.max(1, config.visitorsMacroMaxVisitorsPerCycle);
        cycleTargetVisitors = Math.min(cycleTargetVisitors, (int) Math.max(1L, pendingVisitors));

        long actionBudget = cooldownTicks + (long) cycleTargetVisitors * 160L + 260L;
        if (!beginTimedAction(runtime.tickCount, actionBudget, "serve queued visitors")) {
            return;
        }

        setState(State.OPEN_VISITOR_MENU, runtime.tickCount, true);
        FarmHelperFabric.getWebhookService().onVisitorEvent(
                "Visitors cycle started, queued=" + pendingVisitors + ", target=" + cycleTargetVisitors,
                config.pingEveryoneOnVisitorsMacroLogs
        );
    }

    private void tickState(FeatureRuntimeState runtime, FarmHelperConfig config) {
        switch (state) {
            case OPEN_VISITOR_MENU -> {
                if (isEntryTick(runtime.tickCount)) {
                    currentVisitorName = visitorNameQueue.peekFirst() == null ? "" : visitorNameQueue.peekFirst();
                    shouldRejectVisitor = false;
                    activeOfferLooksExpensive = false;
                    attemptedAutoBuyThisVisitor = false;
                    requestedItemName = "";
                    requestedItemCount = 0;
                    availableRequestedItemCount = 0;
                    missingRequestedItems = false;
                    if (config.visitorsMacroAutosellBeforeServing && config.enableAutoSell) {
                        queueCommand(config.autoSellMarketTypeNpc ? "/trades" : "/bz", runtime.tickCount);
                    }
                    queueCommand("/visitors", runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 24L) {
                    setState(State.MOVE_TO_VISITOR, runtime.tickCount, true);
                }
            }
            case MOVE_TO_VISITOR -> {
                if (isEntryTick(runtime.tickCount)) {
                    queueMoveToEntity("visitor,new visitor", 3.5, 200L, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 70L) {
                    setState(State.INTERACT_VISITOR, runtime.tickCount, true);
                }
            }
            case INTERACT_VISITOR -> {
                if (isEntryTick(runtime.tickCount)) {
                    queueInteractNearestEntity("visitor,new visitor", 4.0, 120L, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 25L) {
                    setState(State.WAIT_VISITOR_SCREEN, runtime.tickCount, true);
                }
            }
            case WAIT_VISITOR_SCREEN -> {
                if (isEntryTick(runtime.tickCount)) {
                    queueWaitForScreen("visitor", 160L, Math.max(1, config.visitorsMacroRetryLimit), runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 35L) {
                    retryState(config, runtime.tickCount, State.INTERACT_VISITOR, "visitor gui did not open");
                }
            }
            case EVALUATE_VISITOR -> {
                if (isEntryTick(runtime.tickCount)) {
                    evaluateRequestedItems();
                    String normalized = normalizeName(currentVisitorName);
                    shouldRejectVisitor = !normalized.isBlank() && denyList.contains(normalized);
                    if (!shouldRejectVisitor && !allowList.isEmpty() && !normalized.isBlank()) {
                        shouldRejectVisitor = !allowList.contains(normalized);
                    }
                    if (!shouldRejectVisitor && activeOfferLooksExpensive) {
                        shouldRejectVisitor = true;
                    }
                    if (!shouldRejectVisitor && missingRequestedItems && !config.autoBazaar) {
                        shouldRejectVisitor = true;
                    }
                }
                if (ticksInState(runtime.tickCount) >= 2L) {
                    if (!shouldRejectVisitor
                            && missingRequestedItems
                            && !attemptedAutoBuyThisVisitor
                            && config.autoBazaar
                            && requestedItemCount > 0
                            && !requestedItemName.isBlank()) {
                        setState(State.OPEN_BAZAAR_BUY, runtime.tickCount, true);
                    } else {
                        setState(shouldRejectVisitor ? State.HANDLE_REJECT : State.HANDLE_OFFER, runtime.tickCount, true);
                    }
                }
            }
            case OPEN_BAZAAR_BUY -> {
                if (isEntryTick(runtime.tickCount)) {
                    attemptedAutoBuyThisVisitor = true;
                    queueCloseScreen(runtime.tickCount);
                    queueCommand("/bz", runtime.tickCount);
                    queueWaitForScreen("bazaar", 140L, 2, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 24L) {
                    setState(State.BUY_REQUIREMENTS, runtime.tickCount, true);
                }
            }
            case BUY_REQUIREMENTS -> {
                if (isEntryTick(runtime.tickCount)) {
                    String item = requestedItemName.isBlank() ? "crop" : requestedItemName;
                    queueClickSlotMatching("name:" + item + ";lore:buy", 100L, 3, runtime.tickCount);
                    queueClickSlotMatching("name:buy instantly", 80L, 2, runtime.tickCount);
                    queueClickSlotMatching("name:confirm", 80L, 2, runtime.tickCount);
                    queueCloseScreen(runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 18L) {
                    setState(State.RETURN_TO_VISITOR, runtime.tickCount, true);
                }
            }
            case RETURN_TO_VISITOR -> {
                if (isEntryTick(runtime.tickCount)) {
                    queueCommand("/visitors", runtime.tickCount);
                    queueMoveToEntity("visitor,new visitor", 3.5, 200L, runtime.tickCount);
                    queueInteractNearestEntity("visitor,new visitor", 4.0, 120L, runtime.tickCount);
                    queueWaitForScreen("visitor", 120L, 2, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 30L) {
                    setState(State.HANDLE_OFFER, runtime.tickCount, true);
                }
            }
            case HANDLE_OFFER -> {
                if (isEntryTick(runtime.tickCount)) {
                    queueClickSlotMatching("name:accept offer;lore:reward", 80L, 5, runtime.tickCount);
                    queueClickSlotMatching("name:accept;lore:visitor", 60L, 4, runtime.tickCount);
                    queueClickSlotMatching("name:serve;lore:visitor", 60L, 3, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 25L) {
                    setState(State.CLOSE_SCREEN, runtime.tickCount, true);
                }
            }
            case HANDLE_REJECT -> {
                if (isEntryTick(runtime.tickCount)) {
                    queueClickSlotMatching("name:refuse;lore:visitor", 80L, 5, runtime.tickCount);
                    queueClickSlotMatching("name:decline;lore:offer", 80L, 5, runtime.tickCount);
                    queueClickSlotMatching("name:close", 60L, 3, runtime.tickCount);
                    if (config.sendVisitorsMacroLogs) {
                        String name = currentVisitorName.isBlank() ? "unknown visitor" : currentVisitorName;
                        String reason = "(filters/budget)";
                        if (missingRequestedItems) {
                            String have = availableRequestedItemCount < 0 ? "unknown" : String.valueOf(availableRequestedItemCount);
                            reason = "(missing items " + have + "/" + requestedItemCount
                                    + " for " + requestedItemName + ")";
                        } else if (activeOfferLooksExpensive) {
                            reason = "(offer over max spend)";
                        }
                        FarmHelperFabric.getWebhookService().onVisitorEvent(
                                "Rejected visitor offer from " + name + " " + reason,
                                config.pingEveryoneOnVisitorsMacroLogs
                        );
                    }
                }
                if (ticksInState(runtime.tickCount) >= 22L) {
                    setState(State.CLOSE_SCREEN, runtime.tickCount, true);
                }
            }
            case CLOSE_SCREEN -> {
                if (isEntryTick(runtime.tickCount)) {
                    queueCloseScreen(runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 10L) {
                    setState(State.NEXT_VISITOR, runtime.tickCount, true);
                }
            }
            case NEXT_VISITOR -> {
                if (isEntryTick(runtime.tickCount)) {
                    if (!visitorNameQueue.isEmpty()) {
                        visitorNameQueue.pollFirst();
                    }
                    pendingVisitors = Math.max(0L, pendingVisitors - 1L);
                    servedThisCycle++;
                }
                if (ticksInState(runtime.tickCount) >= 2L) {
                    if (servedThisCycle < cycleTargetVisitors && pendingVisitors > 0L) {
                        setState(State.OPEN_VISITOR_MENU, runtime.tickCount, true);
                    } else {
                        setState(State.FINISH, runtime.tickCount, true);
                    }
                }
            }
            case FINISH -> {
                FarmHelperFabric.getWebhookService().onVisitorEvent(
                        "Visitors cycle complete: served=" + servedThisCycle + "/" + cycleTargetVisitors
                                + ", remaining queue=" + pendingVisitors,
                        config.pingEveryoneOnVisitorsMacroLogs
                );
                endTimedAction("visitors cycle complete");
                lastServeTick = runtime.tickCount;
                resetState();
            }
            case IDLE -> {
            }
        }
    }

    @Override
    public void onChatMessage(String message) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.visitorsMacro || message == null || message.isBlank()) {
            return;
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        detectVisitorName(normalized);
        detectExpensiveOffer(normalized, config);
        if (!(normalized.contains("visitor") || normalized.contains("new guest") || normalized.contains("has arrived"))) {
            return;
        }

        Matcher matcher = VISITOR_COUNT_PATTERN.matcher(normalized);
        if (matcher.find()) {
            try {
                pendingVisitors = Math.max(pendingVisitors, Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                pendingVisitors++;
            }
        } else {
            pendingVisitors++;
        }

        if (config.sendVisitorsMacroLogs) {
            FarmHelperFabric.getWebhookService().onVisitorEvent(
                    "Visitor event observed (" + pendingVisitors + " queued): " + message,
                    config.pingEveryoneOnVisitorsMacroLogs
            );
        }
    }

    private void detectVisitorName(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return;
        }

        Matcher npcPrefix = NPC_PREFIX_PATTERN.matcher(normalizedMessage);
        if (npcPrefix.find()) {
            currentVisitorName = npcPrefix.group(1).trim();
        }

        Matcher visitorName = VISITOR_NAME_PATTERN.matcher(normalizedMessage);
        if (visitorName.find()) {
            String candidate = visitorName.group(1).trim();
            String normalized = normalizeName(candidate);
            if (!normalized.isBlank()) {
                visitorNameQueue.removeIf(existing -> normalizeName(existing).equals(normalized));
                visitorNameQueue.addLast(candidate);
                if (currentVisitorName.isBlank()) {
                    currentVisitorName = candidate;
                }
            }
        }
    }

    private void detectExpensiveOffer(String normalizedMessage, FarmHelperConfig config) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return;
        }
        Matcher itemMatcher = NEED_ITEM_PATTERN.matcher(normalizedMessage);
        if (itemMatcher.find()) {
            requestedItemCount = Math.max(0, parseIntSafe(itemMatcher.group(1), 0));
            requestedItemName = normalizeName(itemMatcher.group(2));
        }
        if (config.visitorsMacroMaxSpendLimit <= 0f) {
            return;
        }
        if (!(normalizedMessage.contains("coins") && (normalizedMessage.contains("offer") || normalizedMessage.contains("cost")))) {
            return;
        }
        Matcher matcher = COINS_PATTERN.matcher(normalizedMessage);
        if (!matcher.find()) {
            return;
        }
        long coins;
        try {
            coins = Long.parseLong(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException ignored) {
            return;
        }
        // Legacy config stores this in millions.
        long maxAllowedCoins = (long) Math.max(0d, config.visitorsMacroMaxSpendLimit * 1_000_000d);
        if (maxAllowedCoins > 0 && coins > maxAllowedCoins) {
            activeOfferLooksExpensive = true;
        }
    }

    private void parseFilters(FarmHelperConfig config) {
        denyList.clear();
        allowList.clear();
        parseCsvInto(config.visitorsMacroBlacklistCsv, denyList);
        parseCsvInto(config.visitorsMacroWhitelistCsv, allowList);
    }

    private void parseCsvInto(String csv, List<String> output) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String token : csv.split(",")) {
            String normalized = normalizeName(token);
            if (!normalized.isBlank()) {
                output.add(normalized);
            }
        }
    }

    private String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s'\\-]", "").trim();
    }

    private boolean retryState(FarmHelperConfig config, long nowTick, State retryState, String reason) {
        int limit = Math.max(1, config.visitorsMacroRetryLimit);
        if (stateRetries < limit) {
            stateRetries++;
            setState(retryState, nowTick, false);
            return false;
        }
        cycleFailures++;
        if (config.sendVisitorsMacroLogs) {
            FarmHelperFabric.getWebhookService().onVisitorEvent(
                    "Visitors flow retry limit hit: " + reason,
                    config.pingEveryoneOnVisitorsMacroLogs
            );
        }
        if (cycleFailures >= limit) {
            endTimedAction("visitors retry limit");
            lastServeTick = nowTick;
            resetState();
            return false;
        }
        stateRetries = 0;
        setState(State.CLOSE_SCREEN, nowTick, true);
        return false;
    }

    private long ticksInState(long nowTick) {
        return Math.max(0L, nowTick - stateSinceTick);
    }

    private boolean isEntryTick(long nowTick) {
        return ticksInState(nowTick) <= 1L;
    }

    private void setState(State next, long nowTick) {
        setState(next, nowTick, true);
    }

    private void setState(State next, long nowTick, boolean resetRetries) {
        state = next;
        stateSinceTick = nowTick;
        if (resetRetries && next == State.OPEN_VISITOR_MENU) {
            stateRetries = 0;
        }
    }

    private void resetState() {
        state = State.IDLE;
        stateSinceTick = 0L;
        stateRetries = 0;
        cycleFailures = 0;
        servedThisCycle = 0;
        cycleTargetVisitors = 0;
        currentVisitorName = "";
        shouldRejectVisitor = false;
        activeOfferLooksExpensive = false;
        attemptedAutoBuyThisVisitor = false;
        requestedItemName = "";
        requestedItemCount = 0;
        availableRequestedItemCount = 0;
        missingRequestedItems = false;
    }

    private int parseIntSafe(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.replace(",", "").trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void evaluateRequestedItems() {
        availableRequestedItemCount = -1;
        missingRequestedItems = false;
        if (requestedItemCount <= 0 || requestedItemName.isBlank()) {
            return;
        }
        // This module runs in the common source set without direct client inventory access.
        // Treat explicit item requirements as needing bazaar fulfillment when available.
        missingRequestedItems = true;
    }
}

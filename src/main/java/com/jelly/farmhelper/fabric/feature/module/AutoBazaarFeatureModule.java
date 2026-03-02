package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoBazaarFeatureModule extends MacroExclusiveFeatureModule {
    public enum RequestStatus {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private enum RequestType {
        BUY,
        SELL
    }

    private enum State {
        IDLE,
        OPEN_BAZAAR,
        WAIT_BAZAAR,
        EXECUTE_REQUEST,
        WAIT_CONFIRMATION,
        CLOSE_SCREEN,
        FINISH
    }

    private record BazaarRequest(long id, RequestType type, String itemName, int amount, double maxSpendCoins, boolean includeSacks) {
    }

    private static final Pattern COINS_PATTERN = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s+coins", Pattern.CASE_INSENSITIVE);
    private static final int MAX_FLOW_RETRIES = 3;

    private static final ArrayDeque<BazaarRequest> REQUEST_QUEUE = new ArrayDeque<>();
    private static final Map<Long, RequestStatus> REQUEST_STATUS = new ConcurrentHashMap<>();
    private static final AtomicLong REQUEST_IDS = new AtomicLong(1L);

    private State state = State.IDLE;
    private long stateSinceTick = 0L;
    private BazaarRequest currentRequest;
    private long lastRequestTick = -1L;
    private int flowRetries;
    private boolean currentRequestFailed;
    private boolean currentRequestSucceededFromChat;
    private String lastFailureReason = "";
    private String lastSuccessReason = "";

    public AutoBazaarFeatureModule(boolean enabled) {
        super("auto_bazaar", "Auto Bazaar", enabled);
    }

    public static long queueBuy(String itemName, int amount, double maxSpendCoins) {
        String normalizedItem = itemName == null ? "" : itemName.trim();
        if (normalizedItem.isEmpty()) {
            return -1L;
        }
        long id = REQUEST_IDS.getAndIncrement();
        BazaarRequest request = new BazaarRequest(
                id,
                RequestType.BUY,
                normalizedItem,
                Math.max(1, amount),
                Math.max(0.0, maxSpendCoins),
                false
        );
        synchronized (REQUEST_QUEUE) {
            REQUEST_QUEUE.addLast(request);
        }
        REQUEST_STATUS.put(id, RequestStatus.QUEUED);
        return id;
    }

    public static long queueInstantSell(boolean includeSacks) {
        long id = REQUEST_IDS.getAndIncrement();
        BazaarRequest request = new BazaarRequest(id, RequestType.SELL, "", 0, 0.0, includeSacks);
        synchronized (REQUEST_QUEUE) {
            REQUEST_QUEUE.addLast(request);
        }
        REQUEST_STATUS.put(id, RequestStatus.QUEUED);
        return id;
    }

    public static Optional<RequestStatus> requestStatus(long requestId) {
        if (requestId <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(REQUEST_STATUS.get(requestId));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        failCurrent("feature disabled");
        resetState();
    }

    @Override
    public void onDisconnect() {
        super.onDisconnect();
        failCurrent("disconnect");
        resetState();
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.autoBazaar) {
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
            failCurrent("request timeout");
            endTimedAction("autobazaar timeout");
            lastRequestTick = runtime.tickCount;
            resetState();
            return;
        }

        tickState(runtime);
    }

    private void tryStart(FeatureRuntimeState runtime, FarmHelperConfig config) {
        if (lastRequestTick > 0 && runtime.tickCount - lastRequestTick < 10L) {
            return;
        }
        BazaarRequest next = pollNextRequest();
        if (next == null) {
            return;
        }

        if (!passesPreflightChecks(next)) {
            return;
        }

        currentRequest = next;
        REQUEST_STATUS.put(currentRequest.id(), RequestStatus.RUNNING);
        currentRequestFailed = false;
        currentRequestSucceededFromChat = false;
        lastFailureReason = "";
        lastSuccessReason = "";
        flowRetries = 0;

        long actionTicks = Math.max(140L, secondsToTicks(config.autoBazaarActionSeconds));
        long budget = actionTicks + 260L;
        if (!beginTimedAction(runtime.tickCount, budget, "bazaar request")) {
            failCurrent("request could not start");
            resetState();
            return;
        }
        setState(State.OPEN_BAZAAR, runtime.tickCount, true);
        FarmHelperFabric.getWebhookService().sendFeatureLog(
                "Auto Bazaar request started id=" + currentRequest.id() + " type=" + currentRequest.type()
        );
    }

    private boolean passesPreflightChecks(BazaarRequest request) {
        if (request == null || request.type() != RequestType.BUY) {
            return true;
        }
        if (request.maxSpendCoins() <= 0) {
            return true;
        }
        double unit = ProfitCalculatorFeatureModule.estimateUnitPriceCoins(request.itemName());
        if (unit <= 0) {
            return true;
        }
        double estimatedTotal = unit * Math.max(1, request.amount());
        if (estimatedTotal <= request.maxSpendCoins()) {
            return true;
        }
        REQUEST_STATUS.put(request.id(), RequestStatus.FAILED);
        FarmHelperFabric.getWebhookService().sendFeatureLog(
                "Auto Bazaar preflight rejected id=" + request.id()
                        + " estimatedTotal=" + String.format(Locale.US, "%.1f", estimatedTotal)
                        + " > maxSpend=" + String.format(Locale.US, "%.1f", request.maxSpendCoins())
        );
        return false;
    }

    private void tickState(FeatureRuntimeState runtime) {
        switch (state) {
            case OPEN_BAZAAR -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    if (!"GARDEN".equalsIgnoreCase(runtime.location) && !"BARN".equalsIgnoreCase(runtime.location)) {
                        queueCommand("/warp garden", runtime.tickCount);
                    }
                    if (currentRequest != null && currentRequest.type() == RequestType.BUY) {
                        queueCommand("/bz " + currentRequest.itemName(), runtime.tickCount);
                    } else {
                        queueCommand("/bz", runtime.tickCount);
                    }
                }
                if (ticksInState(runtime.tickCount) >= 24L) {
                    setState(State.WAIT_BAZAAR, runtime.tickCount, true);
                }
            }
            case WAIT_BAZAAR -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueWaitForScreen("bazaar", 180L, 4, runtime.tickCount);
                }
                if (runtime.screenOpen && runtime.screenTitle != null
                        && runtime.screenTitle.toLowerCase(Locale.ROOT).contains("bazaar")) {
                    setState(State.EXECUTE_REQUEST, runtime.tickCount, true);
                    return;
                }
                if (ticksInState(runtime.tickCount) >= 40L) {
                    if (retryOrFail(runtime.tickCount, State.OPEN_BAZAAR, "bazaar screen did not open")) {
                        return;
                    }
                    setState(State.FINISH, runtime.tickCount, true);
                }
            }
            case EXECUTE_REQUEST -> {
                if (ticksInState(runtime.tickCount) == 0 && currentRequest != null) {
                    if (currentRequest.type() == RequestType.BUY) {
                        queueClickSlotMatching("name:" + currentRequest.itemName(), 90L, 6, runtime.tickCount);
                        queueClickSlotMatching("name:buy instantly", 90L, 6, runtime.tickCount);
                        if (currentRequest.amount() <= 1) {
                            queueClickSlotMatching("name:buy only one", 80L, 4, runtime.tickCount);
                        } else {
                            queueClickSlotMatching("name:custom amount", 80L, 3, runtime.tickCount);
                            queueClickSlotMatching("name:buy;lore:" + currentRequest.amount() + "x", 70L, 3, runtime.tickCount);
                            queueClickSlotMatching("lore:" + currentRequest.amount() + "x;name:buy", 70L, 3, runtime.tickCount);
                        }
                        queueClickSlotMatching("name:confirm instant buy", 70L, 4, runtime.tickCount);
                        queueClickSlotMatching("name:confirm", 70L, 4, runtime.tickCount);
                        queueClickSlotMatching("name:buy;lore:coins", 80L, 4, runtime.tickCount);
                    } else {
                        queueClickSlotMatching("name:sell inventory now", 90L, 5, runtime.tickCount);
                        if (currentRequest.includeSacks()) {
                            queueClickSlotMatching("name:sell sacks now", 90L, 5, runtime.tickCount);
                        }
                        queueClickSlotMatching("name:selling whole inventory", 80L, 4, runtime.tickCount);
                        queueClickSlotMatching("name:confirm", 70L, 3, runtime.tickCount);
                    }
                }
                if (ticksInState(runtime.tickCount) >= 28L) {
                    setState(State.WAIT_CONFIRMATION, runtime.tickCount, true);
                }
            }
            case WAIT_CONFIRMATION -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    // Handle Bazaar warning/confirm pages.
                    queueClickSlotMatching("name:warning", 60L, 2, runtime.tickCount);
                    queueClickSlotMatching("name:confirm", 70L, 3, runtime.tickCount);
                    queueClickSlotMatching("name:buy;lore:coins", 70L, 2, runtime.tickCount);
                    queueClickSlotMatching("name:selling whole inventory", 70L, 2, runtime.tickCount);
                }
                if (currentRequestFailed || currentRequestSucceededFromChat) {
                    setState(State.CLOSE_SCREEN, runtime.tickCount, true);
                    return;
                }
                if (ticksInState(runtime.tickCount) >= 72L) {
                    if (retryOrFail(runtime.tickCount, State.EXECUTE_REQUEST, "bazaar confirmation timeout")) {
                        return;
                    }
                    setState(State.CLOSE_SCREEN, runtime.tickCount, true);
                }
            }
            case CLOSE_SCREEN -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueCloseScreen(runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 10L) {
                    setState(State.FINISH, runtime.tickCount, true);
                }
            }
            case FINISH -> {
                if (currentRequestFailed) {
                    failCurrent(lastFailureReason.isBlank() ? "flow failure" : lastFailureReason);
                } else {
                    String reason = lastSuccessReason.isBlank()
                            ? (currentRequestSucceededFromChat ? "chat confirmation" : "flow completed")
                            : lastSuccessReason;
                    succeedCurrent(reason);
                }
                endTimedAction("autobazaar request complete");
                lastRequestTick = runtime.tickCount;
                resetState();
            }
            case IDLE -> {
            }
        }
    }

    private boolean retryOrFail(long nowTick, State retryState, String reason) {
        if (flowRetries < MAX_FLOW_RETRIES) {
            flowRetries++;
            setState(retryState, nowTick, false);
            return true;
        }
        currentRequestFailed = true;
        lastFailureReason = reason;
        return false;
    }

    @Override
    public void onChatMessage(String message) {
        if (currentRequest == null || message == null || message.isBlank()) {
            return;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (isFailureChat(normalized)) {
            currentRequestFailed = true;
            lastFailureReason = normalized;
            return;
        }

        if (currentRequest.type() == RequestType.BUY
                && normalized.contains("[bazaar]")
                && normalized.contains("bought")) {
            currentRequestSucceededFromChat = true;
            lastSuccessReason = "buy confirmed";
            enforceSpendLimitFromChat(normalized);
            return;
        }

        if (currentRequest.type() == RequestType.SELL
                && normalized.contains("[bazaar]")
                && (normalized.contains("executing instant sell") || normalized.contains("you sold"))) {
            currentRequestSucceededFromChat = true;
            lastSuccessReason = "sell confirmed";
        }
    }

    private void enforceSpendLimitFromChat(String normalized) {
        if (currentRequest == null || currentRequest.type() != RequestType.BUY) {
            return;
        }
        if (currentRequest.maxSpendCoins() <= 0) {
            return;
        }
        Double spent = extractCoins(normalized);
        if (spent == null) {
            return;
        }
        if (spent <= currentRequest.maxSpendCoins()) {
            return;
        }
        currentRequestFailed = true;
        currentRequestSucceededFromChat = false;
        lastFailureReason = "spend limit exceeded in chat confirmation: " + String.format(Locale.US, "%.1f", spent);
    }

    private boolean isFailureChat(String normalized) {
        return normalized.contains("cannot afford")
                || normalized.contains("not enough coins")
                || normalized.contains("couldn't find")
                || normalized.contains("could not find")
                || normalized.contains("this server is too laggy")
                || normalized.contains("order was cancelled")
                || normalized.contains("price changed")
                || normalized.contains("not found on bazaar")
                || normalized.contains("no matching bazaar item");
    }

    private Double extractCoins(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = COINS_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BazaarRequest pollNextRequest() {
        synchronized (REQUEST_QUEUE) {
            return REQUEST_QUEUE.pollFirst();
        }
    }

    private void failCurrent(String reason) {
        if (currentRequest == null) {
            return;
        }
        REQUEST_STATUS.put(currentRequest.id(), RequestStatus.FAILED);
        FarmHelperFabric.getWebhookService().sendFeatureLog(
                "Auto Bazaar request failed id=" + currentRequest.id() + " reason=" + reason
        );
        currentRequest = null;
        currentRequestFailed = false;
        currentRequestSucceededFromChat = false;
        flowRetries = 0;
        lastFailureReason = "";
        lastSuccessReason = "";
    }

    private void succeedCurrent(String reason) {
        if (currentRequest == null) {
            return;
        }
        REQUEST_STATUS.put(currentRequest.id(), RequestStatus.SUCCEEDED);
        FarmHelperFabric.getWebhookService().sendFeatureLog(
                "Auto Bazaar request succeeded id=" + currentRequest.id() + " (" + reason + ")"
        );
        currentRequest = null;
        currentRequestFailed = false;
        currentRequestSucceededFromChat = false;
        flowRetries = 0;
        lastFailureReason = "";
        lastSuccessReason = "";
    }

    private long ticksInState(long nowTick) {
        return Math.max(0L, nowTick - stateSinceTick);
    }

    private void setState(State next, long nowTick, boolean resetRetries) {
        State previous = state;
        state = next;
        stateSinceTick = nowTick;
        if (resetRetries && previous == State.IDLE && next == State.OPEN_BAZAAR) {
            flowRetries = 0;
        }
    }

    private void resetState() {
        state = State.IDLE;
        stateSinceTick = 0L;
        currentRequest = null;
        currentRequestFailed = false;
        currentRequestSucceededFromChat = false;
        flowRetries = 0;
        lastFailureReason = "";
        lastSuccessReason = "";
    }
}

package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class AutoWardrobeFeatureModule extends MacroExclusiveFeatureModule {
    public enum RequestStatus {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private enum State {
        IDLE,
        OPEN_WARDROBE,
        WAIT_WARDROBE,
        NAVIGATE_PAGE,
        WAIT_PAGE_SWITCH,
        CLICK_SLOT,
        WAIT_SLOT_SETTLE,
        OPEN_EQUIPMENT,
        WAIT_EQUIPMENT,
        SWAP_EQUIPMENT,
        CLOSE_SCREEN,
        FINISH
    }

    private record WardrobeRequest(long id, int slot, List<String> equipmentQueries) {
    }

    private static final ArrayDeque<WardrobeRequest> REQUESTS = new ArrayDeque<>();
    private static final Map<Long, RequestStatus> STATUS = new ConcurrentHashMap<>();
    private static final AtomicLong REQUEST_IDS = new AtomicLong(1L);

    public static volatile int activeWardrobeSlot = -1;

    private State state = State.IDLE;
    private long stateSinceTick;
    private WardrobeRequest currentRequest;
    private int nextEquipmentIndex;
    private int retries;
    private boolean chatFailure;
    private boolean chatAlreadyEquipped;
    private boolean menuFallbackTriggered;
    private String lastFailureReason = "";

    public AutoWardrobeFeatureModule(boolean enabled) {
        super("auto_wardrobe", "Auto Wardrobe", enabled);
    }

    public static long queueSwap(int slot) {
        return queueSwap(slot, "");
    }

    public static long queueSwap(int slot, String equipmentCsv) {
        if (slot < 1 || slot > 18) {
            return -1L;
        }
        List<String> queries = parseEquipmentQueries(equipmentCsv);
        long id = REQUEST_IDS.getAndIncrement();
        WardrobeRequest request = new WardrobeRequest(id, slot, queries);
        synchronized (REQUESTS) {
            REQUESTS.addLast(request);
        }
        STATUS.put(id, RequestStatus.QUEUED);
        return id;
    }

    public static Optional<RequestStatus> requestStatus(long requestId) {
        if (requestId <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(STATUS.get(requestId));
    }

    @Override
    public void onChatMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("already equipped")
                || normalized.contains("already wearing")
                || normalized.contains("set is already active")) {
            chatAlreadyEquipped = true;
        }
        if (normalized.contains("cannot use this command")
                || normalized.contains("cannot use wardrobe")
                || normalized.contains("wardrobe is on cooldown")
                || normalized.contains("cannot open this menu")
                || normalized.contains("you don't have a wardrobe")
                || normalized.contains("this wardrobe slot is locked")
                || normalized.contains("you cannot access your wardrobe")
                || normalized.contains("you cannot do that right now")) {
            chatFailure = true;
            lastFailureReason = normalized;
        }
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
        if (!config.autoWardrobe || runtime.activeFailsafe.isPresent()) {
            return;
        }

        if (!isActionRunning()) {
            WardrobeRequest next = pollNext();
            if (next == null) {
                return;
            }
            currentRequest = next;
            STATUS.put(currentRequest.id(), RequestStatus.RUNNING);
            nextEquipmentIndex = 0;
            retries = 0;
            chatFailure = false;
            chatAlreadyEquipped = false;
            menuFallbackTriggered = false;
            lastFailureReason = "";
            long budget = Math.max(140L, secondsToTicks(config.autoWardrobeActionSeconds));
            if (!beginTimedAction(runtime.tickCount, budget + 220L, "wardrobe swap")) {
                failCurrent("request could not start");
                resetState();
                return;
            }
            setState(State.OPEN_WARDROBE, runtime.tickCount);
            return;
        }

        if (chatFailure) {
            failCurrent("chat signaled failure: " + lastFailureReason);
            endTimedAction("wardrobe flow failed");
            resetState();
            return;
        }

        if (shouldEndAction(runtime.tickCount)) {
            failCurrent("wardrobe timeout");
            endTimedAction("wardrobe timeout");
            resetState();
            return;
        }

        tickState(runtime);
    }

    private void tickState(FeatureRuntimeState runtime) {
        switch (state) {
            case OPEN_WARDROBE -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    if (runtime.screenOpen) {
                        queueCloseScreen(runtime.tickCount);
                    }
                    queueCommand("/wd", runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 14L) {
                    setState(State.WAIT_WARDROBE, runtime.tickCount);
                }
            }
            case WAIT_WARDROBE -> {
                if (isWardrobeScreen(runtime)) {
                    retries = 0;
                    menuFallbackTriggered = false;
                    setState(State.NAVIGATE_PAGE, runtime.tickCount);
                    return;
                }
                if (runtime.screenOpen && isMenuScreen(runtime.screenTitle) && !menuFallbackTriggered) {
                    queueClickSlotMatching("name:wardrobe", 80L, 3, runtime.tickCount);
                    menuFallbackTriggered = true;
                }
                if (ticksInState(runtime.tickCount) > 36L) {
                    if (retries++ < 3) {
                        setState(State.OPEN_WARDROBE, runtime.tickCount);
                    } else {
                        failCurrent("wardrobe screen never opened");
                        setState(State.FINISH, runtime.tickCount);
                    }
                }
            }
            case NAVIGATE_PAGE -> {
                if (currentRequest == null) {
                    setState(State.FINISH, runtime.tickCount);
                    return;
                }
                if (!isWardrobeScreen(runtime)) {
                    if (retries++ < 2) {
                        setState(State.OPEN_WARDROBE, runtime.tickCount);
                    } else {
                        failCurrent("lost wardrobe screen");
                        setState(State.FINISH, runtime.tickCount);
                    }
                    return;
                }
                if (currentRequest.slot() <= 9 || isSecondWardrobePage(runtime.screenTitle)) {
                    setState(State.CLICK_SLOT, runtime.tickCount);
                    return;
                }
                if (ticksInState(runtime.tickCount) == 0) {
                    queueClickSlotMatching("name:next page", 80L, 4, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 14L) {
                    setState(State.WAIT_PAGE_SWITCH, runtime.tickCount);
                }
            }
            case WAIT_PAGE_SWITCH -> {
                if (isSecondWardrobePage(runtime.screenTitle)) {
                    retries = 0;
                    setState(State.CLICK_SLOT, runtime.tickCount);
                    return;
                }
                if (ticksInState(runtime.tickCount) > 24L) {
                    if (retries++ < 2) {
                        setState(State.NAVIGATE_PAGE, runtime.tickCount);
                    } else {
                        failCurrent("could not switch wardrobe page");
                        setState(State.FINISH, runtime.tickCount);
                    }
                }
            }
            case CLICK_SLOT -> {
                if (currentRequest == null) {
                    setState(State.FINISH, runtime.tickCount);
                    return;
                }
                if (ticksInState(runtime.tickCount) == 0) {
                    int adjustedSlot = currentRequest.slot() > 9 ? currentRequest.slot() - 9 : currentRequest.slot();
                    int containerSlotId = 35 + ((adjustedSlot - 1) % 9) + 1;
                    queueClickSlotMatching("slot:" + containerSlotId + ";not:locked", 80L, 4, runtime.tickCount);
                    queueClickSlotMatching("slot:" + containerSlotId, 60L, 2, runtime.tickCount);
                    queueClickSlotMatching("name:equipped", 60L, 2, runtime.tickCount);
                    queueClickSlotMatching("name:wearing", 60L, 2, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 16L) {
                    setState(State.WAIT_SLOT_SETTLE, runtime.tickCount);
                }
            }
            case WAIT_SLOT_SETTLE -> {
                if (chatAlreadyEquipped) {
                    activeWardrobeSlot = currentRequest == null ? -1 : currentRequest.slot();
                    setState(State.CLOSE_SCREEN, runtime.tickCount);
                    return;
                }
                if (ticksInState(runtime.tickCount) >= 14L) {
                    activeWardrobeSlot = currentRequest.slot();
                    if (currentRequest.equipmentQueries().isEmpty()) {
                        setState(State.CLOSE_SCREEN, runtime.tickCount);
                    } else {
                        setState(State.OPEN_EQUIPMENT, runtime.tickCount);
                    }
                }
            }
            case OPEN_EQUIPMENT -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    if (runtime.screenOpen) {
                        queueCloseScreen(runtime.tickCount);
                    }
                    queueCommand("/eq", runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 14L) {
                    setState(State.WAIT_EQUIPMENT, runtime.tickCount);
                }
            }
            case WAIT_EQUIPMENT -> {
                if (isEquipmentScreen(runtime)) {
                    retries = 0;
                    menuFallbackTriggered = false;
                    setState(State.SWAP_EQUIPMENT, runtime.tickCount);
                    return;
                }
                if (runtime.screenOpen && isMenuScreen(runtime.screenTitle) && !menuFallbackTriggered) {
                    queueClickSlotMatching("name:equipment", 80L, 3, runtime.tickCount);
                    queueClickSlotMatching("name:your equipment", 80L, 3, runtime.tickCount);
                    menuFallbackTriggered = true;
                }
                if (ticksInState(runtime.tickCount) > 36L) {
                    if (retries++ < 2) {
                        setState(State.OPEN_EQUIPMENT, runtime.tickCount);
                    } else {
                        failCurrent("equipment screen never opened");
                        setState(State.FINISH, runtime.tickCount);
                    }
                }
            }
            case SWAP_EQUIPMENT -> {
                if (currentRequest == null) {
                    setState(State.CLOSE_SCREEN, runtime.tickCount);
                    return;
                }
                if (!isEquipmentScreen(runtime)) {
                    if (retries++ < 2) {
                        setState(State.OPEN_EQUIPMENT, runtime.tickCount);
                    } else {
                        failCurrent("lost equipment screen");
                        setState(State.FINISH, runtime.tickCount);
                    }
                    return;
                }
                if (nextEquipmentIndex >= currentRequest.equipmentQueries().size()) {
                    setState(State.CLOSE_SCREEN, runtime.tickCount);
                    return;
                }
                if (ticksInState(runtime.tickCount) == 0) {
                    String query = currentRequest.equipmentQueries().get(nextEquipmentIndex);
                    queueClickSlotMatching("name:" + query, 70L, 3, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 16L) {
                    nextEquipmentIndex++;
                    setState(State.SWAP_EQUIPMENT, runtime.tickCount);
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
                if (currentRequest != null && STATUS.get(currentRequest.id()) == RequestStatus.RUNNING) {
                    succeedCurrent();
                }
                endTimedAction("wardrobe flow complete");
                resetState();
            }
            case IDLE -> {
            }
        }
    }

    private WardrobeRequest pollNext() {
        synchronized (REQUESTS) {
            return REQUESTS.pollFirst();
        }
    }

    private void succeedCurrent() {
        if (currentRequest == null) {
            return;
        }
        STATUS.put(currentRequest.id(), RequestStatus.SUCCEEDED);
        currentRequest = null;
    }

    private void failCurrent(String reason) {
        if (currentRequest == null) {
            return;
        }
        STATUS.put(currentRequest.id(), RequestStatus.FAILED);
        FarmHelperFabric.LOGGER.warn("Auto Wardrobe request {} failed: {}", currentRequest.id(), reason);
        currentRequest = null;
    }

    private void resetState() {
        state = State.IDLE;
        stateSinceTick = 0L;
        currentRequest = null;
        nextEquipmentIndex = 0;
        retries = 0;
        chatFailure = false;
        chatAlreadyEquipped = false;
        menuFallbackTriggered = false;
        lastFailureReason = "";
    }

    private long ticksInState(long nowTick) {
        return Math.max(0L, nowTick - stateSinceTick);
    }

    private void setState(State next, long nowTick) {
        if (next == State.OPEN_WARDROBE
                || next == State.OPEN_EQUIPMENT
                || next == State.WAIT_WARDROBE
                || next == State.WAIT_EQUIPMENT) {
            menuFallbackTriggered = false;
        }
        state = next;
        stateSinceTick = nowTick;
    }

    private boolean isWardrobeScreen(FeatureRuntimeState runtime) {
        if (!runtime.screenOpen) {
            return false;
        }
        String title = runtime.screenTitle == null ? "" : runtime.screenTitle.toLowerCase(Locale.ROOT);
        return title.contains("wardrobe");
    }

    private boolean isEquipmentScreen(FeatureRuntimeState runtime) {
        if (!runtime.screenOpen) {
            return false;
        }
        String title = runtime.screenTitle == null ? "" : runtime.screenTitle.toLowerCase(Locale.ROOT);
        return title.contains("equipment");
    }

    private boolean isSecondWardrobePage(String title) {
        String normalized = title == null ? "" : title.toLowerCase(Locale.ROOT);
        return normalized.contains("2)")
                || normalized.contains("page 2")
                || normalized.contains(" 2/2");
    }

    private boolean isMenuScreen(String title) {
        String normalized = title == null ? "" : title.toLowerCase(Locale.ROOT);
        return normalized.contains("skyblock menu")
                || normalized.contains("profile")
                || normalized.contains("settings")
                || normalized.contains("wardrobe")
                || normalized.contains("equipment");
    }

    private static List<String> parseEquipmentQueries(String equipmentCsv) {
        if (equipmentCsv == null || equipmentCsv.isBlank()) {
            return List.of();
        }
        String[] parts = equipmentCsv.split("[,|]");
        List<String> queries = new ArrayList<>(parts.length);
        for (String part : parts) {
            String normalized = part.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                queries.add(normalized);
            }
        }
        return queries;
    }
}

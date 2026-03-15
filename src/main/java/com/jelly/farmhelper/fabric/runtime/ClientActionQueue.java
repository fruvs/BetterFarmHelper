package com.jelly.farmhelper.fabric.runtime;

import com.jelly.farmhelper.fabric.FarmHelperFabric;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

public class ClientActionQueue {
    public record Action(ActionType type, String payload, long createdAtTick) {
    }

    public enum ActionType {
        CHAT_COMMAND,
        CHAT_MESSAGE,
        MOVE_TO_POS,
        MOVE_TO_ENTITY,
        FLY_TO_POS,
        FLY_TO_PLOT_CENTER,
        FLY_TO_ENTITY,
        INTERACT_NEAREST_ENTITY,
        ATTACK_NEAREST_ENTITY,
        WAIT_FOR_SCREEN,
        CLICK_SLOT_MATCHING,
        CLOSE_CURRENT_SCREEN,
        USE_HELD_ITEM,
        SELECT_HOTBAR_ITEM,
        MINE_NEAREST_BLOCK,
        SET_PERFORMANCE_MODE,
        SET_PIP_MODE,
        SET_MOUSE_UNGRAB,
        SET_FREELOOK_MODE,
        SET_USE_KEY,
        TAP_ATTACK_KEY,
        REQUEST_WINDOW_ATTENTION,
        ROTATE_TO,
        PLAY_MOVEMENT_RECORDING,
        STOP_MOVEMENT_RECORDING
    }

    private final ArrayDeque<Action> queue = new ArrayDeque<>();
    private static final int MAX_QUEUE_SIZE = 128;

    public synchronized void enqueue(ActionType type, String payload, long tick) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        String normalizedPayload = payload.trim();
        Action last = queue.peekLast();
        if (last != null && last.type == type && last.payload.equals(normalizedPayload)) {
            return;
        }
        if (queue.size() >= MAX_QUEUE_SIZE) {
            queue.poll();
        }
        queue.add(new Action(type, normalizedPayload, tick));
        FarmHelperFabric.getWebhookService().debugTrace(
                "action-queue",
                "enqueue type=" + type + " payload=" + normalizedPayload + " qsize=" + queue.size()
        );
    }

    public void enqueueCommand(String command, long tick) {
        enqueue(ActionType.CHAT_COMMAND, command, tick);
    }

    public void enqueueMessage(String message, long tick) {
        enqueue(ActionType.CHAT_MESSAGE, message, tick);
    }

    public void enqueueMoveToPos(double x, double y, double z, double tolerance, long timeoutTicks, long tick) {
        enqueue(
                ActionType.MOVE_TO_POS,
                x + "|" + y + "|" + z + "|" + tolerance + "|" + Math.max(1L, timeoutTicks),
                tick
        );
    }

    public void enqueueMoveToEntity(String namesCsv, double radius, long timeoutTicks, long tick) {
        enqueue(
                ActionType.MOVE_TO_ENTITY,
                namesCsv + "|" + radius + "|" + Math.max(1L, timeoutTicks),
                tick
        );
    }

    public void enqueueFlyToPos(double x, double y, double z, double tolerance, long timeoutTicks, long tick) {
        enqueue(
                ActionType.FLY_TO_POS,
                x + "|" + y + "|" + z + "|" + tolerance + "|" + Math.max(1L, timeoutTicks),
                tick
        );
    }

    public void enqueueFlyToPlotCenter(int plotNumber, double tolerance, long timeoutTicks, long tick) {
        enqueue(
                ActionType.FLY_TO_PLOT_CENTER,
                plotNumber + "|" + tolerance + "|" + Math.max(1L, timeoutTicks),
                tick
        );
    }

    public void enqueueFlyToEntity(String namesCsv, double radius, long timeoutTicks, long tick) {
        enqueue(
                ActionType.FLY_TO_ENTITY,
                namesCsv + "|" + radius + "|" + Math.max(1L, timeoutTicks),
                tick
        );
    }

    public void enqueueInteractNearestEntity(String namesCsv, double radius, long timeoutTicks, boolean attack, long tick) {
        enqueue(
                attack ? ActionType.ATTACK_NEAREST_ENTITY : ActionType.INTERACT_NEAREST_ENTITY,
                namesCsv + "|" + radius + "|" + Math.max(1L, timeoutTicks),
                tick
        );
    }

    public void enqueueWaitForScreen(String titleContains, long timeoutTicks, long tick) {
        enqueueWaitForScreen(titleContains, timeoutTicks, 3, tick);
    }

    public void enqueueWaitForScreen(String titleContains, long timeoutTicks, int retries, long tick) {
        enqueue(
                ActionType.WAIT_FOR_SCREEN,
                titleContains + "|" + Math.max(1L, timeoutTicks) + "|" + Math.max(0, retries),
                tick
        );
    }

    public void enqueueClickSlotMatching(String titleContains, long tick) {
        enqueue(ActionType.CLICK_SLOT_MATCHING, titleContains + "|60|4", tick);
    }

    public void enqueueClickSlotMatching(String query, long timeoutTicks, int retries, long tick) {
        enqueue(
                ActionType.CLICK_SLOT_MATCHING,
                query + "|" + Math.max(1L, timeoutTicks) + "|" + Math.max(0, retries),
                tick
        );
    }

    public void enqueueCloseCurrentScreen(long tick) {
        enqueue(ActionType.CLOSE_CURRENT_SCREEN, "close", tick);
    }

    public void enqueueUseHeldItem(long tick) {
        enqueue(ActionType.USE_HELD_ITEM, "use", tick);
    }

    public void enqueueSelectHotbarItem(String itemNameQuery, long tick) {
        enqueue(ActionType.SELECT_HOTBAR_ITEM, itemNameQuery, tick);
    }

    public void enqueueMineNearestBlock(String blockHintsCsv, double radius, long timeoutTicks, long tick) {
        enqueue(
                ActionType.MINE_NEAREST_BLOCK,
                blockHintsCsv + "|" + radius + "|" + Math.max(1L, timeoutTicks),
                tick
        );
    }

    public void enqueueSetPerformanceMode(boolean enabled, int maxFps, int viewDistance, long tick) {
        enqueue(
                ActionType.SET_PERFORMANCE_MODE,
                enabled + "|" + Math.max(10, maxFps) + "|" + Math.max(2, viewDistance),
                tick
        );
    }

    public void enqueueSetPipMode(boolean enabled, long tick) {
        enqueue(ActionType.SET_PIP_MODE, Boolean.toString(enabled), tick);
    }

    public void enqueueSetMouseUngrab(boolean enabled, long tick) {
        enqueue(ActionType.SET_MOUSE_UNGRAB, Boolean.toString(enabled), tick);
    }

    public void enqueueSetFreelookMode(boolean enabled, long tick) {
        enqueue(ActionType.SET_FREELOOK_MODE, Boolean.toString(enabled), tick);
    }

    public void enqueueSetUseKey(boolean enabled, long tick) {
        enqueue(ActionType.SET_USE_KEY, Boolean.toString(enabled), tick);
    }

    public void enqueueTapAttackKey(long tick) {
        enqueue(ActionType.TAP_ATTACK_KEY, "tap", tick);
    }

    public void enqueueRequestWindowAttention(String reason, long tick) {
        String payload = reason == null || reason.isBlank() ? "failsafe" : reason.trim();
        enqueue(ActionType.REQUEST_WINDOW_ATTENTION, payload, tick);
    }

    public void enqueueRotateTo(float yaw, float pitch, long durationTicks, long tick) {
        long duration = Math.max(1L, durationTicks);
        long timeout = duration + 20L;
        enqueue(ActionType.ROTATE_TO, yaw + "|" + pitch + "|" + duration + "|" + timeout, tick);
    }

    public void enqueuePlayMovementRecording(String pattern, long tick) {
        enqueue(ActionType.PLAY_MOVEMENT_RECORDING, pattern, tick);
    }

    public void enqueueStopMovementRecording(long tick) {
        enqueue(ActionType.STOP_MOVEMENT_RECORDING, "stop", tick);
    }

    public synchronized List<Action> drain(int maxActions) {
        List<Action> actions = new ArrayList<>(Math.max(0, maxActions));
        int i = 0;
        while (i < maxActions && !queue.isEmpty()) {
            actions.add(queue.poll());
            i++;
        }
        return actions;
    }

    public synchronized Action pollFirstMatching(Predicate<ActionType> predicate) {
        if (predicate == null || queue.isEmpty()) {
            return null;
        }
        Iterator<Action> iterator = queue.iterator();
        while (iterator.hasNext()) {
            Action action = iterator.next();
            if (predicate.test(action.type())) {
                iterator.remove();
                return action;
            }
        }
        return null;
    }

    public synchronized void pushFront(Action action) {
        if (action == null) {
            return;
        }
        if (queue.size() >= MAX_QUEUE_SIZE) {
            queue.pollLast();
        }
        queue.addFirst(action);
    }

    public synchronized int size() {
        return queue.size();
    }

    public synchronized void clear() {
        queue.clear();
    }
}

package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.runtime.ClientActionQueue;

public abstract class MacroExclusiveFeatureModule extends AbstractFeatureModule {
    private boolean pausedMacroForAction;
    private boolean actionRunning;
    private long actionEndTick;

    protected MacroExclusiveFeatureModule(String id, String name, boolean enabled) {
        super(id, name, enabled);
    }

    protected boolean beginTimedAction(long nowTick, long durationTicks, String reason) {
        if (actionRunning) {
            return false;
        }
        actionRunning = true;
        actionEndTick = nowTick + Math.max(1L, durationTicks);
        if (FarmHelperFabric.getMacroController().isToggled()) {
            pausedMacroForAction = true;
            FarmHelperFabric.getMacroController().pauseForFeature(name() + ": " + reason);
        } else {
            pausedMacroForAction = false;
        }
        return true;
    }

    @Override
    public void onDisable() {
        endTimedAction("feature disabled");
    }

    @Override
    public void onDisconnect() {
        endTimedAction("disconnect");
    }

    protected boolean isActionRunning() {
        return actionRunning;
    }

    protected boolean shouldEndAction(long nowTick) {
        return actionRunning && nowTick >= actionEndTick;
    }

    protected void endTimedAction(String reason) {
        if (!actionRunning) {
            return;
        }
        actionRunning = false;
        actionEndTick = 0L;
        if (pausedMacroForAction) {
            FarmHelperFabric.getMacroController().resumeFromFeature(name() + ": " + reason);
            pausedMacroForAction = false;
        }
    }

    public void cancelActiveAction(String reason) {
        endTimedAction(reason == null ? "cancelled" : reason);
    }

    protected long secondsToTicks(int seconds) {
        return Math.max(1L, seconds) * 20L;
    }

    protected void queueCommand(String command, long tick) {
        FarmHelperFabric.getClientActionQueue().enqueue(ClientActionQueue.ActionType.CHAT_COMMAND, command, tick);
    }

    protected void queueMessage(String message, long tick) {
        FarmHelperFabric.getClientActionQueue().enqueue(ClientActionQueue.ActionType.CHAT_MESSAGE, message, tick);
    }

    protected void queueMoveTo(double x, double y, double z, double tolerance, long timeoutTicks, long tick) {
        FarmHelperFabric.getClientActionQueue().enqueueMoveToPos(x, y, z, tolerance, timeoutTicks, tick);
    }

    protected void queueMoveToEntity(String namesCsv, double radius, long timeoutTicks, long tick) {
        FarmHelperFabric.getClientActionQueue().enqueueMoveToEntity(namesCsv, radius, timeoutTicks, tick);
    }

    protected void queueFlyToEntity(String namesCsv, double radius, long timeoutTicks, long tick) {
        FarmHelperFabric.getClientActionQueue().enqueueFlyToEntity(namesCsv, radius, timeoutTicks, tick);
    }

    protected void queueInteractNearestEntity(String namesCsv, double radius, long timeoutTicks, long tick) {
        FarmHelperFabric.getClientActionQueue().enqueueInteractNearestEntity(namesCsv, radius, timeoutTicks, false, tick);
    }

    protected void queueAttackNearestEntity(String namesCsv, double radius, long timeoutTicks, long tick) {
        FarmHelperFabric.getClientActionQueue().enqueueInteractNearestEntity(namesCsv, radius, timeoutTicks, true, tick);
    }

    protected void queueWaitForScreen(String titleContains, long timeoutTicks, long tick) {
        FarmHelperFabric.getClientActionQueue().enqueueWaitForScreen(titleContains, timeoutTicks, tick);
    }

    protected void queueWaitForScreen(String titleContains, long timeoutTicks, int retries, long tick) {
        FarmHelperFabric.getClientActionQueue().enqueueWaitForScreen(titleContains, timeoutTicks, retries, tick);
    }

    protected void queueClickSlotMatching(String text, long tick) {
        FarmHelperFabric.getClientActionQueue().enqueueClickSlotMatching(text, tick);
    }

    protected void queueClickSlotMatching(String query, long timeoutTicks, int retries, long tick) {
        FarmHelperFabric.getClientActionQueue().enqueueClickSlotMatching(query, timeoutTicks, retries, tick);
    }

    protected void queueCloseScreen(long tick) {
        FarmHelperFabric.getClientActionQueue().enqueueCloseCurrentScreen(tick);
    }

    protected void queueUseHeldItem(long tick) {
        FarmHelperFabric.getClientActionQueue().enqueueUseHeldItem(tick);
    }

    protected void queueSelectHotbarItem(String itemNameQuery, long tick) {
        FarmHelperFabric.getClientActionQueue().enqueueSelectHotbarItem(itemNameQuery, tick);
    }

    protected void queueMineNearestBlock(String blockHintsCsv, double radius, long timeoutTicks, long tick) {
        FarmHelperFabric.getClientActionQueue().enqueueMineNearestBlock(blockHintsCsv, radius, timeoutTicks, tick);
    }

    protected void queueSetPerformanceMode(boolean enabled, int maxFps, int viewDistance, long tick) {
        FarmHelperFabric.getClientActionQueue().enqueueSetPerformanceMode(enabled, maxFps, viewDistance, tick);
    }

    protected void queueSetPipMode(boolean enabled, long tick) {
        FarmHelperFabric.getClientActionQueue().enqueueSetPipMode(enabled, tick);
    }

    protected void queueSetMouseUngrab(boolean enabled, long tick) {
        FarmHelperFabric.getClientActionQueue().enqueueSetMouseUngrab(enabled, tick);
    }

    protected void queueSetFreelook(boolean enabled, long tick) {
        FarmHelperFabric.getClientActionQueue().enqueueSetFreelookMode(enabled, tick);
    }
}

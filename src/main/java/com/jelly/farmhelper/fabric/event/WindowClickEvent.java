package com.jelly.farmhelper.fabric.event;

import net.minecraft.screen.slot.SlotActionType;

public final class WindowClickEvent {
    public final int syncId;
    public final int slotId;
    public final int button;
    public final SlotActionType actionType;
    public final long timestampMs;

    public WindowClickEvent(int syncId, int slotId, int button, SlotActionType actionType, long timestampMs) {
        this.syncId = syncId;
        this.slotId = slotId;
        this.button = button;
        this.actionType = actionType;
        this.timestampMs = timestampMs;
    }
}

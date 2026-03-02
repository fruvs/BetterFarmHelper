package com.jelly.farmhelper.fabric.event;

public final class MillisecondEvent {
    public final long timestampMs;
    public final long tickCount;

    public MillisecondEvent(long timestampMs, long tickCount) {
        this.timestampMs = timestampMs;
        this.tickCount = tickCount;
    }
}

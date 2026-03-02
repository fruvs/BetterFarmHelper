package com.jelly.farmhelper.fabric.event;

import java.util.List;

public final class UpdateTablistEvent {
    public final List<String> tablist;
    public final long timestampMs;

    public UpdateTablistEvent(List<String> tablist, long timestampMs) {
        this.tablist = List.copyOf(tablist);
        this.timestampMs = timestampMs;
    }
}

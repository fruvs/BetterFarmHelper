package com.jelly.farmhelper.fabric.event;

import java.util.List;

public final class UpdateTablistFooterEvent {
    public final List<String> footer;

    public UpdateTablistFooterEvent(List<String> footer) {
        this.footer = List.copyOf(footer);
    }
}

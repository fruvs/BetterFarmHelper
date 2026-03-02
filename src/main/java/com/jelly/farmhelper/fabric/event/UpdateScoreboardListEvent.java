package com.jelly.farmhelper.fabric.event;

import java.util.List;

public final class UpdateScoreboardListEvent {
    public final List<String> scoreboardLines;
    public final List<String> cleanScoreboardLines;
    public final long timestampMs;

    public UpdateScoreboardListEvent(List<String> scoreboardLines, List<String> cleanScoreboardLines, long timestampMs) {
        this.scoreboardLines = List.copyOf(scoreboardLines);
        this.cleanScoreboardLines = List.copyOf(cleanScoreboardLines);
        this.timestampMs = timestampMs;
    }
}

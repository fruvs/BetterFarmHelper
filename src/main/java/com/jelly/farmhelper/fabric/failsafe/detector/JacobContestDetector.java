package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public class JacobContestDetector implements FailsafeDetector {
    private static final Pattern MC_FORMATTING = Pattern.compile("§.");
    private long lastProcessedChatSeq = -1L;

    @Override
    public FailsafeType type() {
        return FailsafeType.JACOB;
    }

    @Override
    public Optional<String> detect(RuntimeSnapshot snapshot, FarmHelperConfig config, DetectorState state) {
        if (!config.enableJacobFailsafe) {
            return Optional.empty();
        }
        if (state.latestChatSeq <= 0 || state.latestChatSeq == lastProcessedChatSeq) {
            return Optional.empty();
        }
        lastProcessedChatSeq = state.latestChatSeq;

        String raw = state.latestChatMessage == null ? "" : state.latestChatMessage;
        if (raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = MC_FORMATTING.matcher(raw).replaceAll("").toLowerCase(Locale.ROOT);

        // Ignore NPC result chatter and reward lines from Jacob.
        if (normalized.contains("[npc] jacob")
                || normalized.contains("let me count the final results")
                || normalized.contains("didn't earn a medal")
                || normalized.contains("participation reward")) {
            return Optional.empty();
        }

        boolean contestStart = normalized.contains("jacob")
                && (normalized.contains("contest has started")
                || normalized.contains("jacob's contest has started")
                || normalized.contains("new jacob contest"));

        if (!contestStart) {
            return Optional.empty();
        }

        return Optional.of("Jacob contest event detected: " + raw);
    }
}

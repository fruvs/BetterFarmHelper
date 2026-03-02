package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;

import java.util.Locale;
import java.util.Optional;

public class ChatKeywordDetector implements FailsafeDetector {
    private final FailsafeType type;
    private final String[] keywords;
    private final String reasonPrefix;

    public ChatKeywordDetector(FailsafeType type, String reasonPrefix, String... keywords) {
        this.type = type;
        this.keywords = keywords;
        this.reasonPrefix = reasonPrefix;
    }

    @Override
    public FailsafeType type() {
        return type;
    }

    @Override
    public Optional<String> detect(RuntimeSnapshot snapshot, FarmHelperConfig config, DetectorState state) {
        if (type == FailsafeType.GUEST_VISIT && !config.pauseOnGuestArrival) {
            return Optional.empty();
        }
        if (type == FailsafeType.EVACUATE && !config.autoEvacuateOnServerReboot) {
            return Optional.empty();
        }
        if (state.latestChatMessage == null || state.latestChatMessage.isEmpty()) {
            return Optional.empty();
        }
        String normalized = state.latestChatMessage.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return Optional.of(reasonPrefix + ": " + state.latestChatMessage);
            }
        }
        return Optional.empty();
    }
}

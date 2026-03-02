package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;

import java.util.Optional;

public class DisconnectDetector implements FailsafeDetector {
    @Override
    public FailsafeType type() {
        return FailsafeType.DISCONNECT;
    }

    @Override
    public Optional<String> detect(RuntimeSnapshot snapshot, FarmHelperConfig config, DetectorState state) {
        if (state.disconnected) {
            return Optional.of("Client disconnected from server");
        }
        return Optional.empty();
    }
}

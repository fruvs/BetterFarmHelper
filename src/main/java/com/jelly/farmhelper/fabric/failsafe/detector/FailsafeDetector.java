package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;

import java.util.Optional;

public interface FailsafeDetector {
    FailsafeType type();

    Optional<String> detect(RuntimeSnapshot snapshot, FarmHelperConfig config, DetectorState state);
}

package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;

import java.util.Optional;

public class WorldChangeDetector implements FailsafeDetector {
    @Override
    public FailsafeType type() {
        return FailsafeType.WORLD_CHANGE;
    }

    @Override
    public Optional<String> detect(RuntimeSnapshot snapshot, FarmHelperConfig config, DetectorState state) {
        if (state.worldChanged || !snapshot.inWorld) {
            return Optional.of("World changed while macroing");
        }
        return Optional.empty();
    }
}

package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;

import java.util.Optional;

public class DirtDetector implements FailsafeDetector {
    private long suspiciousSinceTick = -1L;

    @Override
    public FailsafeType type() {
        return FailsafeType.DIRT;
    }

    @Override
    public Optional<String> detect(RuntimeSnapshot snapshot, FarmHelperConfig config, DetectorState state) {
        if (snapshot.macroState != MacroState.FARMING
                || snapshot.screenOpen
                || snapshot.networkLagging
                || snapshot.macroRuntimeTicks < 80L) {
            suspiciousSinceTick = -1L;
            return Optional.empty();
        }
        if (!snapshot.nearDirt || snapshot.nearSpawnPoint || snapshot.nearRewarpPoint) {
            suspiciousSinceTick = -1L;
            return Optional.empty();
        }

        int stationaryThreshold = Math.max(30, config.stationaryFailsafeTicks / 3);
        if (snapshot.stationaryTicks < stationaryThreshold || snapshot.horizontalSpeedBps > 0.5) {
            suspiciousSinceTick = -1L;
            return Optional.empty();
        }

        if (suspiciousSinceTick < 0L) {
            suspiciousSinceTick = snapshot.tickCount;
            return Optional.empty();
        }

        long confirmTicks = Math.max(30L, config.detectionTimeWindowMs / 25L);
        if (snapshot.tickCount - suspiciousSinceTick < confirmTicks) {
            return Optional.empty();
        }

        suspiciousSinceTick = snapshot.tickCount;
        return Optional.of("Persistent dirt obstruction while stationary near player");
    }
}

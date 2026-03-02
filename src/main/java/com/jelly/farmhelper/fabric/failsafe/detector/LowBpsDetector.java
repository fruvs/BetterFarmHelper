package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;

import java.util.Optional;

public class LowBpsDetector implements FailsafeDetector {
    private int lowBpsTicks;

    @Override
    public FailsafeType type() {
        return FailsafeType.LOW_BPS;
    }

    @Override
    public Optional<String> detect(RuntimeSnapshot snapshot, FarmHelperConfig config, DetectorState state) {
        if (!config.enableBpsCheck) {
            return Optional.empty();
        }
        if (snapshot.macroState != MacroState.FARMING) {
            lowBpsTicks = 0;
            return Optional.empty();
        }
        if (snapshot.macroRuntimeTicks < 60L
                || snapshot.networkLagging
                || snapshot.screenOpen
                || state.packetTeleportSuppressed
                || state.packetRotationSuppressed) {
            lowBpsTicks = 0;
            return Optional.empty();
        }

        if (snapshot.stationaryTicks >= Math.max(60, config.stationaryFailsafeTicks)) {
            lowBpsTicks = 0;
            return Optional.of("Stationary for " + snapshot.stationaryTicks + " ticks");
        }

        float threshold = Math.max(1f, config.minBpsThreshold);
        if (snapshot.horizontalSpeedBps < threshold) {
            lowBpsTicks++;
        } else {
            lowBpsTicks = 0;
        }

        if (lowBpsTicks >= 80) {
            lowBpsTicks = 0;
            return Optional.of("Low movement BPS " + String.format("%.1f", snapshot.horizontalSpeedBps)
                    + " below threshold " + String.format("%.1f", threshold));
        }
        return Optional.empty();
    }
}

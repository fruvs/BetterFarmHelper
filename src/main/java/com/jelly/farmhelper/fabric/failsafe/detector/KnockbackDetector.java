package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;

import java.util.Optional;

public class KnockbackDetector implements FailsafeDetector {
    @Override
    public FailsafeType type() {
        return FailsafeType.KNOCKBACK;
    }

    @Override
    public Optional<String> detect(RuntimeSnapshot snapshot, FarmHelperConfig config, DetectorState state) {
        if (snapshot.macroState != MacroState.FARMING) {
            return Optional.empty();
        }
        if (snapshot.macroRuntimeTicks < 40L) {
            return Optional.empty();
        }
        double threshold = Math.max(0.5, config.verticalKnockbackThreshold / 1000.0);
        if (config.enablePacketFailsafeChecks && state.packetVelocitySeen) {
            if (Math.abs(state.packetVelocityY) > threshold || state.packetVelocityMagnitude > threshold * 1.6) {
                return Optional.of("Knockback velocity packet y=" + String.format("%.2f", state.packetVelocityY)
                        + " mag=" + String.format("%.2f", state.packetVelocityMagnitude));
            }
        }
        if (!snapshot.networkLagging && Math.abs(snapshot.verticalVelocity) > threshold) {
            return Optional.of("Vertical velocity spike: " + String.format("%.2f", snapshot.verticalVelocity));
        }
        return Optional.empty();
    }
}

package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;

import java.util.Optional;

public class BedrockPacketDetector implements FailsafeDetector {
    @Override
    public FailsafeType type() {
        return FailsafeType.BEDROCK_CAGE;
    }

    @Override
    public Optional<String> detect(RuntimeSnapshot snapshot, FarmHelperConfig config, DetectorState state) {
        if (!config.enablePacketFailsafeChecks || snapshot.macroState != MacroState.FARMING) {
            return Optional.empty();
        }
        if (snapshot.macroRuntimeTicks < 40L || state.packetTeleportSuppressed) {
            return Optional.empty();
        }
        if (!state.packetPositionLookSeen) {
            return Optional.empty();
        }

        if (state.packetTeleportTargetY > 80.0
                && state.packetTeleportDistance >= Math.max(3.0, config.teleportDistanceThreshold * 0.7)
                && (snapshot.nearBedrock || state.packetTeleportTargetY > 90.0 || state.packetTeleportDistance > 8.0)) {
            return Optional.of("High-altitude position packet suggests bedrock cage check"
                    + " (y=" + String.format("%.1f", state.packetTeleportTargetY) + ")");
        }
        return Optional.empty();
    }
}

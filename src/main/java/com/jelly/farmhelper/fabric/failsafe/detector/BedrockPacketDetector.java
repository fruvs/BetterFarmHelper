package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;

import java.util.Locale;
import java.util.Optional;

public class BedrockPacketDetector implements FailsafeDetector {
    private static final long CONFIRM_DELAY_TICKS = 2L;
    private static final long CONFIRM_TIMEOUT_TICKS = 7L;

    private long pendingSinceTick = -1L;
    private double pendingTargetY;

    @Override
    public FailsafeType type() {
        return FailsafeType.BEDROCK_CAGE;
    }

    @Override
    public Optional<String> detect(RuntimeSnapshot snapshot, FarmHelperConfig config, DetectorState state) {
        if (!config.enablePacketFailsafeChecks || snapshot.macroState != MacroState.FARMING) {
            clearPending();
            return Optional.empty();
        }
        if (snapshot.macroRuntimeTicks < 40L
                || snapshot.movementRecordingPlaying
                || state.packetTeleportSuppressed
                || snapshot.networkLagging) {
            clearPending();
            return Optional.empty();
        }

        if (state.packetPositionLookSeen) {
            if (snapshot.posY < 66.0) {
                clearPending();
                return Optional.empty();
            }
            if (state.packetTeleportTargetY > 80.0) {
                pendingSinceTick = snapshot.tickCount;
                pendingTargetY = state.packetTeleportTargetY;
            }
        }

        if (pendingSinceTick < 0L) {
            return Optional.empty();
        }

        if (!"GARDEN".equalsIgnoreCase(snapshot.location)) {
            clearPending();
            return Optional.empty();
        }

        long elapsed = snapshot.tickCount - pendingSinceTick;
        if (elapsed < CONFIRM_DELAY_TICKS) {
            return Optional.empty();
        }
        if (snapshot.bedrockCount > 3) {
            String reason = String.format(
                    Locale.US,
                    "Confirmed bedrock cage packet y=%.1f bedrockCount=%d",
                    pendingTargetY,
                    snapshot.bedrockCount
            );
            clearPending();
            return Optional.of(reason);
        }
        if (elapsed > CONFIRM_TIMEOUT_TICKS) {
            clearPending();
        }
        return Optional.empty();
    }

    private void clearPending() {
        pendingSinceTick = -1L;
        pendingTargetY = 0.0;
    }
}

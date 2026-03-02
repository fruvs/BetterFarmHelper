package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RotationDetectorTest {
    @Test
    void suppressedPacketRotationDoesNotTrigger() {
        RotationDetector detector = new RotationDetector();
        FarmHelperConfig config = new FarmHelperConfig();
        RuntimeSnapshot snapshot = farmingSnapshot(200L, 0f, 0f);

        DetectorState state = new DetectorState();
        state.packetPositionLookSeen = true;
        state.packetYawDelta = 35f;
        state.packetPitchDelta = 0f;
        state.packetRotationOriginYaw = 0f;
        state.packetRotationOriginPitch = 0f;
        state.packetRotationSuppressed = true;

        Optional<String> result = detector.detect(snapshot, config, state);

        assertTrue(result.isEmpty());
    }

    @Test
    void packetRotationRequiresConfirmWindowBeforeTriggering() {
        RotationDetector detector = new RotationDetector();
        FarmHelperConfig config = new FarmHelperConfig();

        RuntimeSnapshot first = farmingSnapshot(200L, 0f, 0f);
        DetectorState firstState = new DetectorState();
        firstState.packetPositionLookSeen = true;
        firstState.packetYawDelta = 30f;
        firstState.packetPitchDelta = 0f;
        firstState.packetRotationOriginYaw = 0f;
        firstState.packetRotationOriginPitch = 0f;
        assertTrue(detector.detect(first, config, firstState).isEmpty());

        RuntimeSnapshot second = farmingSnapshot(211L, 36f, 0f);
        Optional<String> result = detector.detect(second, config, new DetectorState());

        assertTrue(result.isPresent());
        assertTrue(result.get().contains("Confirmed packet rotation delta"));
    }

    @Test
    void smallAbruptRotationsStayBelowThreshold() {
        RotationDetector detector = new RotationDetector();
        FarmHelperConfig config = new FarmHelperConfig();
        RuntimeSnapshot snapshot = farmingSnapshot(260L, 0f, 0f);
        snapshot.yawDelta = 12f;
        snapshot.pitchDelta = 6f;

        Optional<String> result = detector.detect(snapshot, config, new DetectorState());

        assertTrue(result.isEmpty());
    }

    private static RuntimeSnapshot farmingSnapshot(long tick, float yaw, float pitch) {
        RuntimeSnapshot snapshot = new RuntimeSnapshot();
        snapshot.tickCount = tick;
        snapshot.macroState = MacroState.FARMING;
        snapshot.macroRuntimeTicks = 600L;
        snapshot.yaw = yaw;
        snapshot.pitch = pitch;
        snapshot.yawDelta = 0f;
        snapshot.pitchDelta = 0f;
        snapshot.networkLagging = false;
        snapshot.screenOpen = false;
        return snapshot;
    }
}

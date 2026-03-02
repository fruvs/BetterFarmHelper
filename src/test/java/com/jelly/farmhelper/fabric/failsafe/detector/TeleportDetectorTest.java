package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportDetectorTest {
    @Test
    void suppressedPacketTeleportDoesNotTrigger() {
        TeleportDetector detector = new TeleportDetector();
        FarmHelperConfig config = new FarmHelperConfig();
        RuntimeSnapshot snapshot = farmingSnapshot(200L, 0.0, 72.0, 0.0);
        DetectorState state = packetTeleportState(8.0, 0.0, 72.0, 0.0, 8.0, 72.0, 0.0);
        state.packetTeleportSuppressed = true;

        Optional<String> result = detector.detect(snapshot, config, state);

        assertTrue(result.isEmpty());
    }

    @Test
    void persistentTeleportPacketTriggersAfterConfirmWindow() {
        TeleportDetector detector = new TeleportDetector();
        FarmHelperConfig config = new FarmHelperConfig();

        RuntimeSnapshot first = farmingSnapshot(200L, 0.0, 72.0, 0.0);
        DetectorState firstState = packetTeleportState(8.0, 0.0, 72.0, 0.0, 8.0, 72.0, 0.0);
        assertTrue(detector.detect(first, config, firstState).isEmpty());

        RuntimeSnapshot confirm = farmingSnapshot(211L, 8.0, 72.0, 0.0);
        assertTrue(detector.detect(confirm, config, new DetectorState()).isEmpty());

        RuntimeSnapshot persisted = farmingSnapshot(219L, 8.0, 72.0, 0.0);
        Optional<String> result = detector.detect(persisted, config, new DetectorState());

        assertTrue(result.isPresent());
        assertTrue(result.get().contains("Confirmed persistent teleport packet"));
    }

    @Test
    void lagBackStylePacketIsIgnoredWhenTargetMatchesHistory() {
        TeleportDetector detector = new TeleportDetector();
        FarmHelperConfig config = new FarmHelperConfig();

        detector.detect(farmingSnapshot(120L, 5.0, 72.0, 0.0), config, new DetectorState());

        RuntimeSnapshot packetSnapshot = farmingSnapshot(200L, 0.0, 72.0, 0.0);
        DetectorState packetState = packetTeleportState(4.6, 0.0, 72.0, 0.0, 5.0, 72.0, 0.0);
        assertTrue(detector.detect(packetSnapshot, config, packetState).isEmpty());

        RuntimeSnapshot confirm = farmingSnapshot(212L, 5.0, 72.0, 0.0);
        Optional<String> result = detector.detect(confirm, config, new DetectorState());

        assertFalse(result.isPresent());
    }

    private static RuntimeSnapshot farmingSnapshot(long tick, double x, double y, double z) {
        RuntimeSnapshot snapshot = new RuntimeSnapshot();
        snapshot.tickCount = tick;
        snapshot.macroState = MacroState.FARMING;
        snapshot.macroRuntimeTicks = 600L;
        snapshot.posX = x;
        snapshot.posY = y;
        snapshot.posZ = z;
        snapshot.velocityX = 0.0;
        snapshot.velocityZ = 0.0;
        snapshot.verticalVelocity = 0.0;
        snapshot.movedDistance = 0.0;
        snapshot.networkLagging = false;
        snapshot.screenOpen = false;
        return snapshot;
    }

    private static DetectorState packetTeleportState(
            double distance,
            double originX,
            double originY,
            double originZ,
            double targetX,
            double targetY,
            double targetZ
    ) {
        DetectorState state = new DetectorState();
        state.packetPositionLookSeen = true;
        state.packetTeleportDistance = distance;
        state.packetTeleportOriginX = originX;
        state.packetTeleportOriginY = originY;
        state.packetTeleportOriginZ = originZ;
        state.packetTeleportTargetX = targetX;
        state.packetTeleportTargetY = targetY;
        state.packetTeleportTargetZ = targetZ;
        return state;
    }
}

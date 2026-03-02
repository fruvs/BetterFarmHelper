package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemChangeDetectorTest {
    @Test
    void selectedHotbarServerSwapToNonToolTriggers() {
        ItemChangeDetector detector = new ItemChangeDetector();
        FarmHelperConfig config = new FarmHelperConfig();
        RuntimeSnapshot snapshot = farmingSnapshot();
        DetectorState state = basePacketSlotState();
        state.packetSlotItemName = "Stone";

        Optional<String> result = detector.detect(snapshot, config, state);

        assertTrue(result.isPresent());
        assertTrue(result.get().contains("non-tool"));
    }

    @Test
    void utilityItemsAreIgnored() {
        ItemChangeDetector detector = new ItemChangeDetector();
        FarmHelperConfig config = new FarmHelperConfig();
        RuntimeSnapshot snapshot = farmingSnapshot();
        DetectorState state = basePacketSlotState();
        state.packetSlotItemName = "InfiniVacuum";

        Optional<String> result = detector.detect(snapshot, config, state);

        assertTrue(result.isEmpty());
    }

    @Test
    void suppressedOrStationaryContextsDoNotTrigger() {
        ItemChangeDetector detector = new ItemChangeDetector();
        FarmHelperConfig config = new FarmHelperConfig();

        RuntimeSnapshot stationarySnapshot = farmingSnapshot();
        stationarySnapshot.stationaryTicks = 260;
        DetectorState stationaryState = basePacketSlotState();
        stationaryState.packetSlotItemName = "Stone";
        assertTrue(detector.detect(stationarySnapshot, config, stationaryState).isEmpty());

        RuntimeSnapshot normalSnapshot = farmingSnapshot();
        DetectorState suppressedState = basePacketSlotState();
        suppressedState.packetItemSuppressed = true;
        suppressedState.packetSlotItemName = "Stone";
        assertTrue(detector.detect(normalSnapshot, config, suppressedState).isEmpty());
    }

    private static RuntimeSnapshot farmingSnapshot() {
        RuntimeSnapshot snapshot = new RuntimeSnapshot();
        snapshot.macroState = MacroState.FARMING;
        snapshot.macroRuntimeTicks = 600L;
        snapshot.stationaryTicks = 0;
        snapshot.screenOpen = false;
        snapshot.networkLagging = false;
        return snapshot;
    }

    private static DetectorState basePacketSlotState() {
        DetectorState state = new DetectorState();
        state.packetSlotUpdateSeen = true;
        state.packetSlotTargetsSelectedHotbar = true;
        state.packetSlotLooksLikeFarmTool = false;
        state.packetSlot = 36;
        return state;
    }
}

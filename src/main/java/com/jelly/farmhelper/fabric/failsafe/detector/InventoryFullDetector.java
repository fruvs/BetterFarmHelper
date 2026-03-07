package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;

import java.util.Optional;

public class InventoryFullDetector implements FailsafeDetector {
    private long fullSinceTick = -1L;
    private boolean latched;

    @Override
    public FailsafeType type() {
        return FailsafeType.FULL_INVENTORY;
    }

    @Override
    public Optional<String> detect(RuntimeSnapshot snapshot, FarmHelperConfig config, DetectorState state) {
        if (snapshot.macroState != MacroState.FARMING || !config.enableAutoSell) {
            fullSinceTick = -1L;
            latched = false;
            return Optional.empty();
        }

        int threshold = Math.max(1, Math.min(100, config.inventoryFullRatio));
        if (snapshot.inventoryFillPercent < threshold) {
            fullSinceTick = -1L;
            latched = false;
            return Optional.empty();
        }

        if (latched) {
            return Optional.empty();
        }

        if (fullSinceTick < 0L) {
            fullSinceTick = snapshot.tickCount;
            return Optional.empty();
        }

        long sustainedTicks = Math.max(20L, config.inventoryFullTimeSeconds * 20L);
        if (snapshot.tickCount - fullSinceTick < sustainedTicks) {
            return Optional.empty();
        }

        latched = true;
        return Optional.of("Inventory nearly full (" + snapshot.inventoryFillPercent + "%) and auto-sell did not clear it");
    }
}

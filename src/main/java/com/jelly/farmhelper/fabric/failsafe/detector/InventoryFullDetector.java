package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;

import java.util.Optional;

public class InventoryFullDetector implements FailsafeDetector {
    @Override
    public FailsafeType type() {
        return FailsafeType.FULL_INVENTORY;
    }

    @Override
    public Optional<String> detect(RuntimeSnapshot snapshot, FarmHelperConfig config, DetectorState state) {
        if (snapshot.macroState != MacroState.FARMING) {
            return Optional.empty();
        }
        if (snapshot.inventoryFillPercent >= 95) {
            return Optional.of("Inventory nearly full (" + snapshot.inventoryFillPercent + "%)");
        }
        return Optional.empty();
    }
}

package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;

import java.util.Optional;

public class ItemChangeDetector implements FailsafeDetector {
    @Override
    public FailsafeType type() {
        return FailsafeType.ITEM_CHANGE;
    }

    @Override
    public Optional<String> detect(RuntimeSnapshot snapshot, FarmHelperConfig config, DetectorState state) {
        if (snapshot.macroState != MacroState.FARMING) {
            return Optional.empty();
        }
        if (snapshot.macroRuntimeTicks < 40L) {
            return Optional.empty();
        }
        if (config.enablePacketFailsafeChecks
                && state.packetSlotUpdateSeen
                && !state.packetItemSuppressed
                && !snapshot.networkLagging
                && !snapshot.screenOpen
                && snapshot.stationaryTicks < Math.max(120, config.stationaryFailsafeTicks)
                && state.packetSlotTargetsSelectedHotbar
                && (state.packetSlot >= 0 && state.packetSlot <= 44)
                && !state.packetSlotLooksLikeFarmTool
                && !looksIntentionalUtilityItem(state.packetSlotItemName)) {
            String itemName = state.packetSlotItemName == null || state.packetSlotItemName.isBlank()
                    ? "empty"
                    : state.packetSlotItemName;
            return Optional.of("Server changed selected slot item to non-tool: " + itemName
                    + " (slot " + state.packetSlot + ")");
        }
        if (snapshot.selectedSlotChanges >= 4) {
            return Optional.of("Frequent hotbar slot changes detected: " + snapshot.selectedSlotChanges);
        }
        return Optional.empty();
    }

    private boolean looksIntentionalUtilityItem(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return false;
        }
        String normalized = itemName.toLowerCase();
        return normalized.contains("vacuum")
                || normalized.contains("sprayonator")
                || normalized.contains("cookie")
                || normalized.contains("rod")
                || normalized.contains("repellent")
                || normalized.contains("composter")
                || normalized.contains("booster")
                || normalized.contains("skyblock menu")
                || normalized.contains("wardrobe")
                || normalized.contains("equipment")
                || normalized.contains("compass")
                || normalized.contains("clock")
                || normalized.contains("paper")
                || normalized.contains("book");
    }
}

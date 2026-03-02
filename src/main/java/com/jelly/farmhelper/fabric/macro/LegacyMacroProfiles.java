package com.jelly.farmhelper.fabric.macro;

import java.util.EnumMap;
import java.util.Map;

public final class LegacyMacroProfiles {
    private static final Map<LegacyMacroType, MacroStrategyProfile> PROFILES = new EnumMap<>(LegacyMacroType.class);

    static {
        PROFILES.put(LegacyMacroType.S_V_NORMAL_TYPE, new MacroStrategyProfile(MacroPattern.S_SHAPE, 140, 14, true));
        PROFILES.put(LegacyMacroType.S_PUMPKIN_MELON, new MacroStrategyProfile(MacroPattern.S_SHAPE, 150, 16, true));
        PROFILES.put(LegacyMacroType.S_PUMPKIN_MELON_MELONGKINGDE, new MacroStrategyProfile(MacroPattern.S_SHAPE, 148, 16, true));
        PROFILES.put(LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT, new MacroStrategyProfile(MacroPattern.S_SHAPE, 142, 16, true));
        PROFILES.put(LegacyMacroType.S_SUGAR_CANE, new MacroStrategyProfile(MacroPattern.S_SHAPE, 132, 12, true));
        PROFILES.put(LegacyMacroType.S_CACTUS, new MacroStrategyProfile(MacroPattern.S_SHAPE, 138, 11, true));
        PROFILES.put(LegacyMacroType.S_CACTUS_SUNTZU, new MacroStrategyProfile(MacroPattern.S_SHAPE, 138, 10, true));
        PROFILES.put(LegacyMacroType.S_COCOA_BEANS, new MacroStrategyProfile(MacroPattern.S_SHAPE, 124, 11, true));
        PROFILES.put(LegacyMacroType.S_COCOA_BEANS_TRAPDOORS, new MacroStrategyProfile(MacroPattern.S_SHAPE, 126, 11, true));
        PROFILES.put(LegacyMacroType.S_COCOA_BEANS_LEFT_RIGHT, new MacroStrategyProfile(MacroPattern.S_SHAPE, 130, 12, true));
        PROFILES.put(LegacyMacroType.S_MUSHROOM, new MacroStrategyProfile(MacroPattern.S_SHAPE, 136, 15, true));
        PROFILES.put(LegacyMacroType.S_MUSHROOM_ROTATE, new MacroStrategyProfile(MacroPattern.S_SHAPE, 120, 15, true));
        PROFILES.put(LegacyMacroType.S_MUSHROOM_SDS, new MacroStrategyProfile(MacroPattern.S_SHAPE, 128, 14, true));
        PROFILES.put(LegacyMacroType.C_NORMAL_TYPE, new MacroStrategyProfile(MacroPattern.BASIC_ROW, 140, 18, true));
    }

    private LegacyMacroProfiles() {
    }

    public static MacroStrategyProfile forType(LegacyMacroType type) {
        return PROFILES.getOrDefault(type, PROFILES.get(LegacyMacroType.S_V_NORMAL_TYPE));
    }
}

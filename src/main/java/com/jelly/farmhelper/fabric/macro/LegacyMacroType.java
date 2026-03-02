package com.jelly.farmhelper.fabric.macro;

public enum LegacyMacroType {
    S_V_NORMAL_TYPE("S Shape / Vertical - Crops"),
    S_PUMPKIN_MELON("S Shape - Pumpkin/Melon"),
    S_PUMPKIN_MELON_MELONGKINGDE("S Shape - Pumpkin/Melon Melongkingde"),
    S_PUMPKIN_MELON_DEFAULT_PLOT("S Shape - Pumpkin/Melon Default Plot"),
    S_SUGAR_CANE("S Shape - Sugar Cane/Wild Roses/Sunflower"),
    S_CACTUS("S Shape - Cactus"),
    S_CACTUS_SUNTZU("S Shape - Cactus SunTzu Black Cat"),
    S_COCOA_BEANS("S Shape - Cocoa Beans"),
    S_COCOA_BEANS_TRAPDOORS("S Shape - Cocoa Beans (With Trapdoors)"),
    S_COCOA_BEANS_LEFT_RIGHT("S Shape - Cocoa Beans (Left/Right)"),
    S_MUSHROOM("S Shape - Mushroom (45 deg)"),
    S_MUSHROOM_ROTATE("S Shape - Mushroom (30 deg with rotations)"),
    S_MUSHROOM_SDS("S Shape - Mushroom SDS"),
    C_NORMAL_TYPE("Circle - Crops");

    private final String displayName;

    LegacyMacroType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public MacroPattern defaultPattern() {
        if (this == C_NORMAL_TYPE) {
            return MacroPattern.BASIC_ROW;
        }
        return MacroPattern.S_SHAPE;
    }
}

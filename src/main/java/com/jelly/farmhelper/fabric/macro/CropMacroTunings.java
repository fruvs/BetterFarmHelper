package com.jelly.farmhelper.fabric.macro;

import java.util.EnumMap;
import java.util.Map;

public final class CropMacroTunings {
    private static final Map<LegacyMacroType, CropMacroTuning> TUNINGS = new EnumMap<>(LegacyMacroType.class);

    static {
        TUNINGS.put(LegacyMacroType.S_V_NORMAL_TYPE,
                new CropMacroTuning(CropMacroMotionMode.LANE_STRAFE, CropYawMode.CARDINAL, 2.8f, 3.3f, 0));
        TUNINGS.put(LegacyMacroType.S_PUMPKIN_MELON,
                new CropMacroTuning(CropMacroMotionMode.LANE_STRAFE, CropYawMode.DIAGONAL, 28.0f, 30.0f, 0));
        TUNINGS.put(LegacyMacroType.S_PUMPKIN_MELON_MELONGKINGDE,
                new CropMacroTuning(CropMacroMotionMode.LANE_STRAFE, CropYawMode.DIAGONAL, -59.2f, -58.2f, 0));
        TUNINGS.put(LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT,
                new CropMacroTuning(CropMacroMotionMode.LANE_STRAFE, CropYawMode.DIAGONAL, 47.0f, 53.0f, 0));
        TUNINGS.put(LegacyMacroType.S_SUGAR_CANE,
                new CropMacroTuning(CropMacroMotionMode.BACKWARD_SWEEP, CropYawMode.DIAGONAL, -0.5f, 0.5f, 0));
        TUNINGS.put(LegacyMacroType.S_CACTUS,
                new CropMacroTuning(CropMacroMotionMode.LANE_STRAFE, CropYawMode.CARDINAL, 0.0f, 0.5f, 0));
        TUNINGS.put(LegacyMacroType.S_CACTUS_SUNTZU,
                new CropMacroTuning(CropMacroMotionMode.LANE_STRAFE, CropYawMode.CARDINAL, -39.5f, -38.0f, 0));
        TUNINGS.put(LegacyMacroType.S_COCOA_BEANS,
                new CropMacroTuning(CropMacroMotionMode.COCOA_STRAFE, CropYawMode.CARDINAL, -70.0f, -69.4f, 0));
        TUNINGS.put(LegacyMacroType.S_COCOA_BEANS_TRAPDOORS,
                new CropMacroTuning(CropMacroMotionMode.COCOA_STRAFE, CropYawMode.CARDINAL, -70.0f, -69.4f, 0));
        TUNINGS.put(LegacyMacroType.S_COCOA_BEANS_LEFT_RIGHT,
                new CropMacroTuning(CropMacroMotionMode.COCOA_STRAFE, CropYawMode.CARDINAL, -90.0f, -90.0f, 0));
        TUNINGS.put(LegacyMacroType.S_MUSHROOM,
                new CropMacroTuning(CropMacroMotionMode.MUSHROOM_45, CropYawMode.DIAGONAL, -1.0f, 1.0f, 0));
        TUNINGS.put(LegacyMacroType.S_MUSHROOM_ROTATE,
                new CropMacroTuning(CropMacroMotionMode.MUSHROOM_ROTATE, CropYawMode.CARDINAL, -1.0f, 1.0f, 30));
        TUNINGS.put(LegacyMacroType.S_MUSHROOM_SDS,
                new CropMacroTuning(CropMacroMotionMode.MUSHROOM_SDS, CropYawMode.CARDINAL, 6.5f, 7.5f, 0));
        TUNINGS.put(LegacyMacroType.C_NORMAL_TYPE,
                new CropMacroTuning(CropMacroMotionMode.CIRCULAR, CropYawMode.DIAGONAL, 2.8f, 3.3f, 0));
    }

    private CropMacroTunings() {
    }

    public static CropMacroTuning forType(LegacyMacroType type) {
        return TUNINGS.getOrDefault(
                type == null ? LegacyMacroType.S_V_NORMAL_TYPE : type,
                TUNINGS.get(LegacyMacroType.S_V_NORMAL_TYPE)
        );
    }
}

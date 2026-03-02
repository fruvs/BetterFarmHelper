package com.jelly.farmhelper.fabric.macro;

public record CropMacroTuning(
        CropMacroMotionMode motionMode,
        CropYawMode yawMode,
        float pitchMin,
        float pitchMax,
        int rotateYawOffsetDegrees
) {
}

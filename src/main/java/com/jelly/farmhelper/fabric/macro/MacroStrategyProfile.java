package com.jelly.farmhelper.fabric.macro;

public record MacroStrategyProfile(
        MacroPattern pattern,
        int defaultForwardTicks,
        int defaultSideStepTicks,
        boolean holdAttack
) {
}

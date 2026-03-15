package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;

import java.util.Optional;

public class BadEffectsDetector implements FailsafeDetector {
    @Override
    public FailsafeType type() {
        return FailsafeType.BAD_EFFECTS;
    }

    @Override
    public Optional<String> detect(RuntimeSnapshot snapshot, FarmHelperConfig config, DetectorState state) {
        if (snapshot.macroState != MacroState.FARMING) {
            return Optional.empty();
        }
        if (snapshot.poisonActive
                || snapshot.witherActive
                || snapshot.blindnessActive
                || snapshot.nauseaActive
                || snapshot.miningFatigueActive
                || snapshot.hungerActive
                || snapshot.slownessActive
                || snapshot.weaknessActive
                || snapshot.burning) {
            return Optional.of("Negative status effect detected while macroing");
        }
        return Optional.empty();
    }
}

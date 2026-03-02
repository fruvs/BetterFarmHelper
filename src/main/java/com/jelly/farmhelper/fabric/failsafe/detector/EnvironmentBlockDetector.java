package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;

import java.util.Optional;
import java.util.function.Function;

public class EnvironmentBlockDetector implements FailsafeDetector {
    private final FailsafeType type;
    private final String label;
    private final Function<RuntimeSnapshot, Boolean> predicate;

    public EnvironmentBlockDetector(FailsafeType type, String label, Function<RuntimeSnapshot, Boolean> predicate) {
        this.type = type;
        this.label = label;
        this.predicate = predicate;
    }

    @Override
    public FailsafeType type() {
        return type;
    }

    @Override
    public Optional<String> detect(RuntimeSnapshot snapshot, FarmHelperConfig config, DetectorState state) {
        if (snapshot.macroState != MacroState.FARMING) {
            return Optional.empty();
        }
        if (predicate.apply(snapshot)) {
            return Optional.of(label + " detected near player");
        }
        return Optional.empty();
    }
}

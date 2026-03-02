package com.jelly.farmhelper.fabric.macro;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Adds subtle non-deterministic variance to movement/rotation so macro input does not look frame-perfect.
 */
public final class PlayerSimulation {
    public record MovementDecision(
            boolean forward,
            boolean back,
            boolean left,
            boolean right,
            boolean sprint,
            boolean attack
    ) {
    }

    private long pauseUntilTick = -1L;
    private long nextPauseCheckTick;
    private long nextJitterUpdateTick;
    private float yawOffset;
    private float pitchOffset;

    public void reset() {
        pauseUntilTick = -1L;
        nextPauseCheckTick = 0L;
        nextJitterUpdateTick = 0L;
        yawOffset = 0f;
        pitchOffset = 0f;
    }

    public MovementDecision adjustMovement(
            FarmHelperConfig config,
            long tick,
            boolean forward,
            boolean back,
            boolean left,
            boolean right,
            boolean sprint,
            boolean attack
    ) {
        if (config == null || !config.playerSimulationEnabled) {
            return new MovementDecision(forward, back, left, right, sprint, attack);
        }

        boolean hasMovementIntent = forward || back || left || right;
        updatePauseState(config, tick, hasMovementIntent);

        boolean outForward = forward;
        boolean outBack = back;
        boolean outLeft = left;
        boolean outRight = right;
        boolean outSprint = sprint;
        boolean outAttack = attack;

        if (tick <= pauseUntilTick) {
            outForward = false;
            outBack = false;
            outLeft = false;
            outRight = false;
            // Keep attack held through brief pauses to avoid obvious "tap" signatures.
            outAttack = attack;
            outSprint = false;
        } else if ((outLeft ^ outRight)
                && ThreadLocalRandom.current().nextInt(100) < Math.max(0, config.playerSimulationStrafeWobbleChancePct)) {
            // Occasionally release strafe for a tick to emulate slight correction.
            outLeft = false;
            outRight = false;
        }

        return new MovementDecision(outForward, outBack, outLeft, outRight, outSprint, outAttack);
    }

    public float getYawOffset(FarmHelperConfig config, long tick) {
        updateJitter(config, tick);
        return yawOffset;
    }

    public float getPitchOffset(FarmHelperConfig config, long tick) {
        updateJitter(config, tick);
        return pitchOffset;
    }

    private void updatePauseState(FarmHelperConfig config, long tick, boolean hasMovementIntent) {
        if (!hasMovementIntent) {
            return;
        }
        if (tick < nextPauseCheckTick) {
            return;
        }
        nextPauseCheckTick = tick + Math.max(20L, config.playerSimulationPauseCheckIntervalTicks);

        int chance = Math.max(0, Math.min(100, config.playerSimulationPauseChancePct));
        if (chance <= 0 || ThreadLocalRandom.current().nextInt(100) >= chance) {
            return;
        }

        int minTicks = Math.max(1, config.playerSimulationPauseMinTicks);
        int maxTicks = Math.max(minTicks, config.playerSimulationPauseMaxTicks);
        pauseUntilTick = tick + ThreadLocalRandom.current().nextInt(minTicks, maxTicks + 1);
    }

    private void updateJitter(FarmHelperConfig config, long tick) {
        if (config == null || !config.playerSimulationEnabled) {
            yawOffset = 0f;
            pitchOffset = 0f;
            return;
        }
        if (tick < nextJitterUpdateTick) {
            return;
        }

        int minInterval = Math.max(8, config.playerSimulationJitterIntervalMinTicks);
        int maxInterval = Math.max(minInterval, config.playerSimulationJitterIntervalMaxTicks);
        nextJitterUpdateTick = tick + ThreadLocalRandom.current().nextInt(minInterval, maxInterval + 1);

        float maxYaw = Math.max(0f, config.playerSimulationYawJitterDegrees);
        float maxPitch = Math.max(0f, config.playerSimulationPitchJitterDegrees);
        yawOffset = randomInRange(-maxYaw, maxYaw);
        pitchOffset = randomInRange(-maxPitch, maxPitch);
    }

    private float randomInRange(float min, float max) {
        if (max <= min) {
            return min;
        }
        return (float) ThreadLocalRandom.current().nextDouble(min, max);
    }
}

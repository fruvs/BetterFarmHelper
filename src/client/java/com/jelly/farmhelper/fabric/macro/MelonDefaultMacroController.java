package com.jelly.farmhelper.fabric.macro;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.util.AngleUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

final class MelonDefaultMacroController implements LegacyMovementController {
    private static final float MELON_SWITCH_ALIGN_TOLERANCE = 2.5f;
    private static final float MELON_LANE_ALIGN_TOLERANCE = 2.5f;
    private final MovementMacroExecutor executor;
    private ChangeLaneDirection changeLaneDirection;
    private boolean switchOrientationFlipped;

    MelonDefaultMacroController(MovementMacroExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void updateState(MinecraftClient client, FarmHelperConfig config, int forwardTicks, int sideTicks, boolean holdAttack) {
        MovementMacroExecutor.LegacyRouteState state = executor.getRouteState();
        if (state == null) {
            executor.setRouteState(MovementMacroExecutor.LegacyRouteState.NONE);
            state = MovementMacroExecutor.LegacyRouteState.NONE;
        }

        float routingYaw = executor.getLaneRoutingYaw();
        MovementMacroExecutor.Walkability walkability = executor.computeWalkability(client, routingYaw);
        switch (state) {
            case RIGHT, LEFT -> {
                boolean leftMarker = executor.isMelonOrPumpkinRelative(client, -1, 0, routingYaw);
                boolean rightMarker = executor.isMelonOrPumpkinRelative(client, 1, 0, routingYaw);
                if (leftMarker) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.LEFT);
                    return;
                }
                if (rightMarker) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.RIGHT);
                    return;
                }
                if (walkability.front()) {
                    if (changeLaneDirection == ChangeLaneDirection.BACKWARD) {
                        executor.startLegacyAntiStuckPulse(config);
                        return;
                    }
                    changeLaneDirection = ChangeLaneDirection.FORWARD;
                    executor.setTargetPitch(50f + (float) (ThreadLocalRandom.current().nextDouble(-3.0, 3.0)));
                    float additionalRotation = 0f;
                    if (state == MovementMacroExecutor.LegacyRouteState.RIGHT) {
                        additionalRotation = -(float) (ThreadLocalRandom.current().nextDouble(0.2, 0.6));
                    } else if (state == MovementMacroExecutor.LegacyRouteState.LEFT) {
                        additionalRotation = (float) (ThreadLocalRandom.current().nextDouble(0.2, 0.6));
                    }
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.SWITCHING_LANE);
                    switchOrientationFlipped = false;
                    executor.setTargetYaw(routingYaw + additionalRotation);
                    return;
                }
                if (walkability.back()) {
                    if (changeLaneDirection == ChangeLaneDirection.FORWARD) {
                        executor.startLegacyAntiStuckPulse(config);
                        return;
                    }
                    changeLaneDirection = ChangeLaneDirection.BACKWARD;
                    executor.setTargetPitch(50f + (float) (ThreadLocalRandom.current().nextDouble(-3.0, 3.0)));
                    float additionalRotation = 0f;
                    if (state == MovementMacroExecutor.LegacyRouteState.RIGHT) {
                        additionalRotation = -(float) (ThreadLocalRandom.current().nextDouble(0.2, 0.6));
                    } else if (state == MovementMacroExecutor.LegacyRouteState.LEFT) {
                        additionalRotation = (float) (ThreadLocalRandom.current().nextDouble(0.2, 0.6));
                    }
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.SWITCHING_LANE);
                    switchOrientationFlipped = false;
                    executor.setTargetYaw(routingYaw + additionalRotation);
                    return;
                }
                if (walkability.left()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.LEFT);
                } else if (walkability.right()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.RIGHT);
                } else {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.NONE);
                }
            }
            case SWITCHING_LANE -> {
                boolean sideWalkable = walkability.right() || walkability.left();
                if (walkability.front() && changeLaneDirection == ChangeLaneDirection.FORWARD && sideWalkable) {
                    executor.startLegacyAntiStuckPulse(config);
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.SWITCHING_LANE);
                    return;
                }
                if (walkability.back() && changeLaneDirection == ChangeLaneDirection.BACKWARD && sideWalkable) {
                    executor.startLegacyAntiStuckPulse(config);
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.SWITCHING_LANE);
                    return;
                }
                if (walkability.right()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.RIGHT);
                    switchOrientationFlipped = false;
                    executor.setTargetPitch(50f + (float) (ThreadLocalRandom.current().nextDouble(-3.0, 3.0)));
                    executor.setTargetYaw(routingYaw + (45f + (float) (ThreadLocalRandom.current().nextDouble(0.0, 2.0))));
                    return;
                }
                if (walkability.left()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.LEFT);
                    switchOrientationFlipped = false;
                    executor.setTargetPitch(50f + (float) (ThreadLocalRandom.current().nextDouble(-3.0, 3.0)));
                    executor.setTargetYaw(routingYaw - (45f + (float) (ThreadLocalRandom.current().nextDouble(0.0, 2.0))));
                    return;
                }
                if (walkability.front()) {
                    if (changeLaneDirection == ChangeLaneDirection.BACKWARD) {
                        executor.startLegacyAntiStuckPulse(config);
                        return;
                    }
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.SWITCHING_LANE);
                    return;
                }
                if (walkability.back()) {
                    if (changeLaneDirection == ChangeLaneDirection.FORWARD) {
                        executor.startLegacyAntiStuckPulse(config);
                        return;
                    }
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.SWITCHING_LANE);
                    return;
                }
                executor.setRouteState(MovementMacroExecutor.LegacyRouteState.NONE);
                switchOrientationFlipped = false;
            }
            case DROPPING -> {
                if (client.player == null) {
                    return;
                }
                int currentY = client.player.getBlockPos().getY();
                if (client.player.isOnGround() && Math.abs(executor.getLegacyLayerY() - currentY) > 1.5) {
                    changeLaneDirection = null;
                    if (config.rotateAfterDrop) {
                        float rotatedYaw = AngleUtils.closestCardinal(executor.getTargetYaw() + 180f);
                        executor.setTargetYaw(rotatedYaw);
                        executor.setLaneReferenceYaw(AngleUtils.closestCardinal(rotatedYaw));
                    }
                    executor.stopAllMovement(client);
                    executor.setLegacyLayerY(currentY);
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.NONE);
                    switchOrientationFlipped = false;
                } else {
                    if (client.player.isOnGround()) {
                        executor.setRouteState(MovementMacroExecutor.LegacyRouteState.NONE);
                        switchOrientationFlipped = false;
                    } else {
                        executor.legacyScheduleNotMoving(config);
                    }
                }
            }
            case NONE -> executor.setRouteState(calculateDirection(client));
            default -> executor.setRouteState(MovementMacroExecutor.LegacyRouteState.NONE);
        }
    }

    @Override
    public void invokeState(MinecraftClient client, FarmHelperConfig config, int forwardTicks, int sideTicks, boolean holdAttack) {
        MovementMacroExecutor.LegacyRouteState state = executor.getRouteState();
        if (state == null) {
            return;
        }
        float routingYaw = executor.getLaneRoutingYaw();
        MovementMacroExecutor.Walkability walkability = executor.computeWalkability(client, routingYaw);
        switch (state) {
            case RIGHT -> {
                if (shouldPauseForRotation(client, MELON_LANE_ALIGN_TOLERANCE)) {
                    executor.holdMovement(client, config, false, false, false, false, false, false);
                    executor.setDirectionLabel("MELON_ALIGN");
                    executor.legacyScheduleNotMoving(config);
                    break;
                }
                boolean forward = !walkability.back();
                executor.holdMovement(client, config, forward, false, false, true, false, true);
                executor.setDirectionLabel("MELON_RIGHT");
            }
            case LEFT -> {
                if (shouldPauseForRotation(client, MELON_LANE_ALIGN_TOLERANCE)) {
                    executor.holdMovement(client, config, false, false, false, false, false, false);
                    executor.setDirectionLabel("MELON_ALIGN");
                    executor.legacyScheduleNotMoving(config);
                    break;
                }
                boolean forward = !walkability.back();
                executor.holdMovement(client, config, forward, false, true, false, false, true);
                executor.setDirectionLabel("MELON_LEFT");
            }
            case SWITCHING_LANE -> {
                if (shouldPauseForRotation(client, MELON_SWITCH_ALIGN_TOLERANCE)) {
                    executor.holdMovement(client, config, false, false, false, false, false, false);
                    executor.setDirectionLabel("MELON_SWITCH_ALIGN");
                    executor.legacyScheduleNotMoving(config);
                    break;
                }
                double velocity = 0.0;
                if (client.player != null) {
                    velocity = Math.abs(client.player.getVelocity().x) + Math.abs(client.player.getVelocity().z);
                }
                if (velocity < 0.15 && !walkability.front()) {
                    if (!switchOrientationFlipped) {
                        switchOrientationFlipped = true;
                        executor.setTargetYaw(AngleUtils.closestCardinal(executor.getTargetYaw() + 180f));
                        executor.holdMovement(client, config, false, false, false, false, false, false);
                        executor.setDirectionLabel("MELON_SWITCH_REVERSE");
                        executor.legacyScheduleNotMoving(config);
                        break;
                    }
                    executor.holdMovement(client, config, false, false, false, false, false, false);
                    executor.setDirectionLabel("MELON_SWITCH_STALL");
                    break;
                }
                executor.holdMovement(client, config, true, false, false, false, true, false);
                executor.setDirectionLabel("MELON_SWITCH");
                executor.legacyScheduleNotMoving(25);
            }
            case DROPPING -> {
                if (client.player == null) {
                    return;
                }
                int currentY = client.player.getBlockPos().getY();
                if (client.player.isOnGround() && Math.abs(executor.getLegacyLayerY() - currentY) <= 1.5) {
                    executor.setLegacyLayerY(currentY);
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.NONE);
                }
            }
            case NONE -> executor.setDirectionLabel("MELON_IDLE");
            default -> executor.setDirectionLabel("MELON_IDLE");
        }
    }

    private boolean shouldPauseForRotation(MinecraftClient client, float toleranceDegrees) {
        if (client == null || client.player == null) {
            return false;
        }
        float yawDelta = Math.abs(MathHelper.wrapDegrees(executor.getTargetYaw() - client.player.getYaw()));
        return yawDelta > toleranceDegrees;
    }

    private MovementMacroExecutor.LegacyRouteState calculateDirection(MinecraftClient client) {
        float routingYaw = executor.getLaneRoutingYaw();
        for (int i = 0; i < 180; i++) {
            if (executor.isMelonOrPumpkinRelative(client, i, 0, routingYaw)) {
                return MovementMacroExecutor.LegacyRouteState.RIGHT;
            }
            if (executor.isMelonOrPumpkinRelative(client, -i, 0, routingYaw)) {
                return MovementMacroExecutor.LegacyRouteState.LEFT;
            }
            if (!executor.isWalkableOffset(client, i, 0, routingYaw)) {
                return MovementMacroExecutor.LegacyRouteState.LEFT;
            }
            if (!executor.isWalkableOffset(client, -i, 0, routingYaw)) {
                return MovementMacroExecutor.LegacyRouteState.RIGHT;
            }
        }
        return MovementMacroExecutor.LegacyRouteState.NONE;
    }

    private enum ChangeLaneDirection {
        FORWARD,
        BACKWARD
    }
}

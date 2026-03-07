package com.jelly.farmhelper.fabric.macro;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.util.AngleUtils;
import com.jelly.farmhelper.fabric.util.PlayerUtils;
import net.minecraft.client.MinecraftClient;

final class VerticalLaneMacroController implements LegacyMovementController {
    private final MovementMacroExecutor executor;
    private ChangeLaneDirection changeLaneDirection;

    VerticalLaneMacroController(MovementMacroExecutor executor) {
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
            case LEFT, RIGHT -> {
                boolean wantsRight = state == MovementMacroExecutor.LegacyRouteState.RIGHT;
                executor.setMoveRight(wantsRight);
                boolean sideWalkable = wantsRight ? walkability.right() : walkability.left();
                if (sideWalkable) {
                    executor.startLegacyAntiStuckPulse(config);
                    return;
                }
                if (walkability.front() && !config.alwaysHoldW) {
                    if (executor.isStuckInMelonsOrPumpkins(client, routingYaw)) {
                        executor.startLegacyAntiStuckPulse(config);
                        return;
                    }
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.SWITCHING_LANE);
                    executor.setLegacyWalkingDirection(client, routingYaw);
                    return;
                }
                if (walkability.back()
                        && executor.getActiveType() != LegacyMacroType.S_CACTUS
                        && executor.getActiveType() != LegacyMacroType.S_CACTUS_SUNTZU
                        && !config.alwaysHoldW) {
                    if (executor.isStuckInMelonsOrPumpkins(client, routingYaw)) {
                        executor.startLegacyAntiStuckPulse(config);
                        return;
                    }
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.SWITCHING_LANE);
                    executor.setLegacyWalkingDirection(client, routingYaw);
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
                if (executor.legacyDetectLagBack(client)) {
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
                } else if (client.player.isOnGround()) {
                    executor.setLegacyLayerY(currentY);
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.NONE);
                } else {
                    executor.legacyScheduleNotMoving(config);
                }
            }
            case NONE -> executor.setRouteState(executor.calculateLegacyVerticalDirection(client));
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
            case LEFT -> {
                boolean forward = config.alwaysHoldW || PlayerUtils.shouldWalkForwards(client);
                executor.holdMovement(client, config, forward, false, true, false, false, holdAttack);
                executor.setDirectionLabel("LEFT");
            }
            case RIGHT -> {
                boolean forward = config.alwaysHoldW || PlayerUtils.shouldWalkForwards(client);
                executor.holdMovement(client, config, forward, false, false, true, false, holdAttack);
                executor.setDirectionLabel("RIGHT");
            }
            case SWITCHING_LANE -> {
                if (changeLaneDirection == null) {
                    if (walkability.front()) {
                        changeLaneDirection = ChangeLaneDirection.FORWARD;
                    } else if (walkability.back()) {
                        changeLaneDirection = ChangeLaneDirection.BACKWARD;
                    } else {
                        executor.setRouteState(MovementMacroExecutor.LegacyRouteState.NONE);
                        return;
                    }
                }
                if (changeLaneDirection == ChangeLaneDirection.FORWARD) {
                    executor.setLaneShiftDirection(MovementMacroExecutor.LaneShiftDirection.FORWARD);
                    executor.holdMovement(client, config, true, false, false, false, true, holdAttack);
                    executor.setDirectionLabel("SWITCH_FWD");
                } else {
                    executor.setLaneShiftDirection(MovementMacroExecutor.LaneShiftDirection.BACKWARD);
                    executor.holdMovement(client, config, false, true, false, false, false, holdAttack);
                    executor.setDirectionLabel("SWITCH_BACK");
                }
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
            case NONE -> executor.setRouteState(executor.calculateLegacyVerticalDirection(client));
            default -> executor.setRouteState(MovementMacroExecutor.LegacyRouteState.NONE);
        }
    }

    private enum ChangeLaneDirection {
        FORWARD,
        BACKWARD
    }
}

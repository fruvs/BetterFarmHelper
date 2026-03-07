package com.jelly.farmhelper.fabric.macro;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.util.AngleUtils;
import net.minecraft.client.MinecraftClient;

final class Mushroom45MacroController implements LegacyMovementController {
    private final MovementMacroExecutor executor;

    Mushroom45MacroController(MovementMacroExecutor executor) {
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
            case LEFT -> {
                if (walkability.right()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.RIGHT);
                } else if (!walkability.left()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.LEFT);
                } else {
                    executor.setRouteState(calculateDirection(client));
                }
            }
            case RIGHT -> {
                if (walkability.left()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.LEFT);
                } else if (!walkability.right()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.RIGHT);
                } else {
                    executor.setRouteState(calculateDirection(client));
                }
            }
            case DROPPING -> {
                if (client.player == null) {
                    return;
                }
                int currentY = client.player.getBlockPos().getY();
                if (client.player.isOnGround() && Math.abs(executor.getLegacyLayerY() - currentY) > 1.5) {
                    if (config.rotateAfterDrop) {
                        float rotatedYaw = AngleUtils.closestCardinal(executor.getTargetYaw() + 180f);
                        executor.setTargetYaw(rotatedYaw);
                        executor.setLaneReferenceYaw(AngleUtils.closestCardinal(rotatedYaw));
                    }
                    executor.stopAllMovement(client);
                    executor.setLegacyLayerY(currentY);
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.NONE);
                } else {
                    executor.legacyScheduleNotMoving(config);
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
        switch (state) {
            case RIGHT -> {
                boolean lookLeft = executor.isMushroom45LookLeft();
                if (config.alwaysHoldW) {
                    executor.holdMovement(client, config, true, false, false, false, false, true);
                } else {
                    boolean forward = !lookLeft;
                    boolean right = lookLeft;
                    executor.holdMovement(client, config, forward, false, false, right, false, true);
                }
                executor.setDirectionLabel("MUSHROOM_RIGHT");
            }
            case LEFT -> {
                boolean lookLeft = executor.isMushroom45LookLeft();
                if (config.alwaysHoldW) {
                    executor.holdMovement(client, config, true, false, false, false, false, true);
                } else {
                    boolean forward = lookLeft;
                    boolean left = !lookLeft;
                    executor.holdMovement(client, config, forward, false, left, false, false, true);
                }
                executor.setDirectionLabel("MUSHROOM_LEFT");
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
            case NONE -> executor.setDirectionLabel("MUSHROOM_IDLE");
            default -> executor.setDirectionLabel("MUSHROOM_IDLE");
        }
    }

    private MovementMacroExecutor.LegacyRouteState calculateDirection(MinecraftClient client) {
        float routingYaw = executor.getLaneRoutingYaw();
        if (executor.isSideCropReady(client, 1, routingYaw)) {
            return MovementMacroExecutor.LegacyRouteState.RIGHT;
        } else if (executor.isSideCropReady(client, -1, routingYaw)) {
            return MovementMacroExecutor.LegacyRouteState.LEFT;
        }
        for (int i = 1; i < 180; i++) {
            if (!executor.isWalkableOffset(client, i, 0, routingYaw)) {
                return MovementMacroExecutor.LegacyRouteState.LEFT;
            }
            if (!executor.isWalkableOffset(client, -i, 0, routingYaw)) {
                return MovementMacroExecutor.LegacyRouteState.RIGHT;
            }
        }
        return MovementMacroExecutor.LegacyRouteState.NONE;
    }
}

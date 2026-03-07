package com.jelly.farmhelper.fabric.macro;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.util.AngleUtils;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.ThreadLocalRandom;

final class MushroomRotateMacroController implements LegacyMovementController {
    private final MovementMacroExecutor executor;

    MushroomRotateMacroController(MovementMacroExecutor executor) {
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
                    executor.setTargetYaw(routingYaw + 30f + (float) (ThreadLocalRandom.current().nextDouble(-2.0, 2.0)));
                } else if (walkability.left()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.LEFT);
                    executor.setTargetYaw(routingYaw - 30f + (float) (ThreadLocalRandom.current().nextDouble(-2.0, 2.0)));
                } else {
                    executor.setRouteState(calculateDirection(client));
                }
                executor.setTargetPitch((float) (ThreadLocalRandom.current().nextDouble(-1.0, 1.0)));
            }
            case RIGHT -> {
                if (walkability.left()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.LEFT);
                    executor.setTargetYaw(routingYaw - 30f + (float) (ThreadLocalRandom.current().nextDouble(-2.0, 2.0)));
                } else if (walkability.right()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.RIGHT);
                    executor.setTargetYaw(routingYaw + 30f + (float) (ThreadLocalRandom.current().nextDouble(-2.0, 2.0)));
                } else {
                    executor.setRouteState(calculateDirection(client));
                }
                executor.setTargetPitch((float) (ThreadLocalRandom.current().nextDouble(-1.0, 1.0)));
            }
            case DROPPING -> {
                if (client.player == null) {
                    return;
                }
                int currentY = client.player.getBlockPos().getY();
                if (client.player.isOnGround() && Math.abs(executor.getLegacyLayerY() - currentY) > 1.5) {
                    if (config.rotateAfterDrop) {
                        float rotatedYaw = AngleUtils.closestCardinal(executor.getTargetYaw() + 180f);
                        executor.setLaneReferenceYaw(AngleUtils.closestCardinal(rotatedYaw));
                        float side = executor.getRouteState() == MovementMacroExecutor.LegacyRouteState.LEFT ? -30f : 30f;
                        executor.setTargetYaw(executor.getLaneRoutingYaw() + side + (float) (ThreadLocalRandom.current().nextDouble(-2.0, 2.0)));
                    }
                    executor.stopAllMovement(client);
                    executor.setLegacyLayerY(currentY);
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.NONE);
                } else {
                    executor.legacyScheduleNotMoving(config);
                }
            }
            case NONE -> {
                executor.setRouteState(calculateDirection(client));
                if (executor.getRouteState() == MovementMacroExecutor.LegacyRouteState.LEFT) {
                    executor.setTargetYaw(routingYaw - 30f + (float) (ThreadLocalRandom.current().nextDouble(-2.0, 2.0)));
                } else if (executor.getRouteState() == MovementMacroExecutor.LegacyRouteState.RIGHT) {
                    executor.setTargetYaw(routingYaw + 30f + (float) (ThreadLocalRandom.current().nextDouble(-2.0, 2.0)));
                }
            }
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
            case RIGHT, LEFT -> {
                executor.holdMovement(client, config, true, false, false, false, false, true);
                executor.setDirectionLabel(state == MovementMacroExecutor.LegacyRouteState.LEFT ? "ROTATE_LEFT" : "ROTATE_RIGHT");
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
            case NONE -> executor.setDirectionLabel("ROTATE_IDLE");
            default -> executor.setDirectionLabel("ROTATE_IDLE");
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

package com.jelly.farmhelper.fabric.macro;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.util.AngleUtils;
import net.minecraft.client.MinecraftClient;

final class CircularMacroController implements LegacyMovementController {
    private final MovementMacroExecutor executor;

    CircularMacroController(MovementMacroExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void updateState(MinecraftClient client, FarmHelperConfig config, int forwardTicks, int sideTicks, boolean holdAttack) {
        MovementMacroExecutor.LegacyRouteState state = executor.getRouteState();
        if (state == null) {
            executor.setRouteState(MovementMacroExecutor.LegacyRouteState.NONE);
            state = MovementMacroExecutor.LegacyRouteState.NONE;
        }
        switch (state) {
            case W, NONE -> executor.setRouteState(MovementMacroExecutor.LegacyRouteState.D);
            case S -> executor.setRouteState(MovementMacroExecutor.LegacyRouteState.A);
            case A -> executor.setRouteState(MovementMacroExecutor.LegacyRouteState.W);
            case D -> executor.setRouteState(MovementMacroExecutor.LegacyRouteState.S);
            case DROPPING -> {
                if (client.player == null) {
                    return;
                }
                int currentY = client.player.getBlockPos().getY();
                if (client.player.isOnGround() && Math.abs(executor.getLegacyLayerY() - currentY) > 1.5) {
                    if (config.rotateAfterDrop) {
                        float rotatedYaw = AngleUtils.closestDiagonal(executor.getTargetYaw() + 180f);
                        executor.setTargetYaw(rotatedYaw);
                        executor.setLaneReferenceYaw(AngleUtils.closestCardinal(rotatedYaw));
                    }
                    executor.stopAllMovement(client);
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.NONE);
                    executor.setLegacyLayerY(currentY);
                } else {
                    executor.legacyScheduleNotMoving(config);
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
            case A -> {
                executor.holdMovement(client, config, false, false, true, false, false, true);
                executor.setDirectionLabel("CIRCLE_A");
            }
            case D -> {
                executor.holdMovement(client, config, false, false, false, true, false, true);
                executor.setDirectionLabel("CIRCLE_D");
            }
            case S -> {
                executor.holdMovement(client, config, false, true, false, false, false, true);
                executor.setDirectionLabel("CIRCLE_S");
            }
            case W -> {
                executor.holdMovement(client, config, true, false, false, false, false, true);
                executor.setDirectionLabel("CIRCLE_W");
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
            default -> {
            }
        }
    }
}

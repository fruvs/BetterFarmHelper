package com.jelly.farmhelper.fabric.macro;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.util.AngleUtils;
import net.minecraft.client.MinecraftClient;

final class SugarCaneMacroController implements LegacyMovementController {
    private final MovementMacroExecutor executor;

    SugarCaneMacroController(MovementMacroExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void updateState(MinecraftClient client, FarmHelperConfig config, int forwardTicks, int sideTicks, boolean holdAttack) {
        MovementMacroExecutor.LegacyRouteState state = executor.getRouteState();
        if (state == null) {
            executor.setRouteState(MovementMacroExecutor.LegacyRouteState.NONE);
            state = MovementMacroExecutor.LegacyRouteState.NONE;
        }
        float yaw = executor.getTargetYaw();
        switch (state) {
            case S -> {
                if (hasWall(client, 0, -1, yaw - 45f) && hasWall(client, 0, -1, yaw + 45f)) {
                    if (executor.getNearestSideWall(client, yaw + 45f, -1) == -999) {
                        executor.setRouteState(MovementMacroExecutor.LegacyRouteState.A);
                    }
                    if (executor.getNearestSideWall(client, yaw - 45f, 1) == -999) {
                        executor.setRouteState(MovementMacroExecutor.LegacyRouteState.D);
                    }
                }
            }
            case A, D -> executor.setRouteState(MovementMacroExecutor.LegacyRouteState.S);
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
            case A -> {
                executor.holdMovement(client, config, false, false, true, false, false, true);
                executor.setDirectionLabel("SUGARCANE_A");
            }
            case D -> {
                executor.holdMovement(client, config, false, false, false, true, false, true);
                executor.setDirectionLabel("SUGARCANE_D");
            }
            case S -> {
                executor.holdMovement(client, config, false, true, false, false, false, true);
                executor.setDirectionLabel("SUGARCANE_S");
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
            case NONE -> executor.setDirectionLabel("SUGARCANE_IDLE");
            default -> executor.setDirectionLabel("SUGARCANE_IDLE");
        }
    }

    private MovementMacroExecutor.LegacyRouteState calculateDirection(MinecraftClient client) {
        float yaw = executor.getTargetYaw();
        boolean leftWater = executor.isWaterRelativeAtYaw(client, 2, -1, 1, yaw - 45f)
                || executor.isWaterRelativeAtYaw(client, 2, 0, 1, yaw - 45f)
                || executor.isWaterRelativeAtYaw(client, -1, -1, 1, yaw - 45f)
                || executor.isWaterRelativeAtYaw(client, -1, 0, 1, yaw - 45f);
        if (leftWater) {
            if (!(hasWall(client, 0, 1, yaw - 45f) && hasWall(client, -1, 0, yaw - 45f))) {
                return MovementMacroExecutor.LegacyRouteState.A;
            } else {
                boolean rightWater = executor.isWaterRelativeAtYaw(client, 2, -1, 1, yaw + 45f)
                        || executor.isWaterRelativeAtYaw(client, 2, 0, 1, yaw + 45f)
                        || executor.isWaterRelativeAtYaw(client, -1, -1, 1, yaw + 45f)
                        || executor.isWaterRelativeAtYaw(client, -1, 0, 1, yaw + 45f);
                if (rightWater) {
                    if (!(hasWall(client, 0, 1, yaw + 45f) && hasWall(client, 11, 0, yaw + 45f))) {
                        return MovementMacroExecutor.LegacyRouteState.D;
                    }
                }
            }
        }
        return MovementMacroExecutor.LegacyRouteState.S;
    }

    private boolean hasWall(MinecraftClient client, int lateral, int forward, float yaw) {
        return executor.hasWallAtYaw(client, lateral, forward, yaw);
    }
}

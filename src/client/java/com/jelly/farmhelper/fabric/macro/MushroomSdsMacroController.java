package com.jelly.farmhelper.fabric.macro;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import net.minecraft.client.MinecraftClient;

final class MushroomSdsMacroController implements LegacyMovementController {
    private final MovementMacroExecutor executor;

    MushroomSdsMacroController(MovementMacroExecutor executor) {
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
                if (walkability.back() && !config.alwaysHoldW) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.SWITCHING_LANE);
                } else if (walkability.left()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.LEFT);
                } else {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.NONE);
                }
            }
            case RIGHT -> {
                if (walkability.back() && !config.alwaysHoldW) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.SWITCHING_LANE);
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
                if (client.player.isOnGround()) {
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
        float routingYaw = executor.getLaneRoutingYaw();
        switch (state) {
            case RIGHT -> {
                executor.holdMovement(client, config, false, false, false, true, false, true);
                executor.setDirectionLabel("SDS_RIGHT");
            }
            case LEFT -> {
                executor.holdMovement(client, config, false, false, true, false, false, true);
                executor.setDirectionLabel("SDS_LEFT");
            }
            case SWITCHING_LANE -> {
                if (!executor.isWalkableOffset(client, 0, -1, routingYaw)) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.NONE);
                    executor.stopAllMovement(client);
                } else {
                    executor.holdMovement(client, config, false, true, false, false, false, true);
                    executor.setDirectionLabel("SDS_SWITCH");
                }
            }
            default -> {
            }
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

package com.jelly.farmhelper.fabric.macro;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import net.minecraft.client.MinecraftClient;

final class CocoaMacroController implements LegacyMovementController {
    private final MovementMacroExecutor executor;

    CocoaMacroController(MovementMacroExecutor executor) {
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
            case BACKWARD -> {
                if (walkability.front() && !walkability.back() && walkability.right()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.SWITCHING_LANE);
                }
            }
            case FORWARD -> {
                if (!walkability.front() && walkability.back() && walkability.right() && !walkability.left()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.SWITCHING_SIDE);
                    return;
                }
                if (walkability.back()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.BACKWARD);
                } else if (walkability.front()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.FORWARD);
                } else {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.NONE);
                }
            }
            case SWITCHING_SIDE -> {
                if (walkability.back() && !walkability.right() && walkability.left()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.BACKWARD);
                }
            }
            case SWITCHING_LANE -> {
                if (!walkability.back() && !walkability.right() && walkability.left()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.FORWARD);
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
        boolean trapdoorMode = executor.getActiveType() == LegacyMacroType.S_COCOA_BEANS_TRAPDOORS;
        switch (state) {
            case BACKWARD -> {
                executor.holdMovement(client, config, false, true, false, false, false, true);
                executor.setDirectionLabel("COCOA_BACK");
            }
            case FORWARD -> {
                boolean hugWall = executor.shouldHugCocoaWall(client, routingYaw, trapdoorMode);
                executor.holdMovement(client, config, true, false, hugWall, false, false, true);
                executor.setDirectionLabel(hugWall ? "COCOA_FORWARD_HUG" : "COCOA_FORWARD");
            }
            case SWITCHING_LANE -> {
                if (executor.hasCocoaLineChanged(client, routingYaw)
                        && !walkability.back()
                        && walkability.left()) {
                    executor.setRouteState(MovementMacroExecutor.LegacyRouteState.FORWARD);
                    break;
                }
                executor.holdMovement(client, config, false, false, false, true, false, false);
                executor.setDirectionLabel("COCOA_SWITCH_LANE");
            }
            case SWITCHING_SIDE -> {
                executor.holdMovement(client, config, false, false, false, true, false, false);
                executor.setDirectionLabel("COCOA_SWITCH_SIDE");
            }
            default -> {
            }
        }
    }

    private MovementMacroExecutor.LegacyRouteState calculateDirection(MinecraftClient client) {
        float routingYaw = executor.getLaneRoutingYaw();
        MovementMacroExecutor.Walkability walkability = executor.computeWalkability(client, routingYaw);
        if (walkability.front() && walkability.right()) {
            return MovementMacroExecutor.LegacyRouteState.FORWARD;
        }
        if (walkability.back()) {
            return MovementMacroExecutor.LegacyRouteState.BACKWARD;
        }
        if (walkability.front()) {
            return MovementMacroExecutor.LegacyRouteState.FORWARD;
        }
        if (walkability.back() && walkability.left()) {
            return MovementMacroExecutor.LegacyRouteState.BACKWARD;
        }
        return MovementMacroExecutor.LegacyRouteState.NONE;
    }
}

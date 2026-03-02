package com.jelly.farmhelper.fabric.macro;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.mixin.client.KeyBindingAccessor;
import com.jelly.farmhelper.fabric.macro.CropMacroMotionMode;
import com.jelly.farmhelper.fabric.macro.CropMacroTuning;
import com.jelly.farmhelper.fabric.macro.CropMacroTunings;
import com.jelly.farmhelper.fabric.macro.CropYawMode;
import com.jelly.farmhelper.fabric.macro.LegacyMacroProfiles;
import com.jelly.farmhelper.fabric.macro.LegacyMacroType;
import com.jelly.farmhelper.fabric.macro.MacroPattern;
import com.jelly.farmhelper.fabric.macro.MacroStrategyProfile;
import com.jelly.farmhelper.fabric.macro.MacroState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShearsItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MovementMacroExecutor {
    private static final Pattern SKYBLOCK_ID_PATTERN = Pattern.compile("id[=:]\"?([A-Z0-9_]{5,})");
    private static final Pattern SKYBLOCK_TIER_PATTERN = Pattern.compile("_(\\d+)$");

    private enum LegacyRouteState {
        NONE,
        LEFT,
        RIGHT,
        FORWARD,
        BACKWARD,
        SWITCHING_SIDE,
        SWITCHING_LANE,
        A,
        D,
        S,
        W
    }

    private enum LaneShiftDirection {
        FORWARD,
        BACKWARD
    }

    private enum CocoaMotionState {
        FORWARD,
        BACKWARD,
        SWITCHING_SIDE,
        SWITCHING_LANE
    }

    private int primaryTicks;
    private int transitionTicksRemaining;
    private int cyclePhase;
    private boolean moveRight = true;
    private boolean laneForward = true;
    private int rotateDirection = 1;
    private long runtimeTicks;
    private long lastToolSwitchTick = -40L;

    private boolean initialized;
    private LegacyMacroType activeType = LegacyMacroType.S_V_NORMAL_TYPE;
    private CropMacroTuning activeTuning = CropMacroTunings.forType(activeType);
    private float baseYaw;
    private float targetYaw;
    private float targetPitch;
    private String directionLabel = "IDLE";
    private LaneShiftDirection laneShiftDirection = LaneShiftDirection.FORWARD;
    private CocoaMotionState cocoaMotionState = CocoaMotionState.FORWARD;
    private LegacyRouteState routeState = LegacyRouteState.NONE;
    private LegacyRouteState previousRouteState = LegacyRouteState.NONE;
    private LegacyRouteState lastMelonYawState = LegacyRouteState.NONE;
    private int routeStateTicks;
    private float laneReferenceYaw;

    private Vec3d lastMoveSample;
    private int stuckTicks;
    private int recoveryTicksRemaining;
    private int recoveryStrafeDirection = 1;
    private int laneSwitchStallTicks;
    private float sampledYawStep = 6f;
    private float sampledPitchStep = 3f;
    private long nextRotationSampleTick = -1L;
    private boolean movementInjected;
    private final PlayerSimulation playerSimulation = new PlayerSimulation();

    public void tick(MinecraftClient client) {
        boolean shouldRun = shouldRun(client);
        if (!shouldRun) {
            if (movementInjected) {
                stopAllMovement(client);
                movementInjected = false;
            }
            return;
        }
        movementInjected = true;

        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        LegacyMacroType macroType = config.macroType == null ? LegacyMacroType.S_V_NORMAL_TYPE : config.macroType;
        MacroStrategyProfile profile = LegacyMacroProfiles.forType(macroType);
        int forwardTicks = config.useLegacyProfileDefaults ? profile.defaultForwardTicks() : Math.max(20, config.forwardTicksBeforeTurn);
        int sideTicks = config.useLegacyProfileDefaults ? profile.defaultSideStepTicks() : Math.max(1, config.sideStepTicks);
        boolean holdAttack = config.holdAttackWhileMacroing || profile.holdAttack();

        if (!initialized || activeType != macroType) {
            initializeForType(client, config, macroType);
        }

        maybeAutoSelectTool(client, config, false);

        if (recoveryTicksRemaining > 0) {
            if (applyRecoveryMovement(client, config, holdAttack)) {
                runtimeTicks++;
                updateStuckHeuristics(client, config);
                return;
            }
        }

        if (config.useLegacyProfileDefaults) {
            applyOrientation(client, config);
            tickLegacyProfileMode(client, config, forwardTicks, sideTicks, holdAttack);
        } else {
            applyCustomOrientation(client, config);
            tickGenericPattern(client, config, forwardTicks, sideTicks, holdAttack);
        }

        updateStuckHeuristics(client, config);
        runtimeTicks++;
    }

    public void reset(MinecraftClient client) {
        primaryTicks = 0;
        transitionTicksRemaining = 0;
        cyclePhase = 0;
        moveRight = true;
        laneForward = true;
        rotateDirection = 1;
        runtimeTicks = 0L;
        lastToolSwitchTick = -40L;
        laneShiftDirection = LaneShiftDirection.FORWARD;
        cocoaMotionState = CocoaMotionState.FORWARD;
        routeState = LegacyRouteState.NONE;
        previousRouteState = LegacyRouteState.NONE;
        lastMelonYawState = LegacyRouteState.NONE;
        routeStateTicks = 0;
        lastMoveSample = null;
        stuckTicks = 0;
        recoveryTicksRemaining = 0;
        recoveryStrafeDirection = 1;
        laneSwitchStallTicks = 0;
        sampledYawStep = 6f;
        sampledPitchStep = 3f;
        nextRotationSampleTick = -1L;
        laneReferenceYaw = 0f;
        initialized = false;
        directionLabel = "IDLE";
        playerSimulation.reset();
        stopAllMovement(client);
        movementInjected = false;
    }

    private void initializeForType(MinecraftClient client, FarmHelperConfig config, LegacyMacroType macroType) {
        activeType = macroType;
        activeTuning = CropMacroTunings.forType(macroType);
        primaryTicks = 0;
        transitionTicksRemaining = 0;
        cyclePhase = 0;
        moveRight = true;
        laneForward = true;
        rotateDirection = 1;
        runtimeTicks = 0L;
        directionLabel = "STARTING";
        laneShiftDirection = LaneShiftDirection.FORWARD;
        cocoaMotionState = CocoaMotionState.FORWARD;
        routeState = LegacyRouteState.NONE;
        previousRouteState = LegacyRouteState.NONE;
        lastMelonYawState = LegacyRouteState.NONE;
        routeStateTicks = 0;
        lastMoveSample = null;
        stuckTicks = 0;
        recoveryTicksRemaining = 0;
        recoveryStrafeDirection = 1;
        laneSwitchStallTicks = 0;
        sampledYawStep = 6f;
        sampledPitchStep = 3f;
        nextRotationSampleTick = -1L;

        ClientPlayerEntity player = client.player;
        if (player == null) {
            initialized = true;
            return;
        }

        baseYaw = config.customYaw
                ? config.customYawLevel
                : resolveBaseYaw(player.getYaw(), activeTuning.yawMode());
        laneReferenceYaw = closestCardinal(baseYaw);
        targetYaw = baseYaw;
        if (activeTuning.motionMode() == CropMacroMotionMode.MUSHROOM_ROTATE && !config.customYaw) {
            targetYaw = baseYaw + activeTuning.rotateYawOffsetDegrees();
        }
        targetPitch = config.customPitch
                ? config.customPitchLevel
                : randomBetween(activeTuning.pitchMin(), activeTuning.pitchMax());

        if (activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT) {
            moveRight = resolveInitialMelonDirection(client, targetYaw);
        }
        if (activeTuning.motionMode() == CropMacroMotionMode.COCOA_STRAFE) {
            cocoaMotionState = resolveInitialCocoaState(client, targetYaw);
        }
        initializeRouteState(client);
        if (activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT) {
            updateMelonOrientationForState(config, true);
        }
        maybeAutoSelectTool(client, config, true);
        initialized = true;
    }

    private boolean shouldRun(MinecraftClient client) {
        return FarmHelperFabric.getMacroController().isToggled()
                && FarmHelperFabric.getMacroController().getState() == MacroState.FARMING
                && !FarmHelperFabric.getFailsafeManager().hasActiveFailsafe()
                && client.player != null
                && client.world != null
                && client.currentScreen == null;
    }

    private void tickCropSpecificMode(MinecraftClient client, FarmHelperConfig config, int forwardTicks, int sideTicks, boolean holdAttack) {
        switch (activeTuning.motionMode()) {
            case LANE_STRAFE -> tickLaneStrafe(client, config, forwardTicks, sideTicks, holdAttack);
            case BACKWARD_SWEEP -> tickBackwardSweep(client, config, forwardTicks, sideTicks, holdAttack);
            case COCOA_STRAFE -> tickCocoaStrafe(client, config, forwardTicks, sideTicks, holdAttack);
            case MUSHROOM_45 -> tickMushroom45(client, config, forwardTicks, holdAttack);
            case MUSHROOM_ROTATE -> tickMushroomRotate(client, config, forwardTicks, holdAttack);
            case MUSHROOM_SDS -> tickMushroomSds(client, config, forwardTicks, sideTicks, holdAttack);
            case CIRCULAR -> tickCircular(client, config, forwardTicks, holdAttack);
        }
    }

    private void tickLegacyProfileMode(MinecraftClient client, FarmHelperConfig config, int forwardTicks, int sideTicks, boolean holdAttack) {
        switch (activeType) {
            case S_V_NORMAL_TYPE,
                    S_PUMPKIN_MELON,
                    S_PUMPKIN_MELON_MELONGKINGDE,
                    S_CACTUS,
                    S_CACTUS_SUNTZU -> tickLegacyVerticalLane(client, config, sideTicks, holdAttack);
            case S_PUMPKIN_MELON_DEFAULT_PLOT -> tickLegacyMelonDefault(client, config, holdAttack);
            case S_SUGAR_CANE -> tickLegacySugarCane(client, config, holdAttack);
            case S_MUSHROOM -> tickLegacyMushroom45(client, config, holdAttack);
            case S_MUSHROOM_ROTATE -> tickLegacyMushroomRotate(client, config, holdAttack);
            case S_MUSHROOM_SDS -> tickLegacyMushroomSds(client, config, holdAttack);
            case C_NORMAL_TYPE -> tickCircular(client, config, Math.max(4, forwardTicks / 3), holdAttack);
            case S_COCOA_BEANS, S_COCOA_BEANS_TRAPDOORS, S_COCOA_BEANS_LEFT_RIGHT ->
                    tickCocoaStrafe(client, config, forwardTicks, sideTicks, holdAttack);
            default -> tickCropSpecificMode(client, config, forwardTicks, sideTicks, holdAttack);
        }
        routeStateTicks++;
    }

    private void initializeRouteState(MinecraftClient client) {
        setRouteState(switch (activeType) {
            case S_SUGAR_CANE -> LegacyRouteState.S;
            case C_NORMAL_TYPE -> LegacyRouteState.D;
            case S_MUSHROOM, S_MUSHROOM_ROTATE, S_MUSHROOM_SDS -> calculateMushroomDirection(client);
            case S_PUMPKIN_MELON_DEFAULT_PLOT, S_V_NORMAL_TYPE, S_PUMPKIN_MELON, S_PUMPKIN_MELON_MELONGKINGDE, S_CACTUS, S_CACTUS_SUNTZU ->
                    calculateLaneDirection(client, activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT);
            default -> LegacyRouteState.NONE;
        });
    }

    private void setRouteState(LegacyRouteState nextState) {
        if (nextState == null || nextState == routeState) {
            return;
        }
        previousRouteState = routeState;
        routeState = nextState;
        routeStateTicks = 0;
        if (activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT) {
            updateMelonOrientationForState(FarmHelperFabric.getConfigManager().getConfig(), false);
        }
    }

    private boolean canFlipRouteState() {
        return routeStateTicks >= 3;
    }

    private void tickLegacyVerticalLane(MinecraftClient client, FarmHelperConfig config, int sideTicks, boolean holdAttack) {
        Walkability walkability = computeWalkability(client, targetYaw);
        if (routeState == LegacyRouteState.NONE) {
            setRouteState(calculateLaneDirection(client, false));
        }

        if (routeState == LegacyRouteState.LEFT || routeState == LegacyRouteState.RIGHT) {
            boolean wantsRight = routeState == LegacyRouteState.RIGHT;
            boolean sideWalkable = wantsRight ? walkability.right : walkability.left;

            if (!sideWalkable) {
                if (walkability.front) {
                    laneShiftDirection = LaneShiftDirection.FORWARD;
                    setRouteState(LegacyRouteState.SWITCHING_LANE);
                    return;
                }
                if (walkability.back
                        && activeType != LegacyMacroType.S_CACTUS
                        && activeType != LegacyMacroType.S_CACTUS_SUNTZU
                        && !config.alwaysHoldW) {
                    laneShiftDirection = LaneShiftDirection.BACKWARD;
                    setRouteState(LegacyRouteState.SWITCHING_LANE);
                    return;
                }
                if ((wantsRight && walkability.left) || (!wantsRight && walkability.right)) {
                    setRouteState(wantsRight ? LegacyRouteState.LEFT : LegacyRouteState.RIGHT);
                    return;
                }
                setRouteState(LegacyRouteState.NONE);
                return;
            }

            moveRight = wantsRight;
            boolean forward = config.alwaysHoldW || shouldForwardDuringStrafe(activeType);
            holdMovement(client, config, forward, false, !wantsRight, wantsRight, false, holdAttack);
            directionLabel = wantsRight ? "RIGHT" : "LEFT";
            return;
        }

        if (routeState == LegacyRouteState.SWITCHING_LANE) {
            boolean forward = laneShiftDirection == LaneShiftDirection.FORWARD || config.alwaysHoldW;
            boolean back = laneShiftDirection == LaneShiftDirection.BACKWARD && !config.alwaysHoldW;
            holdMovement(client, config, forward, back, false, false, false, holdAttack);
            directionLabel = laneShiftDirection == LaneShiftDirection.FORWARD ? "SWITCH_FWD" : "SWITCH_BACK";

            if (walkability.left || walkability.right) {
                setRouteState(calculateLaneDirection(client, false));
                return;
            }
            if (routeStateTicks > Math.max(12, sideTicks * 2)) {
                laneShiftDirection = laneShiftDirection == LaneShiftDirection.FORWARD
                        ? LaneShiftDirection.BACKWARD
                        : LaneShiftDirection.FORWARD;
                if (routeStateTicks > Math.max(20, sideTicks * 3)) {
                    recoveryTicksRemaining = 14;
                    recoveryStrafeDirection = moveRight ? -1 : 1;
                    setRouteState(LegacyRouteState.NONE);
                }
            }
            return;
        }

        setRouteState(calculateLaneDirection(client, false));
    }

    private void tickLegacyMelonDefault(MinecraftClient client, FarmHelperConfig config, boolean holdAttack) {
        Walkability walkability = computeWalkability(client, targetYaw);
        if (routeState == LegacyRouteState.NONE) {
            setRouteState(calculateLaneDirection(client, true));
        }

        if (routeState == LegacyRouteState.LEFT || routeState == LegacyRouteState.RIGHT) {
            if (isMelonOrPumpkinRelative(client, -1, 0, targetYaw)) {
                setRouteState(LegacyRouteState.LEFT);
            } else if (isMelonOrPumpkinRelative(client, 1, 0, targetYaw)) {
                setRouteState(LegacyRouteState.RIGHT);
            }

            boolean wantsRight = routeState == LegacyRouteState.RIGHT;
            boolean sideWalkable = wantsRight ? walkability.right : walkability.left;

            if (!sideWalkable) {
                if (walkability.front) {
                    laneShiftDirection = LaneShiftDirection.FORWARD;
                    setRouteState(LegacyRouteState.SWITCHING_LANE);
                    return;
                }
                if (walkability.back && !config.alwaysHoldW) {
                    laneShiftDirection = LaneShiftDirection.BACKWARD;
                    setRouteState(LegacyRouteState.SWITCHING_LANE);
                    return;
                }
                if (walkability.left || walkability.right) {
                    setRouteState(walkability.right ? LegacyRouteState.RIGHT : LegacyRouteState.LEFT);
                    return;
                }
                setRouteState(LegacyRouteState.NONE);
                return;
            }

            moveRight = wantsRight;
            boolean hugBackWall = !walkability.back;
            holdMovement(client, config, hugBackWall, false, !wantsRight, wantsRight, false, holdAttack);
            directionLabel = wantsRight ? "MELON_RIGHT" : "MELON_LEFT";
            return;
        }

        if (routeState == LegacyRouteState.SWITCHING_LANE) {
            double velocity = 0.0;
            if (client.player != null) {
                velocity = Math.abs(client.player.getVelocity().x) + Math.abs(client.player.getVelocity().z);
            }
            if (velocity < 0.15 && !walkability.front) {
                holdMovement(client, config, false, false, false, false, false, false);
                return;
            }
            holdMovement(client, config, true, false, false, false, true, false);
            directionLabel = "MELON_SWITCH";

            if (walkability.right || walkability.left) {
                setRouteState(calculateLaneDirection(client, true));
                return;
            }
            if (routeStateTicks > 40) {
                setRouteState(LegacyRouteState.NONE);
            }
            return;
        }

        setRouteState(calculateLaneDirection(client, true));
    }

    private void tickLegacySugarCane(MinecraftClient client, FarmHelperConfig config, boolean holdAttack) {
        LegacyRouteState calculated = calculateSugarcaneDirection(client);
        if (calculated != LegacyRouteState.NONE) {
            setRouteState(calculated);
        }

        switch (routeState) {
            case A -> {
                holdMovement(client, config, false, false, true, false, false, holdAttack);
                directionLabel = "SUGARCANE_A";
            }
            case D -> {
                holdMovement(client, config, false, false, false, true, false, holdAttack);
                directionLabel = "SUGARCANE_D";
            }
            default -> {
                holdMovement(client, config, false, true, false, false, false, holdAttack);
                directionLabel = "SUGARCANE_S";
                setRouteState(LegacyRouteState.S);
            }
        }
    }

    private void tickLegacyMushroom45(MinecraftClient client, FarmHelperConfig config, boolean holdAttack) {
        Walkability walkability = computeWalkability(client, targetYaw);
        if (routeState == LegacyRouteState.NONE) {
            setRouteState(calculateMushroomDirection(client));
        }

        if (routeState == LegacyRouteState.RIGHT) {
            if (walkability.left && canFlipRouteState()) {
                setRouteState(LegacyRouteState.LEFT);
            } else if (!walkability.right) {
                setRouteState(LegacyRouteState.RIGHT);
            } else if (!walkability.left && !walkability.right) {
                setRouteState(calculateMushroomDirection(client));
            }
        } else if (routeState == LegacyRouteState.LEFT) {
            if (walkability.right && canFlipRouteState()) {
                setRouteState(LegacyRouteState.RIGHT);
            } else if (!walkability.left) {
                setRouteState(LegacyRouteState.LEFT);
            } else if (!walkability.left && !walkability.right) {
                setRouteState(calculateMushroomDirection(client));
            }
        } else {
            setRouteState(calculateMushroomDirection(client));
        }

        boolean right = routeState == LegacyRouteState.RIGHT;
        if (config.alwaysHoldW) {
            holdMovement(client, config, true, false, false, false, false, holdAttack);
        } else {
            holdMovement(client, config, right, false, !right, right, false, holdAttack);
        }
        directionLabel = right ? "MUSHROOM_RIGHT" : "MUSHROOM_LEFT";
    }

    private void tickLegacyMushroomRotate(MinecraftClient client, FarmHelperConfig config, boolean holdAttack) {
        Walkability walkability = computeWalkability(client, targetYaw);
        if (routeState == LegacyRouteState.NONE) {
            setRouteState(calculateMushroomDirection(client));
        }

        LegacyRouteState before = routeState;
        if (routeState == LegacyRouteState.RIGHT) {
            if (walkability.left && canFlipRouteState()) {
                setRouteState(LegacyRouteState.LEFT);
            } else if (!walkability.right) {
                setRouteState(LegacyRouteState.RIGHT);
            } else if (!walkability.left && !walkability.right) {
                setRouteState(calculateMushroomDirection(client));
            }
        } else if (routeState == LegacyRouteState.LEFT) {
            if (walkability.right && canFlipRouteState()) {
                setRouteState(LegacyRouteState.RIGHT);
            } else if (!walkability.left) {
                setRouteState(LegacyRouteState.LEFT);
            } else if (!walkability.left && !walkability.right) {
                setRouteState(calculateMushroomDirection(client));
            }
        } else {
            setRouteState(calculateMushroomDirection(client));
        }

        if (before != routeState && !config.customYaw) {
            float side = routeState == LegacyRouteState.LEFT ? -1f : 1f;
            float jitter = (float) ThreadLocalRandom.current().nextDouble(-2.0, 2.0);
            targetYaw = baseYaw + side * activeTuning.rotateYawOffsetDegrees() + jitter;
        }
        if (!config.customPitch && (before != routeState || routeStateTicks % 18 == 0)) {
            targetPitch = randomBetween(activeTuning.pitchMin(), activeTuning.pitchMax());
        }

        holdMovement(client, config, true, false, false, false, false, holdAttack);
        directionLabel = routeState == LegacyRouteState.LEFT ? "ROTATE_LEFT" : "ROTATE_RIGHT";
    }

    private void tickLegacyMushroomSds(MinecraftClient client, FarmHelperConfig config, boolean holdAttack) {
        Walkability walkability = computeWalkability(client, targetYaw);
        if (routeState == LegacyRouteState.NONE) {
            setRouteState(calculateMushroomDirection(client));
        }

        switch (routeState) {
            case LEFT -> {
                if (walkability.back && !config.alwaysHoldW) {
                    setRouteState(LegacyRouteState.SWITCHING_LANE);
                    return;
                }
                if (!walkability.left) {
                    setRouteState(LegacyRouteState.NONE);
                    return;
                }
                holdMovement(client, config, false, false, true, false, false, holdAttack);
                directionLabel = "SDS_LEFT";
            }
            case RIGHT -> {
                if (walkability.back && !config.alwaysHoldW) {
                    setRouteState(LegacyRouteState.SWITCHING_LANE);
                    return;
                }
                if (!walkability.right) {
                    setRouteState(LegacyRouteState.NONE);
                    return;
                }
                holdMovement(client, config, false, false, false, true, false, holdAttack);
                directionLabel = "SDS_RIGHT";
            }
            case SWITCHING_LANE -> {
                if (!isWalkableRelative(client, 0.0, -1.0, targetYaw)) {
                    setRouteState(LegacyRouteState.NONE);
                    holdMovement(client, config, false, false, false, false, false, false);
                    return;
                }
                holdMovement(client, config, false, true, false, false, false, holdAttack);
                directionLabel = "SDS_SWITCH";
                if (routeStateTicks > 18) {
                    setRouteState(calculateMushroomDirection(client));
                }
            }
            default -> setRouteState(calculateMushroomDirection(client));
        }
    }

    private LegacyRouteState calculateLaneDirection(MinecraftClient client, boolean melonPriority) {
        for (int i = 1; i < 180; i++) {
            if (melonPriority && isMelonOrPumpkinRelative(client, i, 0, targetYaw)) {
                return LegacyRouteState.RIGHT;
            }
            if (melonPriority && isMelonOrPumpkinRelative(client, -i, 0, targetYaw)) {
                return LegacyRouteState.LEFT;
            }
            if (!isWalkableOffset(client, i, 0, targetYaw)) {
                return LegacyRouteState.LEFT;
            }
            if (!isWalkableOffset(client, -i, 0, targetYaw)) {
                return LegacyRouteState.RIGHT;
            }
        }
        Walkability walkability = computeWalkability(client, targetYaw);
        if (walkability.right && !walkability.left) {
            return LegacyRouteState.RIGHT;
        }
        if (walkability.left && !walkability.right) {
            return LegacyRouteState.LEFT;
        }
        return moveRight ? LegacyRouteState.RIGHT : LegacyRouteState.LEFT;
    }

    private LegacyRouteState calculateMushroomDirection(MinecraftClient client) {
        for (int i = 1; i < 180; i++) {
            if (!isWalkableOffset(client, i, 0, targetYaw)) {
                return LegacyRouteState.LEFT;
            }
            if (!isWalkableOffset(client, -i, 0, targetYaw)) {
                return LegacyRouteState.RIGHT;
            }
        }
        Walkability walkability = computeWalkability(client, targetYaw);
        if (walkability.right && !walkability.left) {
            return LegacyRouteState.RIGHT;
        }
        if (walkability.left && !walkability.right) {
            return LegacyRouteState.LEFT;
        }
        return moveRight ? LegacyRouteState.RIGHT : LegacyRouteState.LEFT;
    }

    private LegacyRouteState calculateSugarcaneDirection(MinecraftClient client) {
        boolean leftWater = isWaterRelativeAtYaw(client, 2, -1, 1, targetYaw - 45f)
                || isWaterRelativeAtYaw(client, 2, 0, 1, targetYaw - 45f)
                || isWaterRelativeAtYaw(client, -1, -1, 1, targetYaw - 45f)
                || isWaterRelativeAtYaw(client, -1, 0, 1, targetYaw - 45f);
        if (leftWater && !(hasWallAtYaw(client, 0, 1, targetYaw - 45f) && hasWallAtYaw(client, -1, 0, targetYaw - 45f))) {
            return LegacyRouteState.A;
        }
        boolean rightWater = isWaterRelativeAtYaw(client, 2, -1, 1, targetYaw + 45f)
                || isWaterRelativeAtYaw(client, 2, 0, 1, targetYaw + 45f)
                || isWaterRelativeAtYaw(client, -1, -1, 1, targetYaw + 45f)
                || isWaterRelativeAtYaw(client, -1, 0, 1, targetYaw + 45f);
        if (rightWater && !(hasWallAtYaw(client, 0, 1, targetYaw + 45f) && hasWallAtYaw(client, 1, 0, targetYaw + 45f))) {
            return LegacyRouteState.D;
        }
        return LegacyRouteState.S;
    }

    private boolean isWalkableOffset(MinecraftClient client, int lateral, int forward, float yawRef) {
        BlockPos feet = getRelativeBlockPos(client, lateral, forward, yawRef);
        if (feet == null || client.world == null) {
            return false;
        }
        return isPassable(client, feet) && isPassable(client, feet.up()) && hasSolidGround(client, feet.down());
    }

    private boolean hasWallAtYaw(MinecraftClient client, int lateral, int forward, float yawRef) {
        BlockPos pos = getRelativeBlockPos(client, lateral, forward, yawRef);
        if (pos == null) {
            return true;
        }
        return !isPassable(client, pos);
    }

    private BlockState getRelativeBlockStateAtYaw(MinecraftClient client, int lateral, int vertical, int forward, float yawRef) {
        BlockPos pos = getRelativeBlockPos(client, lateral, forward, yawRef);
        if (pos == null || client.world == null) {
            return null;
        }
        return client.world.getBlockState(pos.add(0, vertical, 0));
    }

    private boolean isWaterRelativeAtYaw(MinecraftClient client, int lateral, int vertical, int forward, float yawRef) {
        BlockState state = getRelativeBlockStateAtYaw(client, lateral, vertical, forward, yawRef);
        if (state == null) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return true;
        }
        String key = state.getBlock().getTranslationKey().toLowerCase(Locale.ROOT);
        return key.contains("water");
    }

    private void tickGenericPattern(MinecraftClient client, FarmHelperConfig config, int forwardBeforeTurn, int sideStepTicks, boolean holdAttack) {
        MacroPattern effectivePattern = getEffectivePattern(config);
        if (effectivePattern == MacroPattern.S_SHAPE) {
            tickGenericSShape(client, config, forwardBeforeTurn, sideStepTicks, holdAttack);
        } else {
            tickGenericBasicRow(client, config, forwardBeforeTurn, sideStepTicks, holdAttack);
        }
    }

    private void tickGenericBasicRow(MinecraftClient client, FarmHelperConfig config, int forwardBeforeTurn, int sideStepTicks, boolean holdAttack) {
        if (transitionTicksRemaining > 0) {
            transitionTicksRemaining--;
            holdMovement(client, config, true, false, !moveRight, moveRight, false, holdAttack);
            directionLabel = moveRight ? "RIGHT" : "LEFT";
            return;
        }

        holdMovement(client, config, true, false, false, false, false, holdAttack);
        primaryTicks++;
        directionLabel = "FORWARD";

        if (primaryTicks >= forwardBeforeTurn) {
            primaryTicks = 0;
            transitionTicksRemaining = computeRowChangeTicks(config, sideStepTicks, 1);
            moveRight = !moveRight;
        }
    }

    private void tickGenericSShape(MinecraftClient client, FarmHelperConfig config, int forwardBeforeTurn, int sideStepTicks, boolean holdAttack) {
        int adjustedSideStep = Math.max(1, sideStepTicks * 2);
        if (transitionTicksRemaining > 0) {
            transitionTicksRemaining--;
            holdMovement(client, config, true, false, !moveRight, moveRight, false, holdAttack);
            directionLabel = moveRight ? "S_RIGHT" : "S_LEFT";
            return;
        }

        holdMovement(client, config, true, false, false, false, false, holdAttack);
        primaryTicks++;
        directionLabel = "S_FORWARD";

        if (primaryTicks >= forwardBeforeTurn) {
            primaryTicks = 0;
            transitionTicksRemaining = computeRowChangeTicks(config, adjustedSideStep, 2);
            moveRight = !moveRight;
        }
    }

    private void tickLaneStrafe(MinecraftClient client, FarmHelperConfig config, int forwardTicks, int sideTicks, boolean holdAttack) {
        Walkability walkability = computeWalkability(client, targetYaw);
        if (activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT) {
            if (isMelonOrPumpkinRelative(client, -1, 0, targetYaw)) {
                moveRight = false;
            } else if (isMelonOrPumpkinRelative(client, 1, 0, targetYaw)) {
                moveRight = true;
            }
        }

        if (transitionTicksRemaining > 0) {
            transitionTicksRemaining--;
            boolean forward = laneShiftDirection == LaneShiftDirection.FORWARD || config.alwaysHoldW;
            boolean back = laneShiftDirection == LaneShiftDirection.BACKWARD && !config.alwaysHoldW;
            holdMovement(client, config, forward, back, false, false, laneForward, holdAttack);
            directionLabel = laneForward ? "SWITCH_FWD" : "SWITCH_BACK";

            if (detectLaneSwitchStall(walkability, sideTicks)) {
                startLaneSwitchRecovery();
                return;
            }

            if (transitionTicksRemaining == 0) {
                laneForward = laneShiftDirection == LaneShiftDirection.FORWARD;
                if (activeType != LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT) {
                    moveRight = !moveRight;
                } else if (walkability.right && !walkability.left) {
                    moveRight = true;
                } else if (!walkability.right && walkability.left) {
                    moveRight = false;
                } else {
                    moveRight = !moveRight;
                }
                laneSwitchStallTicks = 0;
            }
            return;
        }

        if (activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT) {
            if (moveRight && !walkability.right && walkability.left) {
                moveRight = false;
            } else if (!moveRight && !walkability.left && walkability.right) {
                moveRight = true;
            } else if (!walkability.left && !walkability.right && (walkability.front || walkability.back)) {
                laneShiftDirection = walkability.front ? LaneShiftDirection.FORWARD : LaneShiftDirection.BACKWARD;
                laneForward = laneShiftDirection == LaneShiftDirection.FORWARD;
                transitionTicksRemaining = computeRowChangeTicks(config, Math.max(4, sideTicks), 4);
                return;
            }
        }

        primaryTicks++;
        boolean strafeLeft = !moveRight;
        boolean strafeRight = moveRight;
        boolean huggingBackWall = !walkability.back;
        boolean forward = config.alwaysHoldW
                || shouldForwardDuringStrafe(activeType)
                || (activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT && huggingBackWall);
        holdMovement(client, config, forward, false, strafeLeft, strafeRight, false, holdAttack);
        directionLabel = moveRight ? "LANE_RIGHT" : "LANE_LEFT";

        boolean shouldSwitch = primaryTicks >= forwardTicks;
        if (activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT) {
            if (!walkability.front && walkability.back && !config.alwaysHoldW) {
                shouldSwitch = true;
                laneShiftDirection = LaneShiftDirection.BACKWARD;
            } else if (walkability.front && !walkability.back && !config.alwaysHoldW) {
                laneShiftDirection = LaneShiftDirection.FORWARD;
            } else if (laneShiftDirection == LaneShiftDirection.BACKWARD && walkability.front) {
                laneShiftDirection = LaneShiftDirection.FORWARD;
            }
        } else {
            laneShiftDirection = LaneShiftDirection.FORWARD;
        }

        if (shouldSwitch) {
            primaryTicks = 0;
            transitionTicksRemaining = computeRowChangeTicks(config, Math.max(2, sideTicks), 2);
            laneSwitchStallTicks = 0;
        }
    }

    private void tickBackwardSweep(MinecraftClient client, FarmHelperConfig config, int forwardTicks, int sideTicks, boolean holdAttack) {
        Walkability walkability = computeWalkability(client, targetYaw);
        if (!walkability.back) {
            if (walkability.left && !walkability.right) {
                holdMovement(client, config, false, false, true, false, false, holdAttack);
                directionLabel = "BACKSWEEP_AVOID_RIGHT";
                primaryTicks = 0;
                cyclePhase = 0;
                return;
            }
            if (!walkability.left && walkability.right) {
                holdMovement(client, config, false, false, false, true, false, holdAttack);
                directionLabel = "BACKSWEEP_AVOID_LEFT";
                primaryTicks = 0;
                cyclePhase = 0;
                return;
            }
            if (walkability.front) {
                holdMovement(client, config, true, false, false, false, false, holdAttack);
                directionLabel = "BACKSWEEP_FORWARD_RESET";
                primaryTicks = 0;
                cyclePhase = 1;
                return;
            }
        }

        int segment = cyclePhase % 4;
        int duration = (segment == 0 || segment == 2) ? forwardTicks : sideTicks;
        primaryTicks++;

        switch (segment) {
            case 0, 2 -> {
                holdMovement(client, config, false, true, false, false, false, holdAttack);
                directionLabel = "BACKWARD";
            }
            case 1 -> {
                holdMovement(client, config, false, false, false, true, false, holdAttack);
                directionLabel = "RIGHT";
            }
            default -> {
                holdMovement(client, config, false, false, true, false, false, holdAttack);
                directionLabel = "LEFT";
            }
        }

        if (primaryTicks >= duration) {
            primaryTicks = 0;
            cyclePhase++;
        }
    }

    private void tickCocoaStrafe(MinecraftClient client, FarmHelperConfig config, int forwardTicks, int sideTicks, boolean holdAttack) {
        Walkability walkability = computeWalkability(client, targetYaw);

        switch (cocoaMotionState) {
            case FORWARD -> {
                boolean hugWall = shouldHugCocoaWall(client, targetYaw, activeType == LegacyMacroType.S_COCOA_BEANS_TRAPDOORS);
                holdMovement(client, config, true, false, hugWall, false, false, holdAttack);
                directionLabel = hugWall ? "COCOA_FORWARD_HUG" : "COCOA_FORWARD";
                primaryTicks++;

                if (!walkability.front && walkability.back && walkability.right && !walkability.left) {
                    cocoaMotionState = CocoaMotionState.SWITCHING_SIDE;
                    primaryTicks = 0;
                } else if (!walkability.front && walkability.back) {
                    cocoaMotionState = CocoaMotionState.BACKWARD;
                    primaryTicks = 0;
                } else if (primaryTicks >= Math.max(10, forwardTicks)) {
                    primaryTicks = 0;
                }
            }
            case BACKWARD -> {
                holdMovement(client, config, false, true, false, false, false, holdAttack);
                directionLabel = "COCOA_BACK";
                primaryTicks++;

                if (walkability.front && !walkability.back && walkability.right) {
                    cocoaMotionState = CocoaMotionState.SWITCHING_LANE;
                    primaryTicks = 0;
                } else if (walkability.front && !walkability.back) {
                    cocoaMotionState = CocoaMotionState.FORWARD;
                    primaryTicks = 0;
                } else if (primaryTicks >= Math.max(10, forwardTicks)) {
                    primaryTicks = 0;
                }
            }
            case SWITCHING_SIDE -> {
                holdMovement(client, config, false, false, false, true, false, false);
                directionLabel = "COCOA_SHIFT_RIGHT";
                primaryTicks++;

                if (walkability.back && !walkability.right && walkability.left) {
                    cocoaMotionState = CocoaMotionState.BACKWARD;
                    primaryTicks = 0;
                } else if (primaryTicks >= Math.max(6, sideTicks)) {
                    primaryTicks = 0;
                }
            }
            case SWITCHING_LANE -> {
                holdMovement(client, config, false, false, false, true, false, false);
                directionLabel = "COCOA_SWITCH_LANE";
                primaryTicks++;

                if (hasCocoaLineChanged(client, targetYaw) && !walkability.back && walkability.left) {
                    cocoaMotionState = CocoaMotionState.FORWARD;
                    primaryTicks = 0;
                } else if (!walkability.back && !walkability.right && walkability.left) {
                    cocoaMotionState = CocoaMotionState.FORWARD;
                    primaryTicks = 0;
                } else if (primaryTicks >= Math.max(12, sideTicks * 2)) {
                    cocoaMotionState = walkability.front ? CocoaMotionState.FORWARD : CocoaMotionState.BACKWARD;
                    primaryTicks = 0;
                }
            }
        }
    }

    private void tickMushroom45(MinecraftClient client, FarmHelperConfig config, int forwardTicks, boolean holdAttack) {
        primaryTicks++;
        holdMovement(client, config, true, false, !moveRight, moveRight, false, holdAttack);
        directionLabel = moveRight ? "MUSHROOM_DIAG_RIGHT" : "MUSHROOM_DIAG_LEFT";

        if (primaryTicks >= forwardTicks) {
            primaryTicks = 0;
            moveRight = !moveRight;
            if (!config.customPitch) {
                targetPitch = randomBetween(activeTuning.pitchMin(), activeTuning.pitchMax());
            }
        }
    }

    private void tickMushroomRotate(MinecraftClient client, FarmHelperConfig config, int forwardTicks, boolean holdAttack) {
        primaryTicks++;
        holdMovement(client, config, true, false, false, false, false, holdAttack);
        directionLabel = rotateDirection > 0 ? "ROTATE_RIGHT" : "ROTATE_LEFT";

        if (primaryTicks >= forwardTicks) {
            primaryTicks = 0;
            rotateDirection = -rotateDirection;
            if (!config.customYaw) {
                float jitter = (float) (ThreadLocalRandom.current().nextDouble(-2.0, 2.0));
                targetYaw = baseYaw + rotateDirection * activeTuning.rotateYawOffsetDegrees() + jitter;
            }
            if (!config.customPitch) {
                targetPitch = randomBetween(activeTuning.pitchMin(), activeTuning.pitchMax());
            }
        }
    }

    private void tickMushroomSds(MinecraftClient client, FarmHelperConfig config, int forwardTicks, int sideTicks, boolean holdAttack) {
        Walkability walkability = computeWalkability(client, targetYaw);
        if (transitionTicksRemaining > 0) {
            transitionTicksRemaining--;
            holdMovement(client, config, false, true, false, false, false, holdAttack);
            directionLabel = "SDS_SWITCH_BACK";
            if (transitionTicksRemaining == 0) {
                moveRight = !moveRight;
            }
            return;
        }

        if (moveRight && !walkability.right && walkability.left) {
            moveRight = false;
        } else if (!moveRight && !walkability.left && walkability.right) {
            moveRight = true;
        }

        primaryTicks++;
        holdMovement(client, config, false, false, !moveRight, moveRight, false, holdAttack);
        directionLabel = moveRight ? "SDS_RIGHT" : "SDS_LEFT";

        boolean shouldSwitch = primaryTicks >= forwardTicks;
        if (!config.alwaysHoldW && walkability.back && (!walkability.left || !walkability.right)) {
            shouldSwitch = true;
        }
        if (shouldSwitch) {
            primaryTicks = 0;
            transitionTicksRemaining = computeRowChangeTicks(config, Math.max(2, sideTicks / 2), 2);
        }
    }

    private void tickCircular(MinecraftClient client, FarmHelperConfig config, int forwardTicks, boolean holdAttack) {
        int segment = cyclePhase % 4;
        primaryTicks++;
        switch (segment) {
            case 0 -> {
                holdMovement(client, config, false, false, false, true, false, holdAttack);
                directionLabel = "CIRCLE_D";
            }
            case 1 -> {
                holdMovement(client, config, false, true, false, false, false, holdAttack);
                directionLabel = "CIRCLE_S";
            }
            case 2 -> {
                holdMovement(client, config, false, false, true, false, false, holdAttack);
                directionLabel = "CIRCLE_A";
            }
            default -> {
                holdMovement(client, config, true, false, false, false, false, holdAttack);
                directionLabel = "CIRCLE_W";
            }
        }

        if (primaryTicks >= forwardTicks) {
            primaryTicks = 0;
            cyclePhase++;
        }
    }

    private record Walkability(boolean front, boolean back, boolean left, boolean right) {
    }

    private Walkability computeWalkability(MinecraftClient client, float yawRef) {
        boolean front = isWalkableRelative(client, 0.0, 1.0, yawRef);
        boolean back = isWalkableRelative(client, 0.0, -1.0, yawRef);
        boolean left = isWalkableRelative(client, -1.0, 0.0, yawRef);
        boolean right = isWalkableRelative(client, 1.0, 0.0, yawRef);
        return new Walkability(front, back, left, right);
    }

    private boolean isWalkableRelative(MinecraftClient client, double lateral, double forward, float yawRef) {
        if (client.player == null || client.world == null) {
            return false;
        }
        BlockPos feetPos = getRelativeBlockPos(client, lateral, 0.0, forward, yawRef);
        if (feetPos == null) {
            return false;
        }
        BlockPos headPos = feetPos.up();
        BlockPos groundPos = feetPos.down();
        return isPassable(client, feetPos) && isPassable(client, headPos) && hasSolidGround(client, groundPos);
    }

    private boolean isPassable(MinecraftClient client, BlockPos pos) {
        if (client.world == null) {
            return false;
        }
        BlockState state = client.world.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        VoxelShape shape = state.getCollisionShape(client.world, pos);
        return shape.isEmpty();
    }

    private boolean hasSolidGround(MinecraftClient client, BlockPos pos) {
        if (client.world == null) {
            return false;
        }
        BlockState ground = client.world.getBlockState(pos);
        if (ground.isAir() || !ground.getFluidState().isEmpty()) {
            return false;
        }
        return !ground.getCollisionShape(client.world, pos).isEmpty();
    }

    private boolean isMelonOrPumpkinRelative(MinecraftClient client, int lateral, int forward, float yawRef) {
        BlockState state = getRelativeBlockState(client, lateral, forward, yawRef);
        if (state == null || state.isAir()) {
            return false;
        }
        String id = state.getBlock().toString().toLowerCase(Locale.ROOT);
        String key = state.getBlock().getTranslationKey().toLowerCase(Locale.ROOT);
        return id.contains("melon")
                || id.contains("pumpkin")
                || key.contains("melon")
                || key.contains("pumpkin");
    }

    private BlockPos getRelativeBlockPos(MinecraftClient client, int lateral, int forward, float yawRef) {
        return getRelativeBlockPos(client, (double) lateral, 0.0, (double) forward, yawRef);
    }

    private BlockPos getRelativeBlockPos(MinecraftClient client, double lateral, double vertical, double forward, float yawRef) {
        if (client.player == null || client.world == null) {
            return null;
        }
        double baseY = client.player.getY();
        if ((baseY % 1.0) > 0.7) {
            baseY = Math.ceil(baseY);
        }
        double unitX = getUnitX(yawRef);
        double unitZ = getUnitZ(yawRef);
        double x = client.player.getX() + unitX * forward + unitZ * -lateral;
        double y = baseY + vertical;
        double z = client.player.getZ() + unitZ * forward + unitX * lateral;
        return BlockPos.ofFloored(x, y, z);
    }

    private BlockState getRelativeBlockState(MinecraftClient client, int lateral, int forward, float yawRef) {
        BlockPos pos = getRelativeBlockPos(client, lateral, forward, yawRef);
        if (pos == null || client.world == null) {
            return null;
        }
        return client.world.getBlockState(pos);
    }

    private boolean shouldHugCocoaWall(MinecraftClient client, float yawRef, boolean trapdoorMode) {
        if (client.player == null || client.world == null) {
            return false;
        }
        BlockPos wallPos = getRelativeBlockPos(client, -1, 0, yawRef);
        if (wallPos == null) {
            return false;
        }
        BlockState wall = client.world.getBlockState(wallPos);
        if (wall == null || wall.isAir() || wall.getCollisionShape(client.world, wallPos).isEmpty()) {
            return false;
        }

        double x = client.player.getX() % 1.0;
        double z = client.player.getZ() % 1.0;
        float yaw = (closestCardinal(client.player.getYaw()) % 360f + 360f) % 360f;

        if (trapdoorMode) {
            if (yaw == 0f) return (x > -0.9 && x < -0.5) || (x < 0.5 && x > 0.1);
            if (yaw == 90f) return (z > -0.9 && z < -0.5) || (z < 0.5 && z > 0.1);
            if (yaw == 180f) return (x > -0.5 && x < -0.1) || (x < 0.9 && x > 0.5);
            if (yaw == 270f) return (z > -0.5 && z < -0.1) || (z < 0.9 && z > 0.5);
            return false;
        }

        if (yaw == 0f) return (x > -0.9 && x < -0.35) || (x < 0.65 && x > 0.1);
        if (yaw == 90f) return (z > -0.9 && z < -0.35) || (z < 0.65 && z > 0.1);
        if (yaw == 180f) return (x > -0.65 && x < -0.1) || (x < 0.9 && x > 0.35);
        if (yaw == 270f) return (z > -0.65 && z < -0.1) || (z < 0.9 && z > 0.35);
        return false;
    }

    private boolean hasCocoaLineChanged(MinecraftClient client, float yawRef) {
        BlockState probe = getRelativeBlockState(client, -1, 1, yawRef);
        if (probe == null || probe.isAir() || client.player == null || !isWalkableRelative(client, 0, 1, yawRef)) {
            return false;
        }
        double x = Math.abs(client.player.getX()) % 1.0;
        double z = Math.abs(client.player.getZ()) % 1.0;
        float yaw = (closestCardinal(client.player.getYaw()) % 360f + 360f) % 360f;
        if (yaw == 180f) return x > 0.488;
        if (yaw == 270f) return z > 0.488;
        if (yaw == 90f) return z < 0.512;
        if (yaw == 0f) return x < 0.512;
        return false;
    }

    private boolean resolveInitialMelonDirection(MinecraftClient client, float yawRef) {
        if (client.player == null || client.world == null) {
            return true;
        }
        for (int i = 1; i <= 24; i++) {
            if (isMelonOrPumpkinRelative(client, i, 0, yawRef)) {
                return true;
            }
            if (isMelonOrPumpkinRelative(client, -i, 0, yawRef)) {
                return false;
            }
            BlockPos rightPos = getRelativeBlockPos(client, i, 0, yawRef);
            BlockPos leftPos = getRelativeBlockPos(client, -i, 0, yawRef);
            if (rightPos != null && !isPassable(client, rightPos)) {
                return false;
            }
            if (leftPos != null && !isPassable(client, leftPos)) {
                return true;
            }
        }
        Walkability walkability = computeWalkability(client, yawRef);
        if (walkability.right && !walkability.left) {
            return true;
        }
        if (!walkability.right && walkability.left) {
            return false;
        }
        return moveRight;
    }

    private CocoaMotionState resolveInitialCocoaState(MinecraftClient client, float yawRef) {
        Walkability walkability = computeWalkability(client, yawRef);
        if (walkability.front && walkability.right) {
            return CocoaMotionState.FORWARD;
        }
        if (walkability.back && !walkability.front) {
            return CocoaMotionState.BACKWARD;
        }
        if (walkability.front) {
            return CocoaMotionState.FORWARD;
        }
        if (walkability.back) {
            return CocoaMotionState.BACKWARD;
        }
        return CocoaMotionState.FORWARD;
    }

    private boolean detectLaneSwitchStall(Walkability walkability, int sideTicks) {
        boolean frontOrBackStillOpen = laneShiftDirection == LaneShiftDirection.FORWARD
                ? walkability.front
                : walkability.back;
        boolean sideOpen = walkability.left || walkability.right;
        if (frontOrBackStillOpen && sideOpen) {
            laneSwitchStallTicks++;
        } else {
            laneSwitchStallTicks = 0;
        }
        return laneSwitchStallTicks >= Math.max(8, sideTicks + 4);
    }

    private void startLaneSwitchRecovery() {
        laneSwitchStallTicks = 0;
        transitionTicksRemaining = 0;
        primaryTicks = 0;
        laneForward = !laneForward;
        laneShiftDirection = laneShiftDirection == LaneShiftDirection.FORWARD
                ? LaneShiftDirection.BACKWARD
                : LaneShiftDirection.FORWARD;
        recoveryTicksRemaining = 12;
        recoveryStrafeDirection = moveRight ? -1 : 1;
    }

    private void updateStuckHeuristics(MinecraftClient client, FarmHelperConfig config) {
        if (client.player == null) {
            lastMoveSample = null;
            stuckTicks = 0;
            return;
        }
        Vec3d current = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        if (lastMoveSample == null) {
            lastMoveSample = current;
            stuckTicks = 0;
            return;
        }

        boolean anyMovementKey = client.options.forwardKey.isPressed()
                || client.options.backKey.isPressed()
                || client.options.leftKey.isPressed()
                || client.options.rightKey.isPressed();
        if (!anyMovementKey || recoveryTicksRemaining > 0) {
            lastMoveSample = current;
            stuckTicks = 0;
            return;
        }

        double dx = current.x - lastMoveSample.x;
        double dz = current.z - lastMoveSample.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        int threshold = Math.max(14, Math.min(80, Math.max(1, config.stationaryFailsafeTicks) / 3));
        if (horizontalDistance < 0.015) {
            stuckTicks++;
            if (stuckTicks >= threshold) {
                recoveryTicksRemaining = 14;
                recoveryStrafeDirection = moveRight ? -1 : 1;
                transitionTicksRemaining = 0;
                primaryTicks = 0;
                laneShiftDirection = laneShiftDirection == LaneShiftDirection.FORWARD
                        ? LaneShiftDirection.BACKWARD
                        : LaneShiftDirection.FORWARD;
                cocoaMotionState = CocoaMotionState.SWITCHING_SIDE;
                stuckTicks = 0;
            }
        } else {
            stuckTicks = 0;
        }
        lastMoveSample = current;
    }

    private boolean applyRecoveryMovement(MinecraftClient client, FarmHelperConfig config, boolean holdAttack) {
        if (recoveryTicksRemaining <= 0) {
            return false;
        }
        recoveryTicksRemaining--;
        boolean strafeLeft = recoveryStrafeDirection < 0;
        boolean strafeRight = recoveryStrafeDirection > 0;
        holdMovement(client, config, false, true, strafeLeft, strafeRight, false, holdAttack);
        directionLabel = "RECOVER_STUCK";
        return true;
    }

    private void applyOrientation(MinecraftClient client, FarmHelperConfig config) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }

        if (config.customYaw) {
            targetYaw = config.customYawLevel;
        } else if (activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT) {
            updateMelonOrientationForState(config, false);
        } else if (activeTuning.motionMode() != CropMacroMotionMode.MUSHROOM_ROTATE) {
            targetYaw = baseYaw;
        }

        if (config.customPitch) {
            targetPitch = config.customPitchLevel;
        }

        sampleRotationSmoothing(config);
        float simulationYawOffset = playerSimulation.getYawOffset(config, runtimeTicks);
        float simulationPitchOffset = playerSimulation.getPitchOffset(config, runtimeTicks);
        float yawStep = activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT
                ? sampledYawStep * 1.25f
                : sampledYawStep;
        float pitchStep = sampledPitchStep;
        player.setYaw(approachAngle(player.getYaw(), targetYaw + simulationYawOffset, yawStep));
        player.setPitch(MathHelper.clamp(approach(player.getPitch(), targetPitch + simulationPitchOffset, pitchStep), -90f, 90f));
    }

    private void applyCustomOrientation(MinecraftClient client, FarmHelperConfig config) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        sampleRotationSmoothing(config);
        float simulationYawOffset = playerSimulation.getYawOffset(config, runtimeTicks);
        float simulationPitchOffset = playerSimulation.getPitchOffset(config, runtimeTicks);
        if (config.customYaw) {
            player.setYaw(approachAngle(player.getYaw(), config.customYawLevel + simulationYawOffset, sampledYawStep));
        }
        if (config.customPitch) {
            player.setPitch(MathHelper.clamp(approach(player.getPitch(), config.customPitchLevel + simulationPitchOffset, sampledPitchStep), -90f, 90f));
        }
    }

    private void sampleRotationSmoothing(FarmHelperConfig config) {
        if (!config.useLegacyRandomDelays) {
            sampledYawStep = 6f;
            sampledPitchStep = 3f;
            return;
        }
        if (runtimeTicks < nextRotationSampleTick) {
            return;
        }
        int baseMs = Math.max(200, config.rotationTimeMs);
        int randomMs = Math.max(0, config.rotationTimeRandomnessMs);
        int sampledMs = baseMs + (randomMs <= 0 ? 0 : ThreadLocalRandom.current().nextInt(randomMs + 1));
        float ticks = Math.max(1f, sampledMs / 50f);
        sampledYawStep = MathHelper.clamp(90f / ticks, 1.6f, 18f);
        sampledPitchStep = MathHelper.clamp(45f / ticks, 1.0f, 10f);
        nextRotationSampleTick = runtimeTicks + ThreadLocalRandom.current().nextInt(8, 22);
    }

    private int computeRowChangeTicks(FarmHelperConfig config, int fallbackTicks, int minimumTicks) {
        int base = Math.max(1, fallbackTicks);
        if (!config.useLegacyRandomDelays) {
            return Math.max(minimumTicks, base);
        }
        int baseMs = Math.max(0, config.timeBetweenChangingRowsMs);
        int randomMs = Math.max(0, config.randomTimeBetweenChangingRowsMs);
        int sampledMs = baseMs + (randomMs <= 0 ? 0 : ThreadLocalRandom.current().nextInt(randomMs + 1));
        int sampledTicks = Math.max(1, sampledMs / 50);
        return Math.max(minimumTicks, sampledTicks);
    }

    private void maybeAutoSelectTool(MinecraftClient client, FarmHelperConfig config, boolean force) {
        if (client.player == null) {
            return;
        }
        if (!force && !config.autoChooseTool) {
            return;
        }
        if (!force && runtimeTicks - lastToolSwitchTick < 40L) {
            return;
        }

        int selected = client.player.getInventory().getSelectedSlot();
        int candidate = findBestToolSlot(client.player, activeType);
        if (candidate >= 0 && candidate != selected) {
            client.player.getInventory().setSelectedSlot(candidate);
        }
        lastToolSwitchTick = runtimeTicks;
    }

    private int findBestToolSlot(ClientPlayerEntity player, LegacyMacroType type) {
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String displayName = stack.getName().getString().toLowerCase(Locale.ROOT);
            String itemId = extractSkyblockItemId(stack);
            String metadata = (displayName + " " + stack.getItem() + " " + stack.getComponents()).toLowerCase(Locale.ROOT);
            int score = scoreToolForType(type, stack, metadata, itemId);

            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        return bestScore <= 0 ? -1 : bestSlot;
    }

    private int scoreToolForType(LegacyMacroType type, ItemStack stack, String metadata, String itemId) {
        int score = 0;
        boolean hoe = stack.getItem() instanceof HoeItem;
        boolean axe = stack.getItem() instanceof AxeItem;
        boolean shears = stack.getItem() instanceof ShearsItem;

        if (hoe) score += 20;
        if (axe) score += 12;
        if (shears) score += 8;

        int tier = extractItemTier(itemId);
        score += Math.min(20, tier * 2);

        if (!itemId.isEmpty()) {
            switch (type) {
                case S_COCOA_BEANS, S_COCOA_BEANS_TRAPDOORS, S_COCOA_BEANS_LEFT_RIGHT -> {
                    if (itemId.contains("COCO_CHOPPER")) score += 180;
                    else if (itemId.contains("CHOPPER")) score += 120;
                }
                case S_PUMPKIN_MELON, S_PUMPKIN_MELON_MELONGKINGDE, S_PUMPKIN_MELON_DEFAULT_PLOT -> {
                    if (itemId.contains("MELON_DICER") || itemId.contains("PUMPKIN_DICER")) score += 180;
                    else if (itemId.contains("_DICER")) score += 120;
                }
                case S_MUSHROOM, S_MUSHROOM_ROTATE, S_MUSHROOM_SDS -> {
                    if (itemId.contains("FUNGI_CUTTER")) score += 180;
                    if (itemId.contains("DAEDALUS_AXE")) score += 170;
                }
                case S_SUGAR_CANE -> {
                    if (itemId.contains("HOE_CANE")) score += 180;
                }
                case S_CACTUS, S_CACTUS_SUNTZU -> {
                    if (itemId.contains("CACTUS_KNIFE")) score += 180;
                }
                default -> {
                    if (itemId.contains("HOE_WHEAT")
                            || itemId.contains("HOE_CARROT")
                            || itemId.contains("HOE_POTATO")
                            || itemId.contains("HOE_WARTS")) {
                        score += 170;
                    } else if (itemId.contains("HOE_")) {
                        score += 110;
                    } else if (itemId.contains("_DICER") || itemId.contains("CHOPPER")) {
                        score += 90;
                    }
                }
            }
        }

        switch (type) {
            case S_COCOA_BEANS, S_COCOA_BEANS_TRAPDOORS, S_COCOA_BEANS_LEFT_RIGHT -> {
                if (metadata.contains("coco") || metadata.contains("chopper")) score += 36;
                if (metadata.contains("axe")) score += 14;
            }
            case S_PUMPKIN_MELON, S_PUMPKIN_MELON_MELONGKINGDE, S_PUMPKIN_MELON_DEFAULT_PLOT -> {
                if (metadata.contains("melon") || metadata.contains("pumpkin") || metadata.contains("dicer")) score += 36;
                if (metadata.contains("axe")) score += 14;
            }
            case S_MUSHROOM, S_MUSHROOM_ROTATE, S_MUSHROOM_SDS -> {
                if (metadata.contains("fungi") || metadata.contains("mushroom")) score += 34;
                if (metadata.contains("hoe")) score += 10;
                if (metadata.contains("axe")) score += 6;
            }
            case S_SUGAR_CANE -> {
                if (metadata.contains("cane")) score += 34;
                if (metadata.contains("hoe")) score += 10;
            }
            case S_CACTUS, S_CACTUS_SUNTZU -> {
                if (metadata.contains("cactus") || metadata.contains("knife")) score += 34;
                if (metadata.contains("hoe")) score += 10;
            }
            default -> {
                if (metadata.contains("hoe")) score += 20;
                if (metadata.contains("dicer") || metadata.contains("chopper")) score += 16;
            }
        }

        if (metadata.contains("vacuum")
                || metadata.contains("repellent")
                || metadata.contains("sprayonator")
                || metadata.contains("cookie")
                || metadata.contains("god pot")) {
            score -= 90;
        }
        return score;
    }

    private String extractSkyblockItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        try {
            NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (customData != null) {
                NbtCompound root = customData.copyNbt();
                if (root != null) {
                    NbtCompound extra = root.getCompoundOrEmpty("ExtraAttributes");
                    String id = extra.getString("id", "").trim();
                    if (!id.isEmpty()) {
                        return id.toUpperCase(Locale.ROOT);
                    }
                }
            }
        } catch (Throwable ignored) {
            // Best-effort parser: many non-SkyBlock items won't expose this structure.
        }

        String componentsDump = String.valueOf(stack.getComponents()).toUpperCase(Locale.ROOT);
        Matcher matcher = SKYBLOCK_ID_PATTERN.matcher(componentsDump);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private int extractItemTier(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return 0;
        }
        Matcher matcher = SKYBLOCK_TIER_PATTERN.matcher(itemId);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void holdMovement(
            MinecraftClient client,
            FarmHelperConfig config,
            boolean forward,
            boolean back,
            boolean left,
            boolean right,
            boolean sprint,
            boolean attack
    ) {
        if (config.alwaysHoldW && activeTuning.motionMode() != CropMacroMotionMode.CIRCULAR) {
            forward = true;
        }
        PlayerSimulation.MovementDecision decision = playerSimulation.adjustMovement(
                config,
                runtimeTicks,
                forward,
                back,
                left,
                right,
                sprint,
                attack
        );
        setPressed(client.options.forwardKey, decision.forward());
        setPressed(client.options.backKey, decision.back());
        setPressed(client.options.leftKey, decision.left());
        setPressed(client.options.rightKey, decision.right());
        setPressed(client.options.sprintKey, decision.sprint());
        setPressed(client.options.jumpKey, false);
        setPressed(client.options.sneakKey, false);
        setPressed(client.options.attackKey, decision.attack());
    }

    private void stopAllMovement(MinecraftClient client) {
        if (client == null || client.options == null) {
            return;
        }

        setPressed(client.options.forwardKey, false);
        setPressed(client.options.backKey, false);
        setPressed(client.options.leftKey, false);
        setPressed(client.options.rightKey, false);
        setPressed(client.options.sprintKey, false);
        setPressed(client.options.jumpKey, false);
        setPressed(client.options.sneakKey, false);
        setPressed(client.options.attackKey, false);
    }

    private void setPressed(KeyBinding keyBinding, boolean pressed) {
        if (keyBinding == null) {
            return;
        }
        InputUtil.Key boundKey = resolveBoundKey(keyBinding);
        boolean wasPressed = keyBinding.isPressed();

        // Keep the global key map and this binding in sync every tick so
        // long-held macro movement is not lost by internal input updates.
        KeyBinding.setKeyPressed(boundKey, pressed);
        keyBinding.setPressed(pressed);

        if (pressed && !wasPressed) {
            KeyBinding.onKeyPressed(boundKey);
        }
    }

    public String getDirectionName() {
        return directionLabel;
    }

    private MacroPattern getEffectivePattern(FarmHelperConfig config) {
        if (config.macroPattern != null) {
            return config.macroPattern;
        }
        LegacyMacroType macroType = config.macroType == null ? LegacyMacroType.S_V_NORMAL_TYPE : config.macroType;
        return macroType.defaultPattern();
    }

    private float resolveBaseYaw(float currentYaw, CropYawMode yawMode) {
        if (yawMode == CropYawMode.DIAGONAL) {
            return closestDiagonal(currentYaw);
        }
        return closestCardinal(currentYaw);
    }

    private float closestCardinal(float yaw) {
        return Math.round(yaw / 90.0f) * 90.0f;
    }

    private float closestDiagonal(float yaw) {
        float normalized = MathHelper.wrapDegrees(yaw);
        float closest = 45.0f;
        float min = Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float candidate = 45.0f + i * 90.0f;
            float diff = Math.abs(MathHelper.wrapDegrees(normalized - candidate));
            if (diff < min) {
                min = diff;
                closest = candidate;
            }
        }
        return closest;
    }

    private float randomBetween(float min, float max) {
        if (min == max) {
            return min;
        }
        float low = Math.min(min, max);
        float high = Math.max(min, max);
        return (float) ThreadLocalRandom.current().nextDouble(low, high);
    }

    private float approach(float current, float target, float maxStep) {
        float delta = target - current;
        if (Math.abs(delta) <= maxStep) {
            return target;
        }
        return current + Math.copySign(maxStep, delta);
    }

    private float approachAngle(float current, float target, float maxStep) {
        float delta = MathHelper.wrapDegrees(target - current);
        if (Math.abs(delta) <= maxStep) {
            return target;
        }
        return current + Math.copySign(maxStep, delta);
    }

    private InputUtil.Key resolveBoundKey(KeyBinding keyBinding) {
        if (keyBinding instanceof KeyBindingAccessor accessor) {
            InputUtil.Key bound = accessor.farmhelper$getBoundKey();
            if (bound != null) {
                return bound;
            }
        }
        return keyBinding.getDefaultKey();
    }

    private void updateMelonOrientationForState(FarmHelperConfig config, boolean force) {
        if (activeType != LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT || config.customYaw) {
            return;
        }
        if (!force && routeState == lastMelonYawState) {
            return;
        }
        if (laneReferenceYaw == 0f) {
            laneReferenceYaw = closestCardinal(baseYaw);
        }
        switch (routeState) {
            case LEFT -> targetYaw = laneReferenceYaw - (45f + randomBetween(0f, 2f));
            case RIGHT -> targetYaw = laneReferenceYaw + (45f + randomBetween(0f, 2f));
            case SWITCHING_LANE -> {
                float micro = switch (previousRouteState) {
                    case RIGHT -> -randomBetween(0.2f, 0.6f);
                    case LEFT -> randomBetween(0.2f, 0.6f);
                    default -> randomBetween(-0.8f, 0.8f);
                };
                targetYaw = laneReferenceYaw + micro;
            }
            default -> targetYaw = laneReferenceYaw + randomBetween(-1f, 1f);
        }
        if (!config.customPitch && (force || routeState != lastMelonYawState)) {
            targetPitch = randomBetween(activeTuning.pitchMin(), activeTuning.pitchMax());
        }
        lastMelonYawState = routeState;
    }

    private float getUnitX(float yaw) {
        float normalized = ((yaw % 360f) + 360f) % 360f;
        if (normalized < 30f) {
            return 0f;
        } else if (normalized < 150f) {
            return -1f;
        } else if (normalized < 210f) {
            return 0f;
        } else if (normalized < 330f) {
            return 1f;
        } else {
            return 0f;
        }
    }

    private float getUnitZ(float yaw) {
        float normalized = ((yaw % 360f) + 360f) % 360f;
        if (normalized < 60f) {
            return 1f;
        } else if (normalized < 120f) {
            return 0f;
        } else if (normalized < 240f) {
            return -1f;
        } else if (normalized < 300f) {
            return 0f;
        } else {
            return 1f;
        }
    }

    private boolean shouldForwardDuringStrafe(LegacyMacroType type) {
        return switch (type) {
            case S_PUMPKIN_MELON_DEFAULT_PLOT,
                    S_SUGAR_CANE,
                    S_CACTUS,
                    S_CACTUS_SUNTZU,
                    S_COCOA_BEANS_LEFT_RIGHT,
                    S_MUSHROOM_SDS -> false;
            default -> true;
        };
    }
}

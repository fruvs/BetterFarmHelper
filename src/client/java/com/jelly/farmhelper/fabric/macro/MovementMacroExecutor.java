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
import net.minecraft.block.Blocks;

import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jelly.farmhelper.fabric.util.CropUtils;
import com.jelly.farmhelper.fabric.util.PlayerUtils;
import com.jelly.farmhelper.fabric.util.RenderUtils;

public class MovementMacroExecutor {
    private static final Pattern SKYBLOCK_ID_PATTERN = Pattern.compile("id[=:]\"?([A-Z0-9_]{5,})");
    private static final Pattern SKYBLOCK_TIER_PATTERN = Pattern.compile("_(\\d+)$");
    private static final int MELON_SIDE_COMMIT_TICKS = 18;
    private static final int MELON_SIDE_SIGNAL_STABLE_TICKS = 4;
    private static final float MELON_DEFAULT_YAW_OFFSET_DEGREES = 45f;
    private static final float MELON_LOW_PITCH_YAW_OFFSET_DEGREES = 65f;
    private static final float MELON_SWITCH_ALIGN_TOLERANCE_DEGREES = 9f;
    private static final float MELON_SWITCH_YAW_STEP_DEGREES = 10.5f;

    enum LegacyRouteState {
        NONE,
        DROPPING,
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

    enum LaneShiftDirection {
        FORWARD,
        BACKWARD
    }

    private enum CocoaMotionState {
        FORWARD,
        BACKWARD,
        SWITCHING_SIDE,
        SWITCHING_LANE
    }

    private record LaneScore(
            float yaw,
            double score,
            boolean front,
            boolean back,
            boolean left,
            boolean right,
            int leftRun,
            int rightRun,
            int markerCount
    ) {
    }

    private record LaneSelection(
            LaneScore best,
            LaneScore runnerUp,
            float chosenYaw,
            double margin
    ) {
    }

    private record OrientationSnapshot(
            float fallbackBaseYaw,
            float selectedBaseYaw,
            float selectedLaneYaw,
            float selectedPitch,
            boolean confident,
            boolean preferredRight,
            LaneScore bestCandidate,
            LaneScore runnerUpCandidate,
            String[] slices
    ) {
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
    private boolean laneReferenceYawResolved;

    private Vec3d lastMoveSample;
    private int stuckTicks;
    private int recoveryTicksRemaining;
    private int recoveryStrafeDirection = 1;
    private int laneSwitchStallTicks;
    private int melonLeftSignalTicks;
    private int melonRightSignalTicks;
    private int melonSideBlockedTicks;
    private float sampledYawStep = 6f;
    private float sampledPitchStep = 3f;
    private long nextRotationSampleTick = -1L;
    private boolean movementInjected;
    private boolean startupAlignmentPending;
    private int startupAlignmentStableTicks;
    private long startupAlignmentStartTick;
    private PlayerSimulation.MovementDecision lastMovementDecision =
            new PlayerSimulation.MovementDecision(false, false, false, false, false, false);
    private long lastMovementDecisionTick = -1L;
    private long lastMacroDebugTick = -1L;
    private LegacyRouteState lastLoggedRouteState = LegacyRouteState.NONE;
    private String lastLoggedDirection = "IDLE";
    private Vec3d lastLoggedPosition = Vec3d.ZERO;
    private OrientationSnapshot lastOrientationSnapshot;
    private LegacyMovementController legacyMovementController;
    private boolean legacyUpdatedState;
    private boolean legacyNotMovingScheduled;
    private int legacyRandomWaitMs = -1;
    private int legacyWaitOverrideMs = -1;
    private long legacyNotMovingStartTick = -1L;
    private Vec3d legacyGateLastPos;
    private int legacyAntiStuckTicksRemaining;
    private int legacyLayerY;
    private boolean legacyWalkingDirectionIsX;
    private int legacyPreviousWalkingCoord;
    private int legacyLagBackCounter;
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

        if (client.player != null && client.player.getAbilities().flying) {
            stopAllMovement(client);
            setPressed(client.options.sneakKey, true);
            directionLabel = "DESCEND_FROM_FLIGHT";
            emitMacroDebugTelemetry(client, config, false);
            runtimeTicks++;
            return;
        }

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
        } else {
            applyCustomOrientation(client, config);
        }

        if (handleStartupAlignment(client, config, holdAttack)) {
            updateStuckHeuristics(client, config);
            runtimeTicks++;
            return;
        }

        if (config.useLegacyProfileDefaults) {
            tickLegacyProfileMode(client, config, forwardTicks, sideTicks, holdAttack);
        } else {
            tickGenericPattern(client, config, forwardTicks, sideTicks, holdAttack);
        }

        updateStuckHeuristics(client, config);
        emitMacroDebugTelemetry(client, config, holdAttack);
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
        melonLeftSignalTicks = 0;
        melonRightSignalTicks = 0;
        melonSideBlockedTicks = 0;
        sampledYawStep = 6f;
        sampledPitchStep = 3f;
        nextRotationSampleTick = -1L;
        startupAlignmentPending = false;
        startupAlignmentStableTicks = 0;
        startupAlignmentStartTick = 0L;
        laneReferenceYaw = 0f;
        laneReferenceYawResolved = false;
        initialized = false;
        directionLabel = "IDLE";
        lastMovementDecision = new PlayerSimulation.MovementDecision(false, false, false, false, false, false);
        lastMovementDecisionTick = -1L;
        lastMacroDebugTick = -1L;
        lastLoggedRouteState = LegacyRouteState.NONE;
        lastLoggedDirection = "IDLE";
        lastLoggedPosition = Vec3d.ZERO;
        lastOrientationSnapshot = null;
        legacyMovementController = null;
        legacyUpdatedState = false;
        legacyNotMovingScheduled = false;
        legacyRandomWaitMs = -1;
        legacyWaitOverrideMs = -1;
        legacyNotMovingStartTick = -1L;
        legacyGateLastPos = null;
        legacyAntiStuckTicksRemaining = 0;
        legacyLayerY = 0;
        legacyWalkingDirectionIsX = false;
        legacyPreviousWalkingCoord = 0;
        legacyLagBackCounter = 0;
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
        melonLeftSignalTicks = 0;
        melonRightSignalTicks = 0;
        melonSideBlockedTicks = 0;
        sampledYawStep = 6f;
        sampledPitchStep = 3f;
        nextRotationSampleTick = -1L;
        laneReferenceYaw = 0f;
        laneReferenceYawResolved = false;
        startupAlignmentPending = true;
        startupAlignmentStableTicks = 0;
        startupAlignmentStartTick = runtimeTicks;
        lastMovementDecision = new PlayerSimulation.MovementDecision(false, false, false, false, false, false);
        lastMovementDecisionTick = -1L;
        lastMacroDebugTick = -1L;
        lastLoggedRouteState = LegacyRouteState.NONE;
        lastLoggedDirection = "STARTING";
        if (client.player != null) {
            lastLoggedPosition = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        } else {
            lastLoggedPosition = Vec3d.ZERO;
        }
        lastOrientationSnapshot = null;

        ClientPlayerEntity player = client.player;
        if (player == null) {
            initialized = true;
            return;
        }

        float fallbackBaseYaw = resolveBaseYaw(player.getYaw(), activeTuning.yawMode());
        OrientationSnapshot orientationSnapshot = analyzeOrientationSnapshot(client, config, fallbackBaseYaw);
        lastOrientationSnapshot = orientationSnapshot;
        float legacyDefaultYaw = switch (activeType) {
            case S_PUMPKIN_MELON_DEFAULT_PLOT,
                    S_SUGAR_CANE,
                    S_MUSHROOM,
                    C_NORMAL_TYPE -> closestDiagonal(player.getYaw());
            default -> closestCardinal(player.getYaw());
        };
        baseYaw = config.customYaw ? config.customYawLevel : legacyDefaultYaw;
        laneReferenceYaw = closestCardinal(baseYaw);
        laneReferenceYawResolved = true;
        laneShiftDirection = resolveInitialLaneShiftDirection(player.getYaw(), laneReferenceYaw);
        moveRight = resolveFacingPreferredSide(player.getYaw(), laneReferenceYaw, moveRight);
        targetYaw = baseYaw;
        if (activeTuning.motionMode() == CropMacroMotionMode.MUSHROOM_ROTATE && !config.customYaw) {
            targetYaw = laneReferenceYaw + 30f + (float) (ThreadLocalRandom.current().nextDouble(-2.0, 2.0));
        }
        targetPitch = config.customPitch
                ? config.customPitchLevel
                : switch (activeType) {
            case S_V_NORMAL_TYPE -> (float) (2.8 + ThreadLocalRandom.current().nextDouble(0.0, 0.5));
            case S_PUMPKIN_MELON -> (float) (28.0 + ThreadLocalRandom.current().nextDouble(0.0, 2.0));
            case S_PUMPKIN_MELON_MELONGKINGDE -> (float) (-59.2 + ThreadLocalRandom.current().nextDouble(0.0, 1.0));
            case S_CACTUS -> (float) ThreadLocalRandom.current().nextDouble(0.0, 0.5);
            case S_CACTUS_SUNTZU -> (float) (-38.0 - ThreadLocalRandom.current().nextDouble(0.0, 1.5));
            case S_COCOA_BEANS_LEFT_RIGHT -> -90f;
            case S_PUMPKIN_MELON_DEFAULT_PLOT -> (float) (50.0 + ThreadLocalRandom.current().nextDouble(-3.0, 3.0));
            case S_SUGAR_CANE -> (float) ThreadLocalRandom.current().nextDouble(-0.5, 0.5);
            case S_MUSHROOM, S_MUSHROOM_ROTATE -> (float) ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
            case S_MUSHROOM_SDS -> (float) (6.5 + ThreadLocalRandom.current().nextDouble(0.0, 1.0));
            case C_NORMAL_TYPE -> (float) (2.8 + ThreadLocalRandom.current().nextDouble(0.0, 0.5));
            case S_COCOA_BEANS, S_COCOA_BEANS_TRAPDOORS -> (float) (-70.0 + ThreadLocalRandom.current().nextDouble(0.0, 0.6));
        };

        if (activeTuning.motionMode() == CropMacroMotionMode.COCOA_STRAFE) {
            cocoaMotionState = resolveInitialCocoaState(client, targetYaw);
        }
        initializeRouteState(client);
        if (activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT) {
            laneShiftDirection = LaneShiftDirection.FORWARD;
            moveRight = routeState == LegacyRouteState.RIGHT;
            if (!config.customYaw) {
                float additionalRotation = switch (routeState) {
                    case LEFT -> -(45f + (float) (ThreadLocalRandom.current().nextDouble(0.0, 2.0)));
                    case RIGHT -> 45f + (float) (ThreadLocalRandom.current().nextDouble(0.0, 2.0));
                    default -> (float) ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
                };
                targetYaw = laneReferenceYaw + additionalRotation;
            }
        }
        maybeAutoSelectTool(client, config, true);
        emitOrientationSnapshot(orientationSnapshot);
        legacyMovementController = createLegacyController(activeType);
        legacyUpdatedState = false;
        legacyNotMovingScheduled = false;
        legacyRandomWaitMs = -1;
        legacyWaitOverrideMs = -1;
        legacyNotMovingStartTick = -1L;
        legacyGateLastPos = null;
        legacyAntiStuckTicksRemaining = 0;
        legacyLayerY = player != null ? player.getBlockPos().getY() : 0;
        legacyWalkingDirectionIsX = false;
        legacyPreviousWalkingCoord = 0;
        legacyLagBackCounter = 0;
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
        if (legacyMovementController == null) {
            legacyMovementController = createLegacyController(activeType);
        }
        if (legacyAntiStuckTicksRemaining > 0) {
            invokeLegacyAntiStuckPulse(client);
            routeStateTicks++;
            return;
        }

        tickLegacyDirectionGate(client, config);
        if (canLegacyChangeDirection()) {
            holdMovement(client, config, false, false, false, false, false, holdAttack);
            if (routeState == LegacyRouteState.DROPPING && client.player != null && !client.player.isOnGround()) {
                return;
            }
            legacyMovementController.updateState(client, config, forwardTicks, sideTicks, holdAttack);
            if (routeState != LegacyRouteState.NONE) {
                legacyUpdatedState = true;
            }
        } else {
            if (client.player != null
                    && !client.player.isOnGround()
                    && Math.abs(legacyLayerY - client.player.getY()) > 0.75
                    && client.player.getY() < 80
                    && routeState != LegacyRouteState.DROPPING) {
                setRouteState(LegacyRouteState.DROPPING);
                legacyUpdatedState = true;
                legacyScheduleNotMoving(config);
            }
            legacyMovementController.invokeState(client, config, forwardTicks, sideTicks, holdAttack);
        }
        routeStateTicks++;
    }

    /**
     * Handles the DROPPING state — mirrors old AbstractMacro per-macro DROPPING handling.
     * Stops movement while airborne. On landing:
     * - If dropped > 1.5 blocks and rotateAfterDrop: rotate 180° and reset.
     * - If dropped ≤ 1.5 blocks: just reset to NONE.
     */
    private void tickLegacyDropping(MinecraftClient client, FarmHelperConfig config, boolean holdAttack) {
        if (client.player == null) {
            return;
        }
        boolean onGround = client.player.isOnGround();
        int currentY = client.player.getBlockPos().getY();
        boolean bigDrop = Math.abs(legacyLayerY - currentY) > 1.5;

        if (canLegacyChangeDirection()) {
            // updateState equivalent for DROPPING
            if (onGround && bigDrop) {
                laneShiftDirection = LaneShiftDirection.FORWARD;
                if (config.rotateAfterDrop) {
                    // Rotate 180° — flip the base yaw and lane reference
                    baseYaw = closestCardinal(baseYaw + 180f);
                    laneReferenceYaw = closestCardinal(baseYaw);
                    laneReferenceYawResolved = true;
                    targetYaw = baseYaw;
                    if (activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT) {
                        baseYaw = closestDiagonal(baseYaw);
                        laneReferenceYaw = closestCardinal(baseYaw);
                        targetYaw = baseYaw;
                    }
                }
                stopAllMovement(client);
                legacyLayerY = currentY;
                setRouteState(LegacyRouteState.NONE);
                legacyUpdatedState = true;
            } else if (onGround) {
                // Small drop, just resume
                legacyLayerY = currentY;
                setRouteState(LegacyRouteState.NONE);
                legacyUpdatedState = true;
            } else {
                // Still airborne — keep waiting
                legacyScheduleNotMoving(config);
            }
        } else {
            // invokeState equivalent for DROPPING: check for small-drop landing
            if (onGround && !bigDrop) {
                legacyLayerY = currentY;
                setRouteState(LegacyRouteState.NONE);
            }
            // Stop all movement while dropping
            holdMovement(client, config, false, false, false, false, false, false);
            directionLabel = "DROPPING";
        }
    }

    private LegacyMovementController createLegacyController(LegacyMacroType type) {
        if (type == null) {
            return new VerticalLaneMacroController(this);
        }
        return switch (type) {
            case S_V_NORMAL_TYPE,
                    S_PUMPKIN_MELON,
                    S_PUMPKIN_MELON_MELONGKINGDE,
                    S_CACTUS,
                    S_CACTUS_SUNTZU,
                    S_COCOA_BEANS_LEFT_RIGHT -> new VerticalLaneMacroController(this);
            case S_PUMPKIN_MELON_DEFAULT_PLOT -> new MelonDefaultMacroController(this);
            case S_SUGAR_CANE -> new SugarCaneMacroController(this);
            case S_MUSHROOM -> new Mushroom45MacroController(this);
            case S_MUSHROOM_ROTATE -> new MushroomRotateMacroController(this);
            case S_MUSHROOM_SDS -> new MushroomSdsMacroController(this);
            case C_NORMAL_TYPE -> new CircularMacroController(this);
            case S_COCOA_BEANS, S_COCOA_BEANS_TRAPDOORS -> new CocoaMacroController(this);
        };
    }

    private void initializeRouteState(MinecraftClient client) {
        setRouteState(switch (activeType) {
            case S_PUMPKIN_MELON_DEFAULT_PLOT -> calculateLegacyMelonDefaultDirection(client);
            default -> LegacyRouteState.NONE;
        });
    }

    void setRouteState(LegacyRouteState nextState) {
        if (nextState == null || nextState == routeState) {
            return;
        }
        previousRouteState = routeState;
        routeState = nextState;
        routeStateTicks = 0;
        if (nextState == LegacyRouteState.SWITCHING_LANE || nextState == LegacyRouteState.NONE || nextState == LegacyRouteState.DROPPING) {
            melonLeftSignalTicks = 0;
            melonRightSignalTicks = 0;
            melonSideBlockedTicks = 0;
        } else if (nextState == LegacyRouteState.LEFT || nextState == LegacyRouteState.RIGHT) {
            melonSideBlockedTicks = 0;
            legacyLagBackCounter = 0;
        }
    }

    LegacyRouteState getRouteState() {
        return routeState;
    }

    LegacyRouteState getPreviousRouteState() {
        return previousRouteState;
    }

    void setMoveRight(boolean moveRight) {
        this.moveRight = moveRight;
    }

    boolean isMoveRight() {
        return moveRight;
    }

    LaneShiftDirection getLaneShiftDirection() {
        return laneShiftDirection;
    }

    void setLaneShiftDirection(LaneShiftDirection laneShiftDirection) {
        this.laneShiftDirection = laneShiftDirection;
    }

    void setDirectionLabel(String directionLabel) {
        this.directionLabel = directionLabel;
    }

    int getLegacyLayerY() {
        return legacyLayerY;
    }

    void setLegacyLayerY(int legacyLayerY) {
        this.legacyLayerY = legacyLayerY;
    }

    float getTargetYaw() {
        return targetYaw;
    }

    void setTargetYaw(float targetYaw) {
        this.targetYaw = targetYaw;
    }

    float getTargetPitch() {
        return targetPitch;
    }

    void setTargetPitch(float targetPitch) {
        this.targetPitch = targetPitch;
    }

    void setLaneReferenceYaw(float laneReferenceYaw) {
        this.laneReferenceYaw = laneReferenceYaw;
        this.laneReferenceYawResolved = true;
    }

    LegacyMacroType getActiveType() {
        return activeType;
    }

    private boolean canFlipRouteState() {
        return routeStateTicks >= 3;
    }

    private void updateMelonMarkerSignals(boolean leftMarker, boolean rightMarker) {
        if (leftMarker && !rightMarker) {
            melonLeftSignalTicks = Math.min(MELON_SIDE_SIGNAL_STABLE_TICKS + 8, melonLeftSignalTicks + 1);
            melonRightSignalTicks = Math.max(0, melonRightSignalTicks - 1);
            return;
        }
        if (rightMarker && !leftMarker) {
            melonRightSignalTicks = Math.min(MELON_SIDE_SIGNAL_STABLE_TICKS + 8, melonRightSignalTicks + 1);
            melonLeftSignalTicks = Math.max(0, melonLeftSignalTicks - 1);
            return;
        }
        melonLeftSignalTicks = Math.max(0, melonLeftSignalTicks - 1);
        melonRightSignalTicks = Math.max(0, melonRightSignalTicks - 1);
    }

    private LegacyRouteState stableMelonMarkerSide() {
        if (melonLeftSignalTicks >= MELON_SIDE_SIGNAL_STABLE_TICKS
                && melonLeftSignalTicks > melonRightSignalTicks) {
            return LegacyRouteState.LEFT;
        }
        if (melonRightSignalTicks >= MELON_SIDE_SIGNAL_STABLE_TICKS
                && melonRightSignalTicks > melonLeftSignalTicks) {
            return LegacyRouteState.RIGHT;
        }
        return LegacyRouteState.NONE;
    }

    private boolean canLegacyChangeDirection() {
        return !legacyUpdatedState && !legacyNotMovingScheduled;
    }

    private void tickLegacyDirectionGate(MinecraftClient client, FarmHelperConfig config) {
        if (client == null || client.player == null) {
            return;
        }
        if (isLegacyNotMoving(client)) {
            if (legacyNotMovingScheduled && legacyHasPassedSinceStopped(config)) {
                legacyWaitOverrideMs = -1;
                legacyNotMovingScheduled = false;
                legacyNotMovingStartTick = -1L;
                legacyRandomWaitMs = sampleLegacyRowChangeDelayMs(config);
                legacyUpdatedState = false;
            }
            return;
        }
        legacyScheduleNotMoving(config);
    }

    private boolean isLegacyNotMoving(MinecraftClient client) {
        if (client == null || client.player == null) {
            return true;
        }
        double dx = Math.abs(client.player.getVelocity().x);
        double dy = Math.abs(client.player.getVelocity().y);
        double dz = Math.abs(client.player.getVelocity().z);
        boolean dyAtRest = dy < 0.05 || (dy <= 0.079 && dy >= 0.078);
        return dx < 0.01 && dz < 0.01 && dyAtRest;
    }

    private int sampleLegacyRowChangeDelayMs(FarmHelperConfig config) {
        int baseMs = Math.max(0, config.timeBetweenChangingRowsMs);
        int randomMs = Math.max(0, config.randomTimeBetweenChangingRowsMs);
        if (!config.useLegacyRandomDelays || randomMs == 0) {
            return baseMs;
        }
        return baseMs + ThreadLocalRandom.current().nextInt(randomMs + 1);
    }

    void legacyScheduleNotMoving(FarmHelperConfig config) {
        legacyRandomWaitMs = sampleLegacyRowChangeDelayMs(config);
        legacyNotMovingScheduled = true;
        legacyNotMovingStartTick = runtimeTicks;
    }

    void legacyScheduleNotMoving(int waitMs) {
        legacyWaitOverrideMs = Math.max(0, waitMs);
        legacyNotMovingScheduled = true;
        legacyNotMovingStartTick = runtimeTicks;
    }

    void startLegacyAntiStuckPulse(FarmHelperConfig config) {
        if (legacyAntiStuckTicksRemaining > 0) {
            return;
        }
        legacyAntiStuckTicksRemaining = ThreadLocalRandom.current().nextInt(4, 8);
        int sampledDelay = sampleLegacyRowChangeDelayMs(config);
        legacyScheduleNotMoving(Math.max(350, sampledDelay));
        legacyUpdatedState = true;
    }

    private void invokeLegacyAntiStuckPulse(MinecraftClient client) {
        if (legacyAntiStuckTicksRemaining <= 0 || client == null || client.options == null) {
            return;
        }
        setPressed(client.options.forwardKey, false);
        setPressed(client.options.backKey, true);
        setPressed(client.options.leftKey, false);
        setPressed(client.options.rightKey, false);
        setPressed(client.options.sprintKey, false);
        setPressed(client.options.jumpKey, false);
        setPressed(client.options.sneakKey, true);
        setPressed(client.options.attackKey, false);
        lastMovementDecision = new PlayerSimulation.MovementDecision(false, true, false, false, false, false);
        lastMovementDecisionTick = runtimeTicks;
        directionLabel = "LEGACY_ANTISTUCK";
        legacyAntiStuckTicksRemaining--;
        if (legacyAntiStuckTicksRemaining <= 0) {
            stopAllMovement(client);
        }
    }

    private boolean legacyHasPassedSinceStopped(FarmHelperConfig config) {
        if (!legacyNotMovingScheduled || legacyNotMovingStartTick < 0L) {
            return false;
        }
        if (legacyRandomWaitMs < 0) {
            legacyRandomWaitMs = sampleLegacyRowChangeDelayMs(config);
        }
        int waitMs = legacyWaitOverrideMs >= 0 ? legacyWaitOverrideMs : legacyRandomWaitMs;
        long elapsedMs = Math.max(0L, runtimeTicks - legacyNotMovingStartTick) * 50L;
        return elapsedMs > waitMs;
    }

    /**
     * Old 1.8.9 behavior: checks if there are melon/pumpkin blocks on either side,
     * which means the player is stuck against one and needs anti-stuck instead of lane switch.
     */
    boolean isStuckInMelonsOrPumpkins(MinecraftClient client, float routingYaw) {
        BlockState leftBlock = getRelativeBlockStateAtYaw(client, -1, 0, 0, routingYaw);
        BlockState rightBlock = getRelativeBlockStateAtYaw(client, 1, 0, 0, routingYaw);
        boolean leftMelon = isLegacyMelonOrCarvedPumpkinBlock(leftBlock);
        boolean rightMelon = isLegacyMelonOrCarvedPumpkinBlock(rightBlock);
        return leftMelon || rightMelon;
    }

    /**
     * Old 1.8.9 behavior: determines walking direction axis for lag-back detection.
     * Checks whether LEFT/RIGHT movement changes the X or Z coordinate.
     */
    void setLegacyWalkingDirection(MinecraftClient client, float routingYaw) {
        if (client.player == null) {
            return;
        }
        BlockPos current = getRelativeBlockPos(client, 0, 0, routingYaw);
        BlockPos leftPos = getRelativeBlockPos(client, -1, 0, routingYaw);
        BlockPos rightPos = getRelativeBlockPos(client, 1, 0, routingYaw);
        if (current == null || leftPos == null || rightPos == null) {
            return;
        }
        // If LEFT/RIGHT changes X, we walk along Z. If it changes Z, we walk along X.
        if (current.getX() == leftPos.getX() || current.getX() == rightPos.getX()) {
            legacyWalkingDirectionIsX = false; // walking along Z
            legacyPreviousWalkingCoord = client.player.getBlockPos().getX();
        } else {
            legacyWalkingDirectionIsX = true; // walking along X
            legacyPreviousWalkingCoord = client.player.getBlockPos().getZ();
        }
    }

    /**
     * Old 1.8.9 lag-back detection: if the coordinate hasn't changed since last check,
     * trigger anti-stuck. After 3 consecutive lag-backs, reset state entirely.
     */
    boolean legacyDetectLagBack(MinecraftClient client) {
        if (client.player == null) {
            return false;
        }
        int currentCoord = legacyWalkingDirectionIsX
                ? client.player.getBlockPos().getZ()
                : client.player.getBlockPos().getX();
        if (Math.abs(currentCoord - legacyPreviousWalkingCoord) < 1) {
            legacyLagBackCounter++;
            if (legacyLagBackCounter >= 3) {
                // Stuck: reset state entirely
                setRouteState(LegacyRouteState.NONE);
                legacyLagBackCounter = 0;
                return true;
            }
            // Trigger anti-stuck to try to break free
            startLegacyAntiStuckPulse(FarmHelperFabric.getConfigManager().getConfig());
            return true;
        }
        return false;
    }

    void updateLegacyVerticalLane(MinecraftClient client, FarmHelperConfig config) {
        float routingYaw = getLaneRoutingYaw();
        Walkability walkability = computeWalkability(client, routingYaw);
        switch (routeState) {
            case LEFT, RIGHT -> {
                boolean wantsRight = routeState == LegacyRouteState.RIGHT;
                moveRight = wantsRight;
                boolean sideWalkable = wantsRight ? walkability.right : walkability.left;

                // Old 1.8.9 behavior: if updateState is called (player stopped) but the side
                // we're walking toward is still clear, we're probably stuck on a dirt/crop block.
                // Trigger anti-stuck pulse to break free.
                if (sideWalkable) {
                    startLegacyAntiStuckPulse(config);
                    return;
                }

                if (walkability.front && !config.alwaysHoldW) {
                    // Before switching lane, check if stuck in melon/pumpkin blocks (old behavior)
                    if (isStuckInMelonsOrPumpkins(client, routingYaw)) {
                        startLegacyAntiStuckPulse(config);
                        return;
                    }
                    laneShiftDirection = LaneShiftDirection.FORWARD;
                    setLegacyWalkingDirection(client, routingYaw);
                    setRouteState(LegacyRouteState.SWITCHING_LANE);
                    return;
                }
                if (walkability.back
                        && activeType != LegacyMacroType.S_CACTUS
                        && activeType != LegacyMacroType.S_CACTUS_SUNTZU
                        && !config.alwaysHoldW) {
                    if (isStuckInMelonsOrPumpkins(client, routingYaw)) {
                        startLegacyAntiStuckPulse(config);
                        return;
                    }
                    laneShiftDirection = LaneShiftDirection.BACKWARD;
                    setLegacyWalkingDirection(client, routingYaw);
                    setRouteState(LegacyRouteState.SWITCHING_LANE);
                    return;
                }
                if (walkability.left) {
                    setRouteState(LegacyRouteState.LEFT);
                } else if (walkability.right) {
                    setRouteState(LegacyRouteState.RIGHT);
                } else {
                    setRouteState(LegacyRouteState.NONE);
                }
            }
            case SWITCHING_LANE -> {
                // Lag-back detection (old 1.8.9 behavior): check if coordinate hasn't changed
                if (client.player != null && legacyDetectLagBack(client)) {
                    return;
                }
                if (walkability.left) {
                    setRouteState(LegacyRouteState.LEFT);
                } else if (walkability.right) {
                    setRouteState(LegacyRouteState.RIGHT);
                } else {
                    setRouteState(LegacyRouteState.NONE);
                }
            }
            default -> setRouteState(calculateLegacyVerticalDirection(client));
        }
    }

    void invokeLegacyVerticalLane(MinecraftClient client, FarmHelperConfig config, boolean holdAttack) {
        switch (routeState) {
            case LEFT, RIGHT -> {
                boolean wantsRight = routeState == LegacyRouteState.RIGHT;
                moveRight = wantsRight;
                boolean forward = config.alwaysHoldW || PlayerUtils.shouldWalkForwards(client);
                holdMovement(client, config, forward, false, !wantsRight, wantsRight, false, holdAttack);
                directionLabel = wantsRight ? "RIGHT" : "LEFT";
            }
            case SWITCHING_LANE -> {
                boolean forward = laneShiftDirection == LaneShiftDirection.FORWARD || config.alwaysHoldW;
                boolean back = laneShiftDirection == LaneShiftDirection.BACKWARD && !config.alwaysHoldW;
                boolean sprint = laneShiftDirection == LaneShiftDirection.FORWARD;
                holdMovement(client, config, forward, back, false, false, sprint, holdAttack);
                directionLabel = laneShiftDirection == LaneShiftDirection.FORWARD ? "SWITCH_FWD" : "SWITCH_BACK";
            }
            default -> {
                holdMovement(client, config, false, false, false, false, false, false);
                directionLabel = "IDLE";
            }
        }
    }

    void updateLegacyMelonDefault(MinecraftClient client, FarmHelperConfig config) {
        float routingYaw = getLaneRoutingYaw();
        Walkability walkability = computeWalkability(client, routingYaw);
        if (routeState == LegacyRouteState.NONE) {
            setRouteState(calculateLegacyMelonDefaultDirection(client));
        }
        switch (routeState) {
            case RIGHT, LEFT -> {
                boolean wantsRight = routeState == LegacyRouteState.RIGHT;
                moveRight = wantsRight;
                BlockState blockLeft = getRelativeBlockState(client, -1, 0, routingYaw);
                BlockState blockRight = getRelativeBlockState(client, 1, 0, routingYaw);
                boolean leftMarker = isLegacyMelonOrPumpkinBlock(blockLeft);
                boolean rightMarker = isLegacyMelonOrPumpkinBlock(blockRight);
                if (leftMarker) {
                    setRouteState(LegacyRouteState.LEFT);
                    return;
                }
                if (rightMarker) {
                    setRouteState(LegacyRouteState.RIGHT);
                    return;
                }
                if (walkability.front) {
                    if (laneShiftDirection == LaneShiftDirection.BACKWARD) {
                        startLegacyAntiStuckPulse(config);
                        return;
                    }
                    laneShiftDirection = LaneShiftDirection.FORWARD;
                    setRouteState(LegacyRouteState.SWITCHING_LANE);
                    updateMelonOrientationForState(config, true);
                    return;
                }
                if (walkability.back && !config.alwaysHoldW) {
                    if (laneShiftDirection == LaneShiftDirection.FORWARD) {
                        startLegacyAntiStuckPulse(config);
                        return;
                    }
                    laneShiftDirection = LaneShiftDirection.BACKWARD;
                    setRouteState(LegacyRouteState.SWITCHING_LANE);
                    updateMelonOrientationForState(config, true);
                    return;
                }
                if (walkability.left) {
                    setRouteState(LegacyRouteState.LEFT);
                } else if (walkability.right) {
                    setRouteState(LegacyRouteState.RIGHT);
                } else {
                    setRouteState(LegacyRouteState.NONE);
                    FarmHelperFabric.getWebhookService().debugTrace("macro", "melon default: no direction found");
                }
            }
            case SWITCHING_LANE -> {
                boolean sideWalkable = walkability.right || walkability.left;
                if (walkability.front && laneShiftDirection == LaneShiftDirection.FORWARD && sideWalkable) {
                    startLegacyAntiStuckPulse(config);
                    setRouteState(LegacyRouteState.SWITCHING_LANE);
                    return;
                }
                if (walkability.back && laneShiftDirection == LaneShiftDirection.BACKWARD && sideWalkable) {
                    startLegacyAntiStuckPulse(config);
                    setRouteState(LegacyRouteState.SWITCHING_LANE);
                    return;
                }
                if (walkability.right) {
                    setRouteState(LegacyRouteState.RIGHT);
                    updateMelonOrientationForState(config, true);
                    return;
                }
                if (walkability.left) {
                    setRouteState(LegacyRouteState.LEFT);
                    updateMelonOrientationForState(config, true);
                    return;
                }
                if (walkability.front) {
                    if (laneShiftDirection == LaneShiftDirection.BACKWARD) {
                        startLegacyAntiStuckPulse(config);
                        return;
                    }
                    setRouteState(LegacyRouteState.SWITCHING_LANE);
                    return;
                }
                if (walkability.back) {
                    if (laneShiftDirection == LaneShiftDirection.FORWARD) {
                        startLegacyAntiStuckPulse(config);
                        return;
                    }
                    setRouteState(LegacyRouteState.SWITCHING_LANE);
                    return;
                }

                setRouteState(LegacyRouteState.NONE);
                FarmHelperFabric.getWebhookService().debugTrace("macro", "melon default: switch lane no direction");
            }
            default -> setRouteState(calculateLegacyMelonDefaultDirection(client));
        }
    }

    void invokeLegacyMelonDefault(MinecraftClient client, FarmHelperConfig config, boolean holdAttack) {
        float routingYaw = getLaneRoutingYaw();
        Walkability walkability = computeWalkability(client, routingYaw);
        double velocity = 0.0;
        if (client.player != null) {
            velocity = Math.abs(client.player.getVelocity().x) + Math.abs(client.player.getVelocity().z);
        }
        switch (routeState) {
            case RIGHT, LEFT -> {
                boolean wantsRight = routeState == LegacyRouteState.RIGHT;
                moveRight = wantsRight;
                boolean forward = !walkability.back;
                holdMovement(client, config, forward, false, !wantsRight, wantsRight, false, holdAttack);
                directionLabel = wantsRight ? "MELON_RIGHT" : "MELON_LEFT";
            }
            case SWITCHING_LANE -> {
                if (client.player != null) {
                    float yawDelta = Math.abs(MathHelper.wrapDegrees(targetYaw - client.player.getYaw()));
                    if (yawDelta > MELON_SWITCH_ALIGN_TOLERANCE_DEGREES) {
                        holdMovement(client, config, false, false, false, false, false, holdAttack);
                        directionLabel = "MELON_SWITCH_ALIGN";
                        legacyScheduleNotMoving(25);
                        break;
                    }
                }
                if (velocity < 0.15 && !walkability.front) {
                    holdMovement(client, config, false, false, false, false, false, holdAttack);
                    directionLabel = "MELON_SWITCH_STALL";
                } else {
                    holdMovement(client, config, true, false, false, false, true, false);
                    directionLabel = "MELON_SWITCH";
                }
                legacyScheduleNotMoving(25);
            }
            default -> {
                holdMovement(client, config, false, false, false, false, false, false);
                directionLabel = "MELON_IDLE";
            }
        }
    }

    LegacyRouteState calculateLegacyVerticalDirection(MinecraftClient client) {
        float routingYaw = getLaneRoutingYaw();

        BlockState leftAtFeet = getRelativeBlockStateAtYaw(client, -1, 0, 0, routingYaw);
        BlockState leftBelow = getRelativeBlockStateAtYaw(client, -1, -1, 0, routingYaw);
        if ((leftAtFeet == null || leftAtFeet.isAir()) && (leftBelow == null || leftBelow.isAir())) {
            return LegacyRouteState.RIGHT;
        }
        BlockState rightAtFeet = getRelativeBlockStateAtYaw(client, 1, 0, 0, routingYaw);
        BlockState rightBelow = getRelativeBlockStateAtYaw(client, 1, -1, 0, routingYaw);
        if ((rightAtFeet == null || rightAtFeet.isAir()) && (rightBelow == null || rightBelow.isAir())) {
            return LegacyRouteState.LEFT;
        }

        if (isSideCropReady(client, 1, routingYaw)) {
            return LegacyRouteState.RIGHT;
        }
        if (isSideCropReady(client, -1, routingYaw)) {
            return LegacyRouteState.LEFT;
        }

        for (int i = 1; i < 180; i++) {
            if (!isWalkableOffset(client, i, 0, routingYaw)) {
                boolean canContinueRight = isWalkableOffset(client, i - 1, -1, 1, routingYaw)
                        || isWalkableOffset(client, i - 1, -1, 0, routingYaw);
                return canContinueRight ? LegacyRouteState.RIGHT : LegacyRouteState.LEFT;
            }
            if (!isWalkableOffset(client, -i, 0, routingYaw)) {
                boolean canContinueLeft = isWalkableOffset(client, -i + 1, 0, 1, routingYaw)
                        || isWalkableOffset(client, -i + 1, -1, 0, routingYaw);
                return canContinueLeft ? LegacyRouteState.LEFT : LegacyRouteState.RIGHT;
            }
        }
        return LegacyRouteState.RIGHT;
    }

    LegacyRouteState calculateLegacyMelonDefaultDirection(MinecraftClient client) {
        float routingYaw = getLaneRoutingYaw();
        for (int i = 0; i < 180; i++) {
            if (isMelonOrPumpkinRelative(client, i, 0, routingYaw)) {
                return LegacyRouteState.RIGHT;
            }
            if (isMelonOrPumpkinRelative(client, -i, 0, routingYaw)) {
                return LegacyRouteState.LEFT;
            }
            if (!isWalkableOffset(client, i, 0, routingYaw)) {
                return LegacyRouteState.LEFT;
            }
            if (!isWalkableOffset(client, -i, 0, routingYaw)) {
                return LegacyRouteState.RIGHT;
            }
        }
        return LegacyRouteState.NONE;
    }

    void updateLegacySugarCane(MinecraftClient client) {
        if (routeState == LegacyRouteState.NONE) {
            setRouteState(LegacyRouteState.S);
        }

        if (routeState == LegacyRouteState.S) {
            if (hasWallAtYaw(client, 0, -1, targetYaw - 45f)
                    && hasWallAtYaw(client, 0, -1, targetYaw + 45f)) {
                if (getNearestSideWall(client, targetYaw + 45f, -1) == -999) {
                    setRouteState(LegacyRouteState.A);
                }
                if (getNearestSideWall(client, targetYaw - 45f, 1) == -999) {
                    setRouteState(LegacyRouteState.D);
                }
            }
        } else if (routeState == LegacyRouteState.A || routeState == LegacyRouteState.D) {
            // Legacy behavior: A/D is a one-tick correction pulse before returning to S.
            setRouteState(LegacyRouteState.S);
        }
    }

    void invokeLegacySugarCane(MinecraftClient client, FarmHelperConfig config, boolean holdAttack) {
        switch (routeState) {
            case A -> {
                holdMovement(client, config, false, false, true, false, false, holdAttack);
                directionLabel = "SUGARCANE_A";
            }
            case D -> {
                holdMovement(client, config, false, false, false, true, false, holdAttack);
                directionLabel = "SUGARCANE_D";
            }
            case S -> {
                holdMovement(client, config, false, true, false, false, false, holdAttack);
                directionLabel = "SUGARCANE_S";
            }
            case NONE -> {
                holdMovement(client, config, false, false, false, false, false, false);
                directionLabel = "SUGARCANE_IDLE";
            }
            default -> {
                holdMovement(client, config, false, false, false, false, false, false);
                directionLabel = "SUGARCANE_IDLE";
            }
        }
    }

    void updateLegacyMushroom45(MinecraftClient client) {
        float routingYaw = getLaneRoutingYaw();
        Walkability walkability = computeWalkability(client, routingYaw);
        if (routeState == LegacyRouteState.NONE) {
            setRouteState(calculateMushroomDirection(client));
        }

        if (routeState == LegacyRouteState.LEFT) {
            if (walkability.right) {
                setRouteState(LegacyRouteState.RIGHT);
            } else if (!walkability.left) {
                setRouteState(LegacyRouteState.LEFT);
            } else {
                setRouteState(calculateMushroomDirection(client));
            }
        } else if (routeState == LegacyRouteState.RIGHT) {
            if (walkability.left) {
                setRouteState(LegacyRouteState.LEFT);
            } else if (!walkability.right) {
                setRouteState(LegacyRouteState.RIGHT);
            } else {
                setRouteState(calculateMushroomDirection(client));
            }
        } else {
            setRouteState(calculateMushroomDirection(client));
        }
    }

    void invokeLegacyMushroom45(MinecraftClient client, FarmHelperConfig config, boolean holdAttack) {
        boolean lookLeft = isMushroom45LookLeft();
        boolean forward = false;
        boolean left = false;
        boolean right = false;
        if (routeState != LegacyRouteState.LEFT && routeState != LegacyRouteState.RIGHT) {
            holdMovement(client, config, false, false, false, false, false, false);
            directionLabel = "MUSHROOM_IDLE";
            return;
        }
        if (config.alwaysHoldW) {
            forward = true;
        } else if (routeState == LegacyRouteState.RIGHT) {
            // Legacy: right lane uses either W or D depending on facing alignment.
            forward = !lookLeft;
            right = lookLeft;
        } else {
            // Legacy: left lane uses either W or A depending on facing alignment.
            forward = lookLeft;
            left = !lookLeft;
        }
        holdMovement(client, config, forward, false, left, right, false, holdAttack);
        directionLabel = routeState == LegacyRouteState.RIGHT ? "MUSHROOM_RIGHT" : "MUSHROOM_LEFT";
    }

    void updateLegacyMushroomRotate(MinecraftClient client, FarmHelperConfig config) {
        float routingYaw = getLaneRoutingYaw();
        Walkability walkability = computeWalkability(client, routingYaw);
        if (routeState == LegacyRouteState.NONE) {
            setRouteState(calculateMushroomDirection(client));
        }

        LegacyRouteState before = routeState;
        if (routeState == LegacyRouteState.LEFT) {
            if (walkability.right) {
                setRouteState(LegacyRouteState.RIGHT);
            } else if (walkability.left) {
                setRouteState(LegacyRouteState.LEFT);
            } else {
                setRouteState(calculateMushroomDirection(client));
            }
        } else if (routeState == LegacyRouteState.RIGHT) {
            if (walkability.left) {
                setRouteState(LegacyRouteState.LEFT);
            } else if (walkability.right) {
                setRouteState(LegacyRouteState.RIGHT);
            } else {
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
        if (!config.customPitch) {
            targetPitch = randomBetween(activeTuning.pitchMin(), activeTuning.pitchMax());
        }
    }

    void invokeLegacyMushroomRotate(MinecraftClient client, FarmHelperConfig config, boolean holdAttack) {
        if (routeState != LegacyRouteState.LEFT && routeState != LegacyRouteState.RIGHT) {
            holdMovement(client, config, false, false, false, false, false, false);
            directionLabel = "ROTATE_IDLE";
            return;
        }
        holdMovement(client, config, true, false, false, false, false, holdAttack);
        directionLabel = routeState == LegacyRouteState.LEFT ? "ROTATE_LEFT" : "ROTATE_RIGHT";
    }

    void updateLegacyMushroomSds(MinecraftClient client, FarmHelperConfig config) {
        float routingYaw = getLaneRoutingYaw();
        Walkability walkability = computeWalkability(client, routingYaw);
        if (routeState == LegacyRouteState.NONE) {
            setRouteState(calculateMushroomDirection(client));
        }

        switch (routeState) {
            case LEFT -> {
                if (walkability.back && !config.alwaysHoldW) {
                    setRouteState(LegacyRouteState.SWITCHING_LANE);
                    return;
                }
                setRouteState(walkability.left ? LegacyRouteState.LEFT : LegacyRouteState.NONE);
            }
            case RIGHT -> {
                if (walkability.back && !config.alwaysHoldW) {
                    setRouteState(LegacyRouteState.SWITCHING_LANE);
                    return;
                }
                setRouteState(walkability.right ? LegacyRouteState.RIGHT : LegacyRouteState.NONE);
            }
            case SWITCHING_LANE -> {
            }
            default -> setRouteState(calculateMushroomDirection(client));
        }
    }

    void invokeLegacyMushroomSds(MinecraftClient client, FarmHelperConfig config, boolean holdAttack) {
        float routingYaw = getLaneRoutingYaw();
        switch (routeState) {
            case LEFT -> {
                holdMovement(client, config, false, false, true, false, false, holdAttack);
                directionLabel = "SDS_LEFT";
            }
            case RIGHT -> {
                holdMovement(client, config, false, false, false, true, false, holdAttack);
                directionLabel = "SDS_RIGHT";
            }
            case SWITCHING_LANE -> {
                if (!isWalkableRelative(client, 0.0, -1.0, routingYaw)) {
                    setRouteState(LegacyRouteState.NONE);
                    holdMovement(client, config, false, false, false, false, false, false);
                    directionLabel = "SDS_IDLE";
                    return;
                }
                holdMovement(client, config, false, true, false, false, false, holdAttack);
                directionLabel = "SDS_SWITCH";
            }
            default -> {
                holdMovement(client, config, false, false, false, false, false, false);
                directionLabel = "SDS_IDLE";
            }
        }
    }

    private LegacyRouteState calculateLaneDirection(MinecraftClient client, boolean melonPriority) {
        float routingYaw = getLaneRoutingYaw();
        if (!melonPriority) {
            if (isSideCropReady(client, 1, routingYaw)) {
                return LegacyRouteState.RIGHT;
            }
            if (isSideCropReady(client, -1, routingYaw)) {
                return LegacyRouteState.LEFT;
            }
        }
        for (int i = 1; i < 180; i++) {
            if (melonPriority && isMelonOrPumpkinRelative(client, i, 0, routingYaw)) {
                return LegacyRouteState.RIGHT;
            }
            if (melonPriority && isMelonOrPumpkinRelative(client, -i, 0, routingYaw)) {
                return LegacyRouteState.LEFT;
            }
            if (!isWalkableOffset(client, i, 0, routingYaw)) {
                return LegacyRouteState.LEFT;
            }
            if (!isWalkableOffset(client, -i, 0, routingYaw)) {
                return LegacyRouteState.RIGHT;
            }
        }
        Walkability walkability = computeWalkability(client, routingYaw);
        if (walkability.right && !walkability.left) {
            return LegacyRouteState.RIGHT;
        }
        if (walkability.left && !walkability.right) {
            return LegacyRouteState.LEFT;
        }
        if (!melonPriority && activeType != LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT) {
            return LegacyRouteState.RIGHT;
        }
        return moveRight ? LegacyRouteState.RIGHT : LegacyRouteState.LEFT;
    }

    private LegacyRouteState calculateMushroomDirection(MinecraftClient client) {
        float routingYaw = getLaneRoutingYaw();
        if (isSideCropReady(client, 1, routingYaw)) {
            return LegacyRouteState.RIGHT;
        }
        if (isSideCropReady(client, -1, routingYaw)) {
            return LegacyRouteState.LEFT;
        }
        for (int i = 1; i < 180; i++) {
            if (!isWalkableOffset(client, i, 0, routingYaw)) {
                return LegacyRouteState.LEFT;
            }
            if (!isWalkableOffset(client, -i, 0, routingYaw)) {
                return LegacyRouteState.RIGHT;
            }
        }
        Walkability walkability = computeWalkability(client, routingYaw);
        if (walkability.right && !walkability.left) {
            return LegacyRouteState.RIGHT;
        }
        if (walkability.left && !walkability.right) {
            return LegacyRouteState.LEFT;
        }
        return LegacyRouteState.NONE;
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

    int getNearestSideWall(MinecraftClient client, float yawRef, int dir) {
        for (int i = 0; i < 8; i++) {
            if (hasWallAtYaw(client, i * dir, 0, yawRef)) {
                return i;
            }
        }
        return -999;
    }

    void updateLegacyCircular(MinecraftClient client) {
        if (routeState == LegacyRouteState.NONE || routeState == LegacyRouteState.W) {
            setRouteState(LegacyRouteState.D);
        } else if (routeState == LegacyRouteState.S) {
            setRouteState(LegacyRouteState.A);
        } else if (routeState == LegacyRouteState.A) {
            setRouteState(LegacyRouteState.W);
        } else if (routeState == LegacyRouteState.D) {
            setRouteState(LegacyRouteState.S);
        }
    }

    void invokeLegacyCircular(MinecraftClient client, FarmHelperConfig config, boolean holdAttack) {
        switch (routeState) {
            case A -> {
                holdMovement(client, config, false, false, true, false, false, holdAttack);
                directionLabel = "CIRCLE_A";
            }
            case D -> {
                holdMovement(client, config, false, false, false, true, false, holdAttack);
                directionLabel = "CIRCLE_D";
            }
            case S -> {
                holdMovement(client, config, false, true, false, false, false, holdAttack);
                directionLabel = "CIRCLE_S";
            }
            case W -> {
                holdMovement(client, config, true, false, false, false, false, holdAttack);
                directionLabel = "CIRCLE_W";
            }
            case NONE -> {
                holdMovement(client, config, false, false, false, false, false, false);
                directionLabel = "CIRCLE_IDLE";
            }
            default -> {
                holdMovement(client, config, false, false, false, false, false, false);
                directionLabel = "CIRCLE_IDLE";
            }
        }
    }

    LegacyRouteState calculateLegacyCocoaDirection(MinecraftClient client) {
        float routingYaw = getLaneRoutingYaw();
        Walkability walkability = computeWalkability(client, routingYaw);
        if (walkability.front && walkability.right) {
            return LegacyRouteState.FORWARD;
        }
        if (walkability.back) {
            return LegacyRouteState.BACKWARD;
        }
        if (walkability.front) {
            return LegacyRouteState.FORWARD;
        }
        if (walkability.back && walkability.left) {
            return LegacyRouteState.BACKWARD;
        }
        return LegacyRouteState.NONE;
    }

    void updateLegacyCocoa(MinecraftClient client) {
        float routingYaw = getLaneRoutingYaw();
        Walkability walkability = computeWalkability(client, routingYaw);
        if (routeState == LegacyRouteState.NONE) {
            setRouteState(calculateLegacyCocoaDirection(client));
        }
        switch (routeState) {
            case BACKWARD -> {
                if (walkability.front && !walkability.back && walkability.right) {
                    setRouteState(LegacyRouteState.SWITCHING_LANE);
                }
            }
            case FORWARD -> {
                if (!walkability.front && walkability.back && walkability.right && !walkability.left) {
                    setRouteState(LegacyRouteState.SWITCHING_SIDE);
                    return;
                }
                if (walkability.back) {
                    setRouteState(LegacyRouteState.BACKWARD);
                } else if (walkability.front) {
                    setRouteState(LegacyRouteState.FORWARD);
                } else {
                    setRouteState(LegacyRouteState.NONE);
                }
            }
            case SWITCHING_SIDE -> {
                if (walkability.back && !walkability.right && walkability.left) {
                    setRouteState(LegacyRouteState.BACKWARD);
                }
            }
            case SWITCHING_LANE -> {
                if (!walkability.back && !walkability.right && walkability.left) {
                    setRouteState(LegacyRouteState.FORWARD);
                }
            }
            case NONE -> setRouteState(calculateLegacyCocoaDirection(client));
            default -> setRouteState(LegacyRouteState.NONE);
        }
    }

    void invokeLegacyCocoa(MinecraftClient client, FarmHelperConfig config, boolean holdAttack) {
        float routingYaw = getLaneRoutingYaw();
        Walkability walkability = computeWalkability(client, routingYaw);
        boolean trapdoorMode = activeType == LegacyMacroType.S_COCOA_BEANS_TRAPDOORS;
        switch (routeState) {
            case BACKWARD -> {
                holdMovement(client, config, false, true, false, false, false, holdAttack);
                directionLabel = "COCOA_BACK";
            }
            case FORWARD -> {
                boolean hugWall = shouldHugCocoaWall(client, routingYaw, trapdoorMode);
                holdMovement(client, config, true, false, hugWall, false, false, holdAttack);
                directionLabel = hugWall ? "COCOA_FORWARD_HUG" : "COCOA_FORWARD";
            }
            case SWITCHING_LANE -> {
                if (hasCocoaLineChanged(client, routingYaw) && !walkability.back && walkability.left) {
                    setRouteState(LegacyRouteState.FORWARD);
                    directionLabel = "COCOA_FORWARD";
                    break;
                }
                holdMovement(client, config, false, false, false, true, false, false);
                directionLabel = "COCOA_SWITCH_LANE";
            }
            case SWITCHING_SIDE -> {
                holdMovement(client, config, false, false, false, true, false, false);
                directionLabel = "COCOA_SWITCH_SIDE";
            }
            default -> {
                holdMovement(client, config, false, false, false, false, false, false);
                directionLabel = "COCOA_IDLE";
            }
        }
    }

    boolean isMushroom45LookLeft() {
        float facing = ((getLaneRoutingYaw() % 360f) + 360f) % 360f;
        float difference = MathHelper.wrapDegrees(targetYaw) - 90f;
        if (facing == 90f) {
            if (difference < -220f) return true;
            if (difference > -140f) return false;
            return true;
        }
        if (facing == 270f) {
            if (difference < 40f) return true;
            if (difference > 40f) return false;
            return true;
        }
        if (facing == 180f) {
            if (difference > 40f) return true;
            if (difference < -40f) return false;
            return true;
        }
        if (difference < -130f) return true;
        if (difference < -40f) return false;
        return true;
    }

    boolean isSideCropReady(MinecraftClient client, int lateral, float yawRef) {
        if (!isWalkableOffset(client, lateral, 0, yawRef)) {
            return false;
        }
        BlockState side = getRelativeBlockState(client, lateral, 0, yawRef);
        if (side == null || side.isAir()) {
            return false;
        }
        if (!CropUtils.isCrop(side)) {
            return false;
        }
        return isBlockMatchingActiveCrop(side);
    }

    private boolean isBlockMatchingActiveCrop(BlockState state) {
        String key = state.getBlock().getTranslationKey().toLowerCase(Locale.ROOT);
        return switch (activeType) {
            case S_PUMPKIN_MELON, S_PUMPKIN_MELON_MELONGKINGDE, S_PUMPKIN_MELON_DEFAULT_PLOT ->
                    key.contains("melon") || key.contains("pumpkin");
            case S_SUGAR_CANE -> key.contains("sugar_cane") || key.contains("rose_bush") || key.contains("sunflower");
            case S_CACTUS, S_CACTUS_SUNTZU -> key.contains("cactus");
            case S_COCOA_BEANS, S_COCOA_BEANS_TRAPDOORS, S_COCOA_BEANS_LEFT_RIGHT -> key.contains("cocoa");
            case S_MUSHROOM, S_MUSHROOM_ROTATE, S_MUSHROOM_SDS -> key.contains("mushroom");
            case C_NORMAL_TYPE -> CropUtils.isCropReady(state);
            default -> CropUtils.isCropReady(state);
        };
    }

    boolean isWalkableOffset(MinecraftClient client, int lateral, int forward, float yawRef) {
        return isWalkableOffset(client, lateral, 0, forward, yawRef);
    }

    boolean isWalkableOffset(MinecraftClient client, int lateral, int vertical, int forward, float yawRef) {
        BlockPos feet = getRelativeBlockPos(client, lateral, forward, yawRef);
        if (vertical != 0) {
            feet = getRelativeBlockPos(client, (double) lateral, vertical, (double) forward, yawRef);
        }
        if (feet == null || client.world == null) {
            return false;
        }
        if (isVoidColumn(client, feet)) {
            return false;
        }
        return isPassable(client, feet) && isPassable(client, feet.up());
    }

    boolean hasWallAtYaw(MinecraftClient client, int lateral, int forward, float yawRef) {
        BlockPos pos = getRelativeBlockPos(client, lateral, forward, yawRef);
        if (pos == null) {
            return true;
        }
        return !isPassable(client, pos);
    }

    BlockState getRelativeBlockStateAtYaw(MinecraftClient client, int lateral, int vertical, int forward, float yawRef) {
        BlockPos pos = getRelativeBlockPos(client, lateral, forward, yawRef);
        if (pos == null || client.world == null) {
            return null;
        }
        return client.world.getBlockState(pos.add(0, vertical, 0));
    }

    boolean isWaterRelativeAtYaw(MinecraftClient client, int lateral, int vertical, int forward, float yawRef) {
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

        // For melon/pumpkin default plot, only re-evaluate direction when player
        // has stopped (low velocity), matching legacy 1.8.9 canChangeDirection().
        if (activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT) {
            double melonVelocity = 0.0;
            if (client.player != null) {
                melonVelocity = Math.abs(client.player.getVelocity().x) + Math.abs(client.player.getVelocity().z);
            }
            boolean melonStopped = melonVelocity < 0.1 && primaryTicks >= 4;
            if (melonStopped) {
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
            // Only check for lane switch when the player has stopped.
            double switchVelocity = 0.0;
            if (client.player != null) {
                switchVelocity = Math.abs(client.player.getVelocity().x) + Math.abs(client.player.getVelocity().z);
            }
            boolean switchStopped = switchVelocity < 0.1 && primaryTicks >= 4;
            if (switchStopped) {
                if (!walkability.front && walkability.back && !config.alwaysHoldW) {
                    shouldSwitch = true;
                    laneShiftDirection = LaneShiftDirection.BACKWARD;
                } else if (walkability.front && !walkability.back && !config.alwaysHoldW) {
                    laneShiftDirection = LaneShiftDirection.FORWARD;
                } else if (laneShiftDirection == LaneShiftDirection.BACKWARD && walkability.front) {
                    laneShiftDirection = LaneShiftDirection.FORWARD;
                }
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

    record Walkability(boolean front, boolean back, boolean left, boolean right) {
    }

    Walkability computeWalkability(MinecraftClient client, float yawRef) {
        boolean front = isWalkableRelative(client, 0.0, 1.0, yawRef);
        boolean back = isWalkableRelative(client, 0.0, -1.0, yawRef);
        boolean left = isWalkableRelative(client, -1.0, 0.0, yawRef);
        boolean right = isWalkableRelative(client, 1.0, 0.0, yawRef);
        return new Walkability(front, back, left, right);
    }

    boolean isWalkableRelative(MinecraftClient client, double lateral, double forward, float yawRef) {
        if (client.player == null || client.world == null) {
            return false;
        }
        BlockPos feetPos = getRelativeBlockPos(client, lateral, 0.0, forward, yawRef);
        if (feetPos == null) {
            return false;
        }
        if (isVoidColumn(client, feetPos)) {
            return false;
        }
        BlockPos headPos = feetPos.up();
        return isPassable(client, feetPos) && isPassable(client, headPos);
    }

    private boolean isPassable(MinecraftClient client, BlockPos pos) {
        if (client.world == null) {
            return false;
        }
        BlockState state = client.world.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        // Legacy walkability treated fluids/stems/reeds/wall-signs as passable for movement logic.
        if (!state.getFluidState().isEmpty()) {
            return true;
        }
        if (state.isOf(Blocks.WATER)
                || state.isOf(Blocks.LILY_PAD)
                || state.isOf(Blocks.SUGAR_CANE)
                || state.isOf(Blocks.MELON_STEM)
                || state.isOf(Blocks.ATTACHED_MELON_STEM)
                || state.isOf(Blocks.PUMPKIN_STEM)
                || state.isOf(Blocks.ATTACHED_PUMPKIN_STEM)) {
            return true;
        }
        String key = state.getBlock().getTranslationKey().toLowerCase(Locale.ROOT);
        if (key.contains("wall_sign")) {
            return true;
        }
        VoxelShape shape = state.getCollisionShape(client.world, pos);
        return shape.isEmpty();
    }

    private boolean isVoidColumn(MinecraftClient client, BlockPos pos) {
        if (client.world == null || pos == null) {
            return true;
        }
        for (int y = pos.getY(); y >= 65; y--) {
            BlockState state = client.world.getBlockState(new BlockPos(pos.getX(), y, pos.getZ()));
            if (!state.isAir()) {
                return false;
            }
        }
        return true;
    }

    boolean isMelonOrPumpkinRelative(MinecraftClient client, int lateral, int forward, float yawRef) {
        BlockState state = getRelativeBlockState(client, lateral, forward, yawRef);
        // Legacy behavior only treated full melon/pumpkin blocks as lane markers.
        return isLegacyMelonOrPumpkinBlock(state);
    }

    private boolean isLegacyMelonOrPumpkinBlock(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        return state.isOf(Blocks.MELON)
                || state.isOf(Blocks.PUMPKIN);
    }

    private boolean isLegacyMelonOrCarvedPumpkinBlock(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        return state.isOf(Blocks.MELON)
                || state.isOf(Blocks.PUMPKIN)
                || state.isOf(Blocks.CARVED_PUMPKIN)
                || state.isOf(Blocks.JACK_O_LANTERN);
    }

    BlockPos getRelativeBlockPos(MinecraftClient client, int lateral, int forward, float yawRef) {
        return getRelativeBlockPos(client, (double) lateral, 0.0, (double) forward, yawRef);
    }

    BlockPos getRelativeBlockPos(MinecraftClient client, double lateral, double vertical, double forward, float yawRef) {
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

    BlockState getRelativeBlockState(MinecraftClient client, int lateral, int forward, float yawRef) {
        BlockPos pos = getRelativeBlockPos(client, lateral, forward, yawRef);
        if (pos == null || client.world == null) {
            return null;
        }
        return client.world.getBlockState(pos);
    }

    boolean shouldHugCocoaWall(MinecraftClient client, float yawRef, boolean trapdoorMode) {
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

    boolean hasCocoaLineChanged(MinecraftClient client, float yawRef) {
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

    private LegacyRouteState oppositeSide(LegacyRouteState side) {
        return switch (side) {
            case LEFT -> LegacyRouteState.RIGHT;
            case RIGHT -> LegacyRouteState.LEFT;
            default -> LegacyRouteState.NONE;
        };
    }

    private LegacyRouteState resolveRouteAfterLaneSwitch(MinecraftClient client, Walkability walkability, boolean melonPriority) {
        LegacyRouteState preferred = oppositeSide(previousRouteState);
        if (preferred == LegacyRouteState.RIGHT && walkability.right) {
            return LegacyRouteState.RIGHT;
        }
        if (preferred == LegacyRouteState.LEFT && walkability.left) {
            return LegacyRouteState.LEFT;
        }
        LegacyRouteState calculated;
        if (activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT) {
            calculated = calculateLegacyMelonDefaultDirection(client);
        } else if (activeType == LegacyMacroType.S_V_NORMAL_TYPE
                || activeType == LegacyMacroType.S_PUMPKIN_MELON
                || activeType == LegacyMacroType.S_PUMPKIN_MELON_MELONGKINGDE
                || activeType == LegacyMacroType.S_CACTUS
                || activeType == LegacyMacroType.S_CACTUS_SUNTZU
                || activeType == LegacyMacroType.S_COCOA_BEANS_LEFT_RIGHT) {
            calculated = calculateLegacyVerticalDirection(client);
        } else {
            calculated = calculateLaneDirection(client, melonPriority);
        }
        if (calculated == LegacyRouteState.RIGHT && walkability.right) {
            return LegacyRouteState.RIGHT;
        }
        if (calculated == LegacyRouteState.LEFT && walkability.left) {
            return LegacyRouteState.LEFT;
        }
        if (walkability.right && !walkability.left) {
            return LegacyRouteState.RIGHT;
        }
        if (walkability.left && !walkability.right) {
            return LegacyRouteState.LEFT;
        }
        return preferred != LegacyRouteState.NONE ? preferred : calculated;
    }

    private LaneShiftDirection resolveInitialLaneShiftDirection(float playerYaw, float laneYaw) {
        float delta = Math.abs(MathHelper.wrapDegrees(playerYaw - laneYaw));
        return delta <= 90f ? LaneShiftDirection.FORWARD : LaneShiftDirection.BACKWARD;
    }

    private boolean resolveFacingPreferredSide(float playerYaw, float laneYaw, boolean fallbackRight) {
        float rightMovementYaw = laneYaw - 90f;
        float leftMovementYaw = laneYaw + 90f;
        float rightDelta = Math.abs(MathHelper.wrapDegrees(playerYaw - rightMovementYaw));
        float leftDelta = Math.abs(MathHelper.wrapDegrees(playerYaw - leftMovementYaw));
        if (Math.abs(rightDelta - leftDelta) < 8f) {
            return fallbackRight;
        }
        return rightDelta < leftDelta;
    }

    private boolean resolveInitialMelonDirection(MinecraftClient client, float yawRef) {
        if (client.player == null || client.world == null) {
            return true;
        }
        boolean facingPreferredRight = resolveFacingPreferredSide(client.player.getYaw(), yawRef, moveRight);
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
        return facingPreferredRight;
    }

    private float resolveInitialLaneReferenceYaw(MinecraftClient client, float baseYawRef) {
        float fallback = closestCardinal(baseYawRef);
        if (client.player == null || client.world == null) {
            return fallback;
        }
        LaneSelection selection = selectLaneYaw(client, fallback);
        if (selection == null) {
            return fallback;
        }
        return selection.chosenYaw();
    }

    private OrientationSnapshot analyzeOrientationSnapshot(MinecraftClient client, FarmHelperConfig config, float fallbackBaseYaw) {
        float fallbackLaneYaw = resolveInitialLaneReferenceYaw(client, fallbackBaseYaw);
        LaneSelection laneSelection = selectLaneYaw(client, fallbackLaneYaw);
        float selectedLaneYaw = laneSelection == null ? fallbackLaneYaw : laneSelection.chosenYaw();
        boolean preferredRight = resolveInitialMelonDirection(client, selectedLaneYaw);
        float selectedBaseYaw = resolveAutoBaseYaw(fallbackBaseYaw, selectedLaneYaw, preferredRight);
        float selectedPitch = resolveAutoPitch(client, selectedLaneYaw, selectedBaseYaw, preferredRight);
        String[] slices = buildOrientationSlices(client, selectedLaneYaw);
        boolean confident = laneSelection == null || laneSelection.margin() >= 0.55;
        LaneScore best = laneSelection == null ? null : laneSelection.best();
        LaneScore runnerUp = laneSelection == null ? null : laneSelection.runnerUp();

        // Keep user-controlled values authoritative.
        if (config.customYaw) {
            selectedBaseYaw = config.customYawLevel;
            selectedLaneYaw = closestCardinal(config.customYawLevel);
        }
        if (config.customPitch) {
            selectedPitch = config.customPitchLevel;
        }

        return new OrientationSnapshot(
                fallbackBaseYaw,
                selectedBaseYaw,
                selectedLaneYaw,
                selectedPitch,
                confident,
                preferredRight,
                best,
                runnerUp,
                slices
        );
    }

    private void emitOrientationSnapshot(OrientationSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        String summary = String.format(
                Locale.US,
                "macro=%s fallbackBase=%.2f lane=%.2f base=%.2f pitch=%.2f preferredSide=%s confident=%s margin=%.2f",
                activeType,
                snapshot.fallbackBaseYaw(),
                snapshot.selectedLaneYaw(),
                snapshot.selectedBaseYaw(),
                snapshot.selectedPitch(),
                snapshot.preferredRight() ? "RIGHT" : "LEFT",
                snapshot.confident(),
                snapshot.bestCandidate() == null || snapshot.runnerUpCandidate() == null
                        ? 9.99
                        : snapshot.bestCandidate().score() - snapshot.runnerUpCandidate().score()
        );
        FarmHelperFabric.getWebhookService().debugTrace("farm-snapshot", summary);

        if (snapshot.bestCandidate() != null) {
            FarmHelperFabric.getWebhookService().debugTrace("farm-snapshot", "candidate-best " + formatLaneScore(snapshot.bestCandidate()));
        }
        if (snapshot.runnerUpCandidate() != null) {
            FarmHelperFabric.getWebhookService().debugTrace("farm-snapshot", "candidate-alt " + formatLaneScore(snapshot.runnerUpCandidate()));
        }
        if (snapshot.slices() != null) {
            for (String slice : snapshot.slices()) {
                FarmHelperFabric.getWebhookService().debugTrace("farm-snapshot", slice);
            }
        }
    }

    private String formatLaneScore(LaneScore laneScore) {
        if (laneScore == null) {
            return "(none)";
        }
        return String.format(
                Locale.US,
                "yaw=%.0f score=%.2f walk[F=%s,B=%s,L=%s,R=%s] run[L=%d,R=%d] markers=%d",
                laneScore.yaw(),
                laneScore.score(),
                laneScore.front(),
                laneScore.back(),
                laneScore.left(),
                laneScore.right(),
                laneScore.leftRun(),
                laneScore.rightRun(),
                laneScore.markerCount()
        );
    }

    private LaneSelection selectLaneYaw(MinecraftClient client, float fallbackYaw) {
        float[] candidates = new float[]{0f, 90f, 180f, 270f};
        LaneScore best = null;
        LaneScore runnerUp = null;
        for (float candidate : candidates) {
            LaneScore score = scoreLaneReferenceYawDetailed(client, candidate);
            if (best == null || score.score() > best.score()) {
                runnerUp = best;
                best = score;
            } else if (runnerUp == null || score.score() > runnerUp.score()) {
                runnerUp = score;
            }
        }
        if (best == null) {
            return null;
        }

        float chosenYaw = best.yaw();
        double margin = runnerUp == null ? 9.99 : best.score() - runnerUp.score();
        if (runnerUp != null && margin < 0.55) {
            float bestDelta = Math.abs(MathHelper.wrapDegrees(best.yaw() - fallbackYaw));
            float altDelta = Math.abs(MathHelper.wrapDegrees(runnerUp.yaw() - fallbackYaw));
            if (altDelta < bestDelta) {
                chosenYaw = runnerUp.yaw();
            }
        }
        return new LaneSelection(best, runnerUp, chosenYaw, margin);
    }

    private double scoreLaneReferenceYaw(MinecraftClient client, float yawRef) {
        return scoreLaneReferenceYawDetailed(client, yawRef).score();
    }

    private LaneScore scoreLaneReferenceYawDetailed(MinecraftClient client, float yawRef) {
        Walkability walkability = computeWalkability(client, yawRef);
        int leftRun = measureSideRun(client, yawRef, -1, 24);
        int rightRun = measureSideRun(client, yawRef, 1, 24);
        int markerCount = countMarkerSignals(client, yawRef, 8);

        int sideOpen = (walkability.left ? 1 : 0) + (walkability.right ? 1 : 0);
        int forwardBackOpen = (walkability.front ? 1 : 0) + (walkability.back ? 1 : 0);

        double markerWeight = isMelonFamilyMacro() ? 2.40 : 0.95;
        double score = markerCount * markerWeight
                + sideOpen * 3.1
                + forwardBackOpen * 1.15
                + Math.min(leftRun, rightRun) * 0.28
                + Math.max(leftRun, rightRun) * 0.12;
        if (sideOpen == 0) {
            score -= 10.0;
        }
        if (!walkability.back) {
            score += activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT ? 2.35 : 0.8;
        }
        if (Math.abs(leftRun - rightRun) <= 2) {
            score += 0.75;
        }
        if (walkability.front && !walkability.back) {
            score += 0.55;
        }
        if (!walkability.left && !walkability.right) {
            score -= 4.0;
        }
        return new LaneScore(
                yawRef,
                score,
                walkability.front,
                walkability.back,
                walkability.left,
                walkability.right,
                leftRun,
                rightRun,
                markerCount
        );
    }

    private int measureSideRun(MinecraftClient client, float yawRef, int sideDirection, int maxScan) {
        int run = 0;
        for (int i = 1; i <= maxScan; i++) {
            if (isWalkableOffset(client, sideDirection * i, 0, yawRef)) {
                run++;
            } else {
                break;
            }
        }
        return run;
    }

    private int countMarkerSignals(MinecraftClient client, float yawRef, int scanDistance) {
        int score = 0;
        for (int i = 1; i <= scanDistance; i++) {
            if (isMelonOrPumpkinRelative(client, i, 0, yawRef)) {
                score += isMelonFamilyMacro() ? 3 : 1;
            }
            if (isMelonOrPumpkinRelative(client, -i, 0, yawRef)) {
                score += isMelonFamilyMacro() ? 3 : 1;
            }
            BlockState right = getRelativeBlockState(client, i, 0, yawRef);
            BlockState left = getRelativeBlockState(client, -i, 0, yawRef);
            if (right != null && !right.isAir() && CropUtils.isCrop(right) && isBlockMatchingActiveCrop(right)) {
                score += 2;
            }
            if (left != null && !left.isAir() && CropUtils.isCrop(left) && isBlockMatchingActiveCrop(left)) {
                score += 2;
            }
        }
        if (isMelonOrPumpkinRelative(client, 1, 0, yawRef)) {
            score += 4;
        }
        if (isMelonOrPumpkinRelative(client, -1, 0, yawRef)) {
            score += 4;
        }
        return score;
    }

    private boolean isMelonFamilyMacro() {
        return activeType == LegacyMacroType.S_PUMPKIN_MELON
                || activeType == LegacyMacroType.S_PUMPKIN_MELON_MELONGKINGDE
                || activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT;
    }

    private float resolveAutoBaseYaw(float fallbackBaseYaw, float selectedLaneYaw, boolean preferredRight) {
        if (activeTuning.yawMode() == CropYawMode.CARDINAL) {
            return selectedLaneYaw;
        }
        if (activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT) {
            float offset = melonYawOffsetDegrees();
            return selectedLaneYaw + (preferredRight ? offset : -offset);
        }
        float optionA = closestDiagonal(selectedLaneYaw + 45f);
        float optionB = closestDiagonal(selectedLaneYaw - 45f);
        float optionADelta = Math.abs(MathHelper.wrapDegrees(optionA - fallbackBaseYaw));
        float optionBDelta = Math.abs(MathHelper.wrapDegrees(optionB - fallbackBaseYaw));
        return optionADelta <= optionBDelta ? optionA : optionB;
    }

    private float resolveAutoPitch(MinecraftClient client, float selectedLaneYaw, float selectedBaseYaw, boolean preferredRight) {
        float fallback = randomBetween(activeTuning.pitchMin(), activeTuning.pitchMax());
        BlockPos probe = findPitchProbePos(client, selectedLaneYaw, preferredRight);
        if (probe == null || client.player == null) {
            return fallback;
        }
        Vec3d eyes = client.player.getEyePos();
        Vec3d target = Vec3d.ofCenter(probe).add(0.0, 0.15, 0.0);
        double dx = target.x - eyes.x;
        double dz = target.z - eyes.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDistance < 0.001) {
            return fallback;
        }
        float computedPitch = (float) (-Math.toDegrees(Math.atan2(target.y - eyes.y, horizontalDistance)));
        float minPitch = Math.min(activeTuning.pitchMin(), activeTuning.pitchMax());
        float maxPitch = Math.max(activeTuning.pitchMin(), activeTuning.pitchMax());
        if (activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT) {
            minPitch = Math.max(minPitch, 32.0f);
            maxPitch = Math.min(maxPitch + 0.35f, 35.0f);
        }
        float laneAlignedPitch = MathHelper.clamp(computedPitch, minPitch, maxPitch);
        if (Float.isNaN(laneAlignedPitch) || Float.isInfinite(laneAlignedPitch)) {
            return fallback;
        }
        if (Math.abs(MathHelper.wrapDegrees(selectedBaseYaw - selectedLaneYaw)) < 6.0f) {
            return fallback;
        }
        return laneAlignedPitch;
    }

    private BlockPos findPitchProbePos(MinecraftClient client, float selectedLaneYaw, boolean preferredRight) {
        if (client.player == null || client.world == null) {
            return null;
        }
        BlockPos bestPos = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int forward = 0; forward <= 8; forward++) {
            for (int lateral = -3; lateral <= 3; lateral++) {
                for (int vertical = -1; vertical <= 1; vertical++) {
                    BlockPos base = getRelativeBlockPos(client, lateral, forward, selectedLaneYaw);
                    if (base == null) {
                        continue;
                    }
                    BlockPos probe = base.add(0, vertical, 0);
                    BlockState state = client.world.getBlockState(probe);
                    double score = scorePitchProbe(state, lateral, forward, preferredRight);
                    if (score > bestScore) {
                        bestScore = score;
                        bestPos = probe;
                    }
                }
            }
        }
        return bestPos;
    }

    private double scorePitchProbe(BlockState state, int lateral, int forward, boolean preferredRight) {
        if (state == null || state.isAir()) {
            return Double.NEGATIVE_INFINITY;
        }
        double score = 0.0;
        if (isLegacyMelonOrPumpkinBlock(state)) {
            score += 12.0;
        } else if (CropUtils.isCrop(state) && isBlockMatchingActiveCrop(state)) {
            score += 7.0;
        } else if (CropUtils.isCrop(state)) {
            score += 2.0;
        } else {
            return Double.NEGATIVE_INFINITY;
        }
        score += Math.max(0.0, 8.0 - forward * 0.7);
        if (preferredRight && lateral > 0) {
            score += 1.2;
        } else if (!preferredRight && lateral < 0) {
            score += 1.2;
        }
        score -= Math.abs(lateral) * 0.45;
        return score;
    }

    private String[] buildOrientationSlices(MinecraftClient client, float yawRef) {
        return new String[]{
                buildOrientationSlice(client, yawRef, -1),
                buildOrientationSlice(client, yawRef, 0),
                buildOrientationSlice(client, yawRef, 1)
        };
    }

    private String buildOrientationSlice(MinecraftClient client, float yawRef, int verticalOffset) {
        if (client.player == null || client.world == null) {
            return "slice[y=" + verticalOffset + "]=<unavailable>";
        }
        final int radius = 4;
        StringJoiner rows = new StringJoiner("/");
        for (int forward = radius; forward >= -radius; forward--) {
            StringBuilder row = new StringBuilder(radius * 2 + 1);
            for (int lateral = -radius; lateral <= radius; lateral++) {
                BlockState state = getRelativeBlockStateAtYaw(client, lateral, verticalOffset, forward, yawRef);
                row.append(mapBlockForSnapshot(state));
            }
            rows.add(row.toString());
        }
        return String.format(Locale.US, "slice[y=%d yaw=%.0f]=%s", verticalOffset, yawRef, rows);
    }

    private char mapBlockForSnapshot(BlockState state) {
        if (state == null) {
            return '?';
        }
        if (state.isAir()) {
            return '.';
        }
        if (!state.getFluidState().isEmpty()) {
            return '~';
        }
        if (isLegacyMelonOrPumpkinBlock(state)) {
            return 'M';
        }
        if (state.isOf(Blocks.BARRIER)) {
            return 'B';
        }
        if (CropUtils.isCrop(state)) {
            return CropUtils.isCropReady(state) ? 'C' : 'c';
        }
        return '#';
    }

    float getLaneRoutingYaw() {
        if (laneReferenceYawResolved) {
            return closestCardinal(laneReferenceYaw);
        }
        if (targetYaw != 0f) {
            return closestCardinal(targetYaw);
        }
        return closestCardinal(baseYaw);
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

        if (activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT) {
            boolean anyMovementKey = client.options.forwardKey.isPressed()
                    || client.options.backKey.isPressed()
                    || client.options.leftKey.isPressed()
                    || client.options.rightKey.isPressed();
            if (!anyMovementKey || legacyAntiStuckTicksRemaining > 0) {
                lastMoveSample = current;
                stuckTicks = 0;
                return;
            }
            double dx = current.x - lastMoveSample.x;
            double dz = current.z - lastMoveSample.z;
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            int threshold = Math.max(16, Math.min(60, Math.max(1, config.stationaryFailsafeTicks) / 2));
            if (horizontalDistance < 0.010) {
                stuckTicks++;
                if (stuckTicks >= threshold) {
                    startLegacyAntiStuckPulse(config);
                    stuckTicks = 0;
                }
            } else {
                stuckTicks = 0;
            }
            lastMoveSample = current;
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

    private boolean handleStartupAlignment(MinecraftClient client, FarmHelperConfig config, boolean holdAttack) {
        ClientPlayerEntity player = client.player;
        if (!startupAlignmentPending || player == null) {
            return false;
        }

        float yawDelta = Math.abs(MathHelper.wrapDegrees(targetYaw - player.getYaw()));
        float pitchDelta = Math.abs(MathHelper.wrapDegrees(targetPitch - player.getPitch()));
        boolean alignedNow = yawDelta <= 3.0f && pitchDelta <= 2.2f;
        startupAlignmentStableTicks = alignedNow ? startupAlignmentStableTicks + 1 : 0;

        long alignTimeoutTicks = 34L;
        if (startupAlignmentStableTicks >= 3 || runtimeTicks - startupAlignmentStartTick >= alignTimeoutTicks) {
            startupAlignmentPending = false;
            startupAlignmentStableTicks = 0;
            return false;
        }

        // Keep breaking while aligning, but don't begin pathing/strafe keys until view settles.
        holdMovement(client, config, false, false, false, false, false, holdAttack);
        directionLabel = "ALIGNING_START";
        return true;
    }

    private void applyOrientation(MinecraftClient client, FarmHelperConfig config) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }

        if (!config.useLegacyProfileDefaults && activeTuning.motionMode() != CropMacroMotionMode.MUSHROOM_ROTATE) {
            targetYaw = baseYaw;
        }

        sampleRotationSmoothing(config);
        boolean disableSimulationOffsets = activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT;
        float simulationYawOffset = disableSimulationOffsets ? 0f : playerSimulation.getYawOffset(config, runtimeTicks);
        float simulationPitchOffset = disableSimulationOffsets ? 0f : playerSimulation.getPitchOffset(config, runtimeTicks);
        float yawStep = sampledYawStep;
        float pitchStep = sampledPitchStep;
        if (activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT) {
            if (routeState == LegacyRouteState.SWITCHING_LANE) {
                yawStep = Math.max(yawStep, MELON_SWITCH_YAW_STEP_DEGREES);
            } else {
                yawStep = Math.min(yawStep, 6.5f);
                pitchStep = Math.min(pitchStep, 4.0f);
            }
        }
        if (startupAlignmentPending) {
            yawStep = Math.min(yawStep, 4.0f);
            pitchStep = Math.min(pitchStep, 2.8f);
        }
        player.setYaw(approachAngle(player.getYaw(), targetYaw + simulationYawOffset, yawStep));
        player.setPitch(MathHelper.clamp(approach(player.getPitch(), targetPitch + simulationPitchOffset, pitchStep), -90f, 90f));
    }

    private void applyCustomOrientation(MinecraftClient client, FarmHelperConfig config) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        sampleRotationSmoothing(config);
        boolean disableSimulationOffsets = activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT;
        float simulationYawOffset = disableSimulationOffsets ? 0f : playerSimulation.getYawOffset(config, runtimeTicks);
        float simulationPitchOffset = disableSimulationOffsets ? 0f : playerSimulation.getPitchOffset(config, runtimeTicks);
        float yawStep = startupAlignmentPending ? Math.min(sampledYawStep, 4.0f) : sampledYawStep;
        float pitchStep = startupAlignmentPending ? Math.min(sampledPitchStep, 2.8f) : sampledPitchStep;
        if (config.customYaw) {
            player.setYaw(approachAngle(player.getYaw(), config.customYawLevel + simulationYawOffset, yawStep));
        }
        if (config.customPitch) {
            player.setPitch(MathHelper.clamp(approach(player.getPitch(), config.customPitchLevel + simulationPitchOffset, pitchStep), -90f, 90f));
        }
    }

    private void sampleRotationSmoothing(FarmHelperConfig config) {
        if (!config.useLegacyRandomDelays) {
            sampledYawStep = 4.5f;
            sampledPitchStep = 2.2f;
            return;
        }
        if (runtimeTicks < nextRotationSampleTick) {
            return;
        }
        int baseMs = Math.max(200, config.rotationTimeMs);
        int randomMs = Math.max(0, config.rotationTimeRandomnessMs);
        int sampledMs = baseMs + (randomMs <= 0 ? 0 : ThreadLocalRandom.current().nextInt(randomMs + 1));
        float ticks = Math.max(1f, sampledMs / 50f);
        sampledYawStep = MathHelper.clamp(60f / ticks, 1.1f, 8.0f);
        sampledPitchStep = MathHelper.clamp(30f / ticks, 0.8f, 6.0f);
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

    void holdMovement(
            MinecraftClient client,
            FarmHelperConfig config,
            boolean forward,
            boolean back,
            boolean left,
            boolean right,
            boolean sprint,
            boolean attack
    ) {
        boolean requestedForward = forward;
        boolean requestedBack = back;
        boolean requestedLeft = left;
        boolean requestedRight = right;

        if (config.useLegacyProfileDefaults
                && shouldApplyLegacyWallHugAssist()
                && (left ^ right)
                && !forward
                && !back
                && client != null
                && client.player != null
                && client.world != null) {
            Walkability walkability = computeWalkability(client, getLaneRoutingYaw());
            if (!walkability.back() && walkability.front()) {
                forward = true;
            } else if (!walkability.front() && walkability.back()) {
                back = true;
            }
        }

        if (!config.useLegacyProfileDefaults
                && config.alwaysHoldW
                && !back
                && activeTuning.motionMode() != CropMacroMotionMode.CIRCULAR) {
            forward = true;
        }
        boolean bypassSimulation = startupAlignmentPending
                || recoveryTicksRemaining > 0
                || config.useLegacyProfileDefaults
                || routeState == LegacyRouteState.SWITCHING_LANE
                || activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT;
        PlayerSimulation.MovementDecision decision = bypassSimulation
                ? new PlayerSimulation.MovementDecision(forward, back, left, right, sprint, attack)
                : playerSimulation.adjustMovement(
                        config,
                        runtimeTicks,
                        forward,
                        back,
                        left,
                        right,
                        sprint,
                        attack
                );
        boolean outForward = decision.forward();
        boolean outBack = decision.back();
        boolean outLeft = decision.left();
        boolean outRight = decision.right();
        if (outForward && outBack) {
            if (requestedBack && !requestedForward) {
                outForward = false;
            } else {
                outBack = false;
            }
        }
        if (outLeft && outRight) {
            if (requestedLeft && !requestedRight) {
                outRight = false;
            } else {
                outLeft = false;
            }
        }
        decision = new PlayerSimulation.MovementDecision(
                outForward,
                outBack,
                outLeft,
                outRight,
                decision.sprint(),
                decision.attack()
        );
        setPressed(client.options.forwardKey, decision.forward());
        setPressed(client.options.backKey, decision.back());
        setPressed(client.options.leftKey, decision.left());
        setPressed(client.options.rightKey, decision.right());
        setPressed(client.options.sprintKey, decision.sprint());
        setPressed(client.options.jumpKey, false);
        setPressed(client.options.sneakKey, false);
        setPressed(client.options.attackKey, decision.attack());
        lastMovementDecision = decision;
        lastMovementDecisionTick = runtimeTicks;
    }

    private boolean shouldApplyLegacyWallHugAssist() {
        return switch (activeType) {
            case S_V_NORMAL_TYPE,
                    S_PUMPKIN_MELON,
                    S_PUMPKIN_MELON_MELONGKINGDE,
                    S_CACTUS,
                    S_CACTUS_SUNTZU,
                    S_PUMPKIN_MELON_DEFAULT_PLOT -> true;
            default -> false;
        };
    }

    void stopAllMovement(MinecraftClient client) {
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
        float normalized = ((yaw % 360f) + 360f) % 360f;
        if (normalized < 45f || normalized > 315f) {
            return 0f;
        } else if (normalized < 135f) {
            return 90f;
        } else if (normalized < 225f) {
            return 180f;
        }
        return 270f;
    }

    private float closestDiagonal(float yaw) {
        float normalized = ((yaw % 360f) + 360f) % 360f;
        // Keep legacy bucket semantics exactly (not nearest-angle math).
        if (normalized < 90f && normalized > 0f) {
            return 45f;
        } else if (normalized < 180f) {
            return 135f;
        } else if (normalized < 270f) {
            return 225f;
        }
        return 315f;
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
        if (!laneReferenceYawResolved) {
            laneReferenceYaw = closestCardinal(baseYaw);
            laneReferenceYawResolved = true;
        }
        float yawOffset = melonYawOffsetDegrees();
        float leftYaw = laneReferenceYaw - yawOffset;
        float rightYaw = laneReferenceYaw + yawOffset;
        switch (routeState) {
            case LEFT -> targetYaw = leftYaw - randomBetween(0.0f, 2.0f);
            case RIGHT -> targetYaw = rightYaw + randomBetween(0.0f, 2.0f);
            case SWITCHING_LANE -> {
                boolean fromRight = previousRouteState == LegacyRouteState.RIGHT
                        || (previousRouteState == LegacyRouteState.NONE && moveRight);
                float micro = randomBetween(0.2f, 0.6f);
                targetYaw = laneReferenceYaw + (fromRight ? -micro : micro);
            }
            default -> {
                if (lastMelonYawState == LegacyRouteState.LEFT) {
                    targetYaw = leftYaw - randomBetween(0.0f, 2.0f);
                } else if (lastMelonYawState == LegacyRouteState.RIGHT) {
                    targetYaw = rightYaw + randomBetween(0.0f, 2.0f);
                } else {
                    targetYaw = moveRight
                            ? rightYaw + randomBetween(0.0f, 2.0f)
                            : leftYaw - randomBetween(0.0f, 2.0f);
                }
            }
        }
        if (!config.customPitch && (force || routeState != lastMelonYawState)) {
            targetPitch = randomBetween(activeTuning.pitchMin(), activeTuning.pitchMax());
        }
        if (routeState == LegacyRouteState.LEFT || routeState == LegacyRouteState.RIGHT) {
            lastMelonYawState = routeState;
        }
    }

    private float melonYawOffsetDegrees() {
        if (activeType != LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT) {
            return MELON_DEFAULT_YAW_OFFSET_DEGREES;
        }
        // Lower pitch needs a wider diagonal aim to start mining before the player reaches the block.
        if (activeTuning.pitchMax() <= 36.0f) {
            return MELON_LOW_PITCH_YAW_OFFSET_DEGREES;
        }
        return MELON_DEFAULT_YAW_OFFSET_DEGREES;
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

    private void emitMacroDebugTelemetry(MinecraftClient client, FarmHelperConfig config, boolean holdAttackRequested) {
        if (client == null || client.player == null || config == null || !config.debugMode) {
            return;
        }
        renderPlannedMovementPath(client, lastMovementDecision);

        Vec3d currentPos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        double dx = currentPos.x - lastLoggedPosition.x;
        double dy = currentPos.y - lastLoggedPosition.y;
        double dz = currentPos.z - lastLoggedPosition.z;
        double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean stateChanged = routeState != lastLoggedRouteState || !directionLabel.equals(lastLoggedDirection);
        boolean decisionChanged = lastMovementDecisionTick == runtimeTicks;
        if (!stateChanged && !decisionChanged && moved < 0.03 && runtimeTicks - lastMacroDebugTick < 10L) {
            return;
        }
        if (!stateChanged && runtimeTicks - lastMacroDebugTick < 2L) {
            return;
        }

        float routingYaw = getLaneRoutingYaw();
        Walkability walkability = computeWalkability(client, routingYaw);
        String decision = formatMovementDecision(lastMovementDecision);
        String planned = formatPlannedPathPreview(client, lastMovementDecision, 6);
        String line = String.format(
                Locale.US,
                "tick=%d macro=%s route=%s prev=%s label=%s laneDir=%s routeTicks=%d holdAttackReq=%s keys=%s pos=(%.2f,%.2f,%.2f) moved=%.3f yaw=%.2f pitch=%.2f target=(%.2f,%.2f) routingYaw=%.2f walk[F=%s,B=%s,L=%s,R=%s] planned=%s",
                runtimeTicks,
                activeType,
                routeState,
                previousRouteState,
                directionLabel,
                laneShiftDirection,
                routeStateTicks,
                holdAttackRequested,
                decision,
                currentPos.x, currentPos.y, currentPos.z,
                moved,
                client.player.getYaw(),
                client.player.getPitch(),
                targetYaw,
                targetPitch,
                routingYaw,
                walkability.front,
                walkability.back,
                walkability.left,
                walkability.right,
                planned
        );
        FarmHelperFabric.getWebhookService().debugTrace("macro-plan", line);
        if (activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT
                && routeState == LegacyRouteState.SWITCHING_LANE
                && runtimeTicks % 8L == 0L) {
            emitMelonSwitchDiagnostics(client, routingYaw, walkability);
        }
        lastMacroDebugTick = runtimeTicks;
        lastLoggedRouteState = routeState;
        lastLoggedDirection = directionLabel;
        lastLoggedPosition = currentPos;
    }

    private void emitMelonSwitchDiagnostics(MinecraftClient client, float routingYaw, Walkability walkability) {
        if (client == null || client.player == null) {
            return;
        }
        Vec3d pos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        String line = String.format(
                Locale.US,
                "tick=%d route=%s prev=%s yaw=%.2f targetYaw=%.2f routingYaw=%.2f pos=(%.2f,%.2f,%.2f) walk[F=%s,B=%s,L=%s,R=%s] blocks[F=%s,FD=%s,B=%s,BD=%s,L=%s,R=%s]",
                runtimeTicks,
                routeState,
                previousRouteState,
                client.player.getYaw(),
                targetYaw,
                routingYaw,
                pos.x, pos.y, pos.z,
                walkability.front,
                walkability.back,
                walkability.left,
                walkability.right,
                blockId(getRelativeBlockState(client, 0, 1, routingYaw)),
                blockId(getRelativeBlockStateAtYaw(client, 0, -1, 1, routingYaw)),
                blockId(getRelativeBlockState(client, 0, -1, routingYaw)),
                blockId(getRelativeBlockStateAtYaw(client, 0, -1, -1, routingYaw)),
                blockId(getRelativeBlockState(client, -1, 0, routingYaw)),
                blockId(getRelativeBlockState(client, 1, 0, routingYaw))
        );
        FarmHelperFabric.getWebhookService().debugTrace("melon-switch-diag", line);
    }

    private String blockId(BlockState state) {
        if (state == null) {
            return "null";
        }
        if (state.isAir()) {
            return "air";
        }
        return state.getBlock().getTranslationKey();
    }

    private String formatMovementDecision(PlayerSimulation.MovementDecision decision) {
        if (decision == null) {
            return "-";
        }
        return (decision.forward() ? "W" : "")
                + (decision.back() ? "S" : "")
                + (decision.left() ? "A" : "")
                + (decision.right() ? "D" : "")
                + (decision.sprint() ? "+SPR" : "")
                + (decision.attack() ? "+ATK" : "");
    }

    private void renderPlannedMovementPath(MinecraftClient client, PlayerSimulation.MovementDecision decision) {
        if (client.player == null) {
            return;
        }
        Vec3d[] points = buildPlannedMovementPoints(client, decision, 10);
        int color = activeType == LegacyMacroType.S_PUMPKIN_MELON_DEFAULT_PLOT ? 0xFF49E6B7 : 0xFF4BC7FF;
        for (Vec3d point : points) {
            if (point == null) {
                continue;
            }
            RenderUtils.drawTracer(point, color, 4L);
            RenderUtils.drawBlockBox(BlockPos.ofFloored(point), 0xAA2E6DFF, 4L);
        }
    }

    private String formatPlannedPathPreview(MinecraftClient client, PlayerSimulation.MovementDecision decision, int points) {
        Vec3d[] planned = buildPlannedMovementPoints(client, decision, Math.max(1, points));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < planned.length; i++) {
            Vec3d point = planned[i];
            if (point == null) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(" -> ");
            }
            sb.append(String.format(Locale.US, "(%.1f,%.1f,%.1f)", point.x, point.y, point.z));
        }
        if (sb.isEmpty()) {
            return "(none)";
        }
        return sb.toString();
    }

    private Vec3d[] buildPlannedMovementPoints(MinecraftClient client, PlayerSimulation.MovementDecision decision, int steps) {
        Vec3d[] points = new Vec3d[Math.max(1, steps)];
        if (client == null || client.player == null || decision == null) {
            return points;
        }
        double forward = (decision.forward() ? 1.0 : 0.0) + (decision.back() ? -1.0 : 0.0);
        double strafe = (decision.right() ? 1.0 : 0.0) + (decision.left() ? -1.0 : 0.0);
        if (Math.abs(forward) < 1.0e-4 && Math.abs(strafe) < 1.0e-4) {
            points[0] = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
            return points;
        }
        double yawRad = Math.toRadians(client.player.getYaw());
        Vec3d forwardVec = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
        Vec3d rightVec = new Vec3d(forwardVec.z, 0.0, -forwardVec.x);
        Vec3d move = forwardVec.multiply(forward).add(rightVec.multiply(strafe));
        if (move.lengthSquared() < 1.0e-6) {
            points[0] = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
            return points;
        }
        Vec3d normalized = move.normalize();
        double stepDistance = decision.sprint() ? 0.42 : 0.31;
        Vec3d pos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        for (int i = 0; i < points.length; i++) {
            pos = pos.add(normalized.multiply(stepDistance));
            points[i] = pos;
        }
        return points;
    }
}

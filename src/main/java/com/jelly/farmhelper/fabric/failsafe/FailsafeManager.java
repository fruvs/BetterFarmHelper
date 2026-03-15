package com.jelly.farmhelper.fabric.failsafe;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.detector.BadEffectsDetector;
import com.jelly.farmhelper.fabric.failsafe.detector.BedrockPacketDetector;
import com.jelly.farmhelper.fabric.failsafe.detector.ChatKeywordDetector;
import com.jelly.farmhelper.fabric.failsafe.detector.DetectorState;
import com.jelly.farmhelper.fabric.failsafe.detector.DisconnectDetector;
import com.jelly.farmhelper.fabric.failsafe.detector.DirtDetector;
import com.jelly.farmhelper.fabric.failsafe.detector.EnvironmentBlockDetector;
import com.jelly.farmhelper.fabric.failsafe.detector.FailsafeDetector;
import com.jelly.farmhelper.fabric.failsafe.detector.InventoryFullDetector;
import com.jelly.farmhelper.fabric.failsafe.detector.ItemChangeDetector;
import com.jelly.farmhelper.fabric.failsafe.detector.JacobContestDetector;
import com.jelly.farmhelper.fabric.failsafe.detector.KnockbackDetector;
import com.jelly.farmhelper.fabric.failsafe.detector.LowBpsDetector;
import com.jelly.farmhelper.fabric.failsafe.detector.RotationDetector;
import com.jelly.farmhelper.fabric.failsafe.detector.TeleportDetector;
import com.jelly.farmhelper.fabric.failsafe.detector.WorldChangeDetector;
import com.jelly.farmhelper.fabric.feature.module.UsageStatsFeatureModule;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShearsItem;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

public class FailsafeManager {
    private enum ReactionStage {
        NONE,
        WAIT_BEFORE_START,
        LOOK_AROUND,
        WAIT_FOR_INITIAL_RECORDING,
        SEND_MESSAGE_1,
        ROTATE_AWAY,
        LOOK_AROUND_2,
        WAIT_FOR_SECOND_RECORDING,
        SEND_MESSAGE_2,
        GO_BACK_START,
        GO_BACK_TRAVEL,
        WARP_GARDEN,
        RESTORE_ROTATION,
        FINALIZE
    }

    private enum ReactionStyle {
        PASSIVE,
        LEGACY_ITEM_SHORT,
        LEGACY_LONG
    }

    private static final String[] FAILSAFE_REACTIONS = new String[] {
            "what just happened?",
            "lag spike?",
            "yo what was that",
            "huh?",
            "that was weird"
    };

    private static final String[] FAILSAFE_CONTINUE_REACTIONS = new String[] {
            "can i keep farming?",
            "leave me alone lol",
            "let me farm",
            "hello admin???",
            "bro let me farm ok?"
    };

    private final Queue<FailsafeType> triggerQueue = new ArrayDeque<>();
    private final Map<FailsafeType, String> queuedReasons = new EnumMap<>(FailsafeType.class);
    private final Map<FailsafeType, Long> lastTriggerTick = new EnumMap<>(FailsafeType.class);
    private final List<FailsafeDetector> detectors = new ArrayList<>();
    private final DetectorState detectorState = new DetectorState();

    private Optional<FailsafeType> activeFailsafe = Optional.empty();
    private String activeReason = "";
    private long activeSinceTick = -1L;
    private boolean macroEnabledAtTrigger = false;
    private long restartMacroTick = -1L;
    private RuntimeSnapshot runtime = new RuntimeSnapshot();
    private ReactionStyle reactionStyle = ReactionStyle.PASSIVE;
    private ReactionStage reactionStage = ReactionStage.NONE;
    private long reactionNextTick = -1L;
    private double reactionStartX;
    private double reactionStartY;
    private double reactionStartZ;
    private float reactionStartYaw;
    private float reactionStartPitch;
    private long reactionReturnTimeoutTick = -1L;
    private boolean reactionSideLeft;

    private long suppressTeleportUntilTick = -1L;
    private long suppressRotationUntilTick = -1L;
    private long suppressItemUntilTick = -1L;

    private long lastWorldTimePacketMs = -1L;
    private final ArrayDeque<Long> worldTimePacketIntervalsMs = new ArrayDeque<>();

    public FailsafeManager() {
        registerDetectors();
    }

    public void trigger(FailsafeType type, String reason) {
        long nowTick = runtime.tickCount;
        long cooldownTicks = Math.max(20L, FarmHelperFabric.getConfigManager().getConfig().detectionTimeWindowMs / 50L);
        if (type == FailsafeType.DIRT) {
            cooldownTicks = Math.max(cooldownTicks, 200L);
        }
        long last = lastTriggerTick.getOrDefault(type, -cooldownTicks);
        if (nowTick - last < cooldownTicks) {
            return;
        }
        if (activeFailsafe.isPresent() && activeFailsafe.get() == type) {
            return;
        }
        if (triggerQueue.contains(type)) {
            return;
        }

        triggerQueue.add(type);
        queuedReasons.put(type, reason);
        lastTriggerTick.put(type, nowTick);
        FarmHelperFabric.LOGGER.warn("Failsafe queued: {} ({})", type, reason);
        FarmHelperFabric.getWebhookService().debugCritical("failsafe", "queued " + type + " reason=" + reason);
    }

    public void tick() {
        detectorState.packetTeleportSuppressed = runtime.tickCount < suppressTeleportUntilTick;
        detectorState.packetRotationSuppressed = runtime.tickCount < suppressRotationUntilTick;
        detectorState.packetItemSuppressed = runtime.tickCount < suppressItemUntilTick;

        runDetectors();

        long nowTick = runtime.tickCount;
        if (activeFailsafe.isEmpty() && !triggerQueue.isEmpty()) {
            activateNextFailsafe(nowTick);
        }

        if (activeFailsafe.isPresent()) {
            handleActiveFailsafe(nowTick);
        }

        handleScheduledMacroRestart(nowTick);
        detectorState.resetTransient();
    }

    public boolean hasActiveFailsafe() {
        return activeFailsafe.isPresent();
    }

    public Optional<FailsafeType> getActiveFailsafe() {
        return activeFailsafe;
    }

    public String getActiveReason() {
        return activeReason;
    }

    public void setRuntime(RuntimeSnapshot runtime) {
        this.runtime = runtime == null ? new RuntimeSnapshot() : runtime.copy();
    }

    public void onChatMessage(String message) {
        detectorState.latestChatMessage = message == null ? "" : message;
        detectorState.latestChatSeq++;
    }

    public void onPositionLookPacket(
            double teleportDistance,
            float yawDelta,
            float pitchDelta,
            double originX,
            double originY,
            double originZ,
            double targetX,
            double targetY,
            double targetZ,
            float originYaw,
            float originPitch,
            float targetYaw,
            float targetPitch
    ) {
        detectorState.packetPositionLookSeen = true;
        detectorState.packetTeleportDistance = Math.max(detectorState.packetTeleportDistance, Math.abs(teleportDistance));
        detectorState.packetTeleportOriginX = originX;
        detectorState.packetTeleportOriginY = originY;
        detectorState.packetTeleportOriginZ = originZ;
        detectorState.packetTeleportTargetX = targetX;
        detectorState.packetYawDelta = Math.max(detectorState.packetYawDelta, Math.abs(yawDelta));
        detectorState.packetPitchDelta = Math.max(detectorState.packetPitchDelta, Math.abs(pitchDelta));
        detectorState.packetTeleportTargetY = targetY;
        detectorState.packetTeleportTargetZ = targetZ;
        detectorState.packetRotationOriginYaw = originYaw;
        detectorState.packetRotationOriginPitch = originPitch;
        detectorState.packetRotationTargetYaw = targetYaw;
        detectorState.packetRotationTargetPitch = targetPitch;
    }

    public void onPlayerRotationPacket(
            float yawDelta,
            float pitchDelta,
            float originYaw,
            float originPitch,
            float targetYaw,
            float targetPitch
    ) {
        detectorState.packetRotationSeen = true;
        detectorState.packetRotationYawDelta = Math.max(detectorState.packetRotationYawDelta, Math.abs(yawDelta));
        detectorState.packetRotationPitchDelta = Math.max(detectorState.packetRotationPitchDelta, Math.abs(pitchDelta));
        detectorState.packetRotationOriginYaw = originYaw;
        detectorState.packetRotationOriginPitch = originPitch;
        detectorState.packetRotationTargetYaw = targetYaw;
        detectorState.packetRotationTargetPitch = targetPitch;
    }

    public void onPlayerVelocityPacket(double velocityY, double velocityMagnitude) {
        detectorState.packetVelocitySeen = true;
        detectorState.packetVelocityY = velocityY;
        detectorState.packetVelocityMagnitude = Math.max(0.0, velocityMagnitude);
    }

    public void onScreenHandlerSlotPacket(int slot, int selectedHotbarSlot, ItemStack stack) {
        detectorState.packetSlotUpdateSeen = true;
        detectorState.packetSlot = slot;
        detectorState.packetSlotTargetsSelectedHotbar = slot == selectedHotbarSlot || slot == (36 + selectedHotbarSlot);
        detectorState.packetSlotLooksLikeFarmTool = isFarmTool(stack);
        detectorState.packetSlotItemName = stack == null || stack.isEmpty() ? "" : stack.getName().getString();
    }

    public void onDisconnect() {
        detectorState.disconnected = true;
        lastWorldTimePacketMs = -1L;
        worldTimePacketIntervalsMs.clear();
    }

    public void onWorldChange() {
        detectorState.worldChanged = true;
        suppressPacketChecks(80L, 80L, 40L, "world change");
        worldTimePacketIntervalsMs.clear();
    }

    public void onWorldTimePacket() {
        long now = System.currentTimeMillis();
        if (lastWorldTimePacketMs > 0L) {
            long interval = Math.max(1L, now - lastWorldTimePacketMs);
            worldTimePacketIntervalsMs.addLast(interval);
            while (worldTimePacketIntervalsMs.size() > 20) {
                worldTimePacketIntervalsMs.pollFirst();
            }
        }
        lastWorldTimePacketMs = now;
    }

    public void suppressPacketChecks(long teleportTicks, long rotationTicks, long itemTicks, String reason) {
        long now = runtime.tickCount;
        if (teleportTicks > 0) {
            suppressTeleportUntilTick = Math.max(suppressTeleportUntilTick, now + teleportTicks);
        }
        if (rotationTicks > 0) {
            suppressRotationUntilTick = Math.max(suppressRotationUntilTick, now + rotationTicks);
        }
        if (itemTicks > 0) {
            suppressItemUntilTick = Math.max(suppressItemUntilTick, now + itemTicks);
        }
        if (reason != null && !reason.isBlank()) {
            FarmHelperFabric.LOGGER.debug(
                    "Failsafe packet checks suppressed [{}]: tp={} rot={} item={}",
                    reason,
                    teleportTicks,
                    rotationTicks,
                    itemTicks
            );
        }
    }

    public long getMillisSinceWorldTimePacket() {
        if (lastWorldTimePacketMs <= 0L) {
            return 0L;
        }
        return Math.max(0L, System.currentTimeMillis() - lastWorldTimePacketMs);
    }

    public float getEstimatedServerTps() {
        if (worldTimePacketIntervalsMs.isEmpty()) {
            return 20.0f;
        }
        double sum = 0.0;
        int count = 0;
        for (Long interval : worldTimePacketIntervalsMs) {
            if (interval == null || interval <= 0L) {
                continue;
            }
            sum += interval;
            count++;
        }
        if (count == 0) {
            return 20.0f;
        }
        double avgMs = sum / count;
        double tps = 1000.0 / avgMs;
        return (float) Math.max(0.0, Math.min(20.0, tps));
    }

    public boolean isNetworkLagging() {
        return getMillisSinceWorldTimePacket() > 1300L || getEstimatedServerTps() < 12.0f;
    }

    private boolean isFarmTool(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() == null) {
            return false;
        }
        return stack.getItem() instanceof HoeItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof ShearsItem;
    }

    public void clearActiveFailsafe() {
        clearActiveFailsafe(true);
    }

    public void clearActiveFailsafe(boolean notifyWebhook) {
        if (notifyWebhook) {
            activeFailsafe.ifPresent(FarmHelperFabric.getWebhookService()::onFailsafeCleared);
        }
        activeFailsafe = Optional.empty();
        activeReason = "";
        activeSinceTick = -1L;
        macroEnabledAtTrigger = false;
        reactionStyle = ReactionStyle.PASSIVE;
        reactionStage = ReactionStage.NONE;
        reactionNextTick = -1L;
        reactionStartX = 0.0;
        reactionStartY = 0.0;
        reactionStartZ = 0.0;
        reactionStartYaw = 0f;
        reactionStartPitch = 0f;
        reactionReturnTimeoutTick = -1L;
        reactionSideLeft = false;
        triggerQueue.clear();
        queuedReasons.clear();
    }

    public void onMacroStoppedByUser() {
        restartMacroTick = -1L;
        clearActiveFailsafe(false);
    }

    public boolean cancelFailsafeAndResumeMacro() {
        if (activeFailsafe.isEmpty()) {
            return false;
        }
        boolean shouldResumeMacro = macroEnabledAtTrigger && !FarmHelperFabric.getMacroController().isToggled();
        clearActiveFailsafe();
        if (shouldResumeMacro) {
            FarmHelperFabric.getMacroController().enable();
        }
        return true;
    }

    private void activateNextFailsafe(long nowTick) {
        FailsafeType type = null;
        for (FailsafeType queued : triggerQueue) {
            if (queued == null) {
                continue;
            }
            if (type == null || queued.priority() < type.priority()) {
                type = queued;
            }
        }
        if (type == null) {
            return;
        }
        triggerQueue.remove(type);

        activeFailsafe = Optional.of(type);
        activeReason = queuedReasons.getOrDefault(type, "");
        queuedReasons.remove(type);
        activeSinceTick = nowTick;
        macroEnabledAtTrigger = FarmHelperFabric.getMacroController().isToggled();
        reactionStyle = resolveReactionStyle(type);
        reactionStage = ReactionStage.WAIT_BEFORE_START;
        reactionNextTick = nowTick + initialPauseTicks(FarmHelperFabric.getConfigManager().getConfig());
        captureReactionBaseline();
        reactionSideLeft = resolveRecordingSide(type);

        FarmHelperFabric.LOGGER.warn("Failsafe triggered: {} ({})", type, activeReason);
        FarmHelperFabric.getWebhookService().onFailsafeTriggered(type, activeReason);
        FarmHelperFabric.getWebhookService().debugCritical("failsafe", "triggered " + type + " reason=" + activeReason);
        UsageStatsFeatureModule.recordFailsafeTriggered(type);

        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (config.autoAltTab && shouldAutoAltTabFor(type)) {
            FarmHelperFabric.getClientActionQueue().enqueueRequestWindowAttention(type.name(), nowTick);
        }
    }

    private ReactionStyle resolveReactionStyle(FailsafeType type) {
        return switch (type) {
            case ITEM_CHANGE, FULL_INVENTORY -> ReactionStyle.LEGACY_ITEM_SHORT;
            case BAD_EFFECTS, BEDROCK_CAGE, COBWEB, DIRT, KNOCKBACK, TELEPORT_CHECK, ROTATION_CHECK -> ReactionStyle.LEGACY_LONG;
            default -> ReactionStyle.PASSIVE;
        };
    }

    private boolean shouldAutoAltTabFor(FailsafeType type) {
        return switch (type) {
            case WORLD_CHANGE, EVACUATE, BANWAVE, DISCONNECT, JACOB, GUEST_VISIT, FULL_INVENTORY, MANUAL_TEST -> false;
            default -> true;
        };
    }

    private void captureReactionBaseline() {
        reactionStartX = runtime.posX;
        reactionStartY = runtime.posY;
        reactionStartZ = runtime.posZ;
        reactionStartYaw = runtime.yaw;
        reactionStartPitch = runtime.pitch;
        reactionReturnTimeoutTick = -1L;
    }

    private boolean resolveRecordingSide(FailsafeType type) {
        return switch (type) {
            case DIRT -> runtime.dirtOnLeft || (!runtime.dirtOnRight && ThreadLocalRandom.current().nextBoolean());
            case BEDROCK_CAGE -> runtime.bedrockOnLeft || (!runtime.bedrockOnRight && ThreadLocalRandom.current().nextBoolean());
            default -> ThreadLocalRandom.current().nextBoolean();
        };
    }

    private void handleActiveFailsafe(long nowTick) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (config.failsafeActionDisableOnly) {
            if (macroEnabledAtTrigger && FarmHelperFabric.getMacroController().isToggled()) {
                FarmHelperFabric.getMacroController().disable();
            }
            if (reactionNextTick > 0 && nowTick < reactionNextTick) {
                return;
            }
            finalizeFailsafe(nowTick, config);
            return;
        }

        switch (reactionStyle) {
            case LEGACY_LONG -> handleLegacyLongReaction(nowTick, config);
            case LEGACY_ITEM_SHORT -> handleLegacyItemReaction(nowTick, config);
            case PASSIVE -> handlePassiveReaction(nowTick, config);
        }
    }

    private void handleLegacyLongReaction(long nowTick, FarmHelperConfig config) {
        FailsafeType type = activeFailsafe.orElse(null);
        if (type == null) {
            finalizeFailsafe(nowTick, config);
            return;
        }
        if (closeScreenIfNeeded(nowTick)) {
            return;
        }
        if (shouldCancelLegacyReaction(type)) {
            finalizeFailsafe(nowTick, config);
            return;
        }

        switch (reactionStage) {
            case WAIT_BEFORE_START -> {
                if (nowTick < reactionNextTick) {
                    return;
                }
                reactionStage = ReactionStage.LOOK_AROUND;
                reactionNextTick = nowTick + randomLegacyTicks(500, 500);
            }
            case LOOK_AROUND -> {
                if (nowTick < reactionNextTick) {
                    return;
                }
                queueMovementRecording(resolveInitialRecordingPattern(type), nowTick);
                reactionStage = ReactionStage.WAIT_FOR_INITIAL_RECORDING;
                reactionNextTick = nowTick + randomLegacyTicks(2000, 3000);
            }
            case WAIT_FOR_INITIAL_RECORDING -> {
                if (runtime.movementRecordingPlaying || nowTick < reactionNextTick) {
                    return;
                }
                reactionStage = ReactionStage.SEND_MESSAGE_1;
                reactionNextTick = nowTick;
            }
            case SEND_MESSAGE_1 -> {
                if (nowTick < reactionNextTick) {
                    return;
                }
                if (config.sendFailsafeChatMessage) {
                    sendFailsafeCommand(randomMessage(FAILSAFE_REACTIONS), nowTick);
                    reactionNextTick = nowTick + randomLegacyTicks(500, 1000);
                } else {
                    reactionNextTick = nowTick + randomLegacyTicks(300, 600);
                }
                reactionStage = ReactionStage.ROTATE_AWAY;
            }
            case ROTATE_AWAY -> {
                if (nowTick < reactionNextTick) {
                    return;
                }
                queueHumanLookRotation(nowTick);
                reactionStage = ReactionStage.LOOK_AROUND_2;
                reactionNextTick = nowTick + 14L;
            }
            case LOOK_AROUND_2 -> {
                if (nowTick < reactionNextTick) {
                    return;
                }
                queueMovementRecording(resolveSecondaryRecordingPattern(type), nowTick);
                reactionStage = ReactionStage.WAIT_FOR_SECOND_RECORDING;
                reactionNextTick = nowTick + randomLegacyTicks(2000, 3000);
            }
            case WAIT_FOR_SECOND_RECORDING -> {
                if (runtime.movementRecordingPlaying || nowTick < reactionNextTick) {
                    return;
                }
                reactionStage = ReactionStage.SEND_MESSAGE_2;
                reactionNextTick = nowTick;
            }
            case SEND_MESSAGE_2 -> {
                if (nowTick < reactionNextTick) {
                    return;
                }
                if (config.sendFailsafeChatMessage && config.customFailsafeSendSecondMessage) {
                    sendFailsafeCommand(randomMessage(FAILSAFE_CONTINUE_REACTIONS), nowTick);
                    reactionNextTick = nowTick + randomLegacyTicks(500, 1000);
                } else {
                    reactionNextTick = nowTick + randomLegacyTicks(300, 600);
                }
                reactionStage = ReactionStage.GO_BACK_START;
            }
            case GO_BACK_START -> {
                if (nowTick < reactionNextTick) {
                    return;
                }
                double distanceSq = distanceSqToReactionStart();
                if (distanceSq < 2.0) {
                    reactionStage = ReactionStage.RESTORE_ROTATION;
                    reactionNextTick = nowTick + 1L;
                    return;
                }
                if (distanceSq < 100.0 && runtime.inWorld) {
                    FarmHelperFabric.getClientActionQueue().enqueueMoveToPos(
                            reactionStartX,
                            reactionStartY,
                            reactionStartZ,
                            1.25,
                            120L,
                            nowTick
                    );
                    reactionReturnTimeoutTick = nowTick + 120L;
                    reactionStage = ReactionStage.GO_BACK_TRAVEL;
                    reactionNextTick = nowTick + 4L;
                    return;
                }
                reactionStage = ReactionStage.WARP_GARDEN;
                reactionNextTick = nowTick;
            }
            case GO_BACK_TRAVEL -> {
                double distanceSq = distanceSqToReactionStart();
                if (distanceSq < 2.0) {
                    reactionStage = ReactionStage.RESTORE_ROTATION;
                    reactionNextTick = nowTick + 1L;
                    return;
                }
                if (reactionReturnTimeoutTick > 0L && nowTick >= reactionReturnTimeoutTick) {
                    reactionStage = distanceSq > 100.0 ? ReactionStage.WARP_GARDEN : ReactionStage.RESTORE_ROTATION;
                    reactionNextTick = nowTick + (reactionStage == ReactionStage.RESTORE_ROTATION ? randomLegacyTicks(500, 1000) : 0L);
                }
            }
            case WARP_GARDEN -> {
                if (nowTick < reactionNextTick) {
                    return;
                }
                FarmHelperFabric.getClientActionQueue().enqueueCommand("/warp garden", nowTick);
                suppressPacketChecks(160L, 120L, 60L, "legacy failsafe reaction warp");
                reactionStage = ReactionStage.RESTORE_ROTATION;
                reactionNextTick = nowTick + 60L;
            }
            case RESTORE_ROTATION -> {
                if (nowTick < reactionNextTick) {
                    return;
                }
                if (runtime.inWorld) {
                    queueHumanLookRotation(nowTick);
                    reactionNextTick = nowTick + 14L;
                } else {
                    reactionNextTick = nowTick;
                }
                reactionStage = ReactionStage.FINALIZE;
            }
            case FINALIZE -> {
                if (nowTick < reactionNextTick) {
                    return;
                }
                finalizeFailsafe(nowTick, config);
            }
            case NONE -> {
                reactionStage = ReactionStage.WAIT_BEFORE_START;
                reactionNextTick = nowTick + initialPauseTicks(config);
            }
        }
    }

    private void handleLegacyItemReaction(long nowTick, FarmHelperConfig config) {
        if (closeScreenIfNeeded(nowTick)) {
            return;
        }
        switch (reactionStage) {
            case WAIT_BEFORE_START -> {
                if (nowTick < reactionNextTick) {
                    return;
                }
                reactionStage = ReactionStage.LOOK_AROUND;
                reactionNextTick = nowTick + randomLegacyTicks(500, 500);
            }
            case LOOK_AROUND -> {
                if (nowTick < reactionNextTick) {
                    return;
                }
                queueMovementRecording("ITEM_CHANGE_", nowTick);
                reactionStage = ReactionStage.WAIT_FOR_INITIAL_RECORDING;
                reactionNextTick = nowTick + 2L;
            }
            case WAIT_FOR_INITIAL_RECORDING -> {
                if (runtime.movementRecordingPlaying || nowTick < reactionNextTick) {
                    return;
                }
                reactionStage = ReactionStage.FINALIZE;
                reactionNextTick = nowTick + randomLegacyTicks(500, 1000);
            }
            case FINALIZE -> {
                if (nowTick < reactionNextTick) {
                    return;
                }
                finalizeFailsafe(nowTick, config);
            }
            case NONE -> {
                reactionStage = ReactionStage.WAIT_BEFORE_START;
                reactionNextTick = nowTick + initialPauseTicks(config);
            }
            default -> {
                reactionStage = ReactionStage.FINALIZE;
                reactionNextTick = nowTick;
            }
        }
    }

    private void handlePassiveReaction(long nowTick, FarmHelperConfig config) {
        switch (reactionStage) {
            case WAIT_BEFORE_START -> {
                if (nowTick < reactionNextTick) {
                    return;
                }
                if (!config.enableCustomFailsafeReactions) {
                    finalizeFailsafe(nowTick, config);
                    return;
                }
                reactionStage = ReactionStage.SEND_MESSAGE_1;
                reactionNextTick = nowTick;
            }
            case SEND_MESSAGE_1 -> {
                if (nowTick < reactionNextTick) {
                    return;
                }
                if (config.sendFailsafeChatMessage) {
                    sendFailsafeCommand(randomMessage(FAILSAFE_REACTIONS), nowTick);
                }
                reactionStage = ReactionStage.SEND_MESSAGE_2;
                reactionNextTick = nowTick + randomReactionTicks(config);
            }
            case SEND_MESSAGE_2 -> {
                if (nowTick < reactionNextTick) {
                    return;
                }
                long nextTick = nowTick;
                if (config.sendFailsafeChatMessage && config.customFailsafeSendSecondMessage) {
                    sendFailsafeCommand(randomMessage(FAILSAFE_CONTINUE_REACTIONS), nowTick);
                    nextTick = nowTick + randomReactionTicks(config);
                }
                if (config.customFailsafeWarpToGarden && shouldPassiveReactionWarp()) {
                    reactionStage = ReactionStage.WARP_GARDEN;
                    reactionNextTick = nextTick;
                    return;
                }
                reactionStage = ReactionStage.FINALIZE;
                reactionNextTick = nextTick;
            }
            case WARP_GARDEN -> {
                if (nowTick < reactionNextTick) {
                    return;
                }
                FarmHelperFabric.getClientActionQueue().enqueueCommand("/warp garden", nowTick);
                suppressPacketChecks(160L, 120L, 60L, "passive failsafe reaction warp");
                reactionStage = ReactionStage.FINALIZE;
                reactionNextTick = nowTick + 40L;
            }
            case FINALIZE -> {
                if (nowTick < reactionNextTick) {
                    return;
                }
                finalizeFailsafe(nowTick, config);
            }
            case NONE -> {
                reactionStage = ReactionStage.WAIT_BEFORE_START;
                reactionNextTick = nowTick + initialPauseTicks(config);
            }
            default -> {
                reactionStage = ReactionStage.FINALIZE;
                reactionNextTick = nowTick;
            }
        }
    }

    private boolean shouldPassiveReactionWarp() {
        return activeFailsafe.filter(type -> switch (type) {
            case WORLD_CHANGE, EVACUATE, DISCONNECT, GUEST_VISIT -> false;
            default -> true;
        }).isPresent();
    }

    private boolean closeScreenIfNeeded(long nowTick) {
        if (!runtime.screenOpen) {
            return false;
        }
        FarmHelperFabric.getClientActionQueue().enqueueCloseCurrentScreen(nowTick);
        reactionNextTick = Math.max(reactionNextTick, nowTick + 4L);
        return true;
    }

    private boolean shouldCancelLegacyReaction(FailsafeType type) {
        return switch (type) {
            case BAD_EFFECTS -> !runtime.hasBadEffects;
            case COBWEB -> !runtime.nearCobweb;
            case DIRT -> !runtime.nearDirt;
            case BEDROCK_CAGE -> !runtime.nearBedrock && runtime.posY < 90.0;
            default -> false;
        };
    }

    private String resolveInitialRecordingPattern(FailsafeType type) {
        return switch (type) {
            case ITEM_CHANGE, FULL_INVENTORY -> "ITEM_CHANGE_";
            case BEDROCK_CAGE -> reactionSideLeft ? "BEDROCK_CHECK_Left_Start_" : "BEDROCK_CHECK_Right_Start_";
            case DIRT -> reactionSideLeft ? "DIRT_CHECK_Left_Start_" : "DIRT_CHECK_Right_Start_";
            case TELEPORT_CHECK, KNOCKBACK -> "TELEPORT_CHECK_Start_";
            default -> "ROTATION_CHECK_Start_";
        };
    }

    private String resolveSecondaryRecordingPattern(FailsafeType type) {
        return switch (type) {
            case BEDROCK_CAGE -> resolveConditionalMovementPattern("BEDROCK_CHECK_");
            case DIRT -> {
                if (runtime.allowFlying && runtime.aboveHeadClear) {
                    yield reactionSideLeft ? "DIRT_CHECK_Left_Fly_" : "DIRT_CHECK_Right_Fly_";
                }
                yield reactionSideLeft ? "DIRT_CHECK_Left_Start_" : "DIRT_CHECK_Right_Start_";
            }
            case TELEPORT_CHECK, KNOCKBACK -> resolveConditionalMovementPattern("TELEPORT_CHECK_");
            default -> "ROTATION_CHECK_Continue_";
        };
    }

    private String resolveConditionalMovementPattern(String prefix) {
        if (runtime.jumpBoostActive) {
            return prefix + "JumpBoost_";
        }
        if (runtime.allowFlying && runtime.aboveHeadClear) {
            return prefix + "Fly_";
        }
        return prefix + "OnGround_";
    }

    private void queueMovementRecording(String pattern, long nowTick) {
        if (pattern == null || pattern.isBlank()) {
            return;
        }
        FarmHelperFabric.getClientActionQueue().enqueuePlayMovementRecording(pattern, nowTick);
    }

    private void queueHumanLookRotation(long nowTick) {
        float yaw = reactionStartYaw + (float) ThreadLocalRandom.current().nextDouble(-15.0, 15.0);
        float pitch = (float) ThreadLocalRandom.current().nextDouble(30.0, 60.0);
        FarmHelperFabric.getClientActionQueue().enqueueRotateTo(yaw, pitch, 12L, nowTick);
    }

    private void sendFailsafeCommand(String message, long nowTick) {
        if (message == null || message.isBlank()) {
            return;
        }
        FarmHelperFabric.getClientActionQueue().enqueueCommand("/ac " + message.trim(), nowTick);
    }

    private String randomMessage(String[] messages) {
        if (messages == null || messages.length == 0) {
            return "";
        }
        return messages[ThreadLocalRandom.current().nextInt(messages.length)];
    }

    private long initialPauseTicks(FarmHelperConfig config) {
        long base = Math.max(20L, config.failsafeStopDelayMs / 50L);
        return base + ThreadLocalRandom.current().nextLong(4L, 11L);
    }

    private long randomLegacyTicks(int minMs, int randomMs) {
        int upper = Math.max(0, randomMs);
        int delayMs = minMs + (upper == 0 ? 0 : ThreadLocalRandom.current().nextInt(upper + 1));
        return Math.max(1L, delayMs / 50L);
    }

    private double distanceSqToReactionStart() {
        if (!runtime.inWorld) {
            return Double.MAX_VALUE;
        }
        double dx = runtime.posX - reactionStartX;
        double dy = runtime.posY - reactionStartY;
        double dz = runtime.posZ - reactionStartZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private void finalizeFailsafe(long nowTick, FarmHelperConfig config) {
        long restartDelayTicks = Math.max(0, config.restartAfterFailsafeDelayMinutes) * 60L * 20L;
        boolean deferRestart = config.restartAfterFailsafe
                && macroEnabledAtTrigger
                && (restartDelayTicks > 0L || config.alwaysTeleportToGarden);
        if (deferRestart && FarmHelperFabric.getMacroController().isToggled()) {
            FarmHelperFabric.getMacroController().disable();
        }

        boolean shouldRestart = config.restartAfterFailsafe
                && macroEnabledAtTrigger
                && !FarmHelperFabric.getMacroController().isToggled();
        if (shouldRestart) {
            restartMacroTick = nowTick + restartDelayTicks;
            if (config.alwaysTeleportToGarden) {
                FarmHelperFabric.getClientActionQueue().enqueueCommand("/warp garden", nowTick);
                restartMacroTick += 40L;
            }
        }
        clearActiveFailsafe();
    }

    private long randomReactionTicks(FarmHelperConfig config) {
        int min = Math.max(200, config.customFailsafeReactionMinMs);
        int max = Math.max(min, config.customFailsafeReactionMaxMs);
        int delay = ThreadLocalRandom.current().nextInt(min, max + 1);
        return Math.max(4L, delay / 50L);
    }

    private void handleScheduledMacroRestart(long nowTick) {
        if (restartMacroTick <= 0 || nowTick < restartMacroTick) {
            return;
        }
        restartMacroTick = -1L;

        if (!FarmHelperFabric.getMacroController().isToggled()) {
            FarmHelperFabric.LOGGER.info("Restarting macro after failsafe cooldown");
            FarmHelperFabric.getMacroController().enable();
        }
    }

    private void runDetectors() {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.enableFailsafes
                || !FarmHelperFabric.getMacroController().isToggled()
                || !runtime.macroToggled
                || activeFailsafe.isPresent()) {
            return;
        }

        for (FailsafeDetector detector : detectors) {
            Optional<String> reason = detector.detect(runtime, config, detectorState);
            reason.ifPresent(r -> trigger(detector.type(), r));
        }
    }

    private void registerDetectors() {
        detectors.add(new BadEffectsDetector());
        detectors.add(new BedrockPacketDetector());
        detectors.add(new ChatKeywordDetector(FailsafeType.BANWAVE, "Banwave keyword", "ban wave", "banned in the last"));
        detectors.add(new EnvironmentBlockDetector(FailsafeType.COBWEB, "Cobweb", s -> s.nearCobweb));
        detectors.add(new DirtDetector());
        detectors.add(new DisconnectDetector());
        detectors.add(new ChatKeywordDetector(
                FailsafeType.EVACUATE,
                "Evacuate keyword",
                "server reboot",
                "server update",
                "evacuate",
                "you will be evacuated",
                "restart in"
        ));
        detectors.add(new InventoryFullDetector());
        detectors.add(new ChatKeywordDetector(
                FailsafeType.GUEST_VISIT,
                "Guest visit keyword",
                "has visited your island",
                "is visiting your island",
                "entered your garden",
                "is visiting your garden",
                "is in your garden"
        ));
        detectors.add(new ItemChangeDetector());
        detectors.add(new JacobContestDetector());
        detectors.add(new KnockbackDetector());
        detectors.add(new LowBpsDetector());
        detectors.add(new RotationDetector());
        detectors.add(new TeleportDetector());
        detectors.add(new WorldChangeDetector());
    }
}

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
        HOLD,
        CHAT_SENT,
        SECOND_CHAT_SENT,
        WARPED
    }

    private static final String[] FAILSAFE_REACTIONS = new String[]{
            "what just happened?",
            "lag spike?",
            "yo what was that",
            "huh?",
            "that was weird"
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
    private ReactionStage reactionStage = ReactionStage.NONE;
    private long reactionNextTick = -1L;

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
            FarmHelperFabric.LOGGER.debug("Failsafe packet checks suppressed [{}]: tp={} rot={} item={}",
                    reason, teleportTicks, rotationTicks, itemTicks);
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
        reactionStage = ReactionStage.NONE;
        reactionNextTick = -1L;
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
        FailsafeType type = triggerQueue.poll();
        if (type == null) {
            return;
        }

        activeFailsafe = Optional.of(type);
        activeReason = queuedReasons.getOrDefault(type, "");
        queuedReasons.remove(type);
        activeSinceTick = nowTick;
        macroEnabledAtTrigger = FarmHelperFabric.getMacroController().isToggled();
        reactionStage = ReactionStage.HOLD;
        reactionNextTick = nowTick;
        FarmHelperFabric.LOGGER.warn("Failsafe triggered: {} ({})", type, activeReason);
        FarmHelperFabric.getWebhookService().onFailsafeTriggered(type, activeReason);
        FarmHelperFabric.getWebhookService().debugCritical("failsafe", "triggered " + type + " reason=" + activeReason);
        UsageStatsFeatureModule.recordFailsafeTriggered(type);

        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (config.autoAltTab && shouldAutoAltTabFor(type)) {
            FarmHelperFabric.getClientActionQueue().enqueueRequestWindowAttention(type.name(), nowTick);
        }
        if (config.sendFailsafeChatMessage) {
            String reaction = FAILSAFE_REACTIONS[ThreadLocalRandom.current().nextInt(FAILSAFE_REACTIONS.length)];
            FarmHelperFabric.getClientActionQueue().enqueueMessage(reaction, nowTick);
        }
        if (config.failsafeActionDisableOnly && macroEnabledAtTrigger) {
            FarmHelperFabric.getMacroController().disable();
        }
    }

    private boolean shouldAutoAltTabFor(FailsafeType type) {
        return switch (type) {
            case WORLD_CHANGE, EVACUATE, BANWAVE, DISCONNECT, JACOB, GUEST_VISIT, FULL_INVENTORY, MANUAL_TEST -> false;
            default -> true;
        };
    }

    private void handleActiveFailsafe(long nowTick) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        long holdTicks = Math.max(20L, config.failsafeStopDelayMs / 50L);
        if (reactionStage == ReactionStage.HOLD) {
            if (activeSinceTick > 0 && nowTick - activeSinceTick < holdTicks) {
                return;
            }
            if (!config.enableCustomFailsafeReactions) {
                finalizeFailsafe(nowTick, config);
                return;
            }
            String reaction = FAILSAFE_REACTIONS[ThreadLocalRandom.current().nextInt(FAILSAFE_REACTIONS.length)];
            FarmHelperFabric.getClientActionQueue().enqueueMessage(reaction, nowTick);
            reactionStage = ReactionStage.CHAT_SENT;
            reactionNextTick = nowTick + randomReactionTicks(config);
            if (macroEnabledAtTrigger && FarmHelperFabric.getMacroController().isToggled()) {
                FarmHelperFabric.getMacroController().disable();
            }
            return;
        }
        if (reactionNextTick > 0 && nowTick < reactionNextTick) {
            return;
        }
        if (reactionStage == ReactionStage.CHAT_SENT) {
            if (config.customFailsafeSendSecondMessage) {
                String reaction = FAILSAFE_REACTIONS[ThreadLocalRandom.current().nextInt(FAILSAFE_REACTIONS.length)];
                FarmHelperFabric.getClientActionQueue().enqueueMessage(reaction, nowTick);
                reactionStage = ReactionStage.SECOND_CHAT_SENT;
                reactionNextTick = nowTick + randomReactionTicks(config);
                return;
            }
            reactionStage = ReactionStage.SECOND_CHAT_SENT;
        }
        if (reactionStage == ReactionStage.SECOND_CHAT_SENT && config.customFailsafeWarpToGarden) {
            FarmHelperFabric.getClientActionQueue().enqueueCommand("/warp garden", nowTick);
            suppressPacketChecks(160L, 120L, 60L, "custom failsafe reaction warp");
            reactionStage = ReactionStage.WARPED;
            reactionNextTick = nowTick + 40L;
            return;
        }
        finalizeFailsafe(nowTick, config);
    }

    private void finalizeFailsafe(long nowTick, FarmHelperConfig config) {
        boolean shouldRestart = config.restartAfterFailsafe
                && macroEnabledAtTrigger
                && !FarmHelperFabric.getMacroController().isToggled();
        if (shouldRestart) {
            long restartDelayTicks = Math.max(0, config.restartAfterFailsafeDelayMinutes) * 60L * 20L;
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
        detectors.add(new EnvironmentBlockDetector(FailsafeType.BEDROCK_CAGE, "Bedrock cage", s -> s.nearBedrock));
        detectors.add(new EnvironmentBlockDetector(FailsafeType.COBWEB, "Cobweb", s -> s.nearCobweb));
        detectors.add(new DirtDetector());
        detectors.add(new DisconnectDetector());
        detectors.add(new ChatKeywordDetector(FailsafeType.EVACUATE, "Evacuate keyword",
                "server reboot", "server update", "evacuate", "you will be evacuated", "restart in"));
        detectors.add(new InventoryFullDetector());
        detectors.add(new ChatKeywordDetector(FailsafeType.GUEST_VISIT, "Guest visit keyword",
                "has visited your island",
                "is visiting your island",
                "entered your garden",
                "is visiting your garden",
                "is in your garden"));
        detectors.add(new ItemChangeDetector());
        detectors.add(new JacobContestDetector());
        detectors.add(new KnockbackDetector());
        detectors.add(new LowBpsDetector());
        detectors.add(new RotationDetector());
        detectors.add(new TeleportDetector());
        detectors.add(new WorldChangeDetector());
    }
}

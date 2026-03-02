package com.jelly.farmhelper.fabric.automation;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.FarmHelperFabricClient;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.runtime.ClientActionQueue;
import com.jelly.farmhelper.fabric.state.ClientModeController;
import com.jelly.farmhelper.fabric.util.InventoryUtils;
import com.jelly.farmhelper.fabric.util.KeyBindUtils;
import com.jelly.farmhelper.fabric.util.PlotUtils;
import com.jelly.farmhelper.fabric.util.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;

public class ClientAutomationExecutor {
    private static final double DEFAULT_STOP_DISTANCE = 2.0;
    private static final long DEFAULT_TIMEOUT_TICKS = 200L;
    private static final int DEFAULT_RETRIES = 3;
    private static final float YAW_STEP = 12f;
    private static final float PITCH_STEP = 8f;
    private static final float PEST_YAW_STEP_MIN = 3.5f;
    private static final float PEST_YAW_STEP_MAX = 8.5f;
    private static final float PEST_PITCH_STEP_MIN = 2.0f;
    private static final float PEST_PITCH_STEP_MAX = 5.0f;
    private static final float PEST_AIM_LAG_ALPHA = 0.38f;
    private static final double PEST_AIM_JITTER = 0.10;
    private static final float PEST_DEADZONE_YAW_FALLBACK = 4.5f;
    private static final float PEST_DEADZONE_PITCH_FALLBACK = 3.0f;
    private static final long RETRY_INTERVAL_TICKS = 12L;
    private static final long DEBUG_THINK_INTERVAL_TICKS = 8L;
    private static final Set<String> PEST_QUERY_TOKENS = Set.of(
            "beetle", "cricket", "earthworm", "fly", "locust", "mite",
            "mosquito", "moth", "rat", "slug", "praying mantis", "firefly", "dragonfly", "pest", "pests"
    );
    private static final Set<String> NON_PEST_TARGET_KEYWORDS = Set.of(
            "pesthunter", "philip", "visitor", "npc", "jacob"
    );
    private static final int PEST_TRACER_COLOR = 0xFF3FC4FF;
    private static final int PEST_BOX_COLOR = 0xFF56D8FF;
    private static final int PEST_TEXT_COLOR = 0xFFE8F9FF;
    private static final Vec3d[] PEST_SEARCH_OFFSETS = new Vec3d[] {
            new Vec3d(0, 0, 0),
            new Vec3d(18, 0, 0),
            new Vec3d(-18, 0, 0),
            new Vec3d(0, 0, 18),
            new Vec3d(0, 0, -18),
            new Vec3d(24, 0, 24),
            new Vec3d(-24, 0, 24),
            new Vec3d(24, 0, -24),
            new Vec3d(-24, 0, -24)
    };

    private final PathfinderService pathfinderService = new PathfinderService();
    private final GuiDecisionEngine guiDecisionEngine = new GuiDecisionEngine();
    private final PestEntityHeuristics pestHeuristics = new PestEntityHeuristics();
    private final ClientModeController modeController = new ClientModeController();
    private final MovementRecordingPlayer movementRecordingPlayer = new MovementRecordingPlayer();

    private ActiveAction activeAction;
    private NavigationState navigationState;
    private boolean wasControllingMovement;

    public boolean isBusy() {
        return activeAction != null;
    }

    public void cancelAll(MinecraftClient client, String reason) {
        if (activeAction != null) {
            FarmHelperFabric.getWebhookService().debugTrace(
                    "automation",
                    "cancel active action type=" + activeAction.action.type() + " reason=" + reason
            );
        }
        movementRecordingPlayer.stop(client);
        clearActive(client);
        wasControllingMovement = false;
    }

    public void tick(MinecraftClient client, long nowTick) {
        movementRecordingPlayer.tick(client);
        if (activeAction == null) {
            if (!movementRecordingPlayer.isPlaying() && wasControllingMovement && !isMacroDrivingMovement()) {
                stopMovement(client);
            }
            wasControllingMovement = movementRecordingPlayer.isPlaying();
            navigationState = null;
            return;
        }
        wasControllingMovement = true;
        if (client.player == null || client.world == null || client.interactionManager == null) {
            clearActive(client);
            return;
        }
        if (nowTick >= activeAction.timeoutTick) {
            clearActive(client);
            return;
        }
        if (shouldSuppressRotationForAction(activeAction.action.type())) {
            FarmHelperFabric.getFailsafeManager().suppressPacketChecks(0L, 4L, 0L, "automation active");
        }

        boolean completed = switch (activeAction.action.type()) {
            case MOVE_TO_POS -> tickMoveToPos(client, activeAction, nowTick);
            case MOVE_TO_ENTITY -> tickMoveToEntity(client, activeAction, nowTick);
            case FLY_TO_ENTITY -> tickFlyToEntity(client, activeAction, nowTick);
            case INTERACT_NEAREST_ENTITY -> tickEntityInteraction(client, activeAction, nowTick, false);
            case ATTACK_NEAREST_ENTITY -> tickEntityInteraction(client, activeAction, nowTick, true);
            case WAIT_FOR_SCREEN -> tickWaitForScreen(client, activeAction, nowTick);
            case CLICK_SLOT_MATCHING -> tickClickSlotMatching(client, activeAction, nowTick);
            case MINE_NEAREST_BLOCK -> tickMineNearestBlock(client, activeAction, nowTick);
            default -> true;
        };

        if (completed) {
            FarmHelperFabric.getWebhookService().debugTrace(
                    "automation",
                    "completed action type=" + activeAction.action.type() + " retries=" + activeAction.retries
            );
            clearActive(client);
        }
    }

    public boolean submit(ClientActionQueue.Action action, MinecraftClient client, long nowTick) {
        if (requiresPlayerContext(action.type()) && (client.player == null || client.interactionManager == null)) {
            return false;
        }
        if (isBusy() && requiresActiveProcessing(action.type())) {
            return false;
        }

        FarmHelperFabric.getWebhookService().debugTrace(
                "action-submit",
                "type=" + action.type() + " payload=" + action.payload()
        );

        switch (action.type()) {
            case CHAT_COMMAND -> executeChatCommand(client, action.payload());
            case CHAT_MESSAGE -> executeChatMessage(client, action.payload());
            case CLOSE_CURRENT_SCREEN -> {
                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                }
            }
            case USE_HELD_ITEM -> client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            case SELECT_HOTBAR_ITEM -> executeSelectHotbarItem(client, action.payload());
            case SET_PERFORMANCE_MODE -> applyPerformanceMode(client, action.payload());
            case SET_PIP_MODE -> applyPipMode(client, action.payload());
            case SET_MOUSE_UNGRAB -> applyMouseUngrab(client, action.payload());
            case SET_FREELOOK_MODE -> applyFreelookMode(client, action.payload());
            case SET_USE_KEY -> applyUseKeyHold(client, Boolean.parseBoolean(action.payload()));
            case TAP_ATTACK_KEY -> KeyBindUtils.leftClick(client);
            case REQUEST_WINDOW_ATTENTION -> requestWindowAttention(action.payload());
            case PLAY_MOVEMENT_RECORDING -> playMovementRecording(client, action.payload());
            case STOP_MOVEMENT_RECORDING -> movementRecordingPlayer.stop(client);
            case CLICK_SLOT_MATCHING, MOVE_TO_POS, MOVE_TO_ENTITY, FLY_TO_ENTITY, INTERACT_NEAREST_ENTITY, ATTACK_NEAREST_ENTITY, WAIT_FOR_SCREEN, MINE_NEAREST_BLOCK -> {
                long timeout = parseTimeoutTicks(action.type(), action.payload(), DEFAULT_TIMEOUT_TICKS);
                int maxRetries = parseRetries(action.type(), action.payload(), DEFAULT_RETRIES);
                activeAction = new ActiveAction(action, nowTick, nowTick + timeout, maxRetries);
                navigationState = null;
            }
        }
        return true;
    }

    private void executeChatCommand(MinecraftClient client, String payload) {
        String command = payload == null ? "" : payload.trim();
        if (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (!command.isEmpty()) {
            client.getNetworkHandler().sendChatCommand(command);
            if (isTeleportLikeCommand(command)) {
                FarmHelperFabric.getFailsafeManager().suppressPacketChecks(160L, 120L, 60L, "intentional teleport command");
            } else if (command.startsWith("setmaxspeed")) {
                FarmHelperFabric.getFailsafeManager().suppressPacketChecks(0L, 40L, 20L, "setmaxspeed command");
            }
        }
    }

    private void executeChatMessage(MinecraftClient client, String payload) {
        String message = payload == null ? "" : payload.trim();
        if (!message.isEmpty()) {
            client.getNetworkHandler().sendChatMessage(message);
        }
    }

    private void executeSelectHotbarItem(MinecraftClient client, String payload) {
        if (client.player == null) {
            return;
        }
        String query = payload == null ? "" : payload.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return;
        }
        int slot = findHotbarSlot(client, query);
        if (slot >= 0) {
            client.player.getInventory().setSelectedSlot(slot);
        }
    }

    private void applyPerformanceMode(MinecraftClient client, String payload) {
        List<String> parts = split(payload);
        boolean enabled = !parts.isEmpty() && Boolean.parseBoolean(parts.getFirst());
        int maxFps = parts.size() >= 2 ? (int) parseDouble(parts, 1, 30) : 30;
        int viewDistance = parts.size() >= 3 ? (int) parseDouble(parts, 2, 2) : 2;
        modeController.setPerformanceMode(client, enabled, maxFps, viewDistance);
    }

    private void applyPipMode(MinecraftClient client, String payload) {
        boolean enabled = Boolean.parseBoolean(payload == null ? "false" : payload.trim());
        modeController.setPipMode(client, enabled);
    }

    private void applyMouseUngrab(MinecraftClient client, String payload) {
        boolean enabled = Boolean.parseBoolean(payload == null ? "false" : payload.trim());
        modeController.setMouseUngrab(client, enabled);
    }

    private void applyFreelookMode(MinecraftClient client, String payload) {
        boolean enabled = Boolean.parseBoolean(payload == null ? "false" : payload.trim());
        modeController.setFreelook(client, enabled);
    }

    private void applyUseKeyHold(MinecraftClient client, boolean pressed) {
        if (client == null) {
            return;
        }
        KeyBindUtils.holdUse(client, pressed);
    }

    private void playMovementRecording(MinecraftClient client, String payload) {
        String pattern = payload == null ? "" : payload.trim();
        if (pattern.isEmpty()) {
            return;
        }
        movementRecordingPlayer.playRandomRecording(pattern, client);
    }

    private void requestWindowAttention(String payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        long handle = client.getWindow().getHandle();
        try {
            GLFW.glfwRequestWindowAttention(handle);
        } catch (Throwable ignored) {
        }
        if (!isWindowFocused(handle)) {
            try {
                GLFW.glfwFocusWindow(handle);
            } catch (Throwable ignored) {
            }
        }
        if (!isWindowFocused(handle)) {
            altTabFallback();
        }
        FarmHelperFabric.getWebhookService().debugTrace(
                "failsafe",
                "requested window attention reason=" + (payload == null ? "failsafe" : payload)
        );
    }

    private boolean isWindowFocused(long handle) {
        try {
            return GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void altTabFallback() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            Robot robot = new Robot();
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            int modifier = osName.contains("mac") ? KeyEvent.VK_META : KeyEvent.VK_ALT;
            robot.keyPress(modifier);
            robot.delay(35);
            robot.keyPress(KeyEvent.VK_TAB);
            robot.delay(35);
            robot.keyRelease(KeyEvent.VK_TAB);
            robot.delay(35);
            robot.keyRelease(modifier);
        } catch (AWTException ignored) {
        }
    }

    private boolean tickMoveToPos(MinecraftClient client, ActiveAction action, long nowTick) {
        List<String> parts = split(action.action.payload());
        if (parts.size() < 3) {
            return true;
        }
        double x = parseDouble(parts, 0, client.player.getX());
        double y = parseDouble(parts, 1, client.player.getY());
        double z = parseDouble(parts, 2, client.player.getZ());
        double tolerance = parts.size() >= 4 ? parseDouble(parts, 3, DEFAULT_STOP_DISTANCE) : DEFAULT_STOP_DISTANCE;
        return navigateTowards(client, action, new Vec3d(x, y, z), tolerance, nowTick);
    }

    private boolean tickMoveToEntity(MinecraftClient client, ActiveAction action, long nowTick) {
        List<String> parts = split(action.action.payload());
        if (parts.isEmpty()) {
            return true;
        }
        String namesCsv = parts.getFirst();
        boolean pestMode = isPestRequest(parseNames(namesCsv));
        double radius = parts.size() >= 2 ? parseDouble(parts, 1, 6.0) : 6.0;
        Entity nearest = findNearestEntity(client, namesCsv, 64.0);
        if (nearest == null) {
            stopMovement(client);
            debugThink(action, nowTick, "no entity match for move request names=" + namesCsv);
            if (pestMode && tickPestSearchFallback(client, action, nowTick)) {
                return false;
            }
            return retryOrFinish(action, nowTick);
        }
        if (pestMode) {
            renderPestTargetMarker(nearest, "Track");
        }
        return navigateTowards(client, action, entityPos(nearest), radius, nowTick);
    }

    private boolean tickFlyToEntity(MinecraftClient client, ActiveAction action, long nowTick) {
        List<String> parts = split(action.action.payload());
        if (parts.isEmpty()) {
            return true;
        }
        String namesCsv = parts.getFirst();
        boolean pestMode = isPestRequest(parseNames(namesCsv));
        double radius = parts.size() >= 2 ? parseDouble(parts, 1, 6.0) : 6.0;
        Entity nearest = findNearestEntity(client, namesCsv, 80.0);
        if (nearest == null) {
            stopMovement(client);
            debugThink(action, nowTick, "no entity match for fly request names=" + namesCsv);
            if (pestMode && tickPestSearchFallback(client, action, nowTick)) {
                return false;
            }
            return retryOrFinish(action, nowTick);
        }
        if (pestMode) {
            renderPestTargetMarker(nearest, "Fly");
        }
        return flyTowards(client, entityPos(nearest), radius, action, nowTick, pestMode);
    }

    private boolean tickEntityInteraction(MinecraftClient client, ActiveAction action, long nowTick, boolean attack) {
        List<String> parts = split(action.action.payload());
        if (parts.isEmpty()) {
            return true;
        }
        String namesCsv = parts.getFirst();
        List<String> requestedNames = parseNames(namesCsv);
        boolean pestMode = isPestRequest(requestedNames);
        double radius = parts.size() >= 2 ? parseDouble(parts, 1, 4.5) : 4.5;
        Entity nearest = findNearestEntity(client, namesCsv, 64.0);
        if (nearest == null) {
            stopMovement(client);
            if (attack && pestMode) {
                applyUseKeyHold(client, false);
                action.pestConsecutiveNoTargetTicks++;
                debugThink(action, nowTick, "pest target lost, scanning plot");
                if (tickPestSearchFallback(client, action, nowTick)) {
                    if (action.pestConsecutiveNoTargetTicks >= 28L) {
                        return true;
                    }
                    return false;
                }
                long probeInterval = resolvePestProbeIntervalTicks();
                if (nowTick - action.entityInteractionLastHitTick >= probeInterval) {
                    KeyBindUtils.leftClick(client);
                    action.entityInteractionLastHitTick = nowTick;
                }
                if (action.entityInteractionHits > 0 && action.pestConsecutiveNoTargetTicks >= 12L) {
                    return true;
                }
                if (action.pestConsecutiveNoTargetTicks >= 30L) {
                    return true;
                }
            }
            return retryOrFinish(action, nowTick);
        }
        action.pestConsecutiveNoTargetTicks = 0L;
        if (pestMode) {
            renderPestTargetMarker(nearest, attack ? "Attack" : "Interact");
        }

        Vec3d targetPos = entityPos(nearest);
        if (pestMode && (client.player.getAbilities().flying || client.player.getAbilities().allowFlying)) {
            if (!flyTowards(client, targetPos, Math.max(2.6, radius * 0.9), action, nowTick, true)) {
                debugThink(
                        action,
                        nowTick,
                        "flying toward pest " + nearest.getName().getString() + " dist="
                                + String.format(Locale.US, "%.2f", targetPos.distanceTo(playerPos(client)))
                );
                return false;
            }
        } else if (!navigateTowards(client, action, targetPos, radius, nowTick)) {
            return false;
        }

        if (pestMode) {
            lookAtPest(client, targetPos, action, nowTick);
        } else {
            lookAt(client, targetPos);
        }
        stopMovement(client);
        if (attack) {
            if (action.entityInteractionTargetId != nearest.getId()) {
                action.entityInteractionTargetId = nearest.getId();
                action.entityInteractionHits = 0;
                action.entityInteractionLastHitTick = nowTick - 5L;
                action.pestSmoothedTarget = targetPos;
            }
            if (pestMode) {
                applyUseKeyHold(client, true);
                action.entityInteractionHits++;
                action.pestLastSeenTick = nowTick;
                // Keep chasing/holding vacuum for a while; the caller refreshes this action periodically.
                if (nowTick - action.startTick >= 160L) {
                    return true;
                }
                return false;
            }
            if (nowTick - action.entityInteractionLastHitTick >= 5L) {
                client.interactionManager.attackEntity(client.player, nearest);
                client.player.swingHand(Hand.MAIN_HAND);
                action.entityInteractionLastHitTick = nowTick;
                action.entityInteractionHits++;
            }
            return action.entityInteractionHits >= 1;
        } else {
            client.interactionManager.interactEntity(client.player, nearest, Hand.MAIN_HAND);
            client.player.swingHand(Hand.MAIN_HAND);
            return true;
        }
    }

    private boolean tickWaitForScreen(MinecraftClient client, ActiveAction action, long nowTick) {
        List<String> parts = split(action.action.payload());
        if (parts.isEmpty()) {
            return true;
        }
        String expected = parts.getFirst().toLowerCase(Locale.ROOT);
        if (guiDecisionEngine.isScreenTitleMatch(client, expected)) {
            return true;
        }
        return retryOrFinish(action, nowTick);
    }

    private boolean tickClickSlotMatching(MinecraftClient client, ActiveAction action, long nowTick) {
        if (client.currentScreen == null || client.player == null || client.interactionManager == null) {
            return retryOrFinish(action, nowTick);
        }
        String query = parseSlotQuery(action.action.payload());
        OptionalInt slotId = guiDecisionEngine.findBestSlot(client, query);
        if (slotId.isPresent()) {
            InventoryUtils.clickContainerSlot(
                    client,
                    slotId.getAsInt(),
                    InventoryUtils.ClickType.LEFT,
                    InventoryUtils.ClickMode.PICKUP
            );
            return true;
        }
        return retryOrFinish(action, nowTick);
    }

    private boolean tickMineNearestBlock(MinecraftClient client, ActiveAction action, long nowTick) {
        List<String> parts = split(action.action.payload());
        if (parts.isEmpty()) {
            return true;
        }
        String hintsCsv = parts.getFirst();
        double radius = parts.size() >= 2 ? parseDouble(parts, 1, 4.0) : 4.0;
        List<String> hints = parseNames(hintsCsv);
        if (hints.isEmpty()) {
            return true;
        }

        if (action.miningTarget == null || !isValidMiningTarget(client, action.miningTarget, hints, radius)) {
            action.miningTarget = findNearestMatchingBlock(client, hints, radius);
            action.miningTargetSinceTick = nowTick;
            if (action.miningTarget == null) {
                stopMovement(client);
                return retryOrFinish(action, nowTick);
            }
        }

        Vec3d targetVec = action.miningTarget.toCenterPos();
        if (!navigateTowards(client, action, targetVec, 2.1, nowTick)) {
            return false;
        }

        lookAt(client, targetVec);
        stopMovement(client);
        equipBestToolForBlock(client, action.miningTarget);
        client.interactionManager.attackBlock(action.miningTarget, Direction.UP);
        client.player.swingHand(Hand.MAIN_HAND);

        BlockState state = client.world.getBlockState(action.miningTarget);
        if (state.isAir()) {
            return true;
        }
        if (nowTick - action.miningTargetSinceTick > 14L) {
            action.miningTarget = null;
        }
        return false;
    }

    private boolean isValidMiningTarget(MinecraftClient client, BlockPos pos, List<String> hints, double radius) {
        if (client.world == null || client.player == null || pos == null) {
            return false;
        }
        if (!blockMatchesHints(client.world.getBlockState(pos), hints)) {
            return false;
        }
        return pos.toCenterPos().distanceTo(playerPos(client)) <= Math.max(1.0, radius + 0.75);
    }

    private BlockPos findNearestMatchingBlock(MinecraftClient client, List<String> hints, double radius) {
        if (client.world == null || client.player == null) {
            return null;
        }
        BlockPos playerPos = client.player.getBlockPos();
        int r = Math.max(1, (int) Math.ceil(radius));
        double maxDistanceSq = radius * radius;
        double bestDistanceSq = Double.MAX_VALUE;
        BlockPos best = null;

        for (int x = -r; x <= r; x++) {
            for (int y = -2; y <= 3; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    BlockState state = client.world.getBlockState(pos);
                    if (state.isAir() || !blockMatchesHints(state, hints)) {
                        continue;
                    }
                    double distSq = pos.toCenterPos().squaredDistanceTo(playerPos(client));
                    if (distSq > maxDistanceSq) {
                        continue;
                    }
                    if (distSq < bestDistanceSq) {
                        bestDistanceSq = distSq;
                        best = pos.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    private boolean blockMatchesHints(BlockState state, List<String> hints) {
        if (state == null || state.isAir()) {
            return false;
        }
        String id = state.getBlock().toString().toLowerCase(Locale.ROOT);
        String translation = state.getBlock().getTranslationKey().toLowerCase(Locale.ROOT);
        for (String hint : hints) {
            if (hint.isEmpty()) {
                continue;
            }
            if (id.contains(hint) || translation.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private void equipBestToolForBlock(MinecraftClient client, BlockPos blockPos) {
        if (client.player == null || client.world == null || blockPos == null) {
            return;
        }
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.autoChooseTool) {
            return;
        }

        BlockState state = client.world.getBlockState(blockPos);
        String id = state.getBlock().toString().toLowerCase(Locale.ROOT);
        String translation = state.getBlock().getTranslationKey().toLowerCase(Locale.ROOT);
        String toolQuery;
        if (id.contains("log") || id.contains("wood") || translation.contains("log") || translation.contains("wood")) {
            toolQuery = "treecapitator,axe";
        } else if (id.contains("stone")
                || id.contains("cobble")
                || id.contains("slab")
                || translation.contains("stone")
                || translation.contains("cobble")) {
            toolQuery = "pickaxe,stonk";
        } else {
            toolQuery = "scythe";
        }

        int slot = findHotbarSlot(client, toolQuery);
        if (slot >= 0) {
            client.player.getInventory().setSelectedSlot(slot);
        }
    }

    private int findHotbarSlot(MinecraftClient client, String queryCsv) {
        if (client.player == null) {
            return -1;
        }
        List<String> queries = parseNames(queryCsv);
        if (queries.isEmpty()) {
            return -1;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String name = stack.getName().getString().toLowerCase(Locale.ROOT);
            for (String query : queries) {
                if (name.contains(query)) {
                    return slot;
                }
            }
        }
        return -1;
    }

    private boolean navigateTowards(MinecraftClient client, ActiveAction action, Vec3d target, double stopDistance, long nowTick) {
        Vec3d playerPos = playerPos(client);
        if (playerPos.distanceTo(target) <= Math.max(0.5, stopDistance)) {
            stopMovement(client);
            navigationState = null;
            return true;
        }

        if (navigationState == null || navigationState.goal.distanceTo(target) > 1.2) {
            navigationState = new NavigationState(target);
            planPath(client, navigationState, playerPos);
        }

        if (navigationState.path.isEmpty() || navigationState.index >= navigationState.path.size()) {
            // Fallback if no path was found.
            debugThink(action, nowTick, "path empty, fallback direct move");
            return moveDirect(client, target, stopDistance, action, nowTick);
        }

        Vec3d waypoint = navigationState.path.get(navigationState.index);
        renderNavigationPathMarkers(action, navigationState, playerPos);
        if (playerPos.distanceTo(waypoint) <= 0.9) {
            navigationState.index++;
            if (navigationState.index >= navigationState.path.size()) {
                return moveDirect(client, target, stopDistance, action, nowTick);
            }
            waypoint = navigationState.path.get(navigationState.index);
        }

        if (isStuck(playerPos, nowTick)) {
            if (navigationState.replanAttempts < action.maxRetries) {
                navigationState.replanAttempts++;
                debugThink(action, nowTick, "stuck while pathing, replanning attempt=" + navigationState.replanAttempts);
                planPath(client, navigationState, playerPos);
            } else {
                debugThink(action, nowTick, "stuck after max replans, direct move fallback");
                return moveDirect(client, target, stopDistance, action, nowTick);
            }
        }
        return moveToPoint(client, waypoint, Math.max(0.8, stopDistance * 0.5));
    }

    private void renderNavigationPathMarkers(ActiveAction action, NavigationState nav, Vec3d playerPos) {
        if (action == null || nav == null || nav.path == null || nav.path.isEmpty()) {
            return;
        }
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        boolean show = isDebugModeEnabled() || (action.pestAction && config.pestsTracers);
        if (!show) {
            return;
        }
        int limit = Math.min(nav.path.size(), 20);
        Vec3d prev = playerPos;
        for (int i = Math.max(0, nav.index); i < limit; i++) {
            Vec3d point = nav.path.get(i);
            int color = i == nav.index ? 0xFFF4D35E : 0xFF7AC8F7;
            Box box = new Box(
                    point.x - 0.20, point.y - 0.08, point.z - 0.20,
                    point.x + 0.20, point.y + 0.08, point.z + 0.20
            );
            RenderUtils.drawBox(box, color, 4L);
            RenderUtils.drawTracer(point, color, 4L);
            if (i == nav.index) {
                RenderUtils.drawText(
                        point.add(0.0, 0.30, 0.0),
                        "Path " + (nav.index + 1) + "/" + nav.path.size(),
                        0xFFFDF4C8,
                        4L
                );
            }
            if (prev != null && i > nav.index) {
                RenderUtils.drawTracer(point.lerp(prev, 0.5), 0xFF4FA7D6, 3L);
            }
            prev = point;
        }
    }

    private void planPath(MinecraftClient client, NavigationState nav, Vec3d start) {
        nav.path = pathfinderService.findPath(client, start, nav.goal, 1800);
        nav.index = Math.min(1, Math.max(0, nav.path.size() - 1));
        nav.lastReplanTick = nav.lastProgressTick;
        if (isDebugModeEnabled()) {
            FarmHelperFabric.getWebhookService().debugTrace(
                    "automation-path",
                    String.format(
                            Locale.US,
                            "plan path start=(%.2f,%.2f,%.2f) goal=(%.2f,%.2f,%.2f) nodes=%d",
                            start.x, start.y, start.z,
                            nav.goal.x, nav.goal.y, nav.goal.z,
                            nav.path.size()
                    )
            );
        }
    }

    private boolean moveDirect(MinecraftClient client, Vec3d target, double stopDistance, ActiveAction action, long nowTick) {
        if (!moveToPoint(client, target, stopDistance)) {
            return false;
        }
        if (nowTick >= action.timeoutTick) {
            return retryOrFinish(action, nowTick);
        }
        return true;
    }

    private boolean moveToPoint(MinecraftClient client, Vec3d target, double stopDistance) {
        Vec3d player = playerPos(client);
        double dx = target.x - player.x;
        double dy = target.y - player.y;
        double dz = target.z - player.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDistance <= Math.max(0.5, stopDistance)) {
            stopMovement(client);
            return true;
        }

        lookAt(client, target);
        client.options.forwardKey.setPressed(true);
        client.options.sprintKey.setPressed(true);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.backKey.setPressed(false);
        client.options.jumpKey.setPressed(client.player.horizontalCollision || dy > 1.0);
        client.options.sneakKey.setPressed(false);
        return false;
    }

    private boolean flyTowards(MinecraftClient client, Vec3d target, double stopDistance, ActiveAction action, long nowTick, boolean pestMode) {
        Vec3d player = playerPos(client);
        double dx = target.x - player.x;
        double dy = target.y - player.y;
        double dz = target.z - player.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDistance <= Math.max(0.5, stopDistance)) {
            stopMovement(client);
            return true;
        }

        if (pestMode) {
            lookAtPest(client, target, action, nowTick);
        } else {
            lookAt(client, target);
        }
        boolean canFly = client.player.getAbilities().allowFlying || client.player.getAbilities().flying;

        client.options.forwardKey.setPressed(true);
        client.options.sprintKey.setPressed(true);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.backKey.setPressed(false);
        client.options.jumpKey.setPressed(canFly ? dy > 0.6 : (client.player.horizontalCollision || dy > 1.0));
        client.options.sneakKey.setPressed(canFly && dy < -0.8);
        if (isDebugModeEnabled()) {
            RenderUtils.drawTracer(target, 0xFF9FE2FF, 4L);
        }

        if (nowTick >= action.timeoutTick) {
            return retryOrFinish(action, nowTick);
        }
        return false;
    }

    private boolean isStuck(Vec3d currentPos, long nowTick) {
        if (navigationState == null) {
            return false;
        }
        if (navigationState.lastProgressPos == null) {
            navigationState.lastProgressPos = currentPos;
            navigationState.lastProgressTick = nowTick;
            return false;
        }

        if (nowTick - navigationState.lastProgressTick < 20L) {
            return false;
        }

        double moved = currentPos.distanceTo(navigationState.lastProgressPos);
        navigationState.lastProgressPos = currentPos;
        navigationState.lastProgressTick = nowTick;
        return moved < 0.25;
    }

    private void lookAt(MinecraftClient client, Vec3d target) {
        double dx = target.x - client.player.getX();
        double dy = target.y + client.player.getStandingEyeHeight() - client.player.getEyeY();
        double dz = target.z - client.player.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDistance)));

        float yaw = approachAngle(client.player.getYaw(), targetYaw, YAW_STEP);
        float pitch = approach(client.player.getPitch(), targetPitch, PITCH_STEP);
        client.player.setYaw(yaw);
        client.player.setPitch(MathHelper.clamp(pitch, -90f, 90f));
    }

    private void lookAtPest(MinecraftClient client, Vec3d target, ActiveAction action, long nowTick) {
        if (client == null || client.player == null || target == null || action == null) {
            return;
        }
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        float deadzoneYaw = Math.max(0.5f, config.pestsAimDeadzoneYaw <= 0f ? PEST_DEADZONE_YAW_FALLBACK : config.pestsAimDeadzoneYaw);
        float deadzonePitch = Math.max(0.5f, config.pestsAimDeadzonePitch <= 0f ? PEST_DEADZONE_PITCH_FALLBACK : config.pestsAimDeadzonePitch);

        Vec3d jittered = new Vec3d(
                target.x + randomBetween(-PEST_AIM_JITTER, PEST_AIM_JITTER),
                target.y + randomBetween(-PEST_AIM_JITTER * 0.55, PEST_AIM_JITTER * 0.55),
                target.z + randomBetween(-PEST_AIM_JITTER, PEST_AIM_JITTER)
        );
        if (action.pestSmoothedTarget == null) {
            action.pestSmoothedTarget = jittered;
        } else {
            action.pestSmoothedTarget = action.pestSmoothedTarget.lerp(jittered, PEST_AIM_LAG_ALPHA);
        }

        double dx = action.pestSmoothedTarget.x - client.player.getX();
        double dy = action.pestSmoothedTarget.y + client.player.getStandingEyeHeight() - client.player.getEyeY();
        double dz = action.pestSmoothedTarget.z - client.player.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDistance)));

        float yawDelta = Math.abs(MathHelper.wrapDegrees(targetYaw - client.player.getYaw()));
        float pitchDelta = Math.abs(MathHelper.wrapDegrees(targetPitch - client.player.getPitch()));
        if (yawDelta <= deadzoneYaw && pitchDelta <= deadzonePitch) {
            if (isDebugModeEnabled() && nowTick - action.lastThinkLogTick >= DEBUG_THINK_INTERVAL_TICKS) {
                action.lastThinkLogTick = nowTick;
                FarmHelperFabric.getWebhookService().debugTrace(
                        "automation-aim",
                        String.format(
                                Locale.US,
                                "pest in deadzone yawΔ=%.2f pitchΔ=%.2f box=%.1f/%.1f",
                                yawDelta,
                                pitchDelta,
                                deadzoneYaw,
                                deadzonePitch
                        )
                );
            }
            return;
        }

        float yawStep = (float) randomBetween(PEST_YAW_STEP_MIN, PEST_YAW_STEP_MAX);
        float pitchStep = (float) randomBetween(PEST_PITCH_STEP_MIN, PEST_PITCH_STEP_MAX);
        float yaw = approachAngle(client.player.getYaw(), targetYaw, yawStep);
        float pitch = approach(client.player.getPitch(), targetPitch, pitchStep);
        client.player.setYaw(yaw);
        client.player.setPitch(MathHelper.clamp(pitch, -90f, 90f));

        if (isDebugModeEnabled() && nowTick - action.lastThinkLogTick >= DEBUG_THINK_INTERVAL_TICKS) {
            action.lastThinkLogTick = nowTick;
            FarmHelperFabric.getWebhookService().debugTrace(
                    "automation-aim",
                    String.format(
                            Locale.US,
                            "pest aim lag target=(%.2f,%.2f,%.2f) yawStep=%.2f pitchStep=%.2f",
                            action.pestSmoothedTarget.x,
                            action.pestSmoothedTarget.y,
                            action.pestSmoothedTarget.z,
                            yawStep,
                            pitchStep
                    )
            );
        }
    }

    private Entity findNearestEntity(MinecraftClient client, String namesCsv, double maxDistance) {
        if (client.world == null || client.player == null) {
            return null;
        }
        List<String> normalizedNames = parseNames(namesCsv);
        if (normalizedNames.isEmpty()) {
            return null;
        }
        boolean pestMode = isPestRequest(normalizedNames);

        double effectiveDistance = pestMode ? Math.max(maxDistance, 192.0) : maxDistance;
        double bestDistanceSq = effectiveDistance * effectiveDistance;
        Entity best = null;
        double bestPestScore = -1.0;

        for (Entity entity : client.world.getEntities()) {
            if (entity == null || entity == client.player) {
                continue;
            }
            if (!entity.isAlive()) {
                continue;
            }
            String entityName = entity.getName().getString().toLowerCase(Locale.ROOT);
            boolean nameMatches = normalizedNames.stream().anyMatch(entityName::contains);
            if (!nameMatches && !pestMode) {
                continue;
            }
            String typeName = entity.getType().toString().toLowerCase(Locale.ROOT);

            double distSq = entity.squaredDistanceTo(client.player);
            if (distSq > bestDistanceSq) {
                continue;
            }

            if (pestMode) {
                if (entity instanceof PlayerEntity) {
                    continue;
                }
                double pestScore = pestHeuristics.score(entity);
                boolean confirmed = pestHeuristics.isConfirmedPest(entity);
                boolean likely = pestHeuristics.isLikelyPest(entity);
                if (looksLikeNonPestTarget(entityName, typeName) && !confirmed) {
                    continue;
                }
                if (!confirmed && !likely) {
                    continue;
                }
                if (pestScore > bestPestScore || (pestScore == bestPestScore && distSq < bestDistanceSq)) {
                    bestPestScore = pestScore;
                    bestDistanceSq = distSq;
                    best = entity;
                }
            } else if (distSq < bestDistanceSq) {
                bestDistanceSq = distSq;
                best = entity;
            }
        }
        return best;
    }

    private boolean tickPestSearchFallback(MinecraftClient client, ActiveAction action, long nowTick) {
        if (client == null || client.player == null) {
            return false;
        }
        Vec3d target = computePestSearchWaypoint(client, action);
        if (target == null) {
            return false;
        }
        RenderUtils.drawTracer(target, 0xFF4BC7FF, 8L);
        RenderUtils.drawText(target.add(0.0, 0.45, 0.0), "Searching pests...", 0xFFE8F9FF, 8L);
        boolean reached = navigateTowards(client, action, target, 2.6, nowTick);
        if (reached && nowTick - action.pestSearchLastAdvanceTick >= 8L) {
            action.pestSearchWaypointIndex++;
            action.pestSearchLastAdvanceTick = nowTick;
        }
        return true;
    }

    private Vec3d computePestSearchWaypoint(MinecraftClient client, ActiveAction action) {
        if (client == null || client.player == null) {
            return null;
        }
        int plot = PlotUtils.getPlotNumber(client.player.getBlockPos()).orElse(-1);
        if (plot <= 0) {
            return null;
        }
        BlockPos center = PlotUtils.getPlotCenter(plot);
        if (center == null) {
            return null;
        }
        int index = Math.floorMod(action.pestSearchWaypointIndex, PEST_SEARCH_OFFSETS.length);
        Vec3d offset = PEST_SEARCH_OFFSETS[index];
        return new Vec3d(
                center.getX() + 0.5 + offset.x,
                client.player.getY(),
                center.getZ() + 0.5 + offset.z
        );
    }

    private static List<String> parseNames(String csv) {
        String[] names = csv.split(",");
        List<String> normalized = new ArrayList<>(names.length);
        for (String name : names) {
            String value = name.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private static boolean isPestRequest(List<String> names) {
        for (String name : names) {
            if (PEST_QUERY_TOKENS.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPestAction(ClientActionQueue.Action action) {
        if (action == null) {
            return false;
        }
        return switch (action.type()) {
            case MOVE_TO_ENTITY, FLY_TO_ENTITY, INTERACT_NEAREST_ENTITY, ATTACK_NEAREST_ENTITY ->
                    isPestRequest(parseNames(parseSlotQuery(action.payload())));
            default -> false;
        };
    }

    private boolean looksLikeNonPestTarget(String entityName, String typeName) {
        for (String keyword : NON_PEST_TARGET_KEYWORDS) {
            if (entityName.contains(keyword)) {
                return true;
            }
        }
        return typeName.contains("villager")
                || typeName.contains("merchant")
                || typeName.contains("wandering_trader");
    }

    private static String parseSlotQuery(String payload) {
        List<String> parts = split(payload);
        if (parts.isEmpty()) {
            return "";
        }
        return parts.getFirst();
    }

    private Vec3d entityPos(Entity entity) {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    }

    private Vec3d playerPos(MinecraftClient client) {
        return new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
    }

    private void renderPestTargetMarker(Entity entity, String phaseLabel) {
        if (entity == null) {
            return;
        }
        Vec3d target = entityPos(entity).add(0.0, Math.max(0.4, entity.getHeight() * 0.5), 0.0);
        RenderUtils.drawTracer(target, PEST_TRACER_COLOR, 8L);
        RenderUtils.drawBox(entity.getBoundingBox().expand(0.08), PEST_BOX_COLOR, 8L);
        String name = entity.getName().getString();
        if (name != null && !name.isBlank()) {
            RenderUtils.drawText(target.add(0.0, 0.35, 0.0), "Pest " + phaseLabel + ": " + name, PEST_TEXT_COLOR, 8L);
        }
    }

    private void stopMovement(MinecraftClient client) {
        if (client == null || client.options == null) {
            return;
        }
        client.options.forwardKey.setPressed(false);
        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        client.options.sneakKey.setPressed(false);
    }

    private void clearActive(MinecraftClient client) {
        if (!isMacroDrivingMovement()) {
            stopMovement(client);
        }
        wasControllingMovement = false;
        activeAction = null;
        navigationState = null;
    }

    private boolean retryOrFinish(ActiveAction action, long nowTick) {
        if (nowTick - action.lastRetryTick < RETRY_INTERVAL_TICKS) {
            return false;
        }
        action.lastRetryTick = nowTick;
        action.retries++;
        return action.retries > action.maxRetries;
    }

    private boolean requiresPlayerContext(ClientActionQueue.ActionType type) {
        return switch (type) {
            case REQUEST_WINDOW_ATTENTION -> false;
            default -> true;
        };
    }

    private boolean requiresActiveProcessing(ClientActionQueue.ActionType type) {
        return switch (type) {
            case MOVE_TO_POS, MOVE_TO_ENTITY, FLY_TO_ENTITY, INTERACT_NEAREST_ENTITY, ATTACK_NEAREST_ENTITY, WAIT_FOR_SCREEN, CLICK_SLOT_MATCHING, MINE_NEAREST_BLOCK -> true;
            default -> false;
        };
    }

    private long parseTimeoutTicks(ClientActionQueue.ActionType type, String payload, long fallback) {
        List<String> parts = split(payload);
        if (parts.isEmpty()) {
            return fallback;
        }
        String candidate = switch (type) {
            case WAIT_FOR_SCREEN, CLICK_SLOT_MATCHING -> parts.size() >= 2 ? parts.get(1) : parts.getLast();
            default -> parts.getLast();
        };
        try {
            return Math.max(1L, Long.parseLong(candidate));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int parseRetries(ClientActionQueue.ActionType type, String payload, int fallback) {
        List<String> parts = split(payload);
        if (parts.size() < 3 || (type != ClientActionQueue.ActionType.WAIT_FOR_SCREEN && type != ClientActionQueue.ActionType.CLICK_SLOT_MATCHING)) {
            return fallback;
        }
        try {
            return Math.max(0, Integer.parseInt(parts.get(2)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static List<String> split(String payload) {
        List<String> result = new ArrayList<>();
        if (payload == null || payload.isBlank()) {
            return result;
        }
        for (String part : payload.split("\\|")) {
            result.add(part.trim());
        }
        return result;
    }

    private double parseDouble(List<String> parts, int index, double fallback) {
        if (index < 0 || index >= parts.size()) {
            return fallback;
        }
        try {
            return Double.parseDouble(parts.get(index));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long resolvePestProbeIntervalTicks() {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        long trackerCooldownTicks = 20L;
        // Tracker ability has cooldown, so only send probe clicks at that pace.
        double cooldownSeconds = Math.max(0.2, FarmHelperFabricClient.getRuntimeSnapshot().vacuumTrackerCooldownSeconds);
        trackerCooldownTicks = Math.max(4L, Math.round(cooldownSeconds * 20.0));
        if (config.debugMode) {
            return Math.max(6L, trackerCooldownTicks);
        }
        return Math.max(10L, trackerCooldownTicks);
    }

    private boolean isDebugModeEnabled() {
        return FarmHelperFabric.getConfigManager().getConfig().debugMode;
    }

    private void debugThink(ActiveAction action, long nowTick, String message) {
        if (!isDebugModeEnabled() || action == null || message == null || message.isBlank()) {
            return;
        }
        if (nowTick - action.lastThinkLogTick < DEBUG_THINK_INTERVAL_TICKS) {
            return;
        }
        action.lastThinkLogTick = nowTick;
        FarmHelperFabric.getWebhookService().debugTrace("automation-think", message);
    }

    private boolean shouldSuppressRotationForAction(ClientActionQueue.ActionType type) {
        return switch (type) {
            case MOVE_TO_POS, MOVE_TO_ENTITY, FLY_TO_ENTITY, INTERACT_NEAREST_ENTITY, ATTACK_NEAREST_ENTITY, MINE_NEAREST_BLOCK -> true;
            default -> false;
        };
    }

    private boolean isTeleportLikeCommand(String command) {
        String normalized = command.toLowerCase(Locale.ROOT);
        return normalized.startsWith("warp ")
                || normalized.startsWith("lobby")
                || normalized.startsWith("hub")
                || normalized.startsWith("is")
                || normalized.startsWith("visit")
                || normalized.startsWith("plottp")
                || normalized.startsWith("tptoplot")
                || normalized.startsWith("spawn");
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

    private double randomBetween(double min, double max) {
        if (max <= min) {
            return min;
        }
        return min + Math.random() * (max - min);
    }

    private boolean isMacroDrivingMovement() {
        return FarmHelperFabric.getMacroController().isToggled()
                && FarmHelperFabric.getMacroController().getState() == MacroState.FARMING
                && !FarmHelperFabric.getFailsafeManager().hasActiveFailsafe();
    }

    private static final class ActiveAction {
        private final ClientActionQueue.Action action;
        private final long startTick;
        private final long timeoutTick;
        private final int maxRetries;
        private final boolean pestAction;
        private int retries;
        private long lastRetryTick;
        private BlockPos miningTarget;
        private long miningTargetSinceTick;
        private int entityInteractionTargetId;
        private long entityInteractionLastHitTick;
        private int entityInteractionHits;
        private long pestConsecutiveNoTargetTicks;
        private long pestLastSeenTick;
        private Vec3d pestSmoothedTarget;
        private int pestSearchWaypointIndex;
        private long pestSearchLastAdvanceTick;
        private long lastThinkLogTick;

        private ActiveAction(ClientActionQueue.Action action, long startTick, long timeoutTick, int maxRetries) {
            this.action = action;
            this.startTick = startTick;
            this.timeoutTick = timeoutTick;
            this.maxRetries = maxRetries;
            this.pestAction = isPestAction(action);
            this.retries = 0;
            this.lastRetryTick = startTick;
            this.miningTarget = null;
            this.miningTargetSinceTick = startTick;
            this.entityInteractionTargetId = Integer.MIN_VALUE;
            this.entityInteractionLastHitTick = startTick - 5L;
            this.entityInteractionHits = 0;
            this.pestConsecutiveNoTargetTicks = 0L;
            this.pestLastSeenTick = startTick;
            this.pestSmoothedTarget = null;
            this.pestSearchWaypointIndex = 0;
            this.pestSearchLastAdvanceTick = startTick;
            this.lastThinkLogTick = startTick - DEBUG_THINK_INTERVAL_TICKS;
        }
    }

    private static final class NavigationState {
        private final Vec3d goal;
        private List<Vec3d> path = List.of();
        private int index = 0;
        private int replanAttempts = 0;
        private long lastReplanTick = 0L;
        private Vec3d lastProgressPos;
        private long lastProgressTick = 0L;

        private NavigationState(Vec3d goal) {
            this.goal = goal;
        }
    }
}

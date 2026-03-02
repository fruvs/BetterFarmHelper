package com.jelly.farmhelper.fabric.automation;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.runtime.ClientActionQueue;
import com.jelly.farmhelper.fabric.state.ClientModeController;
import com.jelly.farmhelper.fabric.util.InventoryUtils;
import com.jelly.farmhelper.fabric.util.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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
    private static final long RETRY_INTERVAL_TICKS = 12L;
    private static final Set<String> PEST_QUERY_TOKENS = Set.of(
            "pest", "pests", "beetle", "cricket", "earthworm", "fly", "locust", "mite",
            "mosquito", "moth", "rat", "slug", "praying mantis", "firefly", "dragonfly"
    );
    private static final int PEST_TRACER_COLOR = 0xFF3FC4FF;
    private static final int PEST_BOX_COLOR = 0xFF56D8FF;
    private static final int PEST_TEXT_COLOR = 0xFFE8F9FF;

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
            return retryOrFinish(action, nowTick);
        }
        if (pestMode) {
            renderPestTargetMarker(nearest, "Fly");
        }
        return flyTowards(client, entityPos(nearest), radius, action, nowTick);
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
            if (attack && pestMode && action.entityInteractionHits > 0) {
                return true;
            }
            return retryOrFinish(action, nowTick);
        }
        if (pestMode) {
            renderPestTargetMarker(nearest, attack ? "Attack" : "Interact");
        }

        if (!navigateTowards(client, action, entityPos(nearest), radius, nowTick)) {
            return false;
        }

        lookAt(client, entityPos(nearest));
        stopMovement(client);
        if (attack) {
            if (action.entityInteractionTargetId != nearest.getId()) {
                action.entityInteractionTargetId = nearest.getId();
                action.entityInteractionHits = 0;
                action.entityInteractionLastHitTick = nowTick - 5L;
            }
            if (nowTick - action.entityInteractionLastHitTick >= 5L) {
                client.interactionManager.attackEntity(client.player, nearest);
                client.player.swingHand(Hand.MAIN_HAND);
                action.entityInteractionLastHitTick = nowTick;
                action.entityInteractionHits++;
            }
            if (pestMode) {
                return action.entityInteractionHits >= 3;
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
            return moveDirect(client, target, stopDistance, action, nowTick);
        }

        Vec3d waypoint = navigationState.path.get(navigationState.index);
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
                planPath(client, navigationState, playerPos);
            } else {
                return moveDirect(client, target, stopDistance, action, nowTick);
            }
        }
        return moveToPoint(client, waypoint, Math.max(0.8, stopDistance * 0.5));
    }

    private void planPath(MinecraftClient client, NavigationState nav, Vec3d start) {
        nav.path = pathfinderService.findPath(client, start, nav.goal, 1800);
        nav.index = Math.min(1, Math.max(0, nav.path.size() - 1));
        nav.lastReplanTick = nav.lastProgressTick;
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

    private boolean flyTowards(MinecraftClient client, Vec3d target, double stopDistance, ActiveAction action, long nowTick) {
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
        boolean canFly = client.player.getAbilities().allowFlying || client.player.getAbilities().flying;

        client.options.forwardKey.setPressed(true);
        client.options.sprintKey.setPressed(true);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.backKey.setPressed(false);
        client.options.jumpKey.setPressed(canFly ? dy > 0.6 : (client.player.horizontalCollision || dy > 1.0));
        client.options.sneakKey.setPressed(canFly && dy < -0.8);

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

    private Entity findNearestEntity(MinecraftClient client, String namesCsv, double maxDistance) {
        if (client.world == null || client.player == null) {
            return null;
        }
        List<String> normalizedNames = parseNames(namesCsv);
        if (normalizedNames.isEmpty()) {
            return null;
        }
        boolean pestMode = isPestRequest(normalizedNames);

        double bestDistanceSq = maxDistance * maxDistance;
        Entity best = null;
        double bestPestScore = -1.0;

        for (Entity entity : client.world.getEntities()) {
            if (entity == null || entity == client.player) {
                continue;
            }
            String entityName = entity.getName().getString().toLowerCase(Locale.ROOT);
            boolean nameMatches = normalizedNames.stream().anyMatch(entityName::contains);
            if (!nameMatches && !pestMode) {
                continue;
            }

            double distSq = entity.squaredDistanceTo(client.player);
            if (distSq > bestDistanceSq) {
                continue;
            }

            if (pestMode) {
                double pestScore = pestHeuristics.score(entity);
                boolean confirmed = pestHeuristics.isConfirmedPest(entity);
                boolean likely = pestHeuristics.isLikelyPest(entity);
                if (!confirmed && (!nameMatches || pestScore < 7.5)) {
                    continue;
                }
                if (!confirmed && !likely && !nameMatches) {
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

    private List<String> parseNames(String csv) {
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

    private boolean isPestRequest(List<String> names) {
        for (String name : names) {
            if (PEST_QUERY_TOKENS.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private String parseSlotQuery(String payload) {
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

    private List<String> split(String payload) {
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
        private int retries;
        private long lastRetryTick;
        private BlockPos miningTarget;
        private long miningTargetSinceTick;
        private int entityInteractionTargetId;
        private long entityInteractionLastHitTick;
        private int entityInteractionHits;

        private ActiveAction(ClientActionQueue.Action action, long startTick, long timeoutTick, int maxRetries) {
            this.action = action;
            this.startTick = startTick;
            this.timeoutTick = timeoutTick;
            this.maxRetries = maxRetries;
            this.retries = 0;
            this.lastRetryTick = startTick;
            this.miningTarget = null;
            this.miningTargetSinceTick = startTick;
            this.entityInteractionTargetId = Integer.MIN_VALUE;
            this.entityInteractionLastHitTick = startTick - 5L;
            this.entityInteractionHits = 0;
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

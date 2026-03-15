package com.jelly.farmhelper.fabric;

import com.jelly.farmhelper.fabric.automation.GuiDecisionEngine;
import com.jelly.farmhelper.fabric.automation.ClientAutomationExecutor;
import com.jelly.farmhelper.fabric.automation.PestEntityHeuristics;
import com.jelly.farmhelper.fabric.command.FarmHelperClientCommands;
import com.jelly.farmhelper.fabric.event.MillisecondEvent;
import com.jelly.farmhelper.fabric.event.MotionUpdateEvent;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;
import com.jelly.farmhelper.fabric.feature.VisitorOfferSnapshot;
import com.jelly.farmhelper.fabric.feature.module.AutoReconnectFeatureModule;
import com.jelly.farmhelper.fabric.feature.module.PestsDestroyerFeatureModule;
import com.jelly.farmhelper.fabric.handler.RotationHandler;
import com.jelly.farmhelper.fabric.hud.DebugHudRenderer;
import com.jelly.farmhelper.fabric.hud.FailsafeBannerRenderer;
import com.jelly.farmhelper.fabric.hud.ProfitHudRenderer;
import com.jelly.farmhelper.fabric.hud.StatusHudRenderer;
import com.jelly.farmhelper.fabric.macro.MovementMacroExecutor;
import com.jelly.farmhelper.fabric.runtime.ClientActionQueue;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;
import com.jelly.farmhelper.fabric.state.FreelookController;
import com.jelly.farmhelper.fabric.state.GameStateHandler;
import com.jelly.farmhelper.fabric.state.GameStateTracker;
import com.jelly.farmhelper.fabric.state.RuntimeGuards;
import com.jelly.farmhelper.fabric.util.AudioManager;
import com.jelly.farmhelper.fabric.util.BlockUtils;
import com.jelly.farmhelper.fabric.util.Chat;
import com.jelly.farmhelper.fabric.util.DesktopNotifier;
import com.jelly.farmhelper.fabric.util.FailsafeUtils;
import com.jelly.farmhelper.fabric.util.InventoryUtils;
import com.jelly.farmhelper.fabric.util.KeyBindUtils;
import com.jelly.farmhelper.fabric.util.PlotUtils;
import com.jelly.farmhelper.fabric.util.PlayerUtils;
import com.jelly.farmhelper.fabric.util.RenderUtils;
import com.jelly.farmhelper.fabric.util.ScoreboardUtils;
import com.jelly.farmhelper.fabric.util.TablistUtils;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.config.struct.RewarpPoint;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FarmHelperFabricClient implements ClientModInitializer {
    private static final long KEYBIND_DEBOUNCE_TICKS = 4L;
    private static final Pattern INFESTED_PLOT_PATTERN = Pattern.compile("plot\\s*(\\d+).*?(\\d+)\\s*pests?", Pattern.CASE_INSENSITIVE);
    private static final Pattern GUI_PLOT_PATTERN = Pattern.compile("plot\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GUI_PEST_PATTERN = Pattern.compile("(\\d+)\\D*?pests?", Pattern.CASE_INSENSITIVE);
    private static final int[] CONFIGURE_PLOT_ORDER = {
            21, 13, 9, 14, 22,
            15, 5, 1, 6, 16,
            10, 2, 0, 3, 11,
            17, 7, 4, 8, 18,
            23, 19, 12, 20, 24
    };
    private static final GameStateTracker GAME_STATE_TRACKER = new GameStateTracker();
    private static final GameStateHandler GAME_STATE_HANDLER = new GameStateHandler();
    private static final MovementMacroExecutor MOVEMENT_MACRO_EXECUTOR = new MovementMacroExecutor();
    private static final GuiDecisionEngine GUI_DECISION_ENGINE = new GuiDecisionEngine();
    private static final RuntimeSnapshot RUNTIME_SNAPSHOT = new RuntimeSnapshot();
    private static final FeatureRuntimeState FEATURE_RUNTIME_STATE = new FeatureRuntimeState();
    private static final RotationHandler ROTATION_HANDLER = RotationHandler.getInstance();
    private static volatile boolean forceStopRequested;
    private static KeyBinding toggleMacroKey;
    private static KeyBinding startMacroKey;
    private static KeyBinding stopMacroKey;
    private static KeyBinding openMenuKey;
    private static KeyBinding triggerFailsafeKey;
    private static KeyBinding freelookKey;
    private static KeyBinding cancelFailsafeKey;
    private static KeyBinding toggleUngrabMouseKey;
    private static KeyBinding plotCleaningHelperKey;
    private static KeyBinding triggerPestsDestroyerKey;
    private static KeyBinding tpToInfestedPlotKey;
    private boolean previousMacroToggled;
    private int statusUpdateTicks;
    private long tickCounter;
    private Vec3d lastPlayerPos;
    private float lastYaw;
    private float lastPitch;
    private int lastSelectedSlot = -1;
    private int selectedSlotChanges;
    private long lastClientActionTick = -20L;
    private long lastVoidRecoveryTick = -200L;
    private long lastSpawnRecoveryTick = -200L;
    private long lastRewarpTriggerTick = -400L;
    private long lastAutoPestsRewarpCheckTick = -400L;
    private long worldJoinTick = -1L;
    private boolean postJoinAligned;
    private boolean wasNearRewarpPoint;
    private RewarpPoint pendingRewarpPoint;
    private long pendingRewarpReadyTick = -1L;
    private String lastServerAddress = "";
    private String lastServerName = "Last Server";
    private int reconnectAttempts;
    private long reconnectCooldownUntilTick = -1L;
    private int lastLoggedGuiInfestedPlot = -1;
    private int lastLoggedGuiInfestedPests = 0;
    private long lastGuiNoMatchDebugTick = -200L;
    private long lastToggleMacroKeyTick = -20L;
    private long lastStartMacroKeyTick = -20L;
    private long lastStopMacroKeyTick = -20L;
    private long lastOpenMenuKeyTick = -20L;
    private long lastManualFailsafeKeyTick = -20L;
    private long lastFreelookKeyTick = -20L;
    private long lastCancelFailsafeKeyTick = -20L;
    private long lastToggleUngrabMouseKeyTick = -20L;
    private long lastPlotCleaningHelperKeyTick = -20L;
    private long lastTriggerPestsDestroyerKeyTick = -20L;
    private long lastTpToInfestedPlotKeyTick = -20L;
    private boolean failsafeActiveLastTick;
    private final ClientAutomationExecutor automationExecutor = new ClientAutomationExecutor();
    private final PestEntityHeuristics pestHeuristics = new PestEntityHeuristics();
    private record GuiInfestedPlot(int plot, int pests) {
    }

    @Override
    public void onInitializeClient() {
        int toggleDefault = FarmHelperFabric.getConfigManager().getConfig().toggleMacroKey;
        int startDefault = FarmHelperFabric.getConfigManager().getConfig().startMacroKey;
        int stopDefault = FarmHelperFabric.getConfigManager().getConfig().stopMacroKey;
        int openMenuDefault = FarmHelperFabric.getConfigManager().getConfig().openMenuKey;
        int triggerFailsafeDefault = FarmHelperFabric.getConfigManager().getConfig().triggerFailsafeKey;
        int freelookDefault = FarmHelperFabric.getConfigManager().getConfig().freelookKey;
        int cancelFailsafeDefault = FarmHelperFabric.getConfigManager().getConfig().cancelFailsafeKey;
        int toggleUngrabMouseDefault = FarmHelperFabric.getConfigManager().getConfig().toggleUngrabMouseKey;
        int plotCleaningHelperDefault = FarmHelperFabric.getConfigManager().getConfig().plotCleaningHelperKey;
        int triggerPestsDestroyerDefault = FarmHelperFabric.getConfigManager().getConfig().triggerPestsDestroyerKey;
        int tpToInfestedPlotDefault = FarmHelperFabric.getConfigManager().getConfig().tpToInfestedPlotKey;
        KeyBinding.Category farmHelperCategory = KeyBinding.Category.create(
                Identifier.of(FarmHelperFabric.MOD_ID, "main")
        );

        toggleMacroKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.farmhelperfabric.toggle_macro",
                InputUtil.Type.KEYSYM,
                toggleDefault,
                farmHelperCategory
        ));
        startMacroKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.farmhelperfabric.start_macro",
                InputUtil.Type.KEYSYM,
                startDefault > 0 ? startDefault : GLFW.GLFW_KEY_F7,
                farmHelperCategory
        ));
        stopMacroKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.farmhelperfabric.stop_macro",
                InputUtil.Type.KEYSYM,
                stopDefault > 0 ? stopDefault : GLFW.GLFW_KEY_F9,
                farmHelperCategory
        ));

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.farmhelperfabric.open_menu",
                InputUtil.Type.KEYSYM,
                openMenuDefault != 0 ? openMenuDefault : GLFW.GLFW_KEY_F,
                farmHelperCategory
        ));

        triggerFailsafeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.farmhelperfabric.test_failsafe",
                InputUtil.Type.KEYSYM,
                triggerFailsafeDefault > 0 ? triggerFailsafeDefault : GLFW.GLFW_KEY_F8,
                farmHelperCategory
        ));

        freelookKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.farmhelperfabric.freelook",
                InputUtil.Type.KEYSYM,
                freelookDefault != 0 ? freelookDefault : GLFW.GLFW_KEY_L,
                farmHelperCategory
        ));
        cancelFailsafeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.farmhelperfabric.cancel_failsafe",
                InputUtil.Type.KEYSYM,
                cancelFailsafeDefault != 0 ? cancelFailsafeDefault : GLFW.GLFW_KEY_UNKNOWN,
                farmHelperCategory
        ));
        toggleUngrabMouseKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.farmhelperfabric.toggle_ungrab_mouse",
                InputUtil.Type.KEYSYM,
                toggleUngrabMouseDefault != 0 ? toggleUngrabMouseDefault : GLFW.GLFW_KEY_UNKNOWN,
                farmHelperCategory
        ));
        plotCleaningHelperKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.farmhelperfabric.plot_cleaning_helper",
                InputUtil.Type.KEYSYM,
                plotCleaningHelperDefault != 0 ? plotCleaningHelperDefault : GLFW.GLFW_KEY_P,
                farmHelperCategory
        ));
        triggerPestsDestroyerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.farmhelperfabric.trigger_pests_destroyer",
                InputUtil.Type.KEYSYM,
                triggerPestsDestroyerDefault != 0 ? triggerPestsDestroyerDefault : GLFW.GLFW_KEY_UNKNOWN,
                farmHelperCategory
        ));
        tpToInfestedPlotKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.farmhelperfabric.tp_infested_plot",
                InputUtil.Type.KEYSYM,
                tpToInfestedPlotDefault != 0 ? tpToInfestedPlotDefault : GLFW.GLFW_KEY_UNKNOWN,
                farmHelperCategory
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        HudRenderCallback.EVENT.register((drawContext, renderTickCounter) -> {
            StatusHudRenderer.render(drawContext, renderTickCounter);
            ProfitHudRenderer.render(drawContext, renderTickCounter);
            DebugHudRenderer.render(drawContext, renderTickCounter);
            FailsafeBannerRenderer.render(drawContext, renderTickCounter);
        });
        WorldRenderEvents.END_MAIN.register(RenderUtils::renderWorld);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                FarmHelperClientCommands.register(dispatcher));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String line = message.getString();
            RUNTIME_SNAPSHOT.latestChatMessage = line;
            FarmHelperFabric.getFailsafeManager().onChatMessage(line);
            FarmHelperFabric.getFeatureManager().onChatMessage(line);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, c) -> {
            FarmHelperFabric.getFailsafeManager().onDisconnect();
            FarmHelperFabric.getFeatureManager().onDisconnect();
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                FreelookController.getInstance().setEnabled(client, false);
            }
            worldJoinTick = -1L;
            postJoinAligned = false;
            wasNearRewarpPoint = false;
            clearPendingRewarp();
            reconnectCooldownUntilTick = tickCounter + 20L;
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, c) -> {
            FarmHelperFabric.getFailsafeManager().onWorldChange();
            worldJoinTick = tickCounter;
            postJoinAligned = false;
            wasNearRewarpPoint = false;
            clearPendingRewarp();
            reconnectAttempts = 0;
            reconnectCooldownUntilTick = -1L;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getCurrentServerEntry() != null) {
                lastServerAddress = client.getCurrentServerEntry().address == null ? "" : client.getCurrentServerEntry().address.trim();
                lastServerName = client.getCurrentServerEntry().name == null ? "Last Server" : client.getCurrentServerEntry().name;
            }
        });
        FarmHelperFabric.getEventBus().subscribe(MotionUpdateEvent.Post.class, event -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                ROTATION_HANDLER.onMotionPost(mc);
            }
        });

        FarmHelperFabric.LOGGER.info("FarmHelper Fabric client initialized");
    }

    private void onClientTick(MinecraftClient client) {
        tickCounter++;
        FarmHelperFabric.getEventBus().post(new MillisecondEvent(System.currentTimeMillis(), tickCounter));
        ScoreboardUtils.tick(client);
        TablistUtils.tick(client);
        GAME_STATE_TRACKER.tick(client);
        GAME_STATE_HANDLER.tick(client);
        RenderUtils.tickCleanup();
        ROTATION_HANDLER.tick(client);
        FreelookController.getInstance().applyModelPose(client);
        if (forceStopRequested) {
            forceStopRequested = false;
            FarmHelperFabric.getMacroController().disableByUser();
            MOVEMENT_MACRO_EXECUTOR.reset(client);
            automationExecutor.cancelAll(client, "global stop request");
            FarmHelperFabric.getFeatureManager().cancelMacroExclusiveActions("global stop request");
            FarmHelperFabric.getClientActionQueue().clear();
        }

        if (consumeDebounced(toggleMacroKey, lastToggleMacroKeyTick)) {
            lastToggleMacroKeyTick = tickCounter;
            FarmHelperFabric.getMacroController().toggle();
            if (!FarmHelperFabric.getMacroController().isToggled()) {
                requestGlobalStop();
            } else {
                clearGlobalStopLatch();
            }
            FarmHelperFabric.getWebhookService().debugTrace("keybind", "toggle macro");
            Chat.info("Macro " + (FarmHelperFabric.getMacroController().isToggled() ? "enabled" : "disabled"));
        }
        if (consumeDebounced(startMacroKey, lastStartMacroKeyTick)) {
            lastStartMacroKeyTick = tickCounter;
            clearGlobalStopLatch();
            FarmHelperFabric.getMacroController().enable();
            FarmHelperFabric.getWebhookService().debugTrace("keybind", "start macro");
            Chat.info("Macro enabled");
        }
        if (consumeDebounced(stopMacroKey, lastStopMacroKeyTick)) {
            lastStopMacroKeyTick = tickCounter;
            requestGlobalStop();
            FarmHelperFabric.getMacroController().disableByUser();
            FarmHelperFabric.getFeatureManager().cancelMacroExclusiveActions("manual stop keybind");
            automationExecutor.cancelAll(client, "manual stop keybind");
            FarmHelperFabric.getClientActionQueue().clear();
            FarmHelperFabric.getWebhookService().debugTrace("keybind", "stop macro");
            Chat.info("Macro disabled");
        }

        if (consumeDebounced(triggerFailsafeKey, lastManualFailsafeKeyTick)) {
            lastManualFailsafeKeyTick = tickCounter;
            FarmHelperFabric.getFailsafeManager().trigger(
                    FailsafeType.MANUAL_TEST,
                    "Manual keybind test"
            );
            Chat.info("Manual failsafe trigger queued");
        }

        if (consumeDebounced(freelookKey, lastFreelookKeyTick)) {
            lastFreelookKeyTick = tickCounter;
            toggleFeatureFromKeybind("freelook");
        }

        if (consumeDebounced(toggleUngrabMouseKey, lastToggleUngrabMouseKeyTick)) {
            lastToggleUngrabMouseKeyTick = tickCounter;
            toggleFeatureFromKeybind("ungrab_mouse");
            FarmHelperFabric.getClientActionQueue().enqueueSetMouseUngrab(
                    FarmHelperFabric.getConfigManager().getConfig().autoUngrabMouse,
                    tickCounter
            );
        }

        if (consumeDebounced(plotCleaningHelperKey, lastPlotCleaningHelperKeyTick)) {
            lastPlotCleaningHelperKeyTick = tickCounter;
            toggleFeatureFromKeybind("plot_cleaning_helper");
        }

        if (consumeDebounced(triggerPestsDestroyerKey, lastTriggerPestsDestroyerKeyTick)) {
            lastTriggerPestsDestroyerKeyTick = tickCounter;
            triggerPestsDestroyerManually();
        }

        if (consumeDebounced(tpToInfestedPlotKey, lastTpToInfestedPlotKeyTick)) {
            lastTpToInfestedPlotKeyTick = tickCounter;
            tpToInfestedPlot();
        }

        if (consumeDebounced(cancelFailsafeKey, lastCancelFailsafeKeyTick)) {
            lastCancelFailsafeKeyTick = tickCounter;
            boolean cancelled = FarmHelperFabric.getFailsafeManager().cancelFailsafeAndResumeMacro();
            if (cancelled) {
                Chat.info("Failsafe cancelled");
                FarmHelperFabric.getWebhookService().debugTrace("keybind", "cancel failsafe");
            } else {
                Chat.info("No active failsafe");
            }
        }

        if (consumeDebounced(openMenuKey, lastOpenMenuKeyTick)) {
            lastOpenMenuKeyTick = tickCounter;
            client.setScreen(createConfigScreen(client.currentScreen));
        }

        boolean macroToggled = FarmHelperFabric.getMacroController().isToggled();
        if (!macroToggled && previousMacroToggled) {
            MOVEMENT_MACRO_EXECUTOR.reset(client);
            automationExecutor.cancelAll(client, "macro disabled");
            FarmHelperFabric.getFeatureManager().cancelMacroExclusiveActions("macro disabled");
            FarmHelperFabric.getClientActionQueue().clear();
        }
        previousMacroToggled = macroToggled;

        updateRuntimeSnapshot(client);
        FarmHelperFabric.getFailsafeManager().setRuntime(RUNTIME_SNAPSHOT);
        FarmHelperFabric.getFailsafeManager().tick();
        boolean failsafeActiveNow = FarmHelperFabric.getFailsafeManager().hasActiveFailsafe();
        if (failsafeActiveNow && !failsafeActiveLastTick) {
            AudioManager.getInstance().playFailsafeAlert();
            String activeFailsafe = FarmHelperFabric.getFailsafeManager().getActiveFailsafe()
                    .map(Enum::name)
                    .orElse("UNKNOWN");
            DesktopNotifier.notifyFailsafe(activeFailsafe, FarmHelperFabric.getFailsafeManager().getActiveReason());
            if (FarmHelperFabric.getConfigManager().getConfig().autoAltTab) {
                FailsafeUtils.bringWindowToFront();
            }
        } else if (!failsafeActiveNow && failsafeActiveLastTick) {
            AudioManager.getInstance().stop();
        }
        failsafeActiveLastTick = failsafeActiveNow;
        FarmHelperFabric.getMacroController().tick();
        MOVEMENT_MACRO_EXECUTOR.tick(client);
        tickMacroRecovery(client);
        updateFeatureRuntimeState();
        FarmHelperFabric.getFeatureManager().tickEnabledFeatures(FEATURE_RUNTIME_STATE);
        processClientActionQueue(client);
        tickPestEntityRendering(client);
        tickAutoReconnect(client);
        tickWebhookStatusUpdates();
        FarmHelperFabric.getWebhookService().tickDebug(
                RUNTIME_SNAPSHOT,
                FEATURE_RUNTIME_STATE,
                FarmHelperFabric.getConfigManager().getConfig(),
                FarmHelperFabric.getClientActionQueue().size()
        );
        AudioManager.getInstance().tick(tickCounter);
    }

    public static GameStateTracker getGameStateTracker() {
        return GAME_STATE_TRACKER;
    }

    public static MovementMacroExecutor getMovementMacroExecutor() {
        return MOVEMENT_MACRO_EXECUTOR;
    }

    public static GameStateHandler getGameStateHandler() {
        return GAME_STATE_HANDLER;
    }

    public static RotationHandler getRotationHandler() {
        return ROTATION_HANDLER;
    }

    public static RuntimeSnapshot getRuntimeSnapshot() {
        return RUNTIME_SNAPSHOT.copy();
    }

    public static FeatureRuntimeState getFeatureRuntimeState() {
        FeatureRuntimeState copy = new FeatureRuntimeState();
        copy.tickCount = FEATURE_RUNTIME_STATE.tickCount;
        copy.inWorld = FEATURE_RUNTIME_STATE.inWorld;
        copy.macroToggled = FEATURE_RUNTIME_STATE.macroToggled;
        copy.macroState = FEATURE_RUNTIME_STATE.macroState;
        copy.macroRuntimeTicks = FEATURE_RUNTIME_STATE.macroRuntimeTicks;
        copy.stationaryTicks = FEATURE_RUNTIME_STATE.stationaryTicks;
        copy.movedDistance = FEATURE_RUNTIME_STATE.movedDistance;
        copy.horizontalSpeedBps = FEATURE_RUNTIME_STATE.horizontalSpeedBps;
        copy.posX = FEATURE_RUNTIME_STATE.posX;
        copy.posY = FEATURE_RUNTIME_STATE.posY;
        copy.posZ = FEATURE_RUNTIME_STATE.posZ;
        copy.yaw = FEATURE_RUNTIME_STATE.yaw;
        copy.pitch = FEATURE_RUNTIME_STATE.pitch;
        copy.screenOpen = FEATURE_RUNTIME_STATE.screenOpen;
        copy.screenTitle = FEATURE_RUNTIME_STATE.screenTitle;
        copy.inventoryFillPercent = FEATURE_RUNTIME_STATE.inventoryFillPercent;
        copy.visitorOffer = FEATURE_RUNTIME_STATE.visitorOffer.copy();
        copy.millisSinceWorldTimePacket = FEATURE_RUNTIME_STATE.millisSinceWorldTimePacket;
        copy.estimatedServerTps = FEATURE_RUNTIME_STATE.estimatedServerTps;
        copy.networkLagging = FEATURE_RUNTIME_STATE.networkLagging;
        copy.nearSpawnPoint = FEATURE_RUNTIME_STATE.nearSpawnPoint;
        copy.nearRewarpPoint = FEATURE_RUNTIME_STATE.nearRewarpPoint;
        copy.location = FEATURE_RUNTIME_STATE.location;
        copy.currentPlot = FEATURE_RUNTIME_STATE.currentPlot;
        copy.mostInfestedPlot = FEATURE_RUNTIME_STATE.mostInfestedPlot;
        copy.guiInfestedPlot = FEATURE_RUNTIME_STATE.guiInfestedPlot;
        copy.guiInfestedPests = FEATURE_RUNTIME_STATE.guiInfestedPests;
        copy.pestsInTablist = FEATURE_RUNTIME_STATE.pestsInTablist;
        copy.allowFlying = FEATURE_RUNTIME_STATE.allowFlying;
        copy.flying = FEATURE_RUNTIME_STATE.flying;
        copy.onGround = FEATURE_RUNTIME_STATE.onGround;
        copy.aboveHeadClear = FEATURE_RUNTIME_STATE.aboveHeadClear;
        copy.canFlyHigher = FEATURE_RUNTIME_STATE.canFlyHigher;
        copy.playerSuffocating = FEATURE_RUNTIME_STATE.playerSuffocating;
        copy.automationBusy = FEATURE_RUNTIME_STATE.automationBusy;
        copy.jacobContestActive = FEATURE_RUNTIME_STATE.jacobContestActive;
        copy.godPotionActive = FEATURE_RUNTIME_STATE.godPotionActive;
        copy.cookieBuffActive = FEATURE_RUNTIME_STATE.cookieBuffActive;
        copy.pestRepellentActive = FEATURE_RUNTIME_STATE.pestRepellentActive;
        copy.vacuumRange = FEATURE_RUNTIME_STATE.vacuumRange;
        copy.vacuumDps = FEATURE_RUNTIME_STATE.vacuumDps;
        copy.vacuumTrackerCooldownSeconds = FEATURE_RUNTIME_STATE.vacuumTrackerCooldownSeconds;
        copy.purse = FEATURE_RUNTIME_STATE.purse;
        copy.bits = FEATURE_RUNTIME_STATE.bits;
        copy.copper = FEATURE_RUNTIME_STATE.copper;
        copy.activeFailsafe = FEATURE_RUNTIME_STATE.activeFailsafe;
        return copy;
    }

    public static Screen createConfigScreen(Screen parent) {
        return new com.jelly.farmhelper.fabric.ui.FarmHelperModernConfigScreen(parent);
    }

    public static void requestGlobalStop() {
        forceStopRequested = true;
        RuntimeGuards.latchGlobalStop();
    }

    public static void clearGlobalStopLatch() {
        RuntimeGuards.clearGlobalStopLatch();
    }

    public static boolean isGlobalStopLatched() {
        return RuntimeGuards.isGlobalStopLatched();
    }

    private boolean consumeDebounced(KeyBinding key, long lastAcceptedTick) {
        boolean pressed = false;
        while (key.wasPressed()) {
            pressed = true;
        }
        if (!pressed) {
            return false;
        }
        return tickCounter - lastAcceptedTick >= KEYBIND_DEBOUNCE_TICKS;
    }

    private void toggleFeatureFromKeybind(String featureId) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        boolean enabled = !config.featureToggles.getOrDefault(featureId, false);
        config.featureToggles.put(featureId, enabled);
        FarmHelperFabric.getFeatureManager().setFeatureEnabled(featureId, enabled);
        syncConfigFlagForFeature(featureId, enabled);
        applyImmediateFeatureState(featureId, enabled);
        FarmHelperFabric.getConfigManager().save();
        Chat.info(featureDisplayName(featureId) + " " + (enabled ? "enabled" : "disabled"));
        FarmHelperFabric.getWebhookService().debugTrace("keybind", "toggle feature " + featureId + "=" + enabled);
    }

    private void syncConfigFlagForFeature(String featureId, boolean enabled) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        switch (featureId) {
            case "freelook" -> config.freelook = enabled;
            case "ungrab_mouse" -> config.autoUngrabMouse = enabled;
            case "plot_cleaning_helper" -> config.plotCleaningHelper = enabled;
            case "pests_destroyer" -> config.enablePestsDestroyer = enabled;
            default -> {
            }
        }
    }

    private String featureDisplayName(String featureId) {
        return switch (featureId) {
            case "freelook" -> "Freelook";
            case "ungrab_mouse" -> "Ungrab Mouse";
            case "plot_cleaning_helper" -> "Plot Cleaning Helper";
            case "pests_destroyer" -> "Pests Destroyer";
            default -> featureId;
        };
    }

    private void applyImmediateFeatureState(String featureId, boolean enabled) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        if ("freelook".equals(featureId)) {
            boolean canFreelookNow = enabled && client.player != null && client.world != null;
            FreelookController.getInstance().setEnabled(client, canFreelookNow);
        }
    }

    private void triggerPestsDestroyerManually() {
        MinecraftClient client = MinecraftClient.getInstance();
        automationExecutor.cancelAll(client, "manual pests destroyer trigger");
        FarmHelperFabric.getClientActionQueue().clear();
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        config.enablePestsDestroyer = true;
        config.featureToggles.put("pests_destroyer", true);
        FarmHelperFabric.getFeatureManager().setFeatureEnabled("pests_destroyer", true);
        FarmHelperFabric.getFeatureManager().get("pests_destroyer")
                .filter(PestsDestroyerFeatureModule.class::isInstance)
                .map(PestsDestroyerFeatureModule.class::cast)
                .ifPresent(PestsDestroyerFeatureModule::requestManualTrigger);
        clearGlobalStopLatch();
        FarmHelperFabric.getConfigManager().save();
        Chat.info("Pests Destroyer trigger requested");
        FarmHelperFabric.getWebhookService().debugTrace("keybind", "trigger pests destroyer");
    }

    private void tpToInfestedPlot() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.getNetworkHandler() == null) {
            Chat.info("Not in world");
            return;
        }
        Integer plot = findInfestedPlotInTablist(client);
        if (plot == null) {
            Chat.info("No infested plot detected in tab list");
            return;
        }
        String command = "/plottp " + plot;
        FarmHelperFabric.getClientActionQueue().enqueueCommand(command, tickCounter);
        FarmHelperFabric.getWebhookService().debugTrace("keybind", "tp to infested plot " + plot);
        Chat.info("Teleporting to infested plot " + plot);
    }

    private Integer findInfestedPlotInTablist(MinecraftClient client) {
        var parsed = PlotUtils.mostInfestedPlot(TablistUtils.getTabList()).orElse(null);
        if (parsed != null && parsed.plotNumber() > 0 && parsed.pestCount() > 0) {
            return parsed.plotNumber();
        }

        int bestPlot = -1;
        int bestPests = 0;
        for (PlayerListEntry entry : client.getNetworkHandler().getListedPlayerListEntries()) {
            Text displayName = entry.getDisplayName();
            if (displayName == null) {
                continue;
            }
            String text = displayName.getString().toLowerCase(Locale.ROOT);
            Matcher matcher = INFESTED_PLOT_PATTERN.matcher(text);
            if (!matcher.find()) {
                continue;
            }
            int plot;
            int pests;
            try {
                plot = Integer.parseInt(matcher.group(1));
                pests = Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException ex) {
                continue;
            }
            if (pests <= 0) {
                continue;
            }
            if (pests > bestPests) {
                bestPests = pests;
                bestPlot = plot;
            }
        }
        return bestPlot > 0 ? bestPlot : null;
    }

    private GuiInfestedPlot findInfestedPlotInOpenGui(MinecraftClient client) {
        if (client == null || client.player == null || client.currentScreen == null || client.player.currentScreenHandler == null) {
            return null;
        }
        String title = client.currentScreen.getTitle() == null
                ? ""
                : cleanFormatting(client.currentScreen.getTitle().getString()).toLowerCase(Locale.ROOT);
        if (!(title.contains("desk") || title.contains("plot") || title.contains("infested"))) {
            return null;
        }
        boolean configurePlotsScreen = title.contains("configure plots");

        int bestPlot = -1;
        int bestPests = 0;
        int configureCardIndex = 0;
        String bestNoMatchSample = null;
        for (Slot slot : client.player.currentScreenHandler.slots) {
            if (slot == null || !slot.hasStack()) {
                continue;
            }
            if (slot.inventory == client.player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            String cleanName = cleanFormatting(stack.getName().getString()).toLowerCase(Locale.ROOT);
            StringBuilder combinedBuilder = new StringBuilder(cleanName).append(' ');
            for (String loreLine : InventoryUtils.getItemLore(stack)) {
                combinedBuilder.append(cleanFormatting(loreLine).toLowerCase(Locale.ROOT)).append(' ');
            }
            // Direct 1.21 tooltip API — the most reliable way to get all visible item text.
            try {
                for (Text tooltipLine : stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.BASIC)) {
                    combinedBuilder.append(cleanFormatting(tooltipLine.getString()).toLowerCase(Locale.ROOT)).append(' ');
                }
            } catch (Throwable ignored) {
                // Fallback to extractItemMetadata if direct tooltip fails.
                String metadata = GUI_DECISION_ENGINE.extractItemMetadata(stack, client.player);
                if (metadata != null && !metadata.isBlank()) {
                    combinedBuilder.append(cleanFormatting(metadata).toLowerCase(Locale.ROOT)).append(' ');
                }
            }
            String combined = combinedBuilder.toString();

            boolean looksLikePlotCard = cleanName.contains("plot")
                    || cleanName.contains("barn")
                    || combined.contains("plot")
                    || combined.contains("barn");
            if (!looksLikePlotCard && !combined.contains("pest") && !combined.contains("infested")) {
                continue;
            }

            int plot = -1;
            Matcher plotMatcher = GUI_PLOT_PATTERN.matcher(combined);
            if (plotMatcher.find()) {
                try {
                    plot = Integer.parseInt(plotMatcher.group(1));
                } catch (NumberFormatException ignored) {
                    plot = -1;
                }
            } else if (configurePlotsScreen && looksLikePlotCard && configureCardIndex < CONFIGURE_PLOT_ORDER.length) {
                // In Configure Plots, cards are in a stable order even when names are custom.
                plot = CONFIGURE_PLOT_ORDER[configureCardIndex];
            }
            if (configurePlotsScreen && looksLikePlotCard) {
                configureCardIndex++;
            }

            if (plot < 1 || plot > 24) {
                continue;
            }

            int pests = 0;
            Matcher pestsMatcher = GUI_PEST_PATTERN.matcher(combined);
            while (pestsMatcher.find()) {
                try {
                    pests = Math.max(pests, Integer.parseInt(pestsMatcher.group(1)));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed number and keep scanning this item.
                }
            }
            if (pests <= 0 && combined.contains("infested")) {
                pests = 1;
            }
            if (pests <= 0) {
                if (configurePlotsScreen && bestNoMatchSample == null) {
                    bestNoMatchSample = combined.length() > 220 ? combined.substring(0, 220) + "..." : combined;
                }
                continue;
            }

            if (pests > bestPests) {
                bestPests = pests;
                bestPlot = plot;
            }
        }
        if (configurePlotsScreen && bestPlot <= 0 && tickCounter - lastGuiNoMatchDebugTick >= 40L) {
            lastGuiNoMatchDebugTick = tickCounter;
            // Dump the first few slot summaries so the debug log reveals what the screen actually contains.
            StringBuilder slotDump = new StringBuilder();
            int dumpCount = 0;
            for (Slot slot : client.player.currentScreenHandler.slots) {
                if (slot == null || !slot.hasStack() || slot.inventory == client.player.getInventory()) continue;
                ItemStack dumpStack = slot.getStack();
                if (dumpStack == null || dumpStack.isEmpty()) continue;
                if (dumpCount < 6) {
                    String dumpName = cleanFormatting(dumpStack.getName().getString());
                    int loreLineCount = InventoryUtils.getItemLore(dumpStack).size();
                    slotDump.append(" [slot").append(slot.id).append(" name=\"").append(dumpName)
                            .append("\" loreLines=").append(loreLineCount).append("]");
                }
                dumpCount++;
            }
            FarmHelperFabric.getWebhookService().debugTrace(
                    "runtime",
                    "configure plots scan found no infested card; totalSlots=" + dumpCount
                            + " slots:" + slotDump
                            + " sample=\"" + (bestNoMatchSample == null ? "none" : bestNoMatchSample) + "\""
            );
        }
        return bestPlot > 0 ? new GuiInfestedPlot(bestPlot, bestPests) : null;
    }

    private String cleanFormatting(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("§.", "").trim();
    }

    private void tickWebhookStatusUpdates() {
        if (!FarmHelperFabric.getConfigManager().getConfig().sendStatusUpdates) {
            statusUpdateTicks = 0;
            return;
        }
        if (!FarmHelperFabric.getMacroController().isToggled()) {
            statusUpdateTicks = 0;
            return;
        }

        statusUpdateTicks++;
        int intervalTicks = Math.max(1, FarmHelperFabric.getConfigManager().getConfig().statusUpdateIntervalMinutes) * 60 * 20;
        if (statusUpdateTicks >= intervalTicks) {
            statusUpdateTicks = 0;
            String failsafe = FarmHelperFabric.getFailsafeManager().getActiveFailsafe().map(Enum::name).orElse("NONE");
            FarmHelperFabric.getWebhookService().sendStatusUpdate(
                    FarmHelperFabric.getMacroController().getState(),
                    FarmHelperFabric.getMacroController().getRuntimeTicks(),
                    GAME_STATE_TRACKER.getStationaryTicks(),
                    failsafe
            );
        }
    }

    private void updateRuntimeSnapshot(MinecraftClient client) {
        RUNTIME_SNAPSHOT.tickCount = tickCounter;
        RUNTIME_SNAPSHOT.macroToggled = FarmHelperFabric.getMacroController().isToggled();
        RUNTIME_SNAPSHOT.macroState = FarmHelperFabric.getMacroController().getState();
        RUNTIME_SNAPSHOT.macroRuntimeTicks = FarmHelperFabric.getMacroController().getRuntimeTicks();
        RUNTIME_SNAPSHOT.stationaryTicks = GAME_STATE_TRACKER.getStationaryTicks();

        if (client.player == null || client.world == null) {
            RUNTIME_SNAPSHOT.inWorld = false;
            RUNTIME_SNAPSHOT.movedDistance = 0;
            RUNTIME_SNAPSHOT.horizontalSpeedBps = 0;
            RUNTIME_SNAPSHOT.velocityX = 0;
            RUNTIME_SNAPSHOT.verticalVelocity = 0;
            RUNTIME_SNAPSHOT.velocityZ = 0;
            RUNTIME_SNAPSHOT.posX = 0;
            RUNTIME_SNAPSHOT.posY = 0;
            RUNTIME_SNAPSHOT.posZ = 0;
            RUNTIME_SNAPSHOT.yaw = 0;
            RUNTIME_SNAPSHOT.pitch = 0;
            RUNTIME_SNAPSHOT.yawDelta = 0;
            RUNTIME_SNAPSHOT.pitchDelta = 0;
            RUNTIME_SNAPSHOT.screenOpen = false;
            RUNTIME_SNAPSHOT.screenTitle = "";
            RUNTIME_SNAPSHOT.inventoryFillPercent = 0;
            RUNTIME_SNAPSHOT.visitorOffer.clear();
            RUNTIME_SNAPSHOT.selectedSlotChanges = 0;
            RUNTIME_SNAPSHOT.nearCobweb = false;
            RUNTIME_SNAPSHOT.nearDirt = false;
            RUNTIME_SNAPSHOT.nearBedrock = false;
            RUNTIME_SNAPSHOT.bedrockCount = 0;
            RUNTIME_SNAPSHOT.dirtOnLeft = false;
            RUNTIME_SNAPSHOT.dirtOnRight = false;
            RUNTIME_SNAPSHOT.bedrockOnLeft = false;
            RUNTIME_SNAPSHOT.bedrockOnRight = false;
            RUNTIME_SNAPSHOT.hasBadEffects = false;
            RUNTIME_SNAPSHOT.poisonActive = false;
            RUNTIME_SNAPSHOT.witherActive = false;
            RUNTIME_SNAPSHOT.blindnessActive = false;
            RUNTIME_SNAPSHOT.nauseaActive = false;
            RUNTIME_SNAPSHOT.darknessActive = false;
            RUNTIME_SNAPSHOT.miningFatigueActive = false;
            RUNTIME_SNAPSHOT.hungerActive = false;
            RUNTIME_SNAPSHOT.slownessActive = false;
            RUNTIME_SNAPSHOT.weaknessActive = false;
            RUNTIME_SNAPSHOT.burning = false;
            RUNTIME_SNAPSHOT.jumpBoostActive = false;
            RUNTIME_SNAPSHOT.allowFlying = false;
            RUNTIME_SNAPSHOT.flying = false;
            RUNTIME_SNAPSHOT.onGround = false;
            RUNTIME_SNAPSHOT.aboveHeadClear = false;
            RUNTIME_SNAPSHOT.canFlyHigher = false;
            RUNTIME_SNAPSHOT.playerSuffocating = false;
            RUNTIME_SNAPSHOT.automationBusy = false;
            RUNTIME_SNAPSHOT.movementRecordingPlaying = false;
            RUNTIME_SNAPSHOT.nearSpawnPoint = false;
            RUNTIME_SNAPSHOT.nearRewarpPoint = false;
            RUNTIME_SNAPSHOT.location = "";
            RUNTIME_SNAPSHOT.currentPlot = -1;
            RUNTIME_SNAPSHOT.mostInfestedPlot = -1;
            RUNTIME_SNAPSHOT.guiInfestedPlot = -1;
            RUNTIME_SNAPSHOT.guiInfestedPests = 0;
            RUNTIME_SNAPSHOT.pestsInTablist = 0;
            RUNTIME_SNAPSHOT.jacobContestActive = false;
            RUNTIME_SNAPSHOT.godPotionActive = false;
            RUNTIME_SNAPSHOT.cookieBuffActive = false;
            RUNTIME_SNAPSHOT.pestRepellentActive = false;
            RUNTIME_SNAPSHOT.vacuumRange = 0;
            RUNTIME_SNAPSHOT.vacuumDps = 0;
            RUNTIME_SNAPSHOT.vacuumTrackerCooldownSeconds = 1.0;
            RUNTIME_SNAPSHOT.purse = 0;
            RUNTIME_SNAPSHOT.bits = 0;
            RUNTIME_SNAPSHOT.copper = 0;
            RUNTIME_SNAPSHOT.millisSinceWorldTimePacket = FarmHelperFabric.getFailsafeManager().getMillisSinceWorldTimePacket();
            RUNTIME_SNAPSHOT.estimatedServerTps = FarmHelperFabric.getFailsafeManager().getEstimatedServerTps();
            RUNTIME_SNAPSHOT.networkLagging = FarmHelperFabric.getFailsafeManager().isNetworkLagging();
            lastPlayerPos = null;
            lastSelectedSlot = -1;
            selectedSlotChanges = 0;
            return;
        }

        ClientPlayerEntity player = client.player;
        RUNTIME_SNAPSHOT.inWorld = true;

        Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        RUNTIME_SNAPSHOT.posX = currentPos.x;
        RUNTIME_SNAPSHOT.posY = currentPos.y;
        RUNTIME_SNAPSHOT.posZ = currentPos.z;
        RUNTIME_SNAPSHOT.movedDistance = lastPlayerPos == null ? 0 : currentPos.distanceTo(lastPlayerPos);
        RUNTIME_SNAPSHOT.horizontalSpeedBps = RUNTIME_SNAPSHOT.movedDistance * 20.0;
        lastPlayerPos = currentPos;

        float yaw = player.getYaw();
        float pitch = player.getPitch();
        RUNTIME_SNAPSHOT.yaw = yaw;
        RUNTIME_SNAPSHOT.pitch = pitch;
        RUNTIME_SNAPSHOT.yawDelta = MathHelper.wrapDegrees(yaw - lastYaw);
        RUNTIME_SNAPSHOT.pitchDelta = MathHelper.wrapDegrees(pitch - lastPitch);
        lastYaw = yaw;
        lastPitch = pitch;

        RUNTIME_SNAPSHOT.velocityX = player.getVelocity().x;
        RUNTIME_SNAPSHOT.verticalVelocity = player.getVelocity().y;
        RUNTIME_SNAPSHOT.velocityZ = player.getVelocity().z;
        RUNTIME_SNAPSHOT.screenOpen = client.currentScreen != null;
        RUNTIME_SNAPSHOT.screenTitle = client.currentScreen == null
                ? ""
                : client.currentScreen.getTitle().getString();
        populateVisitorOfferSnapshot(client, player);
        GuiInfestedPlot guiInfestedPlot = findInfestedPlotInOpenGui(client);
        RUNTIME_SNAPSHOT.guiInfestedPlot = guiInfestedPlot == null ? -1 : guiInfestedPlot.plot();
        RUNTIME_SNAPSHOT.guiInfestedPests = guiInfestedPlot == null ? 0 : guiInfestedPlot.pests();
        if (RUNTIME_SNAPSHOT.guiInfestedPlot != lastLoggedGuiInfestedPlot
                || RUNTIME_SNAPSHOT.guiInfestedPests != lastLoggedGuiInfestedPests) {
            if (RUNTIME_SNAPSHOT.guiInfestedPlot > 0 && RUNTIME_SNAPSHOT.guiInfestedPests > 0) {
                FarmHelperFabric.getWebhookService().debugTrace(
                        "runtime",
                        "gui infested plot detected plot=" + RUNTIME_SNAPSHOT.guiInfestedPlot
                                + " pests=" + RUNTIME_SNAPSHOT.guiInfestedPests
                                + " screen=\"" + RUNTIME_SNAPSHOT.screenTitle + "\""
                );
            }
            lastLoggedGuiInfestedPlot = RUNTIME_SNAPSHOT.guiInfestedPlot;
            lastLoggedGuiInfestedPests = RUNTIME_SNAPSHOT.guiInfestedPests;
        }
        RUNTIME_SNAPSHOT.inventoryFillPercent = calculateInventoryFillPercent(player);
        RUNTIME_SNAPSHOT.selectedSlotChanges = trackSelectedSlotChanges(player);
        RUNTIME_SNAPSHOT.nearCobweb = hasNearbyBlock(client, player.getBlockPos(), Blocks.COBWEB);
        RUNTIME_SNAPSHOT.nearDirt = hasNearbyBlock(client, player.getBlockPos(), Blocks.DIRT);
        RUNTIME_SNAPSHOT.nearBedrock = hasNearbyBlock(client, player.getBlockPos(), Blocks.BEDROCK);
        RUNTIME_SNAPSHOT.bedrockCount = countBedrockArea(client, player);
        RUNTIME_SNAPSHOT.dirtOnLeft = hasRelativeBlock(client, player, -1, 1, 0, Blocks.DIRT);
        RUNTIME_SNAPSHOT.dirtOnRight = hasRelativeBlock(client, player, 1, 1, 0, Blocks.DIRT);
        RUNTIME_SNAPSHOT.bedrockOnLeft = hasRelativeBlock(client, player, -1, 1, 0, Blocks.BEDROCK);
        RUNTIME_SNAPSHOT.bedrockOnRight = hasRelativeBlock(client, player, 1, 1, 0, Blocks.BEDROCK);
        RUNTIME_SNAPSHOT.poisonActive = player.hasStatusEffect(StatusEffects.POISON);
        RUNTIME_SNAPSHOT.witherActive = player.hasStatusEffect(StatusEffects.WITHER);
        RUNTIME_SNAPSHOT.blindnessActive = player.hasStatusEffect(StatusEffects.BLINDNESS);
        RUNTIME_SNAPSHOT.nauseaActive = player.hasStatusEffect(StatusEffects.NAUSEA);
        RUNTIME_SNAPSHOT.darknessActive = player.hasStatusEffect(StatusEffects.DARKNESS);
        RUNTIME_SNAPSHOT.miningFatigueActive = player.hasStatusEffect(StatusEffects.MINING_FATIGUE);
        RUNTIME_SNAPSHOT.hungerActive = player.hasStatusEffect(StatusEffects.HUNGER);
        RUNTIME_SNAPSHOT.slownessActive = player.hasStatusEffect(StatusEffects.SLOWNESS);
        RUNTIME_SNAPSHOT.weaknessActive = player.hasStatusEffect(StatusEffects.WEAKNESS);
        RUNTIME_SNAPSHOT.burning = player.isOnFire();
        RUNTIME_SNAPSHOT.jumpBoostActive = player.hasStatusEffect(StatusEffects.JUMP_BOOST);
        RUNTIME_SNAPSHOT.allowFlying = player.getAbilities().allowFlying;
        RUNTIME_SNAPSHOT.flying = player.getAbilities().flying;
        RUNTIME_SNAPSHOT.onGround = player.isOnGround();
        RUNTIME_SNAPSHOT.aboveHeadClear = isAboveHeadClear(client, player);
        RUNTIME_SNAPSHOT.canFlyHigher = BlockUtils.isAboveHeadClear(client, player, 5);
        RUNTIME_SNAPSHOT.playerSuffocating = PlayerUtils.isPlayerSuffocating(client);
        RUNTIME_SNAPSHOT.automationBusy = automationExecutor.isBusy();
        RUNTIME_SNAPSHOT.hasBadEffects = RUNTIME_SNAPSHOT.poisonActive
                || RUNTIME_SNAPSHOT.witherActive
                || RUNTIME_SNAPSHOT.blindnessActive
                || RUNTIME_SNAPSHOT.nauseaActive
                || RUNTIME_SNAPSHOT.darknessActive
                || RUNTIME_SNAPSHOT.miningFatigueActive
                || RUNTIME_SNAPSHOT.hungerActive
                || RUNTIME_SNAPSHOT.slownessActive
                || RUNTIME_SNAPSHOT.weaknessActive
                || RUNTIME_SNAPSHOT.burning;
        RUNTIME_SNAPSHOT.movementRecordingPlaying = automationExecutor.isMovementRecordingPlaying();
        RUNTIME_SNAPSHOT.nearSpawnPoint = isNearConfiguredSpawn(client, FarmHelperFabric.getConfigManager().getConfig());
        RUNTIME_SNAPSHOT.nearRewarpPoint = isNearAnyRewarpPoint(client, FarmHelperFabric.getConfigManager().getConfig());
        RUNTIME_SNAPSHOT.location = GAME_STATE_HANDLER.getLocation().name();
        RUNTIME_SNAPSHOT.currentPlot = GAME_STATE_HANDLER.getCurrentPlot();
        RUNTIME_SNAPSHOT.mostInfestedPlot = GAME_STATE_HANDLER.getMostInfestedPlot();
        RUNTIME_SNAPSHOT.pestsInTablist = GAME_STATE_HANDLER.getTotalPests();
        if (RUNTIME_SNAPSHOT.guiInfestedPlot > 0 && RUNTIME_SNAPSHOT.mostInfestedPlot <= 0) {
            RUNTIME_SNAPSHOT.mostInfestedPlot = RUNTIME_SNAPSHOT.guiInfestedPlot;
        }
        if (RUNTIME_SNAPSHOT.guiInfestedPests > 0 && RUNTIME_SNAPSHOT.pestsInTablist <= 0) {
            RUNTIME_SNAPSHOT.pestsInTablist = RUNTIME_SNAPSHOT.guiInfestedPests;
        }
        RUNTIME_SNAPSHOT.jacobContestActive = GAME_STATE_HANDLER.isJacobContestActive();
        RUNTIME_SNAPSHOT.godPotionActive = GAME_STATE_HANDLER.isGodPotionActive();
        RUNTIME_SNAPSHOT.cookieBuffActive = GAME_STATE_HANDLER.isCookieBuffActive();
        RUNTIME_SNAPSHOT.pestRepellentActive = GAME_STATE_HANDLER.isPestRepellentActive();
        PlayerUtils.VacuumStats vacuumStats = PlayerUtils.resolveBestVacuumStats(client);
        RUNTIME_SNAPSHOT.vacuumRange = vacuumStats.range();
        RUNTIME_SNAPSHOT.vacuumDps = vacuumStats.dps();
        RUNTIME_SNAPSHOT.vacuumTrackerCooldownSeconds = vacuumStats.trackerCooldownSeconds();
        RUNTIME_SNAPSHOT.purse = GAME_STATE_HANDLER.getPurse();
        RUNTIME_SNAPSHOT.bits = GAME_STATE_HANDLER.getBits();
        RUNTIME_SNAPSHOT.copper = GAME_STATE_HANDLER.getCopper();
        RUNTIME_SNAPSHOT.millisSinceWorldTimePacket = FarmHelperFabric.getFailsafeManager().getMillisSinceWorldTimePacket();
        RUNTIME_SNAPSHOT.estimatedServerTps = FarmHelperFabric.getFailsafeManager().getEstimatedServerTps();
        RUNTIME_SNAPSHOT.networkLagging = FarmHelperFabric.getFailsafeManager().isNetworkLagging();
    }

    private void updateFeatureRuntimeState() {
        FEATURE_RUNTIME_STATE.tickCount = RUNTIME_SNAPSHOT.tickCount;
        FEATURE_RUNTIME_STATE.inWorld = RUNTIME_SNAPSHOT.inWorld;
        FEATURE_RUNTIME_STATE.macroToggled = RUNTIME_SNAPSHOT.macroToggled;
        FEATURE_RUNTIME_STATE.macroState = RUNTIME_SNAPSHOT.macroState;
        FEATURE_RUNTIME_STATE.macroRuntimeTicks = RUNTIME_SNAPSHOT.macroRuntimeTicks;
        FEATURE_RUNTIME_STATE.stationaryTicks = RUNTIME_SNAPSHOT.stationaryTicks;
        FEATURE_RUNTIME_STATE.movedDistance = RUNTIME_SNAPSHOT.movedDistance;
        FEATURE_RUNTIME_STATE.horizontalSpeedBps = RUNTIME_SNAPSHOT.horizontalSpeedBps;
        FEATURE_RUNTIME_STATE.posX = RUNTIME_SNAPSHOT.posX;
        FEATURE_RUNTIME_STATE.posY = RUNTIME_SNAPSHOT.posY;
        FEATURE_RUNTIME_STATE.posZ = RUNTIME_SNAPSHOT.posZ;
        FEATURE_RUNTIME_STATE.yaw = RUNTIME_SNAPSHOT.yaw;
        FEATURE_RUNTIME_STATE.pitch = RUNTIME_SNAPSHOT.pitch;
        FEATURE_RUNTIME_STATE.screenOpen = RUNTIME_SNAPSHOT.screenOpen;
        FEATURE_RUNTIME_STATE.screenTitle = RUNTIME_SNAPSHOT.screenTitle;
        FEATURE_RUNTIME_STATE.inventoryFillPercent = RUNTIME_SNAPSHOT.inventoryFillPercent;
        FEATURE_RUNTIME_STATE.visitorOffer = RUNTIME_SNAPSHOT.visitorOffer.copy();
        FEATURE_RUNTIME_STATE.millisSinceWorldTimePacket = RUNTIME_SNAPSHOT.millisSinceWorldTimePacket;
        FEATURE_RUNTIME_STATE.estimatedServerTps = RUNTIME_SNAPSHOT.estimatedServerTps;
        FEATURE_RUNTIME_STATE.networkLagging = RUNTIME_SNAPSHOT.networkLagging;
        FEATURE_RUNTIME_STATE.nearSpawnPoint = RUNTIME_SNAPSHOT.nearSpawnPoint;
        FEATURE_RUNTIME_STATE.nearRewarpPoint = RUNTIME_SNAPSHOT.nearRewarpPoint;
        FEATURE_RUNTIME_STATE.location = RUNTIME_SNAPSHOT.location;
        FEATURE_RUNTIME_STATE.currentPlot = RUNTIME_SNAPSHOT.currentPlot;
        FEATURE_RUNTIME_STATE.mostInfestedPlot = RUNTIME_SNAPSHOT.mostInfestedPlot;
        FEATURE_RUNTIME_STATE.guiInfestedPlot = RUNTIME_SNAPSHOT.guiInfestedPlot;
        FEATURE_RUNTIME_STATE.guiInfestedPests = RUNTIME_SNAPSHOT.guiInfestedPests;
        FEATURE_RUNTIME_STATE.pestsInTablist = RUNTIME_SNAPSHOT.pestsInTablist;
        FEATURE_RUNTIME_STATE.allowFlying = RUNTIME_SNAPSHOT.allowFlying;
        FEATURE_RUNTIME_STATE.flying = RUNTIME_SNAPSHOT.flying;
        FEATURE_RUNTIME_STATE.onGround = RUNTIME_SNAPSHOT.onGround;
        FEATURE_RUNTIME_STATE.aboveHeadClear = RUNTIME_SNAPSHOT.aboveHeadClear;
        FEATURE_RUNTIME_STATE.canFlyHigher = RUNTIME_SNAPSHOT.canFlyHigher;
        FEATURE_RUNTIME_STATE.playerSuffocating = RUNTIME_SNAPSHOT.playerSuffocating;
        FEATURE_RUNTIME_STATE.automationBusy = RUNTIME_SNAPSHOT.automationBusy;
        FEATURE_RUNTIME_STATE.jacobContestActive = RUNTIME_SNAPSHOT.jacobContestActive;
        FEATURE_RUNTIME_STATE.godPotionActive = RUNTIME_SNAPSHOT.godPotionActive;
        FEATURE_RUNTIME_STATE.cookieBuffActive = RUNTIME_SNAPSHOT.cookieBuffActive;
        FEATURE_RUNTIME_STATE.pestRepellentActive = RUNTIME_SNAPSHOT.pestRepellentActive;
        FEATURE_RUNTIME_STATE.vacuumRange = RUNTIME_SNAPSHOT.vacuumRange;
        FEATURE_RUNTIME_STATE.vacuumDps = RUNTIME_SNAPSHOT.vacuumDps;
        FEATURE_RUNTIME_STATE.vacuumTrackerCooldownSeconds = RUNTIME_SNAPSHOT.vacuumTrackerCooldownSeconds;
        FEATURE_RUNTIME_STATE.purse = RUNTIME_SNAPSHOT.purse;
        FEATURE_RUNTIME_STATE.bits = RUNTIME_SNAPSHOT.bits;
        FEATURE_RUNTIME_STATE.copper = RUNTIME_SNAPSHOT.copper;
        FEATURE_RUNTIME_STATE.activeFailsafe = FarmHelperFabric.getFailsafeManager().getActiveFailsafe();
    }

    private int calculateInventoryFillPercent(ClientPlayerEntity player) {
        int total = 0;
        int used = 0;
        for (ItemStack stack : player.getInventory().getMainStacks()) {
            total++;
            if (!stack.isEmpty()) {
                used++;
            }
        }
        if (total == 0) {
            return 0;
        }
        return (int) ((used * 100.0) / total);
    }

    private void populateVisitorOfferSnapshot(MinecraftClient client, ClientPlayerEntity player) {
        VisitorOfferSnapshot snapshot = RUNTIME_SNAPSHOT.visitorOffer;
        snapshot.clear();
        if (player == null) {
            return;
        }

        for (ItemStack stack : player.getInventory().getMainStacks()) {
            if (stack == null || stack.isEmpty()) {
                snapshot.emptyInventorySlots++;
                continue;
            }
            String itemName = cleanFormatting(stack.getName().getString());
            if (itemName.isBlank()) {
                continue;
            }
            snapshot.inventoryCounts.merge(itemName, stack.getCount(), Integer::sum);
        }
        for (int slotIndex = 0; slotIndex < 9; slotIndex++) {
            ItemStack hotbarStack = player.getInventory().getStack(slotIndex);
            if (hotbarStack == null || hotbarStack.isEmpty()) {
                continue;
            }
            String itemName = cleanFormatting(hotbarStack.getName().getString()).toLowerCase(Locale.ROOT);
            if (itemName.contains("compactor")) {
                snapshot.hotbarCompactorSlots.add(slotIndex);
            }
        }

        if (client == null || client.currentScreen == null || player.currentScreenHandler == null) {
            return;
        }

        String title = cleanFormatting(client.currentScreen.getTitle() == null
                ? ""
                : client.currentScreen.getTitle().getString());
        if (!title.toLowerCase(Locale.ROOT).contains("visitor")) {
            return;
        }

        snapshot.visitorScreenOpen = true;
        snapshot.inventoryLoaded = InventoryUtils.isInventoryLoaded(client);
        snapshot.inventoryName = title;
        for (Slot slot : player.currentScreenHandler.slots) {
            if (slot == null || !slot.hasStack() || slot.inventory == player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            String displayName = cleanFormatting(stack.getName().getString());
            List<String> lore = new ArrayList<>();
            for (String loreLine : InventoryUtils.getItemLore(stack)) {
                lore.add(cleanFormatting(loreLine));
            }

            if (slot.id == 13) {
                snapshot.npcSlot = slot.id;
                snapshot.npcName = displayName;
                if (stack.getName() != null
                        && stack.getName().getStyle() != null
                        && stack.getName().getStyle().getColor() != null) {
                    snapshot.npcColorRgb = stack.getName().getStyle().getColor().getRgb();
                }
                snapshot.npcLore.clear();
                snapshot.npcLore.addAll(lore);
            }

            if (snapshot.acceptOfferSlot < 0 && displayName.toLowerCase(Locale.ROOT).contains("accept offer")) {
                snapshot.acceptOfferSlot = slot.id;
                snapshot.acceptOfferName = displayName;
                snapshot.acceptOfferLore.clear();
                snapshot.acceptOfferLore.addAll(lore);
            }
        }
    }

    private int trackSelectedSlotChanges(ClientPlayerEntity player) {
        int slot = player.getInventory().getSelectedSlot();
        if (lastSelectedSlot != -1 && lastSelectedSlot != slot) {
            selectedSlotChanges++;
        }
        lastSelectedSlot = slot;
        if (tickCounter % 20 == 0) {
            // decay every second to keep detection window bounded.
            selectedSlotChanges = Math.max(0, selectedSlotChanges - 1);
        }
        return selectedSlotChanges;
    }

    private boolean hasNearbyBlock(MinecraftClient client, BlockPos center, Block target) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockState state = client.world.getBlockState(center.add(x, y, z));
                    if (state.isOf(target)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasRelativeBlock(MinecraftClient client, ClientPlayerEntity player, int rightOffset, int yOffset, int forwardOffset, Block target) {
        if (client == null || client.world == null || player == null || target == null) {
            return false;
        }
        BlockPos pos = getRelativeBlockPos(player, rightOffset, yOffset, forwardOffset);
        return client.world.getBlockState(pos).isOf(target);
    }

    private int countBedrockArea(MinecraftClient client, ClientPlayerEntity player) {
        if (client == null || client.world == null || player == null) {
            return 0;
        }
        int count = 0;
        BlockPos base = player.getBlockPos();
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                if (client.world.getBlockState(base.add(x, 1, z)).isOf(Blocks.BEDROCK)) {
                    count++;
                }
            }
        }
        return count;
    }

    private BlockPos getRelativeBlockPos(ClientPlayerEntity player, int rightOffset, int yOffset, int forwardOffset) {
        Direction facing = Direction.fromHorizontalDegrees(player.getYaw());
        Direction right = facing.rotateYClockwise();
        int dx = right.getOffsetX() * rightOffset + facing.getOffsetX() * forwardOffset;
        int dz = right.getOffsetZ() * rightOffset + facing.getOffsetZ() * forwardOffset;
        return player.getBlockPos().add(dx, yOffset, dz);
    }

    private boolean isAboveHeadClear(MinecraftClient client, ClientPlayerEntity player) {
        if (client == null || client.world == null || player == null) {
            return false;
        }
        return client.world.isSpaceEmpty(player, player.getBoundingBox().offset(0.0, 1.0, 0.0));
    }

    private void processClientActionQueue(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) {
            return;
        }
        automationExecutor.tick(client, tickCounter);
        if (tickCounter - lastClientActionTick < 2L) {
            return;
        }
        boolean busy = automationExecutor.isBusy();
        ClientActionQueue.Action action = FarmHelperFabric.getClientActionQueue().pollFirstMatching(type ->
                !busy || !requiresActiveAutomationSlot(type)
        );
        if (action == null) {
            return;
        }
        if (automationExecutor.submit(action, client, tickCounter)) {
            lastClientActionTick = tickCounter;
        } else {
            FarmHelperFabric.getClientActionQueue().pushFront(action);
        }
    }

    private boolean requiresActiveAutomationSlot(ClientActionQueue.ActionType type) {
        return switch (type) {
            case MOVE_TO_POS,
                    MOVE_TO_ENTITY,
                    FLY_TO_POS,
                    FLY_TO_PLOT_CENTER,
                    FLY_TO_ENTITY,
                    INTERACT_NEAREST_ENTITY,
                    ATTACK_NEAREST_ENTITY,
                    WAIT_FOR_SCREEN,
                    CLICK_SLOT_MATCHING,
                    MINE_NEAREST_BLOCK,
                    ROTATE_TO -> true;
            default -> false;
        };
    }

    /**
     * Scans all world entities each tick and draws persistent hitbox outlines + tracers
     * for any entity identified as a pest by PestEntityHeuristics. This runs
     * independently of PestsDestroyer so players always see pest ESP when in the garden.
     */
    private void tickPestEntityRendering(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return;
        }
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        boolean wantTracers = config.pestsTracers;
        boolean wantBoxes = config.pestsHighlightBox;
        if (!wantTracers && !wantBoxes) {
            return;
        }
        // Only render pest ESP while in the garden.
        GameStateHandler.Location loc = GAME_STATE_HANDLER.getLocation();
        if (loc != GameStateHandler.Location.GARDEN && loc != GameStateHandler.Location.BARN) {
            return;
        }
        // Throttle to every other tick to reduce overhead.
        if (tickCounter % 2 != 0) {
            return;
        }

        int tracerColor = config.pestsTracerColor;
        int boxColor = config.pestsBoxColor;

        for (Entity entity : client.world.getEntities()) {
            if (entity == null || entity == client.player) {
                continue;
            }
            if (!pestHeuristics.isLikelyPest(entity)) {
                continue;
            }
            Vec3d pos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
            if (wantBoxes) {
                RenderUtils.drawBox(entity.getBoundingBox().expand(0.06), boxColor, 4L);
            }
            if (wantTracers) {
                Vec3d tracerTarget = pos.add(0.0, Math.max(0.3, entity.getHeight() * 0.5), 0.0);
                RenderUtils.drawTracer(tracerTarget, tracerColor, 4L);
            }
            // Draw pest name label above the entity.
            String name = entity.getName().getString();
            if (name != null && !name.isBlank()) {
                Vec3d labelPos = pos.add(0.0, entity.getHeight() + 0.3, 0.0);
                RenderUtils.drawText(labelPos, name, 0xFFE8F9FF, 4L);
            }
        }
    }

    private void tickAutoReconnect(MinecraftClient client) {
        if (client == null || client.world != null) {
            return;
        }
        if (reconnectCooldownUntilTick > 0 && tickCounter < reconnectCooldownUntilTick) {
            return;
        }
        if (lastServerAddress.isBlank()) {
            return;
        }
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.autoReconnect) {
            return;
        }

        AutoReconnectFeatureModule.ReconnectRequest request = AutoReconnectFeatureModule.pollReadyReconnectAttempt(tickCounter);
        if (request == null) {
            return;
        }
        if (!isReconnectAllowedScreen(client)) {
            AutoReconnectFeatureModule.deferReconnectAttempt(tickCounter + 20L);
            return;
        }
        if (reconnectAttempts >= Math.max(1, config.autoReconnectMaxAttempts)) {
            return;
        }

        reconnectAttempts++;
        reconnectCooldownUntilTick = tickCounter + Math.max(20L, config.autoReconnectDelaySeconds * 20L);
        try {
            ServerInfo serverInfo = new ServerInfo(lastServerName, lastServerAddress, ServerInfo.ServerType.OTHER);
            ConnectScreen.connect(
                    new MultiplayerScreen(new TitleScreen()),
                    client,
                    ServerAddress.parse(lastServerAddress),
                    serverInfo,
                    false,
                    null
            );
            Chat.info("AutoReconnect: attempting reconnect (" + reconnectAttempts + "/" + Math.max(1, config.autoReconnectMaxAttempts) + ")");
        } catch (Exception ex) {
            FarmHelperFabric.LOGGER.warn("Auto reconnect failed", ex);
        }
    }

    private boolean isReconnectAllowedScreen(MinecraftClient client) {
        if (client.currentScreen == null) {
            return false;
        }
        String title = client.currentScreen.getTitle() == null
                ? ""
                : client.currentScreen.getTitle().getString().toLowerCase();
        return title.contains("disconnected")
                || title.contains("connection lost")
                || title.contains("failed")
                || title.contains("server closed")
                || client.currentScreen instanceof TitleScreen
                || client.currentScreen instanceof MultiplayerScreen;
    }

    private void tickMacroRecovery(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            clearPendingRewarp();
            return;
        }
        if (!FarmHelperFabric.getMacroController().isToggled()
                || FarmHelperFabric.getMacroController().getState() != com.jelly.farmhelper.fabric.macro.MacroState.FARMING
                || FarmHelperFabric.getFailsafeManager().hasActiveFailsafe()) {
            clearPendingRewarp();
            return;
        }

        if (client.player.getY() < 0 && tickCounter - lastVoidRecoveryTick > 120L) {
            lastVoidRecoveryTick = tickCounter;
            FarmHelperFabric.getFailsafeManager().suppressPacketChecks(140L, 100L, 40L, "void recovery warp");
            FarmHelperFabric.getClientActionQueue().enqueueCommand("/warp garden", tickCounter);
            return;
        }

        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (isNearConfiguredSpawn(client, config)
                && GAME_STATE_TRACKER.getStationaryTicks() > 20
                && tickCounter - lastSpawnRecoveryTick > 200L) {
            lastSpawnRecoveryTick = tickCounter;
            FarmHelperFabric.getFailsafeManager().suppressPacketChecks(120L, 80L, 40L, "spawn recovery warp");
            FarmHelperFabric.getClientActionQueue().enqueueCommand("/warp garden", tickCounter);
            return;
        }

        RewarpPoint nearbyRewarpPoint = findNearbyRewarpPoint(client, config);
        boolean nearRewarpPoint = nearbyRewarpPoint != null;
        boolean enteredRewarpPoint = nearRewarpPoint && !wasNearRewarpPoint;
        wasNearRewarpPoint = nearRewarpPoint;

        if (pendingRewarpPoint != null && !isSameRewarpPoint(pendingRewarpPoint, nearbyRewarpPoint)) {
            FarmHelperFabric.getWebhookService().debugTrace("macro", "rewarp wait cancelled: player left pending rewarp area");
            clearPendingRewarp();
        }

        if (enteredRewarpPoint) {
            maybeTriggerAutoPestsAtRewarp(config);
        }

        if (pendingRewarpPoint != null) {
            KeyBindUtils.stopMovement(client, false);
            boolean stationaryEnough = GAME_STATE_TRACKER.getStationaryTicks() >= 8;
            if (tickCounter >= pendingRewarpReadyTick && stationaryEnough) {
                RewarpPoint triggerPoint = pendingRewarpPoint;
                lastRewarpTriggerTick = tickCounter;
                clearPendingRewarp();
                FarmHelperFabric.getFailsafeManager().suppressPacketChecks(140L, 100L, 40L, "rewarp point warp");
                FarmHelperFabric.getClientActionQueue().enqueueCommand("/warp garden", tickCounter);
                FarmHelperFabric.getWebhookService().debugTrace(
                        "macro",
                        "rewarp triggered at "
                                + triggerPoint.displayName(1)
                                + " (" + triggerPoint.x + "," + triggerPoint.y + "," + triggerPoint.z + ")"
                );
            }
            return;
        }

        if (nearRewarpPoint
                && tickCounter - lastRewarpTriggerTick >= rewarpDelayTicks(config)
                && (enteredRewarpPoint || GAME_STATE_TRACKER.getStationaryTicks() >= Math.max(14, config.stationaryFailsafeTicks / 2))) {
            pendingRewarpPoint = nearbyRewarpPoint;
            pendingRewarpReadyTick = tickCounter + rewarpCommandWaitTicks(config);
            KeyBindUtils.stopMovement(client, false);
            FarmHelperFabric.getWebhookService().debugTrace(
                    "macro",
                    "rewarp armed at "
                            + nearbyRewarpPoint.displayName(1)
                            + " (" + nearbyRewarpPoint.x + "," + nearbyRewarpPoint.y + "," + nearbyRewarpPoint.z + ")"
                            + " waitTicks=" + Math.max(0L, pendingRewarpReadyTick - tickCounter)
            );
            return;
        }

        if (!postJoinAligned && config.rotateAfterWarped && worldJoinTick > 0 && tickCounter - worldJoinTick <= 80L) {
            if (Math.abs(config.spawnYaw) > 0.01f || Math.abs(config.spawnPitch) > 0.01f) {
                RotationHandler.RotationConfiguration.Builder builder = new RotationHandler.RotationConfiguration.Builder()
                        .target(config.spawnYaw, config.spawnPitch)
                        .durationMs(Math.max(180L, config.rotationTimeMs))
                        .easing(RotationHandler.Easing.EASE_OUT_CUBIC)
                        .type(RotationHandler.RotationType.CLIENT)
                        .lockHeadToBody(false);
                ROTATION_HANDLER.rotate(builder.build());
            }
            postJoinAligned = true;
        }
    }

    private boolean isNearConfiguredSpawn(MinecraftClient client, FarmHelperConfig config) {
        if (config.spawnPosY <= 0) {
            return false;
        }
        BlockPos playerPos = client.player.getBlockPos();
        int dx = Math.abs(playerPos.getX() - config.spawnPosX);
        int dy = Math.abs(playerPos.getY() - config.spawnPosY);
        int dz = Math.abs(playerPos.getZ() - config.spawnPosZ);
        return dx <= 1 && dy <= 2 && dz <= 1;
    }

    private boolean isNearAnyRewarpPoint(MinecraftClient client, FarmHelperConfig config) {
        return findNearbyRewarpPoint(client, config) != null;
    }

    private RewarpPoint findNearbyRewarpPoint(MinecraftClient client, FarmHelperConfig config) {
        if (client.player == null || config.rewarpPoints == null || config.rewarpPoints.isEmpty()) {
            return null;
        }
        int radius = Math.max(1, config.rewarpActivationRadius);
        BlockPos playerPos = client.player.getBlockPos();
        RewarpPoint best = null;
        int bestDistSq = Integer.MAX_VALUE;
        int fallbackIndex = 1;
        for (RewarpPoint point : config.rewarpPoints) {
            if (point == null) {
                fallbackIndex++;
                continue;
            }
            point.normalizeInPlace(fallbackIndex++);
            int dx = Math.abs(playerPos.getX() - point.x);
            int dy = Math.abs(playerPos.getY() - point.y);
            int dz = Math.abs(playerPos.getZ() - point.z);
            if (dx <= radius && dy <= Math.max(2, radius) && dz <= radius) {
                int distSq = dx * dx + dy * dy + dz * dz;
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = point;
                }
            }
        }
        return best;
    }

    private void maybeTriggerAutoPestsAtRewarp(FarmHelperConfig config) {
        if (tickCounter - lastAutoPestsRewarpCheckTick < 20L) {
            return;
        }
        lastAutoPestsRewarpCheckTick = tickCounter;

        boolean pestsFeatureEnabled = config.enablePestsDestroyer
                || config.featureToggles.getOrDefault("pests_destroyer", false);
        if (!pestsFeatureEnabled) {
            return;
        }
        if (config.pestsDestroyerDisableDuringJacobsContest && GAME_STATE_HANDLER.isJacobContestActive()) {
            FarmHelperFabric.getWebhookService().debugTrace("feature", "auto pests skipped: Jacob contest active");
            return;
        }

        int threshold = Math.max(1, Math.min(8, config.startKillingPestsAt));
        int detectedPests = Math.max(
                Math.max(0, GAME_STATE_HANDLER.getTotalPests()),
                Math.max(Math.max(0, RUNTIME_SNAPSHOT.pestsInTablist), Math.max(0, RUNTIME_SNAPSHOT.guiInfestedPests))
        );
        FarmHelperFabric.getWebhookService().debugTrace(
                "feature",
                "auto pests rewarp check pests=" + detectedPests + " threshold=" + threshold
        );
        if (detectedPests < threshold) {
            return;
        }

        config.enablePestsDestroyer = true;
        config.featureToggles.put("pests_destroyer", true);
        FarmHelperFabric.getFeatureManager().setFeatureEnabled("pests_destroyer", true);
        FarmHelperFabric.getFeatureManager().get("pests_destroyer")
                .filter(PestsDestroyerFeatureModule.class::isInstance)
                .map(PestsDestroyerFeatureModule.class::cast)
                .ifPresent(PestsDestroyerFeatureModule::requestManualTrigger);
        clearGlobalStopLatch();
        FarmHelperFabric.getWebhookService().sendFeatureLog(
                "Auto Pests trigger queued on rewarp (" + detectedPests + "/" + threshold + ")"
        );
    }

    private long rewarpDelayTicks(FarmHelperConfig config) {
        int base = Math.max(50, config.rewarpDelayMs);
        int random = Math.max(0, config.rewarpDelayRandomnessMs);
        int value = base + (random == 0 ? 0 : (int) (Math.random() * random));
        return Math.max(20L, value / 50L);
    }

    private long rewarpCommandWaitTicks(FarmHelperConfig config) {
        return Math.max(20L, rewarpDelayTicks(config));
    }

    private boolean isSameRewarpPoint(RewarpPoint a, RewarpPoint b) {
        return a != null
                && b != null
                && a.x == b.x
                && a.y == b.y
                && a.z == b.z;
    }

    private void clearPendingRewarp() {
        pendingRewarpPoint = null;
        pendingRewarpReadyTick = -1L;
    }
}

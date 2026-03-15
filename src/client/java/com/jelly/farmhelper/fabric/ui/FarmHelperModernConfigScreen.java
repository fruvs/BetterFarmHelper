package com.jelly.farmhelper.fabric.ui;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureCatalog;
import com.jelly.farmhelper.fabric.macro.LegacyMacroType;
import com.jelly.farmhelper.fabric.macro.MacroPattern;
import com.jelly.farmhelper.fabric.ui.framework.FHScreen;
import com.jelly.farmhelper.fabric.ui.framework.UiButton;
import com.jelly.farmhelper.fabric.ui.framework.UiColumn;
import com.jelly.farmhelper.fabric.ui.framework.UiComponent;
import com.jelly.farmhelper.fabric.ui.framework.UiContainer;
import com.jelly.farmhelper.fabric.ui.framework.UiDraw;
import com.jelly.farmhelper.fabric.ui.framework.UiDropdown;
import com.jelly.farmhelper.fabric.ui.framework.UiScrollContainer;
import com.jelly.farmhelper.fabric.ui.framework.UiTextField;
import com.jelly.farmhelper.fabric.ui.framework.UiTheme;
import com.jelly.farmhelper.fabric.ui.framework.UiToggle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import com.jelly.farmhelper.fabric.util.PlayerUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Unified custom FarmHelper configuration UI.
 * Includes toggle, enum selector, text input, integer input, and float input rows.
 */
public class FarmHelperModernConfigScreen extends FHScreen {
    private static final int DEFAULT_TEXT_MAX = 512;
    private static final Integer[] VISITOR_ACTION_VALUES = {0, 1, 2, 3};
    private static final String[] VISITOR_ACTION_LABELS = {"Accept", "Accept if profitable only", "Decline", "Ignore"};

    private final List<SettingRow> allRows = new ArrayList<>();
    private UiScrollContainer scrollContainer;
    private UiTextField searchField;
    private SettingSection activeSection = SettingSection.QUICK_SETUP;
    private LayoutMetrics layoutMetrics = LayoutMetrics.forScreen(1280, 720);

    private enum SettingSection {
        QUICK_SETUP("Quick Setup", "Spawn, rewarp, crop profile, and the switches needed to start a farm."),
        MACRO("Movement", "Lane movement, rotation, row timings, and macro-specific tuning."),
        FAILSAFES("Failsafes", "Detection thresholds, alerts, and recovery behavior."),
        AUTOMATION("Automation", "Scheduler, trading, upkeep, consumables, and helper flows."),
        VISITORS("Visitors", "Visitor filtering, reward rules, spending limits, and compactor handling."),
        PESTS("Pests", "Pest destroyer, pest farmer, and aim behavior."),
        ADVANCED("Advanced", "HUD, webhooks, proxy, keybinds, and direct feature module toggles."),
        DASHBOARD("All", "Every setting in one searchable list.");

        private final String label;
        private final String description;

        SettingSection(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }
    }

    public FarmHelperModernConfigScreen(net.minecraft.client.gui.screen.Screen parent) {
        super(parent, Text.literal("FarmHelper Settings"));
    }

    @Override
    protected void buildUi() {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        layoutMetrics = LayoutMetrics.forScreen(width, height);

        UiContainer root = new UiContainer() {
            @Override
            public void render(DrawContext context, int mouseX, int mouseY, float delta, UiTheme theme) {
                LayoutMetrics layout = layoutMetrics;

                context.fill(0, 0, width, height, 0x80000000);
                UiDraw.drawRoundedPanel(context, layout.left, layout.top, layout.right, layout.bottom, theme.cornerRadiusLarge + 4, theme.borderColor, theme.rootPanelBackground);

                UiDraw.fillRoundedRect(context, layout.left + 1, layout.top + 1, layout.left + layout.sidebarWidth, layout.bottom - 1, theme.cornerRadiusLarge, theme.sidebarBackground);
                UiDraw.fillRoundedRect(context, layout.left + layout.sidebarWidth, layout.top + 1, layout.right - 1, layout.bottom - 1, theme.cornerRadiusLarge, theme.contentBackground);

                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.textRenderer != null) {
                    context.drawText(client.textRenderer, Text.literal("FarmHelper"), layout.left + layout.headerInset, layout.top + 16, theme.textPrimary, true);
                    if (layout.showSidebarSubtitle) {
                        context.drawText(client.textRenderer, Text.literal("Modern Control Panel"), layout.left + layout.headerInset, layout.top + 32, theme.textSecondary, true);
                    }
                    String sectionLabel = client.textRenderer.trimToWidth(activeSection.label(), Math.max(120, layout.contentWidth - 12));
                    context.drawText(client.textRenderer, Text.literal(sectionLabel), layout.contentLeft, layout.top + 16, theme.textPrimary, true);
                    String sectionDescription = client.textRenderer.trimToWidth(activeSection.description(), Math.max(120, layout.contentWidth - 12));
                    context.drawText(client.textRenderer, Text.literal(sectionDescription), layout.contentLeft, layout.top + 32, theme.textSecondary, false);
                }

                for (UiComponent child : children) {
                    if (child.isVisible()) {
                        child.render(context, mouseX, mouseY, delta, theme);
                    }
                }
            }
        };
        root.setBounds(0, 0, width, height);

        LayoutMetrics layout = layoutMetrics;

        UiColumn sidebar = new UiColumn(layout.sidebarGap);
        sidebar.setBounds(layout.left + layout.sidebarInset, layout.sidebarTop, layout.sidebarWidth - (layout.sidebarInset * 2), layout.sidebarHeight);
        for (SettingSection section : SettingSection.values()) {
            sidebar.addChild(new UiButton(
                    () -> Text.literal((activeSection == section ? "> " : "") + section.label()),
                    () -> setActiveSection(section)
            ));
        }
        root.addChild(sidebar);
        sidebar.layoutChildren();

        searchField = new UiTextField("Search options...");
        searchField.setBounds(layout.contentLeft, layout.searchTop, layout.contentWidth, layout.searchHeight);
        searchField.setListener(this::onSearchChanged);
        root.addChild(searchField);

        scrollContainer = new UiScrollContainer();
        scrollContainer.setBounds(layout.contentLeft - 4, layout.scrollTop, layout.contentWidth + 8, layout.scrollHeight);
        root.addChild(scrollContainer);

        allRows.clear();
        populateRows(config);
        applyFiltersAndLayout();

        this.rootComponent = root;
    }

    private void populateRows(FarmHelperConfig config) {
        addHeader(SettingSection.QUICK_SETUP, "Farm Profile", "Choose the macro profile and enable the core startup behavior.");
        addEnumSetting(
                SettingSection.QUICK_SETUP,
                "Macro Type",
                "Select the farming macro profile to run.",
                LegacyMacroType.values(),
                () -> config.macroType,
                v -> config.macroType = v,
                LegacyMacroType::getDisplayName
        );
        addEnumSetting(
                SettingSection.QUICK_SETUP,
                "Macro Pattern",
                "Select traversal pattern for lanes/rows.",
                MacroPattern.values(),
                () -> config.macroPattern,
                v -> config.macroPattern = v,
                v -> v.name().replace('_', ' ')
        );
        addToggleSetting(SettingSection.QUICK_SETUP, "Use Legacy Profile Defaults", "Apply legacy default timings/angles.", () -> config.useLegacyProfileDefaults, v -> config.useLegacyProfileDefaults = v);
        addToggleSetting(SettingSection.QUICK_SETUP, "Auto Choose Tool", "Pick best farming tool on macro start.", () -> config.autoChooseTool, v -> config.autoChooseTool = v);
        addToggleSetting(SettingSection.QUICK_SETUP, "Hold Attack While Macroing", "Hold left click continuously while farming.", () -> config.holdAttackWhileMacroing, v -> config.holdAttackWhileMacroing = v);
        addToggleSetting(SettingSection.QUICK_SETUP, "Always Hold W", "Keep forward pressed while macroing.", () -> config.alwaysHoldW, v -> config.alwaysHoldW = v);
        addToggleSetting(SettingSection.QUICK_SETUP, "Enable Failsafes", "Enable detector/reaction pipeline.", () -> config.enableFailsafes, v -> config.enableFailsafes = v);

        addHeader(SettingSection.QUICK_SETUP, "Spawn & Rewarp", "Save the current farm position and manage the points the macro uses to recover.");
        addActionSetting(
                SettingSection.QUICK_SETUP,
                "Set Spawn From Current Position",
                "Save the player's current XYZ, yaw, pitch, and plot as the farm spawn location.",
                "Set Spawn",
                () -> {
                    PlayerUtils.setSpawnLocation(client);
                    return true;
                }
        );
        addActionSetting(
                SettingSection.QUICK_SETUP,
                "Add Rewarp From Current Position",
                "Create a rewarp point from the player's current XYZ, yaw, and pitch.",
                "Add Rewarp",
                RewarpPointsScreen::addCurrentPlayerPosition
        );
        addActionSetting(
                SettingSection.QUICK_SETUP,
                "Manage Rewarp Points",
                "Open the rewarp point manager to rename, remove, or clear saved points.",
                "Open Manager",
                () -> {
                    if (client != null) {
                        client.setScreen(new RewarpPointsScreen(this));
                    }
                    return true;
                }
        );
        addIntSetting(SettingSection.QUICK_SETUP, "Rewarp Activation Radius", "Distance from point needed to trigger rewarp.", () -> config.rewarpActivationRadius, v -> config.rewarpActivationRadius = v, 1, 12);

        addHeader(SettingSection.MACRO, "Macro Profile", "Core movement identity, farm speed, and spawn/rewarp configuration.");
        addEnumSetting(
                SettingSection.MACRO,
                "Macro Type",
                "Select the farming macro profile to run.",
                LegacyMacroType.values(),
                () -> config.macroType,
                v -> config.macroType = v,
                LegacyMacroType::getDisplayName
        );
        addEnumSetting(
                SettingSection.MACRO,
                "Macro Pattern",
                "Select traversal pattern for lanes/rows.",
                MacroPattern.values(),
                () -> config.macroPattern,
                v -> config.macroPattern = v,
                v -> v.name().replace('_', ' ')
        );
        addToggleSetting(SettingSection.MACRO, "Use Legacy Profile Defaults", "Apply legacy default timings/angles.", () -> config.useLegacyProfileDefaults, v -> config.useLegacyProfileDefaults = v);
        addToggleSetting(SettingSection.MACRO, "Always Hold W", "Keep forward pressed while macroing.", () -> config.alwaysHoldW, v -> config.alwaysHoldW = v);
        addToggleSetting(SettingSection.MACRO, "Hold Attack While Macroing", "Hold left click continuously while farming.", () -> config.holdAttackWhileMacroing, v -> config.holdAttackWhileMacroing = v);
        addToggleSetting(SettingSection.MACRO, "Use Custom Farming Speed", "Use configured farming speed value.", () -> config.useCustomFarmingSpeed, v -> config.useCustomFarmingSpeed = v);
        addIntSetting(SettingSection.MACRO, "Farming Speed", "Rancher speed override value.", () -> config.farmingSpeed, v -> config.farmingSpeed = v, 1, 400);
        addToggleSetting(SettingSection.MACRO, "Auto Choose Tool", "Pick best farming tool on macro start.", () -> config.autoChooseTool, v -> config.autoChooseTool = v);
        addHeader(SettingSection.MACRO, "Rotation & Camera", "Warp alignment, custom yaw/pitch, and rotation timing.");
        addToggleSetting(SettingSection.MACRO, "Rotate After Warped", "Re-align camera after warp.", () -> config.rotateAfterWarped, v -> config.rotateAfterWarped = v);
        addToggleSetting(SettingSection.MACRO, "Rotate After Drop", "Re-align camera after drop/fall.", () -> config.rotateAfterDrop, v -> config.rotateAfterDrop = v);
        addToggleSetting(SettingSection.MACRO, "Don't Fix After Warping", "Skip micro-corrections after warp.", () -> config.dontFixAfterWarping, v -> config.dontFixAfterWarping = v);
        addToggleSetting(SettingSection.MACRO, "Custom Pitch", "Enable custom pitch level.", () -> config.customPitch, v -> config.customPitch = v);
        addFloatSetting(SettingSection.MACRO, "Custom Pitch Level", "Manual camera pitch.", () -> config.customPitchLevel, v -> config.customPitchLevel = v, -90f, 90f);
        addToggleSetting(SettingSection.MACRO, "Custom Yaw", "Enable custom yaw level.", () -> config.customYaw, v -> config.customYaw = v);
        addFloatSetting(SettingSection.MACRO, "Custom Yaw Level", "Manual camera yaw.", () -> config.customYawLevel, v -> config.customYawLevel = v, -180f, 180f);
        addHeader(SettingSection.MACRO, "Lane Timing", "Turn length, sidestep timing, and row change delays.");
        addIntSetting(SettingSection.MACRO, "Forward Ticks Before Turn", "Ticks before turning to next row.", () -> config.forwardTicksBeforeTurn, v -> config.forwardTicksBeforeTurn = v, 20, 800);
        addIntSetting(SettingSection.MACRO, "Side Step Ticks", "Ticks for side-step lane change.", () -> config.sideStepTicks, v -> config.sideStepTicks = v, 1, 100);
        addIntSetting(SettingSection.MACRO, "Time Between Row Changes (ms)", "Delay before changing rows.", () -> config.timeBetweenChangingRowsMs, v -> config.timeBetweenChangingRowsMs = v, 0, 5000);
        addIntSetting(SettingSection.MACRO, "Random Row Change Delay (ms)", "Extra randomized row-change delay.", () -> config.randomTimeBetweenChangingRowsMs, v -> config.randomTimeBetweenChangingRowsMs = v, 0, 5000);
        addIntSetting(SettingSection.MACRO, "Rotation Time (ms)", "Base smooth rotation duration.", () -> config.rotationTimeMs, v -> config.rotationTimeMs = v, 0, 5000);
        addIntSetting(SettingSection.MACRO, "Rotation Time Randomness (ms)", "Randomized extra rotation time.", () -> config.rotationTimeRandomnessMs, v -> config.rotationTimeRandomnessMs = v, 0, 5000);
        addHeader(SettingSection.MACRO, "Spawn & Rewarp", "Save the farm origin, draw markers, and tune recovery warp behavior.");
        addToggleSetting(SettingSection.MACRO, "Draw Spawn Location", "Render configured spawn marker.", () -> config.drawSpawnLocation, v -> config.drawSpawnLocation = v);
        addToggleSetting(SettingSection.MACRO, "Highlight Rewarp", "Render configured rewarp points.", () -> config.highlightRewarp, v -> config.highlightRewarp = v);
        addActionSetting(
                SettingSection.MACRO,
                "Set Spawn From Current Position",
                "Save the player's current XYZ, yaw, pitch, and plot as the farm spawn location.",
                "Set Spawn",
                () -> {
                    PlayerUtils.setSpawnLocation(client);
                    return true;
                }
        );
        addActionSetting(
                SettingSection.MACRO,
                "Add Rewarp From Current Position",
                "Create a rewarp point from the player's current XYZ, yaw, and pitch.",
                "Add Rewarp",
                RewarpPointsScreen::addCurrentPlayerPosition
        );
        addActionSetting(
                SettingSection.MACRO,
                "Manage Rewarp Points",
                "Open the rewarp point manager to rename, remove, or clear saved points.",
                "Open Manager",
                () -> {
                    if (client != null) {
                        client.setScreen(new RewarpPointsScreen(this));
                    }
                    return true;
                }
        );
        addIntSetting(SettingSection.MACRO, "Spawn Pos X", "Configured spawn X coordinate.", () -> config.spawnPosX, v -> config.spawnPosX = v, -30000000, 30000000);
        addIntSetting(SettingSection.MACRO, "Spawn Pos Y", "Configured spawn Y coordinate.", () -> config.spawnPosY, v -> config.spawnPosY = v, -64, 400);
        addIntSetting(SettingSection.MACRO, "Spawn Pos Z", "Configured spawn Z coordinate.", () -> config.spawnPosZ, v -> config.spawnPosZ = v, -30000000, 30000000);
        addFloatSetting(SettingSection.MACRO, "Spawn Yaw", "Configured spawn yaw.", () -> config.spawnYaw, v -> config.spawnYaw = v, -180f, 180f);
        addFloatSetting(SettingSection.MACRO, "Spawn Pitch", "Configured spawn pitch.", () -> config.spawnPitch, v -> config.spawnPitch = v, -90f, 90f);
        addIntSetting(SettingSection.MACRO, "Spawn Plot", "Configured spawn plot index.", () -> config.spawnPlot, v -> config.spawnPlot = v, 0, 24);
        addIntSetting(SettingSection.MACRO, "Rewarp Activation Radius", "Distance from point needed to trigger rewarp.", () -> config.rewarpActivationRadius, v -> config.rewarpActivationRadius = v, 1, 12);
        addIntSetting(SettingSection.MACRO, "Rewarp Delay (ms)", "Delay before running rewarp command.", () -> config.rewarpDelayMs, v -> config.rewarpDelayMs = v, 0, 5000);
        addIntSetting(SettingSection.MACRO, "Rewarp Delay Randomness (ms)", "Extra randomized rewarp delay.", () -> config.rewarpDelayRandomnessMs, v -> config.rewarpDelayRandomnessMs = v, 0, 5000);
        addHeader(SettingSection.MACRO, "Humanization", "Optional pause and camera variation for non-legacy movement paths.");
        addToggleSetting(SettingSection.MACRO, "Player Simulation", "Enable movement/camera humanization.", () -> config.playerSimulationEnabled, v -> config.playerSimulationEnabled = v);
        addIntSetting(SettingSection.MACRO, "Simulation Pause Chance (%)", "Chance to insert micro-pauses.", () -> config.playerSimulationPauseChancePct, v -> config.playerSimulationPauseChancePct = v, 0, 100);
        addFloatSetting(SettingSection.MACRO, "Simulation Yaw Jitter", "Random yaw jitter degrees.", () -> config.playerSimulationYawJitterDegrees, v -> config.playerSimulationYawJitterDegrees = v, 0f, 30f);
        addFloatSetting(SettingSection.MACRO, "Simulation Pitch Jitter", "Random pitch jitter degrees.", () -> config.playerSimulationPitchJitterDegrees, v -> config.playerSimulationPitchJitterDegrees = v, 0f, 30f);

        addHeader(SettingSection.FAILSAFES, "Core Protection", "Master detector switches, alerts, and banner behavior.");
        addToggleSetting(SettingSection.FAILSAFES, "Enable Failsafes", "Enable detector/reaction pipeline.", () -> config.enableFailsafes, v -> config.enableFailsafes = v);
        addToggleSetting(SettingSection.FAILSAFES, "Desktop Notifications", "Show desktop notifications on failsafe trigger.", () -> config.popUpNotifications, v -> config.popUpNotifications = v);
        addIntSetting(SettingSection.FAILSAFES, "Desktop Notification Cooldown (s)", "Minimum seconds between desktop popups.", () -> config.desktopNotificationCooldownSeconds, v -> config.desktopNotificationCooldownSeconds = v, 0, 300);
        addToggleSetting(SettingSection.FAILSAFES, "Show Failsafe Banner", "Display a large warning banner while a failsafe is active.", () -> config.enableFailsafeBanner, v -> config.enableFailsafeBanner = v);
        addToggleSetting(SettingSection.FAILSAFES, "Banner Shows Reason", "Include the failsafe reason text in the banner.", () -> config.failsafeBannerShowReason, v -> config.failsafeBannerShowReason = v);
        addToggleSetting(SettingSection.FAILSAFES, "Auto Alt-Tab", "Switch focus away from game on failsafe.", () -> config.autoAltTab, v -> config.autoAltTab = v);
        addToggleSetting(SettingSection.FAILSAFES, "Disable Only Action", "Failsafe reaction only disables macro.", () -> config.failsafeActionDisableOnly, v -> config.failsafeActionDisableOnly = v);
        addIntSetting(SettingSection.FAILSAFES, "Failsafe Stop Delay (ms)", "Delay before stopping after trigger.", () -> config.failsafeStopDelayMs, v -> config.failsafeStopDelayMs = v, 0, 10000);
        addToggleSetting(SettingSection.FAILSAFES, "Auto Warp On World Change", "Return to farm after world changes.", () -> config.autoWarpOnWorldChange, v -> config.autoWarpOnWorldChange = v);
        addToggleSetting(SettingSection.FAILSAFES, "Auto Evacuate On Reboot", "Evacuate when reboot/server warnings appear.", () -> config.autoEvacuateOnServerReboot, v -> config.autoEvacuateOnServerReboot = v);
        addToggleSetting(SettingSection.FAILSAFES, "Auto Reconnect", "Reconnect after disconnect.", () -> config.autoReconnect, v -> config.autoReconnect = v);
        addIntSetting(SettingSection.FAILSAFES, "Reconnect Delay (s)", "Delay before reconnect attempt.", () -> config.autoReconnectDelaySeconds, v -> config.autoReconnectDelaySeconds = v, 0, 300);
        addIntSetting(SettingSection.FAILSAFES, "Reconnect Max Attempts", "Maximum reconnect attempts before stop.", () -> config.autoReconnectMaxAttempts, v -> config.autoReconnectMaxAttempts = v, 1, 50);
        addToggleSetting(SettingSection.FAILSAFES, "Lag Detector", "Detect lagback/network lag anomalies.", () -> config.lagDetectorEnabled, v -> config.lagDetectorEnabled = v);
        addToggleSetting(SettingSection.FAILSAFES, "Pause On Guest Arrival", "Pause if another player enters your garden.", () -> config.pauseOnGuestArrival, v -> config.pauseOnGuestArrival = v);
        addToggleSetting(SettingSection.FAILSAFES, "Enable BPS Check", "Detect suspicious low movement speed.", () -> config.enableBpsCheck, v -> config.enableBpsCheck = v);
        addFloatSetting(SettingSection.FAILSAFES, "Min BPS Threshold", "Minimum BPS before low-speed detection triggers.", () -> config.minBpsThreshold, v -> config.minBpsThreshold = v, 0f, 50f);
        addToggleSetting(SettingSection.FAILSAFES, "Packet Failsafe Checks", "Enable packet-level guards.", () -> config.enablePacketFailsafeChecks, v -> config.enablePacketFailsafeChecks = v);
        addHeader(SettingSection.FAILSAFES, "Detection Thresholds", "Sensitivity values for movement, rotation, and teleport checks.");
        addFloatSetting(SettingSection.FAILSAFES, "Teleport Lag Tolerance", "Tolerance for teleport correction checks.", () -> config.teleportLagTolerance, v -> config.teleportLagTolerance = v, 0f, 10f);
        addIntSetting(SettingSection.FAILSAFES, "Detection Time Window (ms)", "Window for packet anomaly aggregation.", () -> config.detectionTimeWindowMs, v -> config.detectionTimeWindowMs = v, 50, 10000);
        addFloatSetting(SettingSection.FAILSAFES, "Pitch Sensitivity", "Pitch threshold for rotation anomaly.", () -> config.pitchSensitivity, v -> config.pitchSensitivity = v, 0f, 180f);
        addFloatSetting(SettingSection.FAILSAFES, "Yaw Sensitivity", "Yaw threshold for rotation anomaly.", () -> config.yawSensitivity, v -> config.yawSensitivity = v, 0f, 180f);
        addFloatSetting(SettingSection.FAILSAFES, "Teleport Distance Threshold", "Distance threshold for lagback detection.", () -> config.teleportDistanceThreshold, v -> config.teleportDistanceThreshold = v, 0f, 30f);
        addFloatSetting(SettingSection.FAILSAFES, "Vertical Knockback Threshold", "Vertical velocity threshold.", () -> config.verticalKnockbackThreshold, v -> config.verticalKnockbackThreshold = v, 0f, 100000f);
        addToggleSetting(SettingSection.FAILSAFES, "Failsafe Sound Alerts", "Enable failsafe alert sound playback.", () -> config.enableFailsafeSound, v -> config.enableFailsafeSound = v);
        addToggleSetting(SettingSection.FAILSAFES, "Anvil Alert Loop", "Loop anvil place/land sounds while failsafe stays active.", () -> config.enableFailsafeAnvilAlert, v -> config.enableFailsafeAnvilAlert = v);
        addIntSetting(SettingSection.FAILSAFES, "Anvil Alert Interval (ticks)", "Tick gap between repeated anvil alert sounds.", () -> config.failsafeAnvilAlertIntervalTicks, v -> config.failsafeAnvilAlertIntervalTicks = v, 1, 80);
        addFloatSetting(SettingSection.FAILSAFES, "Anvil Alert Volume", "Volume multiplier for failsafe anvil sounds.", () -> config.failsafeAnvilAlertVolume, v -> config.failsafeAnvilAlertVolume = v, 0.1f, 2.0f);
        addHeader(SettingSection.FAILSAFES, "Recovery", "Reconnect, restart, and world-return behavior after alerts.");
        addToggleSetting(SettingSection.FAILSAFES, "Restart After Failsafe", "Restart macro automatically after recovery.", () -> config.restartAfterFailsafe, v -> config.restartAfterFailsafe = v);
        addIntSetting(SettingSection.FAILSAFES, "Restart Delay (min)", "Delay before restart after failsafe.", () -> config.restartAfterFailsafeDelayMinutes, v -> config.restartAfterFailsafeDelayMinutes = v, 0, 120);
        addToggleSetting(SettingSection.FAILSAFES, "Jacob Failsafe", "Enable Jacob contest failsafe behavior.", () -> config.enableJacobFailsafe, v -> config.enableJacobFailsafe = v);
        addToggleSetting(SettingSection.FAILSAFES, "Always Teleport To Garden", "Force return to garden in reactions.", () -> config.alwaysTeleportToGarden, v -> config.alwaysTeleportToGarden = v);
        addToggleSetting(SettingSection.FAILSAFES, "Send Failsafe Chat Message", "Send configured chat response on failsafe.", () -> config.sendFailsafeChatMessage, v -> config.sendFailsafeChatMessage = v);
        addToggleSetting(SettingSection.FAILSAFES, "Enable Custom Failsafe Reactions", "Use custom reaction timing/messages.", () -> config.enableCustomFailsafeReactions, v -> config.enableCustomFailsafeReactions = v);
        addHeader(SettingSection.FAILSAFES, "Custom Reaction", "Optional custom timing and message flow after a trigger.");
        addIntSetting(SettingSection.FAILSAFES, "Custom Reaction Min (ms)", "Minimum delay for custom reaction.", () -> config.customFailsafeReactionMinMs, v -> config.customFailsafeReactionMinMs = v, 0, 20000);
        addIntSetting(SettingSection.FAILSAFES, "Custom Reaction Max (ms)", "Maximum delay for custom reaction.", () -> config.customFailsafeReactionMaxMs, v -> config.customFailsafeReactionMaxMs = v, 0, 20000);
        addToggleSetting(SettingSection.FAILSAFES, "Custom Reaction Warp To Garden", "Warp to garden in custom reaction.", () -> config.customFailsafeWarpToGarden, v -> config.customFailsafeWarpToGarden = v);
        addToggleSetting(SettingSection.FAILSAFES, "Custom Reaction Second Message", "Send second message in reaction flow.", () -> config.customFailsafeSendSecondMessage, v -> config.customFailsafeSendSecondMessage = v);

        addHeader(SettingSection.AUTOMATION, "Scheduler", "Automated farm sessions and break timing.");
        addToggleSetting(SettingSection.AUTOMATION, "Enable Scheduler", "Run farm/break cycles automatically.", () -> config.enableScheduler, v -> {
            config.enableScheduler = v;
            setFeatureFromToggle("scheduler", v);
        });
        addIntSetting(SettingSection.AUTOMATION, "Scheduler Farm Time (min)", "Duration of farming phase.", () -> config.schedulerFarmingTimeMinutes, v -> config.schedulerFarmingTimeMinutes = v, 1, 600);
        addIntSetting(SettingSection.AUTOMATION, "Scheduler Break Time (min)", "Duration of break phase.", () -> config.schedulerBreakTimeMinutes, v -> config.schedulerBreakTimeMinutes = v, 0, 600);
        addIntSetting(SettingSection.AUTOMATION, "Scheduler Farm Randomness (min)", "Randomized farm-time variation.", () -> config.schedulerFarmingTimeRandomnessMinutes, v -> config.schedulerFarmingTimeRandomnessMinutes = v, 0, 120);
        addIntSetting(SettingSection.AUTOMATION, "Scheduler Break Randomness (min)", "Randomized break-time variation.", () -> config.schedulerBreakTimeRandomnessMinutes, v -> config.schedulerBreakTimeRandomnessMinutes = v, 0, 120);
        addToggleSetting(SettingSection.AUTOMATION, "Pause Scheduler During Jacob", "Pause scheduler breaks during Jacob contest.", () -> config.pauseSchedulerDuringJacobsContest, v -> config.pauseSchedulerDuringJacobsContest = v);
        addToggleSetting(SettingSection.AUTOMATION, "Disconnect During Break", "Run /lobby during break periods.", () -> config.schedulerDisconnectDuringBreak, v -> config.schedulerDisconnectDuringBreak = v);
        addHeader(SettingSection.AUTOMATION, "Trading", "Bazaar and NPC selling helpers used by other automation flows.");
        addToggleSetting(SettingSection.AUTOMATION, "Auto Sell", "Enable NPC selling automation.", () -> config.enableAutoSell, v -> {
            config.enableAutoSell = v;
            setFeatureFromToggle("auto_sell", v);
        });
        addToggleSetting(SettingSection.AUTOMATION, "Auto Bazaar", "Enable bazaar automation.", () -> config.autoBazaar, v -> {
            config.autoBazaar = v;
            setFeatureFromToggle("auto_bazaar", v);
        });
        addHeader(SettingSection.VISITORS, "Core", "Legacy visitor macro controls, spend limits, and cycle sizing.");
        addToggleSetting(SettingSection.VISITORS, "Visitors Macro", "Enable visitors handling automation.", () -> config.visitorsMacro, v -> {
            config.visitorsMacro = v;
            setFeatureFromToggle("visitors_macro", v);
        });
        addIntSetting(SettingSection.VISITORS, "Visitors Min Visitors", "Threshold to trigger visitors cycle.", () -> config.visitorsMacroMinVisitors, v -> config.visitorsMacroMinVisitors = v, 1, 20);
        addIntSetting(SettingSection.VISITORS, "Visitors Action Seconds", "Timeout for visitor actions.", () -> config.visitorsMacroActionSeconds, v -> config.visitorsMacroActionSeconds = v, 1, 120);
        addToggleSetting(SettingSection.VISITORS, "Auto Sell Before Serving", "Run sell flow before starting the visitor cycle.", () -> config.visitorsMacroAutosellBeforeServing, v -> config.visitorsMacroAutosellBeforeServing = v);
        addIntSetting(SettingSection.VISITORS, "Visitors Min Money (k)", "Minimum purse required before the visitor macro runs.", () -> config.visitorsMacroMinMoney, v -> config.visitorsMacroMinMoney = v, 0, 1_000_000);
        addFloatSetting(SettingSection.VISITORS, "Visitors Max Spend (m)", "Maximum coins to spend per visitor request in millions.", () -> config.visitorsMacroMaxSpendLimit, v -> config.visitorsMacroMaxSpendLimit = v, 0f, 100f);
        addToggleSetting(SettingSection.VISITORS, "AFK Infinite Mode", "Keep waiting for new visitors instead of ending after one cycle.", () -> config.visitorsMacroAfkInfiniteMode, v -> config.visitorsMacroAfkInfiniteMode = v);
        addIntSetting(SettingSection.VISITORS, "Max Visitors Per Cycle", "Maximum queued visitors to process in one run.", () -> config.visitorsMacroMaxVisitorsPerCycle, v -> config.visitorsMacroMaxVisitorsPerCycle = v, 1, 20);
        addIntSetting(SettingSection.VISITORS, "Retry Limit", "Retry budget for visitor interaction failures.", () -> config.visitorsMacroRetryLimit, v -> config.visitorsMacroRetryLimit = v, 1, 20);
        addHeader(SettingSection.VISITORS, "Name Filtering", "Legacy name filter flow applied before rarity decisions.");
        addToggleSetting(SettingSection.VISITORS, "Filter By Name", "Enable the old name filter logic for visitors.", () -> config.filterVisitorsByName, v -> config.filterVisitorsByName = v);
        addEnumSetting(SettingSection.VISITORS, "Name Filter Type", "Choose whether the name list is a blacklist or whitelist.", new String[]{"Blacklist", "Whitelist"}, () -> config.nameFilteringType ? "Whitelist" : "Blacklist", v -> config.nameFilteringType = "Whitelist".equals(v), v -> v);
        addEnumSetting(SettingSection.VISITORS, "Name Action", "Action to take when the name filter rejects a visitor.", new String[]{"Reject", "Ignore"}, () -> config.nameActionType ? "Ignore" : "Reject", v -> config.nameActionType = "Ignore".equals(v), v -> v);
        addTextSetting(SettingSection.VISITORS, "Name Filter", "Visitor names separated with |, matching the legacy config format.", () -> config.nameFilter, v -> config.nameFilter = v, DEFAULT_TEXT_MAX);
        addHeader(SettingSection.VISITORS, "Rarity Filtering", "Legacy rarity-based accept, profit-only, decline, and ignore actions.");
        addToggleSetting(SettingSection.VISITORS, "Filter By Rarity", "Apply rarity actions after name filtering.", () -> config.filterVisitorsByRarity, v -> config.filterVisitorsByRarity = v);
        addVisitorActionSetting("Uncommon", "Action taken when an uncommon visitor arrives.", () -> config.visitorsActionUncommon, v -> config.visitorsActionUncommon = v);
        addVisitorActionSetting("Rare", "Action taken when a rare visitor arrives.", () -> config.visitorsActionRare, v -> config.visitorsActionRare = v);
        addVisitorActionSetting("Legendary", "Action taken when a legendary visitor arrives.", () -> config.visitorsActionLegendary, v -> config.visitorsActionLegendary = v);
        addVisitorActionSetting("Mythic", "Action taken when a mythic visitor arrives.", () -> config.visitorsActionMythic, v -> config.visitorsActionMythic = v);
        addVisitorActionSetting("Special", "Action taken when a special visitor arrives.", () -> config.visitorsActionSpecial, v -> config.visitorsActionSpecial = v);
        addHeader(SettingSection.VISITORS, "Inventory & Logs", "Inventory edge-case handling and visitor webhook logging.");
        addToggleSetting(SettingSection.VISITORS, "Full Inventory Action Ignore", "If cakes do not fit, ignore instead of declining the visitor.", () -> config.fullInventoryAction, v -> config.fullInventoryAction = v);
        addToggleSetting(SettingSection.VISITORS, "Send Visitors Logs", "Send visitors macro logs to webhook.", () -> config.sendVisitorsMacroLogs, v -> config.sendVisitorsMacroLogs = v);
        addToggleSetting(SettingSection.VISITORS, "Ping Everyone On Visitors Logs", "Ping @everyone for visitors logs.", () -> config.pingEveryoneOnVisitorsMacroLogs, v -> config.pingEveryoneOnVisitorsMacroLogs = v);
        addHeader(SettingSection.AUTOMATION, "Consumables", "Keep buffs and consumables stocked while the macro runs.");
        addToggleSetting(SettingSection.AUTOMATION, "Auto Cookie", "Enable automatic cookie refresh.", () -> config.autoCookie, v -> {
            config.autoCookie = v;
            setFeatureFromToggle("auto_cookie", v);
        });
        addToggleSetting(SettingSection.AUTOMATION, "Auto God Pot", "Enable automatic god pot refresh.", () -> config.autoGodPot, v -> {
            config.autoGodPot = v;
            setFeatureFromToggle("auto_god_pot", v);
        });
        addToggleSetting(SettingSection.AUTOMATION, "Auto Repellent", "Enable automatic pest repellent use.", () -> config.autoRepellent, v -> {
            config.autoRepellent = v;
            setFeatureFromToggle("auto_repellent", v);
        });
        addHeader(SettingSection.AUTOMATION, "Farm Upkeep", "Composter, spray, wardrobe, pets, and stuck recovery.");
        addToggleSetting(SettingSection.AUTOMATION, "Auto Composter", "Enable composter refill automation.", () -> config.autoComposter, v -> {
            config.autoComposter = v;
            setFeatureFromToggle("auto_composter", v);
        });
        addToggleSetting(SettingSection.AUTOMATION, "Auto Pest Exchange", "Enable pest exchange automation.", () -> config.autoPestExchange, v -> {
            config.autoPestExchange = v;
            setFeatureFromToggle("auto_pest_exchange", v);
        });
        addToggleSetting(SettingSection.AUTOMATION, "Auto Sprayonator", "Enable sprayonator automation.", () -> config.autoSprayonator, v -> {
            config.autoSprayonator = v;
            setFeatureFromToggle("auto_sprayonator", v);
        });
        addToggleSetting(SettingSection.AUTOMATION, "Auto Wardrobe", "Enable wardrobe automation.", () -> config.autoWardrobe, v -> {
            config.autoWardrobe = v;
            setFeatureFromToggle("auto_wardrobe", v);
        });
        addToggleSetting(SettingSection.AUTOMATION, "Pet Swapper", "Enable pet swap automation.", () -> config.enablePetSwapper, v -> {
            config.enablePetSwapper = v;
            setFeatureFromToggle("pet_swapper", v);
        });
        addToggleSetting(SettingSection.AUTOMATION, "Anti Stuck", "Enable anti-stuck automation.", () -> config.antiStuckEnabled, v -> {
            config.antiStuckEnabled = v;
            setFeatureFromToggle("anti_stuck", v);
        });
        addIntSetting(SettingSection.AUTOMATION, "Anti Stuck Stationary Ticks", "Ticks before anti-stuck starts recovery.", () -> config.antiStuckStationaryTicks, v -> config.antiStuckStationaryTicks = v, 20, 2000);

        addHeader(SettingSection.PESTS, "Pests Destroyer", "Thresholds and aim settings for active pest runs.");
        addToggleSetting(SettingSection.PESTS, "Pests Destroyer", "Enable pests destroyer module.", () -> config.enablePestsDestroyer, v -> {
            config.enablePestsDestroyer = v;
            setFeatureFromToggle("pests_destroyer", v);
        });
        addIntSetting(SettingSection.PESTS, "Start Killing Pests At", "Pest count threshold to trigger run.", () -> config.startKillingPestsAt, v -> config.startKillingPestsAt = v, 1, 20);
        addIntSetting(SettingSection.PESTS, "Pests Action Seconds", "Timeout per pests action stage.", () -> config.pestsDestroyerActionSeconds, v -> config.pestsDestroyerActionSeconds = v, 1, 120);
        addIntSetting(SettingSection.PESTS, "Pests Max Passes", "Max passes over plots per cycle.", () -> config.pestsDestroyerMaxPasses, v -> config.pestsDestroyerMaxPasses = v, 1, 20);
        addToggleSetting(SettingSection.PESTS, "Don't Teleport To Plots", "Fly from barn/spawn to the target plot instead of using /plottp.", () -> config.pestsDestroyerDontTeleportToPlots, v -> config.pestsDestroyerDontTeleportToPlots = v);
        addIntSetting(SettingSection.PESTS, "Can't Reach Ticks", "Ticks of stalled pest hunting before escape recovery. Set 0 to disable.", () -> config.pestsDestroyerCantReachTicks, v -> config.pestsDestroyerCantReachTicks = v, 0, 400);
        addToggleSetting(SettingSection.PESTS, "Disable During Jacob", "Disable pests destroyer during Jacob contests.", () -> config.pestsDestroyerDisableDuringJacobsContest, v -> config.pestsDestroyerDisableDuringJacobsContest = v);
        addToggleSetting(SettingSection.PESTS, "Start Only On Rewarp/Spawn", "Only trigger pests run near spawn/rewarp points.", () -> config.pestsDestroyerStartOnlyOnRewarpOrSpawn, v -> config.pestsDestroyerStartOnlyOnRewarpOrSpawn = v);
        addFloatSetting(SettingSection.PESTS, "Aim Deadzone Yaw", "Yaw deadzone to reduce jitter tracking.", () -> config.pestsAimDeadzoneYaw, v -> config.pestsAimDeadzoneYaw = v, 0f, 45f);
        addFloatSetting(SettingSection.PESTS, "Aim Deadzone Pitch", "Pitch deadzone to reduce jitter tracking.", () -> config.pestsAimDeadzonePitch, v -> config.pestsAimDeadzonePitch = v, 0f, 45f);
        addToggleSetting(SettingSection.PESTS, "Pests Tracers", "Render pest tracers.", () -> config.pestsTracers, v -> config.pestsTracers = v);
        addToggleSetting(SettingSection.PESTS, "Pests Highlight Box", "Render pest hit boxes.", () -> config.pestsHighlightBox, v -> config.pestsHighlightBox = v);
        addHeader(SettingSection.PESTS, "Pest Farmer", "Passive pest-farming helper and equipment swapping.");
        addToggleSetting(SettingSection.PESTS, "Pest Farmer", "Enable pest farmer helper.", () -> config.pestFarmer, v -> {
            config.pestFarmer = v;
            setFeatureFromToggle("pest_farmer", v);
        });
        addToggleSetting(SettingSection.PESTS, "Pest Farmer Kill Pests", "Let pest farmer perform pest kills.", () -> config.pestFarmerKillPests, v -> config.pestFarmerKillPests = v);
        addToggleSetting(SettingSection.PESTS, "Pest Farmer Swap Equipment", "Swap equipment during pest farmer flow.", () -> config.pestFarmerSwapEquipment, v -> config.pestFarmerSwapEquipment = v);

        addHeader(SettingSection.ADVANCED, "HUD & Visuals", "Overlay positions, stats, performance helpers, and visual toggles.");
        addToggleSetting(SettingSection.ADVANCED, "Status HUD", "Enable status HUD panel.", () -> config.enableStatusHud, v -> config.enableStatusHud = v);
        addIntSetting(SettingSection.ADVANCED, "Status HUD X", "Status HUD horizontal position.", () -> config.statusHudX, v -> config.statusHudX = v, 0, 4000);
        addIntSetting(SettingSection.ADVANCED, "Status HUD Y", "Status HUD vertical position.", () -> config.statusHudY, v -> config.statusHudY = v, 0, 4000);
        addToggleSetting(SettingSection.ADVANCED, "Profit HUD", "Enable profit HUD panel.", () -> config.enableProfitHud, v -> config.enableProfitHud = v);
        addIntSetting(SettingSection.ADVANCED, "Profit HUD X", "Profit HUD horizontal position.", () -> config.profitHudX, v -> config.profitHudX = v, 0, 4000);
        addIntSetting(SettingSection.ADVANCED, "Profit HUD Y", "Profit HUD vertical position.", () -> config.profitHudY, v -> config.profitHudY = v, 0, 4000);
        addToggleSetting(SettingSection.ADVANCED, "Debug HUD Overlay", "Enable debug HUD panel.", () -> config.enableDebugHudOverlay, v -> config.enableDebugHudOverlay = v);
        addIntSetting(SettingSection.ADVANCED, "Debug HUD X", "Debug HUD horizontal position.", () -> config.debugHudX, v -> config.debugHudX = v, 0, 4000);
        addIntSetting(SettingSection.ADVANCED, "Debug HUD Y", "Debug HUD vertical position.", () -> config.debugHudY, v -> config.debugHudY = v, 0, 4000);
        addToggleSetting(SettingSection.ADVANCED, "Show HUD Outside Garden", "Show HUD overlays outside Garden location.", () -> config.showStatusHudOutsideGarden, v -> config.showStatusHudOutsideGarden = v);
        addToggleSetting(SettingSection.ADVANCED, "Debug Mode", "Enable expanded debug rendering/logging.", () -> config.debugMode, v -> config.debugMode = v);
        addToggleSetting(SettingSection.ADVANCED, "Streamer Mode", "Hide sensitive data from overlays/logs.", () -> config.streamerMode, v -> config.streamerMode = v);
        addToggleSetting(SettingSection.ADVANCED, "Performance Mode", "Reduce rendering load during runtime.", () -> config.performanceMode, v -> {
            config.performanceMode = v;
            setFeatureFromToggle("performance_mode", v);
        });
        addIntSetting(SettingSection.ADVANCED, "Performance Max FPS", "Max FPS when performance mode is active.", () -> config.performanceModeMaxFps, v -> config.performanceModeMaxFps = v, 5, 240);
        addIntSetting(SettingSection.ADVANCED, "Performance View Distance", "View distance when performance mode is active.", () -> config.performanceModeViewDistance, v -> config.performanceModeViewDistance = v, 2, 32);
        addToggleSetting(SettingSection.ADVANCED, "PiP Mode", "Enable PiP behavior module.", () -> config.pipMode, v -> {
            config.pipMode = v;
            setFeatureFromToggle("pip_mode", v);
        });
        addToggleSetting(SettingSection.ADVANCED, "Freelook Module", "Enable freelook behavior module.", () -> config.freelook, v -> {
            config.freelook = v;
            setFeatureFromToggle("freelook", v);
        });
        addToggleSetting(SettingSection.ADVANCED, "Auto Ungrab Mouse", "Enable ungrab mouse behavior module.", () -> config.autoUngrabMouse, v -> {
            config.autoUngrabMouse = v;
            setFeatureFromToggle("ungrab_mouse", v);
        });
        addToggleSetting(SettingSection.ADVANCED, "Show 24H Stats", "Show 24 hour statistics in HUD.", () -> config.showStats24H, v -> config.showStats24H = v);
        addToggleSetting(SettingSection.ADVANCED, "Show 7D Stats", "Show 7 day statistics in HUD.", () -> config.showStats7D, v -> config.showStats7D = v);
        addToggleSetting(SettingSection.ADVANCED, "Show 30D Stats", "Show 30 day statistics in HUD.", () -> config.showStats30D, v -> config.showStats30D = v);
        addToggleSetting(SettingSection.ADVANCED, "Show Lifetime Stats", "Show lifetime statistics in HUD.", () -> config.showStatsLifetime, v -> config.showStatsLifetime = v);
        addToggleSetting(SettingSection.ADVANCED, "Change Window Title", "Update game window title with runtime info.", () -> config.changeWindowTitle, v -> config.changeWindowTitle = v);

        addHeader(SettingSection.ADVANCED, "Webhook & Logging", "Discord, file logging, and status update settings.");
        addToggleSetting(SettingSection.ADVANCED, "Enable Discord Webhook", "Enable Discord webhook integration.", () -> config.enableWebhook, v -> config.enableWebhook = v);
        addTextSetting(SettingSection.ADVANCED, "Webhook URL", "Discord webhook URL.", () -> config.webhookUrl, v -> config.webhookUrl = v, DEFAULT_TEXT_MAX);
        addToggleSetting(SettingSection.ADVANCED, "Send Webhook Logs", "Send macro logs to webhook.", () -> config.sendWebhookLogs, v -> config.sendWebhookLogs = v);
        addToggleSetting(SettingSection.ADVANCED, "Send Status Updates", "Send periodic status updates.", () -> config.sendStatusUpdates, v -> config.sendStatusUpdates = v);
        addIntSetting(SettingSection.ADVANCED, "Status Update Interval (min)", "Minutes between status updates.", () -> config.statusUpdateIntervalMinutes, v -> config.statusUpdateIntervalMinutes = v, 1, 120);
        addToggleSetting(SettingSection.ADVANCED, "Send Macro Start/Stop Logs", "Send macro start/stop entries.", () -> config.sendMacroEnableDisableLogs, v -> config.sendMacroEnableDisableLogs = v);
        addToggleSetting(SettingSection.ADVANCED, "Send Failsafe Logs", "Send failsafe logs.", () -> config.sendFailsafeLogs, v -> config.sendFailsafeLogs = v);
        addToggleSetting(SettingSection.ADVANCED, "Send Webhook Debug Logs", "Send debug logs to webhook.", () -> config.sendWebhookDebugLogs, v -> config.sendWebhookDebugLogs = v);
        addToggleSetting(SettingSection.ADVANCED, "Write Debug Logs To File", "Write debug logs to minecraft/logs/farmhelper.", () -> config.writeDebugLogsToFile, v -> config.writeDebugLogsToFile = v);
        addHeader(SettingSection.ADVANCED, "Network & Proxy", "Proxy configuration and deferred remote control endpoints.");
        addToggleSetting(SettingSection.ADVANCED, "Proxy Enabled", "Enable proxy settings.", () -> config.proxyEnabled, v -> {
            config.proxyEnabled = v;
            setFeatureFromToggle("proxy", v);
        });
        addEnumSetting(SettingSection.ADVANCED, "Proxy Type", "Network proxy type.", new String[]{"SOCKS", "HTTP"}, () -> config.proxyType, v -> config.proxyType = v, v -> v);
        addTextSetting(SettingSection.ADVANCED, "Proxy Address", "Proxy host:port.", () -> config.proxyAddress, v -> config.proxyAddress = v, 256);
        addTextSetting(SettingSection.ADVANCED, "Proxy Username", "Proxy username.", () -> config.proxyUsername, v -> config.proxyUsername = v, 128);
        addTextSetting(SettingSection.ADVANCED, "Proxy Password", "Proxy password.", () -> config.proxyPassword, v -> config.proxyPassword = v, 128);
        addToggleSetting(SettingSection.ADVANCED, "Enable Remote Control", "Config toggle only (backend intentionally deferred).", () -> config.enableRemoteControl, v -> config.enableRemoteControl = v);
        addTextSetting(SettingSection.ADVANCED, "Remote Control Address", "Remote control server address.", () -> config.remoteControlAddress, v -> config.remoteControlAddress = v, 256);
        addIntSetting(SettingSection.ADVANCED, "Remote Control Port", "Remote control port.", () -> config.remoteControlPort, v -> config.remoteControlPort = v, 1, 65535);

        addHeader(SettingSection.ADVANCED, "Keybinds", "Direct keycode bindings for macro and utility actions.");
        addIntSetting(SettingSection.ADVANCED, "Toggle Macro Key", "GLFW keycode for macro toggle.", () -> config.toggleMacroKey, v -> config.toggleMacroKey = v, -1, 400);
        addIntSetting(SettingSection.ADVANCED, "Start Macro Key", "GLFW keycode for macro start.", () -> config.startMacroKey, v -> config.startMacroKey = v, -1, 400);
        addIntSetting(SettingSection.ADVANCED, "Stop Macro Key", "GLFW keycode for macro stop.", () -> config.stopMacroKey, v -> config.stopMacroKey = v, -1, 400);
        addIntSetting(SettingSection.ADVANCED, "Open Menu Key", "GLFW keycode for opening FarmHelper menu.", () -> config.openMenuKey, v -> config.openMenuKey = v, -1, 400);
        addIntSetting(SettingSection.ADVANCED, "Trigger Failsafe Key", "GLFW keycode for manual failsafe test.", () -> config.triggerFailsafeKey, v -> config.triggerFailsafeKey = v, -1, 400);
        addIntSetting(SettingSection.ADVANCED, "Freelook Key", "GLFW keycode for freelook toggle.", () -> config.freelookKey, v -> config.freelookKey = v, -1, 400);
        addIntSetting(SettingSection.ADVANCED, "Cancel Failsafe Key", "GLFW keycode to cancel current failsafe.", () -> config.cancelFailsafeKey, v -> config.cancelFailsafeKey = v, -1, 400);
        addIntSetting(SettingSection.ADVANCED, "Toggle Ungrab Mouse Key", "GLFW keycode for ungrab toggle.", () -> config.toggleUngrabMouseKey, v -> config.toggleUngrabMouseKey = v, -1, 400);
        addIntSetting(SettingSection.ADVANCED, "Plot Cleaning Helper Key", "GLFW keycode for plot cleaning helper.", () -> config.plotCleaningHelperKey, v -> config.plotCleaningHelperKey = v, -1, 400);
        addIntSetting(SettingSection.ADVANCED, "Trigger Pests Destroyer Key", "GLFW keycode for pests destroyer trigger.", () -> config.triggerPestsDestroyerKey, v -> config.triggerPestsDestroyerKey = v, -1, 400);
        addIntSetting(SettingSection.ADVANCED, "TP To Infested Plot Key", "GLFW keycode for teleporting to infested plot.", () -> config.tpToInfestedPlotKey, v -> config.tpToInfestedPlotKey = v, -1, 400);

        addHeader(SettingSection.ADVANCED, "Feature Modules", "Direct low-level feature toggles for power users.");
        for (FeatureCatalog.FeatureDefinition definition : FeatureCatalog.DEFINITIONS) {
            addToggleSetting(SettingSection.ADVANCED, definition.displayName(), "Direct feature module toggle for " + definition.displayName() + ".", () -> {
                Boolean enabled = config.featureToggles.get(definition.id());
                return enabled != null && enabled;
            }, enabled -> setFeatureFromToggle(definition.id(), enabled));
        }
    }

    private void addToggleSetting(SettingSection section, String title, String description, UiToggle.BooleanGetter getter, UiToggle.BooleanSetter setter) {
        SettingRow row = SettingRow.toggle(section, title, description, getter, value -> {
            setter.set(value);
            FarmHelperFabric.getConfigManager().save();
        });
        allRows.add(row);
        scrollContainer.addChild(row);
    }

    private void addHeader(SettingSection section, String title, String description) {
        SettingRow row = SettingRow.header(section, title, description);
        allRows.add(row);
        scrollContainer.addChild(row);
    }

    private void addTextSetting(SettingSection section, String title, String description, Supplier<String> getter, Consumer<String> setter, int maxLength) {
        SettingRow row = SettingRow.text(section, title, description, getter, value -> {
            setter.accept(value);
            FarmHelperFabric.getConfigManager().save();
        }, maxLength);
        allRows.add(row);
        scrollContainer.addChild(row);
    }

    private void addIntSetting(SettingSection section, String title, String description, IntSupplier getter, IntConsumer setter, int min, int max) {
        addTextSetting(section, title, description, () -> Integer.toString(getter.getAsInt()), text -> {
            if (text == null || text.isBlank()) {
                return;
            }
            try {
                int value = Integer.parseInt(text.trim());
                value = Math.max(min, Math.min(max, value));
                setter.accept(value);
            } catch (NumberFormatException ignored) {
            }
        }, 16);
    }

    private void addFloatSetting(SettingSection section, String title, String description, Supplier<Float> getter, Consumer<Float> setter, float min, float max) {
        addTextSetting(section, title, description, () -> String.format(Locale.ROOT, "%.2f", getter.get()), text -> {
            if (text == null || text.isBlank()) {
                return;
            }
            try {
                float value = Float.parseFloat(text.trim());
                value = Math.max(min, Math.min(max, value));
                setter.accept(value);
            } catch (NumberFormatException ignored) {
            }
        }, 24);
    }

    private <E> void addEnumSetting(
            SettingSection section,
            String title,
            String description,
            E[] values,
            Supplier<E> getter,
            Consumer<E> setter,
            Function<E, String> formatter
    ) {
        SettingRow row = SettingRow.enumSelector(section, title, description, values, getter, value -> {
            setter.accept(value);
            FarmHelperFabric.getConfigManager().save();
        }, formatter);
        allRows.add(row);
        scrollContainer.addChild(row);
    }

    private void addActionSetting(SettingSection section, String title, String description, String buttonLabel, ActionHandler action) {
        SettingRow row = SettingRow.button(section, title, description, buttonLabel, action);
        allRows.add(row);
        scrollContainer.addChild(row);
    }

    private void addVisitorActionSetting(String title, String description, Supplier<Integer> getter, Consumer<Integer> setter) {
        addEnumSetting(
                SettingSection.VISITORS,
                title,
                description,
                VISITOR_ACTION_VALUES,
                getter,
                setter,
                value -> VISITOR_ACTION_LABELS[Math.max(0, Math.min(VISITOR_ACTION_LABELS.length - 1, value))]
        );
    }

    private void onSearchChanged(String query) {
        applyFiltersAndLayout();
    }

    private void setActiveSection(SettingSection section) {
        if (section == null) {
            return;
        }
        activeSection = section;
        scrollContainer.scrollToTop();
        applyFiltersAndLayout();
    }

    private void applyFiltersAndLayout() {
        if (scrollContainer == null) {
            return;
        }
        LayoutMetrics layout = layoutMetrics == null ? LayoutMetrics.forScreen(width, height) : layoutMetrics;
        String lower = searchField == null ? "" : searchField.getText().toLowerCase(Locale.ROOT).trim();
        int y = scrollContainer.getY() + 8;
        int x = scrollContainer.getX() + 8;
        int width = scrollContainer.getWidth() - 16;

        for (SettingRow row : allRows) {
            row.setLayout(layout);
            boolean sectionMatch = activeSection == SettingSection.DASHBOARD || row.section == activeSection;
            boolean searchMatch = lower.isEmpty() || row.titleLower.contains(lower) || row.descriptionLower.contains(lower);
            boolean visible = sectionMatch && searchMatch;
            row.setVisible(visible);
            if (visible) {
                int rowHeight = row.preferredHeight();
                row.setBounds(x, y, width, rowHeight);
                y += rowHeight + layout.rowGap;
            }
        }
        scrollContainer.setContentHeight(y - (scrollContainer.getY() + 8));
    }

    private void setFeatureFromToggle(String featureId, boolean enabled) {
        if (!FeatureCatalog.isValidId(featureId)) {
            return;
        }
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        config.featureToggles.put(featureId, enabled);
        FarmHelperFabric.getFeatureManager().setFeatureEnabled(featureId, enabled);
        FarmHelperFabric.getConfigManager().save();
    }

    private static final class LayoutMetrics {
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final int panelWidth;
        private final int panelHeight;
        private final int sidebarWidth;
        private final int sidebarInset;
        private final int sidebarTop;
        private final int sidebarHeight;
        private final int sidebarGap;
        private final int headerInset;
        private final int contentLeft;
        private final int contentWidth;
        private final int searchTop;
        private final int searchHeight;
        private final int scrollTop;
        private final int scrollHeight;
        private final int headerRowHeight;
        private final int rowHeight;
        private final int rowGap;
        private final int controlHeight;
        private final int controlTop;
        private final int toggleWidth;
        private final int textControlWidth;
        private final int minTextControlWidth;
        private final int buttonWidth;
        private final int minButtonWidth;
        private final int controlRightInset;
        private final int labelReserve;
        private final int rowTextInset;
        private final boolean hideDescription;
        private final boolean showSidebarSubtitle;

        private LayoutMetrics(
                int left,
                int top,
                int right,
                int bottom,
                int panelWidth,
                int panelHeight,
                int sidebarWidth,
                int sidebarInset,
                int sidebarTop,
                int sidebarHeight,
                int sidebarGap,
                int headerInset,
                int contentLeft,
                int contentWidth,
                int searchTop,
                int searchHeight,
                int scrollTop,
                int scrollHeight,
                int headerRowHeight,
                int rowHeight,
                int rowGap,
                int controlHeight,
                int controlTop,
                int toggleWidth,
                int textControlWidth,
                int minTextControlWidth,
                int buttonWidth,
                int minButtonWidth,
                int controlRightInset,
                int labelReserve,
                int rowTextInset,
                boolean hideDescription,
                boolean showSidebarSubtitle
        ) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.panelWidth = panelWidth;
            this.panelHeight = panelHeight;
            this.sidebarWidth = sidebarWidth;
            this.sidebarInset = sidebarInset;
            this.sidebarTop = sidebarTop;
            this.sidebarHeight = sidebarHeight;
            this.sidebarGap = sidebarGap;
            this.headerInset = headerInset;
            this.contentLeft = contentLeft;
            this.contentWidth = contentWidth;
            this.searchTop = searchTop;
            this.searchHeight = searchHeight;
            this.scrollTop = scrollTop;
            this.scrollHeight = scrollHeight;
            this.headerRowHeight = headerRowHeight;
            this.rowHeight = rowHeight;
            this.rowGap = rowGap;
            this.controlHeight = controlHeight;
            this.controlTop = controlTop;
            this.toggleWidth = toggleWidth;
            this.textControlWidth = textControlWidth;
            this.minTextControlWidth = minTextControlWidth;
            this.buttonWidth = buttonWidth;
            this.minButtonWidth = minButtonWidth;
            this.controlRightInset = controlRightInset;
            this.labelReserve = labelReserve;
            this.rowTextInset = rowTextInset;
            this.hideDescription = hideDescription;
            this.showSidebarSubtitle = showSidebarSubtitle;
        }

        private static LayoutMetrics forScreen(int screenWidth, int screenHeight) {
            int marginX = screenWidth < 900 ? 20 : 32;
            int marginY = screenHeight < 620 ? 16 : 32;
            int panelWidth = Math.min(Math.max(320, screenWidth - marginX * 2), 1040);
            int panelHeight = Math.min(Math.max(280, screenHeight - marginY * 2), 600);
            int left = Math.max(0, (screenWidth - panelWidth) / 2);
            int top = Math.max(0, (screenHeight - panelHeight) / 2);
            int right = left + panelWidth;
            int bottom = top + panelHeight;

            boolean compact = panelWidth < 920 || panelHeight < 540;
            boolean veryCompact = panelWidth < 720 || panelHeight < 440;

            int sidebarWidth = panelWidth < 680 ? 156 : panelWidth < 820 ? 184 : compact ? 204 : 230;
            int contentGap = veryCompact ? 10 : compact ? 14 : 20;
            int contentRightInset = veryCompact ? 14 : 20;
            int minContentWidth = veryCompact ? 180 : 240;
            if (panelWidth - sidebarWidth - contentGap - contentRightInset < minContentWidth) {
                sidebarWidth = Math.max(132, panelWidth - minContentWidth - contentGap - contentRightInset);
            }

            int headerInset = veryCompact ? 16 : 24;
            boolean showSidebarSubtitle = !veryCompact;
            int sidebarInset = veryCompact ? 12 : 18;
            int sidebarTop = top + (showSidebarSubtitle ? 66 : 52);
            int sidebarHeight = Math.max(120, panelHeight - (showSidebarSubtitle ? 84 : 66));
            int contentLeft = left + sidebarWidth + contentGap;
            int contentWidth = Math.max(minContentWidth, panelWidth - sidebarWidth - contentGap - contentRightInset);
            int searchHeight = veryCompact ? 20 : 24;
            int searchTop = top + (showSidebarSubtitle ? 22 : 18);
            int scrollTop = searchTop + searchHeight + (veryCompact ? 10 : 12);
            int scrollHeight = Math.max(120, panelHeight - (scrollTop - top) - (veryCompact ? 10 : 16));

            int headerRowHeight = veryCompact ? 50 : 56;
            int rowHeight = veryCompact ? 64 : compact ? 72 : 82;
            int rowGap = veryCompact ? 8 : compact ? 10 : 12;
            int controlHeight = searchHeight;
            int controlTop = veryCompact ? 20 : compact ? 24 : 28;
            int toggleWidth = veryCompact ? 84 : 104;
            int textControlWidth = veryCompact ? 220 : compact ? 300 : 364;
            int minTextControlWidth = veryCompact ? 136 : compact ? 168 : 220;
            int buttonWidth = veryCompact ? 152 : compact ? 184 : 208;
            int minButtonWidth = veryCompact ? 108 : 136;
            int controlRightInset = 12;
            int labelReserve = veryCompact ? 124 : compact ? 168 : 240;
            int rowTextInset = veryCompact ? 12 : 14;
            int sidebarGap = veryCompact ? 6 : 8;

            return new LayoutMetrics(
                    left,
                    top,
                    right,
                    bottom,
                    panelWidth,
                    panelHeight,
                    sidebarWidth,
                    sidebarInset,
                    sidebarTop,
                    sidebarHeight,
                    sidebarGap,
                    headerInset,
                    contentLeft,
                    contentWidth,
                    searchTop,
                    searchHeight,
                    scrollTop,
                    scrollHeight,
                    headerRowHeight,
                    rowHeight,
                    rowGap,
                    controlHeight,
                    controlTop,
                    toggleWidth,
                    textControlWidth,
                    minTextControlWidth,
                    buttonWidth,
                    minButtonWidth,
                    controlRightInset,
                    labelReserve,
                    rowTextInset,
                    veryCompact,
                    showSidebarSubtitle
            );
        }
    }

    private static final class SettingRow extends UiComponent {
        private enum ControlKind {
            HEADER,
            TOGGLE,
            TEXT,
            ENUM,
            BUTTON
        }

        private final SettingSection section;
        private final String title;
        private final String description;
        private final String titleLower;
        private final String descriptionLower;
        private final ControlKind controlKind;
        private final UiToggle toggle;
        private final UiTextField textField;
        private final UiDropdown<?> dropdown;
        private final UiButton button;
        private LayoutMetrics layout = LayoutMetrics.forScreen(1280, 720);

        private SettingRow(SettingSection section, String title, String description, ControlKind controlKind, UiToggle toggle, UiTextField textField, UiDropdown<?> dropdown, UiButton button) {
            this.section = section;
            this.title = title;
            this.description = description;
            this.titleLower = title.toLowerCase(Locale.ROOT);
            this.descriptionLower = description.toLowerCase(Locale.ROOT);
            this.controlKind = controlKind;
            this.toggle = toggle;
            this.textField = textField;
            this.dropdown = dropdown;
            this.button = button;
            this.height = layout.rowHeight;
        }

        static SettingRow toggle(SettingSection section, String title, String description, UiToggle.BooleanGetter getter, UiToggle.BooleanSetter setter) {
            UiToggle toggle = new UiToggle(Text.literal(""), getter, setter);
            return new SettingRow(section, title, description, ControlKind.TOGGLE, toggle, null, null, null);
        }

        static SettingRow header(SettingSection section, String title, String description) {
            return new SettingRow(section, title, description, ControlKind.HEADER, null, null, null, null);
        }

        static SettingRow text(SettingSection section, String title, String description, Supplier<String> getter, Consumer<String> setter, int maxLength) {
            UiTextField textField = new UiTextField("");
            textField.setMaxLength(Math.max(1, maxLength));
            textField.setText(getter.get());
            textField.setListener(setter::accept);
            return new SettingRow(section, title, description, ControlKind.TEXT, null, textField, null, null);
        }

        static <E> SettingRow enumSelector(
                SettingSection section,
                String title,
                String description,
                E[] values,
                Supplier<E> getter,
                Consumer<E> setter,
                Function<E, String> formatter
        ) {
            UiDropdown<E> dropdown = new UiDropdown<>(values, getter, setter, formatter);
            return new SettingRow(section, title, description, ControlKind.ENUM, null, null, dropdown, null);
        }

        static SettingRow button(SettingSection section, String title, String description, String buttonLabel, ActionHandler action) {
            UiButton button = new UiButton(Text.literal(buttonLabel), () -> action.run());
            return new SettingRow(section, title, description, ControlKind.BUTTON, null, null, null, button);
        }

        void setLayout(LayoutMetrics layout) {
            if (layout == null) {
                return;
            }
            this.layout = layout;
            this.height = preferredHeight();
        }

        int preferredHeight() {
            return controlKind == ControlKind.HEADER ? layout.headerRowHeight : layout.rowHeight;
        }

        @Override
        public void setBounds(int x, int y, int width, int height) {
            int resolvedHeight = controlKind == ControlKind.HEADER ? layout.headerRowHeight : layout.rowHeight;
            super.setBounds(x, y, width, resolvedHeight);
            if (controlKind == ControlKind.HEADER) {
                return;
            }
            int controlY = y + layout.controlTop;
            if (controlKind == ControlKind.TOGGLE && toggle != null) {
                toggle.setBounds(x + width - layout.controlRightInset - layout.toggleWidth, controlY, layout.toggleWidth, layout.controlHeight);
            } else if (controlKind == ControlKind.TEXT && textField != null) {
                int controlWidth = resolveControlWidth(layout.textControlWidth, layout.minTextControlWidth);
                textField.setBounds(x + width - layout.controlRightInset - controlWidth, controlY, controlWidth, layout.controlHeight);
            } else if (controlKind == ControlKind.ENUM && dropdown != null) {
                int controlWidth = resolveControlWidth(layout.textControlWidth, layout.minTextControlWidth);
                dropdown.setBounds(x + width - layout.controlRightInset - controlWidth, controlY, controlWidth, layout.controlHeight);
            } else if (controlKind == ControlKind.BUTTON && button != null) {
                int controlWidth = resolveControlWidth(layout.buttonWidth, layout.minButtonWidth);
                button.setBounds(x + width - layout.controlRightInset - controlWidth, controlY, controlWidth, layout.controlHeight);
            }
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta, UiTheme theme) {
            if (!visible) {
                return;
            }

            int cardLeft = x;
            int cardTop = y;
            int cardRight = x + width;
            int cardBottom = y + height;

            if (controlKind == ControlKind.HEADER) {
                UiDraw.drawRoundedPanel(context, cardLeft, cardTop, cardRight, cardBottom, theme.cornerRadiusLarge, 0x332C4758, 0x2214212B);
            } else {
                UiDraw.drawRoundedPanel(context, cardLeft, cardTop, cardRight, cardBottom, theme.cornerRadiusLarge, theme.borderColor, theme.cardBackground);
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.textRenderer != null) {
                int textLeft = cardLeft + layout.rowTextInset;
                if (controlKind == ControlKind.HEADER) {
                    String titleText = client.textRenderer.trimToWidth(title, Math.max(96, width - 24));
                    String descriptionText = client.textRenderer.trimToWidth(description, Math.max(96, width - 24));
                    context.drawText(client.textRenderer, Text.literal(titleText), textLeft, cardTop + 10, theme.textPrimary, true);
                    context.drawText(client.textRenderer, Text.literal(descriptionText), textLeft, cardTop + 26, theme.textSecondary, false);
                } else {
                    int controlLeft = resolveControlLeft();
                    int maxTextWidth = Math.max(96, controlLeft - textLeft - 16);
                    String titleText = client.textRenderer.trimToWidth(title, maxTextWidth);
                    context.drawText(client.textRenderer, Text.literal(titleText), textLeft, cardTop + 12, theme.textPrimary, true);
                    if (!layout.hideDescription) {
                        String descriptionText = client.textRenderer.trimToWidth(description, maxTextWidth);
                        context.drawText(client.textRenderer, Text.literal(descriptionText), textLeft, cardTop + 30, theme.textSecondary, false);
                    }
                }
            }

            if (controlKind == ControlKind.TOGGLE && toggle != null) {
                toggle.render(context, mouseX, mouseY, delta, theme);
            } else if (controlKind == ControlKind.TEXT && textField != null) {
                textField.render(context, mouseX, mouseY, delta, theme);
            } else if (controlKind == ControlKind.ENUM && dropdown != null) {
                dropdown.render(context, mouseX, mouseY, delta, theme);
            } else if (controlKind == ControlKind.BUTTON && button != null) {
                button.render(context, mouseX, mouseY, delta, theme);
            }
        }

        @Override
        public boolean mouseClicked(Click click, boolean dblClick) {
            if (controlKind == ControlKind.HEADER) {
                return false;
            }
            if (controlKind == ControlKind.TOGGLE && toggle != null) {
                return toggle.mouseClicked(click, dblClick);
            }
            if (controlKind == ControlKind.TEXT && textField != null) {
                return textField.mouseClicked(click, dblClick);
            }
            if (controlKind == ControlKind.ENUM && dropdown != null) {
                return dropdown.mouseClicked(click, dblClick);
            }
            if (controlKind == ControlKind.BUTTON && button != null) {
                return button.mouseClicked(click, dblClick);
            }
            return false;
        }

        @Override
        public boolean mouseReleased(Click click) {
            if (controlKind == ControlKind.HEADER) {
                return false;
            }
            if (controlKind == ControlKind.TOGGLE && toggle != null) {
                return toggle.mouseReleased(click);
            }
            if (controlKind == ControlKind.ENUM && dropdown != null) {
                return dropdown.mouseReleased(click);
            }
            if (controlKind == ControlKind.BUTTON && button != null) {
                return button.mouseReleased(click);
            }
            return false;
        }

        @Override
        public void renderOverlay(DrawContext context, int mouseX, int mouseY, float delta, UiTheme theme) {
            if (!visible) {
                return;
            }
            if (controlKind == ControlKind.ENUM && dropdown != null) {
                dropdown.renderOverlay(context, mouseX, mouseY, delta, theme);
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (controlKind == ControlKind.TEXT && textField != null) {
                return textField.keyPressed(keyCode, scanCode, modifiers);
            }
            return false;
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (controlKind == ControlKind.TEXT && textField != null) {
                return textField.charTyped(chr, modifiers);
            }
            return false;
        }

        private int resolveControlWidth(int preferredWidth, int minimumWidth) {
            int availableWidth = width - layout.labelReserve - layout.controlRightInset;
            return Math.max(minimumWidth, Math.min(preferredWidth, availableWidth));
        }

        private int resolveControlLeft() {
            if (toggle != null) {
                return toggle.getX();
            }
            if (textField != null) {
                return textField.getX();
            }
            if (dropdown != null) {
                return dropdown.getX();
            }
            if (button != null) {
                return button.getX();
            }
            return x + width - layout.controlRightInset;
        }
    }

    @FunctionalInterface
    private interface ActionHandler {
        boolean run();
    }
}

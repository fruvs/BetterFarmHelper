package com.jelly.farmhelper.fabric.config;

import com.jelly.farmhelper.fabric.config.struct.RewarpPoint;
import com.jelly.farmhelper.fabric.feature.FeatureCatalog;
import com.jelly.farmhelper.fabric.macro.LegacyMacroType;
import com.jelly.farmhelper.fabric.macro.MacroPattern;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FarmHelperConfig {
    public int schemaVersion = 11;

    // Keybind defaults use GLFW keycodes.
    public int toggleMacroKey = GLFW.GLFW_KEY_GRAVE_ACCENT;
    public int startMacroKey = GLFW.GLFW_KEY_F7;
    public int stopMacroKey = GLFW.GLFW_KEY_F9;
    public int openMenuKey = GLFW.GLFW_KEY_F;
    public int triggerFailsafeKey = GLFW.GLFW_KEY_F8;
    public int freelookKey = GLFW.GLFW_KEY_L;
    public int cancelFailsafeKey = GLFW.GLFW_KEY_UNKNOWN;
    public int toggleUngrabMouseKey = GLFW.GLFW_KEY_UNKNOWN;
    public int plotCleaningHelperKey = GLFW.GLFW_KEY_P;
    public int triggerPestsDestroyerKey = GLFW.GLFW_KEY_UNKNOWN;
    public int tpToInfestedPlotKey = GLFW.GLFW_KEY_UNKNOWN;

    // General + macro
    public LegacyMacroType macroType = LegacyMacroType.S_V_NORMAL_TYPE;
    public MacroPattern macroPattern = MacroPattern.S_SHAPE;
    public boolean useLegacyProfileDefaults = true;
    public boolean alwaysHoldW = false;
    public boolean holdAttackWhileMacroing = true;
    public boolean useCustomFarmingSpeed = false;
    public int farmingSpeed = 400;
    public boolean rotateAfterWarped = false;
    public boolean rotateAfterDrop = false;
    public boolean dontFixAfterWarping = false;
    public boolean customPitch = false;
    public float customPitchLevel = 0f;
    public boolean customYaw = false;
    public float customYawLevel = 0f;
    public boolean autoChooseTool = false;
    public boolean drawSpawnLocation = true;
    public int spawnPosX = 0;
    public int spawnPosY = 0;
    public int spawnPosZ = 0;
    public float spawnYaw = 0f;
    public float spawnPitch = 0f;
    public int spawnPlot = 0;
    public boolean highlightRewarp = true;
    public int rewarpActivationRadius = 2;
    public int rewarpDelayMs = 400;
    public int rewarpDelayRandomnessMs = 350;
    public List<RewarpPoint> rewarpPoints = new ArrayList<>();

    // Movement tuning
    public int forwardTicksBeforeTurn = 140;
    public int sideStepTicks = 16;
    public int stationaryFailsafeTicks = 200;
    public int schedulerFarmingTimeMinutes = 60;
    public int schedulerBreakTimeMinutes = 5;
    public int schedulerFarmingTimeRandomnessMinutes = 5;
    public int schedulerBreakTimeRandomnessMinutes = 5;
    public int schedulerWaitForRewarpTimeoutSeconds = 120;
    public boolean useLegacyRandomDelays = true;
    public int timeBetweenChangingRowsMs = 400;
    public int randomTimeBetweenChangingRowsMs = 200;
    public int rotationTimeMs = 500;
    public int rotationTimeRandomnessMs = 300;
    public boolean playerSimulationEnabled = true;
    public int playerSimulationPauseChancePct = 2;
    public int playerSimulationPauseCheckIntervalTicks = 80;
    public int playerSimulationPauseMinTicks = 1;
    public int playerSimulationPauseMaxTicks = 2;
    public int playerSimulationStrafeWobbleChancePct = 2;
    public float playerSimulationYawJitterDegrees = 1.1f;
    public float playerSimulationPitchJitterDegrees = 0.65f;
    public int playerSimulationJitterIntervalMinTicks = 12;
    public int playerSimulationJitterIntervalMaxTicks = 45;

    // Failsafes
    public boolean enableFailsafes = true;
    public boolean popUpNotifications = true;
    public boolean autoAltTab = false;
    public boolean failsafeActionDisableOnly = false;
    public int failsafeStopDelayMs = 2000;
    public boolean autoWarpOnWorldChange = true;
    public boolean autoEvacuateOnServerReboot = true;
    public boolean autoReconnect = true;
    public int autoReconnectDelaySeconds = 5;
    public int autoReconnectMaxAttempts = 3;
    public boolean lagDetectorEnabled = true;
    public boolean pauseOnGuestArrival = false;
    public float teleportLagTolerance = 0.5f;
    public int detectionTimeWindowMs = 500;
    public float pitchSensitivity = 7f;
    public float yawSensitivity = 5f;
    public float teleportDistanceThreshold = 4f;
    public float verticalKnockbackThreshold = 4000f;
    public boolean enableBpsCheck = true;
    public boolean enablePacketFailsafeChecks = true;
    public float minBpsThreshold = 10f;
    public boolean enableFailsafeSound = true;
    public boolean enableFailsafeBanner = true;
    public boolean failsafeBannerShowReason = true;
    public boolean enableFailsafeAnvilAlert = true;
    public int failsafeAnvilAlertIntervalTicks = 12;
    public float failsafeAnvilAlertVolume = 1.0f;
    public int desktopNotificationCooldownSeconds = 20;
    public boolean restartAfterFailsafe = true;
    public int restartAfterFailsafeDelayMinutes = 0;
    public boolean enableJacobFailsafe = false;
    public boolean alwaysTeleportToGarden = false;
    public boolean sendFailsafeChatMessage = false;
    public boolean enableCustomFailsafeReactions = false;
    public int customFailsafeReactionMinMs = 800;
    public int customFailsafeReactionMaxMs = 2200;
    public boolean customFailsafeWarpToGarden = true;
    public boolean customFailsafeSendSecondMessage = true;

    // Modules
    public boolean enableScheduler = true;
    public boolean pauseSchedulerDuringJacobsContest = true;
    public boolean schedulerDisconnectDuringBreak = false;
    public boolean schedulerWaitUntilRewarp = false;
    public boolean schedulerResetOnDisable = true;
    public boolean antiStuckEnabled = false;
    public int antiStuckStationaryTicks = 95;
    public int antiStuckTriesUntilWarp = 3;
    public boolean visitorsMacro = false;
    public int visitorsMacroMinVisitors = 5;
    public int visitorsMacroActionSeconds = 12;
    public boolean visitorsMacroAutosellBeforeServing = false;
    public int visitorsMacroMinMoney = 2_000;
    public float visitorsMacroMaxSpendLimit = 0.7f;
    public boolean visitorsMacroAfkInfiniteMode = false;
    public int visitorsMacroMaxVisitorsPerCycle = 3;
    public int visitorsMacroRetryLimit = 3;
    public String visitorsMacroBlacklistCsv = "";
    public String visitorsMacroWhitelistCsv = "";

    public boolean autoCookie = false;
    public int autoCookieCheckMinutes = 30;
    public boolean autoCookieAutoBuy = true;
    public boolean autoCookieReturnToGarden = true;

    public boolean autoSprayonator = false;
    public int autoSprayonatorCheckMinutes = 20;
    public int autoSprayonatorActionSeconds = 24;
    public String autoSprayonatorMaterial = "Compost";
    public boolean autoSprayonatorAutoBuyItem = true;
    public int autoSprayonatorAutoBuyAmount = 256;

    public boolean autoBazaar = false;
    public int autoBazaarActionSeconds = 20;

    public boolean autoWardrobe = false;
    public int autoWardrobeActionSeconds = 18;
    public int autoWardrobePreferredSlot = 1;

    public boolean enablePestsDestroyer = false;
    public int startKillingPestsAt = 3;
    public int pestsDestroyerActionSeconds = 15;
    public boolean pestsDestroyerAfkInfiniteMode = false;
    public int pestsDestroyerMaxPasses = 3;
    public int pestsDestroyerRetryLimit = 3;
    public boolean pestsDestroyerDisableDuringJacobsContest = true;
    public boolean pestsDestroyerStartOnlyOnRewarpOrSpawn = false;
    public boolean pestsDestroyerOnTheTrack = false;
    public int pestsDestroyerOnTrackPersistTicks = 30;
    public int pestsDestroyerOnTrackStuckMs = 7000;
    public float pestsDestroyerOnTrackRadius = 8f;
    public int pestsDestroyerOnTrackFov = 95;
    public float pestsAimDeadzoneYaw = 4.5f;
    public float pestsAimDeadzonePitch = 3.0f;
    public boolean pestsTracers = true;
    public boolean pestsHighlightBox = true;
    public int pestsTracerColor = 0xAB00FFD9;
    public int pestsBoxColor = 0xAB56D8FF;

    public boolean pestFarmer = false;
    public int pestFarmerWaitSeconds = 8;
    public boolean pestFarmerSwapEquipment = false;
    public int pestFarmerBiohazardSlot = 1;
    public int pestFarmerFermentoSlot = 2;
    public String pestFarmerBiohazardEquipment = "";
    public String pestFarmerFermentoEquipment = "";
    public boolean pestFarmerKillPests = true;
    public boolean pestFarmerSetSpawn = false;

    public boolean enableAutoSell = false;
    public boolean autoSellMarketTypeNpc = false;
    public boolean autoSellSacks = false;
    public boolean autoSellSacksPlacement = true;
    public int inventoryFullTimeSeconds = 6;
    public int inventoryFullRatio = 65;
    public int autoSellCommandCooldownSeconds = 8;
    public boolean pauseAutoSellDuringJacobsContest = false;
    public boolean autoSellRunes = true;
    public boolean autoSellDeadBush = true;
    public boolean autoSellIronHoe = true;
    public boolean autoSellPestVinyls = true;
    public String autoSellCustomItems = "";

    public boolean autoComposter = false;
    public int autoComposterMinMoney = 2_000;
    public float autoComposterMaxSpendLimit = 1.5f;
    public int autoComposterOrganicMatterLeft = 30_000;
    public int autoComposterFuelLeft = 15_000;
    public boolean autoComposterAutosellBeforeFilling = false;
    public int composterX = -11;
    public int composterY = 72;
    public int composterZ = -27;

    public boolean autoPestExchange = false;
    public int autoPestExchangeMinPests = 10;
    public int autoPestExchangeActionSeconds = 10;
    public boolean autoPestExchangeOnlyStartRelevant = false;
    public boolean logAutoPestExchangeEvents = true;
    public int autoPestExchangeRetryLimit = 3;
    public int pestExchangeDeskX = -24;
    public int pestExchangeDeskY = 71;
    public int pestExchangeDeskZ = -7;

    public boolean autoGodPot = false;
    public int autoGodPotCheckMinutes = 30;
    public boolean autoGodPotFromBackpack = true;

    public boolean autoRepellent = false;
    public int autoRepellentCheckMinutes = 20;
    public boolean pestRepellentType = true;

    public boolean bpsTrackerEnabled = true;
    public boolean checkDesync = true;
    public int desyncPauseDelayMs = 4_500;
    public int desyncStationaryTicks = 110;
    public boolean profitCalculatorEnabled = true;
    public boolean countRngToProfitCalc = true;
    public boolean profitCalcCountPestDrop = true;

    public boolean enablePetSwapper = false;
    public String petSwapperName = "";
    public String petSwapperRestoreName = "";
    public int petSwapperActionSeconds = 8;
    public boolean petSwapperSwapBackAfterContest = true;

    public boolean plotCleaningHelper = false;
    public int plotCleaningScanRadius = 4;
    public int plotCleaningActionSeconds = 15;
    public boolean plotCleaningOnlyDuringCleanup = true;
    public boolean plotCleaningBreakWood = true;
    public boolean plotCleaningBreakStone = true;

    public boolean performanceMode = false;
    public int performanceModeMaxFps = 30;
    public int performanceModeViewDistance = 2;
    public boolean pipMode = false;
    public boolean freelook = false;
    public boolean autoUngrabMouse = true;
    public boolean movementRecorderEnabled = true;
    public boolean autoSetRancherSpeed = false;
    public int rancherSpeedCheckMinutes = 10;
    public boolean leaveTimerEnabled = false;
    public int leaveTimeMinutes = 60;

    public boolean proxyEnabled = false;
    public String proxyAddress = "";
    public String proxyType = "SOCKS";
    public String proxyUsername = "";
    public String proxyPassword = "";

    // HUD + misc
    public boolean enableStatusHud = true;
    public int statusHudX = 8;
    public int statusHudY = 8;
    public boolean enableProfitHud = true;
    public boolean enableDebugHudOverlay = false;
    public int profitHudX = 8;
    public int profitHudY = 170;
    public int debugHudX = 8;
    public int debugHudY = 250;
    public boolean debugMode = false;
    public boolean streamerMode = false;
    public boolean resetStatsBetweenDisabling = false;
    public boolean showStatusHudOutsideGarden = true;
    public boolean showStats24H = true;
    public boolean showStats7D = false;
    public boolean showStats30D = false;
    public boolean showStatsLifetime = true;
    public boolean showStatsTitle = false;
    public boolean showStatsSession = true;
    public boolean showStatsFailsafes = true;
    public boolean sendAnalyticData = false;
    public boolean changeWindowTitle = true;

    // Discord webhook notifications (remote control intentionally disabled)
    public boolean enableWebhook = false;
    public String webhookUrl = "";
    public boolean sendWebhookLogs = false;
    public boolean sendStatusUpdates = false;
    public int statusUpdateIntervalMinutes = 5;
    public boolean sendVisitorsMacroLogs = true;
    public boolean pingEveryoneOnVisitorsMacroLogs = false;
    public boolean sendMacroEnableDisableLogs = true;
    public boolean sendFailsafeLogs = true;
    public boolean sendBanwaveLogs = false;
    public boolean sendWebhookDebugLogs = false;
    public boolean writeDebugLogsToFile = true;

    // Kept as config-only placeholders while remote control is not ported.
    public boolean enableRemoteControl = false;
    public String remoteControlAddress = "127.0.0.1";
    public int remoteControlPort = 21370;

    // Feature toggle map mirrored from legacy modules.
    public Map<String, Boolean> featureToggles = defaultFeatureToggles();

    public static Map<String, Boolean> defaultFeatureToggles() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        for (FeatureCatalog.FeatureDefinition definition : FeatureCatalog.DEFINITIONS) {
            defaults.put(definition.id(), false);
        }
        defaults.put("lag_detector", true);
        defaults.put("bps_tracker", true);
        defaults.put("desync_checker", true);
        defaults.put("profit_calculator", true);
        defaults.put("scheduler", true);
        defaults.put("usage_stats_tracker", true);
        return defaults;
    }
}

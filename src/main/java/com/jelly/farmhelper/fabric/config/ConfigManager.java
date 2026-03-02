package com.jelly.farmhelper.fabric.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.feature.FeatureCatalog;
import com.jelly.farmhelper.fabric.macro.LegacyMacroType;
import com.jelly.farmhelper.fabric.macro.LegacyMacroProfiles;
import com.jelly.farmhelper.fabric.macro.MacroPattern;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("farmhelper-fabric.json");

    private FarmHelperConfig config = new FarmHelperConfig();

    public void load() {
        if (!Files.exists(configPath)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            FarmHelperConfig loaded = GSON.fromJson(reader, FarmHelperConfig.class);
            if (loaded != null) {
                config = sanitize(loaded);
            }
        } catch (IOException e) {
            FarmHelperFabric.LOGGER.error("Failed to load config from {}", configPath, e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            FarmHelperFabric.LOGGER.error("Failed to save config to {}", configPath, e);
        }
    }

    public FarmHelperConfig getConfig() {
        return config;
    }

    private FarmHelperConfig sanitize(FarmHelperConfig loaded) {
        if (loaded.macroType == null) {
            loaded.macroType = LegacyMacroType.S_V_NORMAL_TYPE;
        }
        if (loaded.macroPattern == null) {
            loaded.macroPattern = loaded.useLegacyProfileDefaults
                    ? LegacyMacroProfiles.forType(loaded.macroType).pattern()
                    : loaded.macroType.defaultPattern();
        }
        if (loaded.forwardTicksBeforeTurn <= 0) {
            loaded.forwardTicksBeforeTurn = 140;
        }
        if (loaded.sideStepTicks <= 0) {
            loaded.sideStepTicks = 16;
        }
        if (loaded.rewarpActivationRadius <= 0) {
            loaded.rewarpActivationRadius = 2;
        }
        if (loaded.rewarpDelayMs <= 0) {
            loaded.rewarpDelayMs = 400;
        }
        if (loaded.rewarpDelayRandomnessMs < 0) {
            loaded.rewarpDelayRandomnessMs = 350;
        }
        if (loaded.rewarpPoints == null) {
            loaded.rewarpPoints = new java.util.ArrayList<>();
        }
        if (loaded.stationaryFailsafeTicks <= 0) {
            loaded.stationaryFailsafeTicks = 200;
        }
        if (loaded.schedulerWaitForRewarpTimeoutSeconds <= 0) {
            loaded.schedulerWaitForRewarpTimeoutSeconds = 120;
        }
        if (loaded.timeBetweenChangingRowsMs < 0) {
            loaded.timeBetweenChangingRowsMs = 400;
        }
        if (loaded.randomTimeBetweenChangingRowsMs < 0) {
            loaded.randomTimeBetweenChangingRowsMs = 200;
        }
        if (loaded.rotationTimeMs < 100) {
            loaded.rotationTimeMs = 500;
        }
        if (loaded.rotationTimeRandomnessMs < 0) {
            loaded.rotationTimeRandomnessMs = 300;
        }
        if (loaded.statusUpdateIntervalMinutes <= 0) {
            loaded.statusUpdateIntervalMinutes = 5;
        }
        if (loaded.antiStuckStationaryTicks <= 0) {
            loaded.antiStuckStationaryTicks = 95;
        }
        if (loaded.antiStuckTriesUntilWarp <= 0) {
            loaded.antiStuckTriesUntilWarp = 3;
        }
        if (loaded.triggerFailsafeKey <= 0) {
            loaded.triggerFailsafeKey = org.lwjgl.glfw.GLFW.GLFW_KEY_F8;
        }
        if (loaded.startMacroKey <= 0) {
            loaded.startMacroKey = org.lwjgl.glfw.GLFW.GLFW_KEY_F7;
        }
        if (loaded.stopMacroKey <= 0) {
            loaded.stopMacroKey = org.lwjgl.glfw.GLFW.GLFW_KEY_F9;
        }
        if (loaded.openMenuKey == 0) {
            loaded.openMenuKey = org.lwjgl.glfw.GLFW.GLFW_KEY_F;
        }
        if (loaded.freelookKey == 0) {
            loaded.freelookKey = org.lwjgl.glfw.GLFW.GLFW_KEY_L;
        }
        if (loaded.cancelFailsafeKey == 0) {
            loaded.cancelFailsafeKey = org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN;
        }
        if (loaded.toggleUngrabMouseKey == 0) {
            loaded.toggleUngrabMouseKey = org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN;
        }
        if (loaded.plotCleaningHelperKey == 0) {
            loaded.plotCleaningHelperKey = org.lwjgl.glfw.GLFW.GLFW_KEY_P;
        }
        if (loaded.triggerPestsDestroyerKey == 0) {
            loaded.triggerPestsDestroyerKey = org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN;
        }
        if (loaded.tpToInfestedPlotKey == 0) {
            loaded.tpToInfestedPlotKey = org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN;
        }
        if (loaded.farmingSpeed <= 0) {
            loaded.farmingSpeed = 400;
        }
        if (loaded.inventoryFullRatio <= 0 || loaded.inventoryFullRatio > 100) {
            loaded.inventoryFullRatio = 65;
        }
        if (loaded.inventoryFullTimeSeconds <= 0) {
            loaded.inventoryFullTimeSeconds = 6;
        }
        if (loaded.autoSellCommandCooldownSeconds <= 0) {
            loaded.autoSellCommandCooldownSeconds = 8;
        }
        if (loaded.startKillingPestsAt <= 0) {
            loaded.startKillingPestsAt = 3;
        }
        if (loaded.autoPestExchangeMinPests <= 0) {
            loaded.autoPestExchangeMinPests = 10;
        }
        if (loaded.autoGodPotCheckMinutes <= 0) {
            loaded.autoGodPotCheckMinutes = 30;
        }
        if (loaded.autoRepellentCheckMinutes <= 0) {
            loaded.autoRepellentCheckMinutes = 20;
        }
        if (loaded.autoCookieCheckMinutes <= 0) {
            loaded.autoCookieCheckMinutes = 30;
        }
        if (loaded.autoSprayonatorCheckMinutes <= 0) {
            loaded.autoSprayonatorCheckMinutes = 20;
        }
        if (loaded.autoSprayonatorActionSeconds <= 0) {
            loaded.autoSprayonatorActionSeconds = 24;
        }
        if (loaded.autoSprayonatorAutoBuyAmount <= 0) {
            loaded.autoSprayonatorAutoBuyAmount = 256;
        }
        if (loaded.autoSprayonatorMaterial == null || loaded.autoSprayonatorMaterial.isBlank()) {
            loaded.autoSprayonatorMaterial = "Compost";
        }
        if (loaded.autoBazaarActionSeconds <= 0) {
            loaded.autoBazaarActionSeconds = 20;
        }
        if (loaded.autoWardrobeActionSeconds <= 0) {
            loaded.autoWardrobeActionSeconds = 18;
        }
        if (loaded.autoWardrobePreferredSlot <= 0) {
            loaded.autoWardrobePreferredSlot = 1;
        }
        if (loaded.autoReconnectDelaySeconds <= 0) {
            loaded.autoReconnectDelaySeconds = 5;
        }
        if (loaded.autoReconnectMaxAttempts <= 0) {
            loaded.autoReconnectMaxAttempts = 3;
        }
        if (loaded.customFailsafeReactionMinMs <= 0) {
            loaded.customFailsafeReactionMinMs = 800;
        }
        if (loaded.customFailsafeReactionMaxMs < loaded.customFailsafeReactionMinMs) {
            loaded.customFailsafeReactionMaxMs = loaded.customFailsafeReactionMinMs + 600;
        }
        if (loaded.petSwapperActionSeconds <= 0) {
            loaded.petSwapperActionSeconds = 8;
        }
        if (loaded.plotCleaningScanRadius <= 0) {
            loaded.plotCleaningScanRadius = 4;
        }
        if (loaded.plotCleaningActionSeconds <= 0) {
            loaded.plotCleaningActionSeconds = 15;
        }
        if (loaded.visitorsMacroMinVisitors <= 0) {
            loaded.visitorsMacroMinVisitors = 5;
        }
        if (loaded.visitorsMacroActionSeconds <= 0) {
            loaded.visitorsMacroActionSeconds = 12;
        }
        if (loaded.visitorsMacroMaxVisitorsPerCycle <= 0) {
            loaded.visitorsMacroMaxVisitorsPerCycle = 3;
        }
        if (loaded.visitorsMacroRetryLimit <= 0) {
            loaded.visitorsMacroRetryLimit = 3;
        }
        if (loaded.visitorsMacroBlacklistCsv == null) {
            loaded.visitorsMacroBlacklistCsv = "";
        }
        if (loaded.visitorsMacroWhitelistCsv == null) {
            loaded.visitorsMacroWhitelistCsv = "";
        }
        if (loaded.pestsDestroyerActionSeconds <= 0) {
            loaded.pestsDestroyerActionSeconds = 15;
        }
        if (loaded.pestsDestroyerMaxPasses <= 0) {
            loaded.pestsDestroyerMaxPasses = 3;
        }
        if (loaded.pestsDestroyerRetryLimit <= 0) {
            loaded.pestsDestroyerRetryLimit = 3;
        }
        if (loaded.pestsDestroyerOnTrackPersistTicks <= 0) {
            loaded.pestsDestroyerOnTrackPersistTicks = 30;
        }
        if (loaded.pestsDestroyerOnTrackStuckMs <= 0) {
            loaded.pestsDestroyerOnTrackStuckMs = 7000;
        }
        if (loaded.pestsDestroyerOnTrackRadius <= 0f) {
            loaded.pestsDestroyerOnTrackRadius = 8f;
        }
        if (loaded.pestsDestroyerOnTrackFov <= 0) {
            loaded.pestsDestroyerOnTrackFov = 95;
        }
        if (loaded.pestsAimDeadzoneYaw <= 0f) {
            loaded.pestsAimDeadzoneYaw = 4.5f;
        }
        if (loaded.pestsAimDeadzonePitch <= 0f) {
            loaded.pestsAimDeadzonePitch = 3.0f;
        }
        if (loaded.pestFarmerWaitSeconds <= 0) {
            loaded.pestFarmerWaitSeconds = 8;
        }
        if (loaded.pestFarmerBiohazardSlot <= 0) {
            loaded.pestFarmerBiohazardSlot = 1;
        }
        if (loaded.pestFarmerFermentoSlot <= 0) {
            loaded.pestFarmerFermentoSlot = 2;
        }
        if (loaded.autoPestExchangeActionSeconds <= 0) {
            loaded.autoPestExchangeActionSeconds = 10;
        }
        if (loaded.autoPestExchangeRetryLimit <= 0) {
            loaded.autoPestExchangeRetryLimit = 3;
        }
        if (loaded.desyncPauseDelayMs <= 0) {
            loaded.desyncPauseDelayMs = 4_500;
        }
        if (loaded.desyncStationaryTicks <= 0) {
            loaded.desyncStationaryTicks = 110;
        }
        if (loaded.performanceModeMaxFps < 10) {
            loaded.performanceModeMaxFps = 30;
        }
        if (loaded.performanceModeViewDistance < 2) {
            loaded.performanceModeViewDistance = 2;
        }
        if (loaded.statusHudX < 0) {
            loaded.statusHudX = 8;
        }
        if (loaded.statusHudY < 0) {
            loaded.statusHudY = 8;
        }
        if (loaded.profitHudX < 0) {
            loaded.profitHudX = 8;
        }
        if (loaded.profitHudY < 0) {
            loaded.profitHudY = 170;
        }
        if (loaded.debugHudX < 0) {
            loaded.debugHudX = 8;
        }
        if (loaded.debugHudY < 0) {
            loaded.debugHudY = 250;
        }
        if (loaded.rancherSpeedCheckMinutes <= 0) {
            loaded.rancherSpeedCheckMinutes = 10;
        }
        if (loaded.proxyType == null || loaded.proxyType.isBlank()) {
            loaded.proxyType = "SOCKS";
        }
        loaded.showStatsSession = loaded.showStatsSession || (!loaded.showStats24H && !loaded.showStats7D && !loaded.showStats30D);
        if (loaded.composterY <= 0) {
            loaded.composterX = -11;
            loaded.composterY = 72;
            loaded.composterZ = -27;
        }
        if (loaded.pestExchangeDeskY <= 0) {
            loaded.pestExchangeDeskX = -24;
            loaded.pestExchangeDeskY = 71;
            loaded.pestExchangeDeskZ = -7;
        }
        if (loaded.featureToggles == null) {
            loaded.featureToggles = FarmHelperConfig.defaultFeatureToggles();
        } else {
            for (FeatureCatalog.FeatureDefinition definition : FeatureCatalog.DEFINITIONS) {
                loaded.featureToggles.putIfAbsent(definition.id(), false);
            }
        }
        loaded.schemaVersion = Math.max(8, loaded.schemaVersion);
        return loaded;
    }
}

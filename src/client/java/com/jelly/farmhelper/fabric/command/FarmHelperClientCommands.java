package com.jelly.farmhelper.fabric.command;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.FarmHelperFabricClient;
import com.jelly.farmhelper.fabric.config.struct.RewarpPoint;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.feature.FeatureCatalog;
import com.jelly.farmhelper.fabric.macro.LegacyMacroType;
import com.jelly.farmhelper.fabric.macro.LegacyMacroProfiles;
import com.jelly.farmhelper.fabric.macro.MacroPattern;
import com.jelly.farmhelper.fabric.state.FreelookController;
import com.jelly.farmhelper.fabric.util.Chat;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class FarmHelperClientCommands {
    private FarmHelperClientCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("fh")
                .then(literal("toggle")
                        .executes(ctx -> {
                            FarmHelperFabric.getMacroController().toggle();
                            if (!FarmHelperFabric.getMacroController().isToggled()) {
                                FarmHelperFabricClient.requestGlobalStop();
                                FarmHelperFabric.getFeatureManager().cancelMacroExclusiveActions("command toggle off");
                                FarmHelperFabric.getClientActionQueue().clear();
                            } else {
                                FarmHelperFabricClient.clearGlobalStopLatch();
                            }
                            FarmHelperFabric.getWebhookService().debugTrace("command", "/fh toggle");
                            Chat.info("Macro " + (FarmHelperFabric.getMacroController().isToggled() ? "enabled" : "disabled"));
                            return 1;
                        }))
                .then(literal("start")
                        .executes(ctx -> {
                            FarmHelperFabricClient.clearGlobalStopLatch();
                            FarmHelperFabric.getMacroController().enable();
                            FarmHelperFabric.getWebhookService().debugTrace("command", "/fh start");
                            Chat.info("Macro enabled");
                            return 1;
                        }))
                .then(literal("stop")
                        .executes(ctx -> {
                            FarmHelperFabricClient.requestGlobalStop();
                            FarmHelperFabric.getMacroController().disableByUser();
                            FarmHelperFabric.getFeatureManager().cancelMacroExclusiveActions("command stop");
                            FarmHelperFabric.getClientActionQueue().clear();
                            FarmHelperFabric.getWebhookService().debugTrace("command", "/fh stop");
                            Chat.info("Macro disabled");
                            return 1;
                        }))
                .then(literal("status")
                        .executes(ctx -> {
                            String failsafe = FarmHelperFabric.getFailsafeManager().getActiveFailsafe()
                                    .map(Enum::name)
                                    .orElse("NONE");
                            Chat.info("State=" + FarmHelperFabric.getMacroController().getState()
                                    + " RuntimeTicks=" + FarmHelperFabric.getMacroController().getRuntimeTicks()
                                    + " MacroType=" + FarmHelperFabric.getConfigManager().getConfig().macroType
                                    + " Pattern=" + FarmHelperFabric.getConfigManager().getConfig().macroPattern
                                    + " Failsafe=" + failsafe);
                            return 1;
                        }))
                .then(literal("macro")
                        .then(literal("list")
                                .executes(ctx -> {
                                    StringBuilder builder = new StringBuilder("Macro types: ");
                                    LegacyMacroType[] values = LegacyMacroType.values();
                                    for (int i = 0; i < values.length; i++) {
                                        builder.append(values[i].name());
                                        if (i + 1 < values.length) {
                                            builder.append(", ");
                                        }
                                    }
                                    Chat.info(builder.toString());
                                    return 1;
                                }))
                        .then(literal("type")
                                .then(argument("type", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String typeText = StringArgumentType.getString(ctx, "type").trim().toUpperCase();
                                            try {
                                                LegacyMacroType type = LegacyMacroType.valueOf(typeText);
                                                FarmHelperFabric.getConfigManager().getConfig().macroType = type;
                                                if (FarmHelperFabric.getConfigManager().getConfig().useLegacyProfileDefaults) {
                                                    var profile = LegacyMacroProfiles.forType(type);
                                                    FarmHelperFabric.getConfigManager().getConfig().macroPattern = profile.pattern();
                                                    FarmHelperFabric.getConfigManager().getConfig().forwardTicksBeforeTurn = profile.defaultForwardTicks();
                                                    FarmHelperFabric.getConfigManager().getConfig().sideStepTicks = profile.defaultSideStepTicks();
                                                } else {
                                                    FarmHelperFabric.getConfigManager().getConfig().macroPattern = type.defaultPattern();
                                                }
                                                FarmHelperFabric.getConfigManager().save();
                                                Chat.info("Macro type set to " + type.name());
                                            } catch (IllegalArgumentException ex) {
                                                Chat.info("Unknown macro type: " + typeText);
                                            }
                                            return 1;
                                        })))
                        .then(literal("pattern")
                                .then(argument("pattern", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String patternText = StringArgumentType.getString(ctx, "pattern");
                                            try {
                                                MacroPattern pattern = MacroPattern.valueOf(patternText.toUpperCase());
                                                FarmHelperFabric.getConfigManager().getConfig().macroPattern = pattern;
                                                FarmHelperFabric.getConfigManager().save();
                                                Chat.info("Macro pattern set to " + pattern.name());
                                            } catch (IllegalArgumentException ex) {
                                                Chat.info("Unknown pattern: " + patternText + " (BASIC_ROW, S_SHAPE)");
                                            }
                                            return 1;
                                        }))))
                .then(literal("feature")
                        .then(literal("list")
                                .executes(ctx -> {
                                    StringBuilder line = new StringBuilder("Features: ");
                                    int i = 0;
                                    for (FeatureCatalog.FeatureDefinition definition : FeatureCatalog.DEFINITIONS) {
                                        line.append(definition.id());
                                        if (i + 1 < FeatureCatalog.DEFINITIONS.size()) {
                                            line.append(", ");
                                        }
                                        i++;
                                    }
                                    Chat.info(line.toString());
                                    return 1;
                                }))
                        .then(argument("id", StringArgumentType.word())
                                .then(argument("enabled", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            String enabledText = StringArgumentType.getString(ctx, "enabled");
                                            boolean enabled = enabledText.equalsIgnoreCase("true")
                                                    || enabledText.equalsIgnoreCase("on")
                                                    || enabledText.equalsIgnoreCase("enable")
                                                    || enabledText.equalsIgnoreCase("1");
                                            if (!FeatureCatalog.isValidId(id)) {
                                                Chat.info("Unknown feature id: " + id);
                                                return 1;
                                            }
                                            FarmHelperFabric.getFeatureManager().setFeatureEnabled(id, enabled);
                                            syncConfigForFeature(id, enabled);
                                            applyImmediateFeatureState(id, enabled);
                                            FarmHelperFabric.getConfigManager().save();
                                            Chat.info("Feature " + id + " set to " + enabled);
                                            return 1;
                                        }))))
                .then(literal("ui")
                        .executes(ctx -> {
                            MinecraftClient client = MinecraftClient.getInstance();
                            client.execute(() -> client.setScreen(FarmHelperFabricClient.createConfigScreen(client.currentScreen)));
                            return 1;
                        }))
                .then(literal("rewarp")
                        .then(literal("add")
                                .executes(ctx -> {
                                    MinecraftClient client = MinecraftClient.getInstance();
                                    if (client.player == null) {
                                        Chat.info("Not in world");
                                        return 1;
                                    }
                                    RewarpPoint point = new RewarpPoint(
                                            client.player.getBlockX(),
                                            client.player.getBlockY(),
                                            client.player.getBlockZ(),
                                            client.player.getYaw(),
                                            client.player.getPitch()
                                    );
                                    if (FarmHelperFabric.getConfigManager().getConfig().rewarpPoints.stream().anyMatch(existing ->
                                            existing.x == point.x && existing.y == point.y && existing.z == point.z)) {
                                        Chat.info("Rewarp point already exists at this location");
                                        return 1;
                                    }
                                    point.normalizeInPlace(FarmHelperFabric.getConfigManager().getConfig().rewarpPoints.size() + 1);
                                    FarmHelperFabric.getConfigManager().getConfig().rewarpPoints.add(point);
                                    FarmHelperFabric.getConfigManager().save();
                                    Chat.info("Added rewarp point " + point.displayName(FarmHelperFabric.getConfigManager().getConfig().rewarpPoints.size())
                                            + " at " + point.x + " " + point.y + " " + point.z);
                                    return 1;
                                }))
                        .then(literal("remove")
                                .executes(ctx -> {
                                    MinecraftClient client = MinecraftClient.getInstance();
                                    if (client.player == null) {
                                        Chat.info("Not in world");
                                        return 1;
                                    }
                                    RewarpPoint closest = findClosestRewarpPoint(client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ());
                                    if (closest == null) {
                                        Chat.info("No rewarp points to remove");
                                        return 1;
                                    }
                                    FarmHelperFabric.getConfigManager().getConfig().rewarpPoints.remove(closest);
                                    FarmHelperFabric.getConfigManager().save();
                                    Chat.info("Removed rewarp point at " + closest.x + " " + closest.y + " " + closest.z);
                                    return 1;
                                }))
                        .then(literal("clear")
                                .executes(ctx -> {
                                    FarmHelperFabric.getConfigManager().getConfig().rewarpPoints.clear();
                                    FarmHelperFabric.getConfigManager().save();
                                    Chat.info("Cleared all rewarp points");
                                    return 1;
                                }))
                        .then(literal("list")
                                .executes(ctx -> {
                                    if (FarmHelperFabric.getConfigManager().getConfig().rewarpPoints.isEmpty()) {
                                        Chat.info("No rewarp points configured");
                                        return 1;
                                    }
                                    StringBuilder line = new StringBuilder("Rewarp points: ");
                                    for (int i = 0; i < FarmHelperFabric.getConfigManager().getConfig().rewarpPoints.size(); i++) {
                                        RewarpPoint point = FarmHelperFabric.getConfigManager().getConfig().rewarpPoints.get(i);
                                        line.append("#")
                                                .append(i + 1)
                                                .append(":")
                                                .append(point.displayName(i + 1))
                                                .append("(")
                                                .append(point.x)
                                                .append(",")
                                                .append(point.y)
                                                .append(",")
                                                .append(point.z)
                                                .append(")");
                                        if (i + 1 < FarmHelperFabric.getConfigManager().getConfig().rewarpPoints.size()) {
                                            line.append(" ");
                                        }
                                    }
                                    Chat.info(line.toString());
                                    return 1;
                                })))
                .then(literal("spawn")
                        .then(literal("set")
                                .executes(ctx -> {
                                    MinecraftClient client = MinecraftClient.getInstance();
                                    if (client.player == null) {
                                        Chat.info("Not in world");
                                        return 1;
                                    }
                                    FarmHelperFabric.getConfigManager().getConfig().spawnPosX = client.player.getBlockX();
                                    FarmHelperFabric.getConfigManager().getConfig().spawnPosY = client.player.getBlockY();
                                    FarmHelperFabric.getConfigManager().getConfig().spawnPosZ = client.player.getBlockZ();
                                    FarmHelperFabric.getConfigManager().getConfig().spawnYaw = client.player.getYaw();
                                    FarmHelperFabric.getConfigManager().getConfig().spawnPitch = client.player.getPitch();
                                    FarmHelperFabric.getConfigManager().save();
                                    Chat.info("Spawn anchor set");
                                    return 1;
                                }))
                        .then(literal("reset")
                                .executes(ctx -> {
                                    FarmHelperFabric.getConfigManager().getConfig().spawnPosX = 0;
                                    FarmHelperFabric.getConfigManager().getConfig().spawnPosY = 0;
                                    FarmHelperFabric.getConfigManager().getConfig().spawnPosZ = 0;
                                    FarmHelperFabric.getConfigManager().getConfig().spawnYaw = 0f;
                                    FarmHelperFabric.getConfigManager().getConfig().spawnPitch = 0f;
                                    FarmHelperFabric.getConfigManager().getConfig().spawnPlot = 0;
                                    FarmHelperFabric.getConfigManager().save();
                                    Chat.info("Spawn anchor reset");
                                    return 1;
                                })))
                .then(literal("failsafe")
                        .then(literal("clear")
                                .executes(ctx -> {
                                    boolean cleared = FarmHelperFabric.getFailsafeManager().cancelFailsafeAndResumeMacro();
                                    Chat.info(cleared ? "Failsafe cleared" : "No active failsafe");
                                    return 1;
                                }))
                        .then(argument("type", StringArgumentType.word())
                                .executes(ctx -> {
                                    String typeText = StringArgumentType.getString(ctx, "type");
                                    try {
                                        FailsafeType type = FailsafeType.valueOf(typeText.toUpperCase());
                                        FarmHelperFabric.getFailsafeManager().trigger(type, "Manual command");
                                        Chat.info("Failsafe triggered: " + type.name());
                                    } catch (IllegalArgumentException ex) {
                                        Chat.info("Unknown failsafe type: " + typeText);
                                    }
                                    return 1;
                                })))
                .then(literal("config")
                        .then(literal("reload")
                                .executes(ctx -> {
                                    FarmHelperFabric.getConfigManager().load();
                                    FarmHelperFabric.getFeatureManager().syncWithConfig();
                                    Chat.info("Config reloaded");
                                    return 1;
                                }))
                        .then(literal("save")
                                .executes(ctx -> {
                                    FarmHelperFabric.getConfigManager().save();
                                    Chat.info("Config saved");
                                    return 1;
                                })))
                .then(literal("debuglog")
                        .then(literal("send")
                                .executes(ctx -> {
                                    FarmHelperFabric.getWebhookService().sendCurrentDebugLog("manual command");
                                    Chat.info("Requested debug log upload");
                                    return 1;
                                }))
                        .then(literal("path")
                                .executes(ctx -> {
                                    Chat.info("Debug log file: " + FarmHelperFabric.getWebhookService().getDebugLogFile());
                                    return 1;
                                }))));
    }

    private static void syncConfigForFeature(String id, boolean enabled) {
        switch (id) {
            case "anti_stuck" -> FarmHelperFabric.getConfigManager().getConfig().antiStuckEnabled = enabled;
            case "scheduler" -> FarmHelperFabric.getConfigManager().getConfig().enableScheduler = enabled;
            case "leave_timer" -> FarmHelperFabric.getConfigManager().getConfig().leaveTimerEnabled = enabled;
            case "auto_reconnect" -> FarmHelperFabric.getConfigManager().getConfig().autoReconnect = enabled;
            case "auto_sell" -> FarmHelperFabric.getConfigManager().getConfig().enableAutoSell = enabled;
            case "auto_sprayonator" -> FarmHelperFabric.getConfigManager().getConfig().autoSprayonator = enabled;
            case "auto_wardrobe" -> FarmHelperFabric.getConfigManager().getConfig().autoWardrobe = enabled;
            case "bps_tracker" -> FarmHelperFabric.getConfigManager().getConfig().bpsTrackerEnabled = enabled;
            case "desync_checker" -> FarmHelperFabric.getConfigManager().getConfig().checkDesync = enabled;
            case "freelook" -> FarmHelperFabric.getConfigManager().getConfig().freelook = enabled;
            case "lag_detector" -> FarmHelperFabric.getConfigManager().getConfig().lagDetectorEnabled = enabled;
            case "mov_rec_player" -> FarmHelperFabric.getConfigManager().getConfig().movementRecorderEnabled = enabled;
            case "visitors_macro" -> FarmHelperFabric.getConfigManager().getConfig().visitorsMacro = enabled;
            case "pests_destroyer" -> FarmHelperFabric.getConfigManager().getConfig().enablePestsDestroyer = enabled;
            case "pest_farmer" -> FarmHelperFabric.getConfigManager().getConfig().pestFarmer = enabled;
            case "pests_destroyer_track" -> FarmHelperFabric.getConfigManager().getConfig().pestsDestroyerOnTheTrack = enabled;
            case "auto_composter" -> FarmHelperFabric.getConfigManager().getConfig().autoComposter = enabled;
            case "auto_pest_exchange" -> FarmHelperFabric.getConfigManager().getConfig().autoPestExchange = enabled;
            case "auto_god_pot" -> FarmHelperFabric.getConfigManager().getConfig().autoGodPot = enabled;
            case "auto_repellent" -> FarmHelperFabric.getConfigManager().getConfig().autoRepellent = enabled;
            case "profit_calculator" -> FarmHelperFabric.getConfigManager().getConfig().profitCalculatorEnabled = enabled;
            case "proxy" -> FarmHelperFabric.getConfigManager().getConfig().proxyEnabled = enabled;
            case "rancher_speed_setter" -> FarmHelperFabric.getConfigManager().getConfig().autoSetRancherSpeed = enabled;
            case "performance_mode" -> FarmHelperFabric.getConfigManager().getConfig().performanceMode = enabled;
            case "pip_mode" -> FarmHelperFabric.getConfigManager().getConfig().pipMode = enabled;
            case "ungrab_mouse" -> FarmHelperFabric.getConfigManager().getConfig().autoUngrabMouse = enabled;
            default -> {
            }
        }
    }

    private static RewarpPoint findClosestRewarpPoint(int x, int y, int z) {
        RewarpPoint closest = null;
        double bestDistance = Double.MAX_VALUE;
        for (RewarpPoint point : FarmHelperFabric.getConfigManager().getConfig().rewarpPoints) {
            if (point == null) {
                continue;
            }
            double dx = point.x - x;
            double dy = point.y - y;
            double dz = point.z - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = point;
            }
        }
        return closest;
    }

    private static void applyImmediateFeatureState(String id, boolean enabled) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        if ("freelook".equals(id)) {
            boolean canFreelookNow = enabled && client.player != null && client.world != null;
            FreelookController.getInstance().setEnabled(client, canFreelookNow);
        }
    }
}

package com.jelly.farmhelper.fabric.ui;

import com.jelly.farmhelper.fabric.macro.LegacyMacroType;
import com.jelly.farmhelper.fabric.macro.LegacyMacroProfiles;
import com.jelly.farmhelper.fabric.macro.MacroPattern;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.util.math.MathHelper;
import net.minecraft.text.Text;

import java.util.Arrays;

public class MacroSettingsScreen extends BaseConfigScreen {
    private enum MacroTab {
        CORE("Core", "Macro profile, type, and baseline behavior"),
        MOVEMENT("Movement", "Lane timing and anti-stall tuning"),
        ROTATION("Rotation", "Post-warp alignment and angle controls"),
        SPAWN("Spawn", "Spawn point coordinates and recovery anchors");

        private final String title;
        private final String description;

        MacroTab(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    private MacroTab activeTab = MacroTab.CORE;
    private CyclingButtonWidget<LegacyMacroType> macroTypeButton;
    private CyclingButtonWidget<MacroPattern> macroPatternButton;
    private ButtonWidget farmingSpeedButton;
    private ButtonWidget forwardTicksButton;
    private ButtonWidget sideStepButton;
    private ButtonWidget stationaryButton;
    private ButtonWidget rowDelayButton;
    private ButtonWidget rowRandomDelayButton;
    private ButtonWidget rotationTimeButton;
    private ButtonWidget rotationRandomButton;
    private ButtonWidget simPauseChanceButton;
    private ButtonWidget simPauseTicksButton;
    private ButtonWidget simJitterYawButton;
    private ButtonWidget simJitterPitchButton;
    private ButtonWidget spawnPositionButton;
    private ButtonWidget spawnRotationButton;
    private ButtonWidget tabCoreButton;
    private ButtonWidget tabMovementButton;
    private ButtonWidget tabRotationButton;
    private ButtonWidget tabSpawnButton;
    private ButtonWidget customPitchButton;
    private ButtonWidget customYawButton;
    private ButtonWidget rewarpRadiusButton;
    private ButtonWidget rewarpDelayButton;
    private int spawnAxisIndex;
    private int spawnAngleIndex;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public MacroSettingsScreen(Screen parent) {
        super(parent, Text.literal("FarmHelper - Macro Settings"));
    }

    @Override
    protected boolean usesCustomChrome() {
        return false;
    }

    @Override
    protected Text subtitleText() {
        return Text.literal("Macro profile, movement timings, rotation, and spawn/rewarp tuning.");
    }

    @Override
    protected void init() {
        macroTypeButton = null;
        macroPatternButton = null;
        farmingSpeedButton = null;
        forwardTicksButton = null;
        sideStepButton = null;
        stationaryButton = null;
        spawnPositionButton = null;
        spawnRotationButton = null;
        customPitchButton = null;
        customYawButton = null;
        rowDelayButton = null;
        rowRandomDelayButton = null;
        rotationTimeButton = null;
        rotationRandomButton = null;
        simPauseChanceButton = null;
        simPauseTicksButton = null;
        simJitterYawButton = null;
        simJitterPitchButton = null;
        rewarpRadiusButton = null;
        rewarpDelayButton = null;

        panelWidth = 440;
        panelHeight = Math.max(232, height - 84);
        panelX = width / 2 - panelWidth / 2;
        panelY = 30;

        int tabY = panelY + 8;
        int tabWidth = 102;
        int tabGap = 8;
        int tabX = panelX + 8;

        tabCoreButton = addSimpleButton(tabX, tabY, tabWidth, "", btn -> switchTab(MacroTab.CORE));
        tabX += tabWidth + tabGap;
        tabMovementButton = addSimpleButton(tabX, tabY, tabWidth, "", btn -> switchTab(MacroTab.MOVEMENT));
        tabX += tabWidth + tabGap;
        tabRotationButton = addSimpleButton(tabX, tabY, tabWidth, "", btn -> switchTab(MacroTab.ROTATION));
        tabX += tabWidth + tabGap;
        tabSpawnButton = addSimpleButton(tabX, tabY, tabWidth, "", btn -> switchTab(MacroTab.SPAWN));
        refreshTabLabels();

        int contentLeftX = panelX + 12;
        int contentRightX = panelX + panelWidth / 2 + 6;
        int y = panelY + 48;
        int row = 22;

        switch (activeTab) {
            case CORE -> {
                macroTypeButton = addSelector(
                        contentLeftX,
                        y,
                        206,
                        "Macro Type",
                        Arrays.asList(LegacyMacroType.values()),
                        () -> config.macroType,
                        selected -> {
                            config.macroType = selected;
                            if (config.useLegacyProfileDefaults) {
                                applyProfileDefaults();
                            } else {
                                config.macroPattern = selected.defaultPattern();
                            }
                            refreshDynamicLabels();
                        },
                        LegacyMacroType::getDisplayName
                );
                macroPatternButton = addSelector(
                        contentRightX,
                        y,
                        206,
                        "Pattern",
                        Arrays.asList(MacroPattern.values()),
                        () -> config.macroPattern,
                        selected -> {
                            config.macroPattern = selected;
                            refreshDynamicLabels();
                        },
                        value -> value.name()
                );
                y += row;

                addToggleButton(contentLeftX, y, 206, "Always hold W", () -> config.alwaysHoldW, () -> config.alwaysHoldW = !config.alwaysHoldW);
                addToggleButton(contentRightX, y, 206, "Use profile defaults", () -> config.useLegacyProfileDefaults, () -> config.useLegacyProfileDefaults = !config.useLegacyProfileDefaults);
                y += row;

                addToggleButton(contentLeftX, y, 206, "Hold attack", () -> config.holdAttackWhileMacroing, () -> config.holdAttackWhileMacroing = !config.holdAttackWhileMacroing);
                addToggleButton(contentRightX, y, 206, "Use custom speed", () -> config.useCustomFarmingSpeed, () -> config.useCustomFarmingSpeed = !config.useCustomFarmingSpeed);
                y += row;

                addIntSlider(
                        contentRightX,
                        y,
                        206,
                        "Farming Speed",
                        1,
                        400,
                        () -> config.farmingSpeed,
                        value -> config.farmingSpeed = value
                );
                addSimpleButton(contentLeftX, y, 206, "Apply Profile Defaults", btn -> {
                    applyProfileDefaults();
                    refreshDynamicLabels();
                });
                y += row;

                addToggleButton(contentLeftX, y, 206, "Auto choose tool", () -> config.autoChooseTool, () -> config.autoChooseTool = !config.autoChooseTool);
            }
            case MOVEMENT -> {
                addIntSlider(contentLeftX, y, 206, "Forward Ticks", 20, 400,
                        () -> config.forwardTicksBeforeTurn, value -> config.forwardTicksBeforeTurn = value);
                addIntSlider(contentRightX, y, 206, "Side-step Ticks", 1, 80,
                        () -> config.sideStepTicks, value -> config.sideStepTicks = value);
                y += row;

                addIntSlider(contentLeftX, y, 206, "Stationary Trigger", 60, 1200,
                        () -> config.stationaryFailsafeTicks, value -> config.stationaryFailsafeTicks = value);
                addToggleButton(contentRightX, y, 206, "Always hold W", () -> config.alwaysHoldW, () -> config.alwaysHoldW = !config.alwaysHoldW);
                y += row;

                addToggleButton(contentLeftX, y, 206, "Hold attack", () -> config.holdAttackWhileMacroing, () -> config.holdAttackWhileMacroing = !config.holdAttackWhileMacroing);
                addToggleButton(contentRightX, y, 206, "Use profile defaults", () -> config.useLegacyProfileDefaults, () -> config.useLegacyProfileDefaults = !config.useLegacyProfileDefaults);
                y += row;

                addToggleButton(contentLeftX, y, 206, "Use legacy random delays", () -> config.useLegacyRandomDelays, () -> config.useLegacyRandomDelays = !config.useLegacyRandomDelays);
                addIntSlider(contentRightX, y, 206, "Row Delay (ms)", 0, 2000,
                        () -> config.timeBetweenChangingRowsMs, value -> config.timeBetweenChangingRowsMs = value);
                y += row;

                addIntSlider(contentLeftX, y, 206, "Row Delay Random (ms)", 0, 2000,
                        () -> config.randomTimeBetweenChangingRowsMs, value -> config.randomTimeBetweenChangingRowsMs = value);
                simPauseChanceButton = addSimpleButton(contentRightX, y, 206, "", btn -> {
                    config.playerSimulationPauseChancePct = wrap(config.playerSimulationPauseChancePct + 1, 0, 25);
                    refreshDynamicLabels();
                });
                y += row;

                addToggleButton(contentLeftX, y, 206, "Humanized Simulation", () -> config.playerSimulationEnabled, () -> config.playerSimulationEnabled = !config.playerSimulationEnabled);
                simPauseTicksButton = addSimpleButton(contentRightX, y, 206, "", btn -> {
                    int nextMin = config.playerSimulationPauseMinTicks + 1;
                    if (nextMin > 5) {
                        nextMin = 1;
                    }
                    config.playerSimulationPauseMinTicks = nextMin;
                    config.playerSimulationPauseMaxTicks = Math.max(config.playerSimulationPauseMaxTicks, nextMin);
                    refreshDynamicLabels();
                });
                y += row;

                simJitterYawButton = addSimpleButton(contentLeftX, y, 206, "", btn -> {
                    config.playerSimulationYawJitterDegrees = wrapFloat(config.playerSimulationYawJitterDegrees + 0.2f, 0f, 4f);
                    refreshDynamicLabels();
                });
                simJitterPitchButton = addSimpleButton(contentRightX, y, 206, "", btn -> {
                    config.playerSimulationPitchJitterDegrees = wrapFloat(config.playerSimulationPitchJitterDegrees + 0.1f, 0f, 2f);
                    refreshDynamicLabels();
                });
            }
            case ROTATION -> {
                addToggleButton(contentLeftX, y, 206, "Rotate after warp", () -> config.rotateAfterWarped, () -> config.rotateAfterWarped = !config.rotateAfterWarped);
                addToggleButton(contentRightX, y, 206, "Rotate after drop", () -> config.rotateAfterDrop, () -> config.rotateAfterDrop = !config.rotateAfterDrop);
                y += row;

                addToggleButton(contentLeftX, y, 206, "Disable micro-fix", () -> config.dontFixAfterWarping, () -> config.dontFixAfterWarping = !config.dontFixAfterWarping);
                addToggleButton(contentRightX, y, 206, "Custom Pitch", () -> config.customPitch, () -> config.customPitch = !config.customPitch);
                y += row;

                customPitchButton = addSimpleButton(contentLeftX, y, 206, "", btn -> {
                    config.customPitch = true;
                    config.customPitchLevel = wrapFloat(config.customPitchLevel + 1f, -90f, 90f);
                    refreshDynamicLabels();
                });
                addToggleButton(contentRightX, y, 206, "Custom Yaw", () -> config.customYaw, () -> config.customYaw = !config.customYaw);
                y += row;

                customYawButton = addSimpleButton(contentLeftX, y, 206, "", btn -> {
                    config.customYaw = true;
                    config.customYawLevel = wrapFloat(config.customYawLevel + 5f, -180f, 180f);
                    refreshDynamicLabels();
                });
                addIntSlider(contentRightX, y, 206, "Rotation Time (ms)", 200, 2000,
                        () -> config.rotationTimeMs, value -> config.rotationTimeMs = value);
                y += row;

                addIntSlider(contentLeftX, y, 206, "Rotation Random (ms)", 0, 2000,
                        () -> config.rotationTimeRandomnessMs, value -> config.rotationTimeRandomnessMs = value);
            }
            case SPAWN -> {
                addToggleButton(contentLeftX, y, 206, "Draw spawn location", () -> config.drawSpawnLocation, () -> config.drawSpawnLocation = !config.drawSpawnLocation);
                addToggleButton(contentRightX, y, 206, "Rotate after warp", () -> config.rotateAfterWarped, () -> config.rotateAfterWarped = !config.rotateAfterWarped);
                y += row;

                spawnPositionButton = addSimpleButton(contentLeftX, y, 206, "", btn -> {
                    cycleSpawnCoordinate();
                    refreshDynamicLabels();
                });
                spawnRotationButton = addSimpleButton(contentRightX, y, 206, "", btn -> {
                    cycleSpawnAngles();
                    refreshDynamicLabels();
                });
                y += row;

                addSimpleButton(contentLeftX, y, 206, "Set Spawn From Current", btn -> {
                    if (client != null && client.player != null) {
                        config.spawnPosX = MathHelper.floor(client.player.getX());
                        config.spawnPosY = MathHelper.floor(client.player.getY());
                        config.spawnPosZ = MathHelper.floor(client.player.getZ());
                        config.spawnYaw = client.player.getYaw();
                        config.spawnPitch = client.player.getPitch();
                        refreshDynamicLabels();
                    }
                });
                addSimpleButton(contentRightX, y, 206, "Reset Spawn Anchor", btn -> {
                    config.spawnPosX = 0;
                    config.spawnPosY = 0;
                    config.spawnPosZ = 0;
                    config.spawnYaw = 0f;
                    config.spawnPitch = 0f;
                    config.spawnPlot = 0;
                    refreshDynamicLabels();
                });
                y += row;

                addToggleButton(contentLeftX, y, 206, "Highlight rewarp points", () -> config.highlightRewarp, () -> config.highlightRewarp = !config.highlightRewarp);
                rewarpRadiusButton = addSimpleButton(contentRightX, y, 206, "", btn -> {
                    config.rewarpActivationRadius = wrap(config.rewarpActivationRadius + 1, 1, 6);
                    refreshDynamicLabels();
                });
                y += row;

                rewarpDelayButton = addSimpleButton(contentLeftX, y, 206, "", btn -> {
                    config.rewarpDelayMs = wrap(config.rewarpDelayMs + 50, 50, 3000);
                    refreshDynamicLabels();
                });
                addSimpleButton(contentRightX, y, 206, "Set Rewarp Here", btn -> {
                    if (client != null && client.player != null) {
                        var points = config.rewarpPoints;
                        int x = MathHelper.floor(client.player.getX());
                        int yPos = MathHelper.floor(client.player.getY());
                        int z = MathHelper.floor(client.player.getZ());
                        boolean exists = points.stream().anyMatch(point -> point != null && point.x == x && point.y == yPos && point.z == z);
                        if (!exists) {
                            points.add(new com.jelly.farmhelper.fabric.config.struct.RewarpPoint(
                                    x,
                                    yPos,
                                    z,
                                    client.player.getYaw(),
                                    client.player.getPitch()
                            ));
                        }
                    }
                });
            }
        }

        int actionY = panelY + panelHeight - 26;
        addSimpleButton(width / 2 - 122, actionY, 116, "Back", btn -> close());
        addSimpleButton(width / 2 + 6, actionY, 116, "Save", btn -> com.jelly.farmhelper.fabric.FarmHelperFabric.getConfigManager().save());
        refreshDynamicLabels();
    }

    @Override
    protected void renderDecorations(DrawContext context, int mouseX, int mouseY, float delta) {
        int border = 0xA5495666;
        int background = 0xD0141A24;
        int sectionHeader = 0xFF5BA4FF;
        int detail = 0xFFABB7C8;

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, background);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 1, border);
        context.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, border);
        context.fill(panelX, panelY, panelX + 1, panelY + panelHeight, border);
        context.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, border);

        int dividerY = panelY + 40;
        context.fill(panelX + 8, dividerY, panelX + panelWidth - 8, dividerY + 1, 0x80555555);

        var profile = LegacyMacroProfiles.forType(config.macroType);
        String profileLine = "Legacy Profile: " + profile.pattern()
                + " | Fwd " + profile.defaultForwardTicks()
                + " | Side " + profile.defaultSideStepTicks();
        context.drawText(textRenderer, Text.literal(activeTab.title + " Settings"), panelX + 12, panelY + 44, sectionHeader, false);
        context.drawText(textRenderer, Text.literal(activeTab.description), panelX + 12, panelY + 56, detail, false);
        context.drawText(textRenderer, Text.literal(profileLine), panelX + 12, panelY + panelHeight - 40, 0xFF8CD5A8, false);
    }

    private void refreshDynamicLabels() {
        if (macroTypeButton != null) {
            macroTypeButton.setValue(config.macroType);
        }
        if (macroPatternButton != null) {
            macroPatternButton.setValue(config.macroPattern);
        }
        if (farmingSpeedButton != null) {
            farmingSpeedButton.setMessage(Text.literal("Farming Speed: " + config.farmingSpeed));
        }
        if (forwardTicksButton != null) {
            forwardTicksButton.setMessage(Text.literal("Forward Ticks: " + config.forwardTicksBeforeTurn));
        }
        if (sideStepButton != null) {
            sideStepButton.setMessage(Text.literal("Side-step Ticks: " + config.sideStepTicks));
        }
        if (stationaryButton != null) {
            stationaryButton.setMessage(Text.literal("Stationary Trigger: " + config.stationaryFailsafeTicks));
        }
        if (rowDelayButton != null) {
            rowDelayButton.setMessage(Text.literal("Row Delay (ms): " + config.timeBetweenChangingRowsMs));
        }
        if (rowRandomDelayButton != null) {
            rowRandomDelayButton.setMessage(Text.literal("Row Delay Random (ms): +" + config.randomTimeBetweenChangingRowsMs));
        }
        if (simPauseChanceButton != null) {
            simPauseChanceButton.setMessage(Text.literal("Sim Pause Chance: " + config.playerSimulationPauseChancePct + "%"));
        }
        if (simPauseTicksButton != null) {
            simPauseTicksButton.setMessage(Text.literal("Sim Pause Ticks: " + config.playerSimulationPauseMinTicks + "-" + config.playerSimulationPauseMaxTicks));
        }
        if (simJitterYawButton != null) {
            simJitterYawButton.setMessage(Text.literal(String.format("Sim Yaw Jitter: %.1f°", config.playerSimulationYawJitterDegrees)));
        }
        if (simJitterPitchButton != null) {
            simJitterPitchButton.setMessage(Text.literal(String.format("Sim Pitch Jitter: %.1f°", config.playerSimulationPitchJitterDegrees)));
        }
        if (customPitchButton != null) {
            customPitchButton.setMessage(Text.literal("Custom Pitch: " + config.customPitch + " (" + (int) config.customPitchLevel + ")"));
        }
        if (customYawButton != null) {
            customYawButton.setMessage(Text.literal("Custom Yaw: " + config.customYaw + " (" + (int) config.customYawLevel + ")"));
        }
        if (rotationTimeButton != null) {
            rotationTimeButton.setMessage(Text.literal("Rotation Time (ms): " + config.rotationTimeMs));
        }
        if (rotationRandomButton != null) {
            rotationRandomButton.setMessage(Text.literal("Rotation Random (ms): +" + config.rotationTimeRandomnessMs));
        }
        if (spawnPositionButton != null) {
            spawnPositionButton.setMessage(Text.literal(
                    "Spawn XYZ (" + axisLabel(spawnAxisIndex) + "): "
                            + config.spawnPosX + " " + config.spawnPosY + " " + config.spawnPosZ
            ));
        }
        if (spawnRotationButton != null) {
            spawnRotationButton.setMessage(Text.literal(
                    "Spawn Yaw/Pitch (" + angleLabel(spawnAngleIndex) + "): "
                            + (int) config.spawnYaw + " / " + (int) config.spawnPitch
            ));
        }
        if (rewarpRadiusButton != null) {
            rewarpRadiusButton.setMessage(Text.literal("Rewarp Radius: " + config.rewarpActivationRadius));
        }
        if (rewarpDelayButton != null) {
            rewarpDelayButton.setMessage(Text.literal("Rewarp Delay (ms): " + config.rewarpDelayMs + " +" + config.rewarpDelayRandomnessMs));
        }
    }

    private int wrap(int value, int min, int max) {
        if (value > max) {
            return min;
        }
        if (value < min) {
            return max;
        }
        return value;
    }

    private float wrapFloat(float value, float min, float max) {
        if (value > max) {
            return min;
        }
        if (value < min) {
            return max;
        }
        return value;
    }

    private void applyProfileDefaults() {
        var profile = LegacyMacroProfiles.forType(config.macroType);
        config.macroPattern = profile.pattern();
        config.forwardTicksBeforeTurn = profile.defaultForwardTicks();
        config.sideStepTicks = profile.defaultSideStepTicks();
    }

    private void switchTab(MacroTab tab) {
        if (activeTab == tab) {
            return;
        }
        activeTab = tab;
        clearAndInit();
    }

    private void refreshTabLabels() {
        updateTabButton(tabCoreButton, MacroTab.CORE);
        updateTabButton(tabMovementButton, MacroTab.MOVEMENT);
        updateTabButton(tabRotationButton, MacroTab.ROTATION);
        updateTabButton(tabSpawnButton, MacroTab.SPAWN);
    }

    private void updateTabButton(ButtonWidget button, MacroTab tab) {
        if (button == null) {
            return;
        }
        String label = activeTab == tab ? "> " + tab.title + " <" : tab.title;
        button.setMessage(Text.literal(label));
    }

    private void cycleSpawnCoordinate() {
        switch (spawnAxisIndex) {
            case 0 -> config.spawnPosX += 1;
            case 1 -> config.spawnPosY += 1;
            case 2 -> config.spawnPosZ += 1;
            default -> {
            }
        }
        spawnAxisIndex = (spawnAxisIndex + 1) % 3;
    }

    private void cycleSpawnAngles() {
        if (spawnAngleIndex == 0) {
            config.spawnYaw = wrapFloat(config.spawnYaw + 5f, -180f, 180f);
        } else {
            config.spawnPitch = wrapFloat(config.spawnPitch + 1f, -90f, 90f);
        }
        spawnAngleIndex = (spawnAngleIndex + 1) % 2;
    }

    private String axisLabel(int index) {
        return switch (index) {
            case 0 -> "next X+1";
            case 1 -> "next Y+1";
            case 2 -> "next Z+1";
            default -> "next X+1";
        };
    }

    private String angleLabel(int index) {
        return index == 0 ? "next yaw+5" : "next pitch+1";
    }
}

package com.jelly.farmhelper.fabric.state;

import com.jelly.farmhelper.fabric.util.AngleUtils;
import com.jelly.farmhelper.fabric.util.BlockUtils;
import com.jelly.farmhelper.fabric.util.PlotUtils;
import com.jelly.farmhelper.fabric.util.ScoreboardUtils;
import com.jelly.farmhelper.fabric.util.TablistUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GameStateHandler {
    public enum Location {
        GARDEN,
        BARN,
        HUB,
        PRIVATE_ISLAND,
        LOBBY,
        LIMBO,
        DUNGEON,
        OTHER
    }

    private static final Pattern NUMBER_PATTERN = Pattern.compile("([0-9][0-9,]*\\.?[0-9]*)");

    private Location location = Location.OTHER;
    private String locationSource = "";
    private boolean jacobContestActive;
    private boolean godPotionActive;
    private boolean cookieBuffActive;
    private boolean pestRepellentActive;
    private int totalPests;
    private int mostInfestedPlot = -1;
    private int mostInfestedPlotPests;
    private int currentPlot = -1;
    private String currentPlotName = "";
    private double purse;
    private double bits;
    private double copper;
    private String serverAddress = "";
    private boolean forwardWalkable;
    private boolean backwardWalkable;
    private boolean leftWalkable;
    private boolean rightWalkable;

    public void tick(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            reset();
            return;
        }
        ClientPlayerEntity player = client.player;

        List<String> scoreboard = ScoreboardUtils.getScoreboardLines(true);
        List<String> tablist = TablistUtils.getTabList();
        updateLocation(scoreboard, tablist, player);
        updateContestState(scoreboard);
        updateBuffState(scoreboard, tablist);
        updateCurrencies(scoreboard);
        updatePests(tablist);
        updatePlot(player);
        updateWalkability(client, player);
        serverAddress = client.getCurrentServerEntry() == null ? "" : String.valueOf(client.getCurrentServerEntry().address);
    }

    private void updateLocation(List<String> scoreboard, List<String> tablist, ClientPlayerEntity player) {
        String aggregate = String.join(" ", scoreboard).toLowerCase(Locale.ROOT);
        String tabAggregate = String.join(" ", tablist).toLowerCase(Locale.ROOT);
        if (aggregate.contains("garden") || tabAggregate.contains("garden")) {
            location = Location.GARDEN;
            locationSource = "scoreboard/tablist";
        } else if (aggregate.contains("barn")) {
            location = Location.BARN;
            locationSource = "scoreboard";
        } else if (aggregate.contains("hub") || tabAggregate.contains("village")) {
            location = Location.HUB;
            locationSource = "scoreboard/tablist";
        } else if (aggregate.contains("private island")) {
            location = Location.PRIVATE_ISLAND;
            locationSource = "scoreboard";
        } else if (aggregate.contains("limbo")) {
            location = Location.LIMBO;
            locationSource = "scoreboard";
        } else if (aggregate.contains("dungeon") || aggregate.contains("catacombs")) {
            location = Location.DUNGEON;
            locationSource = "scoreboard";
        } else if (aggregate.contains("lobby")) {
            location = Location.LOBBY;
            locationSource = "scoreboard";
        } else {
            location = Location.OTHER;
            locationSource = "unknown";
        }

        Optional<Integer> plot = PlotUtils.getPlotNumber(player.getBlockPos());
        if (plot.isPresent()) {
            currentPlot = plot.get();
            currentPlotName = PlotUtils.getPlotName(currentPlot);
            if (currentPlot == 0) {
                location = Location.BARN;
                locationSource = "plot";
            } else if (location == Location.OTHER) {
                location = Location.GARDEN;
                locationSource = "plot";
            }
        }
    }

    private void updateContestState(List<String> scoreboard) {
        String aggregate = String.join(" ", scoreboard).toLowerCase(Locale.ROOT);
        jacobContestActive = aggregate.contains("jacob") || aggregate.contains("contest");
    }

    private void updateBuffState(List<String> scoreboard, List<String> tablist) {
        String aggregate = (String.join(" ", scoreboard) + " " + String.join(" ", tablist)).toLowerCase(Locale.ROOT);
        godPotionActive = aggregate.contains("god potion");
        cookieBuffActive = aggregate.contains("cookie buff");
        pestRepellentActive = aggregate.contains("pest repellent");
    }

    private void updateCurrencies(List<String> scoreboard) {
        purse = extractValue(scoreboard, "purse");
        bits = extractValue(scoreboard, "bits");
        copper = extractValue(scoreboard, "copper");
    }

    private void updatePests(List<String> tablist) {
        List<PlotUtils.InfestedPlot> infested = PlotUtils.parseInfestedPlots(tablist);
        totalPests = infested.stream().mapToInt(PlotUtils.InfestedPlot::pestCount).sum();
        if (infested.isEmpty()) {
            mostInfestedPlot = -1;
            mostInfestedPlotPests = 0;
            return;
        }
        mostInfestedPlot = infested.get(0).plotNumber();
        mostInfestedPlotPests = infested.get(0).pestCount();
    }

    private void updatePlot(ClientPlayerEntity player) {
        Optional<Integer> plot = PlotUtils.getPlotNumber(player.getBlockPos());
        if (plot.isEmpty()) {
            currentPlot = -1;
            currentPlotName = "";
            return;
        }
        currentPlot = plot.get();
        currentPlotName = PlotUtils.getPlotName(currentPlot);
    }

    private void updateWalkability(MinecraftClient client, ClientPlayerEntity player) {
        float yaw = AngleUtils.closestCardinal(player.getYaw());
        BlockPos forward = BlockUtils.relativeBlockPos(player, 0f, 0f, 1f, yaw);
        BlockPos backward = BlockUtils.relativeBlockPos(player, 0f, 0f, -1f, yaw);
        BlockPos left = BlockUtils.relativeBlockPos(player, -1f, 0f, 0f, yaw);
        BlockPos right = BlockUtils.relativeBlockPos(player, 1f, 0f, 0f, yaw);
        forwardWalkable = BlockUtils.canWalkThrough(client, forward);
        backwardWalkable = BlockUtils.canWalkThrough(client, backward);
        leftWalkable = BlockUtils.canWalkThrough(client, left);
        rightWalkable = BlockUtils.canWalkThrough(client, right);
    }

    private double extractValue(List<String> lines, String label) {
        for (String line : lines) {
            String cleaned = line.replaceAll("§.", "").toLowerCase(Locale.ROOT);
            if (!cleaned.contains(label.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Matcher matcher = NUMBER_PATTERN.matcher(cleaned);
            if (!matcher.find()) {
                continue;
            }
            String raw = matcher.group(1).replace(",", "");
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private void reset() {
        location = Location.OTHER;
        locationSource = "";
        jacobContestActive = false;
        godPotionActive = false;
        cookieBuffActive = false;
        pestRepellentActive = false;
        totalPests = 0;
        mostInfestedPlot = -1;
        mostInfestedPlotPests = 0;
        currentPlot = -1;
        currentPlotName = "";
        purse = 0;
        bits = 0;
        copper = 0;
        serverAddress = "";
        forwardWalkable = false;
        backwardWalkable = false;
        leftWalkable = false;
        rightWalkable = false;
    }

    public Location getLocation() {
        return location;
    }

    public String getLocationSource() {
        return locationSource;
    }

    public boolean isJacobContestActive() {
        return jacobContestActive;
    }

    public boolean isGodPotionActive() {
        return godPotionActive;
    }

    public boolean isCookieBuffActive() {
        return cookieBuffActive;
    }

    public boolean isPestRepellentActive() {
        return pestRepellentActive;
    }

    public int getTotalPests() {
        return totalPests;
    }

    public int getMostInfestedPlot() {
        return mostInfestedPlot;
    }

    public int getMostInfestedPlotPests() {
        return mostInfestedPlotPests;
    }

    public int getCurrentPlot() {
        return currentPlot;
    }

    public String getCurrentPlotName() {
        return currentPlotName;
    }

    public double getPurse() {
        return purse;
    }

    public double getBits() {
        return bits;
    }

    public double getCopper() {
        return copper;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public boolean isForwardWalkable() {
        return forwardWalkable;
    }

    public boolean isBackwardWalkable() {
        return backwardWalkable;
    }

    public boolean isLeftWalkable() {
        return leftWalkable;
    }

    public boolean isRightWalkable() {
        return rightWalkable;
    }
}

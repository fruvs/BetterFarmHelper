package com.jelly.farmhelper.fabric.runtime;

import com.jelly.farmhelper.fabric.macro.MacroState;

public class RuntimeSnapshot {
    public long tickCount;
    public boolean inWorld;
    public boolean macroToggled;
    public MacroState macroState = MacroState.STOPPED;
    public long macroRuntimeTicks;

    public int stationaryTicks;
    public double movedDistance;
    public double horizontalSpeedBps;
    public double velocityX;
    public double verticalVelocity;
    public double velocityZ;
    public double posX;
    public double posY;
    public double posZ;
    public float yaw;
    public float pitch;
    public float yawDelta;
    public float pitchDelta;

    public int inventoryFillPercent;
    public int selectedSlotChanges;
    public boolean screenOpen;
    public String screenTitle = "";

    public boolean nearCobweb;
    public boolean nearDirt;
    public boolean nearBedrock;
    public boolean hasBadEffects;
    public boolean nearSpawnPoint;
    public boolean nearRewarpPoint;
    public String location = "";
    public int currentPlot = -1;
    public int mostInfestedPlot = -1;
    public int guiInfestedPlot = -1;
    public int guiInfestedPests = 0;
    public int pestsInTablist;
    public boolean jacobContestActive;
    public boolean godPotionActive;
    public boolean cookieBuffActive;
    public boolean pestRepellentActive;
    public double vacuumRange;
    public double vacuumDps;
    public double vacuumTrackerCooldownSeconds;
    public double purse;
    public double bits;
    public double copper;

    public long millisSinceWorldTimePacket;
    public float estimatedServerTps;
    public boolean networkLagging;

    public String latestChatMessage = "";

    public RuntimeSnapshot copy() {
        RuntimeSnapshot snapshot = new RuntimeSnapshot();
        snapshot.tickCount = tickCount;
        snapshot.inWorld = inWorld;
        snapshot.macroToggled = macroToggled;
        snapshot.macroState = macroState;
        snapshot.macroRuntimeTicks = macroRuntimeTicks;
        snapshot.stationaryTicks = stationaryTicks;
        snapshot.movedDistance = movedDistance;
        snapshot.horizontalSpeedBps = horizontalSpeedBps;
        snapshot.velocityX = velocityX;
        snapshot.verticalVelocity = verticalVelocity;
        snapshot.velocityZ = velocityZ;
        snapshot.posX = posX;
        snapshot.posY = posY;
        snapshot.posZ = posZ;
        snapshot.yaw = yaw;
        snapshot.pitch = pitch;
        snapshot.yawDelta = yawDelta;
        snapshot.pitchDelta = pitchDelta;
        snapshot.inventoryFillPercent = inventoryFillPercent;
        snapshot.selectedSlotChanges = selectedSlotChanges;
        snapshot.screenOpen = screenOpen;
        snapshot.screenTitle = screenTitle;
        snapshot.nearCobweb = nearCobweb;
        snapshot.nearDirt = nearDirt;
        snapshot.nearBedrock = nearBedrock;
        snapshot.hasBadEffects = hasBadEffects;
        snapshot.nearSpawnPoint = nearSpawnPoint;
        snapshot.nearRewarpPoint = nearRewarpPoint;
        snapshot.location = location;
        snapshot.currentPlot = currentPlot;
        snapshot.mostInfestedPlot = mostInfestedPlot;
        snapshot.guiInfestedPlot = guiInfestedPlot;
        snapshot.guiInfestedPests = guiInfestedPests;
        snapshot.pestsInTablist = pestsInTablist;
        snapshot.jacobContestActive = jacobContestActive;
        snapshot.godPotionActive = godPotionActive;
        snapshot.cookieBuffActive = cookieBuffActive;
        snapshot.pestRepellentActive = pestRepellentActive;
        snapshot.vacuumRange = vacuumRange;
        snapshot.vacuumDps = vacuumDps;
        snapshot.vacuumTrackerCooldownSeconds = vacuumTrackerCooldownSeconds;
        snapshot.purse = purse;
        snapshot.bits = bits;
        snapshot.copper = copper;
        snapshot.millisSinceWorldTimePacket = millisSinceWorldTimePacket;
        snapshot.estimatedServerTps = estimatedServerTps;
        snapshot.networkLagging = networkLagging;
        snapshot.latestChatMessage = latestChatMessage;
        return snapshot;
    }
}

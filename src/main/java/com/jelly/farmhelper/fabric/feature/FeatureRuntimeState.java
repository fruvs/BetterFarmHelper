package com.jelly.farmhelper.fabric.feature;

import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.macro.MacroState;

import java.util.Optional;

public class FeatureRuntimeState {
    public long tickCount;
    public boolean inWorld;
    public boolean macroToggled;
    public MacroState macroState = MacroState.STOPPED;
    public long macroRuntimeTicks;
    public int stationaryTicks;
    public double movedDistance;
    public double horizontalSpeedBps;
    public double posX;
    public double posY;
    public double posZ;
    public float yaw;
    public float pitch;
    public boolean screenOpen;
    public String screenTitle = "";
    public int inventoryFillPercent;
    public VisitorOfferSnapshot visitorOffer = new VisitorOfferSnapshot();
    public long millisSinceWorldTimePacket;
    public float estimatedServerTps;
    public boolean networkLagging;
    public boolean nearSpawnPoint;
    public boolean nearRewarpPoint;
    public String location = "";
    public int currentPlot = -1;
    public int mostInfestedPlot = -1;
    public int guiInfestedPlot = -1;
    public int guiInfestedPests;
    public int pestsInTablist;
    public boolean allowFlying;
    public boolean flying;
    public boolean onGround;
    public boolean aboveHeadClear;
    public boolean canFlyHigher;
    public boolean playerSuffocating;
    public boolean automationBusy;
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
    public Optional<FailsafeType> activeFailsafe = Optional.empty();
}

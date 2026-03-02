package com.jelly.farmhelper.fabric.failsafe.detector;

public class DetectorState {
    public boolean worldChanged;
    public boolean disconnected;
    public int lastSelectedSlot;
    public int selectedSlotChangeCount;
    public String latestChatMessage = "";
    public long latestChatSeq = 0L;

    public boolean packetPositionLookSeen;
    public double packetTeleportDistance;
    public double packetTeleportOriginX;
    public double packetTeleportOriginY;
    public double packetTeleportOriginZ;
    public double packetTeleportTargetX;
    public double packetTeleportTargetY;
    public double packetTeleportTargetZ;
    public float packetYawDelta;
    public float packetPitchDelta;

    public boolean packetRotationSeen;
    public float packetRotationYawDelta;
    public float packetRotationPitchDelta;
    public float packetRotationOriginYaw;
    public float packetRotationOriginPitch;
    public float packetRotationTargetYaw;
    public float packetRotationTargetPitch;

    public boolean packetVelocitySeen;
    public double packetVelocityY;
    public double packetVelocityMagnitude;

    public boolean packetSlotUpdateSeen;
    public int packetSlot = -1;
    public boolean packetSlotTargetsSelectedHotbar;
    public boolean packetSlotLooksLikeFarmTool;
    public String packetSlotItemName = "";

    public boolean packetTeleportSuppressed;
    public boolean packetRotationSuppressed;
    public boolean packetItemSuppressed;

    public void resetTransient() {
        worldChanged = false;
        disconnected = false;
        latestChatMessage = "";
        latestChatSeq = 0L;
        packetPositionLookSeen = false;
        packetTeleportDistance = 0.0;
        packetTeleportOriginX = 0.0;
        packetTeleportOriginY = 0.0;
        packetTeleportOriginZ = 0.0;
        packetTeleportTargetX = 0.0;
        packetTeleportTargetY = 0.0;
        packetTeleportTargetZ = 0.0;
        packetYawDelta = 0f;
        packetPitchDelta = 0f;
        packetRotationSeen = false;
        packetRotationYawDelta = 0f;
        packetRotationPitchDelta = 0f;
        packetRotationOriginYaw = 0f;
        packetRotationOriginPitch = 0f;
        packetRotationTargetYaw = 0f;
        packetRotationTargetPitch = 0f;
        packetVelocitySeen = false;
        packetVelocityY = 0.0;
        packetVelocityMagnitude = 0.0;
        packetSlotUpdateSeen = false;
        packetSlot = -1;
        packetSlotTargetsSelectedHotbar = false;
        packetSlotLooksLikeFarmTool = false;
        packetSlotItemName = "";
        packetTeleportSuppressed = false;
        packetRotationSuppressed = false;
        packetItemSuppressed = false;
    }
}

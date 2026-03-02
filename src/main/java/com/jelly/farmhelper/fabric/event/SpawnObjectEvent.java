package com.jelly.farmhelper.fabric.event;

import net.minecraft.util.math.Vec3d;

public final class SpawnObjectEvent {
    public final int entityId;
    public final String entityTypeId;
    public final Vec3d position;
    public final Vec3d velocity;
    public final float yaw;
    public final float pitch;

    public SpawnObjectEvent(int entityId, String entityTypeId, Vec3d position, Vec3d velocity, float yaw, float pitch) {
        this.entityId = entityId;
        this.entityTypeId = entityTypeId;
        this.position = position;
        this.velocity = velocity;
        this.yaw = yaw;
        this.pitch = pitch;
    }
}

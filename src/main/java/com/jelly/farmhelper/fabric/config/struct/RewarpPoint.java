package com.jelly.farmhelper.fabric.config.struct;

public class RewarpPoint {
    public int x;
    public int y;
    public int z;
    public float yaw;
    public float pitch;

    public RewarpPoint() {
    }

    public RewarpPoint(int x, int y, int z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }
}

package com.jelly.farmhelper.fabric.event;

public class MotionUpdateEvent {
    public enum Phase {
        PRE,
        POST
    }

    public final Phase phase;
    public final float yaw;
    public final float pitch;
    public final double x;
    public final double y;
    public final double z;

    protected MotionUpdateEvent(Phase phase, float yaw, float pitch, double x, double y, double z) {
        this.phase = phase;
        this.yaw = yaw;
        this.pitch = pitch;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static final class Pre extends MotionUpdateEvent {
        public Pre(float yaw, float pitch, double x, double y, double z) {
            super(Phase.PRE, yaw, pitch, x, y, z);
        }
    }

    public static final class Post extends MotionUpdateEvent {
        public Post(float yaw, float pitch, double x, double y, double z) {
            super(Phase.POST, yaw, pitch, x, y, z);
        }
    }
}

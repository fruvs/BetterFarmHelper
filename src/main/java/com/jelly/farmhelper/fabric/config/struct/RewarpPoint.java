package com.jelly.farmhelper.fabric.config.struct;

public class RewarpPoint {
    public String name;
    public int x;
    public int y;
    public int z;
    public float yaw;
    public float pitch;

    public RewarpPoint() {
    }

    public RewarpPoint(int x, int y, int z, float yaw, float pitch) {
        this(null, x, y, z, yaw, pitch);
    }

    public RewarpPoint(String name, int x, int y, int z, float yaw, float pitch) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public void normalizeInPlace(int fallbackIndex) {
        this.name = normalizeName(this.name, fallbackIndex);
    }

    public String displayName(int fallbackIndex) {
        return normalizeName(this.name, fallbackIndex);
    }

    public static String normalizeName(String candidate, int fallbackIndex) {
        String trimmed = candidate == null ? "" : candidate.trim();
        if (trimmed.isEmpty()) {
            return "Rewarp " + Math.max(1, fallbackIndex);
        }
        if (trimmed.length() > 48) {
            trimmed = trimmed.substring(0, 48).trim();
        }
        return trimmed.isEmpty() ? "Rewarp " + Math.max(1, fallbackIndex) : trimmed;
    }
}

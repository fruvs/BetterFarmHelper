package com.jelly.farmhelper.fabric.util;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class AngleUtils {
    private AngleUtils() {
    }

    public static float normalize180(float angle) {
        return MathHelper.wrapDegrees(angle);
    }

    public static float normalize360(float angle) {
        float out = angle % 360.0f;
        if (out < 0.0f) {
            out += 360.0f;
        }
        return out;
    }

    public static float clockwiseDifference(float fromYaw360, float toYaw360) {
        return normalize360(toYaw360 - fromYaw360);
    }

    public static float counterClockwiseDifference(float fromYaw360, float toYaw360) {
        return normalize360(fromYaw360 - toYaw360);
    }

    public static float smallestAngleDifference(float fromYaw360, float toYaw360) {
        return Math.min(clockwiseDifference(fromYaw360, toYaw360), counterClockwiseDifference(fromYaw360, toYaw360));
    }

    public static float closestCardinal(float yaw) {
        float normalized = normalize360(yaw);
        if (normalized < 45.0f || normalized >= 315.0f) {
            return 0.0f;
        }
        if (normalized < 135.0f) {
            return 90.0f;
        }
        if (normalized < 225.0f) {
            return 180.0f;
        }
        return 270.0f;
    }

    public static float closestDiagonal(float yaw) {
        float normalized = normalize360(yaw);
        if (normalized < 90.0f) {
            return 45.0f;
        }
        if (normalized < 180.0f) {
            return 135.0f;
        }
        if (normalized < 270.0f) {
            return 225.0f;
        }
        return 315.0f;
    }

    public static float closest45(float yaw) {
        float normalized = normalize360(yaw);
        return Math.round(normalized / 45.0f) * 45.0f;
    }

    public static float closest30(float yaw) {
        float normalized = normalize360(yaw);
        return Math.round(normalized / 30.0f) * 30.0f;
    }

    public static float getActualYawFrom360(float yaw) {
        float normalized = normalize360(yaw);
        if (normalized > 180.0f) {
            normalized -= 360.0f;
        }
        return normalize180(normalized);
    }

    public static Vec3d unitVector(float pitch, float yaw) {
        float pitchRad = pitch * ((float) Math.PI / 180.0f);
        float yawRad = yaw * ((float) Math.PI / 180.0f);
        float cosPitch = MathHelper.cos(pitchRad);
        return new Vec3d(
                -MathHelper.sin(yawRad) * cosPitch,
                -MathHelper.sin(pitchRad),
                MathHelper.cos(yawRad) * cosPitch
        );
    }
}

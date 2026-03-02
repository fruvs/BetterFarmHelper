package com.jelly.farmhelper.fabric.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class OldRotationUtils {
    private long startTimeMs;
    private long endTimeMs;
    private float startYaw;
    private float startPitch;
    private float targetYaw;
    private float targetPitch;
    private boolean rotating;
    private boolean completed;

    public void easeTo(float yaw, float pitch, long durationMs) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            reset();
            return;
        }

        startTimeMs = System.currentTimeMillis();
        endTimeMs = startTimeMs + Math.max(1L, durationMs);
        startYaw = player.getYaw();
        startPitch = player.getPitch();

        float yawDiff = AngleUtils.normalize180(yaw - startYaw);
        targetYaw = startYaw + yawDiff;
        targetPitch = MathHelper.clamp(pitch, -90.0f, 90.0f);

        rotating = true;
        completed = false;
    }

    public void update() {
        if (!rotating) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        if (now >= endTimeMs) {
            player.setYaw(targetYaw);
            player.setPitch(targetPitch);
            rotating = false;
            completed = true;
            return;
        }

        float progress = (float) (now - startTimeMs) / (float) Math.max(1L, endTimeMs - startTimeMs);
        float eased = easeOutCubic(progress);
        player.setYaw(lerp(startYaw, targetYaw, eased));
        player.setPitch(lerp(startPitch, targetPitch, eased));
    }

    public void reset() {
        rotating = false;
        completed = false;
        startTimeMs = 0L;
        endTimeMs = 0L;
    }

    public boolean isRotating() {
        return rotating;
    }

    public boolean isCompleted() {
        return completed;
    }

    public static float easeOutCubic(float number) {
        float clamped = MathHelper.clamp(number, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - clamped, 3.0f);
    }

    public static float easeOutQuart(float number) {
        float clamped = MathHelper.clamp(number, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - clamped, 4.0f);
    }

    public static float easeOutExpo(float number) {
        float clamped = MathHelper.clamp(number, 0.0f, 1.0f);
        return clamped >= 1.0f ? 1.0f : 1.0f - (float) Math.pow(2.0, -10.0 * clamped);
    }

    public static float easeOutBack(float number) {
        float clamped = MathHelper.clamp(number, 0.0f, 1.0f);
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float p = clamped - 1.0f;
        return 1.0f + c3 * p * p * p + c1 * p * p;
    }

    public static RotationDiff getNeededChange(float startYaw, float startPitch, float endYaw, float endPitch) {
        float yawDiff = AngleUtils.normalize180(endYaw - startYaw);
        float pitchDiff = endPitch - startPitch;
        return new RotationDiff(yawDiff, pitchDiff);
    }

    public static RotationDiff getNeededChange(ClientPlayerEntity player, Vec3d target) {
        if (player == null || target == null) {
            return new RotationDiff(0f, 0f);
        }
        Vec3d eye = player.getEyePos();
        double diffX = target.x - eye.x;
        double diffY = target.y - eye.y;
        double diffZ = target.z - eye.z;
        double horizontal = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, horizontal));
        return getNeededChange(player.getYaw(), player.getPitch(), yaw, pitch);
    }

    public static boolean shouldRotate(float yawDiff, float pitchDiff, float yawEpsilon, float pitchEpsilon) {
        return Math.abs(yawDiff) > Math.max(0f, yawEpsilon) || Math.abs(pitchDiff) > Math.max(0f, pitchEpsilon);
    }

    public record RotationDiff(float yaw, float pitch) {
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}

package com.jelly.farmhelper.fabric.state;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

public final class FreelookController {
    private static final FreelookController INSTANCE = new FreelookController();

    private boolean active;
    private Perspective previousPerspective = Perspective.FIRST_PERSON;
    private float cameraYaw;
    private float cameraPitch;
    private float cameraPrevYaw;
    private float cameraPrevPitch;
    private FreelookController() {
    }

    public static FreelookController getInstance() {
        return INSTANCE;
    }

    public void setEnabled(MinecraftClient client, boolean enabled) {
        if (client == null || client.options == null) {
            return;
        }

        if (enabled) {
            if (!active) {
                previousPerspective = client.options.getPerspective();
                syncFromPlayer(client.player);
            }
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            active = true;
            return;
        }

        if (!active) {
            return;
        }
        client.options.setPerspective(previousPerspective);
        active = false;
    }

    public void syncFromPlayer(ClientPlayerEntity player) {
        if (player == null) {
            return;
        }
        cameraYaw = player.getYaw();
        cameraPitch = player.getPitch();
        cameraPrevYaw = cameraYaw;
        cameraPrevPitch = cameraPitch;
    }

    public void onMouseLook(double deltaX, double deltaY) {
        if (!active) {
            return;
        }
        cameraPrevYaw = cameraYaw;
        cameraPrevPitch = cameraPitch;
        cameraYaw += (float) (deltaX * 0.15f);
        cameraPitch += (float) (deltaY * 0.15f);
        cameraPitch = MathHelper.clamp(cameraPitch, -90.0f, 90.0f);
    }

    public boolean isActive() {
        return active;
    }

    public float getCameraYaw() {
        return cameraYaw;
    }

    public float getCameraPitch() {
        return cameraPitch;
    }

    public float getCameraPrevYaw() {
        return cameraPrevYaw;
    }

    public float getCameraPrevPitch() {
        return cameraPrevPitch;
    }

    public void applyModelPose(MinecraftClient client) {
        if (!active || client == null) {
            return;
        }
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        // Keep model/head aligned to real player yaw while freelook camera rotates separately.
        float yaw = player.getYaw();
        player.headYaw = yaw;
        player.lastHeadYaw = yaw;
        player.bodyYaw = yaw;
        player.lastBodyYaw = yaw;
    }
}

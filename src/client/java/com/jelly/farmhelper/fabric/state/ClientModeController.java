package com.jelly.farmhelper.fabric.state;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;

public class ClientModeController {
    private boolean performanceApplied;
    private int previousMaxFps = 120;
    private int previousViewDistance = 12;

    private boolean pipApplied;
    private int previousWindowX;
    private int previousWindowY;
    private int previousWindowWidth;
    private int previousWindowHeight;
    private boolean previousWindowFullscreen;

    private boolean ungrabApplied;

    public void setPerformanceMode(MinecraftClient client, boolean enabled, int maxFps, int viewDistance) {
        if (client == null || client.options == null) {
            return;
        }
        if (enabled) {
            if (!performanceApplied) {
                previousMaxFps = client.options.getMaxFps().getValue();
                previousViewDistance = client.options.getViewDistance().getValue();
                performanceApplied = true;
            }
            client.options.getMaxFps().setValue(Math.max(10, maxFps));
            client.options.getViewDistance().setValue(Math.max(2, viewDistance));
        } else if (performanceApplied) {
            client.options.getMaxFps().setValue(Math.max(10, previousMaxFps));
            client.options.getViewDistance().setValue(Math.max(2, previousViewDistance));
            performanceApplied = false;
        }
    }

    public void setPipMode(MinecraftClient client, boolean enabled) {
        if (client == null || client.getWindow() == null) {
            return;
        }
        Window window = client.getWindow();
        if (enabled) {
            if (!pipApplied) {
                previousWindowX = window.getX();
                previousWindowY = window.getY();
                previousWindowWidth = window.getWidth();
                previousWindowHeight = window.getHeight();
                previousWindowFullscreen = window.isFullscreen();
                pipApplied = true;
            }

            if (window.isFullscreen()) {
                window.toggleFullscreen();
            }
            window.setWindowedSize(420, 252);
            GLFW.glfwSetWindowPos(window.getHandle(), Math.max(0, previousWindowX), Math.max(0, previousWindowY));
            GLFW.glfwSetWindowAttrib(window.getHandle(), GLFW.GLFW_FLOATING, GLFW.GLFW_TRUE);
        } else if (pipApplied) {
            GLFW.glfwSetWindowAttrib(window.getHandle(), GLFW.GLFW_FLOATING, GLFW.GLFW_FALSE);
            window.setWindowedSize(Math.max(320, previousWindowWidth), Math.max(240, previousWindowHeight));
            GLFW.glfwSetWindowPos(window.getHandle(), previousWindowX, previousWindowY);
            if (previousWindowFullscreen && !window.isFullscreen()) {
                window.toggleFullscreen();
            }
            pipApplied = false;
        }
    }

    public void setFreelook(MinecraftClient client, boolean enabled) {
        FreelookController.getInstance().setEnabled(client, enabled);
    }

    public void setMouseUngrab(MinecraftClient client, boolean enabled) {
        if (client == null || client.mouse == null || client.options == null) {
            return;
        }
        if (enabled) {
            client.options.pauseOnLostFocus = false;
            client.mouse.unlockCursor();
            ungrabApplied = true;
        } else if (ungrabApplied) {
            client.mouse.lockCursor();
            ungrabApplied = false;
        }
    }
}

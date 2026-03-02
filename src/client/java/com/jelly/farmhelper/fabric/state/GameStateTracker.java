package com.jelly.farmhelper.fabric.state;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class GameStateTracker {
    private Vec3d lastPlayerPos;
    private int stationaryTicks;

    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            lastPlayerPos = null;
            stationaryTicks = 0;
            return;
        }

        Vec3d current = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        if (lastPlayerPos != null && current.squaredDistanceTo(lastPlayerPos) < 0.0001D) {
            stationaryTicks++;
        } else {
            stationaryTicks = 0;
        }

        lastPlayerPos = current;
    }

    public int getStationaryTicks() {
        return stationaryTicks;
    }
}

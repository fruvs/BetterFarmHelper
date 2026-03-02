package com.jelly.farmhelper.fabric.event;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.Vec3d;

public final class SpawnParticleEvent {
    public final ParticleEffect effect;
    public final boolean longDistance;
    public final Vec3d position;
    public final Vec3d velocity;
    public final int count;

    public SpawnParticleEvent(ParticleEffect effect, boolean longDistance, Vec3d position, Vec3d velocity, int count) {
        this.effect = effect;
        this.longDistance = longDistance;
        this.position = position;
        this.velocity = velocity;
        this.count = count;
    }
}

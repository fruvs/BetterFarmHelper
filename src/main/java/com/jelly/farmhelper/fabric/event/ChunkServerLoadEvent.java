package com.jelly.farmhelper.fabric.event;

import net.minecraft.util.math.ChunkPos;

public final class ChunkServerLoadEvent {
    public final int x;
    public final int z;
    public final ChunkPos chunkPos;

    public ChunkServerLoadEvent(int x, int z) {
        this.x = x;
        this.z = z;
        this.chunkPos = new ChunkPos(x, z);
    }
}

package com.jelly.farmhelper.fabric.event;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;

public final class BlockChangeEvent {
    public final BlockPos pos;
    public final BlockState oldState;
    public final BlockState newState;
    public final RegistryKey<World> worldKey;

    public BlockChangeEvent(BlockPos pos, BlockState oldState, BlockState newState, RegistryKey<World> worldKey) {
        this.pos = pos == null ? BlockPos.ORIGIN : pos.toImmutable();
        this.oldState = oldState;
        this.newState = newState;
        this.worldKey = worldKey;
    }
}

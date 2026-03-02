package com.jelly.farmhelper.fabric.event;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class ClickedBlockEvent {
    public final BlockPos pos;
    public final Direction facing;
    public final Block block;

    public ClickedBlockEvent(BlockPos pos, Direction facing, Block block) {
        this.pos = pos;
        this.facing = facing;
        this.block = block;
    }
}

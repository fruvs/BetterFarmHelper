package com.jelly.farmhelper.fabric.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public final class CropUtils {
    private CropUtils() {
    }

    public static boolean isCrop(BlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof CropBlock
                || block instanceof CocoaBlock
                || block instanceof NetherWartBlock
                || state.isOf(Blocks.CACTUS)
                || state.isOf(Blocks.SUGAR_CANE)
                || state.isOf(Blocks.MELON)
                || state.isOf(Blocks.PUMPKIN)
                || state.isOf(Blocks.RED_MUSHROOM)
                || state.isOf(Blocks.BROWN_MUSHROOM);
    }

    public static boolean isCropReady(BlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMature(state);
        }
        if (block instanceof NetherWartBlock) {
            return readIntProperty(state, NetherWartBlock.AGE) >= 3;
        }
        if (block instanceof CocoaBlock) {
            return readIntProperty(state, CocoaBlock.AGE) >= 2;
        }
        return state.isOf(Blocks.CACTUS)
                || state.isOf(Blocks.SUGAR_CANE)
                || state.isOf(Blocks.MELON)
                || state.isOf(Blocks.PUMPKIN)
                || state.isOf(Blocks.RED_MUSHROOM)
                || state.isOf(Blocks.BROWN_MUSHROOM);
    }

    public static int getGrowthAge(BlockState state) {
        if (state == null) {
            return -1;
        }
        if (state.contains(Properties.AGE_7)) {
            return state.get(Properties.AGE_7);
        }
        if (state.contains(Properties.AGE_3)) {
            return state.get(Properties.AGE_3);
        }
        if (state.contains(Properties.AGE_2)) {
            return state.get(Properties.AGE_2);
        }
        if (state.contains(Properties.AGE_15)) {
            return state.get(Properties.AGE_15);
        }
        return -1;
    }

    public static Box getExpandedHitbox(World world, BlockPos pos, BlockState state, boolean expandedCrops, boolean expandedWarts, boolean expandedCocoa) {
        if (world == null || pos == null || state == null) {
            return Box.from(pos.toCenterPos()).expand(0.5);
        }
        Box shape = state.getOutlineShape(world, pos).getBoundingBox().offset(pos);
        if (!isCrop(state)) {
            return shape;
        }
        double maxY = shape.maxY;
        if (state.getBlock() instanceof CropBlock && expandedCrops) {
            int age = getGrowthAge(state);
            maxY = pos.getY() + (0.125 + 0.875 * Math.max(0, age) / 7.0);
        } else if (state.getBlock() instanceof NetherWartBlock && expandedWarts) {
            int age = getGrowthAge(state);
            maxY = pos.getY() + (0.3125 + 0.5625 * Math.max(0, age) / 3.0);
        } else if (state.getBlock() instanceof CocoaBlock && expandedCocoa) {
            int age = getGrowthAge(state);
            maxY = pos.getY() + (0.5 + 0.25 * Math.max(0, age) / 2.0);
        }
        return new Box(shape.minX, shape.minY, shape.minZ, shape.maxX, maxY, shape.maxZ);
    }

    private static int readIntProperty(BlockState state, IntProperty property) {
        if (state.contains(property)) {
            return state.get(property);
        }
        return 0;
    }
}

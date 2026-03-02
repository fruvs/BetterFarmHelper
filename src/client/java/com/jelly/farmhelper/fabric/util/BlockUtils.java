package com.jelly.farmhelper.fabric.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class BlockUtils {
    private BlockUtils() {
    }

    public static float unitX(float yawDegrees) {
        float yaw = AngleUtils.normalize360(yawDegrees);
        if (yaw < 30.0f) {
            return 0.0f;
        }
        if (yaw < 150.0f) {
            return -1.0f;
        }
        if (yaw < 210.0f) {
            return 0.0f;
        }
        if (yaw < 330.0f) {
            return 1.0f;
        }
        return 0.0f;
    }

    public static float unitZ(float yawDegrees) {
        float yaw = AngleUtils.normalize360(yawDegrees);
        if (yaw < 60.0f) {
            return 1.0f;
        }
        if (yaw < 120.0f) {
            return 0.0f;
        }
        if (yaw < 240.0f) {
            return -1.0f;
        }
        if (yaw < 300.0f) {
            return 0.0f;
        }
        return 1.0f;
    }

    public static BlockPos relativeBlockPos(ClientPlayerEntity player, float x, float y, float z, float yaw) {
        if (player == null) {
            return BlockPos.ORIGIN;
        }
        return BlockPos.ofFloored(
                player.getX() + unitX(yaw) * z + unitZ(yaw) * -x,
                player.getY() + y,
                player.getZ() + unitZ(yaw) * z + unitX(yaw) * x
        );
    }

    public static BlockPos relativeBlockPos(ClientPlayerEntity player, float x, float y, float z) {
        return relativeBlockPos(player, x, y, z, player == null ? 0.0f : player.getYaw());
    }

    public static BlockState getBlockState(MinecraftClient client, BlockPos pos) {
        if (client == null || client.world == null || pos == null) {
            return Blocks.AIR.getDefaultState();
        }
        return client.world.getBlockState(pos);
    }

    public static Block getBlock(MinecraftClient client, BlockPos pos) {
        return getBlockState(client, pos).getBlock();
    }

    public static boolean canWalkThrough(MinecraftClient client, BlockPos pos) {
        if (client == null || client.world == null || pos == null) {
            return false;
        }
        BlockState feet = client.world.getBlockState(pos);
        BlockState head = client.world.getBlockState(pos.up());
        boolean feetOpen = feet.isAir() || feet.getCollisionShape(client.world, pos).isEmpty();
        boolean headOpen = head.isAir() || head.getCollisionShape(client.world, pos.up()).isEmpty();
        return feetOpen && headOpen;
    }

    public static boolean hasNearbyBlock(MinecraftClient client, BlockPos center, Predicate<BlockState> predicate, int radius) {
        return countNearbyBlocks(client, center, predicate, radius, radius, radius) > 0;
    }

    public static int countNearbyBlocks(
            MinecraftClient client,
            BlockPos center,
            Predicate<BlockState> predicate,
            int radiusX,
            int radiusY,
            int radiusZ
    ) {
        if (client == null || client.world == null || center == null || predicate == null) {
            return 0;
        }
        int count = 0;
        for (int x = -radiusX; x <= radiusX; x++) {
            for (int y = -radiusY; y <= radiusY; y++) {
                for (int z = -radiusZ; z <= radiusZ; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (predicate.test(client.world.getBlockState(pos))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public static List<BlockPos> findNearbyBlocks(
            MinecraftClient client,
            BlockPos center,
            Predicate<BlockState> predicate,
            int radius
    ) {
        List<BlockPos> found = new ArrayList<>();
        if (client == null || client.world == null || center == null || predicate == null) {
            return found;
        }
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (predicate.test(client.world.getBlockState(pos))) {
                        found.add(pos.toImmutable());
                    }
                }
            }
        }
        return found;
    }

    public static BlockPos nearestMatching(
            MinecraftClient client,
            Vec3d source,
            Predicate<BlockState> predicate,
            int radius
    ) {
        if (client == null || source == null) {
            return null;
        }
        BlockPos center = BlockPos.ofFloored(source);
        BlockPos best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (BlockPos pos : findNearbyBlocks(client, center, predicate, radius)) {
            double distanceSq = source.squaredDistanceTo(Vec3d.ofCenter(pos));
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = pos;
            }
        }
        return best;
    }

    public static boolean isDirectionWalkable(MinecraftClient client, BlockPos origin, Direction direction) {
        if (origin == null || direction == null) {
            return false;
        }
        return canWalkThrough(client, origin.offset(direction));
    }

    public static boolean blockHasCollision(MinecraftClient client, BlockPos pos) {
        if (client == null || client.world == null || pos == null) {
            return false;
        }
        return !client.world.getBlockState(pos).getCollisionShape(client.world, pos).isEmpty();
    }

    public static boolean isAboveHeadClear(MinecraftClient client, ClientPlayerEntity player, int maxHeight) {
        if (client == null || client.world == null || player == null) {
            return false;
        }
        BlockPos start = player.getBlockPos().up();
        int top = Math.min(start.getY() + Math.max(1, maxHeight), client.world.getTopYInclusive());
        for (int y = start.getY(); y <= top; y++) {
            if (blockHasCollision(client, new BlockPos(start.getX(), y, start.getZ()))) {
                return false;
            }
        }
        return true;
    }

    public static int bedrockCount(MinecraftClient client, ClientPlayerEntity player, int radiusX, int radiusY, int radiusZ) {
        if (client == null || client.world == null || player == null) {
            return 0;
        }
        return countNearbyBlocks(
                client,
                player.getBlockPos(),
                state -> state.isOf(Blocks.BEDROCK),
                Math.max(1, radiusX),
                Math.max(1, radiusY),
                Math.max(1, radiusZ)
        );
    }

    public static BlockPos getBlockPosLookingAt(MinecraftClient client, double maxDistance, boolean includeFluids) {
        if (client == null || client.world == null || client.player == null || client.gameRenderer == null) {
            return null;
        }
        Vec3d start = client.player.getEyePos();
        Vec3d end = start.add(client.player.getRotationVec(1.0f).multiply(Math.max(1.0, maxDistance)));
        BlockHitResult hit = rayTraceBlocks(client, start, end, includeFluids);
        return hit == null ? null : hit.getBlockPos().toImmutable();
    }

    public static BlockHitResult rayTraceBlocks(MinecraftClient client, Vec3d from, Vec3d to, boolean includeFluids) {
        if (client == null || client.world == null || from == null || to == null) {
            return null;
        }
        HitResult result = client.world.raycast(new RaycastContext(
                from,
                to,
                RaycastContext.ShapeType.COLLIDER,
                includeFluids ? RaycastContext.FluidHandling.ANY : RaycastContext.FluidHandling.NONE,
                client.player
        ));
        if (result instanceof BlockHitResult blockHitResult) {
            return blockHitResult;
        }
        return null;
    }

    public static boolean canBlockBeSeen(MinecraftClient client, ClientPlayerEntity player, BlockPos pos, double maxDistance) {
        if (client == null || client.world == null || player == null || pos == null) {
            return false;
        }
        Vec3d start = player.getEyePos();
        Vec3d target = Vec3d.ofCenter(pos);
        if (start.squaredDistanceTo(target) > maxDistance * maxDistance) {
            return false;
        }
        BlockHitResult hit = rayTraceBlocks(client, start, target, true);
        return hit != null && pos.equals(hit.getBlockPos());
    }

    public static int cropAroundAmount(MinecraftClient client, BlockPos center, int radius) {
        return countNearbyBlocks(client, center, CropUtils::isCrop, radius, radius, radius);
    }

    public static List<BlockPos> getBlocksInBox(Box box) {
        List<BlockPos> positions = new ArrayList<>();
        if (box == null) {
            return positions;
        }
        int minX = (int) Math.floor(box.minX);
        int minY = (int) Math.floor(box.minY);
        int minZ = (int) Math.floor(box.minZ);
        int maxX = (int) Math.floor(box.maxX);
        int maxY = (int) Math.floor(box.maxY);
        int maxZ = (int) Math.floor(box.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
    }

    public static double horizontalDistanceSq(Vec3d a, Vec3d b) {
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }
}

package com.jelly.farmhelper.fabric.util;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.config.struct.RewarpPoint;
import com.jelly.farmhelper.fabric.macro.LegacyMacroType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.Block;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShearsItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlayerUtils {
    private static final Pattern SKYBLOCK_ID_PATTERN = Pattern.compile("id[=:]\"?([A-Z0-9_]{5,})");
    private static final Pattern SKYBLOCK_TIER_PATTERN = Pattern.compile("_(\\d+)$");

    private PlayerUtils() {
    }

    public enum FarmingCrop {
        WHEAT,
        CARROT,
        POTATO,
        NETHER_WART,
        SUGAR_CANE,
        COCOA_BEANS,
        MELON,
        PUMPKIN,
        MUSHROOM,
        CACTUS,
        ROSE,
        SUNFLOWER,
        NONE
    }

    public static boolean isInventoryEmpty(ClientPlayerEntity player) {
        if (player == null) {
            return true;
        }
        for (ItemStack stack : player.getInventory().getMainStacks()) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static boolean isInventoryFull(ClientPlayerEntity player) {
        if (player == null) {
            return false;
        }
        for (ItemStack stack : player.getInventory().getMainStacks()) {
            if (stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static boolean isSpawnLocationSet(FarmHelperConfig config) {
        return config != null && config.spawnPosY > 0;
    }

    public static BlockPos getConfiguredSpawn(FarmHelperConfig config) {
        if (!isSpawnLocationSet(config)) {
            return null;
        }
        return new BlockPos(config.spawnPosX, config.spawnPosY, config.spawnPosZ);
    }

    public static boolean isNearSpawn(ClientPlayerEntity player, FarmHelperConfig config, int xzTolerance, int yTolerance) {
        if (player == null || !isSpawnLocationSet(config)) {
            return false;
        }
        BlockPos playerPos = player.getBlockPos();
        return Math.abs(playerPos.getX() - config.spawnPosX) <= Math.max(0, xzTolerance)
                && Math.abs(playerPos.getY() - config.spawnPosY) <= Math.max(0, yTolerance)
                && Math.abs(playerPos.getZ() - config.spawnPosZ) <= Math.max(0, xzTolerance);
    }

    public static Optional<RewarpPoint> nearestRewarpPoint(ClientPlayerEntity player, FarmHelperConfig config) {
        if (player == null || config == null || config.rewarpPoints == null || config.rewarpPoints.isEmpty()) {
            return Optional.empty();
        }
        Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());
        return config.rewarpPoints.stream()
                .filter(point -> point != null)
                .min(Comparator.comparingDouble(point -> pos.squaredDistanceTo(point.x + 0.5, point.y + 0.5, point.z + 0.5)));
    }

    public static boolean isNearRewarpPoint(ClientPlayerEntity player, FarmHelperConfig config, int radius) {
        if (player == null) {
            return false;
        }
        int r = Math.max(1, radius);
        BlockPos pos = player.getBlockPos();
        return nearestRewarpPoint(player, config)
                .map(point -> Math.abs(pos.getX() - point.x) <= r
                        && Math.abs(pos.getY() - point.y) <= Math.max(2, r)
                        && Math.abs(pos.getZ() - point.z) <= r)
                .orElse(false);
    }

    public static int selectBestToolForMacro(MinecraftClient client, LegacyMacroType macroType) {
        if (client == null || client.player == null) {
            return -1;
        }
        ClientPlayerEntity player = client.player;
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            String displayName = stack.getName().getString().toLowerCase(Locale.ROOT);
            String itemId = extractSkyblockItemId(stack);
            String metadata = (displayName + " " + stack.getItem() + " " + stack.getComponents()).toLowerCase(Locale.ROOT);
            int score = scoreToolForType(macroType, stack, metadata, itemId);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        if (bestSlot >= 0 && bestScore > 0) {
            player.getInventory().setSelectedSlot(bestSlot);
            return bestSlot;
        }
        return -1;
    }

    public static boolean canBreakBlock(MinecraftClient client, BlockPos pos) {
        if (client == null || client.world == null || pos == null) {
            return false;
        }
        Block block = client.world.getBlockState(pos).getBlock();
        return block.getDefaultState().getHardness(client.world, pos) >= 0.0f;
    }

    public static FarmingCrop getCropBasedOnMouseOver(MinecraftClient client) {
        if (client == null || client.world == null || !(client.crosshairTarget instanceof BlockHitResult hitResult)) {
            return FarmingCrop.NONE;
        }
        return fromBlockState(client.world.getBlockState(hitResult.getBlockPos()));
    }

    public static FarmingCrop getFarmingCrop(MinecraftClient client) {
        FarmingCrop hovered = getCropBasedOnMouseOver(client);
        if (hovered != FarmingCrop.NONE) {
            return hovered;
        }
        if (client == null || client.world == null || client.player == null) {
            return FarmingCrop.NONE;
        }
        BlockPos center = client.player.getBlockPos();
        Vec3d playerPos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        FarmingCrop best = FarmingCrop.NONE;
        double bestDistanceSq = Double.MAX_VALUE;
        for (int x = -3; x <= 3; x++) {
            for (int y = -1; y <= 4; y++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos pos = center.add(x, y, z);
                    BlockState state = client.world.getBlockState(pos);
                    FarmingCrop crop = fromBlockState(state);
                    if (crop == FarmingCrop.NONE) {
                        continue;
                    }
                    double distanceSq = Vec3d.ofCenter(pos).squaredDistanceTo(playerPos);
                    if (distanceSq < bestDistanceSq) {
                        bestDistanceSq = distanceSq;
                        best = crop;
                    }
                }
            }
        }
        return best;
    }

    public static boolean shouldPushBack(MinecraftClient client) {
        if (client == null || client.player == null) {
            return false;
        }
        ClientPlayerEntity player = client.player;
        float yaw = AngleUtils.closestCardinal(player.getYaw());
        BlockPos behind = BlockUtils.relativeBlockPos(player, 0, 0, -1, yaw);
        BlockPos ahead = BlockUtils.relativeBlockPos(player, 0, 0, 1, yaw);
        boolean blockedBehind = !BlockUtils.canWalkThrough(client, behind);
        boolean blockedAhead = !BlockUtils.canWalkThrough(client, ahead);
        return blockedBehind && !blockedAhead;
    }

    public static boolean shouldWalkForwards(MinecraftClient client) {
        if (client == null || client.player == null) {
            return false;
        }
        ClientPlayerEntity player = client.player;
        float yaw = AngleUtils.closestCardinal(player.getYaw());
        BlockPos ahead = BlockUtils.relativeBlockPos(player, 0, 0, 1, yaw);
        return BlockUtils.canWalkThrough(client, ahead);
    }

    public static boolean isPlayerSuffocating(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return false;
        }
        BlockPos head = client.player.getBlockPos().up();
        return BlockUtils.blockHasCollision(client, head) || client.player.isInsideWall();
    }

    public static boolean isInBarn(MinecraftClient client) {
        if (client == null || client.player == null) {
            return false;
        }
        Optional<Integer> plot = PlotUtils.getPlotNumber(client.player.getBlockPos());
        return plot.isPresent() && plot.get() == 0;
    }

    public static void setSpawnLocation(MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        BlockPos pos = client.player.getBlockPos();
        config.spawnPosX = pos.getX();
        config.spawnPosY = pos.getY();
        config.spawnPosZ = pos.getZ();
        config.spawnYaw = client.player.getYaw();
        config.spawnPitch = client.player.getPitch();
        config.spawnPlot = PlotUtils.getPlotNumber(pos).orElse(0);
        FarmHelperFabric.getConfigManager().save();
    }

    public static Vec3d getSpawnLocation(FarmHelperConfig config) {
        if (!isSpawnLocationSet(config)) {
            return null;
        }
        return new Vec3d(config.spawnPosX + 0.5, config.spawnPosY + 0.5, config.spawnPosZ + 0.5);
    }

    private static FarmingCrop fromBlockState(BlockState state) {
        if (state == null) {
            return FarmingCrop.NONE;
        }
        if (state.isOf(Blocks.WHEAT)) return FarmingCrop.WHEAT;
        if (state.isOf(Blocks.CARROTS)) return FarmingCrop.CARROT;
        if (state.isOf(Blocks.POTATOES)) return FarmingCrop.POTATO;
        if (state.isOf(Blocks.NETHER_WART)) return FarmingCrop.NETHER_WART;
        if (state.isOf(Blocks.SUGAR_CANE)) return FarmingCrop.SUGAR_CANE;
        if (state.isOf(Blocks.COCOA)) return FarmingCrop.COCOA_BEANS;
        if (state.isOf(Blocks.MELON)) return FarmingCrop.MELON;
        if (state.isOf(Blocks.PUMPKIN)) return FarmingCrop.PUMPKIN;
        if (state.isOf(Blocks.RED_MUSHROOM) || state.isOf(Blocks.BROWN_MUSHROOM)) return FarmingCrop.MUSHROOM;
        if (state.isOf(Blocks.CACTUS)) return FarmingCrop.CACTUS;
        if (state.isOf(Blocks.ROSE_BUSH)) return FarmingCrop.ROSE;
        if (state.isOf(Blocks.SUNFLOWER)) return FarmingCrop.SUNFLOWER;
        return FarmingCrop.NONE;
    }

    private static int scoreToolForType(LegacyMacroType type, ItemStack stack, String metadata, String itemId) {
        int score = 0;
        boolean hoe = stack.getItem() instanceof HoeItem;
        boolean axe = stack.getItem() instanceof AxeItem;
        boolean shears = stack.getItem() instanceof ShearsItem;

        if (hoe) score += 20;
        if (axe) score += 12;
        if (shears) score += 8;

        int tier = extractItemTier(itemId);
        score += Math.min(20, tier * 2);

        if (!itemId.isEmpty()) {
            switch (type) {
                case S_COCOA_BEANS, S_COCOA_BEANS_TRAPDOORS, S_COCOA_BEANS_LEFT_RIGHT -> {
                    if (itemId.contains("COCO_CHOPPER")) score += 180;
                    else if (itemId.contains("CHOPPER")) score += 120;
                }
                case S_PUMPKIN_MELON, S_PUMPKIN_MELON_MELONGKINGDE, S_PUMPKIN_MELON_DEFAULT_PLOT -> {
                    if (itemId.contains("MELON_DICER") || itemId.contains("PUMPKIN_DICER")) score += 180;
                    else if (itemId.contains("_DICER")) score += 120;
                }
                case S_MUSHROOM, S_MUSHROOM_ROTATE, S_MUSHROOM_SDS -> {
                    if (itemId.contains("FUNGI_CUTTER")) score += 180;
                    if (itemId.contains("DAEDALUS_AXE")) score += 170;
                }
                case S_SUGAR_CANE -> {
                    if (itemId.contains("HOE_CANE")) score += 180;
                }
                case S_CACTUS, S_CACTUS_SUNTZU -> {
                    if (itemId.contains("CACTUS_KNIFE")) score += 180;
                }
                default -> {
                    if (itemId.contains("HOE_WHEAT")
                            || itemId.contains("HOE_CARROT")
                            || itemId.contains("HOE_POTATO")
                            || itemId.contains("HOE_WARTS")) {
                        score += 170;
                    } else if (itemId.contains("HOE_")) {
                        score += 110;
                    } else if (itemId.contains("_DICER") || itemId.contains("CHOPPER")) {
                        score += 90;
                    }
                }
            }
        }

        if (metadata.contains("vacuum")
                || metadata.contains("repellent")
                || metadata.contains("sprayonator")
                || metadata.contains("cookie")
                || metadata.contains("god pot")) {
            score -= 90;
        }
        return score;
    }

    private static String extractSkyblockItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        try {
            NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (customData != null) {
                NbtCompound root = customData.copyNbt();
                if (root != null) {
                    NbtCompound extra = root.getCompoundOrEmpty("ExtraAttributes");
                    String id = extra.getString("id", "").trim();
                    if (!id.isEmpty()) {
                        return id.toUpperCase(Locale.ROOT);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        String componentsDump = String.valueOf(stack.getComponents()).toUpperCase(Locale.ROOT);
        Matcher matcher = SKYBLOCK_ID_PATTERN.matcher(componentsDump);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static int extractItemTier(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return 0;
        }
        Matcher matcher = SKYBLOCK_TIER_PATTERN.matcher(itemId);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}

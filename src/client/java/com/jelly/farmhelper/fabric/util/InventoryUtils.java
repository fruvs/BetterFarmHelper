package com.jelly.farmhelper.fabric.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InventoryUtils {
    private static final Pattern SKYBLOCK_ID_PATTERN = Pattern.compile("id[=:]\"?([A-Z0-9_]{4,})");

    private InventoryUtils() {
    }

    public static int inventoryFillPercent(ClientPlayerEntity player) {
        if (player == null) {
            return 0;
        }
        int total = 0;
        int used = 0;
        for (ItemStack stack : player.getInventory().getMainStacks()) {
            total++;
            if (!stack.isEmpty()) {
                used++;
            }
        }
        if (total == 0) {
            return 0;
        }
        return (int) (used * 100.0 / total);
    }

    public static boolean holdItemInHotbar(ClientPlayerEntity player, String... nameContains) {
        int slot = findHotbarSlotByName(player, nameContains);
        if (slot < 0) {
            return false;
        }
        player.getInventory().setSelectedSlot(slot);
        return true;
    }

    public static int findHotbarSlotByName(ClientPlayerEntity player, String... nameContains) {
        if (player == null || nameContains == null || nameContains.length == 0) {
            return -1;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            String name = stack.getName().getString().toLowerCase(Locale.ROOT);
            for (String probe : nameContains) {
                if (probe != null && !probe.isBlank() && name.contains(probe.toLowerCase(Locale.ROOT))) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static int findHotbarSlotByHypixelId(ClientPlayerEntity player, String containsId) {
        if (player == null || containsId == null || containsId.isBlank()) {
            return -1;
        }
        String probe = containsId.toUpperCase(Locale.ROOT);
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            String id = readSkyblockId(stack);
            if (id != null && id.contains(probe)) {
                return i;
            }
        }
        return -1;
    }

    public static int findInventorySlotByName(ClientPlayerEntity player, String nameContains) {
        if (player == null || nameContains == null || nameContains.isBlank()) {
            return -1;
        }
        String probe = nameContains.toLowerCase(Locale.ROOT);
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            String name = stack.getName().getString().toLowerCase(Locale.ROOT);
            if (name.contains(probe)) {
                return i;
            }
        }
        return -1;
    }

    public static Optional<Slot> findOpenContainerSlotContains(MinecraftClient client, String nameContains) {
        if (client == null || client.player == null || client.player.currentScreenHandler == null || nameContains == null) {
            return Optional.empty();
        }
        String probe = nameContains.toLowerCase(Locale.ROOT);
        for (Slot slot : client.player.currentScreenHandler.slots) {
            if (slot == null || !slot.hasStack()) {
                continue;
            }
            String name = slot.getStack().getName().getString().toLowerCase(Locale.ROOT);
            if (name.contains(probe)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    public static List<String> getItemLore(ItemStack stack) {
        List<String> lore = new ArrayList<>();
        if (stack == null || stack.isEmpty()) {
            return lore;
        }
        Iterable<Text> lines = stack.getOrDefault(DataComponentTypes.LORE, net.minecraft.component.type.LoreComponent.DEFAULT).styledLines();
        for (Text line : lines) {
            lore.add(line.getString());
        }
        return lore;
    }

    public static String readSkyblockId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        String raw = customData.toString();
        Matcher matcher = SKYBLOCK_ID_PATTERN.matcher(raw);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static boolean hasItemInInventory(ClientPlayerEntity player, String nameContains) {
        return findInventorySlotByName(player, nameContains) >= 0;
    }

    public static boolean hasItemInHotbar(ClientPlayerEntity player, String... names) {
        return findHotbarSlotByName(player, names) >= 0;
    }

    public static int getAmountOfItemInInventory(ClientPlayerEntity player, String exactName) {
        if (player == null || exactName == null || exactName.isBlank()) {
            return 0;
        }
        int amount = 0;
        String expected = exactName.toLowerCase(Locale.ROOT);
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getName().getString().toLowerCase(Locale.ROOT).equals(expected)) {
                amount += stack.getCount();
            }
        }
        return amount;
    }

    public static int getAmountOfItemInInventoryContains(ClientPlayerEntity player, String nameContains) {
        if (player == null || nameContains == null || nameContains.isBlank()) {
            return 0;
        }
        int amount = 0;
        String probe = nameContains.toLowerCase(Locale.ROOT);
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getName().getString().toLowerCase(Locale.ROOT).contains(probe)) {
                amount += stack.getCount();
            }
        }
        return amount;
    }

    public static boolean canFitItemInInventory(ClientPlayerEntity player, String exactName, int amount) {
        if (player == null || amount <= 0) {
            return true;
        }
        int freeCapacity = 0;
        String expected = exactName == null ? "" : exactName.toLowerCase(Locale.ROOT);
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) {
                freeCapacity += 64;
                continue;
            }
            if (!expected.isBlank() && stack.getName().getString().toLowerCase(Locale.ROOT).equals(expected)) {
                freeCapacity += Math.max(0, stack.getMaxCount() - stack.getCount());
            }
            if (freeCapacity >= amount) {
                return true;
            }
        }
        return freeCapacity >= amount;
    }

    public static int getRancherBootSpeed(ClientPlayerEntity player) {
        if (player == null) {
            return -1;
        }
        Pattern speedPattern = Pattern.compile("current speed cap:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            for (String loreLine : getItemLore(stack)) {
                Matcher matcher = speedPattern.matcher(loreLine.replaceAll("§.", ""));
                if (matcher.find()) {
                    try {
                        return Integer.parseInt(matcher.group(1));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return -1;
    }

    public static boolean isInventoryLoaded(MinecraftClient client) {
        if (client == null || client.player == null || client.player.currentScreenHandler == null) {
            return false;
        }
        if (!(client.currentScreen instanceof HandledScreen<?>)) {
            return false;
        }
        return !client.player.currentScreenHandler.slots.isEmpty();
    }

    public static String getInventoryName(MinecraftClient client) {
        if (client == null || client.currentScreen == null || !(client.currentScreen instanceof HandledScreen<?> screen)) {
            return null;
        }
        return screen.getTitle() == null ? null : screen.getTitle().getString();
    }

    public static void openInventory(MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }
        client.setScreen(new InventoryScreen(client.player));
    }

    public static void clickSlotWithId(MinecraftClient client, int syncId, int slotId, ClickType clickType, ClickMode clickMode) {
        if (client == null || client.player == null || client.interactionManager == null) {
            return;
        }
        client.interactionManager.clickSlot(
                syncId,
                slotId,
                clickType.button,
                clickMode.actionType,
                client.player
        );
    }

    public static void clickContainerSlot(MinecraftClient client, int slotId, ClickType clickType, ClickMode clickMode) {
        if (client == null || client.player == null || client.player.currentScreenHandler == null) {
            return;
        }
        clickSlotWithId(client, client.player.currentScreenHandler.syncId, slotId, clickType, clickMode);
    }

    public static void clickSlot(MinecraftClient client, int slotId, ClickType clickType, ClickMode clickMode) {
        if (client == null || client.player == null || client.player.playerScreenHandler == null) {
            return;
        }
        clickSlotWithId(client, client.player.playerScreenHandler.syncId, slotId, clickType, clickMode);
    }

    public static void swapSlots(MinecraftClient client, int slot, int hotbarSlot) {
        if (client == null || client.player == null || client.interactionManager == null) {
            return;
        }
        client.interactionManager.clickSlot(
                client.player.currentScreenHandler.syncId,
                slot,
                hotbarSlot,
                SlotActionType.SWAP,
                client.player
        );
    }

    public static ArrayList<Slot> getIndexesOfItemsFromContainer(MinecraftClient client, java.util.function.Predicate<Slot> predicate) {
        ArrayList<Slot> indexes = new ArrayList<>();
        if (client == null || client.player == null || client.player.currentScreenHandler == null || predicate == null) {
            return indexes;
        }
        for (Slot slot : client.player.currentScreenHandler.slots) {
            if (slot != null && slot.hasStack() && predicate.test(slot)) {
                indexes.add(slot);
            }
        }
        return indexes;
    }

    public static ArrayList<Slot> getIndexesOfItemsFromInventory(MinecraftClient client, java.util.function.Predicate<Slot> predicate) {
        ArrayList<Slot> indexes = new ArrayList<>();
        if (client == null || client.player == null || client.player.playerScreenHandler == null || predicate == null) {
            return indexes;
        }
        for (Slot slot : client.player.playerScreenHandler.slots) {
            if (slot != null && slot.hasStack() && predicate.test(slot)) {
                indexes.add(slot);
            }
        }
        return indexes;
    }

    public enum ClickType {
        LEFT(0),
        RIGHT(1);

        private final int button;

        ClickType(int button) {
            this.button = button;
        }
    }

    public enum ClickMode {
        PICKUP(SlotActionType.PICKUP),
        QUICK_MOVE(SlotActionType.QUICK_MOVE),
        SWAP(SlotActionType.SWAP),
        CLONE(SlotActionType.CLONE),
        THROW(SlotActionType.THROW),
        QUICK_CRAFT(SlotActionType.QUICK_CRAFT),
        PICKUP_ALL(SlotActionType.PICKUP_ALL);

        private final SlotActionType actionType;

        ClickMode(SlotActionType actionType) {
            this.actionType = actionType;
        }
    }
}

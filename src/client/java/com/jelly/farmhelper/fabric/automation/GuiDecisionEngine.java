package com.jelly.farmhelper.fabric.automation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

public class GuiDecisionEngine {
    private static final int NO_MATCH = Integer.MIN_VALUE;

    public boolean isScreenTitleMatch(MinecraftClient client, String expected) {
        if (client == null || client.currentScreen == null || expected == null || expected.isBlank()) {
            return false;
        }
        String title = client.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        return title.contains(expected.trim().toLowerCase(Locale.ROOT));
    }

    public OptionalInt findBestSlot(MinecraftClient client, String queryRaw) {
        if (client == null || client.player == null || client.currentScreen == null || queryRaw == null || queryRaw.isBlank()) {
            return OptionalInt.empty();
        }

        SlotQuery query = SlotQuery.parse(queryRaw);
        int bestSlotId = -1;
        int bestScore = NO_MATCH;

        for (Slot slot : client.player.currentScreenHandler.slots) {
            if (slot == null) {
                continue;
            }
            if (query.exactSlotId != null && slot.id != query.exactSlotId) {
                continue;
            }
            if (query.exactSlotId != null && !query.hasTextTokens()) {
                return OptionalInt.of(slot.id);
            }
            if (slot.getStack() == null || slot.getStack().isEmpty()) {
                continue;
            }
            int score = scoreSlot(slot.getStack(), query, client.player);
            if (score > bestScore) {
                bestScore = score;
                bestSlotId = slot.id;
            }
        }

        if (bestScore <= NO_MATCH / 2 || bestSlotId < 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(bestSlotId);
    }

    public String extractItemMetadata(ItemStack stack, PlayerEntity player) {
        StringBuilder builder = new StringBuilder();
        builder.append(stack.getName().getString().toLowerCase(Locale.ROOT));
        builder.append(' ');
        builder.append(stack.toString().toLowerCase(Locale.ROOT));

        // Prefer reflection so this helper remains resilient across mapping/API updates.
        appendReflected(builder, stack, "getComponents");
        appendReflected(builder, stack, "getComponentChanges");
        appendReflected(builder, stack, "getNbt");
        appendTooltip(builder, stack, player);
        return builder.toString();
    }

    private int scoreSlot(ItemStack stack, SlotQuery query, PlayerEntity player) {
        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        String metadata = extractItemMetadata(stack, player);
        int score = 0;

        for (String token : query.nameTokens) {
            if (!name.contains(token)) {
                return NO_MATCH;
            }
            score += 6;
        }

        for (String token : query.loreTokens) {
            if (!metadata.contains(token)) {
                return NO_MATCH;
            }
            score += 4;
        }

        for (String token : query.anyTokens) {
            if (name.contains(token)) {
                score += 3;
            } else if (metadata.contains(token)) {
                score += 2;
            } else {
                return NO_MATCH;
            }
        }

        for (String token : query.notTokens) {
            if (name.contains(token) || metadata.contains(token)) {
                return NO_MATCH;
            }
        }

        if (score == 0) {
            return NO_MATCH;
        }
        return score;
    }

    private void appendReflected(StringBuilder builder, ItemStack stack, String methodName) {
        try {
            Method method = stack.getClass().getMethod(methodName);
            Object value = method.invoke(stack);
            if (value != null) {
                builder.append(' ').append(value.toString().toLowerCase(Locale.ROOT));
            }
        } catch (Throwable ignored) {
            // Best effort only.
        }
    }

    /**
     * Appends tooltip text from an ItemStack using the 1.21 tooltip API directly.
     * The old reflection-based approach failed because it passed null for Item.TooltipContext,
     * which caused an NPE that was silently caught — meaning lore text was never captured.
     */
    private void appendTooltip(StringBuilder builder, ItemStack stack, PlayerEntity player) {
        try {
            List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, player, TooltipType.BASIC);
            for (Text line : tooltip) {
                builder.append(' ').append(line.getString().toLowerCase(Locale.ROOT));
            }
        } catch (Throwable ignored) {
            // Best effort only — some edge-case items may not support tooltip generation.
        }
    }

    private static final class SlotQuery {
        private Integer exactSlotId;
        private final List<String> nameTokens = new ArrayList<>();
        private final List<String> loreTokens = new ArrayList<>();
        private final List<String> anyTokens = new ArrayList<>();
        private final List<String> notTokens = new ArrayList<>();

        static SlotQuery parse(String raw) {
            SlotQuery query = new SlotQuery();
            String[] parts = raw.toLowerCase(Locale.ROOT).split(";");
            for (String part : parts) {
                String token = part.trim();
                if (token.isEmpty()) {
                    continue;
                }
                if (token.startsWith("name:")) {
                    query.addTokens(query.nameTokens, token.substring("name:".length()));
                } else if (token.startsWith("lore:")) {
                    query.addTokens(query.loreTokens, token.substring("lore:".length()));
                } else if (token.startsWith("not:")) {
                    query.addTokens(query.notTokens, token.substring("not:".length()));
                } else if (token.startsWith("slot:")) {
                    try {
                        query.exactSlotId = Integer.parseInt(token.substring("slot:".length()).trim());
                    } catch (NumberFormatException ignored) {
                        query.exactSlotId = null;
                    }
                } else {
                    query.addTokens(query.anyTokens, token);
                }
            }
            return query;
        }

        private boolean hasTextTokens() {
            return !nameTokens.isEmpty() || !loreTokens.isEmpty() || !anyTokens.isEmpty() || !notTokens.isEmpty();
        }

        private void addTokens(List<String> target, String raw) {
            for (String token : raw.trim().split("\\s+")) {
                String normalized = token.trim();
                if (!normalized.isEmpty()) {
                    target.add(normalized);
                }
            }
        }
    }
}

package com.jelly.farmhelper.fabric.automation;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PestEntityHeuristics {
    private static final List<String> PEST_KEYWORDS = List.of(
            "beetle", "cricket", "earthworm", "fly", "locust", "mite",
            "mosquito", "moth", "rat", "slug", "praying mantis",
            "firefly", "dragonfly", "pest"
    );
    private static final List<String> PEST_ENTITY_TYPE_KEYWORDS = List.of(
            "silverfish", "bat", "endermite", "spider", "cave_spider"
    );

    // Legacy texture-signature fragments from the 1.8.9 implementation.
    private static final List<String> TEXTURE_SIGNATURES = List.of(
            "70a1e836bf1968b2", // beetle
            "a24c69f96ce55622", // cricket
            "6403ba4027a333d8", // earthworm
            "9d90e777826a5246", // fly
            "4b24a482a32db1ea", // locust
            "be6baf6431a9daa2", // mite
            "52a9fe05bc663efc", // mosquito
            "65485c4b34e5b547", // moth
            "a8abb471db0ab787", // rat
            "7a79d0fd677b5453", // slug
            "1e04bb6367caa4e8", // praying mantis
            "4ce79e90adf34718", // firefly
            "254aff4c0b2dce3a"  // dragonfly
    );

    public double score(Entity entity) {
        if (entity == null) {
            return 0.0;
        }
        double score = 0.0;
        String name = normalizedName(entity);
        String typeName = normalizedType(entity);
        String tags = commandTags(entity);
        String equipment = equipmentMetadata(entity);
        boolean nameHasKeyword = containsAny(name, PEST_KEYWORDS);
        boolean typeMatches = containsAny(typeName, PEST_ENTITY_TYPE_KEYWORDS);
        boolean tagsMatch = containsAny(tags, PEST_KEYWORDS);
        boolean equipmentHasKeyword = containsAny(equipment, PEST_KEYWORDS);
        boolean textureSignature = containsAny(equipment, TEXTURE_SIGNATURES);

        if (nameHasKeyword) {
            score += 8.0;
        }
        if (typeMatches) {
            score += 7.5;
        }
        if (tagsMatch) {
            score += 3.5;
        }
        if (equipmentHasKeyword) {
            score += 4.0;
        }
        if (textureSignature) {
            score += 12.0;
        }
        if (entity.hasCustomName()) {
            score += 1.0;
        }
        if (entity.isInvisible()) {
            score += 0.6;
        }

        // Armor stands are common false positives unless we have strong evidence.
        if (typeName.contains("armor_stand") && !textureSignature && !equipmentHasKeyword) {
            score -= 4.0;
        }

        return score;
    }

    public boolean isLikelyPest(Entity entity) {
        return score(entity) >= 10.5;
    }

    public boolean isConfirmedPest(Entity entity) {
        if (entity == null) {
            return false;
        }
        String name = normalizedName(entity);
        String typeName = normalizedType(entity);
        String tags = commandTags(entity);
        String equipment = equipmentMetadata(entity);
        boolean nameHasKeyword = containsAny(name, PEST_KEYWORDS);
        boolean typeMatches = containsAny(typeName, PEST_ENTITY_TYPE_KEYWORDS);
        boolean tagsMatch = containsAny(tags, PEST_KEYWORDS);
        boolean equipmentHasKeyword = containsAny(equipment, PEST_KEYWORDS);
        boolean textureSignature = containsAny(equipment, TEXTURE_SIGNATURES);
        if (textureSignature) {
            return true;
        }
        if (nameHasKeyword && typeMatches) {
            return true;
        }
        return (nameHasKeyword || equipmentHasKeyword) && tagsMatch;
    }

    private boolean containsAny(String haystack, List<String> needles) {
        if (haystack == null || haystack.isBlank()) {
            return false;
        }
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String commandTags(Entity entity) {
        try {
            Set<String> tags = entity.getCommandTags();
            return String.join(" ", tags).toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String normalizedName(Entity entity) {
        return entity.getName().getString().toLowerCase(Locale.ROOT);
    }

    private String normalizedType(Entity entity) {
        return entity.getType().toString().toLowerCase(Locale.ROOT);
    }

    private String equipmentMetadata(Entity entity) {
        StringBuilder builder = new StringBuilder();
        for (ItemStack stack : reflectedEquipment(entity)) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            builder.append(' ').append(stack.getName().getString().toLowerCase(Locale.ROOT));
            builder.append(' ').append(stack.toString().toLowerCase(Locale.ROOT));
            try {
                Method method = stack.getClass().getMethod("getComponents");
                Object value = method.invoke(stack);
                if (value != null) {
                    builder.append(' ').append(value.toString().toLowerCase(Locale.ROOT));
                }
            } catch (Throwable ignored) {
                // Best effort.
            }
            try {
                Method method = stack.getClass().getMethod("getNbt");
                Object value = method.invoke(stack);
                if (value != null) {
                    builder.append(' ').append(value.toString().toLowerCase(Locale.ROOT));
                }
            } catch (Throwable ignored) {
                // Best effort.
            }
        }
        return builder.toString();
    }

    private List<ItemStack> reflectedEquipment(Entity entity) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Method method : entity.getClass().getMethods()) {
            if (!method.getName().equals("getArmorItems")
                    && !method.getName().equals("getHandItems")
                    && !method.getName().equals("getItemsEquipped")) {
                continue;
            }
            try {
                Object value = method.invoke(entity);
                if (value instanceof Iterable<?> iterable) {
                    for (Object item : iterable) {
                        if (item instanceof ItemStack stack) {
                            stacks.add(stack);
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Best effort.
            }
        }
        return stacks;
    }
}

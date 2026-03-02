package com.jelly.farmhelper.fabric.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlotUtils {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PLOT_NAMES_PATH = FabricLoader.getInstance().getConfigDir().resolve("farmhelper-fabric-plots.json");
    private static final Pattern INFESTED_PLOT_PATTERN = Pattern.compile("plot\\s*(\\d+).*?(\\d+)\\s*pests?", Pattern.CASE_INSENSITIVE);
    private static final Pattern INFESTED_PLOT_ALT_PATTERN = Pattern.compile("plot\\s*(\\d+)\\D+?(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INFESTED_PLOT_REVERSED_PATTERN = Pattern.compile("(\\d+)\\s*pests?\\D+?plot\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern FORMATTING_CODE_PATTERN = Pattern.compile("§.");

    private static final Map<Integer, PlotDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<Integer, String> CUSTOM_NAMES = new HashMap<>();

    static {
        register(21, "Plot 21", -15, -10, -15, -10);
        register(13, "Plot 13", -9, -4, -15, -10);
        register(9, "Plot 9", -3, 2, -15, -10);
        register(14, "Plot 14", 3, 8, -15, -10);
        register(22, "Plot 22", 9, 14, -15, -10);
        register(15, "Plot 15", -15, -10, -9, -4);
        register(5, "Plot 5", -9, -4, -9, -4);
        register(1, "Plot 1", -3, 2, -9, -4);
        register(6, "Plot 6", 3, 8, -9, -4);
        register(16, "Plot 16", 9, 14, -9, -4);
        register(10, "Plot 10", -15, -10, -3, 2);
        register(2, "Plot 2", -9, -4, -3, 2);
        register(0, "Barn", -3, 2, -3, 2);
        register(3, "Plot 3", 3, 8, -3, 2);
        register(11, "Plot 11", 9, 14, -3, 2);
        register(17, "Plot 17", -15, -10, 3, 8);
        register(7, "Plot 7", -9, -4, 3, 8);
        register(4, "Plot 4", -3, 2, 3, 8);
        register(8, "Plot 8", 3, 8, 3, 8);
        register(18, "Plot 18", 9, 14, 3, 8);
        register(23, "Plot 23", -15, -10, 9, 14);
        register(19, "Plot 19", -9, -4, 9, 14);
        register(12, "Plot 12", -3, 2, 9, 14);
        register(20, "Plot 20", 3, 8, 9, 14);
        register(24, "Plot 24", 9, 14, 9, 14);
        loadCustomNames();
    }

    private PlotUtils() {
    }

    public static Optional<Integer> getPlotNumber(BlockPos pos) {
        if (pos == null) {
            return Optional.empty();
        }
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        for (PlotDefinition definition : DEFINITIONS.values()) {
            if (definition.containsChunk(chunkX, chunkZ)) {
                return Optional.of(definition.number());
            }
        }
        return Optional.empty();
    }

    public static String getPlotName(int plotNumber) {
        if (CUSTOM_NAMES.containsKey(plotNumber)) {
            return CUSTOM_NAMES.get(plotNumber);
        }
        PlotDefinition definition = DEFINITIONS.get(plotNumber);
        return definition == null ? ("Plot " + plotNumber) : definition.defaultName();
    }

    public static void setPlotName(int plotNumber, String name) {
        if (!DEFINITIONS.containsKey(plotNumber)) {
            return;
        }
        if (name == null || name.isBlank()) {
            CUSTOM_NAMES.remove(plotNumber);
        } else {
            CUSTOM_NAMES.put(plotNumber, name);
        }
        saveCustomNames();
    }

    public static BlockPos getPlotCenter(int plotNumber) {
        PlotDefinition definition = DEFINITIONS.get(plotNumber);
        if (definition == null) {
            return BlockPos.ORIGIN;
        }
        int centerChunkX = (definition.minChunkX() + definition.maxChunkX()) / 2;
        int centerChunkZ = (definition.minChunkZ() + definition.maxChunkZ()) / 2;
        int centerX = centerChunkX * 16 + 8;
        int centerZ = centerChunkZ * 16 + 8;
        return new BlockPos(centerX, 80, centerZ);
    }

    public static List<InfestedPlot> parseInfestedPlots(List<String> tablist) {
        Map<Integer, InfestedPlot> byPlot = new HashMap<>();
        if (tablist == null) {
            return List.of();
        }
        for (String entry : tablist) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String cleaned = FORMATTING_CODE_PATTERN.matcher(entry).replaceAll("").toLowerCase(Locale.ROOT);
            if (!cleaned.contains("plot") || !cleaned.contains("pest")) {
                continue;
            }

            ParsedInfested parsed = parseInfestedLine(cleaned);
            if (parsed == null || parsed.plotNumber < 1 || parsed.plotNumber > 24 || parsed.pestCount <= 0) {
                continue;
            }

            InfestedPlot existing = byPlot.get(parsed.plotNumber);
            if (existing == null || parsed.pestCount > existing.pestCount()) {
                byPlot.put(parsed.plotNumber, new InfestedPlot(parsed.plotNumber, parsed.pestCount, entry));
            }
        }

        List<InfestedPlot> plots = new ArrayList<>(byPlot.values());
        plots.sort(Comparator.comparingInt(InfestedPlot::pestCount).reversed());
        return plots;
    }

    private static ParsedInfested parseInfestedLine(String cleaned) {
        Matcher primary = INFESTED_PLOT_PATTERN.matcher(cleaned);
        if (primary.find()) {
            try {
                return new ParsedInfested(
                        Integer.parseInt(primary.group(1)),
                        Integer.parseInt(primary.group(2))
                );
            } catch (NumberFormatException ignored) {
            }
        }

        Matcher alternative = INFESTED_PLOT_ALT_PATTERN.matcher(cleaned);
        if (alternative.find()) {
            try {
                return new ParsedInfested(
                        Integer.parseInt(alternative.group(1)),
                        Integer.parseInt(alternative.group(2))
                );
            } catch (NumberFormatException ignored) {
            }
        }

        Matcher reversed = INFESTED_PLOT_REVERSED_PATTERN.matcher(cleaned);
        if (reversed.find()) {
            try {
                return new ParsedInfested(
                        Integer.parseInt(reversed.group(2)),
                        Integer.parseInt(reversed.group(1))
                );
            } catch (NumberFormatException ignored) {
            }
        }

        List<Integer> numbers = new ArrayList<>();
        Matcher numberMatcher = NUMBER_PATTERN.matcher(cleaned);
        while (numberMatcher.find()) {
            try {
                numbers.add(Integer.parseInt(numberMatcher.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
        if (numbers.size() >= 2) {
            int plot = numbers.get(0);
            int pests = numbers.get(1);
            return new ParsedInfested(plot, pests);
        }

        return null;
    }

    public static Optional<InfestedPlot> mostInfestedPlot(List<String> tablist) {
        return parseInfestedPlots(tablist).stream().findFirst();
    }

    private static void register(int number, String defaultName, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        DEFINITIONS.put(number, new PlotDefinition(number, defaultName, minChunkX, maxChunkX, minChunkZ, maxChunkZ));
    }

    private static void loadCustomNames() {
        if (!Files.exists(PLOT_NAMES_PATH)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(PLOT_NAMES_PATH)) {
            JsonObject object = GSON.fromJson(reader, JsonObject.class);
            if (object == null) {
                return;
            }
            for (Map.Entry<String, com.google.gson.JsonElement> entry : object.entrySet()) {
                try {
                    int plot = Integer.parseInt(entry.getKey());
                    CUSTOM_NAMES.put(plot, entry.getValue().getAsString());
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void saveCustomNames() {
        try {
            Files.createDirectories(PLOT_NAMES_PATH.getParent());
            JsonObject object = new JsonObject();
            for (Map.Entry<Integer, String> entry : CUSTOM_NAMES.entrySet()) {
                object.addProperty(String.valueOf(entry.getKey()), entry.getValue());
            }
            try (Writer writer = Files.newBufferedWriter(PLOT_NAMES_PATH)) {
                GSON.toJson(object, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public record PlotDefinition(
            int number,
            String defaultName,
            int minChunkX,
            int maxChunkX,
            int minChunkZ,
            int maxChunkZ
    ) {
        public boolean containsChunk(int chunkX, int chunkZ) {
            return chunkX >= Math.min(minChunkX, maxChunkX)
                    && chunkX <= Math.max(minChunkX, maxChunkX)
                    && chunkZ >= Math.min(minChunkZ, maxChunkZ)
                    && chunkZ <= Math.max(minChunkZ, maxChunkZ);
        }
    }

    public record InfestedPlot(int plotNumber, int pestCount, String sourceLine) {
    }

    private static final class ParsedInfested {
        private final int plotNumber;
        private final int pestCount;

        private ParsedInfested(int plotNumber, int pestCount) {
            this.plotNumber = plotNumber;
            this.pestCount = pestCount;
        }
    }
}

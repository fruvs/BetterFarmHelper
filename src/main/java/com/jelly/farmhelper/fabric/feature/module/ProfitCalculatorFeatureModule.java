package com.jelly.farmhelper.fabric.feature.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;
import com.jelly.farmhelper.fabric.macro.MacroState;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProfitCalculatorFeatureModule extends AbstractFeatureModule {
    private static final Pattern DICER_DROP_PATTERN = Pattern.compile("dicer dropped\\s+(\\d+)x\\s+([\\w\\s]+)!", Pattern.CASE_INSENSITIVE);
    private static final Pattern PEST_DROP_PATTERN = Pattern.compile("you received\\s+(\\d+)x\\s+enchanted\\s+(.+?)\\s+for killing", Pattern.CASE_INSENSITIVE);
    private static final Pattern COINS_PATTERN = Pattern.compile("([0-9][0-9,]*)\\s+coins");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9\\s]");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    private static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final long BAZAAR_REFRESH_MS = 5L * 60L * 1000L;
    private static final long BAZAAR_RETRY_BACKOFF_MS = 45L * 1000L;

    private static final Map<String, Double> FALLBACK_ITEM_VALUES = Map.ofEntries(
            Map.entry("hay bale", 1680d),
            Map.entry("seeds", 36d),
            Map.entry("carrot", 768d),
            Map.entry("potato", 768d),
            Map.entry("melon", 5120d),
            Map.entry("pumpkin", 1024d),
            Map.entry("sugar cane", 5120d),
            Map.entry("cocoa beans", 640d),
            Map.entry("nether wart", 6400d),
            Map.entry("cactus green", 3072d),
            Map.entry("red mushroom", 640d),
            Map.entry("brown mushroom", 640d),
            Map.entry("cropie", 25000d),
            Map.entry("squash", 75000d),
            Map.entry("fermento", 250000d),
            Map.entry("burrowing spores", 1200d),
            Map.entry("helianthus", 275000d),
            Map.entry("moonflower", 640d),
            Map.entry("sunflower", 640d),
            Map.entry("rose", 640d),
            Map.entry("enchanted compost", 30000d)
    );

    private static final Map<String, String> BAZAAR_PRODUCT_IDS = Map.ofEntries(
            Map.entry("hay bale", "ENCHANTED_HAY_BALE"),
            Map.entry("seeds", "ENCHANTED_SEEDS"),
            Map.entry("carrot", "ENCHANTED_CARROT"),
            Map.entry("potato", "ENCHANTED_POTATO"),
            Map.entry("melon", "ENCHANTED_MELON_BLOCK"),
            Map.entry("pumpkin", "ENCHANTED_PUMPKIN"),
            Map.entry("sugar cane", "ENCHANTED_SUGAR_CANE"),
            Map.entry("cocoa beans", "ENCHANTED_COCOA"),
            Map.entry("nether wart", "MUTANT_NETHER_STALK"),
            Map.entry("cactus green", "ENCHANTED_CACTUS"),
            Map.entry("red mushroom", "ENCHANTED_RED_MUSHROOM"),
            Map.entry("brown mushroom", "ENCHANTED_BROWN_MUSHROOM"),
            Map.entry("cropie", "CROPIE"),
            Map.entry("squash", "SQUASH"),
            Map.entry("fermento", "FERMENTO"),
            Map.entry("burrowing spores", "BURROWING_SPORES"),
            Map.entry("helianthus", "HELIANTHUS"),
            Map.entry("moonflower", "ENCHANTED_MOONFLOWER"),
            Map.entry("sunflower", "ENCHANTED_SUNFLOWER"),
            Map.entry("rose", "ENCHANTED_WILD_ROSE"),
            Map.entry("enchanted compost", "ENCHANTED_COMPOST")
    );

    private static final Map<String, String> DROP_NAME_ALIASES = Map.ofEntries(
            Map.entry("ench hay bale", "hay bale"),
            Map.entry("enchanted hay bale", "hay bale"),
            Map.entry("hay bales", "hay bale"),
            Map.entry("ench seeds", "seeds"),
            Map.entry("enchanted seeds", "seeds"),
            Map.entry("ench carrots", "carrot"),
            Map.entry("enchanted carrots", "carrot"),
            Map.entry("ench potato", "potato"),
            Map.entry("enchanted potato", "potato"),
            Map.entry("enchanted melon block", "melon"),
            Map.entry("melon block", "melon"),
            Map.entry("polished melon", "melon"),
            Map.entry("enchanted melon", "melon"),
            Map.entry("enchanted pumpkin", "pumpkin"),
            Map.entry("polished pumpkin", "pumpkin"),
            Map.entry("enchanted sugar cane", "sugar cane"),
            Map.entry("enchanted cocoa", "cocoa beans"),
            Map.entry("enchanted cocoa beans", "cocoa beans"),
            Map.entry("enchanted nether wart", "nether wart"),
            Map.entry("mutant nether stalk", "nether wart"),
            Map.entry("mutant nether wart", "nether wart"),
            Map.entry("enchanted cactus", "cactus green"),
            Map.entry("enchanted red mushroom", "red mushroom"),
            Map.entry("enchanted brown mushroom", "brown mushroom"),
            Map.entry("enchanted compost", "enchanted compost"),
            Map.entry("burrowing spore", "burrowing spores")
    );

    private static final HttpClient BAZAAR_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ExecutorService BAZAAR_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "farmhelper-profit-bazaar");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<String, Long> TRACKED_COUNTS = new ConcurrentHashMap<>();
    private static final Map<String, Double> LIVE_ITEM_VALUES = new ConcurrentHashMap<>();
    private static volatile double totalProfitCoins;
    private static volatile double hourlyProfitCoins;
    private static volatile long lastBazaarAttemptAtMs;
    private static volatile long lastBazaarSuccessAtMs;
    private static volatile boolean bazaarRefreshInFlight;
    private static volatile String bazaarPricingMode = "fallback";

    private boolean seenMacroSession;

    public ProfitCalculatorFeatureModule(boolean enabled) {
        super("profit_calculator", "Profit Calculator", enabled);
    }

    @Override
    public void onDisable() {
        if (FarmHelperFabric.getConfigManager().getConfig().resetStatsBetweenDisabling) {
            reset();
        }
    }

    @Override
    public void onDisconnect() {
        if (FarmHelperFabric.getConfigManager().getConfig().resetStatsBetweenDisabling) {
            reset();
        }
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.profitCalculatorEnabled) {
            return;
        }
        maybeRefreshBazaarValues();

        if (!runtime.macroToggled || runtime.macroState != MacroState.FARMING) {
            if (seenMacroSession && config.resetStatsBetweenDisabling) {
                reset();
                seenMacroSession = false;
            }
            return;
        }

        seenMacroSession = true;
        long runtimeTicks = Math.max(1L, runtime.macroRuntimeTicks);
        double runtimeHours = runtimeTicks / 20.0 / 3600.0;
        if (runtimeHours > 0.0) {
            hourlyProfitCoins = totalProfitCoins / runtimeHours;
        }
    }

    @Override
    public void onChatMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        String normalized = stripFormatting(message).toLowerCase(Locale.ROOT);
        if (normalized.contains(":")) {
            return;
        }

        Matcher dicer = DICER_DROP_PATTERN.matcher(normalized);
        if (dicer.find()) {
            long amount = parseLongSafe(dicer.group(1), 0);
            addDrop(normalizeDropName(dicer.group(2)), amount);
            return;
        }

        Matcher pestDrop = PEST_DROP_PATTERN.matcher(normalized);
        if (pestDrop.find()) {
            long amount = parseLongSafe(pestDrop.group(1), 0);
            addDrop(normalizeDropName(pestDrop.group(2)), amount);
            return;
        }

        if (isSaleLine(normalized)) {
            // Item value is already tracked at drop time; a sale event would double-count.
            return;
        }

        if (isBuyLine(normalized)) {
            Matcher coinsMatcher = COINS_PATTERN.matcher(normalized);
            if (coinsMatcher.find()) {
                totalProfitCoins -= parseLongSafe(coinsMatcher.group(1), 0);
            }
            return;
        }

        if (!normalized.contains("coins")) {
            return;
        }
        if (!(normalized.contains("bountiful")
                || normalized.contains("you earned")
                || normalized.contains("you gained")
                || normalized.contains("you found"))) {
            return;
        }
        Matcher coinsMatcher = COINS_PATTERN.matcher(normalized);
        if (coinsMatcher.find()) {
            totalProfitCoins += parseLongSafe(coinsMatcher.group(1), 0);
        }
    }

    public static double getTotalProfitCoins() {
        return totalProfitCoins;
    }

    public static double getHourlyProfitCoins() {
        return hourlyProfitCoins;
    }

    public static Map<String, Long> getTrackedCounts() {
        return Map.copyOf(TRACKED_COUNTS);
    }

    public static String getBazaarPricingMode() {
        return bazaarPricingMode;
    }

    public static double estimateUnitPriceCoins(String itemName) {
        String key = canonicalizeItemName(itemName);
        if (key.isBlank()) {
            return 0d;
        }
        Double live = LIVE_ITEM_VALUES.get(key);
        if (live != null && live > 0d) {
            return live;
        }
        return Math.max(0d, FALLBACK_ITEM_VALUES.getOrDefault(key, 0d));
    }

    private void addDrop(String canonicalName, long amount) {
        if (amount <= 0) {
            return;
        }
        if (canonicalName == null || canonicalName.isBlank()) {
            return;
        }
        String name = canonicalName.trim().toLowerCase(Locale.ROOT);
        TRACKED_COUNTS.merge(name, amount, Long::sum);
        double value = LIVE_ITEM_VALUES.getOrDefault(name, FALLBACK_ITEM_VALUES.getOrDefault(name, 0d));
        totalProfitCoins += value * amount;
    }

    private void maybeRefreshBazaarValues() {
        long now = System.currentTimeMillis();
        if (bazaarRefreshInFlight) {
            return;
        }
        long interval = lastBazaarSuccessAtMs > 0 ? BAZAAR_REFRESH_MS : BAZAAR_RETRY_BACKOFF_MS;
        if (now - lastBazaarAttemptAtMs < interval) {
            return;
        }
        bazaarRefreshInFlight = true;
        lastBazaarAttemptAtMs = now;
        BAZAAR_EXECUTOR.submit(() -> {
            try {
                Map<String, Double> refreshed = fetchBazaarValues();
                if (!refreshed.isEmpty()) {
                    LIVE_ITEM_VALUES.clear();
                    LIVE_ITEM_VALUES.putAll(refreshed);
                    lastBazaarSuccessAtMs = System.currentTimeMillis();
                    bazaarPricingMode = "live";
                } else if (LIVE_ITEM_VALUES.isEmpty()) {
                    bazaarPricingMode = "fallback";
                }
            } catch (Exception e) {
                if (LIVE_ITEM_VALUES.isEmpty()) {
                    bazaarPricingMode = "fallback";
                }
                FarmHelperFabric.LOGGER.debug("Failed to refresh bazaar prices for profit calculator", e);
            } finally {
                bazaarRefreshInFlight = false;
            }
        });
    }

    private Map<String, Double> fetchBazaarValues() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BAZAAR_URL))
                .header("User-Agent", "FarmHelperFabric/1.0")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = BAZAAR_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("bazaar status " + response.statusCode());
        }

        JsonElement rootElement = JsonParser.parseString(response.body());
        if (!rootElement.isJsonObject()) {
            throw new IOException("bazaar response was not json object");
        }
        JsonObject root = rootElement.getAsJsonObject();
        if (!root.has("success") || !root.get("success").getAsBoolean()) {
            throw new IOException("bazaar response success=false");
        }

        JsonObject products = root.has("products") && root.get("products").isJsonObject()
                ? root.getAsJsonObject("products")
                : null;
        if (products == null) {
            throw new IOException("bazaar products missing");
        }

        Map<String, Double> refreshed = new HashMap<>();
        for (Map.Entry<String, String> entry : BAZAAR_PRODUCT_IDS.entrySet()) {
            double price = extractPrice(products, entry.getValue());
            if (price > 0d) {
                refreshed.put(entry.getKey(), price);
            }
        }
        return refreshed;
    }

    private double extractPrice(JsonObject products, String productId) {
        if (products == null || productId == null || !products.has(productId) || !products.get(productId).isJsonObject()) {
            return -1d;
        }
        JsonObject product = products.getAsJsonObject(productId);
        if (product.has("quick_status") && product.get("quick_status").isJsonObject()) {
            JsonObject quick = product.getAsJsonObject("quick_status");
            if (quick.has("sellPrice")) {
                double sellPrice = quick.get("sellPrice").getAsDouble();
                if (sellPrice > 0d) {
                    return sellPrice;
                }
            }
            if (quick.has("buyPrice")) {
                double buyPrice = quick.get("buyPrice").getAsDouble();
                if (buyPrice > 0d) {
                    return buyPrice;
                }
            }
        }

        if (product.has("sell_summary") && product.get("sell_summary").isJsonArray()) {
            JsonArray sellSummary = product.getAsJsonArray("sell_summary");
            if (!sellSummary.isEmpty() && sellSummary.get(0).isJsonObject()) {
                JsonObject first = sellSummary.get(0).getAsJsonObject();
                if (first.has("pricePerUnit")) {
                    double summaryPrice = first.get("pricePerUnit").getAsDouble();
                    if (summaryPrice > 0d) {
                        return summaryPrice;
                    }
                }
            }
        }
        return -1d;
    }

    private boolean isSaleLine(String normalizedLine) {
        return normalizedLine.contains("[bazaar] sold")
                || normalizedLine.contains("coins from selling")
                || normalizedLine.contains("you sold");
    }

    private boolean isBuyLine(String normalizedLine) {
        return normalizedLine.contains("[bazaar] bought")
                || normalizedLine.contains("you bought")
                || normalizedLine.contains("spent")
                || normalizedLine.contains("for buying");
    }

    private String normalizeDropName(String rawName) {
        return canonicalizeItemName(rawName);
    }

    private static String canonicalizeItemName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "";
        }
        String normalized = stripFormatting(rawName).toLowerCase(Locale.ROOT);
        normalized = NON_ALPHANUMERIC.matcher(normalized).replaceAll(" ");
        normalized = MULTI_SPACE.matcher(normalized).replaceAll(" ").trim();
        if (normalized.startsWith("an ")) {
            normalized = normalized.substring(3).trim();
        } else if (normalized.startsWith("a ")) {
            normalized = normalized.substring(2).trim();
        }
        String alias = DROP_NAME_ALIASES.get(normalized);
        if (alias != null) {
            return alias;
        }
        if (normalized.startsWith("enchanted ")) {
            alias = DROP_NAME_ALIASES.get(normalized.substring("enchanted ".length()).trim());
            if (alias != null) {
                return alias;
            }
        }
        return normalized;
    }

    private static long parseLongSafe(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.replace(",", "").trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String stripFormatting(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("§.", "");
    }

    private void reset() {
        totalProfitCoins = 0;
        hourlyProfitCoins = 0;
        TRACKED_COUNTS.clear();
        seenMacroSession = false;
    }
}

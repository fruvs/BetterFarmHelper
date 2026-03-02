package com.jelly.farmhelper.fabric.util;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.event.UpdateTablistEvent;
import com.jelly.farmhelper.fabric.event.UpdateTablistFooterEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TablistUtils {
    private static final CopyOnWriteArrayList<String> CACHED_TABLIST = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<String> CACHED_FOOTER = new CopyOnWriteArrayList<>();

    private TablistUtils() {
    }

    public static List<String> getTabList() {
        return List.copyOf(CACHED_TABLIST);
    }

    public static List<String> getFooterLines() {
        return List.copyOf(CACHED_FOOTER);
    }

    public static void tick(MinecraftClient client) {
        if (client == null || client.getNetworkHandler() == null) {
            clear();
            return;
        }

        List<String> next = new ArrayList<>();
        List<PlayerListEntry> entries = new ArrayList<>(client.getNetworkHandler().getListedPlayerListEntries());
        entries.sort(Comparator.comparing(entry -> entry.getProfile().name().toLowerCase(Locale.ROOT)));
        for (PlayerListEntry entry : entries) {
            Text display = entry.getDisplayName();
            String value = display == null ? entry.getProfile().name() : display.getString();
            if (value != null && !value.isBlank()) {
                next.add(value);
            }
        }
        updateTabList(next);
    }

    public static void setFooterLines(List<String> lines) {
        updateFooter(lines == null ? List.of() : lines);
    }

    private static void updateTabList(List<String> next) {
        if (CACHED_TABLIST.equals(next)) {
            return;
        }
        CACHED_TABLIST.clear();
        CACHED_TABLIST.addAll(next);
        FarmHelperFabric.getEventBus().post(new UpdateTablistEvent(next, System.currentTimeMillis()));
    }

    private static void updateFooter(List<String> nextFooter) {
        if (CACHED_FOOTER.equals(nextFooter)) {
            return;
        }
        CACHED_FOOTER.clear();
        CACHED_FOOTER.addAll(nextFooter);
        FarmHelperFabric.getEventBus().post(new UpdateTablistFooterEvent(nextFooter));
    }

    private static void clear() {
        if (!CACHED_TABLIST.isEmpty()) {
            CACHED_TABLIST.clear();
            FarmHelperFabric.getEventBus().post(new UpdateTablistEvent(List.of(), System.currentTimeMillis()));
        }
        if (!CACHED_FOOTER.isEmpty()) {
            CACHED_FOOTER.clear();
            FarmHelperFabric.getEventBus().post(new UpdateTablistFooterEvent(List.of()));
        }
    }
}

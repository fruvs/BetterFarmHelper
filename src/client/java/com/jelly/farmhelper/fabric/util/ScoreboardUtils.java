package com.jelly.farmhelper.fabric.util;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.event.UpdateScoreboardLineEvent;
import com.jelly.farmhelper.fabric.event.UpdateScoreboardListEvent;
import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ScoreboardUtils {
    private static final CopyOnWriteArrayList<String> CACHED_LINES = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<String> CACHED_CLEAN_LINES = new CopyOnWriteArrayList<>();

    private ScoreboardUtils() {
    }

    public static List<String> getScoreboardLines(boolean clean) {
        return clean ? List.copyOf(CACHED_CLEAN_LINES) : List.copyOf(CACHED_LINES);
    }

    public static void tick(MinecraftClient client) {
        if (client == null || client.world == null) {
            clear();
            return;
        }

        List<String> raw = readLinesReflective(client.world.getScoreboard());
        List<String> clean = raw.stream().map(ScoreboardUtils::cleanLine).toList();
        if (CACHED_CLEAN_LINES.equals(clean)) {
            return;
        }

        CACHED_LINES.clear();
        CACHED_LINES.addAll(raw);
        CACHED_CLEAN_LINES.clear();
        CACHED_CLEAN_LINES.addAll(clean);
        long timestamp = System.currentTimeMillis();
        FarmHelperFabric.getEventBus().post(new UpdateScoreboardListEvent(raw, clean, timestamp));
        for (String line : clean) {
            FarmHelperFabric.getEventBus().post(new UpdateScoreboardLineEvent(line));
        }
    }

    private static List<String> readLinesReflective(Object scoreboard) {
        if (scoreboard == null) {
            return List.of();
        }
        try {
            Object objective = findSidebarObjective(scoreboard);
            if (objective == null) {
                return List.of();
            }

            Collection<?> entries = findEntries(scoreboard, objective);
            if (entries == null || entries.isEmpty()) {
                return List.of();
            }

            List<Object> sorted = new ArrayList<>(entries);
            sorted.sort(Comparator.comparingInt(ScoreboardUtils::extractScoreValue).reversed());

            List<String> lines = new ArrayList<>();
            int start = Math.max(0, sorted.size() - 15);
            for (int i = start; i < sorted.size(); i++) {
                String line = extractLineText(scoreboard, sorted.get(i));
                if (line != null && !line.isBlank()) {
                    lines.add(line);
                }
            }
            return lines;
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static Object findSidebarObjective(Object scoreboard) throws Exception {
        Class<?> scoreboardClass = scoreboard.getClass();

        try {
            Class<?> displaySlotClass = Class.forName("net.minecraft.scoreboard.ScoreboardDisplaySlot");
            Object sidebar = Enum.valueOf((Class<Enum>) displaySlotClass.asSubclass(Enum.class), "SIDEBAR");
            Method getObjectiveForSlot = scoreboardClass.getMethod("getObjectiveForSlot", displaySlotClass);
            return getObjectiveForSlot.invoke(scoreboard, sidebar);
        } catch (Throwable ignored) {
        }

        try {
            Method legacy = scoreboardClass.getMethod("getObjectiveInDisplaySlot", int.class);
            return legacy.invoke(scoreboard, 1);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Collection<?> findEntries(Object scoreboard, Object objective) {
        for (Method method : scoreboard.getClass().getMethods()) {
            if (!Collection.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || !params[0].isAssignableFrom(objective.getClass())) {
                continue;
            }
            if (!method.getName().toLowerCase().contains("score")) {
                continue;
            }
            try {
                Object value = method.invoke(scoreboard, objective);
                if (value instanceof Collection<?> collection) {
                    return collection;
                }
            } catch (Throwable ignored) {
            }
        }
        return List.of();
    }

    private static int extractScoreValue(Object entry) {
        if (entry == null) {
            return 0;
        }
        for (Method method : entry.getClass().getMethods()) {
            if (method.getParameterCount() == 0
                    && method.getReturnType() == int.class
                    && (method.getName().toLowerCase().contains("score")
                    || method.getName().equals("value"))) {
                try {
                    return (int) method.invoke(entry);
                } catch (Throwable ignored) {
                }
            }
        }
        for (Field field : entry.getClass().getDeclaredFields()) {
            if (field.getType() == int.class) {
                try {
                    field.setAccessible(true);
                    return field.getInt(entry);
                } catch (Throwable ignored) {
                }
            }
        }
        return 0;
    }

    private static String extractLineText(Object scoreboard, Object entry) {
        if (entry == null) {
            return null;
        }

        Object owner = null;
        for (Method method : entry.getClass().getMethods()) {
            if (method.getParameterCount() == 0
                    && (method.getName().equals("owner")
                    || method.getName().toLowerCase().contains("holder")
                    || method.getName().toLowerCase().contains("player"))) {
                try {
                    owner = method.invoke(entry);
                    if (owner != null) {
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        if (owner == null) {
            return null;
        }

        String rawName = Objects.toString(owner, "");
        try {
            Method getNameForScoreboard = owner.getClass().getMethod("getNameForScoreboard");
            rawName = Objects.toString(getNameForScoreboard.invoke(owner), rawName);
        } catch (Throwable ignored) {
        }

        String teamPrefix = "";
        String teamSuffix = "";
        try {
            Method getScoreHolderTeam = scoreboard.getClass().getMethod("getScoreHolderTeam", String.class);
            Object team = getScoreHolderTeam.invoke(scoreboard, rawName);
            if (team != null) {
                for (Method method : team.getClass().getMethods()) {
                    if (method.getParameterCount() != 0 || method.getReturnType() == void.class) {
                        continue;
                    }
                    String name = method.getName().toLowerCase();
                    if (name.contains("prefix")) {
                        teamPrefix = Objects.toString(method.invoke(team), teamPrefix);
                    } else if (name.contains("suffix")) {
                        teamSuffix = Objects.toString(method.invoke(team), teamSuffix);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return teamPrefix + rawName + teamSuffix;
    }

    private static String cleanLine(String line) {
        if (line == null) {
            return "";
        }
        String withoutColor = line.replaceAll("§.", "");
        StringBuilder cleaned = new StringBuilder(withoutColor.length());
        for (char c : withoutColor.toCharArray()) {
            if ((c >= 32 && c < 127) || c == '\u0d60') {
                cleaned.append(c);
            }
        }
        return cleaned.toString();
    }

    private static void clear() {
        if (CACHED_LINES.isEmpty() && CACHED_CLEAN_LINES.isEmpty()) {
            return;
        }
        CACHED_LINES.clear();
        CACHED_CLEAN_LINES.clear();
        FarmHelperFabric.getEventBus().post(new UpdateScoreboardListEvent(List.of(), List.of(), System.currentTimeMillis()));
    }
}

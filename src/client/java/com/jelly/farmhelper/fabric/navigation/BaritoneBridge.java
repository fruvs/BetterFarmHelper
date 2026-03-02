package com.jelly.farmhelper.fabric.navigation;

import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class BaritoneBridge {
    private static boolean pathingRequested;

    private BaritoneBridge() {
    }

    public static boolean isAvailable() {
        try {
            Class.forName("baritone.api.BaritoneAPI");
            Class.forName("baritone.api.pathing.goals.GoalBlock");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean walkTo(BlockPos pos) {
        return setGoal(pos, false, 0);
    }

    public static boolean walkNear(BlockPos pos, int radius) {
        return setGoal(pos, true, Math.max(1, radius));
    }

    public static boolean cancel() {
        Object behavior = pathingBehavior();
        if (behavior == null) {
            pathingRequested = false;
            return false;
        }
        try {
            Method cancel = behavior.getClass().getMethod("cancelEverything");
            cancel.invoke(behavior);
            pathingRequested = false;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isPathing() {
        if (!pathingRequested) {
            return false;
        }
        Object behavior = pathingBehavior();
        if (behavior == null) {
            pathingRequested = false;
            return false;
        }
        try {
            Method getPath = behavior.getClass().getMethod("getCurrent");
            Object path = getPath.invoke(behavior);
            if (path == null) {
                pathingRequested = false;
                return false;
            }
            return true;
        } catch (Throwable ignored) {
            return pathingRequested;
        }
    }

    private static boolean setGoal(BlockPos pos, boolean near, int radius) {
        if (pos == null || !isAvailable()) {
            return false;
        }
        Object behavior = pathingBehavior();
        if (behavior == null) {
            return false;
        }
        try {
            Object goal;
            if (near) {
                Class<?> goalNearClass = Class.forName("baritone.api.pathing.goals.GoalNear");
                Constructor<?> ctor = goalNearClass.getConstructor(BlockPos.class, int.class);
                goal = ctor.newInstance(pos, radius);
            } else {
                Class<?> goalBlockClass = Class.forName("baritone.api.pathing.goals.GoalBlock");
                Constructor<?> ctor = goalBlockClass.getConstructor(BlockPos.class);
                goal = ctor.newInstance(pos);
            }

            Method setGoal = behavior.getClass().getMethod("setGoalAndPath", Class.forName("baritone.api.pathing.goals.Goal"));
            setGoal.invoke(behavior, goal);
            pathingRequested = true;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object pathingBehavior() {
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Method getProvider = apiClass.getMethod("getProvider");
            Object provider = getProvider.invoke(null);
            Method getPrimary = provider.getClass().getMethod("getPrimaryBaritone");
            Object primary = getPrimary.invoke(provider);
            Method getPathingBehavior = primary.getClass().getMethod("getPathingBehavior");
            return getPathingBehavior.invoke(primary);
        } catch (Throwable ignored) {
            return null;
        }
    }
}

package com.jelly.farmhelper.fabric.state;

public final class RuntimeGuards {
    private static volatile boolean globalStopLatched;

    private RuntimeGuards() {
    }

    public static void latchGlobalStop() {
        globalStopLatched = true;
    }

    public static void clearGlobalStopLatch() {
        globalStopLatched = false;
    }

    public static boolean isGlobalStopLatched() {
        return globalStopLatched;
    }
}

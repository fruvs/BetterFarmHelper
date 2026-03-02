package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

public class AutoReconnectFeatureModule extends AbstractFeatureModule {
    public record ReconnectRequest(long readyAtTick, int attempt, long createdAtTick) {
    }

    private static volatile ReconnectRequest pendingReconnectRequest;
    private static volatile int nextAttempt = 1;
    private static volatile long requestedAtTick = -1L;
    private static volatile long deferUntilTick = -1L;
    private static volatile long reconnectRequestedTickGlobal = -1L;
    private static volatile int maxAttemptsSnapshot = 0;

    private long reconnectRequestedTick = -1L;

    public AutoReconnectFeatureModule(boolean enabled) {
        super("auto_reconnect", "Auto Reconnect", enabled);
    }

    @Override
    public void onDisconnect() {
        reconnectRequestedTick = System.currentTimeMillis() / 50L;
        requestedAtTick = reconnectRequestedTick;
        reconnectRequestedTickGlobal = reconnectRequestedTick;
        nextAttempt = 1;
        pendingReconnectRequest = null;
        deferUntilTick = -1L;
        maxAttemptsSnapshot = Math.max(1, FarmHelperFabric.getConfigManager().getConfig().autoReconnectMaxAttempts);
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        if (!FarmHelperFabric.getConfigManager().getConfig().autoReconnect) {
            return;
        }
        if (reconnectRequestedTick <= 0L) {
            return;
        }
        if (runtime.inWorld) {
            reconnectRequestedTick = -1L;
            reconnectRequestedTickGlobal = -1L;
            pendingReconnectRequest = null;
            return;
        }

        long nowTick = runtime.tickCount > 0 ? runtime.tickCount : (System.currentTimeMillis() / 50L);
        long now = System.currentTimeMillis() / 50L;
        long reconnectDelayTicks = 20L * Math.max(1, FarmHelperFabric.getConfigManager().getConfig().autoReconnectDelaySeconds);
        int maxAttempts = Math.max(1, FarmHelperFabric.getConfigManager().getConfig().autoReconnectMaxAttempts);
        maxAttemptsSnapshot = maxAttempts;

        if (deferUntilTick > 0 && nowTick < deferUntilTick) {
            return;
        }
        if (pendingReconnectRequest != null) {
            return;
        }
        if (nextAttempt > maxAttempts) {
            reconnectRequestedTick = -1L;
            reconnectRequestedTickGlobal = -1L;
            return;
        }
        if ((now - reconnectRequestedTick) >= reconnectDelayTicks) {
            pendingReconnectRequest = new ReconnectRequest(nowTick, nextAttempt, requestedAtTick);
            nextAttempt++;
            reconnectRequestedTick = now;
        }
    }

    public static ReconnectRequest pollReadyReconnectAttempt(long nowTick) {
        ReconnectRequest request = pendingReconnectRequest;
        if (request == null) {
            return null;
        }
        if (nowTick < request.readyAtTick()) {
            return null;
        }
        pendingReconnectRequest = null;
        return request;
    }

    public static void deferReconnectAttempt(long nextReadyTick) {
        deferUntilTick = Math.max(deferUntilTick, nextReadyTick);
        ReconnectRequest pending = pendingReconnectRequest;
        if (pending != null) {
            pendingReconnectRequest = new ReconnectRequest(
                    Math.max(nextReadyTick, pending.readyAtTick()),
                    pending.attempt(),
                    pending.createdAtTick()
            );
        }
    }

    public static boolean hasPendingReconnect() {
        return reconnectRequestedTickGlobal > 0L || pendingReconnectRequest != null;
    }

    public static long getRemainingReconnectTicks(long nowTick) {
        ReconnectRequest pending = pendingReconnectRequest;
        if (pending != null) {
            return Math.max(0L, pending.readyAtTick() - nowTick);
        }
        if (deferUntilTick > 0L) {
            return Math.max(0L, deferUntilTick - nowTick);
        }
        return 0L;
    }

    public static int getCurrentAttempt() {
        return Math.max(1, nextAttempt - 1);
    }

    public static int getMaxAttemptsSnapshot() {
        return Math.max(1, maxAttemptsSnapshot);
    }
}

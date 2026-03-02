package com.jelly.farmhelper.fabric.feature.module;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.feature.FeatureRuntimeState;

import java.util.Locale;

public class AutoCookieFeatureModule extends MacroExclusiveFeatureModule {
    private enum State {
        IDLE,
        TRY_CONSUME,
        WAIT_CONSUME_MENU,
        CONFIRM_CONSUME,
        OPEN_BAZAAR,
        WAIT_BAZAAR,
        BUY_COOKIE,
        CLOSE_BAZAAR,
        FINISH
    }

    private State state = State.IDLE;
    private long stateSinceTick = 0L;
    private boolean cookieExpired;
    private boolean consumeConfirmed;
    private boolean purchaseAttempted;
    private long lastCookieCycleTick = -1L;

    public AutoCookieFeatureModule(boolean enabled) {
        super("auto_cookie", "Auto Cookie", enabled);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        cookieExpired = false;
        resetState();
    }

    @Override
    public void onDisconnect() {
        super.onDisconnect();
        cookieExpired = false;
        resetState();
    }

    @Override
    public void onTick(FeatureRuntimeState runtime) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.autoCookie) {
            resetState();
            return;
        }
        if (runtime.activeFailsafe.isPresent() || !runtime.macroToggled) {
            return;
        }

        if (!isActionRunning()) {
            tryStart(runtime, config);
            return;
        }

        if (shouldEndAction(runtime.tickCount)) {
            endTimedAction("cookie timeout");
            lastCookieCycleTick = runtime.tickCount;
            resetState();
            return;
        }

        tickState(runtime, config);
    }

    private void tryStart(FeatureRuntimeState runtime, FarmHelperConfig config) {
        if (runtime.cookieBuffActive) {
            cookieExpired = false;
            consumeConfirmed = true;
            lastCookieCycleTick = runtime.tickCount;
            return;
        }
        if (lastCookieCycleTick <= 0) {
            lastCookieCycleTick = runtime.tickCount;
        }
        long intervalTicks = secondsToTicks(Math.max(1, config.autoCookieCheckMinutes) * 60);
        boolean periodicCheckDue = runtime.tickCount - lastCookieCycleTick >= intervalTicks;
        if (!cookieExpired && !periodicCheckDue) {
            return;
        }

        if (!beginTimedAction(runtime.tickCount, intervalTicks + 320L, "cookie refresh")) {
            return;
        }

        consumeConfirmed = false;
        purchaseAttempted = false;
        setState(State.TRY_CONSUME, runtime.tickCount);
        FarmHelperFabric.getWebhookService().sendFeatureLog("Auto Cookie cycle started");
    }

    private void tickState(FeatureRuntimeState runtime, FarmHelperConfig config) {
        switch (state) {
            case TRY_CONSUME -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueSelectHotbarItem("booster cookie", runtime.tickCount);
                    queueUseHeldItem(runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 14L) {
                    setState(State.WAIT_CONSUME_MENU, runtime.tickCount);
                }
            }
            case WAIT_CONSUME_MENU -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueWaitForScreen("consume booster cookie", 100L, 2, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 22L) {
                    setState(State.CONFIRM_CONSUME, runtime.tickCount);
                }
            }
            case CONFIRM_CONSUME -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueClickSlotMatching("name:consume cookie;lore:booster", 80L, 3, runtime.tickCount);
                    queueClickSlotMatching("name:consume", 60L, 2, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 24L) {
                    if (consumeConfirmed) {
                        setState(State.FINISH, runtime.tickCount);
                    } else if (config.autoCookieAutoBuy && !purchaseAttempted) {
                        purchaseAttempted = true;
                        setState(State.OPEN_BAZAAR, runtime.tickCount);
                    } else {
                        setState(State.FINISH, runtime.tickCount);
                    }
                }
            }
            case OPEN_BAZAAR -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueCommand("/bz booster cookie", runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 24L) {
                    setState(State.WAIT_BAZAAR, runtime.tickCount);
                }
            }
            case WAIT_BAZAAR -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueWaitForScreen("bazaar", 160L, 4, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 30L) {
                    setState(State.BUY_COOKIE, runtime.tickCount);
                }
            }
            case BUY_COOKIE -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueClickSlotMatching("name:booster cookie", 80L, 6, runtime.tickCount);
                    queueClickSlotMatching("name:buy instantly;lore:cookie", 80L, 6, runtime.tickCount);
                    queueClickSlotMatching("name:buy only one", 80L, 6, runtime.tickCount);
                    queueClickSlotMatching("name:buy;lore:coins", 80L, 4, runtime.tickCount);
                }
                if (ticksInState(runtime.tickCount) >= 36L) {
                    setState(State.CLOSE_BAZAAR, runtime.tickCount);
                }
            }
            case CLOSE_BAZAAR -> {
                if (ticksInState(runtime.tickCount) == 0) {
                    queueCloseScreen(runtime.tickCount);
                    if (config.autoCookieReturnToGarden) {
                        queueCommand("/warp garden", runtime.tickCount);
                    }
                }
                if (ticksInState(runtime.tickCount) >= 14L) {
                    setState(State.TRY_CONSUME, runtime.tickCount);
                }
            }
            case FINISH -> {
                lastCookieCycleTick = runtime.tickCount;
                if (consumeConfirmed) {
                    cookieExpired = false;
                }
                endTimedAction("cookie cycle complete");
                resetState();
            }
            case IDLE -> {
            }
        }
    }

    @Override
    public void onChatMessage(String message) {
        FarmHelperConfig config = FarmHelperFabric.getConfigManager().getConfig();
        if (!config.autoCookie || message == null || message.isBlank()) {
            return;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("you consumed a booster cookie")) {
            consumeConfirmed = true;
            cookieExpired = false;
            return;
        }
        if (normalized.contains("cookie")
                && (normalized.contains("not active")
                || normalized.contains("expired")
                || normalized.contains("ran out")
                || normalized.contains("inactive"))) {
            cookieExpired = true;
        }
    }

    private long ticksInState(long nowTick) {
        return Math.max(0L, nowTick - stateSinceTick);
    }

    private void setState(State next, long nowTick) {
        state = next;
        stateSinceTick = nowTick;
    }

    private void resetState() {
        state = State.IDLE;
        stateSinceTick = 0L;
        consumeConfirmed = false;
        purchaseAttempted = false;
    }
}

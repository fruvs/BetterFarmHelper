package com.jelly.farmhelper.fabric.feature;

public interface Feature {
    String id();

    boolean enabled();

    void setEnabled(boolean enabled);

    default void onEnable() {
    }

    default void onDisable() {
    }

    default void onTick(FeatureRuntimeState runtime) {
    }

    default void onChatMessage(String message) {
    }

    default void onDisconnect() {
    }
}

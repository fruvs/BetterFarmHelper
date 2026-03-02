package com.jelly.farmhelper.fabric.event;

public final class InventoryInputEvent {
    public final int keyCode;
    public final char typedChar;

    public InventoryInputEvent(int keyCode, char typedChar) {
        this.keyCode = keyCode;
        this.typedChar = typedChar;
    }
}

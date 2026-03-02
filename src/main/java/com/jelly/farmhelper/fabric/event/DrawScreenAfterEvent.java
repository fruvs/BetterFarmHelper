package com.jelly.farmhelper.fabric.event;

public final class DrawScreenAfterEvent {
    public final String screenClassName;
    public final String screenTitle;

    public DrawScreenAfterEvent(String screenClassName, String screenTitle) {
        this.screenClassName = screenClassName;
        this.screenTitle = screenTitle;
    }
}

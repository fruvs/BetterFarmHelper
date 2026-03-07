package com.jelly.farmhelper.fabric.ui.framework;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Click;

/**
 * Base building block for the custom FarmHelper UI system.
 * Components are laid out and rendered manually rather than using vanilla widgets.
 */
public abstract class UiComponent {
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;
    protected boolean enabled = true;

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /** Called before rendering to allow animations or state updates. */
    public void tick(Screen screen) {
    }

    /** Render this component. Coordinates are already in screen space. */
    public abstract void render(DrawContext context, int mouseX, int mouseY, float delta, UiTheme theme);

    /** Optional overlay pass rendered after normal children for top-layer UI (dropdown lists, tooltips, etc.). */
    public void renderOverlay(DrawContext context, int mouseX, int mouseY, float delta, UiTheme theme) {
    }

    /** True if the mouse is inside this component's bounds. */
    protected boolean isInside(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    // Input routing hooks. Default to no‑op and let containers dispatch as needed.

    public boolean mouseClicked(Click click, boolean dblClick) {
        return false;
    }

    public boolean mouseReleased(Click click) {
        return false;
    }

    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        return false;
    }
}

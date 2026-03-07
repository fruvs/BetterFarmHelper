package com.jelly.farmhelper.fabric.ui.framework;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * Base Screen for all custom FarmHelper UI built on the new framework.
 * It owns a root UiComponent tree, a shared theme, and dispatches input.
 */
public abstract class FHScreen extends Screen {
    protected final Screen parent;
    protected final UiTheme theme = UiTheme.DEFAULT_DARK;
    protected UiComponent rootComponent;

    protected FHScreen(Screen parent, Text title) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void init() {
        buildUi();
    }

    /** Called from init() and when the screen is resized to (re)construct layout. */
    protected abstract void buildUi();

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Blur / darken background
        renderBackground(context, mouseX, mouseY, delta);
        if (rootComponent != null) {
            rootComponent.tick(this);
            rootComponent.render(context, mouseX, mouseY, delta, theme);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean dblClick) {
        if (rootComponent != null && rootComponent.mouseClicked(click, dblClick)) {
            return true;
        }
        return super.mouseClicked(click, dblClick);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (rootComponent != null && rootComponent.mouseReleased(click)) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (rootComponent != null && rootComponent.mouseDragged(click, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (rootComponent != null && rootComponent.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input == null ? -1 : input.getKeycode();
        int scanCode = input == null ? 0 : input.scancode();
        int modifiers = input == null ? 0 : input.modifiers();
        if (rootComponent != null && rootComponent.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        // Escape closes back to parent
        if (keyCode == 256) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        char chr = input == null ? '\0' : (char) input.codepoint();
        int modifiers = input == null ? 0 : input.modifiers();
        if (rootComponent != null && rootComponent.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void close() {
        if (client == null) {
            super.close();
            return;
        }
        client.setScreen(parent);
    }

    protected MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }
}

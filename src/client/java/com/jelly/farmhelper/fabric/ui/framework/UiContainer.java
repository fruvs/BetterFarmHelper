package com.jelly.farmhelper.fabric.ui.framework;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple container that owns and forwards events to child components.
 */
public class UiContainer extends UiComponent {
    protected final List<UiComponent> children = new ArrayList<>();

    public void addChild(UiComponent child) {
        children.add(child);
    }

    public List<UiComponent> getChildren() {
        return children;
    }

    @Override
    public void tick(net.minecraft.client.gui.screen.Screen screen) {
        for (UiComponent child : children) {
            if (child.isVisible()) {
                child.tick(screen);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta, UiTheme theme) {
        if (!visible) return;
        for (UiComponent child : children) {
            if (child.isVisible()) {
                child.render(context, mouseX, mouseY, delta, theme);
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean dblClick) {
        if (!visible) return false;
        for (UiComponent child : children) {
            if (child.isVisible() && child.mouseClicked(click, dblClick)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (!visible) return false;
        for (UiComponent child : children) {
            if (child.isVisible() && child.mouseReleased(click)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (!visible) return false;
        for (UiComponent child : children) {
            if (child.isVisible() && child.mouseDragged(click, deltaX, deltaY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!visible) return false;
        for (UiComponent child : children) {
            if (child.isVisible() && child.mouseScrolled(mouseX, mouseY, amount)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        for (UiComponent child : children) {
            if (child.isVisible() && child.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!visible) return false;
        for (UiComponent child : children) {
            if (child.isVisible() && child.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return false;
    }
}


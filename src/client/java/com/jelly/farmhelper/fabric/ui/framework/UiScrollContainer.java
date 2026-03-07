package com.jelly.farmhelper.fabric.ui.framework;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;

/**
 * Vertical scroll container for large content areas.
 */
public class UiScrollContainer extends UiContainer {
    private int contentHeight;
    private double scrollOffset;

    public void setContentHeight(int contentHeight) {
        this.contentHeight = contentHeight;
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
    }

    private double maxScroll() {
        int visible = height;
        return Math.max(0, contentHeight - visible);
    }

    public void scrollToTop() {
        scrollOffset = 0;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta, UiTheme theme) {
        if (!visible) return;

        // Clip to container bounds
        int x1 = x;
        int y1 = y;
        int x2 = x + width;
        int y2 = y + height;
        context.enableScissor(x1, y1, x2, y2);
        int offsetY = (int) Math.round(scrollOffset);
        for (UiComponent child : children) {
            if (!child.isVisible()) continue;
            int oldX = child.getX();
            int oldY = child.getY();
            int oldWidth = child.getWidth();
            int oldHeight = child.getHeight();

            child.setBounds(oldX, oldY - offsetY, oldWidth, oldHeight);
            child.render(context, mouseX, mouseY, delta, theme);
            child.setBounds(oldX, oldY, oldWidth, oldHeight);
        }

        // Overlay pass so dropdown lists/tooltips render above later rows.
        for (UiComponent child : children) {
            if (!child.isVisible()) continue;
            int oldX = child.getX();
            int oldY = child.getY();
            int oldWidth = child.getWidth();
            int oldHeight = child.getHeight();

            child.setBounds(oldX, oldY - offsetY, oldWidth, oldHeight);
            child.renderOverlay(context, mouseX, mouseY, delta, theme);
            child.setBounds(oldX, oldY, oldWidth, oldHeight);
        }
        context.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!visible || !isInside(mouseX, mouseY)) return false;
        scrollOffset -= amount * 12.0;
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean dblClick) {
        if (!visible || click == null) return false;
        // Adjust coordinates for scroll offset before dispatching
        double adjustedY = click.y() + scrollOffset;
        Click adjusted = new Click(click.x(), adjustedY, click.buttonInfo());
        for (UiComponent child : children) {
            if (child.isVisible() && child.mouseClicked(adjusted, dblClick)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (!visible || click == null) return false;
        double adjustedY = click.y() + scrollOffset;
        Click adjusted = new Click(click.x(), adjustedY, click.buttonInfo());
        for (UiComponent child : children) {
            if (child.isVisible() && child.mouseReleased(adjusted)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (!visible || click == null) return false;
        double adjustedY = click.y() + scrollOffset;
        Click adjusted = new Click(click.x(), adjustedY, click.buttonInfo());
        for (UiComponent child : children) {
            if (child.isVisible() && child.mouseDragged(adjusted, deltaX, deltaY)) {
                return true;
            }
        }
        return false;
    }
}

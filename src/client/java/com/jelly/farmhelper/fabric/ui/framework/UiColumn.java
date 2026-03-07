package com.jelly.farmhelper.fabric.ui.framework;

/**
 * Vertical layout container: stacks children top‑to‑bottom with uniform spacing.
 * This is the core of the simple layout engine used across the custom UI.
 */
public class UiColumn extends UiContainer {
    private int spacing = 6;

    public UiColumn() {
    }

    public UiColumn(int spacing) {
        this.spacing = spacing;
    }

    public void setSpacing(int spacing) {
        this.spacing = spacing;
    }

    /**
     * Assign positions to children based on this column's bounds.
     * Children keep their preferred heights; width is clamped to the column width.
     */
    public void layoutChildren() {
        int currentY = y;
        for (UiComponent child : children) {
            if (!child.isVisible()) continue;
            int childHeight = child.getHeight() > 0 ? child.getHeight() : 22;
            child.setBounds(x, currentY, width, childHeight);
            currentY += childHeight + spacing;
        }
    }
}


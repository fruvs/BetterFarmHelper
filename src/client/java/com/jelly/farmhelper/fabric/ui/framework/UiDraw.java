package com.jelly.farmhelper.fabric.ui.framework;

import net.minecraft.client.gui.DrawContext;

/**
 * Shared immediate-mode drawing helpers for custom FarmHelper widgets.
 */
public final class UiDraw {
    private UiDraw() {
    }

    public static void drawRoundedPanel(DrawContext context, int x1, int y1, int x2, int y2, int radius, int borderColor, int fillColor) {
        fillRoundedRect(context, x1, y1, x2, y2, radius, borderColor);
        fillRoundedRect(context, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(1, radius - 1), fillColor);
    }

    public static void fillRoundedRect(DrawContext context, int x1, int y1, int x2, int y2, int radius, int color) {
        int w = x2 - x1;
        int h = y2 - y1;
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) {
            context.fill(x1, y1, x2, y2, color);
            return;
        }

        context.fill(x1 + r, y1, x2 - r, y2, color);
        context.fill(x1, y1 + r, x1 + r, y2 - r, color);
        context.fill(x2 - r, y1 + r, x2, y2 - r, color);

        for (int row = 0; row < r; row++) {
            int dy = r - row;
            int inset = (int) Math.ceil(r - Math.sqrt((double) (r * r - dy * dy)));
            int lx = x1 + inset;
            int rx = x2 - inset;
            context.fill(lx, y1 + row, rx, y1 + row + 1, color);
            context.fill(lx, y2 - row - 1, rx, y2 - row, color);
        }
    }
}

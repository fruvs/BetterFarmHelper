package com.jelly.farmhelper.fabric.ui.framework;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;

import java.util.function.Supplier;

/**
 * Simple pill‑shaped button with hover/press states.
 * Drawn manually using rectangles and text; does not use vanilla ButtonWidget.
 */
public class UiButton extends UiComponent {
    private final Supplier<Text> labelSupplier;
    private final Runnable onClick;

    private boolean hovered;
    private boolean pressed;

    public UiButton(Text label, Runnable onClick) {
        this(() -> label, onClick);
    }

    public UiButton(Supplier<Text> labelSupplier, Runnable onClick) {
        this.labelSupplier = labelSupplier;
        this.onClick = onClick;
        this.height = 24;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta, UiTheme theme) {
        if (!visible) return;
        hovered = isInside(mouseX, mouseY);
        int bg = hovered ? theme.controlBackgroundHover : theme.controlBackground;
        int border = hovered ? theme.accent : theme.borderColor;

        UiDraw.drawRoundedPanel(context, x, y, x + width, y + height, theme.cornerRadiusSmall, border, bg);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.textRenderer != null) {
            Text label = labelSupplier == null ? Text.empty() : labelSupplier.get();
            int tw = client.textRenderer.getWidth(label);
            int tx = x + (width - tw) / 2;
            int ty = y + (height - 8) / 2;
            int color = enabled ? theme.textPrimary : theme.textSecondary;
            context.drawText(client.textRenderer, label, tx, ty, color, true);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean dblClick) {
        if (!enabled || !visible || click == null || click.button() != 0) {
            return false;
        }
        if (isInside(click.x(), click.y())) {
            pressed = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (!enabled || !visible || click == null || click.button() != 0) {
            return false;
        }
        boolean wasPressed = pressed;
        pressed = false;
        if (wasPressed && isInside(click.x(), click.y()) && onClick != null) {
            onClick.run();
            return true;
        }
        return false;
    }
}

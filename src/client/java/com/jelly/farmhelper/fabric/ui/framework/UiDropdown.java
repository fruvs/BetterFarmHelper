package com.jelly.farmhelper.fabric.ui.framework;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Simple dropdown control with click-to-open option list.
 */
public class UiDropdown<E> extends UiComponent {
    private static UiDropdown<?> activeOpenDropdown;

    private final E[] options;
    private final Supplier<E> getter;
    private final java.util.function.Consumer<E> setter;
    private final Function<E, String> formatter;
    private boolean open;

    public UiDropdown(E[] options, Supplier<E> getter, java.util.function.Consumer<E> setter, Function<E, String> formatter) {
        this.options = options;
        this.getter = getter;
        this.setter = setter;
        this.formatter = formatter;
        this.height = 24;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta, UiTheme theme) {
        if (!visible) {
            return;
        }

        boolean hovered = isInside(mouseX, mouseY);
        int bg = hovered ? theme.controlBackgroundHover : theme.controlBackground;
        int border = hovered ? theme.accent : theme.borderColor;

        UiDraw.drawRoundedPanel(context, x, y, x + width, y + height, theme.cornerRadiusSmall, border, bg);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.textRenderer != null) {
            E current = getter.get();
            String valueText = current == null ? "N/A" : formatter.apply(current);
            String draw = client.textRenderer.trimToWidth(valueText + (open ? "  ^" : "  v"), Math.max(12, width - 12));
            context.drawText(client.textRenderer, Text.literal(draw), x + 8, y + (height - 8) / 2, theme.textPrimary, true);
        }

        if (!open || options == null || options.length == 0) {
            return;
        }
    }

    @Override
    public void renderOverlay(DrawContext context, int mouseX, int mouseY, float delta, UiTheme theme) {
        if (!visible || !open || options == null || options.length == 0) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        int optionHeight = 22;
        int listTop = y + height + 4;
        int listBottom = listTop + optionHeight * options.length;
        UiDraw.drawRoundedPanel(context, x, listTop, x + width, listBottom, theme.cornerRadiusSmall, theme.borderColor, theme.cardBackground);

        E current = getter.get();
        for (int i = 0; i < options.length; i++) {
            int oy1 = listTop + i * optionHeight;
            int oy2 = oy1 + optionHeight;
            boolean optionHover = mouseX >= x && mouseX <= x + width && mouseY >= oy1 && mouseY <= oy2;
            boolean selected = current == options[i] || (current != null && current.equals(options[i]));

            if (optionHover) {
                UiDraw.fillRoundedRect(context, x + 2, oy1 + 1, x + width - 2, oy2 - 1, 3, theme.controlBackgroundHover);
            } else if (selected) {
                UiDraw.fillRoundedRect(context, x + 2, oy1 + 1, x + width - 2, oy2 - 1, 3, 0x4422C55E);
            }

            if (client != null && client.textRenderer != null) {
                String text = client.textRenderer.trimToWidth(formatter.apply(options[i]), Math.max(12, width - 12));
                context.drawText(client.textRenderer, Text.literal(text), x + 8, oy1 + 7, theme.textPrimary, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean dblClick) {
        if (!enabled || !visible || click == null || click.button() != 0) {
            return false;
        }

        double mx = click.x();
        double my = click.y();
        boolean inMain = isInside(mx, my);
        if (inMain) {
            if (!open && activeOpenDropdown != null && activeOpenDropdown != this) {
                activeOpenDropdown.open = false;
            }
            open = !open;
            activeOpenDropdown = open ? this : null;
            return true;
        }

        if (!open || options == null || options.length == 0) {
            return false;
        }

        int optionHeight = 22;
        int listTop = y + height + 4;
        int listBottom = listTop + optionHeight * options.length;
        boolean inList = mx >= x && mx <= x + width && my >= listTop && my <= listBottom;
        if (inList) {
            int idx = (int) ((my - listTop) / optionHeight);
            if (idx >= 0 && idx < options.length) {
                setter.accept(options[idx]);
                open = false;
                if (activeOpenDropdown == this) {
                    activeOpenDropdown = null;
                }
                return true;
            }
        }

        // Consume first outside click to close dropdown and avoid click-through.
        open = false;
        if (activeOpenDropdown == this) {
            activeOpenDropdown = null;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        return false;
    }
}

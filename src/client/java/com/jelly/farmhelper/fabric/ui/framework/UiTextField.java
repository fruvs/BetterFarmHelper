package com.jelly.farmhelper.fabric.ui.framework;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;

/**
 * Minimal custom text field used for the global search bar.
 * It does not rely on vanilla TextFieldWidget.
 */
public class UiTextField extends UiComponent {
    private final String placeholder;
    private String value = "";
    private boolean focused;
    private int maxLength = 512;

    private TextChangeListener listener;

    public interface TextChangeListener {
        void onChange(String text);
    }

    public UiTextField(String placeholder) {
        this.placeholder = placeholder;
        this.height = 24;
    }

    public void setText(String value) {
        if (value == null) {
            this.value = "";
        } else if (value.length() > maxLength) {
            this.value = value.substring(0, maxLength);
        } else {
            this.value = value;
        }
        if (listener != null) {
            listener.onChange(this.value);
        }
    }

    public String getText() {
        return value;
    }

    public void setListener(TextChangeListener listener) {
        this.listener = listener;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        if (value.length() > this.maxLength) {
            value = value.substring(0, this.maxLength);
        }
    }

    public boolean isFocused() {
        return focused;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta, UiTheme theme) {
        if (!visible) return;
        boolean hovered = isInside(mouseX, mouseY);
        int bg = hovered || focused ? theme.controlBackgroundHover : theme.controlBackground;
        int border = focused ? theme.accent : theme.borderColor;

        UiDraw.drawRoundedPanel(context, x, y, x + width, y + height, theme.cornerRadiusSmall, border, bg);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return;
        }
        String toDraw = value.isEmpty() && !focused ? placeholder : value;
        toDraw = client.textRenderer.trimToWidth(toDraw, Math.max(12, width - 12));
        int color = value.isEmpty() && !focused ? theme.textSecondary : theme.textPrimary;
        context.drawText(client.textRenderer, toDraw, x + 6, y + (height - 8) / 2, color, true);
    }

    @Override
    public boolean mouseClicked(Click click, boolean dblClick) {
        if (!enabled || !visible || click == null || click.button() != 0) {
            return false;
        }
        focused = isInside(click.x(), click.y());
        return focused;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused) return false;
        // Handle backspace
        if (keyCode == 259 && !value.isEmpty()) {
            value = value.substring(0, value.length() - 1);
            if (listener != null) listener.onChange(value);
            return true;
        }
        // Escape releases focus
        if (keyCode == 256) {
            focused = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!focused) return false;
        if (chr == '\n' || chr == '\r') {
            return true;
        }
        if (value.length() >= maxLength) {
            return true;
        }
        value = value + chr;
        if (listener != null) listener.onChange(value);
        return true;
    }
}

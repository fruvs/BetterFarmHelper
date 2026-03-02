package com.jelly.farmhelper.fabric.ui;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.state.FreelookController;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public abstract class BaseConfigScreen extends Screen {
    private static final int CHROME_SHELL = 0xE910141A;
    private static final int CHROME_BORDER = 0xA5495666;
    private static final int CHROME_PANEL = 0xD0141A24;
    private static final int CHROME_TITLE = 0xFFE9EEF8;
    private static final int CHROME_SUBTITLE = 0xFFABB7C8;

    protected final Screen parent;
    protected final FarmHelperConfig config;
    private final Map<ClickableWidget, Integer> widgetBaseY = new LinkedHashMap<>();
    private int scrollOffsetY;

    protected BaseConfigScreen(Screen parent, Text title) {
        super(title);
        this.parent = parent;
        this.config = FarmHelperFabric.getConfigManager().getConfig();
    }

    @Override
    public void close() {
        FarmHelperFabric.getConfigManager().save();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        syncWidgetTracking();
        renderBackground(context, mouseX, mouseY, delta);
        if (!usesCustomChrome()) {
            renderUnifiedChrome(context);
            Text subtitle = subtitleText();
            if (subtitle != null && !subtitle.getString().isBlank()) {
                context.drawCenteredTextWithShadow(textRenderer, subtitle, width / 2, 30, CHROME_SUBTITLE);
            }
        }
        renderDecorations(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    protected <T extends net.minecraft.client.gui.Element & net.minecraft.client.gui.Drawable & net.minecraft.client.gui.Selectable> T addDrawableChild(T drawableElement) {
        T added = super.addDrawableChild(drawableElement);
        if (added instanceof ClickableWidget widget) {
            widgetBaseY.put(widget, widget.getY() - scrollOffsetY);
            widget.setY(widgetBaseY.get(widget) + scrollOffsetY);
        }
        return added;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        syncWidgetTracking();
        if (widgetBaseY.isEmpty() || Math.abs(verticalAmount) < 0.001) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        int minBaseY = Integer.MAX_VALUE;
        int maxBaseBottom = Integer.MIN_VALUE;
        for (Map.Entry<ClickableWidget, Integer> entry : widgetBaseY.entrySet()) {
            ClickableWidget widget = entry.getKey();
            int baseY = entry.getValue();
            minBaseY = Math.min(minBaseY, baseY);
            maxBaseBottom = Math.max(maxBaseBottom, baseY + widget.getHeight());
        }
        if (minBaseY == Integer.MAX_VALUE || maxBaseBottom == Integer.MIN_VALUE) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        int viewportTop = 36;
        int viewportBottom = height - 34;
        int minOffset = Math.min(0, viewportBottom - maxBaseBottom);
        int maxOffset = Math.max(0, viewportTop - minBaseY);
        if (minOffset == maxOffset) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        int delta = (int) Math.round(verticalAmount * 18.0);
        if (delta == 0) {
            delta = verticalAmount > 0 ? 1 : -1;
        }
        int nextOffset = MathHelper.clamp(scrollOffsetY + delta, minOffset, maxOffset);
        if (nextOffset == scrollOffsetY) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        scrollOffsetY = nextOffset;
        applyScrollOffset();
        return true;
    }

    protected void renderDecorations(DrawContext context, int mouseX, int mouseY, float delta) {
        // Extension hook for screens that need custom panel decorations.
    }

    protected boolean usesCustomChrome() {
        return false;
    }

    protected Text subtitleText() {
        return null;
    }

    private void syncWidgetTracking() {
        widgetBaseY.entrySet().removeIf(entry -> !children().contains(entry.getKey()));
    }

    private void applyScrollOffset() {
        for (Map.Entry<ClickableWidget, Integer> entry : widgetBaseY.entrySet()) {
            entry.getKey().setY(entry.getValue() + scrollOffsetY);
        }
    }

    private void renderUnifiedChrome(DrawContext context) {
        int shellX = 10;
        int shellY = 10;
        int shellWidth = width - 20;
        int shellHeight = height - 20;
        int panelInset = 8;
        int panelY = shellY + 30;

        context.fill(shellX, shellY, shellX + shellWidth, shellY + shellHeight, CHROME_SHELL);
        context.fill(shellX, shellY, shellX + shellWidth, shellY + 1, CHROME_BORDER);
        context.fill(shellX, shellY + shellHeight - 1, shellX + shellWidth, shellY + shellHeight, CHROME_BORDER);
        context.fill(shellX, shellY, shellX + 1, shellY + shellHeight, CHROME_BORDER);
        context.fill(shellX + shellWidth - 1, shellY, shellX + shellWidth, shellY + shellHeight, CHROME_BORDER);
        context.fill(
                shellX + panelInset,
                panelY,
                shellX + shellWidth - panelInset,
                shellY + shellHeight - panelInset,
                CHROME_PANEL
        );
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, CHROME_TITLE);
    }

    protected ButtonWidget addSimpleButton(int x, int y, int width, String label, ButtonWidget.PressAction onPress) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), btn -> {
                    onPress.onPress(btn);
                    FarmHelperFabric.getConfigManager().save();
                })
                .dimensions(x, y, width, 20)
                .build());
    }

    protected CyclingButtonWidget<Boolean> addToggleButton(int x, int y, int width, String label, Supplier<Boolean> getter, Runnable afterToggle) {
        return addDrawableChild(CyclingButtonWidget.onOffBuilder(getter.get())
                .build(x, y, width, 20, Text.literal(label), (btn, value) -> {
                    afterToggle.run();
                    FarmHelperFabric.getConfigManager().save();
                }));
    }

    protected ButtonWidget addCycleButton(int x, int y, int width, String text, Runnable onPress) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(text), btn -> {
                    onPress.run();
                    FarmHelperFabric.getConfigManager().save();
                })
                .dimensions(x, y, width, 20)
                .build());
    }

    protected <T> CyclingButtonWidget<T> addSelector(
            int x,
            int y,
            int width,
            String label,
            List<T> values,
            Supplier<T> getter,
            Consumer<T> setter,
            Function<T, String> valueLabel
    ) {
        return addDrawableChild(CyclingButtonWidget.builder(
                        value -> Text.literal(valueLabel.apply(value)),
                        getter.get()
                )
                .values(values)
                .build(x, y, width, 20, Text.literal(label), (btn, value) -> {
                    setter.accept(value);
                    FarmHelperFabric.getConfigManager().save();
                }));
    }

    protected void setFeatureEnabled(String id, boolean enabled) {
        config.featureToggles.put(id, enabled);
        FarmHelperFabric.getFeatureManager().setFeatureEnabled(id, enabled);
        if ("freelook".equals(id) && client != null) {
            boolean canFreelookNow = enabled && client.player != null && client.world != null;
            FreelookController.getInstance().setEnabled(client, canFreelookNow);
        }
    }

    protected TextFieldWidget addIntegerField(
            int x,
            int y,
            int width,
            String placeholder,
            int initialValue,
            int min,
            int max,
            IntConsumer setter
    ) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, 20, Text.literal(placeholder));
        field.setText(String.valueOf(initialValue));
        field.setSuggestion(placeholder);
        field.setMaxLength(12);
        field.setChangedListener(value -> {
            if (value == null || value.isBlank() || "-".equals(value)) {
                return;
            }
            try {
                int parsed = Integer.parseInt(value.trim());
                int clamped = MathHelper.clamp(parsed, min, max);
                setter.accept(clamped);
                if (clamped != parsed) {
                    field.setText(String.valueOf(clamped));
                }
                FarmHelperFabric.getConfigManager().save();
            } catch (NumberFormatException ignored) {
                // Ignore invalid edits until user enters a valid number.
            }
        });
        addDrawableChild(field);
        return field;
    }

    protected TextFieldWidget addTextField(
            int x,
            int y,
            int width,
            String placeholder,
            String initialValue,
            int maxLength,
            Consumer<String> setter
    ) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, 20, Text.literal(placeholder));
        field.setText(initialValue == null ? "" : initialValue);
        field.setSuggestion(placeholder);
        field.setMaxLength(Math.max(8, maxLength));
        field.setChangedListener(value -> {
            setter.accept(value == null ? "" : value);
            FarmHelperFabric.getConfigManager().save();
        });
        addDrawableChild(field);
        return field;
    }

    protected TextFieldWidget addFloatField(
            int x,
            int y,
            int width,
            String placeholder,
            float initialValue,
            float min,
            float max,
            Consumer<Float> setter
    ) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, 20, Text.literal(placeholder));
        field.setText(String.valueOf(initialValue));
        field.setSuggestion(placeholder);
        field.setMaxLength(14);
        field.setChangedListener(value -> {
            if (value == null || value.isBlank() || "-".equals(value) || ".".equals(value) || "-.".equals(value)) {
                return;
            }
            try {
                float parsed = Float.parseFloat(value.trim());
                float clamped = MathHelper.clamp(parsed, min, max);
                setter.accept(clamped);
                if (Math.abs(clamped - parsed) > 0.0001f) {
                    field.setText(String.valueOf(clamped));
                }
                FarmHelperFabric.getConfigManager().save();
            } catch (NumberFormatException ignored) {
                // Ignore invalid edits until user enters a valid number.
            }
        });
        addDrawableChild(field);
        return field;
    }

    protected SliderWidget addIntSlider(
            int x,
            int y,
            int width,
            String label,
            int min,
            int max,
            IntSupplier getter,
            IntConsumer setter
    ) {
        int safeMin = Math.min(min, max);
        int safeMax = Math.max(min, max);
        int initial = MathHelper.clamp(getter.getAsInt(), safeMin, safeMax);
        double range = Math.max(1d, safeMax - safeMin);
        SliderWidget slider = new SliderWidget(x, y, width, 20, Text.literal(label), (initial - safeMin) / range) {
            {
                this.updateMessage();
            }

            @Override
            protected void updateMessage() {
                int value = safeMin + (int) Math.round(this.value * range);
                setMessage(Text.literal(label + ": " + MathHelper.clamp(value, safeMin, safeMax)));
            }

            @Override
            protected void applyValue() {
                int value = safeMin + (int) Math.round(this.value * range);
                setter.accept(MathHelper.clamp(value, safeMin, safeMax));
                FarmHelperFabric.getConfigManager().save();
            }
        };
        return addDrawableChild(slider);
    }
}

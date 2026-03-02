package com.jelly.farmhelper.fabric.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class HudEditorScreen extends BaseConfigScreen {
    private static final int BOX_WIDTH = 220;
    private static final int STATUS_BOX_HEIGHT = 122;
    private static final int PROFIT_BOX_HEIGHT = 76;
    private static final int DEBUG_BOX_HEIGHT = 86;

    public HudEditorScreen(Screen parent) {
        super(parent, Text.literal("FarmHelper - HUD Editor"));
    }

    @Override
    protected Text subtitleText() {
        return Text.literal("Adjust HUD placement with precise nudges.");
    }

    @Override
    protected void init() {
        int x = 16;
        int y = 38;
        y = addNudgeRow(x, y, "Status", () -> config.statusHudX, () -> config.statusHudY, STATUS_BOX_HEIGHT, BOX_WIDTH,
                (nx, ny) -> {
                    config.statusHudX = nx;
                    config.statusHudY = ny;
                });
        y += 4;
        y = addNudgeRow(x, y, "Profit", () -> config.profitHudX, () -> config.profitHudY, PROFIT_BOX_HEIGHT, BOX_WIDTH,
                (nx, ny) -> {
                    config.profitHudX = nx;
                    config.profitHudY = ny;
                });
        y += 4;
        addNudgeRow(x, y, "Debug", () -> config.debugHudX, () -> config.debugHudY, DEBUG_BOX_HEIGHT, BOX_WIDTH,
                (nx, ny) -> {
                    config.debugHudX = nx;
                    config.debugHudY = ny;
                });

        int buttonY = height - 30;
        addSimpleButton(width / 2 - 188, buttonY, 120, "Reset Status", btn -> {
            config.statusHudX = 8;
            config.statusHudY = 8;
        });
        addSimpleButton(width / 2 - 62, buttonY, 120, "Reset Profit", btn -> {
            config.profitHudX = 8;
            config.profitHudY = 170;
        });
        addSimpleButton(width / 2 + 64, buttonY, 120, "Reset Debug", btn -> {
            config.debugHudX = 8;
            config.debugHudY = 250;
        });
        addSimpleButton(width - 90, 10, 76, "Back", btn -> close());
    }

    @Override
    protected void renderDecorations(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawText(textRenderer, Text.literal("Preview boxes"), 16, height - 24, 0xFFABB7C8, false);
        drawHudBox(context, clampX(config.statusHudX, BOX_WIDTH), clampY(config.statusHudY, STATUS_BOX_HEIGHT), BOX_WIDTH, STATUS_BOX_HEIGHT, "Status HUD", 0x7A1B2432, 0xFF6CC6FF);
        drawHudBox(context, clampX(config.profitHudX, BOX_WIDTH), clampY(config.profitHudY, PROFIT_BOX_HEIGHT), BOX_WIDTH, PROFIT_BOX_HEIGHT, "Profit HUD", 0x7A1F2C24, 0xFF8CD5A8);
        drawHudBox(context, clampX(config.debugHudX, BOX_WIDTH), clampY(config.debugHudY, DEBUG_BOX_HEIGHT), BOX_WIDTH, DEBUG_BOX_HEIGHT, "Debug HUD", 0x7A2A1E24, 0xFFFFAA55);
    }

    private int addNudgeRow(
            int x,
            int y,
            String label,
            IntGetter getX,
            IntGetter getY,
            int widgetHeight,
            int widgetWidth,
            IntPairSetter setter
    ) {
        final int rowW = 64;
        final int gap = 6;
        addSimpleButton(x, y, rowW, label + " -X", btn -> setter.set(clampX(getX.get() - 10, widgetWidth), clampY(getY.get(), widgetHeight)));
        addSimpleButton(x + rowW + gap, y, rowW, label + " +X", btn -> setter.set(clampX(getX.get() + 10, widgetWidth), clampY(getY.get(), widgetHeight)));
        addSimpleButton(x + (rowW + gap) * 2, y, rowW, label + " -Y", btn -> setter.set(clampX(getX.get(), widgetWidth), clampY(getY.get() - 10, widgetHeight)));
        addSimpleButton(x + (rowW + gap) * 3, y, rowW, label + " +Y", btn -> setter.set(clampX(getX.get(), widgetWidth), clampY(getY.get() + 10, widgetHeight)));
        addSimpleButton(x + (rowW + gap) * 4, y, rowW, label + " +1", btn -> setter.set(clampX(getX.get() + 1, widgetWidth), clampY(getY.get() + 1, widgetHeight)));
        return y + 24;
    }

    private void drawHudBox(DrawContext context, int x, int y, int width, int height, String title, int fill, int border) {
        context.fill(x, y, x + width, y + height, fill);
        context.fill(x, y, x + width, y + 1, border);
        context.fill(x, y + height - 1, x + width, y + height, border);
        context.fill(x, y, x + 1, y + height, border);
        context.fill(x + width - 1, y, x + width, y + height, border);
        context.drawText(textRenderer, Text.literal(title), x + 6, y + 6, 0xFFE9EEF8, false);
    }

    private int clampX(int x, int widgetWidth) {
        return MathHelper.clamp(x, 4, Math.max(4, width - widgetWidth - 4));
    }

    private int clampY(int y, int widgetHeight) {
        return MathHelper.clamp(y, 4, Math.max(4, height - widgetHeight - 4));
    }

    @FunctionalInterface
    private interface IntGetter {
        int get();
    }

    @FunctionalInterface
    private interface IntPairSetter {
        void set(int x, int y);
    }
}

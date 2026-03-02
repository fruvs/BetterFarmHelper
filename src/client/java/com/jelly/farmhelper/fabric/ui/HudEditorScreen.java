package com.jelly.farmhelper.fabric.ui;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class HudEditorScreen extends Screen {
    private static final int BOX_WIDTH = 220;
    private static final int STATUS_BOX_HEIGHT = 122;
    private static final int PROFIT_BOX_HEIGHT = 76;
    private static final int DEBUG_BOX_HEIGHT = 86;

    private final Screen parent;
    private final FarmHelperConfig config;
    private DragTarget activeDragTarget = DragTarget.NONE;
    private int dragOffsetX;
    private int dragOffsetY;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("FarmHelper - HUD Editor"));
        this.parent = parent;
        this.config = FarmHelperFabric.getConfigManager().getConfig();
    }

    @Override
    protected void init() {
        int buttonY = height - 30;
        addSimpleButton(width / 2 - 188, buttonY, 120, "Reset Status", btn -> {
            config.statusHudX = 8;
            config.statusHudY = 8;
            saveConfig();
        });
        addSimpleButton(width / 2 - 62, buttonY, 120, "Reset Profit", btn -> {
            config.profitHudX = 8;
            config.profitHudY = 170;
            saveConfig();
        });
        addSimpleButton(width / 2 + 64, buttonY, 120, "Reset Debug", btn -> {
            config.debugHudX = 8;
            config.debugHudY = 250;
            saveConfig();
        });
        addSimpleButton(width - 90, 10, 76, "Back", btn -> close());
    }

    @Override
    public void close() {
        saveConfig();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawText(textRenderer, Text.literal("FarmHelper HUD Editor"), 16, 12, 0xFFE9EEF8, false);
        context.drawText(textRenderer, Text.literal("Drag each HUD box with your mouse to reposition."), 16, 24, 0xFFABB7C8, false);
        context.drawText(textRenderer, Text.literal("Preview boxes"), 16, height - 24, 0xFFABB7C8, false);

        drawHudBox(context, DragTarget.STATUS, clampX(config.statusHudX, BOX_WIDTH), clampY(config.statusHudY, STATUS_BOX_HEIGHT), BOX_WIDTH, STATUS_BOX_HEIGHT, "Status HUD", 0x7A1B2432, 0xFF6CC6FF, mouseX, mouseY);
        drawHudBox(context, DragTarget.PROFIT, clampX(config.profitHudX, BOX_WIDTH), clampY(config.profitHudY, PROFIT_BOX_HEIGHT), BOX_WIDTH, PROFIT_BOX_HEIGHT, "Profit HUD", 0x7A1F2C24, 0xFF8CD5A8, mouseX, mouseY);
        drawHudBox(context, DragTarget.DEBUG, clampX(config.debugHudX, BOX_WIDTH), clampY(config.debugHudY, DEBUG_BOX_HEIGHT), BOX_WIDTH, DEBUG_BOX_HEIGHT, "Debug HUD", 0x7A2A1E24, 0xFFFFAA55, mouseX, mouseY);
    }

    private ButtonWidget addSimpleButton(int x, int y, int width, String text, ButtonWidget.PressAction action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(text), action).dimensions(x, y, width, 20).build());
    }

    private void drawHudBox(DrawContext context, DragTarget target, int x, int y, int width, int height, String title, int fill, int border, int mouseX, int mouseY) {
        boolean hovered = target != DragTarget.NONE && isInside(mouseX, mouseY, x, y, width, height);
        boolean dragging = target == activeDragTarget;
        int accent = dragging ? 0xFFFFFFFF : (hovered ? 0xFFD6F2FF : border);
        context.fill(x, y, x + width, y + height, fill);
        context.fill(x, y, x + width, y + 1, accent);
        context.fill(x, y + height - 1, x + width, y + height, accent);
        context.fill(x, y, x + 1, y + height, accent);
        context.fill(x + width - 1, y, x + width, y + height, accent);
        context.drawText(textRenderer, Text.literal(title), x + 6, y + 6, 0xFFE9EEF8, false);
        if (hovered || dragging) {
            context.drawText(textRenderer, Text.literal("Drag"), x + width - 32, y + 6, 0xFFE9EEF8, false);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean dblClick) {
        if (super.mouseClicked(click, dblClick)) {
            return true;
        }
        if (click == null || click.button() != 0) {
            return false;
        }
        double mouseX = click.x();
        double mouseY = click.y();
        DragTarget target = resolveTargetAt(mouseX, mouseY);
        if (target == DragTarget.NONE) {
            return false;
        }
        activeDragTarget = target;
        dragOffsetX = (int) mouseX - getTargetX(target);
        dragOffsetY = (int) mouseY - getTargetY(target);
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (click == null || click.button() != 0 || activeDragTarget == DragTarget.NONE) {
            return super.mouseDragged(click, deltaX, deltaY);
        }
        double mouseX = click.x();
        double mouseY = click.y();
        int width = getTargetWidth(activeDragTarget);
        int height = getTargetHeight(activeDragTarget);
        int nx = clampX((int) mouseX - dragOffsetX, width);
        int ny = clampY((int) mouseY - dragOffsetY, height);
        setTargetPos(activeDragTarget, nx, ny);
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click != null && click.button() == 0 && activeDragTarget != DragTarget.NONE) {
            activeDragTarget = DragTarget.NONE;
            saveConfig();
            return true;
        }
        return super.mouseReleased(click);
    }

    private DragTarget resolveTargetAt(double mouseX, double mouseY) {
        if (isInside(mouseX, mouseY, config.debugHudX, config.debugHudY, BOX_WIDTH, DEBUG_BOX_HEIGHT)) {
            return DragTarget.DEBUG;
        }
        if (isInside(mouseX, mouseY, config.profitHudX, config.profitHudY, BOX_WIDTH, PROFIT_BOX_HEIGHT)) {
            return DragTarget.PROFIT;
        }
        if (isInside(mouseX, mouseY, config.statusHudX, config.statusHudY, BOX_WIDTH, STATUS_BOX_HEIGHT)) {
            return DragTarget.STATUS;
        }
        return DragTarget.NONE;
    }

    private boolean isInside(double x, double y, int bx, int by, int bw, int bh) {
        int cx = clampX(bx, bw);
        int cy = clampY(by, bh);
        return x >= cx && x <= cx + bw && y >= cy && y <= cy + bh;
    }

    private int getTargetX(DragTarget target) {
        return switch (target) {
            case STATUS -> clampX(config.statusHudX, BOX_WIDTH);
            case PROFIT -> clampX(config.profitHudX, BOX_WIDTH);
            case DEBUG -> clampX(config.debugHudX, BOX_WIDTH);
            case NONE -> 0;
        };
    }

    private int getTargetY(DragTarget target) {
        return switch (target) {
            case STATUS -> clampY(config.statusHudY, STATUS_BOX_HEIGHT);
            case PROFIT -> clampY(config.profitHudY, PROFIT_BOX_HEIGHT);
            case DEBUG -> clampY(config.debugHudY, DEBUG_BOX_HEIGHT);
            case NONE -> 0;
        };
    }

    private int getTargetWidth(DragTarget target) {
        return BOX_WIDTH;
    }

    private int getTargetHeight(DragTarget target) {
        return switch (target) {
            case STATUS -> STATUS_BOX_HEIGHT;
            case PROFIT -> PROFIT_BOX_HEIGHT;
            case DEBUG -> DEBUG_BOX_HEIGHT;
            case NONE -> 0;
        };
    }

    private void setTargetPos(DragTarget target, int x, int y) {
        switch (target) {
            case STATUS -> {
                config.statusHudX = x;
                config.statusHudY = y;
            }
            case PROFIT -> {
                config.profitHudX = x;
                config.profitHudY = y;
            }
            case DEBUG -> {
                config.debugHudX = x;
                config.debugHudY = y;
            }
            case NONE -> {
            }
        }
    }

    private int clampX(int x, int widgetWidth) {
        return MathHelper.clamp(x, 4, Math.max(4, width - widgetWidth - 4));
    }

    private int clampY(int y, int widgetHeight) {
        return MathHelper.clamp(y, 4, Math.max(4, height - widgetHeight - 4));
    }

    private void saveConfig() {
        FarmHelperFabric.getConfigManager().save();
    }

    private enum DragTarget {
        NONE,
        STATUS,
        PROFIT,
        DEBUG
    }
}

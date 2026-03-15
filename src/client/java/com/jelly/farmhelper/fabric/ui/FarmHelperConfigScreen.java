package com.jelly.farmhelper.fabric.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Legacy entrypoint kept for compatibility with older call sites.
 * It delegates directly to the modern configuration screen.
 */
public class FarmHelperConfigScreen extends Screen {
    private final Screen parent;
    private boolean delegated;

    public FarmHelperConfigScreen(Screen parent) {
        super(Text.literal("FarmHelper Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        delegateToModern();
    }

    @Override
    public void tick() {
        if (!delegated) {
            delegateToModern();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
    }

    private void delegateToModern() {
        if (delegated || client == null) {
            return;
        }
        delegated = true;
        client.setScreen(new FarmHelperModernConfigScreen(parent));
    }
}

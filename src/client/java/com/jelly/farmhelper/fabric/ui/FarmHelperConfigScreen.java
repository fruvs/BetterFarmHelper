package com.jelly.farmhelper.fabric.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Legacy entrypoint kept for compatibility with older call sites.
 * It now delegates directly to the consolidated YACL configuration screen.
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
        delegateToYacl();
    }

    @Override
    public void tick() {
        if (!delegated) {
            delegateToYacl();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
    }

    private void delegateToYacl() {
        if (delegated || client == null) {
            return;
        }
        delegated = true;
        client.setScreen(FarmHelperYaclScreen.create(parent));
    }
}

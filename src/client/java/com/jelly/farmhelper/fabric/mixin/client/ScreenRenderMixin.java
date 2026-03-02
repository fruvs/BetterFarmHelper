package com.jelly.farmhelper.fabric.mixin.client;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.event.DrawScreenAfterEvent;
import com.jelly.farmhelper.fabric.ui.AutoReconnectOverlayRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenRenderMixin {
    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void farmhelper$afterRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (screen instanceof HandledScreen<?>) {
            return;
        }
        if (screen instanceof TitleScreen || screen instanceof MultiplayerScreen) {
            AutoReconnectOverlayRenderer.render(context, screen.width);
        }
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        FarmHelperFabric.getEventBus().post(new DrawScreenAfterEvent(screen.getClass().getName(), title));
    }
}

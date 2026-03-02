package com.jelly.farmhelper.fabric.mixin.client;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.event.DrawScreenAfterEvent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenRenderMixin {
    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void farmhelper$afterContainerRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        FarmHelperFabric.getEventBus().post(new DrawScreenAfterEvent(screen.getClass().getName(), title));
    }
}

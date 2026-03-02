package com.jelly.farmhelper.fabric.mixin.client;

import com.jelly.farmhelper.fabric.state.FreelookController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseFreelookMixin {
    @Redirect(
            method = "updateMouse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;changeLookDirection(DD)V"
            ),
            require = 0
    )
    private void farmhelper$redirectMouseLookToFreelook(Entity entity, double deltaX, double deltaY) {
        FreelookController controller = FreelookController.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && controller.isActive() && client.player != null && entity == client.player) {
            controller.onMouseLook(deltaX, deltaY);
            return;
        }
        entity.changeLookDirection(deltaX, deltaY);
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void farmhelper$consumeScrollForFreelook(long window, double horizontal, double vertical, CallbackInfo ci) {
        FreelookController controller = FreelookController.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || !controller.isActive()) {
            return;
        }
        if (client.currentScreen != null) {
            return;
        }
        controller.adjustDistance(vertical);
        ci.cancel();
    }
}

package com.jelly.farmhelper.fabric.mixin.client;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.event.InventoryInputEvent;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public class HandledScreenInputMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), require = 0)
    private void farmhelper$onInventoryKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        int keyCode = input == null ? -1 : input.getKeycode();
        char typed = '\0';
        if (keyCode >= 0) {
            String keyName = GLFW.glfwGetKeyName(keyCode, 0);
            if (keyName != null && !keyName.isEmpty()) {
                typed = keyName.charAt(0);
            }
        }
        FarmHelperFabric.getEventBus().post(new InventoryInputEvent(keyCode, typed));
    }
}

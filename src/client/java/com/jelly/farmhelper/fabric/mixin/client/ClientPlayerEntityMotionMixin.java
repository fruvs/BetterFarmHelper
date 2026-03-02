package com.jelly.farmhelper.fabric.mixin.client;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.event.MotionUpdateEvent;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMotionMixin {
    @Inject(method = "sendMovementPackets", at = @At("HEAD"), require = 0)
    private void farmhelper$postMotionPre(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        FarmHelperFabric.getEventBus().post(new MotionUpdateEvent.Pre(
                player.getYaw(),
                player.getPitch(),
                player.getX(),
                player.getY(),
                player.getZ()
        ));
    }

    @Inject(method = "sendMovementPackets", at = @At("TAIL"), require = 0)
    private void farmhelper$postMotionPost(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        FarmHelperFabric.getEventBus().post(new MotionUpdateEvent.Post(
                player.getYaw(),
                player.getPitch(),
                player.getX(),
                player.getY(),
                player.getZ()
        ));
    }
}

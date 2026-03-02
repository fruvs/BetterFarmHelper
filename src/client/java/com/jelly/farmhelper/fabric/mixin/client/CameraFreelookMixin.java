package com.jelly.farmhelper.fabric.mixin.client;

import com.jelly.farmhelper.fabric.state.FreelookController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Camera.class)
public abstract class CameraFreelookMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Unique
    private boolean farmhelper$patchedEntityRotation;
    @Unique
    private float farmhelper$originalYaw;
    @Unique
    private float farmhelper$originalPitch;
    @Unique
    private float farmhelper$originalBodyYaw;
    @Unique
    private float farmhelper$originalHeadYaw;
    @Unique
    private float farmhelper$originalLastBodyYaw;
    @Unique
    private float farmhelper$originalLastHeadYaw;

    @Inject(method = "update", at = @At("HEAD"))
    private void farmhelper$captureAndApplyFreelook(
            World area,
            Entity focusedEntity,
            boolean thirdPerson,
            boolean inverseView,
            float tickProgress,
            CallbackInfo ci
    ) {
        farmhelper$patchedEntityRotation = false;
        FreelookController controller = FreelookController.getInstance();
        if (!controller.isActive()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || focusedEntity != client.player) {
            return;
        }

        farmhelper$patchedEntityRotation = true;
        farmhelper$originalYaw = focusedEntity.getYaw();
        farmhelper$originalPitch = focusedEntity.getPitch();
        focusedEntity.setYaw(controller.getCameraYaw());
        focusedEntity.setPitch(controller.getCameraPitch());

        if (focusedEntity instanceof LivingEntity living) {
            farmhelper$originalBodyYaw = living.bodyYaw;
            farmhelper$originalHeadYaw = living.headYaw;
            farmhelper$originalLastBodyYaw = living.lastBodyYaw;
            farmhelper$originalLastHeadYaw = living.lastHeadYaw;
            living.bodyYaw = controller.getCameraYaw();
            living.headYaw = controller.getCameraYaw();
            living.lastBodyYaw = controller.getCameraYaw();
            living.lastHeadYaw = controller.getCameraYaw();
        }
    }

    @ModifyArg(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"
            ),
            index = 0,
            require = 0
    )
    private float farmhelper$useFreelookDistance(float originalDistance) {
        FreelookController controller = FreelookController.getInstance();
        if (!controller.isActive()) {
            return originalDistance;
        }
        return controller.getDistance();
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void farmhelper$applyFreelookRotation(
            World area,
            Entity focusedEntity,
            boolean thirdPerson,
            boolean inverseView,
            float tickProgress,
            CallbackInfo ci
    ) {
        FreelookController controller = FreelookController.getInstance();
        if (controller.isActive()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null && focusedEntity == client.player) {
                setRotation(controller.getCameraYaw(), controller.getCameraPitch());
            }
        }
        if (!farmhelper$patchedEntityRotation || focusedEntity == null) {
            return;
        }
        focusedEntity.setYaw(farmhelper$originalYaw);
        focusedEntity.setPitch(farmhelper$originalPitch);
        if (focusedEntity instanceof LivingEntity living) {
            living.bodyYaw = farmhelper$originalBodyYaw;
            living.headYaw = farmhelper$originalHeadYaw;
            living.lastBodyYaw = farmhelper$originalLastBodyYaw;
            living.lastHeadYaw = farmhelper$originalLastHeadYaw;
        }
        farmhelper$patchedEntityRotation = false;
    }
}

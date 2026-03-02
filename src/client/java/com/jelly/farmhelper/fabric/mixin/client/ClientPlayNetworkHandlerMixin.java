package com.jelly.farmhelper.fabric.mixin.client;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.event.BlockChangeEvent;
import com.jelly.farmhelper.fabric.event.ChunkServerLoadEvent;
import com.jelly.farmhelper.fabric.event.ReceivePacketEvent;
import com.jelly.farmhelper.fabric.event.SpawnObjectEvent;
import com.jelly.farmhelper.fabric.event.SpawnParticleEvent;
import com.jelly.farmhelper.fabric.util.TablistUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRotationS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onPlayerPositionLook", at = @At("HEAD"))
    private void farmhelper$onPlayerPositionLook(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || packet == null) {
            return;
        }
        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        EntityPosition current = new EntityPosition(playerPos, player.getVelocity(), player.getYaw(), player.getPitch());
        EntityPosition absolute = EntityPosition.apply(current, packet.change(), packet.relatives());

        double teleportDistance = current.position().distanceTo(absolute.position());
        float yawDelta = Math.abs(MathHelper.wrapDegrees(absolute.yaw() - player.getYaw()));
        float pitchDelta = Math.abs(MathHelper.wrapDegrees(absolute.pitch() - player.getPitch()));

        FarmHelperFabric.getEventBus().post(new ReceivePacketEvent(packet));
        FarmHelperFabric.getFailsafeManager().onPositionLookPacket(
                teleportDistance,
                yawDelta,
                pitchDelta,
                current.position().x,
                current.position().y,
                current.position().z,
                absolute.position().x,
                absolute.position().y,
                absolute.position().z,
                current.yaw(),
                current.pitch(),
                absolute.yaw(),
                absolute.pitch()
        );
    }

    @Inject(method = "onPlayerRotation", at = @At("HEAD"))
    private void farmhelper$onPlayerRotation(PlayerRotationS2CPacket packet, CallbackInfo ci) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || packet == null) {
            return;
        }
        FarmHelperFabric.getEventBus().post(new ReceivePacketEvent(packet));
        float targetYaw = packet.relativeYaw() ? player.getYaw() + packet.yaw() : packet.yaw();
        float targetPitch = packet.relativePitch() ? player.getPitch() + packet.pitch() : packet.pitch();
        float yawDelta = Math.abs(MathHelper.wrapDegrees(targetYaw - player.getYaw()));
        float pitchDelta = Math.abs(MathHelper.wrapDegrees(targetPitch - player.getPitch()));
        FarmHelperFabric.getFailsafeManager().onPlayerRotationPacket(
                yawDelta,
                pitchDelta,
                player.getYaw(),
                player.getPitch(),
                targetYaw,
                targetPitch
        );
    }

    @Inject(method = "onEntityVelocityUpdate", at = @At("HEAD"))
    private void farmhelper$onEntityVelocity(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || packet == null || packet.getEntityId() != player.getId()) {
            return;
        }
        FarmHelperFabric.getEventBus().post(new ReceivePacketEvent(packet));
        Vec3d velocity = packet.getVelocity();
        FarmHelperFabric.getFailsafeManager().onPlayerVelocityPacket(
                velocity.y,
                velocity.length()
        );
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("HEAD"))
    private void farmhelper$onScreenHandlerSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || packet == null) {
            return;
        }
        FarmHelperFabric.getEventBus().post(new ReceivePacketEvent(packet));
        FarmHelperFabric.getFailsafeManager().onScreenHandlerSlotPacket(
                packet.getSlot(),
                player.getInventory().getSelectedSlot(),
                packet.getStack()
        );
    }

    @Inject(method = "onWorldTimeUpdate", at = @At("HEAD"))
    private void farmhelper$onWorldTimeUpdate(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        FarmHelperFabric.getEventBus().post(new ReceivePacketEvent(packet));
        FarmHelperFabric.getFailsafeManager().onWorldTimePacket();
    }

    @Inject(method = "onBlockUpdate", at = @At("HEAD"), require = 0)
    private void farmhelper$onBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        FarmHelperFabric.getEventBus().post(new ReceivePacketEvent(packet));
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || packet == null) {
            return;
        }
        FarmHelperFabric.getEventBus().post(new BlockChangeEvent(
                packet.getPos(),
                world.getBlockState(packet.getPos()),
                packet.getState(),
                world.getRegistryKey()
        ));
    }

    @Inject(method = "onChunkData", at = @At("TAIL"), require = 0)
    private void farmhelper$onChunkData(ChunkDataS2CPacket packet, CallbackInfo ci) {
        FarmHelperFabric.getEventBus().post(new ReceivePacketEvent(packet));
        if (packet == null) {
            return;
        }
        int x = invokeInt(packet, "getChunkX", "chunkX", "x");
        int z = invokeInt(packet, "getChunkZ", "chunkZ", "z");
        FarmHelperFabric.getEventBus().post(new ChunkServerLoadEvent(x, z));
    }

    @Inject(method = "onParticle", at = @At("HEAD"), require = 0)
    private void farmhelper$onParticle(ParticleS2CPacket packet, CallbackInfo ci) {
        FarmHelperFabric.getEventBus().post(new ReceivePacketEvent(packet));
        if (packet == null) {
            return;
        }
        FarmHelperFabric.getEventBus().post(new SpawnParticleEvent(
                packet.getParameters(),
                packet.shouldForceSpawn() || packet.isImportant(),
                new Vec3d(packet.getX(), packet.getY(), packet.getZ()),
                new Vec3d(packet.getOffsetX(), packet.getOffsetY(), packet.getOffsetZ()),
                packet.getCount()
        ));
    }

    @Inject(method = "onEntitySpawn", at = @At("HEAD"), require = 0)
    private void farmhelper$onEntitySpawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        FarmHelperFabric.getEventBus().post(new ReceivePacketEvent(packet));
        if (packet == null) {
            return;
        }
        FarmHelperFabric.getEventBus().post(new SpawnObjectEvent(
                packet.getEntityId(),
                String.valueOf(packet.getEntityType()),
                new Vec3d(packet.getX(), packet.getY(), packet.getZ()),
                packet.getVelocity(),
                packet.getYaw(),
                packet.getPitch()
        ));
    }

    @Inject(method = "onPlayerListHeader", at = @At("TAIL"), require = 0)
    private void farmhelper$onPlayerListHeader(PlayerListHeaderS2CPacket packet, CallbackInfo ci) {
        FarmHelperFabric.getEventBus().post(new ReceivePacketEvent(packet));
        if (packet == null) {
            return;
        }
        String footer = packet.footer() == null ? "" : packet.footer().getString();
        List<String> lines = footer.isBlank() ? List.of() : Arrays.asList(footer.split("\\R"));
        TablistUtils.setFooterLines(lines);
    }

    private int invokeInt(Object target, String... names) {
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                Object value = method.invoke(target);
                if (value instanceof Number number) {
                    return number.intValue();
                }
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }
}

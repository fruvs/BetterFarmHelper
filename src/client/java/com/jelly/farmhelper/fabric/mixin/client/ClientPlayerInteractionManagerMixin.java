package com.jelly.farmhelper.fabric.mixin.client;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import com.jelly.farmhelper.fabric.event.ClickedBlockEvent;
import com.jelly.farmhelper.fabric.event.PlayerDestroyBlockEvent;
import com.jelly.farmhelper.fabric.event.WindowClickEvent;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    private static BlockPos farmhelper$lastAttackPos;
    private static Direction farmhelper$lastAttackFacing = Direction.UP;

    @Inject(method = "attackBlock", at = @At("HEAD"), require = 0)
    private void farmhelper$onAttackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || pos == null || direction == null) {
            return;
        }
        farmhelper$lastAttackPos = pos.toImmutable();
        farmhelper$lastAttackFacing = direction;
        Block block = client.world.getBlockState(pos).getBlock();
        FarmHelperFabric.getEventBus().post(new ClickedBlockEvent(pos.toImmutable(), direction, block));
    }

    @Inject(method = "breakBlock", at = @At("HEAD"), require = 0)
    private void farmhelper$onBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || pos == null) {
            return;
        }
        Block block = client.world.getBlockState(pos).getBlock();
        Direction direction = resolveBreakDirection(client, pos);
        FarmHelperFabric.getEventBus().post(new PlayerDestroyBlockEvent(pos.toImmutable(), direction, block));
    }

    private Direction resolveBreakDirection(MinecraftClient client, BlockPos pos) {
        if (client.crosshairTarget instanceof BlockHitResult hitResult
                && pos.equals(hitResult.getBlockPos())) {
            return hitResult.getSide();
        }
        if (farmhelper$lastAttackPos != null && farmhelper$lastAttackPos.equals(pos)) {
            return farmhelper$lastAttackFacing;
        }
        return Direction.UP;
    }

    @Inject(method = "clickSlot", at = @At("HEAD"), require = 0)
    private void farmhelper$onWindowClick(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        FarmHelperFabric.getEventBus().post(new WindowClickEvent(
                syncId,
                slotId,
                button,
                actionType,
                System.currentTimeMillis()
        ));
    }
}

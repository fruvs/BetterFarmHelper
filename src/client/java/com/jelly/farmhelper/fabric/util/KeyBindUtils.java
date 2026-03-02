package com.jelly.farmhelper.fabric.util;

import com.jelly.farmhelper.fabric.mixin.client.KeyBindingAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class KeyBindUtils {
    private static final Map<Integer, java.util.function.Function<GameOptions, KeyBinding>> YAW_KEY_MAP = Map.of(
            0, options -> options.forwardKey,
            90, options -> options.leftKey,
            180, options -> options.backKey,
            -90, options -> options.rightKey
    );

    private KeyBindUtils() {
    }

    public static void setPressed(KeyBinding key, boolean pressed) {
        if (key == null) {
            return;
        }
        key.setPressed(pressed);
    }

    public static void holdAttack(MinecraftClient client, boolean pressed) {
        if (client == null || client.options == null) {
            return;
        }
        setPressed(client.options.attackKey, pressed);
    }

    public static void holdUse(MinecraftClient client, boolean pressed) {
        if (client == null || client.options == null) {
            return;
        }
        setPressed(client.options.useKey, pressed);
    }

    public static void holdMovement(
            MinecraftClient client,
            boolean forward,
            boolean back,
            boolean left,
            boolean right,
            boolean jump,
            boolean sneak,
            boolean sprint
    ) {
        if (client == null || client.options == null) {
            return;
        }
        GameOptions options = client.options;
        setPressed(options.forwardKey, forward);
        setPressed(options.backKey, back);
        setPressed(options.leftKey, left);
        setPressed(options.rightKey, right);
        setPressed(options.jumpKey, jump);
        setPressed(options.sneakKey, sneak);
        setPressed(options.sprintKey, sprint);
    }

    public static void stopMovement(MinecraftClient client, boolean keepAttack) {
        if (client == null || client.options == null) {
            return;
        }
        GameOptions options = client.options;
        setPressed(options.forwardKey, false);
        setPressed(options.backKey, false);
        setPressed(options.leftKey, false);
        setPressed(options.rightKey, false);
        setPressed(options.jumpKey, false);
        setPressed(options.sneakKey, false);
        setPressed(options.sprintKey, false);
        setPressed(options.useKey, false);
        if (!keepAttack) {
            setPressed(options.attackKey, false);
        }
    }

    public static void releaseAll(MinecraftClient client) {
        stopMovement(client, false);
    }

    public static void leftClick(MinecraftClient client) {
        if (client == null) {
            return;
        }
        tapKey(client.options == null ? null : client.options.attackKey);
    }

    public static void rightClick(MinecraftClient client) {
        if (client == null) {
            return;
        }
        tapKey(client.options == null ? null : client.options.useKey);
    }

    public static void middleClick(MinecraftClient client) {
        if (client == null) {
            return;
        }
        tapKey(client.options == null ? null : client.options.pickItemKey);
    }

    public static void holdThese(MinecraftClient client, boolean withAttack, KeyBinding... keys) {
        if (client == null || client.options == null) {
            return;
        }
        releaseAllExcept(client, keys);
        if (keys != null) {
            for (KeyBinding key : keys) {
                setPressed(key, true);
            }
        }
        if (withAttack) {
            setPressed(client.options.attackKey, true);
        }
    }

    public static void holdThese(MinecraftClient client, KeyBinding... keys) {
        holdThese(client, false, keys);
    }

    public static void releaseAllExcept(MinecraftClient client, KeyBinding... keepPressed) {
        if (client == null || client.options == null) {
            return;
        }
        List<KeyBinding> keep = keepPressed == null ? List.of() : List.of(keepPressed);
        KeyBinding[] all = {
                client.options.attackKey,
                client.options.useKey,
                client.options.backKey,
                client.options.forwardKey,
                client.options.leftKey,
                client.options.rightKey,
                client.options.jumpKey,
                client.options.sneakKey,
                client.options.sprintKey
        };
        for (KeyBinding key : all) {
            if (!keep.contains(key)) {
                setPressed(key, false);
            }
        }
    }

    public static boolean areAllMovementKeysReleased(MinecraftClient client) {
        if (client == null || client.options == null) {
            return true;
        }
        return !client.options.forwardKey.isPressed()
                && !client.options.backKey.isPressed()
                && !client.options.leftKey.isPressed()
                && !client.options.rightKey.isPressed()
                && !client.options.jumpKey.isPressed();
    }

    public static List<KeyBinding> getHoldingKeybinds(MinecraftClient client) {
        if (client == null || client.options == null) {
            return List.of();
        }
        KeyBinding[] all = {
                client.options.attackKey,
                client.options.useKey,
                client.options.forwardKey,
                client.options.backKey,
                client.options.leftKey,
                client.options.rightKey,
                client.options.jumpKey,
                client.options.sneakKey,
                client.options.sprintKey
        };
        List<KeyBinding> pressed = new ArrayList<>();
        for (KeyBinding key : all) {
            if (key.isPressed()) {
                pressed.add(key);
            }
        }
        return pressed;
    }

    public static List<KeyBinding> getNeededKeyPresses(MinecraftClient client, Vec3d origin, Vec3d destination) {
        if (client == null || client.options == null || client.player == null || origin == null || destination == null) {
            return List.of();
        }
        double[] delta = {origin.x - destination.x, origin.z - destination.z};
        float requiredAngle = (float) (Math.atan2(delta[0], -delta[1]) * (180.0 / Math.PI));
        float angleDifference = AngleUtils.normalize180(requiredAngle - client.player.getYaw()) * -1.0f;
        return mapYawToKeys(client.options, angleDifference);
    }

    public static List<KeyBinding> getNeededKeyPresses(MinecraftClient client, float neededYaw) {
        if (client == null || client.options == null || client.player == null) {
            return List.of();
        }
        float relative = AngleUtils.normalize180(neededYaw - client.player.getYaw()) * -1.0f;
        return mapYawToKeys(client.options, relative);
    }

    public static List<KeyBinding> getOppositeKeys(MinecraftClient client, List<KeyBinding> keys) {
        if (client == null || client.options == null || keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<KeyBinding> opposite = new ArrayList<>();
        for (KeyBinding key : keys) {
            if (key == client.options.forwardKey) opposite.add(client.options.backKey);
            else if (key == client.options.backKey) opposite.add(client.options.forwardKey);
            else if (key == client.options.leftKey) opposite.add(client.options.rightKey);
            else if (key == client.options.rightKey) opposite.add(client.options.leftKey);
        }
        return opposite;
    }

    public static List<KeyBinding> getKeyPressesToDecelerate(MinecraftClient client, Vec3d origin, Vec3d destination) {
        return getOppositeKeys(client, getNeededKeyPresses(client, origin, destination));
    }

    private static List<KeyBinding> mapYawToKeys(GameOptions options, float relativeYaw) {
        List<KeyBinding> keys = new ArrayList<>();
        for (Map.Entry<Integer, java.util.function.Function<GameOptions, KeyBinding>> entry : YAW_KEY_MAP.entrySet()) {
            int yaw = entry.getKey();
            if (Math.abs(yaw - relativeYaw) < 67.5f || Math.abs(yaw - (relativeYaw + 360.0f)) < 67.5f) {
                keys.add(entry.getValue().apply(options));
            }
        }
        return keys;
    }

    private static void tapKey(KeyBinding keyBinding) {
        if (keyBinding == null) {
            return;
        }
        setPressed(keyBinding, true);
        InputUtil.Key bound = ((KeyBindingAccessor) (Object) keyBinding).farmhelper$getBoundKey();
        if (bound != null) {
            KeyBinding.onKeyPressed(bound);
            KeyBinding.setKeyPressed(bound, true);
            KeyBinding.setKeyPressed(bound, false);
        }
        setPressed(keyBinding, false);
    }
}

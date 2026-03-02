package com.jelly.farmhelper.fabric.util;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SignUtils {
    private SignUtils() {
    }

    public static List<String> readLines(BlockEntity blockEntity) {
        List<String> lines = new ArrayList<>();
        if (!(blockEntity instanceof SignBlockEntity sign)) {
            return lines;
        }
        for (Text text : sign.getFrontText().getMessages(false)) {
            lines.add(text.getString());
        }
        return lines;
    }

    public static List<String> readLinesAt(MinecraftClient client, BlockPos pos) {
        if (client == null || client.world == null || pos == null) {
            return List.of();
        }
        BlockEntity blockEntity = client.world.getBlockEntity(pos);
        return readLines(blockEntity);
    }

    public static boolean anyLineContains(MinecraftClient client, BlockPos pos, String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String probe = text.toLowerCase(Locale.ROOT);
        for (String line : readLinesAt(client, pos)) {
            if (line.toLowerCase(Locale.ROOT).contains(probe)) {
                return true;
            }
        }
        return false;
    }
}

package com.jelly.farmhelper.fabric.util;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class RenderUtils {
    private static final List<WorldBoxMarker> BOXES = new ArrayList<>();
    private static final List<WorldTracerMarker> TRACERS = new ArrayList<>();
    private static final List<WorldTextMarker> WORLD_TEXT = new ArrayList<>();

    private RenderUtils() {
    }

    public static void drawBlockBox(BlockPos pos, int color, long durationTicks) {
        if (pos == null) {
            return;
        }
        drawBox(new Box(pos), color, durationTicks);
    }

    public static void drawBox(Box box, int color, long durationTicks) {
        if (box == null) {
            return;
        }
        BOXES.add(new WorldBoxMarker(box, color, System.currentTimeMillis() + Math.max(1L, durationTicks) * 50L));
    }

    public static void drawTracer(Vec3d target, int color, long durationTicks) {
        if (target == null) {
            return;
        }
        TRACERS.add(new WorldTracerMarker(target, color, System.currentTimeMillis() + Math.max(1L, durationTicks) * 50L));
    }

    public static void drawText(Vec3d target, String text, int color, long durationTicks) {
        if (target == null || text == null || text.isBlank()) {
            return;
        }
        WORLD_TEXT.add(new WorldTextMarker(target, text, color, System.currentTimeMillis() + Math.max(1L, durationTicks) * 50L));
    }

    public static void drawCenterTopText(DrawContext context, String text, int y, int color) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (context == null || client == null || client.textRenderer == null || text == null) {
            return;
        }
        int width = client.textRenderer.getWidth(text);
        int x = Math.max(4, (client.getWindow().getScaledWidth() - width) / 2);
        context.drawText(client.textRenderer, text, x, Math.max(4, y), color, true);
    }

    public static void drawMultiLineText(DrawContext context, List<String> lines, int x, int y, int color) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (context == null || client == null || client.textRenderer == null || lines == null || lines.isEmpty()) {
            return;
        }
        int lineY = y;
        for (String line : lines) {
            context.drawText(client.textRenderer, line, x, lineY, color, true);
            lineY += 10;
        }
    }

    public static void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || context == null || context.matrices() == null || context.consumers() == null) {
            return;
        }

        MatrixStack matrices = context.matrices();
        Vec3d cameraPos = new Vec3d(client.player.getX(), client.player.getY() + client.player.getStandingEyeHeight(), client.player.getZ());
        VertexConsumer lines = context.consumers().getBuffer(RenderLayers.lines());

        for (WorldBoxMarker marker : BOXES) {
            VertexRendering.drawOutline(
                    matrices,
                    lines,
                    VoxelShapes.cuboid(marker.box),
                    -cameraPos.x,
                    -cameraPos.y,
                    -cameraPos.z,
                    marker.color,
                    2.0f
            );
        }

        Vec3d eyePos = cameraPos;
        for (WorldTracerMarker marker : TRACERS) {
            Box tracer = tracerToBox(eyePos, marker.target, 0.03);
            VertexRendering.drawOutline(
                    matrices,
                    lines,
                    VoxelShapes.cuboid(tracer),
                    -cameraPos.x,
                    -cameraPos.y,
                    -cameraPos.z,
                    marker.color,
                    1.4f
            );
        }
    }

    private static Box tracerToBox(Vec3d from, Vec3d to, double thickness) {
        double minX = Math.min(from.x, to.x) - thickness;
        double minY = Math.min(from.y, to.y) - thickness;
        double minZ = Math.min(from.z, to.z) - thickness;
        double maxX = Math.max(from.x, to.x) + thickness;
        double maxY = Math.max(from.y, to.y) + thickness;
        double maxZ = Math.max(from.z, to.z) + thickness;
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static void renderWorldTextAsHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (context == null || client == null || client.textRenderer == null || WORLD_TEXT.isEmpty()) {
            return;
        }
        int y = 60;
        for (WorldTextMarker marker : WORLD_TEXT) {
            context.drawText(client.textRenderer, Text.literal(marker.text), 8, y, marker.color, true);
            y += 10;
        }
    }

    public static void tickCleanup() {
        long now = System.currentTimeMillis();
        cleanup(now, BOXES);
        cleanup(now, TRACERS);
        cleanup(now, WORLD_TEXT);
    }

    private static <T extends ExpiringMarker> void cleanup(long now, List<T> markers) {
        for (Iterator<T> iterator = markers.iterator(); iterator.hasNext(); ) {
            if (iterator.next().expiresAtMs <= now) {
                iterator.remove();
            }
        }
    }

    private static class ExpiringMarker {
        protected final long expiresAtMs;

        private ExpiringMarker(long expiresAtMs) {
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static final class WorldBoxMarker extends ExpiringMarker {
        private final Box box;
        private final int color;

        private WorldBoxMarker(Box box, int color, long expiresAtMs) {
            super(expiresAtMs);
            this.box = box;
            this.color = color;
        }
    }

    private static final class WorldTracerMarker extends ExpiringMarker {
        private final Vec3d target;
        private final int color;

        private WorldTracerMarker(Vec3d target, int color, long expiresAtMs) {
            super(expiresAtMs);
            this.target = target;
            this.color = color;
        }
    }

    private static final class WorldTextMarker extends ExpiringMarker {
        private final Vec3d target;
        private final String text;
        private final int color;

        private WorldTextMarker(Vec3d target, String text, int color, long expiresAtMs) {
            super(expiresAtMs);
            this.target = target;
            this.text = text;
            this.color = color;
        }
    }
}

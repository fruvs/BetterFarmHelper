package com.jelly.farmhelper.fabric.automation;

import com.jelly.farmhelper.fabric.FarmHelperFabric;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class MovementRecordingPlayer {
    private record Step(
            boolean forward,
            boolean left,
            boolean backward,
            boolean right,
            boolean sneak,
            boolean sprint,
            boolean fly,
            boolean jump,
            boolean attack,
            float yaw,
            float pitch,
            int delayTicks
    ) {
    }

    private final Map<String, List<Step>> recordings = new HashMap<>();
    private final Random random = new Random();

    private boolean loaded;
    private boolean playing;
    private String activeRecording = "";
    private List<Step> activeSteps = List.of();
    private int stepIndex;
    private int stepTicks;
    private float yawOffset;

    public boolean isPlaying() {
        return playing;
    }

    public String activeRecordingName() {
        return activeRecording;
    }

    public boolean playRandomRecording(String pattern, MinecraftClient client) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        ensureLoaded();
        if (recordings.isEmpty()) {
            return false;
        }

        String normalized = pattern.toLowerCase(Locale.ROOT);
        List<Map.Entry<String, List<Step>>> candidates = new ArrayList<>();
        for (Map.Entry<String, List<Step>> entry : recordings.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).contains(normalized) && !entry.getValue().isEmpty()) {
                candidates.add(entry);
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }

        Map.Entry<String, List<Step>> selected = candidates.get(random.nextInt(candidates.size()));
        activeRecording = selected.getKey();
        activeSteps = selected.getValue();
        stepIndex = 0;
        stepTicks = 0;
        yawOffset = client.player == null || activeSteps.isEmpty() ? 0f : client.player.getYaw() - activeSteps.getFirst().yaw();
        playing = true;
        return true;
    }

    public void stop(MinecraftClient client) {
        releaseMovement(client);
        playing = false;
        activeRecording = "";
        activeSteps = List.of();
        stepIndex = 0;
        stepTicks = 0;
        yawOffset = 0f;
    }

    public void tick(MinecraftClient client) {
        if (!playing) {
            return;
        }
        if (client.player == null || client.options == null || activeSteps.isEmpty()) {
            stop(client);
            return;
        }
        if (stepIndex < 0 || stepIndex >= activeSteps.size()) {
            stop(client);
            return;
        }

        Step step = activeSteps.get(stepIndex);
        setKey(client.options.forwardKey, step.forward());
        setKey(client.options.backKey, step.backward());
        setKey(client.options.leftKey, step.left());
        setKey(client.options.rightKey, step.right());
        setKey(client.options.sneakKey, step.sneak());
        setKey(client.options.sprintKey, step.sprint());
        setKey(client.options.jumpKey, step.jump());
        setKey(client.options.attackKey, step.attack());

        if (client.player.getAbilities().allowFlying) {
            boolean wasFlying = client.player.getAbilities().flying;
            boolean wantFlying = step.fly();
            if (wasFlying != wantFlying) {
                client.player.getAbilities().flying = wantFlying;
                client.player.sendAbilitiesUpdate();
            }
        }
        float targetYaw = MathHelper.wrapDegrees(step.yaw() + yawOffset);
        float targetPitch = MathHelper.clamp(step.pitch(), -90f, 90f);
        client.player.setYaw(approachAngle(client.player.getYaw(), targetYaw, 14f));
        client.player.setPitch(approach(client.player.getPitch(), targetPitch, 10f));

        stepTicks++;
        if (stepTicks >= Math.max(1, step.delayTicks())) {
            stepTicks = 0;
            stepIndex++;
            if (stepIndex >= activeSteps.size()) {
                stop(client);
            }
        }
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        FabricLoader.getInstance().getModContainer(FarmHelperFabric.MOD_ID)
                .flatMap(container -> container.findPath("assets/farmhelperfabric/legacy/farmhelper/movrec"))
                .ifPresent(this::loadFromDirectory);
    }

    private void loadFromDirectory(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".movement"))
                    .forEach(this::loadSingleRecording);
        } catch (IOException e) {
            FarmHelperFabric.LOGGER.warn("Failed to load movement recordings from {}", root, e);
        }
    }

    private void loadSingleRecording(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            List<Step> steps = new ArrayList<>();
            for (String line : lines) {
                Step step = parseStep(line);
                if (step != null) {
                    steps.add(step);
                }
            }
            if (!steps.isEmpty()) {
                String key = Objects.toString(path.getFileName(), path.toString());
                recordings.put(key, List.copyOf(steps));
            }
        } catch (IOException e) {
            FarmHelperFabric.LOGGER.debug("Could not read movement recording {}", path, e);
        }
    }

    private Step parseStep(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] split = raw.split(";");
        if (split.length < 12) {
            return null;
        }
        try {
            return new Step(
                    Boolean.parseBoolean(split[0].trim()),
                    Boolean.parseBoolean(split[1].trim()),
                    Boolean.parseBoolean(split[2].trim()),
                    Boolean.parseBoolean(split[3].trim()),
                    Boolean.parseBoolean(split[4].trim()),
                    Boolean.parseBoolean(split[5].trim()),
                    Boolean.parseBoolean(split[6].trim()),
                    Boolean.parseBoolean(split[7].trim()),
                    Boolean.parseBoolean(split[8].trim()),
                    Float.parseFloat(split[9].trim()),
                    Float.parseFloat(split[10].trim()),
                    Integer.parseInt(split[11].trim())
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void releaseMovement(MinecraftClient client) {
        if (client == null || client.options == null) {
            return;
        }
        setKey(client.options.forwardKey, false);
        setKey(client.options.backKey, false);
        setKey(client.options.leftKey, false);
        setKey(client.options.rightKey, false);
        setKey(client.options.sneakKey, false);
        setKey(client.options.sprintKey, false);
        setKey(client.options.jumpKey, false);
        setKey(client.options.attackKey, false);
    }

    private void setKey(KeyBinding key, boolean pressed) {
        if (key != null) {
            key.setPressed(pressed);
        }
    }

    private float approach(float current, float target, float maxStep) {
        float delta = target - current;
        if (Math.abs(delta) <= maxStep) {
            return target;
        }
        return current + Math.copySign(maxStep, delta);
    }

    private float approachAngle(float current, float target, float maxStep) {
        float delta = MathHelper.wrapDegrees(target - current);
        if (Math.abs(delta) <= maxStep) {
            return target;
        }
        return current + Math.copySign(maxStep, delta);
    }
}

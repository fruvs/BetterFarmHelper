package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.macro.LegacyMacroType;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;
import net.minecraft.util.math.MathHelper;

import java.util.Optional;

public class RotationDetector implements FailsafeDetector {
    private long pendingSinceTick = -1L;
    private float pendingOriginYaw;
    private float pendingOriginPitch;
    private float pendingPacketYawDelta;
    private float pendingPacketPitchDelta;

    @Override
    public FailsafeType type() {
        return FailsafeType.ROTATION_CHECK;
    }

    @Override
    public Optional<String> detect(RuntimeSnapshot snapshot, FarmHelperConfig config, DetectorState state) {
        if (snapshot.macroState != MacroState.FARMING) {
            clearPending();
            return Optional.empty();
        }
        if (snapshot.macroRuntimeTicks < 40L) {
            clearPending();
            return Optional.empty();
        }
        if (snapshot.movementRecordingPlaying) {
            clearPending();
            return Optional.empty();
        }
        if (config.enablePacketFailsafeChecks) {
            if (state.packetRotationSuppressed || state.packetTeleportSuppressed || snapshot.networkLagging || snapshot.screenOpen) {
                clearPending();
                return Optional.empty();
            }
            float packetYawLimit = Math.max(2f, config.yawSensitivity);
            float packetPitchLimit = Math.max(2f, config.pitchSensitivity);
            if (state.packetPositionLookSeen
                    && !(Math.abs(state.packetYawDelta - 360f) < 0.01f && state.packetPitchDelta < 0.01f)
                    && (state.packetYawDelta > packetYawLimit || state.packetPitchDelta > packetPitchLimit)) {
                registerPending(snapshot.tickCount, state.packetRotationOriginYaw, state.packetRotationOriginPitch,
                        state.packetYawDelta, state.packetPitchDelta);
            }
            if (state.packetRotationSeen
                    && !(Math.abs(state.packetRotationYawDelta - 360f) < 0.01f && state.packetRotationPitchDelta < 0.01f)
                    && (state.packetRotationYawDelta > packetYawLimit || state.packetRotationPitchDelta > packetPitchLimit)) {
                registerPending(snapshot.tickCount, state.packetRotationOriginYaw, state.packetRotationOriginPitch,
                        state.packetRotationYawDelta, state.packetRotationPitchDelta);
            }

            long confirmTicks = Math.max(2L, config.detectionTimeWindowMs / 50L);
            if (pendingSinceTick >= 0) {
                long elapsed = snapshot.tickCount - pendingSinceTick;
                if (elapsed >= confirmTicks) {
                    float yawFromOrigin = Math.abs(MathHelper.wrapDegrees(snapshot.yaw - pendingOriginYaw));
                    float pitchFromOrigin = Math.abs(MathHelper.wrapDegrees(snapshot.pitch - pendingOriginPitch));
                    float packetYaw = pendingPacketYawDelta;
                    float packetPitch = pendingPacketPitchDelta;
                    clearPending();
                    if (yawFromOrigin > packetYawLimit || pitchFromOrigin > packetPitchLimit) {
                        return Optional.of("Confirmed packet rotation delta yaw=" + String.format("%.1f", packetYaw)
                                + " pitch=" + String.format("%.1f", packetPitch));
                    }
                } else {
                    return Optional.empty();
                }
            }
        }
        float yawLimit = Math.max(1f, config.yawSensitivity) * 5f;
        float pitchLimit = Math.max(1f, config.pitchSensitivity) * 5f;
        if (config.macroType == LegacyMacroType.S_MUSHROOM_ROTATE && Math.abs(snapshot.yawDelta) <= yawLimit * 2.5f) {
            return Optional.empty();
        }
        if (snapshot.macroRuntimeTicks < 100L && config.rotateAfterWarped) {
            return Optional.empty();
        }
        if (!snapshot.screenOpen && !snapshot.networkLagging
                && (Math.abs(snapshot.yawDelta) > yawLimit || Math.abs(snapshot.pitchDelta) > pitchLimit)) {
            return Optional.of("Abrupt rotation delta yaw=" + String.format("%.1f", snapshot.yawDelta)
                    + " pitch=" + String.format("%.1f", snapshot.pitchDelta));
        }
        return Optional.empty();
    }

    private void registerPending(long tick, float originYaw, float originPitch, float packetYawDelta, float packetPitchDelta) {
        pendingSinceTick = tick;
        pendingOriginYaw = originYaw;
        pendingOriginPitch = originPitch;
        pendingPacketYawDelta = packetYawDelta;
        pendingPacketPitchDelta = packetPitchDelta;
    }

    private void clearPending() {
        pendingSinceTick = -1L;
        pendingOriginYaw = 0f;
        pendingOriginPitch = 0f;
        pendingPacketYawDelta = 0f;
        pendingPacketPitchDelta = 0f;
    }
}

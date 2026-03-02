package com.jelly.farmhelper.fabric.failsafe.detector;

import com.jelly.farmhelper.fabric.config.FarmHelperConfig;
import com.jelly.farmhelper.fabric.failsafe.FailsafeType;
import com.jelly.farmhelper.fabric.macro.MacroState;
import com.jelly.farmhelper.fabric.runtime.RuntimeSnapshot;

import java.util.ArrayDeque;
import java.util.Optional;

public class TeleportDetector implements FailsafeDetector {
    private long pendingSinceTick = -1L;
    private double pendingOriginX;
    private double pendingOriginY;
    private double pendingOriginZ;
    private double pendingTargetX;
    private double pendingTargetY;
    private double pendingTargetZ;
    private double pendingPacketDistance;
    private boolean pendingLikelyLagBack;

    private long persistenceUntilTick = -1L;
    private double persistenceOriginX;
    private double persistenceOriginY;
    private double persistenceOriginZ;
    private double persistenceTargetX;
    private double persistenceTargetY;
    private double persistenceTargetZ;
    private double persistencePacketDistance;

    private final ArrayDeque<HistorySample> recentPositions = new ArrayDeque<>();

    @Override
    public FailsafeType type() {
        return FailsafeType.TELEPORT_CHECK;
    }

    @Override
    public Optional<String> detect(RuntimeSnapshot snapshot, FarmHelperConfig config, DetectorState state) {
        trackPositionHistory(snapshot.tickCount, snapshot.posX, snapshot.posY, snapshot.posZ);
        if (snapshot.macroState != MacroState.FARMING) {
            clearPending();
            clearPersistence();
            return Optional.empty();
        }
        if (snapshot.macroRuntimeTicks < 40L) {
            clearPending();
            clearPersistence();
            return Optional.empty();
        }
        if (hasPersistenceCheck()) {
            Optional<String> persistentResult = evaluatePersistence(snapshot, config);
            if (persistentResult.isPresent()) {
                clearPending();
                clearPersistence();
                return persistentResult;
            }
            if (!hasPersistenceCheck()) {
                return Optional.empty();
            }
            return Optional.empty();
        }

        if (config.enablePacketFailsafeChecks
                && !state.packetTeleportSuppressed
                && !snapshot.networkLagging
                && !snapshot.screenOpen
                && state.packetPositionLookSeen) {
            double packetThreshold = Math.max(1.0, config.teleportDistanceThreshold);
            if (state.packetTeleportDistance > packetThreshold) {
                double dy = Math.abs(state.packetTeleportTargetY - state.packetTeleportOriginY);
                if (dy > 8.0 && Math.abs(snapshot.verticalVelocity) > 0.35) {
                    return Optional.empty();
                }
                pendingSinceTick = snapshot.tickCount;
                pendingOriginX = state.packetTeleportOriginX;
                pendingOriginY = state.packetTeleportOriginY;
                pendingOriginZ = state.packetTeleportOriginZ;
                pendingTargetX = state.packetTeleportTargetX;
                pendingTargetY = state.packetTeleportTargetY;
                pendingTargetZ = state.packetTeleportTargetZ;
                pendingPacketDistance = state.packetTeleportDistance;
                pendingLikelyLagBack = isLikelyLagBackPosition(state.packetTeleportTargetX, state.packetTeleportTargetY, state.packetTeleportTargetZ);
            }
        }

        if (pendingSinceTick >= 0) {
            long confirmTicks = Math.max(2L, config.detectionTimeWindowMs / 50L);
            long elapsed = snapshot.tickCount - pendingSinceTick;
            if (elapsed >= confirmTicks) {
                double threshold = Math.max(1.0, config.teleportDistanceThreshold);
                double targetSlack = threshold + Math.max(1.0, config.teleportLagTolerance * 2.0);
                double currentToOrigin = distance(snapshot.posX, snapshot.posY, snapshot.posZ,
                        pendingOriginX, pendingOriginY, pendingOriginZ);
                double currentToTarget = distance(snapshot.posX, snapshot.posY, snapshot.posZ,
                        pendingTargetX, pendingTargetY, pendingTargetZ);
                double packetDistance = pendingPacketDistance;
                boolean likelyLagBack = pendingLikelyLagBack;
                double originX = pendingOriginX;
                double originY = pendingOriginY;
                double originZ = pendingOriginZ;
                double targetX = pendingTargetX;
                double targetY = pendingTargetY;
                double targetZ = pendingTargetZ;
                double dx = pendingTargetX - pendingOriginX;
                double dy = pendingTargetY - pendingOriginY;
                double dz = pendingTargetZ - pendingOriginZ;
                clearPending();
                if (likelyLagBack && packetDistance < Math.max(3.0, threshold + 1.0)) {
                    return Optional.empty();
                }
                if (isSameDirectionMovement(snapshot.velocityX, snapshot.velocityZ, dx, dz, originY, targetY, dy)) {
                    return Optional.empty();
                }
                if (currentToOrigin > threshold && currentToTarget <= targetSlack) {
                    beginPersistenceCheck(
                            snapshot.tickCount,
                            config,
                            originX,
                            originY,
                            originZ,
                            targetX,
                            targetY,
                            targetZ,
                            packetDistance
                    );
                }
            } else {
                return Optional.empty();
            }
        }
        double threshold = Math.max(1.0, config.teleportDistanceThreshold + config.teleportLagTolerance);
        if (!snapshot.networkLagging && !snapshot.screenOpen && snapshot.movedDistance > threshold) {
            return Optional.of("Unexpected position delta: " + String.format("%.2f", snapshot.movedDistance));
        }
        return Optional.empty();
    }

    private void beginPersistenceCheck(
            long nowTick,
            FarmHelperConfig config,
            double originX,
            double originY,
            double originZ,
            double targetX,
            double targetY,
            double targetZ,
            double packetDistance
    ) {
        long persistenceTicks = Math.max(3L, config.detectionTimeWindowMs / 75L);
        persistenceUntilTick = nowTick + persistenceTicks;
        persistenceOriginX = originX;
        persistenceOriginY = originY;
        persistenceOriginZ = originZ;
        persistenceTargetX = targetX;
        persistenceTargetY = targetY;
        persistenceTargetZ = targetZ;
        persistencePacketDistance = packetDistance;
    }

    private Optional<String> evaluatePersistence(RuntimeSnapshot snapshot, FarmHelperConfig config) {
        if (!hasPersistenceCheck()) {
            return Optional.empty();
        }
        if (snapshot.networkLagging || snapshot.screenOpen) {
            clearPersistence();
            return Optional.empty();
        }

        double threshold = Math.max(1.0, config.teleportDistanceThreshold);
        double targetSlack = threshold + Math.max(1.0, config.teleportLagTolerance * 2.0);
        double currentToOrigin = distance(snapshot.posX, snapshot.posY, snapshot.posZ,
                persistenceOriginX, persistenceOriginY, persistenceOriginZ);
        double currentToTarget = distance(snapshot.posX, snapshot.posY, snapshot.posZ,
                persistenceTargetX, persistenceTargetY, persistenceTargetZ);

        if (currentToOrigin <= threshold || currentToTarget > targetSlack + Math.max(0.5, config.teleportLagTolerance * 3.0)) {
            clearPersistence();
            return Optional.empty();
        }

        if (snapshot.tickCount >= persistenceUntilTick) {
            return Optional.of("Confirmed persistent teleport packet distance="
                    + String.format("%.2f", persistencePacketDistance)
                    + " drift=" + String.format("%.2f", currentToOrigin));
        }
        return Optional.empty();
    }

    private double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void clearPending() {
        pendingSinceTick = -1L;
        pendingOriginX = 0.0;
        pendingOriginY = 0.0;
        pendingOriginZ = 0.0;
        pendingTargetX = 0.0;
        pendingTargetY = 0.0;
        pendingTargetZ = 0.0;
        pendingPacketDistance = 0.0;
        pendingLikelyLagBack = false;
    }

    private boolean hasPersistenceCheck() {
        return persistenceUntilTick >= 0L;
    }

    private void clearPersistence() {
        persistenceUntilTick = -1L;
        persistenceOriginX = 0.0;
        persistenceOriginY = 0.0;
        persistenceOriginZ = 0.0;
        persistenceTargetX = 0.0;
        persistenceTargetY = 0.0;
        persistenceTargetZ = 0.0;
        persistencePacketDistance = 0.0;
    }

    private void trackPositionHistory(long tick, double x, double y, double z) {
        recentPositions.addLast(new HistorySample(tick, x, y, z));
        while (recentPositions.size() > 220) {
            recentPositions.pollFirst();
        }
        while (!recentPositions.isEmpty() && tick - recentPositions.peekFirst().tick > 220L) {
            recentPositions.pollFirst();
        }
    }

    private boolean isLikelyLagBackPosition(double x, double y, double z) {
        for (HistorySample sample : recentPositions) {
            if (Math.abs(sample.y - y) > 2.0) {
                continue;
            }
            if (distance(sample.x, sample.y, sample.z, x, y, z) <= 1.25) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameDirectionMovement(double velX, double velZ, double dx, double dz, double originY, double targetY, double dy) {
        double dot = velX * dx + velZ * dz;
        return dot > 0
                && originY <= targetY
                && Math.abs(dy) < 1.0
                && (Math.abs(dx) < 1.0 || Math.abs(dz) < 1.0);
    }

    private record HistorySample(long tick, double x, double y, double z) {
    }
}

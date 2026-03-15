package com.jelly.farmhelper.fabric.failsafe;

public enum FailsafeType {
    BAD_EFFECTS(1),
    BANWAVE(6),
    BEDROCK_CAGE(1),
    COBWEB(3),
    DIRT(3),
    DISCONNECT(1),
    EVACUATE(1),
    FULL_INVENTORY(3),
    GUEST_VISIT(1),
    ITEM_CHANGE(3),
    JACOB(7),
    KNOCKBACK(4),
    TELEPORT_CHECK(5),
    ROTATION_CHECK(4),
    WORLD_CHANGE(2),
    LOW_BPS(9),
    MANUAL_TEST(9);

    private final int priority;

    FailsafeType(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}

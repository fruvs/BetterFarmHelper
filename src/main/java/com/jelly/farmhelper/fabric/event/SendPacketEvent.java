package com.jelly.farmhelper.fabric.event;

import net.minecraft.network.packet.Packet;

public final class SendPacketEvent {
    public final Packet<?> packet;

    public SendPacketEvent(Packet<?> packet) {
        this.packet = packet;
    }
}

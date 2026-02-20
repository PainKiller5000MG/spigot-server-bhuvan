package net.minecraft.network.protocol;

import net.minecraft.resources.Identifier;

public record PacketType<T extends Packet<?>>(PacketFlow flow, Identifier id) {

    public String toString() {
        String s = this.flow.id();

        return s + "/" + String.valueOf(this.id);
    }
}

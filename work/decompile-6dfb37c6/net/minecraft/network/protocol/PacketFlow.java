package net.minecraft.network.protocol;

public enum PacketFlow {

    SERVERBOUND("serverbound"), CLIENTBOUND("clientbound");

    private final String id;

    private PacketFlow(String id) {
        this.id = id;
    }

    public PacketFlow getOpposite() {
        return this == PacketFlow.CLIENTBOUND ? PacketFlow.SERVERBOUND : PacketFlow.CLIENTBOUND;
    }

    public String id() {
        return this.id;
    }
}

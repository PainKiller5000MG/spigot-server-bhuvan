package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.entity.player.Abilities;

public class ServerboundPlayerAbilitiesPacket implements Packet<ServerGamePacketListener> {

    public static final StreamCodec<FriendlyByteBuf, ServerboundPlayerAbilitiesPacket> STREAM_CODEC = Packet.<FriendlyByteBuf, ServerboundPlayerAbilitiesPacket>codec(ServerboundPlayerAbilitiesPacket::write, ServerboundPlayerAbilitiesPacket::new);
    private static final int FLAG_FLYING = 2;
    private final boolean isFlying;

    public ServerboundPlayerAbilitiesPacket(Abilities abilities) {
        this.isFlying = abilities.flying;
    }

    private ServerboundPlayerAbilitiesPacket(FriendlyByteBuf input) {
        byte b0 = input.readByte();

        this.isFlying = (b0 & 2) != 0;
    }

    private void write(FriendlyByteBuf output) {
        byte b0 = 0;

        if (this.isFlying) {
            b0 = (byte) (b0 | 2);
        }

        output.writeByte(b0);
    }

    @Override
    public PacketType<ServerboundPlayerAbilitiesPacket> type() {
        return GamePacketTypes.SERVERBOUND_PLAYER_ABILITIES;
    }

    public void handle(ServerGamePacketListener listener) {
        listener.handlePlayerAbilities(this);
    }

    public boolean isFlying() {
        return this.isFlying;
    }
}

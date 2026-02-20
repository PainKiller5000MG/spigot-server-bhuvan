package net.minecraft.network.protocol.login;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import net.minecraft.network.protocol.login.custom.DiscardedQueryAnswerPayload;
import org.jspecify.annotations.Nullable;

public record ServerboundCustomQueryAnswerPacket(int transactionId, @Nullable CustomQueryAnswerPayload payload) implements Packet<ServerLoginPacketListener> {

    public static final StreamCodec<FriendlyByteBuf, ServerboundCustomQueryAnswerPacket> STREAM_CODEC = Packet.<FriendlyByteBuf, ServerboundCustomQueryAnswerPacket>codec(ServerboundCustomQueryAnswerPacket::write, ServerboundCustomQueryAnswerPacket::read);
    private static final int MAX_PAYLOAD_SIZE = 1048576;

    private static ServerboundCustomQueryAnswerPacket read(FriendlyByteBuf input) {
        int i = input.readVarInt();

        return new ServerboundCustomQueryAnswerPacket(i, readPayload(i, input));
    }

    private static CustomQueryAnswerPayload readPayload(int transactionId, FriendlyByteBuf input) {
        return readUnknownPayload(input);
    }

    private static CustomQueryAnswerPayload readUnknownPayload(FriendlyByteBuf input) {
        int i = input.readableBytes();

        if (i >= 0 && i <= 1048576) {
            input.skipBytes(i);
            return DiscardedQueryAnswerPayload.INSTANCE;
        } else {
            throw new IllegalArgumentException("Payload may not be larger than 1048576 bytes");
        }
    }

    private void write(FriendlyByteBuf output) {
        output.writeVarInt(this.transactionId);
        output.writeNullable(this.payload, (friendlybytebuf1, customqueryanswerpayload) -> {
            customqueryanswerpayload.write(friendlybytebuf1);
        });
    }

    @Override
    public PacketType<ServerboundCustomQueryAnswerPacket> type() {
        return LoginPacketTypes.SERVERBOUND_CUSTOM_QUERY_ANSWER;
    }

    public void handle(ServerLoginPacketListener listener) {
        listener.handleCustomQueryPacket(this);
    }
}

package net.minecraft.network.protocol.common.custom;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public record DiscardedPayload(Identifier id) implements CustomPacketPayload {

    public static <T extends FriendlyByteBuf> StreamCodec<T, DiscardedPayload> codec(Identifier id, int maxPayloadSize) {
        return CustomPacketPayload.codec((discardedpayload, friendlybytebuf) -> {
        }, (friendlybytebuf) -> {
            int j = friendlybytebuf.readableBytes();

            if (j >= 0 && j <= maxPayloadSize) {
                friendlybytebuf.skipBytes(j);
                return new DiscardedPayload(id);
            } else {
                throw new IllegalArgumentException("Payload may not be larger than " + maxPayloadSize + " bytes");
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<DiscardedPayload> type() {
        return new CustomPacketPayload.Type<DiscardedPayload>(this.id);
    }
}

package net.minecraft.network.protocol.common.custom;

import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.resources.Identifier;

public interface CustomPacketPayload {

    CustomPacketPayload.Type<? extends CustomPacketPayload> type();

    static <B extends ByteBuf, T extends CustomPacketPayload> StreamCodec<B, T> codec(StreamMemberEncoder<B, T> writer, StreamDecoder<B, T> reader) {
        return StreamCodec.<B, T>ofMember(writer, reader);
    }

    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> createType(String id) {
        return new CustomPacketPayload.Type<T>(Identifier.withDefaultNamespace(id));
    }

    static <B extends FriendlyByteBuf> StreamCodec<B, CustomPacketPayload> codec(final CustomPacketPayload.FallbackProvider<B> fallback, List<CustomPacketPayload.TypeAndCodec<? super B, ?>> types) {
        final Map<Identifier, StreamCodec<? super B, ? extends CustomPacketPayload>> map = (Map) types.stream().collect(Collectors.toUnmodifiableMap((custompacketpayload_typeandcodec) -> {
            return custompacketpayload_typeandcodec.type().id();
        }, CustomPacketPayload.TypeAndCodec::codec));

        return new StreamCodec<B, CustomPacketPayload>() {
            private StreamCodec<? super B, ? extends CustomPacketPayload> findCodec(Identifier typeId) {
                StreamCodec<? super B, ? extends CustomPacketPayload> streamcodec = (StreamCodec) map.get(typeId);

                return streamcodec != null ? streamcodec : fallback.create(typeId);
            }

            private <T extends CustomPacketPayload> void writeCap(B output, CustomPacketPayload.Type<T> type, CustomPacketPayload payload) {
                output.writeIdentifier(type.id());
                StreamCodec<B, T> streamcodec = this.findCodec(type.id);

                streamcodec.encode(output, payload);
            }

            public void encode(B output, CustomPacketPayload value) {
                this.writeCap(output, value.type(), value);
            }

            public CustomPacketPayload decode(B input) {
                Identifier identifier = input.readIdentifier();

                return (CustomPacketPayload) this.findCodec(identifier).decode(input);
            }
        };
    }

    public static record Type<T extends CustomPacketPayload>(Identifier id) {

    }

    public static record TypeAndCodec<B extends FriendlyByteBuf, T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, StreamCodec<B, T> codec) {

    }

    public interface FallbackProvider<B extends FriendlyByteBuf> {

        StreamCodec<B, ? extends CustomPacketPayload> create(Identifier typeId);
    }
}

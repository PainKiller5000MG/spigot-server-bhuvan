package net.minecraft.network.protocol;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.ClientboundPacketListener;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.ServerboundPacketListener;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import org.jspecify.annotations.Nullable;

public class ProtocolInfoBuilder<T extends PacketListener, B extends ByteBuf, C> {

    private final ConnectionProtocol protocol;
    private final PacketFlow flow;
    private final List<ProtocolInfoBuilder.CodecEntry<T, ?, B, C>> codecs = new ArrayList();
    private @Nullable BundlerInfo bundlerInfo;

    public ProtocolInfoBuilder(ConnectionProtocol protocol, PacketFlow flow) {
        this.protocol = protocol;
        this.flow = flow;
    }

    public <P extends Packet<? super T>> ProtocolInfoBuilder<T, B, C> addPacket(PacketType<P> type, StreamCodec<? super B, P> serializer) {
        this.codecs.add(new ProtocolInfoBuilder.CodecEntry(type, serializer, (CodecModifier) null));
        return this;
    }

    public <P extends Packet<? super T>> ProtocolInfoBuilder<T, B, C> addPacket(PacketType<P> type, StreamCodec<? super B, P> serializer, CodecModifier<B, P, C> modifier) {
        this.codecs.add(new ProtocolInfoBuilder.CodecEntry(type, serializer, modifier));
        return this;
    }

    public <P extends BundlePacket<? super T>, D extends BundleDelimiterPacket<? super T>> ProtocolInfoBuilder<T, B, C> withBundlePacket(PacketType<P> bundlerPacket, Function<Iterable<Packet<? super T>>, P> constructor, D delimiterPacket) {
        StreamCodec<ByteBuf, D> streamcodec = StreamCodec.<ByteBuf, D>unit(delimiterPacket);
        PacketType<D> packettype1 = delimiterPacket.type();

        this.codecs.add(new ProtocolInfoBuilder.CodecEntry(packettype1, streamcodec, (CodecModifier) null));
        this.bundlerInfo = BundlerInfo.createForPacket(bundlerPacket, constructor, delimiterPacket);
        return this;
    }

    private StreamCodec<ByteBuf, Packet<? super T>> buildPacketCodec(Function<ByteBuf, B> contextWrapper, List<ProtocolInfoBuilder.CodecEntry<T, ?, B, C>> codecs, C context) {
        ProtocolCodecBuilder<ByteBuf, T> protocolcodecbuilder = new ProtocolCodecBuilder<ByteBuf, T>(this.flow);

        for (ProtocolInfoBuilder.CodecEntry<T, ?, B, C> protocolinfobuilder_codecentry : codecs) {
            protocolinfobuilder_codecentry.addToBuilder(protocolcodecbuilder, contextWrapper, context);
        }

        return protocolcodecbuilder.build();
    }

    private static ProtocolInfo.Details buildDetails(final ConnectionProtocol protocol, final PacketFlow flow, final List<? extends ProtocolInfoBuilder.CodecEntry<?, ?, ?, ?>> codecs) {
        return new ProtocolInfo.Details() {
            @Override
            public ConnectionProtocol id() {
                return protocol;
            }

            @Override
            public PacketFlow flow() {
                return flow;
            }

            @Override
            public void listPackets(ProtocolInfo.Details.PacketVisitor output) {
                for (int i = 0; i < codecs.size(); ++i) {
                    ProtocolInfoBuilder.CodecEntry<?, ?, ?, ?> protocolinfobuilder_codecentry = (ProtocolInfoBuilder.CodecEntry) codecs.get(i);

                    output.accept(protocolinfobuilder_codecentry.type, i);
                }

            }
        };
    }

    public SimpleUnboundProtocol<T, B> buildUnbound(final C context) {
        final List<ProtocolInfoBuilder.CodecEntry<T, ?, B, C>> list = List.copyOf(this.codecs);
        final BundlerInfo bundlerinfo = this.bundlerInfo;
        final ProtocolInfo.Details protocolinfo_details = buildDetails(this.protocol, this.flow, list);

        return new SimpleUnboundProtocol<T, B>() {
            @Override
            public ProtocolInfo<T> bind(Function<ByteBuf, B> contextWrapper) {
                return new ProtocolInfoBuilder.Implementation<T>(ProtocolInfoBuilder.this.protocol, ProtocolInfoBuilder.this.flow, ProtocolInfoBuilder.this.buildPacketCodec(contextWrapper, list, context), bundlerinfo);
            }

            @Override
            public ProtocolInfo.Details details() {
                return protocolinfo_details;
            }
        };
    }

    public UnboundProtocol<T, B, C> buildUnbound() {
        final List<ProtocolInfoBuilder.CodecEntry<T, ?, B, C>> list = List.copyOf(this.codecs);
        final BundlerInfo bundlerinfo = this.bundlerInfo;
        final ProtocolInfo.Details protocolinfo_details = buildDetails(this.protocol, this.flow, list);

        return new UnboundProtocol<T, B, C>() {
            @Override
            public ProtocolInfo<T> bind(Function<ByteBuf, B> contextWrapper, C context) {
                return new ProtocolInfoBuilder.Implementation<T>(ProtocolInfoBuilder.this.protocol, ProtocolInfoBuilder.this.flow, ProtocolInfoBuilder.this.buildPacketCodec(contextWrapper, list, context), bundlerinfo);
            }

            @Override
            public ProtocolInfo.Details details() {
                return protocolinfo_details;
            }
        };
    }

    private static <L extends PacketListener, B extends ByteBuf> SimpleUnboundProtocol<L, B> protocol(ConnectionProtocol id, PacketFlow flow, Consumer<ProtocolInfoBuilder<L, B, Unit>> config) {
        ProtocolInfoBuilder<L, B, Unit> protocolinfobuilder = new ProtocolInfoBuilder<L, B, Unit>(id, flow);

        config.accept(protocolinfobuilder);
        return protocolinfobuilder.buildUnbound(Unit.INSTANCE);
    }

    public static <T extends ServerboundPacketListener, B extends ByteBuf> SimpleUnboundProtocol<T, B> serverboundProtocol(ConnectionProtocol id, Consumer<ProtocolInfoBuilder<T, B, Unit>> config) {
        return protocol(id, PacketFlow.SERVERBOUND, config);
    }

    public static <T extends ClientboundPacketListener, B extends ByteBuf> SimpleUnboundProtocol<T, B> clientboundProtocol(ConnectionProtocol id, Consumer<ProtocolInfoBuilder<T, B, Unit>> config) {
        return protocol(id, PacketFlow.CLIENTBOUND, config);
    }

    private static <L extends PacketListener, B extends ByteBuf, C> UnboundProtocol<L, B, C> contextProtocol(ConnectionProtocol id, PacketFlow flow, Consumer<ProtocolInfoBuilder<L, B, C>> config) {
        ProtocolInfoBuilder<L, B, C> protocolinfobuilder = new ProtocolInfoBuilder<L, B, C>(id, flow);

        config.accept(protocolinfobuilder);
        return protocolinfobuilder.buildUnbound();
    }

    public static <T extends ServerboundPacketListener, B extends ByteBuf, C> UnboundProtocol<T, B, C> contextServerboundProtocol(ConnectionProtocol id, Consumer<ProtocolInfoBuilder<T, B, C>> config) {
        return contextProtocol(id, PacketFlow.SERVERBOUND, config);
    }

    public static <T extends ClientboundPacketListener, B extends ByteBuf, C> UnboundProtocol<T, B, C> contextClientboundProtocol(ConnectionProtocol id, Consumer<ProtocolInfoBuilder<T, B, C>> config) {
        return contextProtocol(id, PacketFlow.CLIENTBOUND, config);
    }

    private static record CodecEntry<T extends PacketListener, P extends Packet<? super T>, B extends ByteBuf, C>(PacketType<P> type, StreamCodec<? super B, P> serializer, @Nullable CodecModifier<B, P, C> modifier) {

        public void addToBuilder(ProtocolCodecBuilder<ByteBuf, T> codecBuilder, Function<ByteBuf, B> contextWrapper, C context) {
            StreamCodec<? super B, P> streamcodec;

            if (this.modifier != null) {
                streamcodec = this.modifier.apply(this.serializer, context);
            } else {
                streamcodec = this.serializer;
            }

            StreamCodec<ByteBuf, P> streamcodec1 = streamcodec.mapStream(contextWrapper);

            codecBuilder.add(this.type, streamcodec1);
        }
    }

    private static record Implementation<L extends PacketListener>(ConnectionProtocol id, PacketFlow flow, StreamCodec<ByteBuf, Packet<? super L>> codec, @Nullable BundlerInfo bundlerInfo) implements ProtocolInfo<L> {

    }
}

package net.minecraft.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.VarInt;

public class IdDispatchCodec<B extends ByteBuf, V, T> implements StreamCodec<B, V> {

    private static final int UNKNOWN_TYPE = -1;
    private final Function<V, ? extends T> typeGetter;
    private final List<IdDispatchCodec.Entry<B, V, T>> byId;
    private final Object2IntMap<T> toId;

    private IdDispatchCodec(Function<V, ? extends T> typeGetter, List<IdDispatchCodec.Entry<B, V, T>> byId, Object2IntMap<T> toId) {
        this.typeGetter = typeGetter;
        this.byId = byId;
        this.toId = toId;
    }

    public V decode(B input) {
        int i = VarInt.read(input);

        if (i >= 0 && i < this.byId.size()) {
            IdDispatchCodec.Entry<B, V, T> iddispatchcodec_entry = (IdDispatchCodec.Entry) this.byId.get(i);

            try {
                return (V) iddispatchcodec_entry.serializer.decode(input);
            } catch (Exception exception) {
                if (exception instanceof IdDispatchCodec.DontDecorateException) {
                    throw exception;
                } else {
                    throw new DecoderException("Failed to decode packet '" + String.valueOf(iddispatchcodec_entry.type) + "'", exception);
                }
            }
        } else {
            throw new DecoderException("Received unknown packet id " + i);
        }
    }

    public void encode(B output, V value) {
        T t0 = (T) this.typeGetter.apply(value);
        int i = this.toId.getOrDefault(t0, -1);

        if (i == -1) {
            throw new EncoderException("Sending unknown packet '" + String.valueOf(t0) + "'");
        } else {
            VarInt.write(output, i);
            IdDispatchCodec.Entry<B, V, T> iddispatchcodec_entry = (IdDispatchCodec.Entry) this.byId.get(i);

            try {
                StreamCodec<? super B, V> streamcodec = iddispatchcodec_entry.serializer;

                streamcodec.encode(output, value);
            } catch (Exception exception) {
                if (exception instanceof IdDispatchCodec.DontDecorateException) {
                    throw exception;
                } else {
                    throw new EncoderException("Failed to encode packet '" + String.valueOf(t0) + "'", exception);
                }
            }
        }
    }

    public static <B extends ByteBuf, V, T> IdDispatchCodec.Builder<B, V, T> builder(Function<V, ? extends T> typeGetter) {
        return new IdDispatchCodec.Builder<B, V, T>(typeGetter);
    }

    public static class Builder<B extends ByteBuf, V, T> {

        private final List<IdDispatchCodec.Entry<B, V, T>> entries = new ArrayList();
        private final Function<V, ? extends T> typeGetter;

        private Builder(Function<V, ? extends T> typeGetter) {
            this.typeGetter = typeGetter;
        }

        public IdDispatchCodec.Builder<B, V, T> add(T type, StreamCodec<? super B, ? extends V> serializer) {
            this.entries.add(new IdDispatchCodec.Entry(serializer, type));
            return this;
        }

        public IdDispatchCodec<B, V, T> build() {
            Object2IntOpenHashMap<T> object2intopenhashmap = new Object2IntOpenHashMap();

            object2intopenhashmap.defaultReturnValue(-2);

            for (IdDispatchCodec.Entry<B, V, T> iddispatchcodec_entry : this.entries) {
                int i = object2intopenhashmap.size();
                int j = object2intopenhashmap.putIfAbsent(iddispatchcodec_entry.type, i);

                if (j != -2) {
                    throw new IllegalStateException("Duplicate registration for type " + String.valueOf(iddispatchcodec_entry.type));
                }
            }

            return new IdDispatchCodec<B, V, T>(this.typeGetter, List.copyOf(this.entries), object2intopenhashmap);
        }
    }

    private static record Entry<B, V, T>(StreamCodec<? super B, ? extends V> serializer, T type) {

    }

    public interface DontDecorateException {}
}

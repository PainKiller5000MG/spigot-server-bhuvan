package net.minecraft.network.codec;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Function10;
import com.mojang.datafixers.util.Function11;
import com.mojang.datafixers.util.Function12;
import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;
import com.mojang.datafixers.util.Function7;
import com.mojang.datafixers.util.Function8;
import com.mojang.datafixers.util.Function9;
import io.netty.buffer.ByteBuf;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public interface StreamCodec<B, V> extends StreamEncoder<B, V>, StreamDecoder<B, V> {

    static <B, V> StreamCodec<B, V> of(final StreamEncoder<B, V> encoder, final StreamDecoder<B, V> decoder) {
        return new StreamCodec<B, V>() {
            @Override
            public V decode(B input) {
                return decoder.decode(input);
            }

            @Override
            public void encode(B output, V value) {
                encoder.encode(output, value);
            }
        };
    }

    static <B, V> StreamCodec<B, V> ofMember(final StreamMemberEncoder<B, V> encoder, final StreamDecoder<B, V> decoder) {
        return new StreamCodec<B, V>() {
            @Override
            public V decode(B input) {
                return decoder.decode(input);
            }

            @Override
            public void encode(B output, V value) {
                encoder.encode(value, output);
            }
        };
    }

    static <B, V> StreamCodec<B, V> unit(final V instance) {
        return new StreamCodec<B, V>() {
            @Override
            public V decode(B input) {
                return instance;
            }

            @Override
            public void encode(B output, V value) {
                if (!value.equals(instance)) {
                    String s = String.valueOf(value);

                    throw new IllegalStateException("Can't encode '" + s + "', expected '" + String.valueOf(instance) + "'");
                }
            }
        };
    }

    default <O> StreamCodec<B, O> apply(StreamCodec.CodecOperation<B, V, O> operation) {
        return operation.apply(this);
    }

    default <O> StreamCodec<B, O> map(final Function<? super V, ? extends O> to, final Function<? super O, ? extends V> from) {
        return new StreamCodec<B, O>() {
            @Override
            public O decode(B input) {
                return (O) to.apply(StreamCodec.this.decode(input));
            }

            @Override
            public void encode(B output, O value) {
                StreamCodec.this.encode(output, from.apply(value));
            }
        };
    }

    default <O extends ByteBuf> StreamCodec<O, V> mapStream(final Function<O, ? extends B> operation) {
        return new StreamCodec<O, V>() {
            public V decode(O input) {
                B b0 = (B) operation.apply(input);

                return (V) StreamCodec.this.decode(b0);
            }

            public void encode(O output, V value) {
                B b0 = (B) operation.apply(output);

                StreamCodec.this.encode(b0, value);
            }
        };
    }

    default <U> StreamCodec<B, U> dispatch(final Function<? super U, ? extends V> type, final Function<? super V, ? extends StreamCodec<? super B, ? extends U>> codec) {
        return new StreamCodec<B, U>() {
            @Override
            public U decode(B input) {
                V v0 = (V) StreamCodec.this.decode(input);
                StreamCodec<? super B, ? extends U> streamcodec = (StreamCodec) codec.apply(v0);

                return (U) streamcodec.decode(input);
            }

            @Override
            public void encode(B output, U value) {
                V v0 = (V) type.apply(value);
                StreamCodec<B, U> streamcodec = (StreamCodec) codec.apply(v0);

                StreamCodec.this.encode(output, v0);
                streamcodec.encode(output, value);
            }
        };
    }

    static <B, C, T1> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec1, final Function<C, T1> getter1, final Function<T1, C> constructor) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B input) {
                T1 t1 = (T1) codec1.decode(input);

                return (C) constructor.apply(t1);
            }

            @Override
            public void encode(B output, C value) {
                codec1.encode(output, getter1.apply(value));
            }
        };
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec1, final Function<C, T1> getter1, final StreamCodec<? super B, T2> codec2, final Function<C, T2> getter2, final BiFunction<T1, T2, C> constructor) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B input) {
                T1 t1 = (T1) codec1.decode(input);
                T2 t2 = (T2) codec2.decode(input);

                return (C) constructor.apply(t1, t2);
            }

            @Override
            public void encode(B output, C value) {
                codec1.encode(output, getter1.apply(value));
                codec2.encode(output, getter2.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec1, final Function<C, T1> getter1, final StreamCodec<? super B, T2> codec2, final Function<C, T2> getter2, final StreamCodec<? super B, T3> codec3, final Function<C, T3> getter3, final Function3<T1, T2, T3, C> constructor) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B input) {
                T1 t1 = (T1) codec1.decode(input);
                T2 t2 = (T2) codec2.decode(input);
                T3 t3 = (T3) codec3.decode(input);

                return (C) constructor.apply(t1, t2, t3);
            }

            @Override
            public void encode(B output, C value) {
                codec1.encode(output, getter1.apply(value));
                codec2.encode(output, getter2.apply(value));
                codec3.encode(output, getter3.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec1, final Function<C, T1> getter1, final StreamCodec<? super B, T2> codec2, final Function<C, T2> getter2, final StreamCodec<? super B, T3> codec3, final Function<C, T3> getter3, final StreamCodec<? super B, T4> codec4, final Function<C, T4> getter4, final Function4<T1, T2, T3, T4, C> constructor) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B input) {
                T1 t1 = (T1) codec1.decode(input);
                T2 t2 = (T2) codec2.decode(input);
                T3 t3 = (T3) codec3.decode(input);
                T4 t4 = (T4) codec4.decode(input);

                return (C) constructor.apply(t1, t2, t3, t4);
            }

            @Override
            public void encode(B output, C value) {
                codec1.encode(output, getter1.apply(value));
                codec2.encode(output, getter2.apply(value));
                codec3.encode(output, getter3.apply(value));
                codec4.encode(output, getter4.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec1, final Function<C, T1> getter1, final StreamCodec<? super B, T2> codec2, final Function<C, T2> getter2, final StreamCodec<? super B, T3> codec3, final Function<C, T3> getter3, final StreamCodec<? super B, T4> codec4, final Function<C, T4> getter4, final StreamCodec<? super B, T5> codec5, final Function<C, T5> getter5, final Function5<T1, T2, T3, T4, T5, C> constructor) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B input) {
                T1 t1 = (T1) codec1.decode(input);
                T2 t2 = (T2) codec2.decode(input);
                T3 t3 = (T3) codec3.decode(input);
                T4 t4 = (T4) codec4.decode(input);
                T5 t5 = (T5) codec5.decode(input);

                return (C) constructor.apply(t1, t2, t3, t4, t5);
            }

            @Override
            public void encode(B output, C value) {
                codec1.encode(output, getter1.apply(value));
                codec2.encode(output, getter2.apply(value));
                codec3.encode(output, getter3.apply(value));
                codec4.encode(output, getter4.apply(value));
                codec5.encode(output, getter5.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec1, final Function<C, T1> getter1, final StreamCodec<? super B, T2> codec2, final Function<C, T2> getter2, final StreamCodec<? super B, T3> codec3, final Function<C, T3> getter3, final StreamCodec<? super B, T4> codec4, final Function<C, T4> getter4, final StreamCodec<? super B, T5> codec5, final Function<C, T5> getter5, final StreamCodec<? super B, T6> codec6, final Function<C, T6> getter6, final Function6<T1, T2, T3, T4, T5, T6, C> constructor) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B input) {
                T1 t1 = (T1) codec1.decode(input);
                T2 t2 = (T2) codec2.decode(input);
                T3 t3 = (T3) codec3.decode(input);
                T4 t4 = (T4) codec4.decode(input);
                T5 t5 = (T5) codec5.decode(input);
                T6 t6 = (T6) codec6.decode(input);

                return (C) constructor.apply(t1, t2, t3, t4, t5, t6);
            }

            @Override
            public void encode(B output, C value) {
                codec1.encode(output, getter1.apply(value));
                codec2.encode(output, getter2.apply(value));
                codec3.encode(output, getter3.apply(value));
                codec4.encode(output, getter4.apply(value));
                codec5.encode(output, getter5.apply(value));
                codec6.encode(output, getter6.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec1, final Function<C, T1> getter1, final StreamCodec<? super B, T2> codec2, final Function<C, T2> getter2, final StreamCodec<? super B, T3> codec3, final Function<C, T3> getter3, final StreamCodec<? super B, T4> codec4, final Function<C, T4> getter4, final StreamCodec<? super B, T5> codec5, final Function<C, T5> getter5, final StreamCodec<? super B, T6> codec6, final Function<C, T6> getter6, final StreamCodec<? super B, T7> codec7, final Function<C, T7> getter7, final Function7<T1, T2, T3, T4, T5, T6, T7, C> constructor) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B input) {
                T1 t1 = (T1) codec1.decode(input);
                T2 t2 = (T2) codec2.decode(input);
                T3 t3 = (T3) codec3.decode(input);
                T4 t4 = (T4) codec4.decode(input);
                T5 t5 = (T5) codec5.decode(input);
                T6 t6 = (T6) codec6.decode(input);
                T7 t7 = (T7) codec7.decode(input);

                return (C) constructor.apply(t1, t2, t3, t4, t5, t6, t7);
            }

            @Override
            public void encode(B output, C value) {
                codec1.encode(output, getter1.apply(value));
                codec2.encode(output, getter2.apply(value));
                codec3.encode(output, getter3.apply(value));
                codec4.encode(output, getter4.apply(value));
                codec5.encode(output, getter5.apply(value));
                codec6.encode(output, getter6.apply(value));
                codec7.encode(output, getter7.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7, T8> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec1, final Function<C, T1> getter1, final StreamCodec<? super B, T2> codec2, final Function<C, T2> getter2, final StreamCodec<? super B, T3> codec3, final Function<C, T3> getter3, final StreamCodec<? super B, T4> codec4, final Function<C, T4> getter4, final StreamCodec<? super B, T5> codec5, final Function<C, T5> getter5, final StreamCodec<? super B, T6> codec6, final Function<C, T6> getter6, final StreamCodec<? super B, T7> codec7, final Function<C, T7> getter7, final StreamCodec<? super B, T8> codec8, final Function<C, T8> getter8, final Function8<T1, T2, T3, T4, T5, T6, T7, T8, C> constructor) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B input) {
                T1 t1 = (T1) codec1.decode(input);
                T2 t2 = (T2) codec2.decode(input);
                T3 t3 = (T3) codec3.decode(input);
                T4 t4 = (T4) codec4.decode(input);
                T5 t5 = (T5) codec5.decode(input);
                T6 t6 = (T6) codec6.decode(input);
                T7 t7 = (T7) codec7.decode(input);
                T8 t8 = (T8) codec8.decode(input);

                return (C) constructor.apply(t1, t2, t3, t4, t5, t6, t7, t8);
            }

            @Override
            public void encode(B output, C value) {
                codec1.encode(output, getter1.apply(value));
                codec2.encode(output, getter2.apply(value));
                codec3.encode(output, getter3.apply(value));
                codec4.encode(output, getter4.apply(value));
                codec5.encode(output, getter5.apply(value));
                codec6.encode(output, getter6.apply(value));
                codec7.encode(output, getter7.apply(value));
                codec8.encode(output, getter8.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec1, final Function<C, T1> getter1, final StreamCodec<? super B, T2> codec2, final Function<C, T2> getter2, final StreamCodec<? super B, T3> codec3, final Function<C, T3> getter3, final StreamCodec<? super B, T4> codec4, final Function<C, T4> getter4, final StreamCodec<? super B, T5> codec5, final Function<C, T5> getter5, final StreamCodec<? super B, T6> codec6, final Function<C, T6> getter6, final StreamCodec<? super B, T7> codec7, final Function<C, T7> getter7, final StreamCodec<? super B, T8> codec8, final Function<C, T8> getter8, final StreamCodec<? super B, T9> codec9, final Function<C, T9> getter9, final Function9<T1, T2, T3, T4, T5, T6, T7, T8, T9, C> constructor) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B input) {
                T1 t1 = (T1) codec1.decode(input);
                T2 t2 = (T2) codec2.decode(input);
                T3 t3 = (T3) codec3.decode(input);
                T4 t4 = (T4) codec4.decode(input);
                T5 t5 = (T5) codec5.decode(input);
                T6 t6 = (T6) codec6.decode(input);
                T7 t7 = (T7) codec7.decode(input);
                T8 t8 = (T8) codec8.decode(input);
                T9 t9 = (T9) codec9.decode(input);

                return (C) constructor.apply(t1, t2, t3, t4, t5, t6, t7, t8, t9);
            }

            @Override
            public void encode(B output, C value) {
                codec1.encode(output, getter1.apply(value));
                codec2.encode(output, getter2.apply(value));
                codec3.encode(output, getter3.apply(value));
                codec4.encode(output, getter4.apply(value));
                codec5.encode(output, getter5.apply(value));
                codec6.encode(output, getter6.apply(value));
                codec7.encode(output, getter7.apply(value));
                codec8.encode(output, getter8.apply(value));
                codec9.encode(output, getter9.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec1, final Function<C, T1> getter1, final StreamCodec<? super B, T2> codec2, final Function<C, T2> getter2, final StreamCodec<? super B, T3> codec3, final Function<C, T3> getter3, final StreamCodec<? super B, T4> codec4, final Function<C, T4> getter4, final StreamCodec<? super B, T5> codec5, final Function<C, T5> getter5, final StreamCodec<? super B, T6> codec6, final Function<C, T6> getter6, final StreamCodec<? super B, T7> codec7, final Function<C, T7> getter7, final StreamCodec<? super B, T8> codec8, final Function<C, T8> getter8, final StreamCodec<? super B, T9> codec9, final Function<C, T9> getter9, final StreamCodec<? super B, T10> codec10, final Function<C, T10> getter10, final Function10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, C> constructor) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B input) {
                T1 t1 = (T1) codec1.decode(input);
                T2 t2 = (T2) codec2.decode(input);
                T3 t3 = (T3) codec3.decode(input);
                T4 t4 = (T4) codec4.decode(input);
                T5 t5 = (T5) codec5.decode(input);
                T6 t6 = (T6) codec6.decode(input);
                T7 t7 = (T7) codec7.decode(input);
                T8 t8 = (T8) codec8.decode(input);
                T9 t9 = (T9) codec9.decode(input);
                T10 t10 = (T10) codec10.decode(input);

                return (C) constructor.apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10);
            }

            @Override
            public void encode(B output, C value) {
                codec1.encode(output, getter1.apply(value));
                codec2.encode(output, getter2.apply(value));
                codec3.encode(output, getter3.apply(value));
                codec4.encode(output, getter4.apply(value));
                codec5.encode(output, getter5.apply(value));
                codec6.encode(output, getter6.apply(value));
                codec7.encode(output, getter7.apply(value));
                codec8.encode(output, getter8.apply(value));
                codec9.encode(output, getter9.apply(value));
                codec10.encode(output, getter10.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec1, final Function<C, T1> getter1, final StreamCodec<? super B, T2> codec2, final Function<C, T2> getter2, final StreamCodec<? super B, T3> codec3, final Function<C, T3> getter3, final StreamCodec<? super B, T4> codec4, final Function<C, T4> getter4, final StreamCodec<? super B, T5> codec5, final Function<C, T5> getter5, final StreamCodec<? super B, T6> codec6, final Function<C, T6> getter6, final StreamCodec<? super B, T7> codec7, final Function<C, T7> getter7, final StreamCodec<? super B, T8> codec8, final Function<C, T8> getter8, final StreamCodec<? super B, T9> codec9, final Function<C, T9> getter9, final StreamCodec<? super B, T10> codec10, final Function<C, T10> getter10, final StreamCodec<? super B, T11> codec11, final Function<C, T11> getter11, final Function11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, C> constructor) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B input) {
                T1 t1 = (T1) codec1.decode(input);
                T2 t2 = (T2) codec2.decode(input);
                T3 t3 = (T3) codec3.decode(input);
                T4 t4 = (T4) codec4.decode(input);
                T5 t5 = (T5) codec5.decode(input);
                T6 t6 = (T6) codec6.decode(input);
                T7 t7 = (T7) codec7.decode(input);
                T8 t8 = (T8) codec8.decode(input);
                T9 t9 = (T9) codec9.decode(input);
                T10 t10 = (T10) codec10.decode(input);
                T11 t11 = (T11) codec11.decode(input);

                return (C) constructor.apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11);
            }

            @Override
            public void encode(B output, C value) {
                codec1.encode(output, getter1.apply(value));
                codec2.encode(output, getter2.apply(value));
                codec3.encode(output, getter3.apply(value));
                codec4.encode(output, getter4.apply(value));
                codec5.encode(output, getter5.apply(value));
                codec6.encode(output, getter6.apply(value));
                codec7.encode(output, getter7.apply(value));
                codec8.encode(output, getter8.apply(value));
                codec9.encode(output, getter9.apply(value));
                codec10.encode(output, getter10.apply(value));
                codec11.encode(output, getter11.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> StreamCodec<B, C> composite(final StreamCodec<? super B, T1> codec1, final Function<C, T1> getter1, final StreamCodec<? super B, T2> codec2, final Function<C, T2> getter2, final StreamCodec<? super B, T3> codec3, final Function<C, T3> getter3, final StreamCodec<? super B, T4> codec4, final Function<C, T4> getter4, final StreamCodec<? super B, T5> codec5, final Function<C, T5> getter5, final StreamCodec<? super B, T6> codec6, final Function<C, T6> getter6, final StreamCodec<? super B, T7> codec7, final Function<C, T7> getter7, final StreamCodec<? super B, T8> codec8, final Function<C, T8> getter8, final StreamCodec<? super B, T9> codec9, final Function<C, T9> getter9, final StreamCodec<? super B, T10> codec10, final Function<C, T10> getter10, final StreamCodec<? super B, T11> codec11, final Function<C, T11> getter11, final StreamCodec<? super B, T12> codec12, final Function<C, T12> getter12, final Function12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, C> constructor) {
        return new StreamCodec<B, C>() {
            @Override
            public C decode(B input) {
                T1 t1 = (T1) codec1.decode(input);
                T2 t2 = (T2) codec2.decode(input);
                T3 t3 = (T3) codec3.decode(input);
                T4 t4 = (T4) codec4.decode(input);
                T5 t5 = (T5) codec5.decode(input);
                T6 t6 = (T6) codec6.decode(input);
                T7 t7 = (T7) codec7.decode(input);
                T8 t8 = (T8) codec8.decode(input);
                T9 t9 = (T9) codec9.decode(input);
                T10 t10 = (T10) codec10.decode(input);
                T11 t11 = (T11) codec11.decode(input);
                T12 t12 = (T12) codec12.decode(input);

                return (C) constructor.apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12);
            }

            @Override
            public void encode(B output, C value) {
                codec1.encode(output, getter1.apply(value));
                codec2.encode(output, getter2.apply(value));
                codec3.encode(output, getter3.apply(value));
                codec4.encode(output, getter4.apply(value));
                codec5.encode(output, getter5.apply(value));
                codec6.encode(output, getter6.apply(value));
                codec7.encode(output, getter7.apply(value));
                codec8.encode(output, getter8.apply(value));
                codec9.encode(output, getter9.apply(value));
                codec10.encode(output, getter10.apply(value));
                codec11.encode(output, getter11.apply(value));
                codec12.encode(output, getter12.apply(value));
            }
        };
    }

    static <B, T> StreamCodec<B, T> recursive(final UnaryOperator<StreamCodec<B, T>> factory) {
        return new StreamCodec<B, T>() {
            private final Supplier<StreamCodec<B, T>> inner = Suppliers.memoize(() -> {
                return (StreamCodec) factory.apply(this);
            });

            @Override
            public T decode(B input) {
                return (T) ((StreamCodec) this.inner.get()).decode(input);
            }

            @Override
            public void encode(B output, T value) {
                ((StreamCodec) this.inner.get()).encode(output, value);
            }
        };
    }

    default <S extends B> StreamCodec<S, V> cast() {
        return this;
    }

    @FunctionalInterface
    public interface CodecOperation<B, S, T> {

        StreamCodec<B, T> apply(StreamCodec<B, S> original);
    }
}

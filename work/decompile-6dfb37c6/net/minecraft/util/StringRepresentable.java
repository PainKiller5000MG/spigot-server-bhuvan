package net.minecraft.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Keyable;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public interface StringRepresentable {

    int PRE_BUILT_MAP_THRESHOLD = 16;

    String getSerializedName();

    static <E extends Enum<E> & StringRepresentable> StringRepresentable.EnumCodec<E> fromEnum(Supplier<E[]> values) {
        return fromEnumWithMapping(values, (s) -> {
            return s;
        });
    }

    static <E extends Enum<E> & StringRepresentable> StringRepresentable.EnumCodec<E> fromEnumWithMapping(Supplier<E[]> values, Function<String, String> converter) {
        E[] ae = (Enum[]) values.get();
        Function<String, E> function1 = createNameLookup(ae, (oenum) -> {
            return (String) converter.apply(((StringRepresentable) oenum).getSerializedName());
        });

        return new StringRepresentable.EnumCodec<E>(ae, function1);
    }

    static <T extends StringRepresentable> Codec<T> fromValues(Supplier<T[]> values) {
        T[] at = (StringRepresentable[]) values.get();
        Function<String, T> function = createNameLookup(at);
        ToIntFunction<T> tointfunction = Util.<T>createIndexLookup(Arrays.asList(at));

        return new StringRepresentable.StringRepresentableCodec<T>(at, function, tointfunction);
    }

    static <T extends StringRepresentable> Function<String, @Nullable T> createNameLookup(T[] valueArray) {
        return createNameLookup(valueArray, StringRepresentable::getSerializedName);
    }

    static <T> Function<String, @Nullable T> createNameLookup(T[] valueArray, Function<T, String> converter) {
        if (valueArray.length > 16) {
            Map<String, T> map = (Map) Arrays.stream(valueArray).collect(Collectors.toMap(converter, (object) -> {
                return object;
            }));

            Objects.requireNonNull(map);
            return map::get;
        } else {
            return (s) -> {
                for (T t0 : valueArray) {
                    if (((String) converter.apply(t0)).equals(s)) {
                        return t0;
                    }
                }

                return null;
            };
        }
    }

    static Keyable keys(final StringRepresentable[] values) {
        return new Keyable() {
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                Stream stream = Arrays.stream(values).map(StringRepresentable::getSerializedName);

                Objects.requireNonNull(ops);
                return stream.map(ops::createString);
            }
        };
    }

    public static class StringRepresentableCodec<S extends StringRepresentable> implements Codec<S> {

        private final Codec<S> codec;

        public StringRepresentableCodec(S[] valueArray, Function<String, @Nullable S> nameResolver, ToIntFunction<S> idResolver) {
            this.codec = ExtraCodecs.orCompressed(Codec.stringResolver(StringRepresentable::getSerializedName, nameResolver), ExtraCodecs.idResolverCodec(idResolver, (i) -> {
                return i >= 0 && i < valueArray.length ? valueArray[i] : null;
            }, -1));
        }

        public <T> DataResult<Pair<S, T>> decode(DynamicOps<T> ops, T input) {
            return this.codec.decode(ops, input);
        }

        public <T> DataResult<T> encode(S input, DynamicOps<T> ops, T prefix) {
            return this.codec.encode(input, ops, prefix);
        }
    }

    public static class EnumCodec<E extends Enum<E> & StringRepresentable> extends StringRepresentable.StringRepresentableCodec<E> {

        private final Function<String, @Nullable E> resolver;

        public EnumCodec(E[] valueArray, Function<String, E> nameResolver) {
            super(valueArray, nameResolver, (object) -> {
                return ((Enum) object).ordinal();
            });
            this.resolver = nameResolver;
        }

        public @Nullable E byName(String name) {
            return (E) (this.resolver.apply(name));
        }

        public E byName(String name, E _default) {
            return (E) (Objects.requireNonNullElse(this.byName(name), _default));
        }

        public E byName(String name, Supplier<? extends E> defaultSupplier) {
            return (E) (Objects.requireNonNullElseGet(this.byName(name), defaultSupplier));
        }
    }
}

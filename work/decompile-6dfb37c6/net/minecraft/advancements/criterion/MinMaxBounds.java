package net.minecraft.advancements.criterion;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.BuiltInExceptionProvider;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public interface MinMaxBounds<T extends Number & Comparable<T>> {

    SimpleCommandExceptionType ERROR_EMPTY = new SimpleCommandExceptionType(Component.translatable("argument.range.empty"));
    SimpleCommandExceptionType ERROR_SWAPPED = new SimpleCommandExceptionType(Component.translatable("argument.range.swapped"));

    MinMaxBounds.Bounds<T> bounds();

    default Optional<T> min() {
        return this.bounds().min;
    }

    default Optional<T> max() {
        return this.bounds().max;
    }

    default boolean isAny() {
        return this.bounds().isAny();
    }

    public static record Ints(MinMaxBounds.Bounds<Integer> bounds, MinMaxBounds.Bounds<Long> boundsSqr) implements MinMaxBounds<Integer> {

        public static final MinMaxBounds.Ints ANY = new MinMaxBounds.Ints(MinMaxBounds.Bounds.any());
        public static final Codec<MinMaxBounds.Ints> CODEC = MinMaxBounds.Bounds.createCodec(Codec.INT).validate(MinMaxBounds.Bounds::validateSwappedBoundsInCodec).xmap(MinMaxBounds.Ints::new, MinMaxBounds.Ints::bounds);
        public static final StreamCodec<ByteBuf, MinMaxBounds.Ints> STREAM_CODEC = MinMaxBounds.Bounds.createStreamCodec(ByteBufCodecs.INT).map(MinMaxBounds.Ints::new, MinMaxBounds.Ints::bounds);

        private Ints(MinMaxBounds.Bounds<Integer> bounds) {
            this(bounds, bounds.map((integer) -> {
                return Mth.square(integer.longValue());
            }));
        }

        public static MinMaxBounds.Ints exactly(int value) {
            return new MinMaxBounds.Ints(MinMaxBounds.Bounds.exactly(value));
        }

        public static MinMaxBounds.Ints between(int min, int max) {
            return new MinMaxBounds.Ints(MinMaxBounds.Bounds.between(min, max));
        }

        public static MinMaxBounds.Ints atLeast(int value) {
            return new MinMaxBounds.Ints(MinMaxBounds.Bounds.atLeast(value));
        }

        public static MinMaxBounds.Ints atMost(int value) {
            return new MinMaxBounds.Ints(MinMaxBounds.Bounds.atMost(value));
        }

        public boolean matches(int value) {
            return this.bounds.min.isPresent() && (Integer) this.bounds.min.get() > value ? false : this.bounds.max.isEmpty() || (Integer) this.bounds.max.get() >= value;
        }

        public boolean matchesSqr(long valueSqr) {
            return this.boundsSqr.min.isPresent() && (Long) this.boundsSqr.min.get() > valueSqr ? false : this.boundsSqr.max.isEmpty() || (Long) this.boundsSqr.max.get() >= valueSqr;
        }

        public static MinMaxBounds.Ints fromReader(StringReader reader) throws CommandSyntaxException {
            int i = reader.getCursor();
            Function function = Integer::parseInt;
            BuiltInExceptionProvider builtinexceptionprovider = CommandSyntaxException.BUILT_IN_EXCEPTIONS;

            Objects.requireNonNull(builtinexceptionprovider);
            MinMaxBounds.Bounds<Integer> minmaxbounds_bounds = MinMaxBounds.Bounds.<Integer>fromReader(reader, function, builtinexceptionprovider::readerInvalidInt);

            if (minmaxbounds_bounds.areSwapped()) {
                reader.setCursor(i);
                throw MinMaxBounds.Ints.ERROR_SWAPPED.createWithContext(reader);
            } else {
                return new MinMaxBounds.Ints(minmaxbounds_bounds);
            }
        }
    }

    public static record Doubles(MinMaxBounds.Bounds<Double> bounds, MinMaxBounds.Bounds<Double> boundsSqr) implements MinMaxBounds<Double> {

        public static final MinMaxBounds.Doubles ANY = new MinMaxBounds.Doubles(MinMaxBounds.Bounds.any());
        public static final Codec<MinMaxBounds.Doubles> CODEC = MinMaxBounds.Bounds.createCodec(Codec.DOUBLE).validate(MinMaxBounds.Bounds::validateSwappedBoundsInCodec).xmap(MinMaxBounds.Doubles::new, MinMaxBounds.Doubles::bounds);
        public static final StreamCodec<ByteBuf, MinMaxBounds.Doubles> STREAM_CODEC = MinMaxBounds.Bounds.createStreamCodec(ByteBufCodecs.DOUBLE).map(MinMaxBounds.Doubles::new, MinMaxBounds.Doubles::bounds);

        private Doubles(MinMaxBounds.Bounds<Double> bounds) {
            this(bounds, bounds.map(Mth::square));
        }

        public static MinMaxBounds.Doubles exactly(double value) {
            return new MinMaxBounds.Doubles(MinMaxBounds.Bounds.exactly(value));
        }

        public static MinMaxBounds.Doubles between(double min, double max) {
            return new MinMaxBounds.Doubles(MinMaxBounds.Bounds.between(min, max));
        }

        public static MinMaxBounds.Doubles atLeast(double value) {
            return new MinMaxBounds.Doubles(MinMaxBounds.Bounds.atLeast(value));
        }

        public static MinMaxBounds.Doubles atMost(double value) {
            return new MinMaxBounds.Doubles(MinMaxBounds.Bounds.atMost(value));
        }

        public boolean matches(double value) {
            return this.bounds.min.isPresent() && (Double) this.bounds.min.get() > value ? false : this.bounds.max.isEmpty() || (Double) this.bounds.max.get() >= value;
        }

        public boolean matchesSqr(double valueSqr) {
            return this.boundsSqr.min.isPresent() && (Double) this.boundsSqr.min.get() > valueSqr ? false : this.boundsSqr.max.isEmpty() || (Double) this.boundsSqr.max.get() >= valueSqr;
        }

        public static MinMaxBounds.Doubles fromReader(StringReader reader) throws CommandSyntaxException {
            int i = reader.getCursor();
            Function function = Double::parseDouble;
            BuiltInExceptionProvider builtinexceptionprovider = CommandSyntaxException.BUILT_IN_EXCEPTIONS;

            Objects.requireNonNull(builtinexceptionprovider);
            MinMaxBounds.Bounds<Double> minmaxbounds_bounds = MinMaxBounds.Bounds.<Double>fromReader(reader, function, builtinexceptionprovider::readerInvalidDouble);

            if (minmaxbounds_bounds.areSwapped()) {
                reader.setCursor(i);
                throw MinMaxBounds.Doubles.ERROR_SWAPPED.createWithContext(reader);
            } else {
                return new MinMaxBounds.Doubles(minmaxbounds_bounds);
            }
        }
    }

    public static record FloatDegrees(MinMaxBounds.Bounds<Float> bounds) implements MinMaxBounds<Float> {

        public static final MinMaxBounds.FloatDegrees ANY = new MinMaxBounds.FloatDegrees(MinMaxBounds.Bounds.any());
        public static final Codec<MinMaxBounds.FloatDegrees> CODEC = MinMaxBounds.Bounds.createCodec(Codec.FLOAT).xmap(MinMaxBounds.FloatDegrees::new, MinMaxBounds.FloatDegrees::bounds);
        public static final StreamCodec<ByteBuf, MinMaxBounds.FloatDegrees> STREAM_CODEC = MinMaxBounds.Bounds.createStreamCodec(ByteBufCodecs.FLOAT).map(MinMaxBounds.FloatDegrees::new, MinMaxBounds.FloatDegrees::bounds);

        public static MinMaxBounds.FloatDegrees fromReader(StringReader reader) throws CommandSyntaxException {
            Function function = Float::parseFloat;
            BuiltInExceptionProvider builtinexceptionprovider = CommandSyntaxException.BUILT_IN_EXCEPTIONS;

            Objects.requireNonNull(builtinexceptionprovider);
            MinMaxBounds.Bounds<Float> minmaxbounds_bounds = MinMaxBounds.Bounds.<Float>fromReader(reader, function, builtinexceptionprovider::readerInvalidFloat);

            return new MinMaxBounds.FloatDegrees(minmaxbounds_bounds);
        }
    }

    public static record Bounds<T extends Number & Comparable<T>>(Optional<T> min, Optional<T> max) {

        public boolean isAny() {
            return this.min().isEmpty() && this.max().isEmpty();
        }

        public DataResult<MinMaxBounds.Bounds<T>> validateSwappedBoundsInCodec() {
            return this.areSwapped() ? DataResult.error(() -> {
                String s = String.valueOf(this.min());

                return "Swapped bounds in range: " + s + " is higher than " + String.valueOf(this.max());
            }) : DataResult.success(this);
        }

        public boolean areSwapped() {
            return this.min.isPresent() && this.max.isPresent() && ((Comparable) ((Number) this.min.get())).compareTo((Number) this.max.get()) > 0;
        }

        public Optional<T> asPoint() {
            Optional<T> optional = this.min();
            Optional<T> optional1 = this.max();

            return optional.equals(optional1) ? optional : Optional.empty();
        }

        public static <T extends Number & Comparable<T>> MinMaxBounds.Bounds<T> any() {
            return new MinMaxBounds.Bounds<T>(Optional.empty(), Optional.empty());
        }

        public static <T extends Number & Comparable<T>> MinMaxBounds.Bounds<T> exactly(T value) {
            Optional<T> optional = Optional.of(value);

            return new MinMaxBounds.Bounds<T>(optional, optional);
        }

        public static <T extends Number & Comparable<T>> MinMaxBounds.Bounds<T> between(T min, T max) {
            return new MinMaxBounds.Bounds<T>(Optional.of(min), Optional.of(max));
        }

        public static <T extends Number & Comparable<T>> MinMaxBounds.Bounds<T> atLeast(T value) {
            return new MinMaxBounds.Bounds<T>(Optional.of(value), Optional.empty());
        }

        public static <T extends Number & Comparable<T>> MinMaxBounds.Bounds<T> atMost(T value) {
            return new MinMaxBounds.Bounds<T>(Optional.empty(), Optional.of(value));
        }

        public <U extends Number & Comparable<U>> MinMaxBounds.Bounds<U> map(Function<T, U> mapper) {
            return new MinMaxBounds.Bounds<U>(this.min.map(mapper), this.max.map(mapper));
        }

        static <T extends Number & Comparable<T>> Codec<MinMaxBounds.Bounds<T>> createCodec(Codec<T> numberCodec) {
            Codec<MinMaxBounds.Bounds<T>> codec1 = RecordCodecBuilder.create((instance) -> {
                return instance.group(numberCodec.optionalFieldOf("min").forGetter(MinMaxBounds.Bounds::min), numberCodec.optionalFieldOf("max").forGetter(MinMaxBounds.Bounds::max)).apply(instance, MinMaxBounds.Bounds::new);
            });

            return Codec.either(codec1, numberCodec).xmap((either) -> {
                return (MinMaxBounds.Bounds) either.map((minmaxbounds_bounds) -> {
                    return minmaxbounds_bounds;
                }, (object) -> {
                    return exactly((Number) object);
                });
            }, (minmaxbounds_bounds) -> {
                Optional<T> optional = minmaxbounds_bounds.asPoint();

                return optional.isPresent() ? Either.right((Number) optional.get()) : Either.left(minmaxbounds_bounds);
            });
        }

        static <B extends ByteBuf, T extends Number & Comparable<T>> StreamCodec<B, MinMaxBounds.Bounds<T>> createStreamCodec(final StreamCodec<B, T> numberCodec) {
            return new StreamCodec<B, MinMaxBounds.Bounds<T>>() {
                private static final int MIN_FLAG = 1;
                private static final int MAX_FLAG = 2;

                public MinMaxBounds.Bounds<T> decode(B input) {
                    byte b1 = input.readByte();
                    Optional<T> optional = (b1 & 1) != 0 ? Optional.of((Number) numberCodec.decode(input)) : Optional.empty();
                    Optional<T> optional1 = (b1 & 2) != 0 ? Optional.of((Number) numberCodec.decode(input)) : Optional.empty();

                    return new MinMaxBounds.Bounds<T>(optional, optional1);
                }

                public void encode(B output, MinMaxBounds.Bounds<T> value) {
                    Optional<T> optional = value.min();
                    Optional<T> optional1 = value.max();

                    output.writeByte((optional.isPresent() ? 1 : 0) | (optional1.isPresent() ? 2 : 0));
                    optional.ifPresent((number) -> {
                        numberCodec.encode(output, number);
                    });
                    optional1.ifPresent((number) -> {
                        numberCodec.encode(output, number);
                    });
                }
            };
        }

        public static <T extends Number & Comparable<T>> MinMaxBounds.Bounds<T> fromReader(StringReader reader, Function<String, T> converter, Supplier<DynamicCommandExceptionType> parseExc) throws CommandSyntaxException {
            if (!reader.canRead()) {
                throw MinMaxBounds.ERROR_EMPTY.createWithContext(reader);
            } else {
                int i = reader.getCursor();

                try {
                    Optional<T> optional = readNumber(reader, converter, parseExc);
                    Optional<T> optional1;

                    if (reader.canRead(2) && reader.peek() == '.' && reader.peek(1) == '.') {
                        reader.skip();
                        reader.skip();
                        optional1 = readNumber(reader, converter, parseExc);
                    } else {
                        optional1 = optional;
                    }

                    if (optional.isEmpty() && optional1.isEmpty()) {
                        throw MinMaxBounds.ERROR_EMPTY.createWithContext(reader);
                    } else {
                        return new MinMaxBounds.Bounds<T>(optional, optional1);
                    }
                } catch (CommandSyntaxException commandsyntaxexception) {
                    reader.setCursor(i);
                    throw new CommandSyntaxException(commandsyntaxexception.getType(), commandsyntaxexception.getRawMessage(), commandsyntaxexception.getInput(), i);
                }
            }
        }

        private static <T extends Number> Optional<T> readNumber(StringReader reader, Function<String, T> converter, Supplier<DynamicCommandExceptionType> parseExc) throws CommandSyntaxException {
            int i = reader.getCursor();

            while (reader.canRead() && isAllowedInputChar(reader)) {
                reader.skip();
            }

            String s = reader.getString().substring(i, reader.getCursor());

            if (s.isEmpty()) {
                return Optional.empty();
            } else {
                try {
                    return Optional.of((Number) converter.apply(s));
                } catch (NumberFormatException numberformatexception) {
                    throw ((DynamicCommandExceptionType) parseExc.get()).createWithContext(reader, s);
                }
            }
        }

        private static boolean isAllowedInputChar(StringReader reader) {
            char c0 = reader.peek();

            return (c0 < '0' || c0 > '9') && c0 != '-' ? (c0 != '.' ? false : !reader.canRead(2) || reader.peek(1) != '.') : true;
        }
    }
}

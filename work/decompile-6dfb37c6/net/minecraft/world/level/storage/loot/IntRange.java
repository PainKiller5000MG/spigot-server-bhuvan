package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;
import org.jspecify.annotations.Nullable;

public class IntRange {

    private static final Codec<IntRange> RECORD_CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(NumberProviders.CODEC.optionalFieldOf("min").forGetter((intrange) -> {
            return Optional.ofNullable(intrange.min);
        }), NumberProviders.CODEC.optionalFieldOf("max").forGetter((intrange) -> {
            return Optional.ofNullable(intrange.max);
        })).apply(instance, IntRange::new);
    });
    public static final Codec<IntRange> CODEC = Codec.either(Codec.INT, IntRange.RECORD_CODEC).xmap((either) -> {
        return (IntRange) either.map(IntRange::exact, Function.identity());
    }, (intrange) -> {
        OptionalInt optionalint = intrange.unpackExact();

        return optionalint.isPresent() ? Either.left(optionalint.getAsInt()) : Either.right(intrange);
    });
    private final @Nullable NumberProvider min;
    private final @Nullable NumberProvider max;
    private final IntRange.IntLimiter limiter;
    private final IntRange.IntChecker predicate;

    public Set<ContextKey<?>> getReferencedContextParams() {
        ImmutableSet.Builder<ContextKey<?>> immutableset_builder = ImmutableSet.builder();

        if (this.min != null) {
            immutableset_builder.addAll(this.min.getReferencedContextParams());
        }

        if (this.max != null) {
            immutableset_builder.addAll(this.max.getReferencedContextParams());
        }

        return immutableset_builder.build();
    }

    private IntRange(Optional<NumberProvider> min, Optional<NumberProvider> max) {
        this((NumberProvider) min.orElse((Object) null), (NumberProvider) max.orElse((Object) null));
    }

    private IntRange(@Nullable NumberProvider min, @Nullable NumberProvider max) {
        this.min = min;
        this.max = max;
        if (min == null) {
            if (max == null) {
                this.limiter = (lootcontext, i) -> {
                    return i;
                };
                this.predicate = (lootcontext, i) -> {
                    return true;
                };
            } else {
                this.limiter = (lootcontext, i) -> {
                    return Math.min(max.getInt(lootcontext), i);
                };
                this.predicate = (lootcontext, i) -> {
                    return i <= max.getInt(lootcontext);
                };
            }
        } else if (max == null) {
            this.limiter = (lootcontext, i) -> {
                return Math.max(min.getInt(lootcontext), i);
            };
            this.predicate = (lootcontext, i) -> {
                return i >= min.getInt(lootcontext);
            };
        } else {
            this.limiter = (lootcontext, i) -> {
                return Mth.clamp(i, min.getInt(lootcontext), max.getInt(lootcontext));
            };
            this.predicate = (lootcontext, i) -> {
                return i >= min.getInt(lootcontext) && i <= max.getInt(lootcontext);
            };
        }

    }

    public static IntRange exact(int value) {
        ConstantValue constantvalue = ConstantValue.exactly((float) value);

        return new IntRange(Optional.of(constantvalue), Optional.of(constantvalue));
    }

    public static IntRange range(int min, int max) {
        return new IntRange(Optional.of(ConstantValue.exactly((float) min)), Optional.of(ConstantValue.exactly((float) max)));
    }

    public static IntRange lowerBound(int value) {
        return new IntRange(Optional.of(ConstantValue.exactly((float) value)), Optional.empty());
    }

    public static IntRange upperBound(int value) {
        return new IntRange(Optional.empty(), Optional.of(ConstantValue.exactly((float) value)));
    }

    public int clamp(LootContext context, int value) {
        return this.limiter.apply(context, value);
    }

    public boolean test(LootContext context, int value) {
        return this.predicate.test(context, value);
    }

    private OptionalInt unpackExact() {
        if (Objects.equals(this.min, this.max)) {
            NumberProvider numberprovider = this.min;

            if (numberprovider instanceof ConstantValue) {
                ConstantValue constantvalue = (ConstantValue) numberprovider;

                if (Math.floor((double) constantvalue.value()) == (double) constantvalue.value()) {
                    return OptionalInt.of((int) constantvalue.value());
                }
            }
        }

        return OptionalInt.empty();
    }

    @FunctionalInterface
    private interface IntChecker {

        boolean test(LootContext context, int value);
    }

    @FunctionalInterface
    private interface IntLimiter {

        int apply(LootContext context, int value);
    }
}

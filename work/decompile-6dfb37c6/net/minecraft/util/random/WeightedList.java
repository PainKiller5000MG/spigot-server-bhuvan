package net.minecraft.util.random;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

public final class WeightedList<E> {

    private static final int FLAT_THRESHOLD = 64;
    private final int totalWeight;
    private final List<Weighted<E>> items;
    private final WeightedList.@Nullable Selector<E> selector;

    private WeightedList(List<? extends Weighted<E>> items) {
        this.items = List.copyOf(items);
        this.totalWeight = WeightedRandom.getTotalWeight(items, Weighted::weight);
        if (this.totalWeight == 0) {
            this.selector = null;
        } else if (this.totalWeight < 64) {
            this.selector = new WeightedList.Flat<E>(this.items, this.totalWeight);
        } else {
            this.selector = new WeightedList.Compact<E>(this.items);
        }

    }

    public static <E> WeightedList<E> of() {
        return new WeightedList<E>(List.of());
    }

    public static <E> WeightedList<E> of(E value) {
        return new WeightedList<E>(List.of(new Weighted(value, 1)));
    }

    @SafeVarargs
    public static <E> WeightedList<E> of(Weighted<E>... items) {
        return new WeightedList<E>(List.of(items));
    }

    public static <E> WeightedList<E> of(List<Weighted<E>> items) {
        return new WeightedList<E>(items);
    }

    public static <E> WeightedList.Builder<E> builder() {
        return new WeightedList.Builder<E>();
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    public <T> WeightedList<T> map(Function<E, T> mapper) {
        return new WeightedList<T>(Lists.transform(this.items, (weighted) -> {
            return weighted.map(mapper);
        }));
    }

    public Optional<E> getRandom(RandomSource random) {
        if (this.selector == null) {
            return Optional.empty();
        } else {
            int i = random.nextInt(this.totalWeight);

            return Optional.of(this.selector.get(i));
        }
    }

    public E getRandomOrThrow(RandomSource random) {
        if (this.selector == null) {
            throw new IllegalStateException("Weighted list has no elements");
        } else {
            int i = random.nextInt(this.totalWeight);

            return this.selector.get(i);
        }
    }

    public List<Weighted<E>> unwrap() {
        return this.items;
    }

    public static <E> Codec<WeightedList<E>> codec(Codec<E> elementCodec) {
        return Weighted.codec(elementCodec).listOf().xmap(WeightedList::of, WeightedList::unwrap);
    }

    public static <E> Codec<WeightedList<E>> codec(MapCodec<E> elementCodec) {
        return Weighted.codec(elementCodec).listOf().xmap(WeightedList::of, WeightedList::unwrap);
    }

    public static <E> Codec<WeightedList<E>> nonEmptyCodec(Codec<E> elementCodec) {
        return ExtraCodecs.nonEmptyList(Weighted.codec(elementCodec).listOf()).xmap(WeightedList::of, WeightedList::unwrap);
    }

    public static <E> Codec<WeightedList<E>> nonEmptyCodec(MapCodec<E> elementCodec) {
        return ExtraCodecs.nonEmptyList(Weighted.codec(elementCodec).listOf()).xmap(WeightedList::of, WeightedList::unwrap);
    }

    public static <E, B extends ByteBuf> StreamCodec<B, WeightedList<E>> streamCodec(StreamCodec<B, E> elementCodec) {
        return Weighted.streamCodec(elementCodec).apply(ByteBufCodecs.list()).map(WeightedList::of, WeightedList::unwrap);
    }

    public boolean contains(E value) {
        for (Weighted<E> weighted : this.items) {
            if (weighted.value().equals(value)) {
                return true;
            }
        }

        return false;
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof WeightedList)) {
            return false;
        } else {
            WeightedList<?> weightedlist = (WeightedList) obj;

            return this.totalWeight == weightedlist.totalWeight && Objects.equals(this.items, weightedlist.items);
        }
    }

    public int hashCode() {
        int i = this.totalWeight;

        i = 31 * i + this.items.hashCode();
        return i;
    }

    public static class Builder<E> {

        private final ImmutableList.Builder<Weighted<E>> result = ImmutableList.builder();

        public Builder() {}

        public WeightedList.Builder<E> add(E item) {
            return this.add(item, 1);
        }

        public WeightedList.Builder<E> add(E item, int weight) {
            this.result.add(new Weighted(item, weight));
            return this;
        }

        public WeightedList<E> build() {
            return new WeightedList<E>(this.result.build());
        }
    }

    private static class Flat<E> implements WeightedList.Selector<E> {

        private final Object[] entries;

        private Flat(List<Weighted<E>> entries, int totalWeight) {
            this.entries = new Object[totalWeight];
            int j = 0;

            for (Weighted<E> weighted : entries) {
                int k = weighted.weight();

                Arrays.fill(this.entries, j, j + k, weighted.value());
                j += k;
            }

        }

        @Override
        public E get(int selection) {
            return (E) this.entries[selection];
        }
    }

    private static class Compact<E> implements WeightedList.Selector<E> {

        private final Weighted<?>[] entries;

        private Compact(List<Weighted<E>> entries) {
            this.entries = (Weighted[]) entries.toArray((i) -> {
                return new Weighted[i];
            });
        }

        @Override
        public E get(int selection) {
            for (Weighted<?> weighted : this.entries) {
                selection -= weighted.weight();
                if (selection < 0) {
                    return (E) weighted.value();
                }
            }

            throw new IllegalStateException(selection + " exceeded total weight");
        }
    }

    private interface Selector<E> {

        E get(int selection);
    }
}

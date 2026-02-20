package net.minecraft.util.random;

import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;

public class WeightedRandom {

    private WeightedRandom() {}

    public static <T> int getTotalWeight(List<T> items, ToIntFunction<T> weightGetter) {
        long i = 0L;

        for (T t0 : items) {
            i += (long) weightGetter.applyAsInt(t0);
        }

        if (i > 2147483647L) {
            throw new IllegalArgumentException("Sum of weights must be <= 2147483647");
        } else {
            return (int) i;
        }
    }

    public static <T> Optional<T> getRandomItem(RandomSource random, List<T> items, int totalWeight, ToIntFunction<T> weightGetter) {
        if (totalWeight < 0) {
            throw (IllegalArgumentException) Util.pauseInIde(new IllegalArgumentException("Negative total weight in getRandomItem"));
        } else if (totalWeight == 0) {
            return Optional.empty();
        } else {
            int j = random.nextInt(totalWeight);

            return getWeightedItem(items, j, weightGetter);
        }
    }

    public static <T> Optional<T> getWeightedItem(List<T> items, int index, ToIntFunction<T> weightGetter) {
        for (T t0 : items) {
            index -= weightGetter.applyAsInt(t0);
            if (index < 0) {
                return Optional.of(t0);
            }
        }

        return Optional.empty();
    }

    public static <T> Optional<T> getRandomItem(RandomSource random, List<T> items, ToIntFunction<T> weightGetter) {
        return getRandomItem(random, items, getTotalWeight(items, weightGetter), weightGetter);
    }
}

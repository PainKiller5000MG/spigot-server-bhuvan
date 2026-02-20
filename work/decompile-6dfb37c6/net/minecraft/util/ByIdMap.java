package net.minecraft.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

public class ByIdMap {

    public ByIdMap() {}

    private static <T> IntFunction<T> createMap(ToIntFunction<T> idGetter, T[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("Empty value list");
        } else {
            Int2ObjectMap<T> int2objectmap = new Int2ObjectOpenHashMap();

            for (T t0 : values) {
                int i = idGetter.applyAsInt(t0);
                T t1 = (T) int2objectmap.put(i, t0);

                if (t1 != null) {
                    throw new IllegalArgumentException("Duplicate entry on id " + i + ": current=" + String.valueOf(t0) + ", previous=" + String.valueOf(t1));
                }
            }

            return int2objectmap;
        }
    }

    public static <T> IntFunction<T> sparse(ToIntFunction<T> idGetter, T[] values, T _default) {
        IntFunction<T> intfunction = createMap(idGetter, values);

        return (i) -> {
            return Objects.requireNonNullElse(intfunction.apply(i), _default);
        };
    }

    private static <T> T[] createSortedArray(ToIntFunction<T> idGetter, T[] values) {
        int i = values.length;

        if (i == 0) {
            throw new IllegalArgumentException("Empty value list");
        } else {
            T[] at1 = (T[]) ((Object[]) values.clone());

            Arrays.fill(at1, (Object) null);

            for (T t0 : values) {
                int j = idGetter.applyAsInt(t0);

                if (j < 0 || j >= i) {
                    throw new IllegalArgumentException("Values are not continous, found index " + j + " for value " + String.valueOf(t0));
                }

                T t1 = (T) at1[j];

                if (t1 != null) {
                    throw new IllegalArgumentException("Duplicate entry on id " + j + ": current=" + String.valueOf(t0) + ", previous=" + String.valueOf(t1));
                }

                at1[j] = t0;
            }

            for (int k = 0; k < i; ++k) {
                if (at1[k] == null) {
                    throw new IllegalArgumentException("Missing value at index: " + k);
                }
            }

            return at1;
        }
    }

    public static <T> IntFunction<T> continuous(ToIntFunction<T> idGetter, T[] values, ByIdMap.OutOfBoundsStrategy strategy) {
        T[] at1 = (T[]) createSortedArray(idGetter, values);
        int i = at1.length;
        IntFunction intfunction;

        switch (strategy.ordinal()) {
            case 0:
                T t0 = (T) at1[0];

                intfunction = (j) -> {
                    return j >= 0 && j < i ? at1[j] : t0;
                };
                break;
            case 1:
                intfunction = (j) -> {
                    return at1[Mth.positiveModulo(j, i)];
                };
                break;
            case 2:
                intfunction = (j) -> {
                    return at1[Mth.clamp(j, 0, i - 1)];
                };
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return intfunction;
    }

    public static enum OutOfBoundsStrategy {

        ZERO, WRAP, CLAMP;

        private OutOfBoundsStrategy() {}
    }
}

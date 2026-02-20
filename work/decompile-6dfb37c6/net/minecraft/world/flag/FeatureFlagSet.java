package net.minecraft.world.flag;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.Arrays;
import java.util.Collection;
import org.jspecify.annotations.Nullable;

public final class FeatureFlagSet {

    private static final FeatureFlagSet EMPTY = new FeatureFlagSet((FeatureFlagUniverse) null, 0L);
    public static final int MAX_CONTAINER_SIZE = 64;
    private final @Nullable FeatureFlagUniverse universe;
    private final long mask;

    private FeatureFlagSet(@Nullable FeatureFlagUniverse universe, long mask) {
        this.universe = universe;
        this.mask = mask;
    }

    static FeatureFlagSet create(FeatureFlagUniverse universe, Collection<FeatureFlag> flags) {
        if (flags.isEmpty()) {
            return FeatureFlagSet.EMPTY;
        } else {
            long i = computeMask(universe, 0L, flags);

            return new FeatureFlagSet(universe, i);
        }
    }

    public static FeatureFlagSet of() {
        return FeatureFlagSet.EMPTY;
    }

    public static FeatureFlagSet of(FeatureFlag flag) {
        return new FeatureFlagSet(flag.universe, flag.mask);
    }

    public static FeatureFlagSet of(FeatureFlag flag, FeatureFlag... flags) {
        long i = flags.length == 0 ? flag.mask : computeMask(flag.universe, flag.mask, Arrays.asList(flags));

        return new FeatureFlagSet(flag.universe, i);
    }

    private static long computeMask(FeatureFlagUniverse universe, long mask, Iterable<FeatureFlag> flags) {
        for (FeatureFlag featureflag : flags) {
            if (universe != featureflag.universe) {
                String s = String.valueOf(universe);

                throw new IllegalStateException("Mismatched feature universe, expected '" + s + "', but got '" + String.valueOf(featureflag.universe) + "'");
            }

            mask |= featureflag.mask;
        }

        return mask;
    }

    public boolean contains(FeatureFlag flag) {
        return this.universe != flag.universe ? false : (this.mask & flag.mask) != 0L;
    }

    public boolean isEmpty() {
        return this.equals(FeatureFlagSet.EMPTY);
    }

    public boolean isSubsetOf(FeatureFlagSet set) {
        return this.universe == null ? true : (this.universe != set.universe ? false : (this.mask & ~set.mask) == 0L);
    }

    public boolean intersects(FeatureFlagSet set) {
        return this.universe != null && set.universe != null && this.universe == set.universe ? (this.mask & set.mask) != 0L : false;
    }

    public FeatureFlagSet join(FeatureFlagSet other) {
        if (this.universe == null) {
            return other;
        } else if (other.universe == null) {
            return this;
        } else if (this.universe != other.universe) {
            String s = String.valueOf(this.universe);

            throw new IllegalArgumentException("Mismatched set elements: '" + s + "' != '" + String.valueOf(other.universe) + "'");
        } else {
            return new FeatureFlagSet(this.universe, this.mask | other.mask);
        }
    }

    public FeatureFlagSet subtract(FeatureFlagSet other) {
        if (this.universe != null && other.universe != null) {
            if (this.universe != other.universe) {
                String s = String.valueOf(this.universe);

                throw new IllegalArgumentException("Mismatched set elements: '" + s + "' != '" + String.valueOf(other.universe) + "'");
            } else {
                long i = this.mask & ~other.mask;

                return i == 0L ? FeatureFlagSet.EMPTY : new FeatureFlagSet(this.universe, i);
            }
        } else {
            return this;
        }
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else {
            boolean flag;

            if (o instanceof FeatureFlagSet) {
                FeatureFlagSet featureflagset = (FeatureFlagSet) o;

                if (this.universe == featureflagset.universe && this.mask == featureflagset.mask) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }
    }

    public int hashCode() {
        return (int) HashCommon.mix(this.mask);
    }
}

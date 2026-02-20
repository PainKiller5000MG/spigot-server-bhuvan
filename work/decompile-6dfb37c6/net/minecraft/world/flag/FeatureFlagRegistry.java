package net.minecraft.world.flag;

import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public class FeatureFlagRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final FeatureFlagUniverse universe;
    public final Map<Identifier, FeatureFlag> names;
    private final FeatureFlagSet allFlags;

    private FeatureFlagRegistry(FeatureFlagUniverse universe, FeatureFlagSet allFlags, Map<Identifier, FeatureFlag> names) {
        this.universe = universe;
        this.names = names;
        this.allFlags = allFlags;
    }

    public boolean isSubset(FeatureFlagSet set) {
        return set.isSubsetOf(this.allFlags);
    }

    public FeatureFlagSet allFlags() {
        return this.allFlags;
    }

    public FeatureFlagSet fromNames(Iterable<Identifier> flagIds) {
        return this.fromNames(flagIds, (identifier) -> {
            FeatureFlagRegistry.LOGGER.warn("Unknown feature flag: {}", identifier);
        });
    }

    public FeatureFlagSet subset(FeatureFlag... flags) {
        return FeatureFlagSet.create(this.universe, Arrays.asList(flags));
    }

    public FeatureFlagSet fromNames(Iterable<Identifier> flagIds, Consumer<Identifier> unknownFlags) {
        Set<FeatureFlag> set = Sets.newIdentityHashSet();

        for (Identifier identifier : flagIds) {
            FeatureFlag featureflag = (FeatureFlag) this.names.get(identifier);

            if (featureflag == null) {
                unknownFlags.accept(identifier);
            } else {
                set.add(featureflag);
            }
        }

        return FeatureFlagSet.create(this.universe, set);
    }

    public Set<Identifier> toNames(FeatureFlagSet set) {
        Set<Identifier> set1 = new HashSet();

        this.names.forEach((identifier, featureflag) -> {
            if (set.contains(featureflag)) {
                set1.add(identifier);
            }

        });
        return set1;
    }

    public Codec<FeatureFlagSet> codec() {
        return Identifier.CODEC.listOf().comapFlatMap((list) -> {
            Set<Identifier> set = new HashSet();

            Objects.requireNonNull(set);
            FeatureFlagSet featureflagset = this.fromNames(list, set::add);

            return !set.isEmpty() ? DataResult.error(() -> {
                return "Unknown feature ids: " + String.valueOf(set);
            }, featureflagset) : DataResult.success(featureflagset);
        }, (featureflagset) -> {
            return List.copyOf(this.toNames(featureflagset));
        });
    }

    public static class Builder {

        private final FeatureFlagUniverse universe;
        private int id;
        private final Map<Identifier, FeatureFlag> flags = new LinkedHashMap();

        public Builder(String universeId) {
            this.universe = new FeatureFlagUniverse(universeId);
        }

        public FeatureFlag createVanilla(String name) {
            return this.create(Identifier.withDefaultNamespace(name));
        }

        public FeatureFlag create(Identifier name) {
            if (this.id >= 64) {
                throw new IllegalStateException("Too many feature flags");
            } else {
                FeatureFlag featureflag = new FeatureFlag(this.universe, this.id++);
                FeatureFlag featureflag1 = (FeatureFlag) this.flags.put(name, featureflag);

                if (featureflag1 != null) {
                    throw new IllegalStateException("Duplicate feature flag " + String.valueOf(name));
                } else {
                    return featureflag;
                }
            }
        }

        public FeatureFlagRegistry build() {
            FeatureFlagSet featureflagset = FeatureFlagSet.create(this.universe, this.flags.values());

            return new FeatureFlagRegistry(this.universe, featureflagset, Map.copyOf(this.flags));
        }
    }
}

package net.minecraft.world.flag;

import com.mojang.serialization.Codec;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;

public class FeatureFlags {

    public static final FeatureFlag VANILLA;
    public static final FeatureFlag TRADE_REBALANCE;
    public static final FeatureFlag REDSTONE_EXPERIMENTS;
    public static final FeatureFlag MINECART_IMPROVEMENTS;
    public static final FeatureFlagRegistry REGISTRY;
    public static final Codec<FeatureFlagSet> CODEC;
    public static final FeatureFlagSet VANILLA_SET;
    public static final FeatureFlagSet DEFAULT_FLAGS;

    public FeatureFlags() {}

    public static String printMissingFlags(FeatureFlagSet allowedFlags, FeatureFlagSet requestedFlags) {
        return printMissingFlags(FeatureFlags.REGISTRY, allowedFlags, requestedFlags);
    }

    public static String printMissingFlags(FeatureFlagRegistry registry, FeatureFlagSet allowedFlags, FeatureFlagSet requestedFlags) {
        Set<Identifier> set = registry.toNames(requestedFlags);
        Set<Identifier> set1 = registry.toNames(allowedFlags);

        return (String) set.stream().filter((identifier) -> {
            return !set1.contains(identifier);
        }).map(Identifier::toString).collect(Collectors.joining(", "));
    }

    public static boolean isExperimental(FeatureFlagSet features) {
        return !features.isSubsetOf(FeatureFlags.VANILLA_SET);
    }

    static {
        FeatureFlagRegistry.Builder featureflagregistry_builder = new FeatureFlagRegistry.Builder("main");

        VANILLA = featureflagregistry_builder.createVanilla("vanilla");
        TRADE_REBALANCE = featureflagregistry_builder.createVanilla("trade_rebalance");
        REDSTONE_EXPERIMENTS = featureflagregistry_builder.createVanilla("redstone_experiments");
        MINECART_IMPROVEMENTS = featureflagregistry_builder.createVanilla("minecart_improvements");
        REGISTRY = featureflagregistry_builder.build();
        CODEC = FeatureFlags.REGISTRY.codec();
        VANILLA_SET = FeatureFlagSet.of(FeatureFlags.VANILLA);
        DEFAULT_FLAGS = FeatureFlags.VANILLA_SET;
    }
}

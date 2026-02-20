package net.minecraft.world.level.biome;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;

public class BiomeGenerationSettings {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final BiomeGenerationSettings EMPTY = new BiomeGenerationSettings(HolderSet.direct(), List.of());
    public static final MapCodec<BiomeGenerationSettings> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        Codec codec = ConfiguredWorldCarver.LIST_CODEC;
        Logger logger = BiomeGenerationSettings.LOGGER;

        Objects.requireNonNull(logger);
        RecordCodecBuilder recordcodecbuilder = codec.promotePartial(Util.prefix("Carver: ", logger::error)).fieldOf("carvers").forGetter((biomegenerationsettings) -> {
            return biomegenerationsettings.carvers;
        });
        Codec codec1 = PlacedFeature.LIST_OF_LISTS_CODEC;
        Logger logger1 = BiomeGenerationSettings.LOGGER;

        Objects.requireNonNull(logger1);
        return instance.group(recordcodecbuilder, codec1.promotePartial(Util.prefix("Features: ", logger1::error)).fieldOf("features").forGetter((biomegenerationsettings) -> {
            return biomegenerationsettings.features;
        })).apply(instance, BiomeGenerationSettings::new);
    });
    private final HolderSet<ConfiguredWorldCarver<?>> carvers;
    private final List<HolderSet<PlacedFeature>> features;
    private final Supplier<List<ConfiguredFeature<?, ?>>> flowerFeatures;
    private final Supplier<Set<PlacedFeature>> featureSet;

    private BiomeGenerationSettings(HolderSet<ConfiguredWorldCarver<?>> carvers, List<HolderSet<PlacedFeature>> features) {
        this.carvers = carvers;
        this.features = features;
        this.flowerFeatures = Suppliers.memoize(() -> {
            return (List) features.stream().flatMap(HolderSet::stream).map(Holder::value).flatMap(PlacedFeature::getFeatures).filter((configuredfeature) -> {
                return configuredfeature.feature() == Feature.FLOWER;
            }).collect(ImmutableList.toImmutableList());
        });
        this.featureSet = Suppliers.memoize(() -> {
            return (Set) features.stream().flatMap(HolderSet::stream).map(Holder::value).collect(Collectors.toSet());
        });
    }

    public Iterable<Holder<ConfiguredWorldCarver<?>>> getCarvers() {
        return this.carvers;
    }

    public List<ConfiguredFeature<?, ?>> getFlowerFeatures() {
        return (List) this.flowerFeatures.get();
    }

    public List<HolderSet<PlacedFeature>> features() {
        return this.features;
    }

    public boolean hasFeature(PlacedFeature feature) {
        return ((Set) this.featureSet.get()).contains(feature);
    }

    public static class PlainBuilder {

        private final List<Holder<ConfiguredWorldCarver<?>>> carvers = new ArrayList();
        private final List<List<Holder<PlacedFeature>>> features = new ArrayList();

        public PlainBuilder() {}

        public BiomeGenerationSettings.PlainBuilder addFeature(GenerationStep.Decoration step, Holder<PlacedFeature> feature) {
            return this.addFeature(step.ordinal(), feature);
        }

        public BiomeGenerationSettings.PlainBuilder addFeature(int index, Holder<PlacedFeature> feature) {
            this.addFeatureStepsUpTo(index);
            ((List) this.features.get(index)).add(feature);
            return this;
        }

        public BiomeGenerationSettings.PlainBuilder addCarver(Holder<ConfiguredWorldCarver<?>> carver) {
            this.carvers.add(carver);
            return this;
        }

        private void addFeatureStepsUpTo(int index) {
            while (this.features.size() <= index) {
                this.features.add(Lists.newArrayList());
            }

        }

        public BiomeGenerationSettings build() {
            return new BiomeGenerationSettings(HolderSet.direct(this.carvers), (List) this.features.stream().map(HolderSet::direct).collect(ImmutableList.toImmutableList()));
        }
    }

    public static class Builder extends BiomeGenerationSettings.PlainBuilder {

        private final HolderGetter<PlacedFeature> placedFeatures;
        private final HolderGetter<ConfiguredWorldCarver<?>> worldCarvers;

        public Builder(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
            this.placedFeatures = placedFeatures;
            this.worldCarvers = worldCarvers;
        }

        public BiomeGenerationSettings.Builder addFeature(GenerationStep.Decoration step, ResourceKey<PlacedFeature> feature) {
            this.addFeature(step.ordinal(), this.placedFeatures.getOrThrow(feature));
            return this;
        }

        public BiomeGenerationSettings.Builder addCarver(ResourceKey<ConfiguredWorldCarver<?>> carver) {
            this.addCarver(this.worldCarvers.getOrThrow(carver));
            return this;
        }
    }
}

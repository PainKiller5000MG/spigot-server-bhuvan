package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.WeightedPlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class RandomFeatureConfiguration implements FeatureConfiguration {

    public static final Codec<RandomFeatureConfiguration> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.apply2(RandomFeatureConfiguration::new, WeightedPlacedFeature.CODEC.listOf().fieldOf("features").forGetter((randomfeatureconfiguration) -> {
            return randomfeatureconfiguration.features;
        }), PlacedFeature.CODEC.fieldOf("default").forGetter((randomfeatureconfiguration) -> {
            return randomfeatureconfiguration.defaultFeature;
        }));
    });
    public final List<WeightedPlacedFeature> features;
    public final Holder<PlacedFeature> defaultFeature;

    public RandomFeatureConfiguration(List<WeightedPlacedFeature> features, Holder<PlacedFeature> defaultFeature) {
        this.features = features;
        this.defaultFeature = defaultFeature;
    }

    @Override
    public Stream<ConfiguredFeature<?, ?>> getFeatures() {
        return Stream.concat(this.features.stream().flatMap((weightedplacedfeature) -> {
            return (weightedplacedfeature.feature.value()).getFeatures();
        }), (this.defaultFeature.value()).getFeatures());
    }
}

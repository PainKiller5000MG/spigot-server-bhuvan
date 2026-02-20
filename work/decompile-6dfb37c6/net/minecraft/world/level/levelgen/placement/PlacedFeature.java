package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.FeatureCountTracker;
import org.apache.commons.lang3.mutable.MutableBoolean;

public record PlacedFeature(Holder<ConfiguredFeature<?, ?>> feature, List<PlacementModifier> placement) {

    public static final Codec<PlacedFeature> DIRECT_CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(ConfiguredFeature.CODEC.fieldOf("feature").forGetter((placedfeature) -> {
            return placedfeature.feature;
        }), PlacementModifier.CODEC.listOf().fieldOf("placement").forGetter((placedfeature) -> {
            return placedfeature.placement;
        })).apply(instance, PlacedFeature::new);
    });
    public static final Codec<Holder<PlacedFeature>> CODEC = RegistryFileCodec.<Holder<PlacedFeature>>create(Registries.PLACED_FEATURE, PlacedFeature.DIRECT_CODEC);
    public static final Codec<HolderSet<PlacedFeature>> LIST_CODEC = RegistryCodecs.homogeneousList(Registries.PLACED_FEATURE, PlacedFeature.DIRECT_CODEC);
    public static final Codec<List<HolderSet<PlacedFeature>>> LIST_OF_LISTS_CODEC = RegistryCodecs.homogeneousList(Registries.PLACED_FEATURE, PlacedFeature.DIRECT_CODEC, true).listOf();

    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        return this.placeWithContext(new PlacementContext(level, generator, Optional.empty()), random, origin);
    }

    public boolean placeWithBiomeCheck(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        return this.placeWithContext(new PlacementContext(level, generator, Optional.of(this)), random, origin);
    }

    private boolean placeWithContext(PlacementContext context, RandomSource random, BlockPos origin) {
        Stream<BlockPos> stream = Stream.of(origin);

        for (PlacementModifier placementmodifier : this.placement) {
            stream = stream.flatMap((blockpos1) -> {
                return placementmodifier.getPositions(context, random, blockpos1);
            });
        }

        ConfiguredFeature<?, ?> configuredfeature = this.feature.value();
        MutableBoolean mutableboolean = new MutableBoolean();

        stream.forEach((blockpos1) -> {
            if (configuredfeature.place(context.getLevel(), context.generator(), random, blockpos1)) {
                mutableboolean.setTrue();
                if (SharedConstants.DEBUG_FEATURE_COUNT) {
                    FeatureCountTracker.featurePlaced(context.getLevel().getLevel(), configuredfeature, context.topFeature());
                }
            }

        });
        return mutableboolean.isTrue();
    }

    public Stream<ConfiguredFeature<?, ?>> getFeatures() {
        return ((ConfiguredFeature) this.feature.value()).getFeatures();
    }

    public String toString() {
        return "Placed " + String.valueOf(this.feature);
    }
}

package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.configurations.RandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class RandomSelectorFeature extends Feature<RandomFeatureConfiguration> {

    public RandomSelectorFeature(Codec<RandomFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<RandomFeatureConfiguration> context) {
        RandomFeatureConfiguration randomfeatureconfiguration = context.config();
        RandomSource randomsource = context.random();
        WorldGenLevel worldgenlevel = context.level();
        ChunkGenerator chunkgenerator = context.chunkGenerator();
        BlockPos blockpos = context.origin();

        for (WeightedPlacedFeature weightedplacedfeature : randomfeatureconfiguration.features) {
            if (randomsource.nextFloat() < weightedplacedfeature.chance) {
                return weightedplacedfeature.place(worldgenlevel, chunkgenerator, randomsource, blockpos);
            }
        }

        return ((PlacedFeature) randomfeatureconfiguration.defaultFeature.value()).place(worldgenlevel, chunkgenerator, randomsource, blockpos);
    }
}

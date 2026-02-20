package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleRandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class SimpleRandomSelectorFeature extends Feature<SimpleRandomFeatureConfiguration> {

    public SimpleRandomSelectorFeature(Codec<SimpleRandomFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<SimpleRandomFeatureConfiguration> context) {
        RandomSource randomsource = context.random();
        SimpleRandomFeatureConfiguration simplerandomfeatureconfiguration = context.config();
        WorldGenLevel worldgenlevel = context.level();
        BlockPos blockpos = context.origin();
        ChunkGenerator chunkgenerator = context.chunkGenerator();
        int i = randomsource.nextInt(simplerandomfeatureconfiguration.features.size());
        PlacedFeature placedfeature = simplerandomfeatureconfiguration.features.get(i).value();

        return placedfeature.place(worldgenlevel, chunkgenerator, randomsource, blockpos);
    }
}

package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class RandomPatchFeature extends Feature<RandomPatchConfiguration> {

    public RandomPatchFeature(Codec<RandomPatchConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<RandomPatchConfiguration> context) {
        RandomPatchConfiguration randompatchconfiguration = context.config();
        RandomSource randomsource = context.random();
        BlockPos blockpos = context.origin();
        WorldGenLevel worldgenlevel = context.level();
        int i = 0;
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        int j = randompatchconfiguration.xzSpread() + 1;
        int k = randompatchconfiguration.ySpread() + 1;

        for (int l = 0; l < randompatchconfiguration.tries(); ++l) {
            blockpos_mutableblockpos.setWithOffset(blockpos, randomsource.nextInt(j) - randomsource.nextInt(j), randomsource.nextInt(k) - randomsource.nextInt(k), randomsource.nextInt(j) - randomsource.nextInt(j));
            if (((PlacedFeature) randompatchconfiguration.feature().value()).place(worldgenlevel, context.chunkGenerator(), randomsource, blockpos_mutableblockpos)) {
                ++i;
            }
        }

        return i > 0;
    }
}

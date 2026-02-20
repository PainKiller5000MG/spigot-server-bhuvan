package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

public class ScatteredOreFeature extends Feature<OreConfiguration> {

    private static final int MAX_DIST_FROM_ORIGIN = 7;

    ScatteredOreFeature(Codec<OreConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<OreConfiguration> context) {
        WorldGenLevel worldgenlevel = context.level();
        RandomSource randomsource = context.random();
        OreConfiguration oreconfiguration = context.config();
        BlockPos blockpos = context.origin();
        int i = randomsource.nextInt(oreconfiguration.size + 1);
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (int j = 0; j < i; ++j) {
            this.offsetTargetPos(blockpos_mutableblockpos, randomsource, blockpos, Math.min(j, 7));
            BlockState blockstate = worldgenlevel.getBlockState(blockpos_mutableblockpos);

            for (OreConfiguration.TargetBlockState oreconfiguration_targetblockstate : oreconfiguration.targetStates) {
                Objects.requireNonNull(worldgenlevel);
                if (OreFeature.canPlaceOre(blockstate, worldgenlevel::getBlockState, randomsource, oreconfiguration, oreconfiguration_targetblockstate, blockpos_mutableblockpos)) {
                    worldgenlevel.setBlock(blockpos_mutableblockpos, oreconfiguration_targetblockstate.state, 2);
                    break;
                }
            }
        }

        return true;
    }

    private void offsetTargetPos(BlockPos.MutableBlockPos targetPos, RandomSource random, BlockPos origin, int maxDistFromOriginForThisTry) {
        int j = this.getRandomPlacementInOneAxisRelativeToOrigin(random, maxDistFromOriginForThisTry);
        int k = this.getRandomPlacementInOneAxisRelativeToOrigin(random, maxDistFromOriginForThisTry);
        int l = this.getRandomPlacementInOneAxisRelativeToOrigin(random, maxDistFromOriginForThisTry);

        targetPos.setWithOffset(origin, j, k, l);
    }

    private int getRandomPlacementInOneAxisRelativeToOrigin(RandomSource random, int maxDistanceFromOrigin) {
        return Math.round((random.nextFloat() - random.nextFloat()) * (float) maxDistanceFromOrigin);
    }
}

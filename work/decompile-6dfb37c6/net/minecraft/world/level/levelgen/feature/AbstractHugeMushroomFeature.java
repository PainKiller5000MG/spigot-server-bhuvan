package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;

public abstract class AbstractHugeMushroomFeature extends Feature<HugeMushroomFeatureConfiguration> {

    public AbstractHugeMushroomFeature(Codec<HugeMushroomFeatureConfiguration> codec) {
        super(codec);
    }

    protected void placeTrunk(LevelAccessor level, RandomSource random, BlockPos origin, HugeMushroomFeatureConfiguration config, int treeHeight, BlockPos.MutableBlockPos blockPos) {
        for (int j = 0; j < treeHeight; ++j) {
            blockPos.set(origin).move(Direction.UP, j);
            this.placeMushroomBlock(level, blockPos, config.stemProvider.getState(random, origin));
        }

    }

    protected void placeMushroomBlock(LevelAccessor level, BlockPos.MutableBlockPos blockPos, BlockState newState) {
        BlockState blockstate1 = level.getBlockState(blockPos);

        if (blockstate1.isAir() || blockstate1.is(BlockTags.REPLACEABLE_BY_MUSHROOMS)) {
            this.setBlock(level, blockPos, newState);
        }

    }

    protected int getTreeHeight(RandomSource random) {
        int i = random.nextInt(3) + 4;

        if (random.nextInt(12) == 0) {
            i *= 2;
        }

        return i;
    }

    protected boolean isValidPosition(LevelAccessor level, BlockPos origin, int treeHeight, BlockPos.MutableBlockPos blockPos, HugeMushroomFeatureConfiguration config) {
        int j = origin.getY();

        if (j >= level.getMinY() + 1 && j + treeHeight + 1 <= level.getMaxY()) {
            BlockState blockstate = level.getBlockState(origin.below());

            if (!isDirt(blockstate) && !blockstate.is(BlockTags.MUSHROOM_GROW_BLOCK)) {
                return false;
            } else {
                for (int k = 0; k <= treeHeight; ++k) {
                    int l = this.getTreeRadiusForHeight(-1, -1, config.foliageRadius, k);

                    for (int i1 = -l; i1 <= l; ++i1) {
                        for (int j1 = -l; j1 <= l; ++j1) {
                            BlockState blockstate1 = level.getBlockState(blockPos.setWithOffset(origin, i1, k, j1));

                            if (!blockstate1.isAir() && !blockstate1.is(BlockTags.LEAVES)) {
                                return false;
                            }
                        }
                    }
                }

                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<HugeMushroomFeatureConfiguration> context) {
        WorldGenLevel worldgenlevel = context.level();
        BlockPos blockpos = context.origin();
        RandomSource randomsource = context.random();
        HugeMushroomFeatureConfiguration hugemushroomfeatureconfiguration = context.config();
        int i = this.getTreeHeight(randomsource);
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        if (!this.isValidPosition(worldgenlevel, blockpos, i, blockpos_mutableblockpos, hugemushroomfeatureconfiguration)) {
            return false;
        } else {
            this.makeCap(worldgenlevel, randomsource, blockpos, i, blockpos_mutableblockpos, hugemushroomfeatureconfiguration);
            this.placeTrunk(worldgenlevel, randomsource, blockpos, hugemushroomfeatureconfiguration, i, blockpos_mutableblockpos);
            return true;
        }
    }

    protected abstract int getTreeRadiusForHeight(int trunkHeight, int treeHeight, int leafRadius, int yo);

    protected abstract void makeCap(LevelAccessor level, RandomSource random, BlockPos origin, int treeHeight, BlockPos.MutableBlockPos blockPos, HugeMushroomFeatureConfiguration config);
}

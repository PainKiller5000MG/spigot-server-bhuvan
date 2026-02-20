package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.BlockPileConfiguration;

public class BlockPileFeature extends Feature<BlockPileConfiguration> {

    public BlockPileFeature(Codec<BlockPileConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<BlockPileConfiguration> context) {
        BlockPos blockpos = context.origin();
        WorldGenLevel worldgenlevel = context.level();
        RandomSource randomsource = context.random();
        BlockPileConfiguration blockpileconfiguration = context.config();

        if (blockpos.getY() < worldgenlevel.getMinY() + 5) {
            return false;
        } else {
            int i = 2 + randomsource.nextInt(2);
            int j = 2 + randomsource.nextInt(2);

            for (BlockPos blockpos1 : BlockPos.betweenClosed(blockpos.offset(-i, 0, -j), blockpos.offset(i, 1, j))) {
                int k = blockpos.getX() - blockpos1.getX();
                int l = blockpos.getZ() - blockpos1.getZ();

                if ((float) (k * k + l * l) <= randomsource.nextFloat() * 10.0F - randomsource.nextFloat() * 6.0F) {
                    this.tryPlaceBlock(worldgenlevel, blockpos1, randomsource, blockpileconfiguration);
                } else if ((double) randomsource.nextFloat() < 0.031D) {
                    this.tryPlaceBlock(worldgenlevel, blockpos1, randomsource, blockpileconfiguration);
                }
            }

            return true;
        }
    }

    private boolean mayPlaceOn(LevelAccessor level, BlockPos blockPos, RandomSource random) {
        BlockPos blockpos1 = blockPos.below();
        BlockState blockstate = level.getBlockState(blockpos1);

        return blockstate.is(Blocks.DIRT_PATH) ? random.nextBoolean() : blockstate.isFaceSturdy(level, blockpos1, Direction.UP);
    }

    private void tryPlaceBlock(LevelAccessor level, BlockPos blockPos, RandomSource random, BlockPileConfiguration config) {
        if (level.isEmptyBlock(blockPos) && this.mayPlaceOn(level, blockPos, random)) {
            level.setBlock(blockPos, config.stateProvider.getState(random, blockPos), 260);
        }

    }
}

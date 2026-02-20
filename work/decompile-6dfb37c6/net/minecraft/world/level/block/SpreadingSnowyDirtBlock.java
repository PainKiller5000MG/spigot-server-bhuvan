package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;

public abstract class SpreadingSnowyDirtBlock extends SnowyDirtBlock {

    protected SpreadingSnowyDirtBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    private static boolean canBeGrass(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockpos1 = pos.above();
        BlockState blockstate1 = level.getBlockState(blockpos1);

        if (blockstate1.is(Blocks.SNOW) && (Integer) blockstate1.getValue(SnowLayerBlock.LAYERS) == 1) {
            return true;
        } else if (blockstate1.getFluidState().getAmount() == 8) {
            return false;
        } else {
            int i = LightEngine.getLightBlockInto(state, blockstate1, Direction.UP, blockstate1.getLightBlock());

            return i < 15;
        }
    }

    @Override
    protected abstract MapCodec<? extends SpreadingSnowyDirtBlock> codec();

    private static boolean canPropagate(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockpos1 = pos.above();

        return canBeGrass(state, level, pos) && !level.getFluidState(blockpos1).is(FluidTags.WATER);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!canBeGrass(state, level, pos)) {
            level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        } else {
            if (level.getMaxLocalRawBrightness(pos.above()) >= 9) {
                BlockState blockstate1 = this.defaultBlockState();

                for (int i = 0; i < 4; ++i) {
                    BlockPos blockpos1 = pos.offset(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);

                    if (level.getBlockState(blockpos1).is(Blocks.DIRT) && canPropagate(blockstate1, level, blockpos1)) {
                        level.setBlockAndUpdate(blockpos1, (BlockState) blockstate1.setValue(SpreadingSnowyDirtBlock.SNOWY, isSnowySetting(level.getBlockState(blockpos1.above()))));
                    }
                }
            }

        }
    }
}

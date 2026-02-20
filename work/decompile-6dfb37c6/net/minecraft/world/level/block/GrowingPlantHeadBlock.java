package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class GrowingPlantHeadBlock extends GrowingPlantBlock implements BonemealableBlock {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_25;
    public static final int MAX_AGE = 25;
    private final double growPerTickProbability;

    protected GrowingPlantHeadBlock(BlockBehaviour.Properties properties, Direction growthDirection, VoxelShape shape, boolean scheduleFluidTicks, double growPerTickProbability) {
        super(properties, growthDirection, shape, scheduleFluidTicks);
        this.growPerTickProbability = growPerTickProbability;
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(GrowingPlantHeadBlock.AGE, 0));
    }

    @Override
    protected abstract MapCodec<? extends GrowingPlantHeadBlock> codec();

    @Override
    public BlockState getStateForPlacement(RandomSource random) {
        return (BlockState) this.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, random.nextInt(25));
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return (Integer) state.getValue(GrowingPlantHeadBlock.AGE) < 25;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if ((Integer) state.getValue(GrowingPlantHeadBlock.AGE) < 25 && random.nextDouble() < this.growPerTickProbability) {
            BlockPos blockpos1 = pos.relative(this.growthDirection);

            if (this.canGrowInto(level.getBlockState(blockpos1))) {
                level.setBlockAndUpdate(blockpos1, this.getGrowIntoState(state, level.random));
            }
        }

    }

    protected BlockState getGrowIntoState(BlockState growFromState, RandomSource random) {
        return (BlockState) growFromState.cycle(GrowingPlantHeadBlock.AGE);
    }

    public BlockState getMaxAgeState(BlockState fromState) {
        return (BlockState) fromState.setValue(GrowingPlantHeadBlock.AGE, 25);
    }

    public boolean isMaxAge(BlockState state) {
        return (Integer) state.getValue(GrowingPlantHeadBlock.AGE) == 25;
    }

    protected BlockState updateBodyAfterConvertedFromHead(BlockState headState, BlockState bodyState) {
        return bodyState;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (directionToNeighbour == this.growthDirection.getOpposite()) {
            if (!state.canSurvive(level, pos)) {
                ticks.scheduleTick(pos, (Block) this, 1);
            } else {
                BlockState blockstate2 = level.getBlockState(pos.relative(this.growthDirection));

                if (blockstate2.is(this) || blockstate2.is(this.getBodyBlock())) {
                    return this.updateBodyAfterConvertedFromHead(state, this.getBodyBlock().defaultBlockState());
                }
            }
        }

        if (directionToNeighbour != this.growthDirection || !neighbourState.is(this) && !neighbourState.is(this.getBodyBlock())) {
            if (this.scheduleFluidTicks) {
                ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
            }

            return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
        } else {
            return this.updateBodyAfterConvertedFromHead(state, this.getBodyBlock().defaultBlockState());
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GrowingPlantHeadBlock.AGE);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return this.canGrowInto(level.getBlockState(pos.relative(this.growthDirection)));
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos blockpos1 = pos.relative(this.growthDirection);
        int i = Math.min((Integer) state.getValue(GrowingPlantHeadBlock.AGE) + 1, 25);
        int j = this.getBlocksToGrowWhenBonemealed(random);

        for (int k = 0; k < j && this.canGrowInto(level.getBlockState(blockpos1)); ++k) {
            level.setBlockAndUpdate(blockpos1, (BlockState) state.setValue(GrowingPlantHeadBlock.AGE, i));
            blockpos1 = blockpos1.relative(this.growthDirection);
            i = Math.min(i + 1, 25);
        }

    }

    protected abstract int getBlocksToGrowWhenBonemealed(RandomSource random);

    protected abstract boolean canGrowInto(BlockState state);

    @Override
    protected GrowingPlantHeadBlock getHeadBlock() {
        return this;
    }
}

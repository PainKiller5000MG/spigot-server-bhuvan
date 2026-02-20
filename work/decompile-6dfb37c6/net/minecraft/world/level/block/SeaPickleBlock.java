package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class SeaPickleBlock extends VegetationBlock implements SimpleWaterloggedBlock, BonemealableBlock {

    public static final MapCodec<SeaPickleBlock> CODEC = simpleCodec(SeaPickleBlock::new);
    public static final int MAX_PICKLES = 4;
    public static final IntegerProperty PICKLES = BlockStateProperties.PICKLES;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE_ONE = Block.column(4.0D, 0.0D, 6.0D);
    private static final VoxelShape SHAPE_TWO = Block.column(10.0D, 0.0D, 6.0D);
    private static final VoxelShape SHAPE_THREE = Block.column(12.0D, 0.0D, 6.0D);
    private static final VoxelShape SHAPE_FOUR = Block.column(12.0D, 0.0D, 7.0D);

    @Override
    public MapCodec<SeaPickleBlock> codec() {
        return SeaPickleBlock.CODEC;
    }

    protected SeaPickleBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(SeaPickleBlock.PICKLES, 1)).setValue(SeaPickleBlock.WATERLOGGED, true));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockstate = context.getLevel().getBlockState(context.getClickedPos());

        if (blockstate.is(this)) {
            return (BlockState) blockstate.setValue(SeaPickleBlock.PICKLES, Math.min(4, (Integer) blockstate.getValue(SeaPickleBlock.PICKLES) + 1));
        } else {
            FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
            boolean flag = fluidstate.getType() == Fluids.WATER;

            return (BlockState) super.getStateForPlacement(context).setValue(SeaPickleBlock.WATERLOGGED, flag);
        }
    }

    public static boolean isDead(BlockState state) {
        return !(Boolean) state.getValue(SeaPickleBlock.WATERLOGGED);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.getCollisionShape(level, pos).getFaceShape(Direction.UP).isEmpty() || state.isFaceSturdy(level, pos, Direction.UP);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockpos1 = pos.below();

        return this.mayPlaceOn(level.getBlockState(blockpos1), level, blockpos1);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            if ((Boolean) state.getValue(SeaPickleBlock.WATERLOGGED)) {
                ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
            }

            return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
        }
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return !context.isSecondaryUseActive() && context.getItemInHand().is(this.asItem()) && (Integer) state.getValue(SeaPickleBlock.PICKLES) < 4 ? true : super.canBeReplaced(state, context);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape voxelshape;

        switch ((Integer) state.getValue(SeaPickleBlock.PICKLES)) {
            case 2:
                voxelshape = SeaPickleBlock.SHAPE_TWO;
                break;
            case 3:
                voxelshape = SeaPickleBlock.SHAPE_THREE;
                break;
            case 4:
                voxelshape = SeaPickleBlock.SHAPE_FOUR;
                break;
            default:
                voxelshape = SeaPickleBlock.SHAPE_ONE;
        }

        return voxelshape;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(SeaPickleBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SeaPickleBlock.PICKLES, SeaPickleBlock.WATERLOGGED);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return !isDead(state) && level.getBlockState(pos.below()).is(BlockTags.CORAL_BLOCKS);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        int i = 5;
        int j = 1;
        int k = 2;
        int l = 0;
        int i1 = pos.getX() - 2;
        int j1 = 0;

        for (int k1 = 0; k1 < 5; ++k1) {
            for (int l1 = 0; l1 < j; ++l1) {
                int i2 = 2 + pos.getY() - 1;

                for (int j2 = i2 - 2; j2 < i2; ++j2) {
                    BlockPos blockpos1 = new BlockPos(i1 + k1, j2, pos.getZ() - j1 + l1);

                    if (!blockpos1.equals(pos) && random.nextInt(6) == 0 && level.getBlockState(blockpos1).is(Blocks.WATER)) {
                        BlockState blockstate1 = level.getBlockState(blockpos1.below());

                        if (blockstate1.is(BlockTags.CORAL_BLOCKS)) {
                            level.setBlock(blockpos1, (BlockState) Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, random.nextInt(4) + 1), 3);
                        }
                    }
                }
            }

            if (l < 2) {
                j += 2;
                ++j1;
            } else {
                j -= 2;
                --j1;
            }

            ++l;
        }

        level.setBlock(pos, (BlockState) state.setValue(SeaPickleBlock.PICKLES, 4), 2);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}

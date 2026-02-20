package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ScaffoldingBlock extends Block implements SimpleWaterloggedBlock {

    public static final MapCodec<ScaffoldingBlock> CODEC = simpleCodec(ScaffoldingBlock::new);
    private static final int TICK_DELAY = 1;
    private static final VoxelShape SHAPE_STABLE = Shapes.or(Block.column(16.0D, 14.0D, 16.0D), (VoxelShape) Shapes.rotateHorizontal(Block.box(0.0D, 0.0D, 0.0D, 2.0D, 16.0D, 2.0D)).values().stream().reduce(Shapes.empty(), Shapes::or));
    private static final VoxelShape SHAPE_UNSTABLE_BOTTOM = Block.column(16.0D, 0.0D, 2.0D);
    private static final VoxelShape SHAPE_UNSTABLE = Shapes.or(ScaffoldingBlock.SHAPE_STABLE, ScaffoldingBlock.SHAPE_UNSTABLE_BOTTOM, (VoxelShape) Shapes.rotateHorizontal(Block.boxZ(16.0D, 0.0D, 2.0D, 0.0D, 2.0D)).values().stream().reduce(Shapes.empty(), Shapes::or));
    private static final VoxelShape SHAPE_BELOW_BLOCK = Shapes.block().move(0.0D, -1.0D, 0.0D).optimize();
    public static final int STABILITY_MAX_DISTANCE = 7;
    public static final IntegerProperty DISTANCE = BlockStateProperties.STABILITY_DISTANCE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty BOTTOM = BlockStateProperties.BOTTOM;

    @Override
    public MapCodec<ScaffoldingBlock> codec() {
        return ScaffoldingBlock.CODEC;
    }

    protected ScaffoldingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(ScaffoldingBlock.DISTANCE, 7)).setValue(ScaffoldingBlock.WATERLOGGED, false)).setValue(ScaffoldingBlock.BOTTOM, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ScaffoldingBlock.DISTANCE, ScaffoldingBlock.WATERLOGGED, ScaffoldingBlock.BOTTOM);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return !context.isHoldingItem(state.getBlock().asItem()) ? ((Boolean) state.getValue(ScaffoldingBlock.BOTTOM) ? ScaffoldingBlock.SHAPE_UNSTABLE : ScaffoldingBlock.SHAPE_STABLE) : Shapes.block();
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().is(this.asItem());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos blockpos = context.getClickedPos();
        Level level = context.getLevel();
        int i = getDistance(level, blockpos);

        return (BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(ScaffoldingBlock.WATERLOGGED, level.getFluidState(blockpos).getType() == Fluids.WATER)).setValue(ScaffoldingBlock.DISTANCE, i)).setValue(ScaffoldingBlock.BOTTOM, this.isBottom(level, blockpos, i));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide()) {
            level.scheduleTick(pos, (Block) this, 1);
        }

    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(ScaffoldingBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        if (!level.isClientSide()) {
            ticks.scheduleTick(pos, (Block) this, 1);
        }

        return state;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int i = getDistance(level, pos);
        BlockState blockstate1 = (BlockState) ((BlockState) state.setValue(ScaffoldingBlock.DISTANCE, i)).setValue(ScaffoldingBlock.BOTTOM, this.isBottom(level, pos, i));

        if ((Integer) blockstate1.getValue(ScaffoldingBlock.DISTANCE) == 7) {
            if ((Integer) state.getValue(ScaffoldingBlock.DISTANCE) == 7) {
                FallingBlockEntity.fall(level, pos, blockstate1);
            } else {
                level.destroyBlock(pos, true);
            }
        } else if (state != blockstate1) {
            level.setBlock(pos, blockstate1, 3);
        }

    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return getDistance(level, pos) < 7;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return context.isPlacement() ? Shapes.empty() : (context.isAbove(Shapes.block(), pos, true) && !context.isDescending() ? ScaffoldingBlock.SHAPE_STABLE : ((Integer) state.getValue(ScaffoldingBlock.DISTANCE) != 0 && (Boolean) state.getValue(ScaffoldingBlock.BOTTOM) && context.isAbove(ScaffoldingBlock.SHAPE_BELOW_BLOCK, pos, true) ? ScaffoldingBlock.SHAPE_UNSTABLE_BOTTOM : Shapes.empty()));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(ScaffoldingBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    private boolean isBottom(BlockGetter level, BlockPos pos, int distance) {
        return distance > 0 && !level.getBlockState(pos.below()).is(this);
    }

    public static int getDistance(BlockGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = pos.mutable().move(Direction.DOWN);
        BlockState blockstate = level.getBlockState(blockpos_mutableblockpos);
        int i = 7;

        if (blockstate.is(Blocks.SCAFFOLDING)) {
            i = (Integer) blockstate.getValue(ScaffoldingBlock.DISTANCE);
        } else if (blockstate.isFaceSturdy(level, blockpos_mutableblockpos, Direction.UP)) {
            return 0;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState blockstate1 = level.getBlockState(blockpos_mutableblockpos.setWithOffset(pos, direction));

            if (blockstate1.is(Blocks.SCAFFOLDING)) {
                i = Math.min(i, (Integer) blockstate1.getValue(ScaffoldingBlock.DISTANCE) + 1);
                if (i == 1) {
                    break;
                }
            }
        }

        return i;
    }
}

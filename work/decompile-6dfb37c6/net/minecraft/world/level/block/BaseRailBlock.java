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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public abstract class BaseRailBlock extends Block implements SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE_FLAT = Block.column(16.0D, 0.0D, 2.0D);
    private static final VoxelShape SHAPE_SLOPE = Block.column(16.0D, 0.0D, 8.0D);
    private final boolean isStraight;

    public static boolean isRail(Level level, BlockPos pos) {
        return isRail(level.getBlockState(pos));
    }

    public static boolean isRail(BlockState state) {
        return state.is(BlockTags.RAILS) && state.getBlock() instanceof BaseRailBlock;
    }

    protected BaseRailBlock(boolean isStraight, BlockBehaviour.Properties properties) {
        super(properties);
        this.isStraight = isStraight;
    }

    @Override
    protected abstract MapCodec<? extends BaseRailBlock> codec();

    public boolean isStraight() {
        return this.isStraight;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ((RailShape) state.getValue(this.getShapeProperty())).isSlope() ? BaseRailBlock.SHAPE_SLOPE : BaseRailBlock.SHAPE_FLAT;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSupportRigidBlock(level, pos.below());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            this.updateState(state, level, pos, movedByPiston);
        }
    }

    protected BlockState updateState(BlockState state, Level level, BlockPos pos, boolean movedByPiston) {
        state = this.updateDir(level, pos, state, true);
        if (this.isStraight) {
            level.neighborChanged(state, pos, this, (Orientation) null, movedByPiston);
        }

        return state;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide() && level.getBlockState(pos).is(this)) {
            RailShape railshape = (RailShape) state.getValue(this.getShapeProperty());

            if (shouldBeRemoved(pos, level, railshape)) {
                dropResources(state, level, pos);
                level.removeBlock(pos, movedByPiston);
            } else {
                this.updateState(state, level, pos, block);
            }

        }
    }

    private static boolean shouldBeRemoved(BlockPos pos, Level level, RailShape shape) {
        if (!canSupportRigidBlock(level, pos.below())) {
            return true;
        } else {
            switch (shape) {
                case ASCENDING_EAST:
                    return !canSupportRigidBlock(level, pos.east());
                case ASCENDING_WEST:
                    return !canSupportRigidBlock(level, pos.west());
                case ASCENDING_NORTH:
                    return !canSupportRigidBlock(level, pos.north());
                case ASCENDING_SOUTH:
                    return !canSupportRigidBlock(level, pos.south());
                default:
                    return false;
            }
        }
    }

    protected void updateState(BlockState state, Level level, BlockPos pos, Block block) {}

    protected BlockState updateDir(Level level, BlockPos pos, BlockState state, boolean first) {
        if (level.isClientSide()) {
            return state;
        } else {
            RailShape railshape = (RailShape) state.getValue(this.getShapeProperty());

            return (new RailState(level, pos, state)).place(level.hasNeighborSignal(pos), first, railshape).getState();
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston) {
            if (((RailShape) state.getValue(this.getShapeProperty())).isSlope()) {
                level.updateNeighborsAt(pos.above(), this);
            }

            if (this.isStraight) {
                level.updateNeighborsAt(pos, this);
                level.updateNeighborsAt(pos.below(), this);
            }

        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        boolean flag = fluidstate.getType() == Fluids.WATER;
        BlockState blockstate = super.defaultBlockState();
        Direction direction = context.getHorizontalDirection();
        boolean flag1 = direction == Direction.EAST || direction == Direction.WEST;

        return (BlockState) ((BlockState) blockstate.setValue(this.getShapeProperty(), flag1 ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH)).setValue(BaseRailBlock.WATERLOGGED, flag);
    }

    public abstract Property<RailShape> getShapeProperty();

    protected RailShape rotate(RailShape shape, Rotation rotation) {
        RailShape railshape1;

        switch (rotation) {
            case CLOCKWISE_180:
                switch (shape) {
                    case ASCENDING_EAST:
                        railshape1 = RailShape.ASCENDING_WEST;
                        return railshape1;
                    case ASCENDING_WEST:
                        railshape1 = RailShape.ASCENDING_EAST;
                        return railshape1;
                    case ASCENDING_NORTH:
                        railshape1 = RailShape.ASCENDING_SOUTH;
                        return railshape1;
                    case ASCENDING_SOUTH:
                        railshape1 = RailShape.ASCENDING_NORTH;
                        return railshape1;
                    case NORTH_SOUTH:
                        railshape1 = RailShape.NORTH_SOUTH;
                        return railshape1;
                    case EAST_WEST:
                        railshape1 = RailShape.EAST_WEST;
                        return railshape1;
                    case SOUTH_EAST:
                        railshape1 = RailShape.NORTH_WEST;
                        return railshape1;
                    case SOUTH_WEST:
                        railshape1 = RailShape.NORTH_EAST;
                        return railshape1;
                    case NORTH_WEST:
                        railshape1 = RailShape.SOUTH_EAST;
                        return railshape1;
                    case NORTH_EAST:
                        railshape1 = RailShape.SOUTH_WEST;
                        return railshape1;
                    default:
                        throw new MatchException((String) null, (Throwable) null);
                }
            case COUNTERCLOCKWISE_90:
                switch (shape) {
                    case ASCENDING_EAST:
                        railshape1 = RailShape.ASCENDING_NORTH;
                        return railshape1;
                    case ASCENDING_WEST:
                        railshape1 = RailShape.ASCENDING_SOUTH;
                        return railshape1;
                    case ASCENDING_NORTH:
                        railshape1 = RailShape.ASCENDING_WEST;
                        return railshape1;
                    case ASCENDING_SOUTH:
                        railshape1 = RailShape.ASCENDING_EAST;
                        return railshape1;
                    case NORTH_SOUTH:
                        railshape1 = RailShape.EAST_WEST;
                        return railshape1;
                    case EAST_WEST:
                        railshape1 = RailShape.NORTH_SOUTH;
                        return railshape1;
                    case SOUTH_EAST:
                        railshape1 = RailShape.NORTH_EAST;
                        return railshape1;
                    case SOUTH_WEST:
                        railshape1 = RailShape.SOUTH_EAST;
                        return railshape1;
                    case NORTH_WEST:
                        railshape1 = RailShape.SOUTH_WEST;
                        return railshape1;
                    case NORTH_EAST:
                        railshape1 = RailShape.NORTH_WEST;
                        return railshape1;
                    default:
                        throw new MatchException((String) null, (Throwable) null);
                }
            case CLOCKWISE_90:
                switch (shape) {
                    case ASCENDING_EAST:
                        railshape1 = RailShape.ASCENDING_SOUTH;
                        return railshape1;
                    case ASCENDING_WEST:
                        railshape1 = RailShape.ASCENDING_NORTH;
                        return railshape1;
                    case ASCENDING_NORTH:
                        railshape1 = RailShape.ASCENDING_EAST;
                        return railshape1;
                    case ASCENDING_SOUTH:
                        railshape1 = RailShape.ASCENDING_WEST;
                        return railshape1;
                    case NORTH_SOUTH:
                        railshape1 = RailShape.EAST_WEST;
                        return railshape1;
                    case EAST_WEST:
                        railshape1 = RailShape.NORTH_SOUTH;
                        return railshape1;
                    case SOUTH_EAST:
                        railshape1 = RailShape.SOUTH_WEST;
                        return railshape1;
                    case SOUTH_WEST:
                        railshape1 = RailShape.NORTH_WEST;
                        return railshape1;
                    case NORTH_WEST:
                        railshape1 = RailShape.NORTH_EAST;
                        return railshape1;
                    case NORTH_EAST:
                        railshape1 = RailShape.SOUTH_EAST;
                        return railshape1;
                    default:
                        throw new MatchException((String) null, (Throwable) null);
                }
            default:
                railshape1 = shape;
                return railshape1;
        }
    }

    protected RailShape mirror(RailShape shape, Mirror mirror) {
        RailShape railshape1;

        switch (mirror) {
            case LEFT_RIGHT:
                switch (shape) {
                    case ASCENDING_NORTH:
                        railshape1 = RailShape.ASCENDING_SOUTH;
                        return railshape1;
                    case ASCENDING_SOUTH:
                        railshape1 = RailShape.ASCENDING_NORTH;
                        return railshape1;
                    case NORTH_SOUTH:
                    case EAST_WEST:
                    default:
                        railshape1 = shape;
                        return railshape1;
                    case SOUTH_EAST:
                        railshape1 = RailShape.NORTH_EAST;
                        return railshape1;
                    case SOUTH_WEST:
                        railshape1 = RailShape.NORTH_WEST;
                        return railshape1;
                    case NORTH_WEST:
                        railshape1 = RailShape.SOUTH_WEST;
                        return railshape1;
                    case NORTH_EAST:
                        railshape1 = RailShape.SOUTH_EAST;
                        return railshape1;
                }
            case FRONT_BACK:
                switch (shape) {
                    case ASCENDING_EAST:
                        railshape1 = RailShape.ASCENDING_WEST;
                        return railshape1;
                    case ASCENDING_WEST:
                        railshape1 = RailShape.ASCENDING_EAST;
                        return railshape1;
                    case ASCENDING_NORTH:
                    case ASCENDING_SOUTH:
                    case NORTH_SOUTH:
                    case EAST_WEST:
                    default:
                        railshape1 = shape;
                        return railshape1;
                    case SOUTH_EAST:
                        railshape1 = RailShape.SOUTH_WEST;
                        return railshape1;
                    case SOUTH_WEST:
                        railshape1 = RailShape.SOUTH_EAST;
                        return railshape1;
                    case NORTH_WEST:
                        railshape1 = RailShape.NORTH_EAST;
                        return railshape1;
                    case NORTH_EAST:
                        railshape1 = RailShape.NORTH_WEST;
                        return railshape1;
                }
            default:
                railshape1 = shape;
                return railshape1;
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(BaseRailBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(BaseRailBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
}

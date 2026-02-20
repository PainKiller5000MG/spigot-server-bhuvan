package net.minecraft.world.level.block;

import com.mojang.math.OctahedralGroup;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StairBlock extends Block implements SimpleWaterloggedBlock {

    public static final MapCodec<StairBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BlockState.CODEC.fieldOf("base_state").forGetter((stairblock) -> {
            return stairblock.baseState;
        }), propertiesCodec()).apply(instance, StairBlock::new);
    });
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final EnumProperty<StairsShape> SHAPE = BlockStateProperties.STAIRS_SHAPE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE_OUTER = Shapes.or(Block.column(16.0D, 0.0D, 8.0D), Block.box(0.0D, 8.0D, 0.0D, 8.0D, 16.0D, 8.0D));
    private static final VoxelShape SHAPE_STRAIGHT = Shapes.or(StairBlock.SHAPE_OUTER, Shapes.rotate(StairBlock.SHAPE_OUTER, OctahedralGroup.BLOCK_ROT_Y_90));
    private static final VoxelShape SHAPE_INNER = Shapes.or(StairBlock.SHAPE_STRAIGHT, Shapes.rotate(StairBlock.SHAPE_STRAIGHT, OctahedralGroup.BLOCK_ROT_Y_90));
    private static final Map<Direction, VoxelShape> SHAPE_BOTTOM_OUTER = Shapes.rotateHorizontal(StairBlock.SHAPE_OUTER);
    private static final Map<Direction, VoxelShape> SHAPE_BOTTOM_STRAIGHT = Shapes.rotateHorizontal(StairBlock.SHAPE_STRAIGHT);
    private static final Map<Direction, VoxelShape> SHAPE_BOTTOM_INNER = Shapes.rotateHorizontal(StairBlock.SHAPE_INNER);
    private static final Map<Direction, VoxelShape> SHAPE_TOP_OUTER = Shapes.rotateHorizontal(StairBlock.SHAPE_OUTER, OctahedralGroup.INVERT_Y);
    private static final Map<Direction, VoxelShape> SHAPE_TOP_STRAIGHT = Shapes.rotateHorizontal(StairBlock.SHAPE_STRAIGHT, OctahedralGroup.INVERT_Y);
    private static final Map<Direction, VoxelShape> SHAPE_TOP_INNER = Shapes.rotateHorizontal(StairBlock.SHAPE_INNER, OctahedralGroup.INVERT_Y);
    private final Block base;
    protected final BlockState baseState;

    @Override
    public MapCodec<? extends StairBlock> codec() {
        return StairBlock.CODEC;
    }

    protected StairBlock(BlockState baseState, BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(StairBlock.FACING, Direction.NORTH)).setValue(StairBlock.HALF, Half.BOTTOM)).setValue(StairBlock.SHAPE, StairsShape.STRAIGHT)).setValue(StairBlock.WATERLOGGED, false));
        this.base = baseState.getBlock();
        this.baseState = baseState;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean flag = state.getValue(StairBlock.HALF) == Half.BOTTOM;
        Direction direction = (Direction) state.getValue(StairBlock.FACING);
        Map map;

        switch ((StairsShape) state.getValue(StairBlock.SHAPE)) {
            case STRAIGHT:
                map = flag ? StairBlock.SHAPE_BOTTOM_STRAIGHT : StairBlock.SHAPE_TOP_STRAIGHT;
                break;
            case OUTER_LEFT:
            case OUTER_RIGHT:
                map = flag ? StairBlock.SHAPE_BOTTOM_OUTER : StairBlock.SHAPE_TOP_OUTER;
                break;
            case INNER_RIGHT:
            case INNER_LEFT:
                map = flag ? StairBlock.SHAPE_BOTTOM_INNER : StairBlock.SHAPE_TOP_INNER;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        Direction direction1;

        switch ((StairsShape) state.getValue(StairBlock.SHAPE)) {
            case STRAIGHT:
            case OUTER_LEFT:
            case INNER_RIGHT:
                direction1 = direction;
                break;
            case INNER_LEFT:
                direction1 = direction.getCounterClockWise();
                break;
            case OUTER_RIGHT:
                direction1 = direction.getClockWise();
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return (VoxelShape) map.get(direction1);
    }

    @Override
    public float getExplosionResistance() {
        return this.base.getExplosionResistance();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getClickedFace();
        BlockPos blockpos = context.getClickedPos();
        FluidState fluidstate = context.getLevel().getFluidState(blockpos);
        BlockState blockstate = (BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(StairBlock.FACING, context.getHorizontalDirection())).setValue(StairBlock.HALF, direction != Direction.DOWN && (direction == Direction.UP || context.getClickLocation().y - (double) blockpos.getY() <= 0.5D) ? Half.BOTTOM : Half.TOP)).setValue(StairBlock.WATERLOGGED, fluidstate.getType() == Fluids.WATER);

        return (BlockState) blockstate.setValue(StairBlock.SHAPE, getStairsShape(blockstate, context.getLevel(), blockpos));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(StairBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return directionToNeighbour.getAxis().isHorizontal() ? (BlockState) state.setValue(StairBlock.SHAPE, getStairsShape(state, level, pos)) : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    private static StairsShape getStairsShape(BlockState state, BlockGetter level, BlockPos pos) {
        Direction direction = (Direction) state.getValue(StairBlock.FACING);
        BlockState blockstate1 = level.getBlockState(pos.relative(direction));

        if (isStairs(blockstate1) && state.getValue(StairBlock.HALF) == blockstate1.getValue(StairBlock.HALF)) {
            Direction direction1 = (Direction) blockstate1.getValue(StairBlock.FACING);

            if (direction1.getAxis() != ((Direction) state.getValue(StairBlock.FACING)).getAxis() && canTakeShape(state, level, pos, direction1.getOpposite())) {
                if (direction1 == direction.getCounterClockWise()) {
                    return StairsShape.OUTER_LEFT;
                }

                return StairsShape.OUTER_RIGHT;
            }
        }

        BlockState blockstate2 = level.getBlockState(pos.relative(direction.getOpposite()));

        if (isStairs(blockstate2) && state.getValue(StairBlock.HALF) == blockstate2.getValue(StairBlock.HALF)) {
            Direction direction2 = (Direction) blockstate2.getValue(StairBlock.FACING);

            if (direction2.getAxis() != ((Direction) state.getValue(StairBlock.FACING)).getAxis() && canTakeShape(state, level, pos, direction2)) {
                if (direction2 == direction.getCounterClockWise()) {
                    return StairsShape.INNER_LEFT;
                }

                return StairsShape.INNER_RIGHT;
            }
        }

        return StairsShape.STRAIGHT;
    }

    private static boolean canTakeShape(BlockState state, BlockGetter level, BlockPos pos, Direction neighbour) {
        BlockState blockstate1 = level.getBlockState(pos.relative(neighbour));

        return !isStairs(blockstate1) || blockstate1.getValue(StairBlock.FACING) != state.getValue(StairBlock.FACING) || blockstate1.getValue(StairBlock.HALF) != state.getValue(StairBlock.HALF);
    }

    public static boolean isStairs(BlockState state) {
        return state.getBlock() instanceof StairBlock;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(StairBlock.FACING, rotation.rotate((Direction) state.getValue(StairBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction direction = (Direction) state.getValue(StairBlock.FACING);
        StairsShape stairsshape = (StairsShape) state.getValue(StairBlock.SHAPE);

        switch (mirror) {
            case LEFT_RIGHT:
                if (direction.getAxis() == Direction.Axis.Z) {
                    switch (stairsshape) {
                        case OUTER_LEFT:
                            return (BlockState) state.rotate(Rotation.CLOCKWISE_180).setValue(StairBlock.SHAPE, StairsShape.OUTER_RIGHT);
                        case INNER_RIGHT:
                            return (BlockState) state.rotate(Rotation.CLOCKWISE_180).setValue(StairBlock.SHAPE, StairsShape.INNER_LEFT);
                        case INNER_LEFT:
                            return (BlockState) state.rotate(Rotation.CLOCKWISE_180).setValue(StairBlock.SHAPE, StairsShape.INNER_RIGHT);
                        case OUTER_RIGHT:
                            return (BlockState) state.rotate(Rotation.CLOCKWISE_180).setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT);
                        default:
                            return state.rotate(Rotation.CLOCKWISE_180);
                    }
                }
                break;
            case FRONT_BACK:
                if (direction.getAxis() == Direction.Axis.X) {
                    switch (stairsshape) {
                        case STRAIGHT:
                            return state.rotate(Rotation.CLOCKWISE_180);
                        case OUTER_LEFT:
                            return (BlockState) state.rotate(Rotation.CLOCKWISE_180).setValue(StairBlock.SHAPE, StairsShape.OUTER_RIGHT);
                        case INNER_RIGHT:
                            return (BlockState) state.rotate(Rotation.CLOCKWISE_180).setValue(StairBlock.SHAPE, StairsShape.INNER_RIGHT);
                        case INNER_LEFT:
                            return (BlockState) state.rotate(Rotation.CLOCKWISE_180).setValue(StairBlock.SHAPE, StairsShape.INNER_LEFT);
                        case OUTER_RIGHT:
                            return (BlockState) state.rotate(Rotation.CLOCKWISE_180).setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT);
                    }
                }
        }

        return super.mirror(state, mirror);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(StairBlock.FACING, StairBlock.HALF, StairBlock.SHAPE, StairBlock.WATERLOGGED);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(StairBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}

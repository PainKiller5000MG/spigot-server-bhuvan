package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WallBlock extends Block implements SimpleWaterloggedBlock {

    public static final MapCodec<WallBlock> CODEC = simpleCodec(WallBlock::new);
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final EnumProperty<WallSide> EAST = BlockStateProperties.EAST_WALL;
    public static final EnumProperty<WallSide> NORTH = BlockStateProperties.NORTH_WALL;
    public static final EnumProperty<WallSide> SOUTH = BlockStateProperties.SOUTH_WALL;
    public static final EnumProperty<WallSide> WEST = BlockStateProperties.WEST_WALL;
    public static final Map<Direction, EnumProperty<WallSide>> PROPERTY_BY_DIRECTION = ImmutableMap.copyOf(Maps.newEnumMap(Map.of(Direction.NORTH, WallBlock.NORTH, Direction.EAST, WallBlock.EAST, Direction.SOUTH, WallBlock.SOUTH, Direction.WEST, WallBlock.WEST)));
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private final Function<BlockState, VoxelShape> shapes;
    private final Function<BlockState, VoxelShape> collisionShapes;
    private static final VoxelShape TEST_SHAPE_POST = Block.column(2.0D, 0.0D, 16.0D);
    private static final Map<Direction, VoxelShape> TEST_SHAPES_WALL = Shapes.rotateHorizontal(Block.boxZ(2.0D, 16.0D, 0.0D, 9.0D));

    @Override
    public MapCodec<WallBlock> codec() {
        return WallBlock.CODEC;
    }

    public WallBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(WallBlock.UP, true)).setValue(WallBlock.NORTH, WallSide.NONE)).setValue(WallBlock.EAST, WallSide.NONE)).setValue(WallBlock.SOUTH, WallSide.NONE)).setValue(WallBlock.WEST, WallSide.NONE)).setValue(WallBlock.WATERLOGGED, false));
        this.shapes = this.makeShapes(16.0F, 14.0F);
        this.collisionShapes = this.makeShapes(24.0F, 24.0F);
    }

    private Function<BlockState, VoxelShape> makeShapes(float postHeight, float wallTop) {
        VoxelShape voxelshape = Block.column(8.0D, 0.0D, (double) postHeight);
        int i = 6;
        Map<Direction, VoxelShape> map = Shapes.rotateHorizontal(Block.boxZ(6.0D, 0.0D, (double) wallTop, 0.0D, 11.0D));
        Map<Direction, VoxelShape> map1 = Shapes.rotateHorizontal(Block.boxZ(6.0D, 0.0D, (double) postHeight, 0.0D, 11.0D));

        return this.getShapeForEachState((blockstate) -> {
            VoxelShape voxelshape1 = (Boolean) blockstate.getValue(WallBlock.UP) ? voxelshape : Shapes.empty();

            for (Map.Entry<Direction, EnumProperty<WallSide>> map_entry : WallBlock.PROPERTY_BY_DIRECTION.entrySet()) {
                VoxelShape voxelshape2;

                switch ((WallSide) blockstate.getValue((Property) map_entry.getValue())) {
                    case NONE:
                        voxelshape2 = Shapes.empty();
                        break;
                    case LOW:
                        voxelshape2 = (VoxelShape) map.get(map_entry.getKey());
                        break;
                    case TALL:
                        voxelshape2 = (VoxelShape) map1.get(map_entry.getKey());
                        break;
                    default:
                        throw new MatchException((String) null, (Throwable) null);
                }

                voxelshape1 = Shapes.or(voxelshape1, voxelshape2);
            }

            return voxelshape1;
        }, new Property[]{WallBlock.WATERLOGGED});
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) this.shapes.apply(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) this.collisionShapes.apply(state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    private boolean connectsTo(BlockState state, boolean faceSolid, Direction direction) {
        Block block = state.getBlock();
        boolean flag1 = block instanceof FenceGateBlock && FenceGateBlock.connectsToDirection(state, direction);

        return state.is(BlockTags.WALLS) || !isExceptionForConnection(state) && faceSolid || block instanceof IronBarsBlock || flag1;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader levelreader = context.getLevel();
        BlockPos blockpos = context.getClickedPos();
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        BlockPos blockpos1 = blockpos.north();
        BlockPos blockpos2 = blockpos.east();
        BlockPos blockpos3 = blockpos.south();
        BlockPos blockpos4 = blockpos.west();
        BlockPos blockpos5 = blockpos.above();
        BlockState blockstate = levelreader.getBlockState(blockpos1);
        BlockState blockstate1 = levelreader.getBlockState(blockpos2);
        BlockState blockstate2 = levelreader.getBlockState(blockpos3);
        BlockState blockstate3 = levelreader.getBlockState(blockpos4);
        BlockState blockstate4 = levelreader.getBlockState(blockpos5);
        boolean flag = this.connectsTo(blockstate, blockstate.isFaceSturdy(levelreader, blockpos1, Direction.SOUTH), Direction.SOUTH);
        boolean flag1 = this.connectsTo(blockstate1, blockstate1.isFaceSturdy(levelreader, blockpos2, Direction.WEST), Direction.WEST);
        boolean flag2 = this.connectsTo(blockstate2, blockstate2.isFaceSturdy(levelreader, blockpos3, Direction.NORTH), Direction.NORTH);
        boolean flag3 = this.connectsTo(blockstate3, blockstate3.isFaceSturdy(levelreader, blockpos4, Direction.EAST), Direction.EAST);
        BlockState blockstate5 = (BlockState) this.defaultBlockState().setValue(WallBlock.WATERLOGGED, fluidstate.getType() == Fluids.WATER);

        return this.updateShape(levelreader, blockstate5, blockpos5, blockstate4, flag, flag1, flag2, flag3);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(WallBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return directionToNeighbour == Direction.DOWN ? super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random) : (directionToNeighbour == Direction.UP ? this.topUpdate(level, state, neighbourPos, neighbourState) : this.sideUpdate(level, pos, state, neighbourPos, neighbourState, directionToNeighbour));
    }

    private static boolean isConnected(BlockState state, Property<WallSide> northWall) {
        return state.getValue(northWall) != WallSide.NONE;
    }

    private static boolean isCovered(VoxelShape aboveShape, VoxelShape testShape) {
        return !Shapes.joinIsNotEmpty(testShape, aboveShape, BooleanOp.ONLY_FIRST);
    }

    private BlockState topUpdate(LevelReader level, BlockState state, BlockPos topPos, BlockState topNeighbour) {
        boolean flag = isConnected(state, WallBlock.NORTH);
        boolean flag1 = isConnected(state, WallBlock.EAST);
        boolean flag2 = isConnected(state, WallBlock.SOUTH);
        boolean flag3 = isConnected(state, WallBlock.WEST);

        return this.updateShape(level, state, topPos, topNeighbour, flag, flag1, flag2, flag3);
    }

    private BlockState sideUpdate(LevelReader level, BlockPos pos, BlockState state, BlockPos neighbourPos, BlockState neighbour, Direction direction) {
        Direction direction1 = direction.getOpposite();
        boolean flag = direction == Direction.NORTH ? this.connectsTo(neighbour, neighbour.isFaceSturdy(level, neighbourPos, direction1), direction1) : isConnected(state, WallBlock.NORTH);
        boolean flag1 = direction == Direction.EAST ? this.connectsTo(neighbour, neighbour.isFaceSturdy(level, neighbourPos, direction1), direction1) : isConnected(state, WallBlock.EAST);
        boolean flag2 = direction == Direction.SOUTH ? this.connectsTo(neighbour, neighbour.isFaceSturdy(level, neighbourPos, direction1), direction1) : isConnected(state, WallBlock.SOUTH);
        boolean flag3 = direction == Direction.WEST ? this.connectsTo(neighbour, neighbour.isFaceSturdy(level, neighbourPos, direction1), direction1) : isConnected(state, WallBlock.WEST);
        BlockPos blockpos2 = pos.above();
        BlockState blockstate2 = level.getBlockState(blockpos2);

        return this.updateShape(level, state, blockpos2, blockstate2, flag, flag1, flag2, flag3);
    }

    private BlockState updateShape(LevelReader level, BlockState state, BlockPos topPos, BlockState topNeighbour, boolean north, boolean east, boolean south, boolean west) {
        VoxelShape voxelshape = topNeighbour.getCollisionShape(level, topPos).getFaceShape(Direction.DOWN);
        BlockState blockstate2 = this.updateSides(state, north, east, south, west, voxelshape);

        return (BlockState) blockstate2.setValue(WallBlock.UP, this.shouldRaisePost(blockstate2, topNeighbour, voxelshape));
    }

    private boolean shouldRaisePost(BlockState state, BlockState topNeighbour, VoxelShape aboveShape) {
        boolean flag = topNeighbour.getBlock() instanceof WallBlock && (Boolean) topNeighbour.getValue(WallBlock.UP);

        if (flag) {
            return true;
        } else {
            WallSide wallside = (WallSide) state.getValue(WallBlock.NORTH);
            WallSide wallside1 = (WallSide) state.getValue(WallBlock.SOUTH);
            WallSide wallside2 = (WallSide) state.getValue(WallBlock.EAST);
            WallSide wallside3 = (WallSide) state.getValue(WallBlock.WEST);
            boolean flag1 = wallside1 == WallSide.NONE;
            boolean flag2 = wallside3 == WallSide.NONE;
            boolean flag3 = wallside2 == WallSide.NONE;
            boolean flag4 = wallside == WallSide.NONE;
            boolean flag5 = flag4 && flag1 && flag2 && flag3 || flag4 != flag1 || flag2 != flag3;

            if (flag5) {
                return true;
            } else {
                boolean flag6 = wallside == WallSide.TALL && wallside1 == WallSide.TALL || wallside2 == WallSide.TALL && wallside3 == WallSide.TALL;

                return flag6 ? false : topNeighbour.is(BlockTags.WALL_POST_OVERRIDE) || isCovered(aboveShape, WallBlock.TEST_SHAPE_POST);
            }
        }
    }

    private BlockState updateSides(BlockState state, boolean northConnection, boolean eastConnection, boolean southConnection, boolean westConnection, VoxelShape aboveShape) {
        return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(WallBlock.NORTH, this.makeWallState(northConnection, aboveShape, (VoxelShape) WallBlock.TEST_SHAPES_WALL.get(Direction.NORTH)))).setValue(WallBlock.EAST, this.makeWallState(eastConnection, aboveShape, (VoxelShape) WallBlock.TEST_SHAPES_WALL.get(Direction.EAST)))).setValue(WallBlock.SOUTH, this.makeWallState(southConnection, aboveShape, (VoxelShape) WallBlock.TEST_SHAPES_WALL.get(Direction.SOUTH)))).setValue(WallBlock.WEST, this.makeWallState(westConnection, aboveShape, (VoxelShape) WallBlock.TEST_SHAPES_WALL.get(Direction.WEST)));
    }

    private WallSide makeWallState(boolean connectsToSide, VoxelShape aboveShape, VoxelShape testShape) {
        return connectsToSide ? (isCovered(aboveShape, testShape) ? WallSide.TALL : WallSide.LOW) : WallSide.NONE;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(WallBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return !(Boolean) state.getValue(WallBlock.WATERLOGGED);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WallBlock.UP, WallBlock.NORTH, WallBlock.EAST, WallBlock.WEST, WallBlock.SOUTH, WallBlock.WATERLOGGED);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_180:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(WallBlock.NORTH, (WallSide) state.getValue(WallBlock.SOUTH))).setValue(WallBlock.EAST, (WallSide) state.getValue(WallBlock.WEST))).setValue(WallBlock.SOUTH, (WallSide) state.getValue(WallBlock.NORTH))).setValue(WallBlock.WEST, (WallSide) state.getValue(WallBlock.EAST));
            case COUNTERCLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(WallBlock.NORTH, (WallSide) state.getValue(WallBlock.EAST))).setValue(WallBlock.EAST, (WallSide) state.getValue(WallBlock.SOUTH))).setValue(WallBlock.SOUTH, (WallSide) state.getValue(WallBlock.WEST))).setValue(WallBlock.WEST, (WallSide) state.getValue(WallBlock.NORTH));
            case CLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(WallBlock.NORTH, (WallSide) state.getValue(WallBlock.WEST))).setValue(WallBlock.EAST, (WallSide) state.getValue(WallBlock.NORTH))).setValue(WallBlock.SOUTH, (WallSide) state.getValue(WallBlock.EAST))).setValue(WallBlock.WEST, (WallSide) state.getValue(WallBlock.SOUTH));
            default:
                return state;
        }
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        switch (mirror) {
            case LEFT_RIGHT:
                return (BlockState) ((BlockState) state.setValue(WallBlock.NORTH, (WallSide) state.getValue(WallBlock.SOUTH))).setValue(WallBlock.SOUTH, (WallSide) state.getValue(WallBlock.NORTH));
            case FRONT_BACK:
                return (BlockState) ((BlockState) state.setValue(WallBlock.EAST, (WallSide) state.getValue(WallBlock.WEST))).setValue(WallBlock.WEST, (WallSide) state.getValue(WallBlock.EAST));
            default:
                return super.mirror(state, mirror);
        }
    }
}

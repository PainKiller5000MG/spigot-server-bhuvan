package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class VineBlock extends Block {

    public static final MapCodec<VineBlock> CODEC = simpleCodec(VineBlock::new);
    public static final BooleanProperty UP = PipeBlock.UP;
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = (Map) PipeBlock.PROPERTY_BY_DIRECTION.entrySet().stream().filter((entry) -> {
        return entry.getKey() != Direction.DOWN;
    }).collect(Util.toMap());
    private final Function<BlockState, VoxelShape> shapes;

    @Override
    public MapCodec<VineBlock> codec() {
        return VineBlock.CODEC;
    }

    public VineBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(VineBlock.UP, false)).setValue(VineBlock.NORTH, false)).setValue(VineBlock.EAST, false)).setValue(VineBlock.SOUTH, false)).setValue(VineBlock.WEST, false));
        this.shapes = this.makeShapes();
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        Map<Direction, VoxelShape> map = Shapes.rotateAll(Block.boxZ(16.0D, 0.0D, 1.0D));

        return this.getShapeForEachState((blockstate) -> {
            VoxelShape voxelshape = Shapes.empty();

            for (Map.Entry<Direction, BooleanProperty> map_entry : VineBlock.PROPERTY_BY_DIRECTION.entrySet()) {
                if ((Boolean) blockstate.getValue((Property) map_entry.getValue())) {
                    voxelshape = Shapes.or(voxelshape, (VoxelShape) map.get(map_entry.getKey()));
                }
            }

            return voxelshape.isEmpty() ? Shapes.block() : voxelshape;
        });
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) this.shapes.apply(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return this.hasFaces(this.getUpdatedState(state, level, pos));
    }

    private boolean hasFaces(BlockState blockState) {
        return this.countFaces(blockState) > 0;
    }

    private int countFaces(BlockState blockState) {
        int i = 0;

        for (BooleanProperty booleanproperty : VineBlock.PROPERTY_BY_DIRECTION.values()) {
            if ((Boolean) blockState.getValue(booleanproperty)) {
                ++i;
            }
        }

        return i;
    }

    private boolean canSupportAtFace(BlockGetter level, BlockPos pos, Direction direction) {
        if (direction == Direction.DOWN) {
            return false;
        } else {
            BlockPos blockpos1 = pos.relative(direction);

            if (isAcceptableNeighbour(level, blockpos1, direction)) {
                return true;
            } else if (direction.getAxis() == Direction.Axis.Y) {
                return false;
            } else {
                BooleanProperty booleanproperty = (BooleanProperty) VineBlock.PROPERTY_BY_DIRECTION.get(direction);
                BlockState blockstate = level.getBlockState(pos.above());

                return blockstate.is(this) && (Boolean) blockstate.getValue(booleanproperty);
            }
        }
    }

    public static boolean isAcceptableNeighbour(BlockGetter level, BlockPos neighbourPos, Direction directionToNeighbour) {
        return MultifaceBlock.canAttachTo(level, directionToNeighbour, neighbourPos, level.getBlockState(neighbourPos));
    }

    private BlockState getUpdatedState(BlockState state, BlockGetter level, BlockPos pos) {
        BlockPos blockpos1 = pos.above();

        if ((Boolean) state.getValue(VineBlock.UP)) {
            state = (BlockState) state.setValue(VineBlock.UP, isAcceptableNeighbour(level, blockpos1, Direction.DOWN));
        }

        BlockState blockstate1 = null;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BooleanProperty booleanproperty = getPropertyForFace(direction);

            if ((Boolean) state.getValue(booleanproperty)) {
                boolean flag = this.canSupportAtFace(level, pos, direction);

                if (!flag) {
                    if (blockstate1 == null) {
                        blockstate1 = level.getBlockState(blockpos1);
                    }

                    flag = blockstate1.is(this) && (Boolean) blockstate1.getValue(booleanproperty);
                }

                state = (BlockState) state.setValue(booleanproperty, flag);
            }
        }

        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (directionToNeighbour == Direction.DOWN) {
            return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
        } else {
            BlockState blockstate2 = this.getUpdatedState(state, level, pos);

            return !this.hasFaces(blockstate2) ? Blocks.AIR.defaultBlockState() : blockstate2;
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if ((Boolean) level.getGameRules().get(GameRules.SPREAD_VINES)) {
            if (random.nextInt(4) == 0) {
                Direction direction = Direction.getRandom(random);
                BlockPos blockpos1 = pos.above();

                if (direction.getAxis().isHorizontal() && !(Boolean) state.getValue(getPropertyForFace(direction))) {
                    if (this.canSpread(level, pos)) {
                        BlockPos blockpos2 = pos.relative(direction);
                        BlockState blockstate1 = level.getBlockState(blockpos2);

                        if (blockstate1.isAir()) {
                            Direction direction1 = direction.getClockWise();
                            Direction direction2 = direction.getCounterClockWise();
                            boolean flag = (Boolean) state.getValue(getPropertyForFace(direction1));
                            boolean flag1 = (Boolean) state.getValue(getPropertyForFace(direction2));
                            BlockPos blockpos3 = blockpos2.relative(direction1);
                            BlockPos blockpos4 = blockpos2.relative(direction2);

                            if (flag && isAcceptableNeighbour(level, blockpos3, direction1)) {
                                level.setBlock(blockpos2, (BlockState) this.defaultBlockState().setValue(getPropertyForFace(direction1), true), 2);
                            } else if (flag1 && isAcceptableNeighbour(level, blockpos4, direction2)) {
                                level.setBlock(blockpos2, (BlockState) this.defaultBlockState().setValue(getPropertyForFace(direction2), true), 2);
                            } else {
                                Direction direction3 = direction.getOpposite();

                                if (flag && level.isEmptyBlock(blockpos3) && isAcceptableNeighbour(level, pos.relative(direction1), direction3)) {
                                    level.setBlock(blockpos3, (BlockState) this.defaultBlockState().setValue(getPropertyForFace(direction3), true), 2);
                                } else if (flag1 && level.isEmptyBlock(blockpos4) && isAcceptableNeighbour(level, pos.relative(direction2), direction3)) {
                                    level.setBlock(blockpos4, (BlockState) this.defaultBlockState().setValue(getPropertyForFace(direction3), true), 2);
                                } else if ((double) random.nextFloat() < 0.05D && isAcceptableNeighbour(level, blockpos2.above(), Direction.UP)) {
                                    level.setBlock(blockpos2, (BlockState) this.defaultBlockState().setValue(VineBlock.UP, true), 2);
                                }
                            }
                        } else if (isAcceptableNeighbour(level, blockpos2, direction)) {
                            level.setBlock(pos, (BlockState) state.setValue(getPropertyForFace(direction), true), 2);
                        }

                    }
                } else {
                    if (direction == Direction.UP && pos.getY() < level.getMaxY()) {
                        if (this.canSupportAtFace(level, pos, direction)) {
                            level.setBlock(pos, (BlockState) state.setValue(VineBlock.UP, true), 2);
                            return;
                        }

                        if (level.isEmptyBlock(blockpos1)) {
                            if (!this.canSpread(level, pos)) {
                                return;
                            }

                            BlockState blockstate2 = state;

                            for (Direction direction4 : Direction.Plane.HORIZONTAL) {
                                if (random.nextBoolean() || !isAcceptableNeighbour(level, blockpos1.relative(direction4), direction4)) {
                                    blockstate2 = (BlockState) blockstate2.setValue(getPropertyForFace(direction4), false);
                                }
                            }

                            if (this.hasHorizontalConnection(blockstate2)) {
                                level.setBlock(blockpos1, blockstate2, 2);
                            }

                            return;
                        }
                    }

                    if (pos.getY() > level.getMinY()) {
                        BlockPos blockpos5 = pos.below();
                        BlockState blockstate3 = level.getBlockState(blockpos5);

                        if (blockstate3.isAir() || blockstate3.is(this)) {
                            BlockState blockstate4 = blockstate3.isAir() ? this.defaultBlockState() : blockstate3;
                            BlockState blockstate5 = this.copyRandomFaces(state, blockstate4, random);

                            if (blockstate4 != blockstate5 && this.hasHorizontalConnection(blockstate5)) {
                                level.setBlock(blockpos5, blockstate5, 2);
                            }
                        }
                    }

                }
            }
        }
    }

    private BlockState copyRandomFaces(BlockState from, BlockState to, RandomSource random) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (random.nextBoolean()) {
                BooleanProperty booleanproperty = getPropertyForFace(direction);

                if ((Boolean) from.getValue(booleanproperty)) {
                    to = (BlockState) to.setValue(booleanproperty, true);
                }
            }
        }

        return to;
    }

    private boolean hasHorizontalConnection(BlockState state) {
        return (Boolean) state.getValue(VineBlock.NORTH) || (Boolean) state.getValue(VineBlock.EAST) || (Boolean) state.getValue(VineBlock.SOUTH) || (Boolean) state.getValue(VineBlock.WEST);
    }

    private boolean canSpread(BlockGetter level, BlockPos pos) {
        int i = 4;
        Iterable<BlockPos> iterable = BlockPos.betweenClosed(pos.getX() - 4, pos.getY() - 1, pos.getZ() - 4, pos.getX() + 4, pos.getY() + 1, pos.getZ() + 4);
        int j = 5;

        for (BlockPos blockpos1 : iterable) {
            if (level.getBlockState(blockpos1).is(this)) {
                --j;
                if (j <= 0) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        BlockState blockstate1 = context.getLevel().getBlockState(context.getClickedPos());

        return blockstate1.is(this) ? this.countFaces(blockstate1) < VineBlock.PROPERTY_BY_DIRECTION.size() : super.canBeReplaced(state, context);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockstate = context.getLevel().getBlockState(context.getClickedPos());
        boolean flag = blockstate.is(this);
        BlockState blockstate1 = flag ? blockstate : this.defaultBlockState();

        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction != Direction.DOWN) {
                BooleanProperty booleanproperty = getPropertyForFace(direction);
                boolean flag1 = flag && (Boolean) blockstate.getValue(booleanproperty);

                if (!flag1 && this.canSupportAtFace(context.getLevel(), context.getClickedPos(), direction)) {
                    return (BlockState) blockstate1.setValue(booleanproperty, true);
                }
            }
        }

        return flag ? blockstate1 : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VineBlock.UP, VineBlock.NORTH, VineBlock.EAST, VineBlock.SOUTH, VineBlock.WEST);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_180:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(VineBlock.NORTH, (Boolean) state.getValue(VineBlock.SOUTH))).setValue(VineBlock.EAST, (Boolean) state.getValue(VineBlock.WEST))).setValue(VineBlock.SOUTH, (Boolean) state.getValue(VineBlock.NORTH))).setValue(VineBlock.WEST, (Boolean) state.getValue(VineBlock.EAST));
            case COUNTERCLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(VineBlock.NORTH, (Boolean) state.getValue(VineBlock.EAST))).setValue(VineBlock.EAST, (Boolean) state.getValue(VineBlock.SOUTH))).setValue(VineBlock.SOUTH, (Boolean) state.getValue(VineBlock.WEST))).setValue(VineBlock.WEST, (Boolean) state.getValue(VineBlock.NORTH));
            case CLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(VineBlock.NORTH, (Boolean) state.getValue(VineBlock.WEST))).setValue(VineBlock.EAST, (Boolean) state.getValue(VineBlock.NORTH))).setValue(VineBlock.SOUTH, (Boolean) state.getValue(VineBlock.EAST))).setValue(VineBlock.WEST, (Boolean) state.getValue(VineBlock.SOUTH));
            default:
                return state;
        }
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        switch (mirror) {
            case LEFT_RIGHT:
                return (BlockState) ((BlockState) state.setValue(VineBlock.NORTH, (Boolean) state.getValue(VineBlock.SOUTH))).setValue(VineBlock.SOUTH, (Boolean) state.getValue(VineBlock.NORTH));
            case FRONT_BACK:
                return (BlockState) ((BlockState) state.setValue(VineBlock.EAST, (Boolean) state.getValue(VineBlock.WEST))).setValue(VineBlock.WEST, (Boolean) state.getValue(VineBlock.EAST));
            default:
                return super.mirror(state, mirror);
        }
    }

    public static BooleanProperty getPropertyForFace(Direction direction) {
        return (BooleanProperty) VineBlock.PROPERTY_BY_DIRECTION.get(direction);
    }
}

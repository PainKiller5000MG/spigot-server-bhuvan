package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class MultifaceBlock extends Block implements SimpleWaterloggedBlock {

    public static final MapCodec<MultifaceBlock> CODEC = simpleCodec(MultifaceBlock::new);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION;
    protected static final Direction[] DIRECTIONS = Direction.values();
    private final Function<BlockState, VoxelShape> shapes;
    private final boolean canRotate;
    private final boolean canMirrorX;
    private final boolean canMirrorZ;

    @Override
    protected MapCodec<? extends MultifaceBlock> codec() {
        return MultifaceBlock.CODEC;
    }

    public MultifaceBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(getDefaultMultifaceState(this.stateDefinition));
        this.shapes = this.makeShapes();
        this.canRotate = Direction.Plane.HORIZONTAL.stream().allMatch(this::isFaceSupported);
        this.canMirrorX = Direction.Plane.HORIZONTAL.stream().filter(Direction.Axis.X).filter(this::isFaceSupported).count() % 2L == 0L;
        this.canMirrorZ = Direction.Plane.HORIZONTAL.stream().filter(Direction.Axis.Z).filter(this::isFaceSupported).count() % 2L == 0L;
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        Map<Direction, VoxelShape> map = Shapes.rotateAll(Block.boxZ(16.0D, 0.0D, 1.0D));

        return this.getShapeForEachState((blockstate) -> {
            VoxelShape voxelshape = Shapes.empty();

            for (Direction direction : MultifaceBlock.DIRECTIONS) {
                if (hasFace(blockstate, direction)) {
                    voxelshape = Shapes.or(voxelshape, (VoxelShape) map.get(direction));
                }
            }

            return voxelshape.isEmpty() ? Shapes.block() : voxelshape;
        }, new Property[]{MultifaceBlock.WATERLOGGED});
    }

    public static Set<Direction> availableFaces(BlockState state) {
        if (!(state.getBlock() instanceof MultifaceBlock)) {
            return Set.of();
        } else {
            Set<Direction> set = EnumSet.noneOf(Direction.class);

            for (Direction direction : Direction.values()) {
                if (hasFace(state, direction)) {
                    set.add(direction);
                }
            }

            return set;
        }
    }

    public static Set<Direction> unpack(byte data) {
        Set<Direction> set = EnumSet.noneOf(Direction.class);

        for (Direction direction : Direction.values()) {
            if ((data & (byte) (1 << direction.ordinal())) > 0) {
                set.add(direction);
            }
        }

        return set;
    }

    public static byte pack(Collection<Direction> directions) {
        byte b0 = 0;

        for (Direction direction : directions) {
            b0 = (byte) (b0 | 1 << direction.ordinal());
        }

        return b0;
    }

    protected boolean isFaceSupported(Direction faceDirection) {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        for (Direction direction : MultifaceBlock.DIRECTIONS) {
            if (this.isFaceSupported(direction)) {
                builder.add(getFaceProperty(direction));
            }
        }

        builder.add(MultifaceBlock.WATERLOGGED);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(MultifaceBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return !hasAnyFace(state) ? Blocks.AIR.defaultBlockState() : (hasFace(state, directionToNeighbour) && !canAttachTo(level, directionToNeighbour, neighbourPos, neighbourState) ? removeFace(state, getFaceProperty(directionToNeighbour)) : state);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(MultifaceBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) this.shapes.apply(state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        boolean flag = false;

        for (Direction direction : MultifaceBlock.DIRECTIONS) {
            if (hasFace(state, direction)) {
                if (!canAttachTo(level, pos, direction)) {
                    return false;
                }

                flag = true;
            }
        }

        return flag;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return !context.getItemInHand().is(this.asItem()) || hasAnyVacantFace(state);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos blockpos = context.getClickedPos();
        BlockState blockstate = level.getBlockState(blockpos);

        return (BlockState) Arrays.stream(context.getNearestLookingDirections()).map((direction) -> {
            return this.getStateForPlacement(blockstate, level, blockpos, direction);
        }).filter(Objects::nonNull).findFirst().orElse((Object) null);
    }

    public boolean isValidStateForPlacement(BlockGetter level, BlockState oldState, BlockPos placementPos, Direction placementDirection) {
        if (this.isFaceSupported(placementDirection) && (!oldState.is(this) || !hasFace(oldState, placementDirection))) {
            BlockPos blockpos1 = placementPos.relative(placementDirection);

            return canAttachTo(level, placementDirection, blockpos1, level.getBlockState(blockpos1));
        } else {
            return false;
        }
    }

    public @Nullable BlockState getStateForPlacement(BlockState oldState, BlockGetter level, BlockPos placementPos, Direction placementDirection) {
        if (!this.isValidStateForPlacement(level, oldState, placementPos, placementDirection)) {
            return null;
        } else {
            BlockState blockstate1;

            if (oldState.is(this)) {
                blockstate1 = oldState;
            } else if (oldState.getFluidState().isSourceOfType(Fluids.WATER)) {
                blockstate1 = (BlockState) this.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
            } else {
                blockstate1 = this.defaultBlockState();
            }

            return (BlockState) blockstate1.setValue(getFaceProperty(placementDirection), true);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (!this.canRotate) {
            return state;
        } else {
            Objects.requireNonNull(rotation);
            return this.mapDirections(state, rotation::rotate);
        }
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.FRONT_BACK && !this.canMirrorX) {
            return state;
        } else if (mirror == Mirror.LEFT_RIGHT && !this.canMirrorZ) {
            return state;
        } else {
            Objects.requireNonNull(mirror);
            return this.mapDirections(state, mirror::mirror);
        }
    }

    private BlockState mapDirections(BlockState state, Function<Direction, Direction> mapping) {
        BlockState blockstate1 = state;

        for (Direction direction : MultifaceBlock.DIRECTIONS) {
            if (this.isFaceSupported(direction)) {
                blockstate1 = (BlockState) blockstate1.setValue(getFaceProperty((Direction) mapping.apply(direction)), (Boolean) state.getValue(getFaceProperty(direction)));
            }
        }

        return blockstate1;
    }

    public static boolean hasFace(BlockState state, Direction faceDirection) {
        BooleanProperty booleanproperty = getFaceProperty(faceDirection);

        return (Boolean) state.getValueOrElse(booleanproperty, false);
    }

    public static boolean canAttachTo(BlockGetter level, BlockPos pos, Direction directionTowardsNeighbour) {
        BlockPos blockpos1 = pos.relative(directionTowardsNeighbour);
        BlockState blockstate = level.getBlockState(blockpos1);

        return canAttachTo(level, directionTowardsNeighbour, blockpos1, blockstate);
    }

    public static boolean canAttachTo(BlockGetter level, Direction directionTowardsNeighbour, BlockPos neighbourPos, BlockState neighbourState) {
        return Block.isFaceFull(neighbourState.getBlockSupportShape(level, neighbourPos), directionTowardsNeighbour.getOpposite()) || Block.isFaceFull(neighbourState.getCollisionShape(level, neighbourPos), directionTowardsNeighbour.getOpposite());
    }

    private static BlockState removeFace(BlockState state, BooleanProperty property) {
        BlockState blockstate1 = (BlockState) state.setValue(property, false);

        return hasAnyFace(blockstate1) ? blockstate1 : Blocks.AIR.defaultBlockState();
    }

    public static BooleanProperty getFaceProperty(Direction faceDirection) {
        return (BooleanProperty) MultifaceBlock.PROPERTY_BY_DIRECTION.get(faceDirection);
    }

    private static BlockState getDefaultMultifaceState(StateDefinition<Block, BlockState> stateDefinition) {
        BlockState blockstate = (BlockState) (stateDefinition.any()).setValue(MultifaceBlock.WATERLOGGED, false);

        for (BooleanProperty booleanproperty : MultifaceBlock.PROPERTY_BY_DIRECTION.values()) {
            blockstate = (BlockState) blockstate.trySetValue(booleanproperty, false);
        }

        return blockstate;
    }

    protected static boolean hasAnyFace(BlockState state) {
        for (Direction direction : MultifaceBlock.DIRECTIONS) {
            if (hasFace(state, direction)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasAnyVacantFace(BlockState state) {
        for (Direction direction : MultifaceBlock.DIRECTIONS) {
            if (!hasFace(state, direction)) {
                return true;
            }
        }

        return false;
    }
}

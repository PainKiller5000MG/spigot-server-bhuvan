package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class MossyCarpetBlock extends Block implements BonemealableBlock {

    public static final MapCodec<MossyCarpetBlock> CODEC = simpleCodec(MossyCarpetBlock::new);
    public static final BooleanProperty BASE = BlockStateProperties.BOTTOM;
    public static final EnumProperty<WallSide> NORTH = BlockStateProperties.NORTH_WALL;
    public static final EnumProperty<WallSide> EAST = BlockStateProperties.EAST_WALL;
    public static final EnumProperty<WallSide> SOUTH = BlockStateProperties.SOUTH_WALL;
    public static final EnumProperty<WallSide> WEST = BlockStateProperties.WEST_WALL;
    public static final Map<Direction, EnumProperty<WallSide>> PROPERTY_BY_DIRECTION = ImmutableMap.copyOf(Maps.newEnumMap(Map.of(Direction.NORTH, MossyCarpetBlock.NORTH, Direction.EAST, MossyCarpetBlock.EAST, Direction.SOUTH, MossyCarpetBlock.SOUTH, Direction.WEST, MossyCarpetBlock.WEST)));
    private final Function<BlockState, VoxelShape> shapes;

    @Override
    public MapCodec<MossyCarpetBlock> codec() {
        return MossyCarpetBlock.CODEC;
    }

    public MossyCarpetBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(MossyCarpetBlock.BASE, true)).setValue(MossyCarpetBlock.NORTH, WallSide.NONE)).setValue(MossyCarpetBlock.EAST, WallSide.NONE)).setValue(MossyCarpetBlock.SOUTH, WallSide.NONE)).setValue(MossyCarpetBlock.WEST, WallSide.NONE));
        this.shapes = this.makeShapes();
    }

    public Function<BlockState, VoxelShape> makeShapes() {
        Map<Direction, VoxelShape> map = Shapes.rotateHorizontal(Block.boxZ(16.0D, 0.0D, 10.0D, 0.0D, 1.0D));
        Map<Direction, VoxelShape> map1 = Shapes.rotateAll(Block.boxZ(16.0D, 0.0D, 1.0D));

        return this.getShapeForEachState((blockstate) -> {
            VoxelShape voxelshape = (Boolean) blockstate.getValue(MossyCarpetBlock.BASE) ? (VoxelShape) map1.get(Direction.DOWN) : Shapes.empty();

            for (Map.Entry<Direction, EnumProperty<WallSide>> map_entry : MossyCarpetBlock.PROPERTY_BY_DIRECTION.entrySet()) {
                switch ((WallSide) blockstate.getValue((Property) map_entry.getValue())) {
                    case NONE:
                    default:
                        break;
                    case LOW:
                        voxelshape = Shapes.or(voxelshape, (VoxelShape) map.get(map_entry.getKey()));
                        break;
                    case TALL:
                        voxelshape = Shapes.or(voxelshape, (VoxelShape) map1.get(map_entry.getKey()));
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
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (Boolean) state.getValue(MossyCarpetBlock.BASE) ? (VoxelShape) this.shapes.apply(this.defaultBlockState()) : Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState blockstate1 = level.getBlockState(pos.below());

        return (Boolean) state.getValue(MossyCarpetBlock.BASE) ? !blockstate1.isAir() : blockstate1.is(this) && (Boolean) blockstate1.getValue(MossyCarpetBlock.BASE);
    }

    private static boolean hasFaces(BlockState blockState) {
        if ((Boolean) blockState.getValue(MossyCarpetBlock.BASE)) {
            return true;
        } else {
            for (EnumProperty<WallSide> enumproperty : MossyCarpetBlock.PROPERTY_BY_DIRECTION.values()) {
                if (blockState.getValue(enumproperty) != WallSide.NONE) {
                    return true;
                }
            }

            return false;
        }
    }

    private static boolean canSupportAtFace(BlockGetter level, BlockPos pos, Direction direction) {
        return direction == Direction.UP ? false : MultifaceBlock.canAttachTo(level, pos, direction);
    }

    private static BlockState getUpdatedState(BlockState state, BlockGetter level, BlockPos pos, boolean createSides) {
        BlockState blockstate1 = null;
        BlockState blockstate2 = null;

        createSides |= (Boolean) state.getValue(MossyCarpetBlock.BASE);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            EnumProperty<WallSide> enumproperty = getPropertyForFace(direction);
            WallSide wallside = canSupportAtFace(level, pos, direction) ? (createSides ? WallSide.LOW : (WallSide) state.getValue(enumproperty)) : WallSide.NONE;

            if (wallside == WallSide.LOW) {
                if (blockstate1 == null) {
                    blockstate1 = level.getBlockState(pos.above());
                }

                if (blockstate1.is(Blocks.PALE_MOSS_CARPET) && blockstate1.getValue(enumproperty) != WallSide.NONE && !(Boolean) blockstate1.getValue(MossyCarpetBlock.BASE)) {
                    wallside = WallSide.TALL;
                }

                if (!(Boolean) state.getValue(MossyCarpetBlock.BASE)) {
                    if (blockstate2 == null) {
                        blockstate2 = level.getBlockState(pos.below());
                    }

                    if (blockstate2.is(Blocks.PALE_MOSS_CARPET) && blockstate2.getValue(enumproperty) == WallSide.NONE) {
                        wallside = WallSide.NONE;
                    }
                }
            }

            state = (BlockState) state.setValue(enumproperty, wallside);
        }

        return state;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return getUpdatedState(this.defaultBlockState(), context.getLevel(), context.getClickedPos(), true);
    }

    public static void placeAt(LevelAccessor level, BlockPos pos, RandomSource random, @Block.UpdateFlags int updateType) {
        BlockState blockstate = Blocks.PALE_MOSS_CARPET.defaultBlockState();
        BlockState blockstate1 = getUpdatedState(blockstate, level, pos, true);

        level.setBlock(pos, blockstate1, updateType);
        Objects.requireNonNull(random);
        BlockState blockstate2 = createTopperWithSideChance(level, pos, random::nextBoolean);

        if (!blockstate2.isAir()) {
            level.setBlock(pos.above(), blockstate2, updateType);
            BlockState blockstate3 = getUpdatedState(blockstate1, level, pos, true);

            level.setBlock(pos, blockstate3, updateType);
        }

    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        if (!level.isClientSide()) {
            RandomSource randomsource = level.getRandom();

            Objects.requireNonNull(randomsource);
            BlockState blockstate1 = createTopperWithSideChance(level, pos, randomsource::nextBoolean);

            if (!blockstate1.isAir()) {
                level.setBlock(pos.above(), blockstate1, 3);
            }

        }
    }

    private static BlockState createTopperWithSideChance(BlockGetter level, BlockPos pos, BooleanSupplier sideSurvivalTest) {
        BlockPos blockpos1 = pos.above();
        BlockState blockstate = level.getBlockState(blockpos1);
        boolean flag = blockstate.is(Blocks.PALE_MOSS_CARPET);

        if ((!flag || !(Boolean) blockstate.getValue(MossyCarpetBlock.BASE)) && (flag || blockstate.canBeReplaced())) {
            BlockState blockstate1 = (BlockState) Blocks.PALE_MOSS_CARPET.defaultBlockState().setValue(MossyCarpetBlock.BASE, false);
            BlockState blockstate2 = getUpdatedState(blockstate1, level, pos.above(), true);

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                EnumProperty<WallSide> enumproperty = getPropertyForFace(direction);

                if (blockstate2.getValue(enumproperty) != WallSide.NONE && !sideSurvivalTest.getAsBoolean()) {
                    blockstate2 = (BlockState) blockstate2.setValue(enumproperty, WallSide.NONE);
                }
            }

            if (hasFaces(blockstate2) && blockstate2 != blockstate) {
                return blockstate2;
            } else {
                return Blocks.AIR.defaultBlockState();
            }
        } else {
            return Blocks.AIR.defaultBlockState();
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            BlockState blockstate2 = getUpdatedState(state, level, pos, false);

            return !hasFaces(blockstate2) ? Blocks.AIR.defaultBlockState() : blockstate2;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MossyCarpetBlock.BASE, MossyCarpetBlock.NORTH, MossyCarpetBlock.EAST, MossyCarpetBlock.SOUTH, MossyCarpetBlock.WEST);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState blockstate1;

        switch (rotation) {
            case CLOCKWISE_180:
                blockstate1 = (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(MossyCarpetBlock.NORTH, (WallSide) state.getValue(MossyCarpetBlock.SOUTH))).setValue(MossyCarpetBlock.EAST, (WallSide) state.getValue(MossyCarpetBlock.WEST))).setValue(MossyCarpetBlock.SOUTH, (WallSide) state.getValue(MossyCarpetBlock.NORTH))).setValue(MossyCarpetBlock.WEST, (WallSide) state.getValue(MossyCarpetBlock.EAST));
                break;
            case COUNTERCLOCKWISE_90:
                blockstate1 = (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(MossyCarpetBlock.NORTH, (WallSide) state.getValue(MossyCarpetBlock.EAST))).setValue(MossyCarpetBlock.EAST, (WallSide) state.getValue(MossyCarpetBlock.SOUTH))).setValue(MossyCarpetBlock.SOUTH, (WallSide) state.getValue(MossyCarpetBlock.WEST))).setValue(MossyCarpetBlock.WEST, (WallSide) state.getValue(MossyCarpetBlock.NORTH));
                break;
            case CLOCKWISE_90:
                blockstate1 = (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(MossyCarpetBlock.NORTH, (WallSide) state.getValue(MossyCarpetBlock.WEST))).setValue(MossyCarpetBlock.EAST, (WallSide) state.getValue(MossyCarpetBlock.NORTH))).setValue(MossyCarpetBlock.SOUTH, (WallSide) state.getValue(MossyCarpetBlock.EAST))).setValue(MossyCarpetBlock.WEST, (WallSide) state.getValue(MossyCarpetBlock.SOUTH));
                break;
            default:
                blockstate1 = state;
        }

        return blockstate1;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState blockstate1;

        switch (mirror) {
            case LEFT_RIGHT:
                blockstate1 = (BlockState) ((BlockState) state.setValue(MossyCarpetBlock.NORTH, (WallSide) state.getValue(MossyCarpetBlock.SOUTH))).setValue(MossyCarpetBlock.SOUTH, (WallSide) state.getValue(MossyCarpetBlock.NORTH));
                break;
            case FRONT_BACK:
                blockstate1 = (BlockState) ((BlockState) state.setValue(MossyCarpetBlock.EAST, (WallSide) state.getValue(MossyCarpetBlock.WEST))).setValue(MossyCarpetBlock.WEST, (WallSide) state.getValue(MossyCarpetBlock.EAST));
                break;
            default:
                blockstate1 = super.mirror(state, mirror);
        }

        return blockstate1;
    }

    public static @Nullable EnumProperty<WallSide> getPropertyForFace(Direction direction) {
        return (EnumProperty) MossyCarpetBlock.PROPERTY_BY_DIRECTION.get(direction);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return (Boolean) state.getValue(MossyCarpetBlock.BASE) && !createTopperWithSideChance(level, pos, () -> {
            return true;
        }).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockState blockstate1 = createTopperWithSideChance(level, pos, () -> {
            return true;
        });

        if (!blockstate1.isAir()) {
            level.setBlock(pos.above(), blockstate1, 3);
        }

    }
}

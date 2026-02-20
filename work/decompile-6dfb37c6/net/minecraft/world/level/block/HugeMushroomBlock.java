package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

public class HugeMushroomBlock extends Block {

    public static final MapCodec<HugeMushroomBlock> CODEC = simpleCodec(HugeMushroomBlock::new);
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final BooleanProperty UP = PipeBlock.UP;
    public static final BooleanProperty DOWN = PipeBlock.DOWN;
    private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION;

    @Override
    public MapCodec<HugeMushroomBlock> codec() {
        return HugeMushroomBlock.CODEC;
    }

    public HugeMushroomBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(HugeMushroomBlock.NORTH, true)).setValue(HugeMushroomBlock.EAST, true)).setValue(HugeMushroomBlock.SOUTH, true)).setValue(HugeMushroomBlock.WEST, true)).setValue(HugeMushroomBlock.UP, true)).setValue(HugeMushroomBlock.DOWN, true));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter blockgetter = context.getLevel();
        BlockPos blockpos = context.getClickedPos();

        return (BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(HugeMushroomBlock.DOWN, !blockgetter.getBlockState(blockpos.below()).is(this))).setValue(HugeMushroomBlock.UP, !blockgetter.getBlockState(blockpos.above()).is(this))).setValue(HugeMushroomBlock.NORTH, !blockgetter.getBlockState(blockpos.north()).is(this))).setValue(HugeMushroomBlock.EAST, !blockgetter.getBlockState(blockpos.east()).is(this))).setValue(HugeMushroomBlock.SOUTH, !blockgetter.getBlockState(blockpos.south()).is(this))).setValue(HugeMushroomBlock.WEST, !blockgetter.getBlockState(blockpos.west()).is(this));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return neighbourState.is(this) ? (BlockState) state.setValue((Property) HugeMushroomBlock.PROPERTY_BY_DIRECTION.get(directionToNeighbour), false) : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue((Property) HugeMushroomBlock.PROPERTY_BY_DIRECTION.get(rotation.rotate(Direction.NORTH)), (Boolean) state.getValue(HugeMushroomBlock.NORTH))).setValue((Property) HugeMushroomBlock.PROPERTY_BY_DIRECTION.get(rotation.rotate(Direction.SOUTH)), (Boolean) state.getValue(HugeMushroomBlock.SOUTH))).setValue((Property) HugeMushroomBlock.PROPERTY_BY_DIRECTION.get(rotation.rotate(Direction.EAST)), (Boolean) state.getValue(HugeMushroomBlock.EAST))).setValue((Property) HugeMushroomBlock.PROPERTY_BY_DIRECTION.get(rotation.rotate(Direction.WEST)), (Boolean) state.getValue(HugeMushroomBlock.WEST))).setValue((Property) HugeMushroomBlock.PROPERTY_BY_DIRECTION.get(rotation.rotate(Direction.UP)), (Boolean) state.getValue(HugeMushroomBlock.UP))).setValue((Property) HugeMushroomBlock.PROPERTY_BY_DIRECTION.get(rotation.rotate(Direction.DOWN)), (Boolean) state.getValue(HugeMushroomBlock.DOWN));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return (BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue((Property) HugeMushroomBlock.PROPERTY_BY_DIRECTION.get(mirror.mirror(Direction.NORTH)), (Boolean) state.getValue(HugeMushroomBlock.NORTH))).setValue((Property) HugeMushroomBlock.PROPERTY_BY_DIRECTION.get(mirror.mirror(Direction.SOUTH)), (Boolean) state.getValue(HugeMushroomBlock.SOUTH))).setValue((Property) HugeMushroomBlock.PROPERTY_BY_DIRECTION.get(mirror.mirror(Direction.EAST)), (Boolean) state.getValue(HugeMushroomBlock.EAST))).setValue((Property) HugeMushroomBlock.PROPERTY_BY_DIRECTION.get(mirror.mirror(Direction.WEST)), (Boolean) state.getValue(HugeMushroomBlock.WEST))).setValue((Property) HugeMushroomBlock.PROPERTY_BY_DIRECTION.get(mirror.mirror(Direction.UP)), (Boolean) state.getValue(HugeMushroomBlock.UP))).setValue((Property) HugeMushroomBlock.PROPERTY_BY_DIRECTION.get(mirror.mirror(Direction.DOWN)), (Boolean) state.getValue(HugeMushroomBlock.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HugeMushroomBlock.UP, HugeMushroomBlock.DOWN, HugeMushroomBlock.NORTH, HugeMushroomBlock.EAST, HugeMushroomBlock.SOUTH, HugeMushroomBlock.WEST);
    }
}

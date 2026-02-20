package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;

public class ChorusPlantBlock extends PipeBlock {

    public static final MapCodec<ChorusPlantBlock> CODEC = simpleCodec(ChorusPlantBlock::new);

    @Override
    public MapCodec<ChorusPlantBlock> codec() {
        return ChorusPlantBlock.CODEC;
    }

    protected ChorusPlantBlock(BlockBehaviour.Properties properties) {
        super(10.0F, properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(ChorusPlantBlock.NORTH, false)).setValue(ChorusPlantBlock.EAST, false)).setValue(ChorusPlantBlock.SOUTH, false)).setValue(ChorusPlantBlock.WEST, false)).setValue(ChorusPlantBlock.UP, false)).setValue(ChorusPlantBlock.DOWN, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return getStateWithConnections(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
    }

    public static BlockState getStateWithConnections(BlockGetter level, BlockPos pos, BlockState defaultState) {
        BlockState blockstate1 = level.getBlockState(pos.below());
        BlockState blockstate2 = level.getBlockState(pos.above());
        BlockState blockstate3 = level.getBlockState(pos.north());
        BlockState blockstate4 = level.getBlockState(pos.east());
        BlockState blockstate5 = level.getBlockState(pos.south());
        BlockState blockstate6 = level.getBlockState(pos.west());
        Block block = defaultState.getBlock();

        return (BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) defaultState.trySetValue(ChorusPlantBlock.DOWN, blockstate1.is(block) || blockstate1.is(Blocks.CHORUS_FLOWER) || blockstate1.is(Blocks.END_STONE))).trySetValue(ChorusPlantBlock.UP, blockstate2.is(block) || blockstate2.is(Blocks.CHORUS_FLOWER))).trySetValue(ChorusPlantBlock.NORTH, blockstate3.is(block) || blockstate3.is(Blocks.CHORUS_FLOWER))).trySetValue(ChorusPlantBlock.EAST, blockstate4.is(block) || blockstate4.is(Blocks.CHORUS_FLOWER))).trySetValue(ChorusPlantBlock.SOUTH, blockstate5.is(block) || blockstate5.is(Blocks.CHORUS_FLOWER))).trySetValue(ChorusPlantBlock.WEST, blockstate6.is(block) || blockstate6.is(Blocks.CHORUS_FLOWER));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            ticks.scheduleTick(pos, (Block) this, 1);
            return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
        } else {
            boolean flag = neighbourState.is(this) || neighbourState.is(Blocks.CHORUS_FLOWER) || directionToNeighbour == Direction.DOWN && neighbourState.is(Blocks.END_STONE);

            return (BlockState) state.setValue((Property) ChorusPlantBlock.PROPERTY_BY_DIRECTION.get(directionToNeighbour), flag);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }

    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState blockstate1 = level.getBlockState(pos.below());
        boolean flag = !level.getBlockState(pos.above()).isAir() && !blockstate1.isAir();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos1 = pos.relative(direction);
            BlockState blockstate2 = level.getBlockState(blockpos1);

            if (blockstate2.is(this)) {
                if (flag) {
                    return false;
                }

                BlockState blockstate3 = level.getBlockState(blockpos1.below());

                if (blockstate3.is(this) || blockstate3.is(Blocks.END_STONE)) {
                    return true;
                }
            }
        }

        return blockstate1.is(this) || blockstate1.is(Blocks.END_STONE);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ChorusPlantBlock.NORTH, ChorusPlantBlock.EAST, ChorusPlantBlock.SOUTH, ChorusPlantBlock.WEST, ChorusPlantBlock.UP, ChorusPlantBlock.DOWN);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}

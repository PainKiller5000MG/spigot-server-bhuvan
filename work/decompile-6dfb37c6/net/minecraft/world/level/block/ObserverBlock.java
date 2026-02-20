package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;

public class ObserverBlock extends DirectionalBlock {

    public static final MapCodec<ObserverBlock> CODEC = simpleCodec(ObserverBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    @Override
    public MapCodec<ObserverBlock> codec() {
        return ObserverBlock.CODEC;
    }

    public ObserverBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(ObserverBlock.FACING, Direction.SOUTH)).setValue(ObserverBlock.POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ObserverBlock.FACING, ObserverBlock.POWERED);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(ObserverBlock.FACING, rotation.rotate((Direction) state.getValue(ObserverBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(ObserverBlock.FACING)));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(ObserverBlock.POWERED)) {
            level.setBlock(pos, (BlockState) state.setValue(ObserverBlock.POWERED, false), 2);
        } else {
            level.setBlock(pos, (BlockState) state.setValue(ObserverBlock.POWERED, true), 2);
            level.scheduleTick(pos, (Block) this, 2);
        }

        this.updateNeighborsInFront(level, pos, state);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (state.getValue(ObserverBlock.FACING) == directionToNeighbour && !(Boolean) state.getValue(ObserverBlock.POWERED)) {
            this.startSignal(level, ticks, pos);
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    private void startSignal(LevelReader level, ScheduledTickAccess ticks, BlockPos pos) {
        if (!level.isClientSide() && !ticks.getBlockTicks().hasScheduledTick(pos, this)) {
            ticks.scheduleTick(pos, (Block) this, 2);
        }

    }

    protected void updateNeighborsInFront(Level level, BlockPos pos, BlockState state) {
        Direction direction = (Direction) state.getValue(ObserverBlock.FACING);
        BlockPos blockpos1 = pos.relative(direction.getOpposite());
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, direction.getOpposite(), (Direction) null);

        level.neighborChanged(blockpos1, this, orientation);
        level.updateNeighborsAtExceptFromFacing(blockpos1, this, direction, orientation);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getSignal(level, pos, direction);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(ObserverBlock.POWERED) && state.getValue(ObserverBlock.FACING) == direction ? 15 : 0;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!state.is(oldState.getBlock())) {
            if (!level.isClientSide() && (Boolean) state.getValue(ObserverBlock.POWERED) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
                BlockState blockstate2 = (BlockState) state.setValue(ObserverBlock.POWERED, false);

                level.setBlock(pos, blockstate2, 18);
                this.updateNeighborsInFront(level, pos, blockstate2);
            }

        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if ((Boolean) state.getValue(ObserverBlock.POWERED) && level.getBlockTicks().hasScheduledTick(pos, this)) {
            this.updateNeighborsInFront(level, pos, (BlockState) state.setValue(ObserverBlock.POWERED, false));
        }

    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState) this.defaultBlockState().setValue(ObserverBlock.FACING, context.getNearestLookingDirection().getOpposite().getOpposite());
    }
}

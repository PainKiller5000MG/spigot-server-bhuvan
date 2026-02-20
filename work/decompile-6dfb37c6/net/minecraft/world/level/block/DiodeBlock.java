package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;
import org.jspecify.annotations.Nullable;

public abstract class DiodeBlock extends HorizontalDirectionalBlock {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape SHAPE = Block.column(16.0D, 0.0D, 2.0D);

    protected DiodeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected abstract MapCodec<? extends DiodeBlock> codec();

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return DiodeBlock.SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockpos1 = pos.below();

        return this.canSurviveOn(level, blockpos1, level.getBlockState(blockpos1));
    }

    protected boolean canSurviveOn(LevelReader level, BlockPos neightborPos, BlockState neighborState) {
        return neighborState.isFaceSturdy(level, neightborPos, Direction.UP, SupportType.RIGID);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!this.isLocked(level, pos, state)) {
            boolean flag = (Boolean) state.getValue(DiodeBlock.POWERED);
            boolean flag1 = this.shouldTurnOn(level, pos, state);

            if (flag && !flag1) {
                level.setBlock(pos, (BlockState) state.setValue(DiodeBlock.POWERED, false), 2);
            } else if (!flag) {
                level.setBlock(pos, (BlockState) state.setValue(DiodeBlock.POWERED, true), 2);
                if (!flag1) {
                    level.scheduleTick(pos, (Block) this, this.getDelay(state), TickPriority.VERY_HIGH);
                }
            }

        }
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getSignal(level, pos, direction);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return !(Boolean) state.getValue(DiodeBlock.POWERED) ? 0 : (state.getValue(DiodeBlock.FACING) == direction ? this.getOutputSignal(level, pos, state) : 0);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (state.canSurvive(level, pos)) {
            this.checkTickOnNeighbor(level, pos, state);
        } else {
            BlockEntity blockentity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;

            dropResources(state, level, pos, blockentity);
            level.removeBlock(pos, false);

            for (Direction direction : Direction.values()) {
                level.updateNeighborsAt(pos.relative(direction), this);
            }

        }
    }

    protected void checkTickOnNeighbor(Level level, BlockPos pos, BlockState state) {
        if (!this.isLocked(level, pos, state)) {
            boolean flag = (Boolean) state.getValue(DiodeBlock.POWERED);
            boolean flag1 = this.shouldTurnOn(level, pos, state);

            if (flag != flag1 && !level.getBlockTicks().willTickThisTick(pos, this)) {
                TickPriority tickpriority = TickPriority.HIGH;

                if (this.shouldPrioritize(level, pos, state)) {
                    tickpriority = TickPriority.EXTREMELY_HIGH;
                } else if (flag) {
                    tickpriority = TickPriority.VERY_HIGH;
                }

                level.scheduleTick(pos, (Block) this, this.getDelay(state), tickpriority);
            }

        }
    }

    public boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
        return false;
    }

    protected boolean shouldTurnOn(Level level, BlockPos pos, BlockState state) {
        return this.getInputSignal(level, pos, state) > 0;
    }

    protected int getInputSignal(Level level, BlockPos pos, BlockState state) {
        Direction direction = (Direction) state.getValue(DiodeBlock.FACING);
        BlockPos blockpos1 = pos.relative(direction);
        int i = level.getSignal(blockpos1, direction);

        if (i >= 15) {
            return i;
        } else {
            BlockState blockstate1 = level.getBlockState(blockpos1);

            return Math.max(i, blockstate1.is(Blocks.REDSTONE_WIRE) ? (Integer) blockstate1.getValue(RedStoneWireBlock.POWER) : 0);
        }
    }

    protected int getAlternateSignal(SignalGetter level, BlockPos pos, BlockState state) {
        Direction direction = (Direction) state.getValue(DiodeBlock.FACING);
        Direction direction1 = direction.getClockWise();
        Direction direction2 = direction.getCounterClockWise();
        boolean flag = this.sideInputDiodesOnly();

        return Math.max(level.getControlInputSignal(pos.relative(direction1), direction1, flag), level.getControlInputSignal(pos.relative(direction2), direction2, flag));
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState) this.defaultBlockState().setValue(DiodeBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        if (this.shouldTurnOn(level, pos, state)) {
            level.scheduleTick(pos, (Block) this, 1);
        }

    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        this.updateNeighborsInFront(level, pos, state);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston) {
            this.updateNeighborsInFront(level, pos, state);
        }

    }

    protected void updateNeighborsInFront(Level level, BlockPos pos, BlockState state) {
        Direction direction = (Direction) state.getValue(DiodeBlock.FACING);
        BlockPos blockpos1 = pos.relative(direction.getOpposite());
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, direction.getOpposite(), Direction.UP);

        level.neighborChanged(blockpos1, this, orientation);
        level.updateNeighborsAtExceptFromFacing(blockpos1, this, direction, orientation);
    }

    protected boolean sideInputDiodesOnly() {
        return false;
    }

    protected int getOutputSignal(BlockGetter level, BlockPos pos, BlockState state) {
        return 15;
    }

    public static boolean isDiode(BlockState state) {
        return state.getBlock() instanceof DiodeBlock;
    }

    public boolean shouldPrioritize(BlockGetter level, BlockPos pos, BlockState state) {
        Direction direction = ((Direction) state.getValue(DiodeBlock.FACING)).getOpposite();
        BlockState blockstate1 = level.getBlockState(pos.relative(direction));

        return isDiode(blockstate1) && blockstate1.getValue(DiodeBlock.FACING) != direction;
    }

    protected abstract int getDelay(BlockState state);
}

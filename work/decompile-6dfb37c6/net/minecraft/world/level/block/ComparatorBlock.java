package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.ticks.TickPriority;
import org.jspecify.annotations.Nullable;

public class ComparatorBlock extends DiodeBlock implements EntityBlock {

    public static final MapCodec<ComparatorBlock> CODEC = simpleCodec(ComparatorBlock::new);
    public static final EnumProperty<ComparatorMode> MODE = BlockStateProperties.MODE_COMPARATOR;

    @Override
    public MapCodec<ComparatorBlock> codec() {
        return ComparatorBlock.CODEC;
    }

    public ComparatorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(ComparatorBlock.FACING, Direction.NORTH)).setValue(ComparatorBlock.POWERED, false)).setValue(ComparatorBlock.MODE, ComparatorMode.COMPARE));
    }

    @Override
    protected int getDelay(BlockState state) {
        return 2;
    }

    @Override
    public BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return directionToNeighbour == Direction.DOWN && !this.canSurviveOn(level, neighbourPos, neighbourState) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected int getOutputSignal(BlockGetter level, BlockPos pos, BlockState state) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        return blockentity instanceof ComparatorBlockEntity ? ((ComparatorBlockEntity) blockentity).getOutputSignal() : 0;
    }

    private int calculateOutputSignal(Level level, BlockPos pos, BlockState state) {
        int i = this.getInputSignal(level, pos, state);

        if (i == 0) {
            return 0;
        } else {
            int j = this.getAlternateSignal(level, pos, state);

            return j > i ? 0 : (state.getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT ? i - j : i);
        }
    }

    @Override
    protected boolean shouldTurnOn(Level level, BlockPos pos, BlockState state) {
        int i = this.getInputSignal(level, pos, state);

        if (i == 0) {
            return false;
        } else {
            int j = this.getAlternateSignal(level, pos, state);

            return i > j ? true : i == j && state.getValue(ComparatorBlock.MODE) == ComparatorMode.COMPARE;
        }
    }

    @Override
    protected int getInputSignal(Level level, BlockPos pos, BlockState state) {
        int i = super.getInputSignal(level, pos, state);
        Direction direction = (Direction) state.getValue(ComparatorBlock.FACING);
        BlockPos blockpos1 = pos.relative(direction);
        BlockState blockstate1 = level.getBlockState(blockpos1);

        if (blockstate1.hasAnalogOutputSignal()) {
            i = blockstate1.getAnalogOutputSignal(level, blockpos1, direction.getOpposite());
        } else if (i < 15 && blockstate1.isRedstoneConductor(level, blockpos1)) {
            blockpos1 = blockpos1.relative(direction);
            blockstate1 = level.getBlockState(blockpos1);
            ItemFrame itemframe = this.getItemFrame(level, direction, blockpos1);
            int j = Math.max(itemframe == null ? Integer.MIN_VALUE : itemframe.getAnalogOutput(), blockstate1.hasAnalogOutputSignal() ? blockstate1.getAnalogOutputSignal(level, blockpos1, direction.getOpposite()) : Integer.MIN_VALUE);

            if (j != Integer.MIN_VALUE) {
                i = j;
            }
        }

        return i;
    }

    private @Nullable ItemFrame getItemFrame(Level level, Direction direction, BlockPos tPos) {
        List<ItemFrame> list = level.<ItemFrame>getEntitiesOfClass(ItemFrame.class, new AABB((double) tPos.getX(), (double) tPos.getY(), (double) tPos.getZ(), (double) (tPos.getX() + 1), (double) (tPos.getY() + 1), (double) (tPos.getZ() + 1)), (itemframe) -> {
            return itemframe.getDirection() == direction;
        });

        return list.size() == 1 ? (ItemFrame) list.get(0) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        } else {
            state = (BlockState) state.cycle(ComparatorBlock.MODE);
            float f = state.getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT ? 0.55F : 0.5F;

            level.playSound(player, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F, f);
            level.setBlock(pos, state, 2);
            this.refreshOutputState(level, pos, state);
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    protected void checkTickOnNeighbor(Level level, BlockPos pos, BlockState state) {
        if (!level.getBlockTicks().willTickThisTick(pos, this)) {
            int i = this.calculateOutputSignal(level, pos, state);
            BlockEntity blockentity = level.getBlockEntity(pos);
            int j = blockentity instanceof ComparatorBlockEntity ? ((ComparatorBlockEntity) blockentity).getOutputSignal() : 0;

            if (i != j || (Boolean) state.getValue(ComparatorBlock.POWERED) != this.shouldTurnOn(level, pos, state)) {
                TickPriority tickpriority = this.shouldPrioritize(level, pos, state) ? TickPriority.HIGH : TickPriority.NORMAL;

                level.scheduleTick(pos, (Block) this, 2, tickpriority);
            }

        }
    }

    private void refreshOutputState(Level level, BlockPos pos, BlockState state) {
        int i = this.calculateOutputSignal(level, pos, state);
        BlockEntity blockentity = level.getBlockEntity(pos);
        int j = 0;

        if (blockentity instanceof ComparatorBlockEntity comparatorblockentity) {
            j = comparatorblockentity.getOutputSignal();
            comparatorblockentity.setOutputSignal(i);
        }

        if (j != i || state.getValue(ComparatorBlock.MODE) == ComparatorMode.COMPARE) {
            boolean flag = this.shouldTurnOn(level, pos, state);
            boolean flag1 = (Boolean) state.getValue(ComparatorBlock.POWERED);

            if (flag1 && !flag) {
                level.setBlock(pos, (BlockState) state.setValue(ComparatorBlock.POWERED, false), 2);
            } else if (!flag1 && flag) {
                level.setBlock(pos, (BlockState) state.setValue(ComparatorBlock.POWERED, true), 2);
            }

            this.updateNeighborsInFront(level, pos, state);
        }

    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        this.refreshOutputState(level, pos, state);
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int b0, int b1) {
        super.triggerEvent(state, level, pos, b0, b1);
        BlockEntity blockentity = level.getBlockEntity(pos);

        return blockentity != null && blockentity.triggerEvent(b0, b1);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new ComparatorBlockEntity(worldPosition, blockState);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ComparatorBlock.FACING, ComparatorBlock.MODE, ComparatorBlock.POWERED);
    }
}

package net.minecraft.world.level.block;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

public class RedstoneTorchBlock extends BaseTorchBlock {

    public static final MapCodec<RedstoneTorchBlock> CODEC = simpleCodec(RedstoneTorchBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final Map<BlockGetter, List<RedstoneTorchBlock.Toggle>> RECENT_TOGGLES = new WeakHashMap();
    public static final int RECENT_TOGGLE_TIMER = 60;
    public static final int MAX_RECENT_TOGGLES = 8;
    public static final int RESTART_DELAY = 160;
    private static final int TOGGLE_DELAY = 2;

    @Override
    public MapCodec<? extends RedstoneTorchBlock> codec() {
        return RedstoneTorchBlock.CODEC;
    }

    protected RedstoneTorchBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(RedstoneTorchBlock.LIT, true));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        this.notifyNeighbors(level, pos, state);
    }

    private void notifyNeighbors(Level level, BlockPos pos, BlockState state) {
        Orientation orientation = this.randomOrientation(level, state);

        for (Direction direction : Direction.values()) {
            level.updateNeighborsAt(pos.relative(direction), this, ExperimentalRedstoneUtils.withFront(orientation, direction));
        }

    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston) {
            this.notifyNeighbors(level, pos, state);
        }

    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(RedstoneTorchBlock.LIT) && Direction.UP != direction ? 15 : 0;
    }

    protected boolean hasNeighborSignal(Level level, BlockPos pos, BlockState state) {
        return level.hasSignal(pos.below(), Direction.DOWN);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean flag = this.hasNeighborSignal(level, pos, state);
        List<RedstoneTorchBlock.Toggle> list = (List) RedstoneTorchBlock.RECENT_TOGGLES.get(level);

        while (list != null && !list.isEmpty() && level.getGameTime() - ((RedstoneTorchBlock.Toggle) list.get(0)).when > 60L) {
            list.remove(0);
        }

        if ((Boolean) state.getValue(RedstoneTorchBlock.LIT)) {
            if (flag) {
                level.setBlock(pos, (BlockState) state.setValue(RedstoneTorchBlock.LIT, false), 3);
                if (isToggledTooFrequently(level, pos, true)) {
                    level.levelEvent(1502, pos, 0);
                    level.scheduleTick(pos, level.getBlockState(pos).getBlock(), 160);
                }
            }
        } else if (!flag && !isToggledTooFrequently(level, pos, false)) {
            level.setBlock(pos, (BlockState) state.setValue(RedstoneTorchBlock.LIT, true), 3);
        }

    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if ((Boolean) state.getValue(RedstoneTorchBlock.LIT) == this.hasNeighborSignal(level, pos, state) && !level.getBlockTicks().willTickThisTick(pos, this)) {
            level.scheduleTick(pos, (Block) this, 2);
        }

    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == Direction.DOWN ? state.getSignal(level, pos, direction) : 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(RedstoneTorchBlock.LIT)) {
            double d0 = (double) pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.2D;
            double d1 = (double) pos.getY() + 0.7D + (random.nextDouble() - 0.5D) * 0.2D;
            double d2 = (double) pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.2D;

            level.addParticle(DustParticleOptions.REDSTONE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RedstoneTorchBlock.LIT);
    }

    private static boolean isToggledTooFrequently(Level level, BlockPos pos, boolean add) {
        List<RedstoneTorchBlock.Toggle> list = (List) RedstoneTorchBlock.RECENT_TOGGLES.computeIfAbsent(level, (blockgetter) -> {
            return Lists.newArrayList();
        });

        if (add) {
            list.add(new RedstoneTorchBlock.Toggle(pos.immutable(), level.getGameTime()));
        }

        int i = 0;

        for (RedstoneTorchBlock.Toggle redstonetorchblock_toggle : list) {
            if (redstonetorchblock_toggle.pos.equals(pos)) {
                ++i;
                if (i >= 8) {
                    return true;
                }
            }
        }

        return false;
    }

    protected @Nullable Orientation randomOrientation(Level level, BlockState state) {
        return ExperimentalRedstoneUtils.initialOrientation(level, (Direction) null, Direction.UP);
    }

    public static class Toggle {

        private final BlockPos pos;
        private final long when;

        public Toggle(BlockPos pos, long when) {
            this.pos = pos;
            this.when = when;
        }
    }
}

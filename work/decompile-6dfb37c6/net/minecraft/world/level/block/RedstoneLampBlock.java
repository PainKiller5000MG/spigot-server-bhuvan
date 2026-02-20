package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

public class RedstoneLampBlock extends Block {

    public static final MapCodec<RedstoneLampBlock> CODEC = simpleCodec(RedstoneLampBlock::new);
    public static final BooleanProperty LIT = RedstoneTorchBlock.LIT;

    @Override
    public MapCodec<RedstoneLampBlock> codec() {
        return RedstoneLampBlock.CODEC;
    }

    public RedstoneLampBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) this.defaultBlockState().setValue(RedstoneLampBlock.LIT, false));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState) this.defaultBlockState().setValue(RedstoneLampBlock.LIT, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            boolean flag1 = (Boolean) state.getValue(RedstoneLampBlock.LIT);

            if (flag1 != level.hasNeighborSignal(pos)) {
                if (flag1) {
                    level.scheduleTick(pos, (Block) this, 4);
                } else {
                    level.setBlock(pos, (BlockState) state.cycle(RedstoneLampBlock.LIT), 2);
                }
            }

        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(RedstoneLampBlock.LIT) && !level.hasNeighborSignal(pos)) {
            level.setBlock(pos, (BlockState) state.cycle(RedstoneLampBlock.LIT), 2);
        }

    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RedstoneLampBlock.LIT);
    }
}

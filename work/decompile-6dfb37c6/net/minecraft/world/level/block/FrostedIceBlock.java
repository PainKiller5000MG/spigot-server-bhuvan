package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

public class FrostedIceBlock extends IceBlock {

    public static final MapCodec<FrostedIceBlock> CODEC = simpleCodec(FrostedIceBlock::new);
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    private static final int NEIGHBORS_TO_AGE = 4;
    private static final int NEIGHBORS_TO_MELT = 2;

    @Override
    public MapCodec<FrostedIceBlock> codec() {
        return FrostedIceBlock.CODEC;
    }

    public FrostedIceBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(FrostedIceBlock.AGE, 0));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        level.scheduleTick(pos, (Block) this, Mth.nextInt(level.getRandom(), 60, 120));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0 || this.fewerNeigboursThan(level, pos, 4)) {
            int i = level.dimension() == Level.END ? level.getBrightness(LightLayer.BLOCK, pos) : level.getMaxLocalRawBrightness(pos);

            if (i > 11 - (Integer) state.getValue(FrostedIceBlock.AGE) - state.getLightBlock() && this.slightlyMelt(state, level, pos)) {
                BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

                for (Direction direction : Direction.values()) {
                    blockpos_mutableblockpos.setWithOffset(pos, direction);
                    BlockState blockstate1 = level.getBlockState(blockpos_mutableblockpos);

                    if (blockstate1.is(this) && !this.slightlyMelt(blockstate1, level, blockpos_mutableblockpos)) {
                        level.scheduleTick(blockpos_mutableblockpos, (Block) this, Mth.nextInt(random, 20, 40));
                    }
                }

                return;
            }
        }

        level.scheduleTick(pos, (Block) this, Mth.nextInt(random, 20, 40));
    }

    private boolean slightlyMelt(BlockState state, Level level, BlockPos pos) {
        int i = (Integer) state.getValue(FrostedIceBlock.AGE);

        if (i < 3) {
            level.setBlock(pos, (BlockState) state.setValue(FrostedIceBlock.AGE, i + 1), 2);
            return false;
        } else {
            this.melt(state, level, pos);
            return true;
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (block.defaultBlockState().is(this) && this.fewerNeigboursThan(level, pos, 2)) {
            this.melt(state, level, pos);
        }

        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
    }

    private boolean fewerNeigboursThan(BlockGetter level, BlockPos pos, int limit) {
        int j = 0;
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.values()) {
            blockpos_mutableblockpos.setWithOffset(pos, direction);
            if (level.getBlockState(blockpos_mutableblockpos).is(this)) {
                ++j;
                if (j >= limit) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FrostedIceBlock.AGE);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }
}

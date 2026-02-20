package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public class BuddingAmethystBlock extends AmethystBlock {

    public static final MapCodec<BuddingAmethystBlock> CODEC = simpleCodec(BuddingAmethystBlock::new);
    public static final int GROWTH_CHANCE = 5;
    private static final Direction[] DIRECTIONS = Direction.values();

    @Override
    public MapCodec<BuddingAmethystBlock> codec() {
        return BuddingAmethystBlock.CODEC;
    }

    public BuddingAmethystBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) == 0) {
            Direction direction = BuddingAmethystBlock.DIRECTIONS[random.nextInt(BuddingAmethystBlock.DIRECTIONS.length)];
            BlockPos blockpos1 = pos.relative(direction);
            BlockState blockstate1 = level.getBlockState(blockpos1);
            Block block = null;

            if (canClusterGrowAtState(blockstate1)) {
                block = Blocks.SMALL_AMETHYST_BUD;
            } else if (blockstate1.is(Blocks.SMALL_AMETHYST_BUD) && blockstate1.getValue(AmethystClusterBlock.FACING) == direction) {
                block = Blocks.MEDIUM_AMETHYST_BUD;
            } else if (blockstate1.is(Blocks.MEDIUM_AMETHYST_BUD) && blockstate1.getValue(AmethystClusterBlock.FACING) == direction) {
                block = Blocks.LARGE_AMETHYST_BUD;
            } else if (blockstate1.is(Blocks.LARGE_AMETHYST_BUD) && blockstate1.getValue(AmethystClusterBlock.FACING) == direction) {
                block = Blocks.AMETHYST_CLUSTER;
            }

            if (block != null) {
                BlockState blockstate2 = (BlockState) ((BlockState) block.defaultBlockState().setValue(AmethystClusterBlock.FACING, direction)).setValue(AmethystClusterBlock.WATERLOGGED, blockstate1.getFluidState().getType() == Fluids.WATER);

                level.setBlockAndUpdate(blockpos1, blockstate2);
            }

        }
    }

    public static boolean canClusterGrowAtState(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) && state.getFluidState().getAmount() == 8;
    }
}

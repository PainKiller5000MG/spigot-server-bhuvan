package net.minecraft.world.level.block;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public class NetherVines {

    private static final double BONEMEAL_GROW_PROBABILITY_DECREASE_RATE = 0.826D;
    public static final double GROW_PER_TICK_PROBABILITY = 0.1D;

    public NetherVines() {}

    public static boolean isValidGrowthState(BlockState state) {
        return state.isAir();
    }

    public static int getBlocksToGrowWhenBonemealed(RandomSource random) {
        double d0 = 1.0D;

        int i;

        for (i = 0; random.nextDouble() < d0; ++i) {
            d0 *= 0.826D;
        }

        return i;
    }
}

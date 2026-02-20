package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;

public class HugeBrownMushroomFeature extends AbstractHugeMushroomFeature {

    public HugeBrownMushroomFeature(Codec<HugeMushroomFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    protected void makeCap(LevelAccessor level, RandomSource random, BlockPos origin, int treeHeight, BlockPos.MutableBlockPos blockPos, HugeMushroomFeatureConfiguration config) {
        int j = config.foliageRadius;

        for (int k = -j; k <= j; ++k) {
            for (int l = -j; l <= j; ++l) {
                boolean flag = k == -j;
                boolean flag1 = k == j;
                boolean flag2 = l == -j;
                boolean flag3 = l == j;
                boolean flag4 = flag || flag1;
                boolean flag5 = flag2 || flag3;

                if (!flag4 || !flag5) {
                    blockPos.setWithOffset(origin, k, treeHeight, l);
                    boolean flag6 = flag || flag5 && k == 1 - j;
                    boolean flag7 = flag1 || flag5 && k == j - 1;
                    boolean flag8 = flag2 || flag4 && l == 1 - j;
                    boolean flag9 = flag3 || flag4 && l == j - 1;
                    BlockState blockstate = config.capProvider.getState(random, origin);

                    if (blockstate.hasProperty(HugeMushroomBlock.WEST) && blockstate.hasProperty(HugeMushroomBlock.EAST) && blockstate.hasProperty(HugeMushroomBlock.NORTH) && blockstate.hasProperty(HugeMushroomBlock.SOUTH)) {
                        blockstate = (BlockState) ((BlockState) ((BlockState) ((BlockState) blockstate.setValue(HugeMushroomBlock.WEST, flag6)).setValue(HugeMushroomBlock.EAST, flag7)).setValue(HugeMushroomBlock.NORTH, flag8)).setValue(HugeMushroomBlock.SOUTH, flag9);
                    }

                    this.placeMushroomBlock(level, blockPos, blockstate);
                }
            }
        }

    }

    @Override
    protected int getTreeRadiusForHeight(int trunkHeight, int treeHeight, int leafRadius, int yo) {
        return yo <= 3 ? 0 : leafRadius;
    }
}

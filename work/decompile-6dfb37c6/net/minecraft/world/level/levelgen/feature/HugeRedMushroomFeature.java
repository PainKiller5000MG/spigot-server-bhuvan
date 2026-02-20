package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;

public class HugeRedMushroomFeature extends AbstractHugeMushroomFeature {

    public HugeRedMushroomFeature(Codec<HugeMushroomFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    protected void makeCap(LevelAccessor level, RandomSource random, BlockPos origin, int treeHeight, BlockPos.MutableBlockPos blockPos, HugeMushroomFeatureConfiguration config) {
        for (int j = treeHeight - 3; j <= treeHeight; ++j) {
            int k = j < treeHeight ? config.foliageRadius : config.foliageRadius - 1;
            int l = config.foliageRadius - 2;

            for (int i1 = -k; i1 <= k; ++i1) {
                for (int j1 = -k; j1 <= k; ++j1) {
                    boolean flag = i1 == -k;
                    boolean flag1 = i1 == k;
                    boolean flag2 = j1 == -k;
                    boolean flag3 = j1 == k;
                    boolean flag4 = flag || flag1;
                    boolean flag5 = flag2 || flag3;

                    if (j >= treeHeight || flag4 != flag5) {
                        blockPos.setWithOffset(origin, i1, j, j1);
                        BlockState blockstate = config.capProvider.getState(random, origin);

                        if (blockstate.hasProperty(HugeMushroomBlock.WEST) && blockstate.hasProperty(HugeMushroomBlock.EAST) && blockstate.hasProperty(HugeMushroomBlock.NORTH) && blockstate.hasProperty(HugeMushroomBlock.SOUTH) && blockstate.hasProperty(HugeMushroomBlock.UP)) {
                            blockstate = (BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) blockstate.setValue(HugeMushroomBlock.UP, j >= treeHeight - 1)).setValue(HugeMushroomBlock.WEST, i1 < -l)).setValue(HugeMushroomBlock.EAST, i1 > l)).setValue(HugeMushroomBlock.NORTH, j1 < -l)).setValue(HugeMushroomBlock.SOUTH, j1 > l);
                        }

                        this.placeMushroomBlock(level, blockPos, blockstate);
                    }
                }
            }
        }

    }

    @Override
    protected int getTreeRadiusForHeight(int trunkHeight, int treeHeight, int leafRadius, int yo) {
        int i1 = 0;

        if (yo < treeHeight && yo >= treeHeight - 3) {
            i1 = leafRadius;
        } else if (yo == treeHeight) {
            i1 = leafRadius;
        }

        return i1;
    }
}

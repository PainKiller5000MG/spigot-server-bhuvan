package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;

public class DiskFeature extends Feature<DiskConfiguration> {

    public DiskFeature(Codec<DiskConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<DiskConfiguration> context) {
        DiskConfiguration diskconfiguration = context.config();
        BlockPos blockpos = context.origin();
        WorldGenLevel worldgenlevel = context.level();
        RandomSource randomsource = context.random();
        boolean flag = false;
        int i = blockpos.getY();
        int j = i + diskconfiguration.halfHeight();
        int k = i - diskconfiguration.halfHeight() - 1;
        int l = diskconfiguration.radius().sample(randomsource);
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (BlockPos blockpos1 : BlockPos.betweenClosed(blockpos.offset(-l, 0, -l), blockpos.offset(l, 0, l))) {
            int i1 = blockpos1.getX() - blockpos.getX();
            int j1 = blockpos1.getZ() - blockpos.getZ();

            if (i1 * i1 + j1 * j1 <= l * l) {
                flag |= this.placeColumn(diskconfiguration, worldgenlevel, randomsource, j, k, blockpos_mutableblockpos.set(blockpos1));
            }
        }

        return flag;
    }

    protected boolean placeColumn(DiskConfiguration config, WorldGenLevel level, RandomSource random, int top, int bottom, BlockPos.MutableBlockPos pos) {
        boolean flag = false;
        boolean flag1 = false;

        for (int k = top; k > bottom; --k) {
            pos.setY(k);
            if (config.target().test(level, pos)) {
                BlockState blockstate = config.stateProvider().getState(level, random, pos);

                level.setBlock(pos, blockstate, 2);
                if (!flag1) {
                    this.markAboveForPostProcessing(level, pos);
                }

                flag = true;
                flag1 = true;
            } else {
                flag1 = false;
            }
        }

        return flag;
    }
}

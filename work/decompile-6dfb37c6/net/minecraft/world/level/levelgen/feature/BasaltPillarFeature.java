package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class BasaltPillarFeature extends Feature<NoneFeatureConfiguration> {

    public BasaltPillarFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        BlockPos blockpos = context.origin();
        WorldGenLevel worldgenlevel = context.level();
        RandomSource randomsource = context.random();

        if (worldgenlevel.isEmptyBlock(blockpos) && !worldgenlevel.isEmptyBlock(blockpos.above())) {
            BlockPos.MutableBlockPos blockpos_mutableblockpos = blockpos.mutable();
            BlockPos.MutableBlockPos blockpos_mutableblockpos1 = blockpos.mutable();
            boolean flag = true;
            boolean flag1 = true;
            boolean flag2 = true;
            boolean flag3 = true;

            while (worldgenlevel.isEmptyBlock(blockpos_mutableblockpos)) {
                if (worldgenlevel.isOutsideBuildHeight(blockpos_mutableblockpos)) {
                    return true;
                }

                worldgenlevel.setBlock(blockpos_mutableblockpos, Blocks.BASALT.defaultBlockState(), 2);
                flag = flag && this.placeHangOff(worldgenlevel, randomsource, blockpos_mutableblockpos1.setWithOffset(blockpos_mutableblockpos, Direction.NORTH));
                flag1 = flag1 && this.placeHangOff(worldgenlevel, randomsource, blockpos_mutableblockpos1.setWithOffset(blockpos_mutableblockpos, Direction.SOUTH));
                flag2 = flag2 && this.placeHangOff(worldgenlevel, randomsource, blockpos_mutableblockpos1.setWithOffset(blockpos_mutableblockpos, Direction.WEST));
                flag3 = flag3 && this.placeHangOff(worldgenlevel, randomsource, blockpos_mutableblockpos1.setWithOffset(blockpos_mutableblockpos, Direction.EAST));
                blockpos_mutableblockpos.move(Direction.DOWN);
            }

            blockpos_mutableblockpos.move(Direction.UP);
            this.placeBaseHangOff(worldgenlevel, randomsource, blockpos_mutableblockpos1.setWithOffset(blockpos_mutableblockpos, Direction.NORTH));
            this.placeBaseHangOff(worldgenlevel, randomsource, blockpos_mutableblockpos1.setWithOffset(blockpos_mutableblockpos, Direction.SOUTH));
            this.placeBaseHangOff(worldgenlevel, randomsource, blockpos_mutableblockpos1.setWithOffset(blockpos_mutableblockpos, Direction.WEST));
            this.placeBaseHangOff(worldgenlevel, randomsource, blockpos_mutableblockpos1.setWithOffset(blockpos_mutableblockpos, Direction.EAST));
            blockpos_mutableblockpos.move(Direction.DOWN);
            BlockPos.MutableBlockPos blockpos_mutableblockpos2 = new BlockPos.MutableBlockPos();

            for (int i = -3; i < 4; ++i) {
                for (int j = -3; j < 4; ++j) {
                    int k = Mth.abs(i) * Mth.abs(j);

                    if (randomsource.nextInt(10) < 10 - k) {
                        blockpos_mutableblockpos2.set(blockpos_mutableblockpos.offset(i, 0, j));
                        int l = 3;

                        while (worldgenlevel.isEmptyBlock(blockpos_mutableblockpos1.setWithOffset(blockpos_mutableblockpos2, Direction.DOWN))) {
                            blockpos_mutableblockpos2.move(Direction.DOWN);
                            --l;
                            if (l <= 0) {
                                break;
                            }
                        }

                        if (!worldgenlevel.isEmptyBlock(blockpos_mutableblockpos1.setWithOffset(blockpos_mutableblockpos2, Direction.DOWN))) {
                            worldgenlevel.setBlock(blockpos_mutableblockpos2, Blocks.BASALT.defaultBlockState(), 2);
                        }
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private void placeBaseHangOff(LevelAccessor level, RandomSource random, BlockPos pos) {
        if (random.nextBoolean()) {
            level.setBlock(pos, Blocks.BASALT.defaultBlockState(), 2);
        }

    }

    private boolean placeHangOff(LevelAccessor level, RandomSource random, BlockPos hangOffPos) {
        if (random.nextInt(10) != 0) {
            level.setBlock(hangOffPos, Blocks.BASALT.defaultBlockState(), 2);
            return true;
        } else {
            return false;
        }
    }
}

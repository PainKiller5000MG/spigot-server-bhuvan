package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;

public class HugeFungusFeature extends Feature<HugeFungusConfiguration> {

    private static final float HUGE_PROBABILITY = 0.06F;

    public HugeFungusFeature(Codec<HugeFungusConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<HugeFungusConfiguration> context) {
        WorldGenLevel worldgenlevel = context.level();
        BlockPos blockpos = context.origin();
        RandomSource randomsource = context.random();
        ChunkGenerator chunkgenerator = context.chunkGenerator();
        HugeFungusConfiguration hugefungusconfiguration = context.config();
        Block block = hugefungusconfiguration.validBaseState.getBlock();
        BlockPos blockpos1 = null;
        BlockState blockstate = worldgenlevel.getBlockState(blockpos.below());

        if (blockstate.is(block)) {
            blockpos1 = blockpos;
        }

        if (blockpos1 == null) {
            return false;
        } else {
            int i = Mth.nextInt(randomsource, 4, 13);

            if (randomsource.nextInt(12) == 0) {
                i *= 2;
            }

            if (!hugefungusconfiguration.planted) {
                int j = chunkgenerator.getGenDepth();

                if (blockpos1.getY() + i + 1 >= j) {
                    return false;
                }
            }

            boolean flag = !hugefungusconfiguration.planted && randomsource.nextFloat() < 0.06F;

            worldgenlevel.setBlock(blockpos, Blocks.AIR.defaultBlockState(), 260);
            this.placeStem(worldgenlevel, randomsource, hugefungusconfiguration, blockpos1, i, flag);
            this.placeHat(worldgenlevel, randomsource, hugefungusconfiguration, blockpos1, i, flag);
            return true;
        }
    }

    private static boolean isReplaceable(WorldGenLevel level, BlockPos pos, HugeFungusConfiguration config, boolean checkNonReplaceablePlants) {
        return level.isStateAtPosition(pos, BlockBehaviour.BlockStateBase::canBeReplaced) ? true : (checkNonReplaceablePlants ? config.replaceableBlocks.test(level, pos) : false);
    }

    private void placeStem(WorldGenLevel level, RandomSource random, HugeFungusConfiguration config, BlockPos surfaceOrigin, int totalHeight, boolean isHuge) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        BlockState blockstate = config.stemState;
        int j = isHuge ? 1 : 0;

        for (int k = -j; k <= j; ++k) {
            for (int l = -j; l <= j; ++l) {
                boolean flag1 = isHuge && Mth.abs(k) == j && Mth.abs(l) == j;

                for (int i1 = 0; i1 < totalHeight; ++i1) {
                    blockpos_mutableblockpos.setWithOffset(surfaceOrigin, k, i1, l);
                    if (isReplaceable(level, blockpos_mutableblockpos, config, true)) {
                        if (config.planted) {
                            if (!level.getBlockState(blockpos_mutableblockpos.below()).isAir()) {
                                level.destroyBlock(blockpos_mutableblockpos, true);
                            }

                            level.setBlock(blockpos_mutableblockpos, blockstate, 3);
                        } else if (flag1) {
                            if (random.nextFloat() < 0.1F) {
                                this.setBlock(level, blockpos_mutableblockpos, blockstate);
                            }
                        } else {
                            this.setBlock(level, blockpos_mutableblockpos, blockstate);
                        }
                    }
                }
            }
        }

    }

    private void placeHat(WorldGenLevel level, RandomSource random, HugeFungusConfiguration config, BlockPos surfaceOrigin, int totalHeight, boolean isHuge) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        boolean flag1 = config.hatState.is(Blocks.NETHER_WART_BLOCK);
        int j = Math.min(random.nextInt(1 + totalHeight / 3) + 5, totalHeight);
        int k = totalHeight - j;

        for (int l = k; l <= totalHeight; ++l) {
            int i1 = l < totalHeight - random.nextInt(3) ? 2 : 1;

            if (j > 8 && l < k + 4) {
                i1 = 3;
            }

            if (isHuge) {
                ++i1;
            }

            for (int j1 = -i1; j1 <= i1; ++j1) {
                for (int k1 = -i1; k1 <= i1; ++k1) {
                    boolean flag2 = j1 == -i1 || j1 == i1;
                    boolean flag3 = k1 == -i1 || k1 == i1;
                    boolean flag4 = !flag2 && !flag3 && l != totalHeight;
                    boolean flag5 = flag2 && flag3;
                    boolean flag6 = l < k + 3;

                    blockpos_mutableblockpos.setWithOffset(surfaceOrigin, j1, l, k1);
                    if (isReplaceable(level, blockpos_mutableblockpos, config, false)) {
                        if (config.planted && !level.getBlockState(blockpos_mutableblockpos.below()).isAir()) {
                            level.destroyBlock(blockpos_mutableblockpos, true);
                        }

                        if (flag6) {
                            if (!flag4) {
                                this.placeHatDropBlock(level, random, blockpos_mutableblockpos, config.hatState, flag1);
                            }
                        } else if (flag4) {
                            this.placeHatBlock(level, random, config, blockpos_mutableblockpos, 0.1F, 0.2F, flag1 ? 0.1F : 0.0F);
                        } else if (flag5) {
                            this.placeHatBlock(level, random, config, blockpos_mutableblockpos, 0.01F, 0.7F, flag1 ? 0.083F : 0.0F);
                        } else {
                            this.placeHatBlock(level, random, config, blockpos_mutableblockpos, 5.0E-4F, 0.98F, flag1 ? 0.07F : 0.0F);
                        }
                    }
                }
            }
        }

    }

    private void placeHatBlock(LevelAccessor level, RandomSource random, HugeFungusConfiguration config, BlockPos.MutableBlockPos blockPos, float decorBlockProbability, float hatBlockProbability, float vinesProbability) {
        if (random.nextFloat() < decorBlockProbability) {
            this.setBlock(level, blockPos, config.decorState);
        } else if (random.nextFloat() < hatBlockProbability) {
            this.setBlock(level, blockPos, config.hatState);
            if (random.nextFloat() < vinesProbability) {
                tryPlaceWeepingVines(blockPos, level, random);
            }
        }

    }

    private void placeHatDropBlock(LevelAccessor level, RandomSource random, BlockPos blockPos, BlockState hatState, boolean placeVines) {
        if (level.getBlockState(blockPos.below()).is(hatState.getBlock())) {
            this.setBlock(level, blockPos, hatState);
        } else if ((double) random.nextFloat() < 0.15D) {
            this.setBlock(level, blockPos, hatState);
            if (placeVines && random.nextInt(11) == 0) {
                tryPlaceWeepingVines(blockPos, level, random);
            }
        }

    }

    private static void tryPlaceWeepingVines(BlockPos hatBlockPos, LevelAccessor level, RandomSource random) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = hatBlockPos.mutable().move(Direction.DOWN);

        if (level.isEmptyBlock(blockpos_mutableblockpos)) {
            int i = Mth.nextInt(random, 1, 5);

            if (random.nextInt(7) == 0) {
                i *= 2;
            }

            int j = 23;
            int k = 25;

            WeepingVinesFeature.placeWeepingVinesColumn(level, random, blockpos_mutableblockpos, i, 23, 25);
        }
    }
}

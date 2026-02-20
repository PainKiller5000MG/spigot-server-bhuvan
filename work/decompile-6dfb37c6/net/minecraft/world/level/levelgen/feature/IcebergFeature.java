package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;

public class IcebergFeature extends Feature<BlockStateConfiguration> {

    public IcebergFeature(Codec<BlockStateConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<BlockStateConfiguration> context) {
        BlockPos blockpos = context.origin();
        WorldGenLevel worldgenlevel = context.level();

        blockpos = new BlockPos(blockpos.getX(), context.chunkGenerator().getSeaLevel(), blockpos.getZ());
        RandomSource randomsource = context.random();
        boolean flag = randomsource.nextDouble() > 0.7D;
        BlockState blockstate = (context.config()).state;
        double d0 = randomsource.nextDouble() * 2.0D * Math.PI;
        int i = 11 - randomsource.nextInt(5);
        int j = 3 + randomsource.nextInt(3);
        boolean flag1 = randomsource.nextDouble() > 0.7D;
        int k = 11;
        int l = flag1 ? randomsource.nextInt(6) + 6 : randomsource.nextInt(15) + 3;

        if (!flag1 && randomsource.nextDouble() > 0.9D) {
            l += randomsource.nextInt(19) + 7;
        }

        int i1 = Math.min(l + randomsource.nextInt(11), 18);
        int j1 = Math.min(l + randomsource.nextInt(7) - randomsource.nextInt(5), 11);
        int k1 = flag1 ? i : 11;

        for (int l1 = -k1; l1 < k1; ++l1) {
            for (int i2 = -k1; i2 < k1; ++i2) {
                for (int j2 = 0; j2 < l; ++j2) {
                    int k2 = flag1 ? this.heightDependentRadiusEllipse(j2, l, j1) : this.heightDependentRadiusRound(randomsource, j2, l, j1);

                    if (flag1 || l1 < k2) {
                        this.generateIcebergBlock(worldgenlevel, randomsource, blockpos, l, l1, j2, i2, k2, k1, flag1, j, d0, flag, blockstate);
                    }
                }
            }
        }

        this.smooth(worldgenlevel, blockpos, j1, l, flag1, i);

        for (int l2 = -k1; l2 < k1; ++l2) {
            for (int i3 = -k1; i3 < k1; ++i3) {
                for (int j3 = -1; j3 > -i1; --j3) {
                    int k3 = flag1 ? Mth.ceil((float) k1 * (1.0F - (float) Math.pow((double) j3, 2.0D) / ((float) i1 * 8.0F))) : k1;
                    int l3 = this.heightDependentRadiusSteep(randomsource, -j3, i1, j1);

                    if (l2 < l3) {
                        this.generateIcebergBlock(worldgenlevel, randomsource, blockpos, i1, l2, j3, i3, l3, k3, flag1, j, d0, flag, blockstate);
                    }
                }
            }
        }

        boolean flag2 = flag1 ? randomsource.nextDouble() > 0.1D : randomsource.nextDouble() > 0.7D;

        if (flag2) {
            this.generateCutOut(randomsource, worldgenlevel, j1, l, blockpos, flag1, i, d0, j);
        }

        return true;
    }

    private void generateCutOut(RandomSource random, LevelAccessor level, int width, int height, BlockPos globalOrigin, boolean isEllipse, int shapeEllipseA, double shapeAngle, int shapeEllipseC) {
        int i1 = random.nextBoolean() ? -1 : 1;
        int j1 = random.nextBoolean() ? -1 : 1;
        int k1 = random.nextInt(Math.max(width / 2 - 2, 1));

        if (random.nextBoolean()) {
            k1 = width / 2 + 1 - random.nextInt(Math.max(width - width / 2 - 1, 1));
        }

        int l1 = random.nextInt(Math.max(width / 2 - 2, 1));

        if (random.nextBoolean()) {
            l1 = width / 2 + 1 - random.nextInt(Math.max(width - width / 2 - 1, 1));
        }

        if (isEllipse) {
            k1 = l1 = random.nextInt(Math.max(shapeEllipseA - 5, 1));
        }

        BlockPos blockpos1 = new BlockPos(i1 * k1, 0, j1 * l1);
        double d1 = isEllipse ? shapeAngle + (Math.PI / 2D) : random.nextDouble() * 2.0D * Math.PI;

        for (int i2 = 0; i2 < height - 3; ++i2) {
            int j2 = this.heightDependentRadiusRound(random, i2, height, width);

            this.carve(j2, i2, globalOrigin, level, false, d1, blockpos1, shapeEllipseA, shapeEllipseC);
        }

        for (int k2 = -1; k2 > -height + random.nextInt(5); --k2) {
            int l2 = this.heightDependentRadiusSteep(random, -k2, height, width);

            this.carve(l2, k2, globalOrigin, level, true, d1, blockpos1, shapeEllipseA, shapeEllipseC);
        }

    }

    private void carve(int radius, int yOff, BlockPos globalOrigin, LevelAccessor level, boolean underWater, double angle, BlockPos localOrigin, int shapeEllipseA, int shapeEllipseC) {
        int i1 = radius + 1 + shapeEllipseA / 3;
        int j1 = Math.min(radius - 3, 3) + shapeEllipseC / 2 - 1;

        for (int k1 = -i1; k1 < i1; ++k1) {
            for (int l1 = -i1; l1 < i1; ++l1) {
                double d1 = this.signedDistanceEllipse(k1, l1, localOrigin, i1, j1, angle);

                if (d1 < 0.0D) {
                    BlockPos blockpos2 = globalOrigin.offset(k1, yOff, l1);
                    BlockState blockstate = level.getBlockState(blockpos2);

                    if (isIcebergState(blockstate) || blockstate.is(Blocks.SNOW_BLOCK)) {
                        if (underWater) {
                            this.setBlock(level, blockpos2, Blocks.WATER.defaultBlockState());
                        } else {
                            this.setBlock(level, blockpos2, Blocks.AIR.defaultBlockState());
                            this.removeFloatingSnowLayer(level, blockpos2);
                        }
                    }
                }
            }
        }

    }

    private void removeFloatingSnowLayer(LevelAccessor level, BlockPos pos) {
        if (level.getBlockState(pos.above()).is(Blocks.SNOW)) {
            this.setBlock(level, pos.above(), Blocks.AIR.defaultBlockState());
        }

    }

    private void generateIcebergBlock(LevelAccessor level, RandomSource random, BlockPos origin, int height, int xo, int yOff, int zo, int radius, int a, boolean isEllipse, int shapeEllipseC, double shapeAngle, boolean snowOnTop, BlockState mainBlockState) {
        double d1 = isEllipse ? this.signedDistanceEllipse(xo, zo, BlockPos.ZERO, a, this.getEllipseC(yOff, height, shapeEllipseC), shapeAngle) : this.signedDistanceCircle(xo, zo, BlockPos.ZERO, radius, random);

        if (d1 < 0.0D) {
            BlockPos blockpos1 = origin.offset(xo, yOff, zo);
            double d2 = isEllipse ? -0.5D : (double) (-6 - random.nextInt(3));

            if (d1 > d2 && random.nextDouble() > 0.9D) {
                return;
            }

            this.setIcebergBlock(blockpos1, level, random, height - yOff, height, isEllipse, snowOnTop, mainBlockState);
        }

    }

    private void setIcebergBlock(BlockPos pos, LevelAccessor level, RandomSource random, int hDiff, int height, boolean isEllipse, boolean snowOnTop, BlockState mainBlockState) {
        BlockState blockstate1 = level.getBlockState(pos);

        if (blockstate1.isAir() || blockstate1.is(Blocks.SNOW_BLOCK) || blockstate1.is(Blocks.ICE) || blockstate1.is(Blocks.WATER)) {
            boolean flag2 = !isEllipse || random.nextDouble() > 0.05D;
            int k = isEllipse ? 3 : 2;

            if (snowOnTop && !blockstate1.is(Blocks.WATER) && (double) hDiff <= (double) random.nextInt(Math.max(1, height / k)) + (double) height * 0.6D && flag2) {
                this.setBlock(level, pos, Blocks.SNOW_BLOCK.defaultBlockState());
            } else {
                this.setBlock(level, pos, mainBlockState);
            }
        }

    }

    private int getEllipseC(int yOff, int height, int shapeEllipseC) {
        int l = shapeEllipseC;

        if (yOff > 0 && height - yOff <= 3) {
            l = shapeEllipseC - (4 - (height - yOff));
        }

        return l;
    }

    private double signedDistanceCircle(int xo, int zo, BlockPos origin, int radius, RandomSource random) {
        float f = 10.0F * Mth.clamp(random.nextFloat(), 0.2F, 0.8F) / (float) radius;

        return (double) f + Math.pow((double) (xo - origin.getX()), 2.0D) + Math.pow((double) (zo - origin.getZ()), 2.0D) - Math.pow((double) radius, 2.0D);
    }

    private double signedDistanceEllipse(int xo, int zo, BlockPos origin, int a, int c, double angle) {
        return Math.pow(((double) (xo - origin.getX()) * Math.cos(angle) - (double) (zo - origin.getZ()) * Math.sin(angle)) / (double) a, 2.0D) + Math.pow(((double) (xo - origin.getX()) * Math.sin(angle) + (double) (zo - origin.getZ()) * Math.cos(angle)) / (double) c, 2.0D) - 1.0D;
    }

    private int heightDependentRadiusRound(RandomSource random, int yOff, int height, int width) {
        float f = 3.5F - random.nextFloat();
        float f1 = (1.0F - (float) Math.pow((double) yOff, 2.0D) / ((float) height * f)) * (float) width;

        if (height > 15 + random.nextInt(5)) {
            int l = yOff < 3 + random.nextInt(6) ? yOff / 2 : yOff;

            f1 = (1.0F - (float) l / ((float) height * f * 0.4F)) * (float) width;
        }

        return Mth.ceil(f1 / 2.0F);
    }

    private int heightDependentRadiusEllipse(int yOff, int height, int width) {
        float f = 1.0F;
        float f1 = (1.0F - (float) Math.pow((double) yOff, 2.0D) / ((float) height * 1.0F)) * (float) width;

        return Mth.ceil(f1 / 2.0F);
    }

    private int heightDependentRadiusSteep(RandomSource random, int yOff, int height, int width) {
        float f = 1.0F + random.nextFloat() / 2.0F;
        float f1 = (1.0F - (float) yOff / ((float) height * f)) * (float) width;

        return Mth.ceil(f1 / 2.0F);
    }

    private static boolean isIcebergState(BlockState state) {
        return state.is(Blocks.PACKED_ICE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.BLUE_ICE);
    }

    private boolean belowIsAir(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos.below()).isAir();
    }

    private void smooth(LevelAccessor level, BlockPos origin, int width, int height, boolean isEllipse, int shapeEllipseA) {
        int l = isEllipse ? shapeEllipseA : width / 2;

        for (int i1 = -l; i1 <= l; ++i1) {
            for (int j1 = -l; j1 <= l; ++j1) {
                for (int k1 = 0; k1 <= height; ++k1) {
                    BlockPos blockpos1 = origin.offset(i1, k1, j1);
                    BlockState blockstate = level.getBlockState(blockpos1);

                    if (isIcebergState(blockstate) || blockstate.is(Blocks.SNOW)) {
                        if (this.belowIsAir(level, blockpos1)) {
                            this.setBlock(level, blockpos1, Blocks.AIR.defaultBlockState());
                            this.setBlock(level, blockpos1.above(), Blocks.AIR.defaultBlockState());
                        } else if (isIcebergState(blockstate)) {
                            BlockState[] ablockstate = new BlockState[]{level.getBlockState(blockpos1.west()), level.getBlockState(blockpos1.east()), level.getBlockState(blockpos1.north()), level.getBlockState(blockpos1.south())};
                            int l1 = 0;

                            for (BlockState blockstate1 : ablockstate) {
                                if (!isIcebergState(blockstate1)) {
                                    ++l1;
                                }
                            }

                            if (l1 >= 3) {
                                this.setBlock(level, blockpos1, Blocks.AIR.defaultBlockState());
                            }
                        }
                    }
                }
            }
        }

    }
}

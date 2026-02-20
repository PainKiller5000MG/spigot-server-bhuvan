package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.BitSet;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

public class OreFeature extends Feature<OreConfiguration> {

    public OreFeature(Codec<OreConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<OreConfiguration> context) {
        RandomSource randomsource = context.random();
        BlockPos blockpos = context.origin();
        WorldGenLevel worldgenlevel = context.level();
        OreConfiguration oreconfiguration = context.config();
        float f = randomsource.nextFloat() * (float) Math.PI;
        float f1 = (float) oreconfiguration.size / 8.0F;
        int i = Mth.ceil(((float) oreconfiguration.size / 16.0F * 2.0F + 1.0F) / 2.0F);
        double d0 = (double) blockpos.getX() + Math.sin((double) f) * (double) f1;
        double d1 = (double) blockpos.getX() - Math.sin((double) f) * (double) f1;
        double d2 = (double) blockpos.getZ() + Math.cos((double) f) * (double) f1;
        double d3 = (double) blockpos.getZ() - Math.cos((double) f) * (double) f1;
        int j = 2;
        double d4 = (double) (blockpos.getY() + randomsource.nextInt(3) - 2);
        double d5 = (double) (blockpos.getY() + randomsource.nextInt(3) - 2);
        int k = blockpos.getX() - Mth.ceil(f1) - i;
        int l = blockpos.getY() - 2 - i;
        int i1 = blockpos.getZ() - Mth.ceil(f1) - i;
        int j1 = 2 * (Mth.ceil(f1) + i);
        int k1 = 2 * (2 + i);

        for (int l1 = k; l1 <= k + j1; ++l1) {
            for (int i2 = i1; i2 <= i1 + j1; ++i2) {
                if (l <= worldgenlevel.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, l1, i2)) {
                    return this.doPlace(worldgenlevel, randomsource, oreconfiguration, d0, d1, d2, d3, d4, d5, k, l, i1, j1, k1);
                }
            }
        }

        return false;
    }

    protected boolean doPlace(WorldGenLevel level, RandomSource random, OreConfiguration config, double x0, double x1, double z0, double z1, double y0, double y1, int xStart, int yStart, int zStart, int sizeXZ, int sizeY) {
        int j1 = 0;
        BitSet bitset = new BitSet(sizeXZ * sizeY * sizeXZ);
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        int k1 = config.size;
        double[] adouble = new double[k1 * 4];

        for (int l1 = 0; l1 < k1; ++l1) {
            float f = (float) l1 / (float) k1;
            double d6 = Mth.lerp((double) f, x0, x1);
            double d7 = Mth.lerp((double) f, y0, y1);
            double d8 = Mth.lerp((double) f, z0, z1);
            double d9 = random.nextDouble() * (double) k1 / 16.0D;
            double d10 = ((double) (Mth.sin((double) ((float) Math.PI * f)) + 1.0F) * d9 + 1.0D) / 2.0D;

            adouble[l1 * 4 + 0] = d6;
            adouble[l1 * 4 + 1] = d7;
            adouble[l1 * 4 + 2] = d8;
            adouble[l1 * 4 + 3] = d10;
        }

        for (int i2 = 0; i2 < k1 - 1; ++i2) {
            if (adouble[i2 * 4 + 3] > 0.0D) {
                for (int j2 = i2 + 1; j2 < k1; ++j2) {
                    if (adouble[j2 * 4 + 3] > 0.0D) {
                        double d11 = adouble[i2 * 4 + 0] - adouble[j2 * 4 + 0];
                        double d12 = adouble[i2 * 4 + 1] - adouble[j2 * 4 + 1];
                        double d13 = adouble[i2 * 4 + 2] - adouble[j2 * 4 + 2];
                        double d14 = adouble[i2 * 4 + 3] - adouble[j2 * 4 + 3];

                        if (d14 * d14 > d11 * d11 + d12 * d12 + d13 * d13) {
                            if (d14 > 0.0D) {
                                adouble[j2 * 4 + 3] = -1.0D;
                            } else {
                                adouble[i2 * 4 + 3] = -1.0D;
                            }
                        }
                    }
                }
            }
        }

        try (BulkSectionAccess bulksectionaccess = new BulkSectionAccess(level)) {
            for (int k2 = 0; k2 < k1; ++k2) {
                double d15 = adouble[k2 * 4 + 3];

                if (d15 >= 0.0D) {
                    double d16 = adouble[k2 * 4 + 0];
                    double d17 = adouble[k2 * 4 + 1];
                    double d18 = adouble[k2 * 4 + 2];
                    int l2 = Math.max(Mth.floor(d16 - d15), xStart);
                    int i3 = Math.max(Mth.floor(d17 - d15), yStart);
                    int j3 = Math.max(Mth.floor(d18 - d15), zStart);
                    int k3 = Math.max(Mth.floor(d16 + d15), l2);
                    int l3 = Math.max(Mth.floor(d17 + d15), i3);
                    int i4 = Math.max(Mth.floor(d18 + d15), j3);

                    for (int j4 = l2; j4 <= k3; ++j4) {
                        double d19 = ((double) j4 + 0.5D - d16) / d15;

                        if (d19 * d19 < 1.0D) {
                            for (int k4 = i3; k4 <= l3; ++k4) {
                                double d20 = ((double) k4 + 0.5D - d17) / d15;

                                if (d19 * d19 + d20 * d20 < 1.0D) {
                                    for (int l4 = j3; l4 <= i4; ++l4) {
                                        double d21 = ((double) l4 + 0.5D - d18) / d15;

                                        if (d19 * d19 + d20 * d20 + d21 * d21 < 1.0D && !level.isOutsideBuildHeight(k4)) {
                                            int i5 = j4 - xStart + (k4 - yStart) * sizeXZ + (l4 - zStart) * sizeXZ * sizeY;

                                            if (!bitset.get(i5)) {
                                                bitset.set(i5);
                                                blockpos_mutableblockpos.set(j4, k4, l4);
                                                if (level.ensureCanWrite(blockpos_mutableblockpos)) {
                                                    LevelChunkSection levelchunksection = bulksectionaccess.getSection(blockpos_mutableblockpos);

                                                    if (levelchunksection != null) {
                                                        int j5 = SectionPos.sectionRelative(j4);
                                                        int k5 = SectionPos.sectionRelative(k4);
                                                        int l5 = SectionPos.sectionRelative(l4);
                                                        BlockState blockstate = levelchunksection.getBlockState(j5, k5, l5);

                                                        for (OreConfiguration.TargetBlockState oreconfiguration_targetblockstate : config.targetStates) {
                                                            Objects.requireNonNull(bulksectionaccess);
                                                            if (canPlaceOre(blockstate, bulksectionaccess::getBlockState, random, config, oreconfiguration_targetblockstate, blockpos_mutableblockpos)) {
                                                                levelchunksection.setBlockState(j5, k5, l5, oreconfiguration_targetblockstate.state, false);
                                                                ++j1;
                                                                break;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return j1 > 0;
    }

    public static boolean canPlaceOre(BlockState orePosState, Function<BlockPos, BlockState> blockGetter, RandomSource random, OreConfiguration config, OreConfiguration.TargetBlockState targetState, BlockPos.MutableBlockPos orePos) {
        return !targetState.target.test(orePosState, random) ? false : (shouldSkipAirCheck(random, config.discardChanceOnAirExposure) ? true : !isAdjacentToAir(blockGetter, orePos));
    }

    protected static boolean shouldSkipAirCheck(RandomSource random, float discardChanceOnAirExposure) {
        return discardChanceOnAirExposure <= 0.0F ? true : (discardChanceOnAirExposure >= 1.0F ? false : random.nextFloat() >= discardChanceOnAirExposure);
    }
}

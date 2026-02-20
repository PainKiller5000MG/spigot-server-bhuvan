package net.minecraft.world.level.levelgen;

import java.util.Arrays;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.jspecify.annotations.Nullable;

public interface Aquifer {

    static Aquifer create(NoiseChunk noiseChunk, ChunkPos pos, NoiseRouter router, PositionalRandomFactory positionalRandomFactory, int minBlockY, int yBlockSize, Aquifer.FluidPicker fluidRule) {
        return new Aquifer.NoiseBasedAquifer(noiseChunk, pos, router, positionalRandomFactory, minBlockY, yBlockSize, fluidRule);
    }

    static Aquifer createDisabled(final Aquifer.FluidPicker fluidRule) {
        return new Aquifer() {
            @Override
            public @Nullable BlockState computeSubstance(DensityFunction.FunctionContext context, double density) {
                return density > 0.0D ? null : fluidRule.computeFluid(context.blockX(), context.blockY(), context.blockZ()).at(context.blockY());
            }

            @Override
            public boolean shouldScheduleFluidUpdate() {
                return false;
            }
        };
    }

    @Nullable
    BlockState computeSubstance(DensityFunction.FunctionContext context, double density);

    boolean shouldScheduleFluidUpdate();

    public static class NoiseBasedAquifer implements Aquifer {

        private static final int X_RANGE = 10;
        private static final int Y_RANGE = 9;
        private static final int Z_RANGE = 10;
        private static final int X_SEPARATION = 6;
        private static final int Y_SEPARATION = 3;
        private static final int Z_SEPARATION = 6;
        private static final int X_SPACING = 16;
        private static final int Y_SPACING = 12;
        private static final int Z_SPACING = 16;
        private static final int X_SPACING_SHIFT = 4;
        private static final int Z_SPACING_SHIFT = 4;
        private static final int MAX_REASONABLE_DISTANCE_TO_AQUIFER_CENTER = 11;
        private static final double FLOWING_UPDATE_SIMULARITY = similarity(Mth.square(10), Mth.square(12));
        private static final int SAMPLE_OFFSET_X = -5;
        private static final int SAMPLE_OFFSET_Y = 1;
        private static final int SAMPLE_OFFSET_Z = -5;
        private static final int MIN_CELL_SAMPLE_X = 0;
        private static final int MIN_CELL_SAMPLE_Y = -1;
        private static final int MIN_CELL_SAMPLE_Z = 0;
        private static final int MAX_CELL_SAMPLE_X = 1;
        private static final int MAX_CELL_SAMPLE_Y = 1;
        private static final int MAX_CELL_SAMPLE_Z = 1;
        private final NoiseChunk noiseChunk;
        private final DensityFunction barrierNoise;
        private final DensityFunction fluidLevelFloodednessNoise;
        private final DensityFunction fluidLevelSpreadNoise;
        private final DensityFunction lavaNoise;
        private final PositionalRandomFactory positionalRandomFactory;
        private final @Nullable Aquifer.FluidStatus[] aquiferCache;
        private final long[] aquiferLocationCache;
        private final Aquifer.FluidPicker globalFluidPicker;
        private final DensityFunction erosion;
        private final DensityFunction depth;
        private boolean shouldScheduleFluidUpdate;
        private final int skipSamplingAboveY;
        private final int minGridX;
        private final int minGridY;
        private final int minGridZ;
        private final int gridSizeX;
        private final int gridSizeZ;
        private static final int[][] SURFACE_SAMPLING_OFFSETS_IN_CHUNKS = new int[][]{{0, 0}, {-2, -1}, {-1, -1}, {0, -1}, {1, -1}, {-3, 0}, {-2, 0}, {-1, 0}, {1, 0}, {-2, 1}, {-1, 1}, {0, 1}, {1, 1}};

        private NoiseBasedAquifer(NoiseChunk noiseChunk, ChunkPos pos, NoiseRouter router, PositionalRandomFactory positionalRandomFactory, int minBlockY, int yBlockSize, Aquifer.FluidPicker globalFluidPicker) {
            this.noiseChunk = noiseChunk;
            this.barrierNoise = router.barrierNoise();
            this.fluidLevelFloodednessNoise = router.fluidLevelFloodednessNoise();
            this.fluidLevelSpreadNoise = router.fluidLevelSpreadNoise();
            this.lavaNoise = router.lavaNoise();
            this.erosion = router.erosion();
            this.depth = router.depth();
            this.positionalRandomFactory = positionalRandomFactory;
            this.minGridX = gridX(pos.getMinBlockX() + -5) + 0;
            this.globalFluidPicker = globalFluidPicker;
            int k = gridX(pos.getMaxBlockX() + -5) + 1;

            this.gridSizeX = k - this.minGridX + 1;
            this.minGridY = gridY(minBlockY + 1) + -1;
            int l = gridY(minBlockY + yBlockSize + 1) + 1;
            int i1 = l - this.minGridY + 1;

            this.minGridZ = gridZ(pos.getMinBlockZ() + -5) + 0;
            int j1 = gridZ(pos.getMaxBlockZ() + -5) + 1;

            this.gridSizeZ = j1 - this.minGridZ + 1;
            int k1 = this.gridSizeX * i1 * this.gridSizeZ;

            this.aquiferCache = new Aquifer.FluidStatus[k1];
            this.aquiferLocationCache = new long[k1];
            Arrays.fill(this.aquiferLocationCache, Long.MAX_VALUE);
            int l1 = this.adjustSurfaceLevel(noiseChunk.maxPreliminarySurfaceLevel(fromGridX(this.minGridX, 0), fromGridZ(this.minGridZ, 0), fromGridX(k, 9), fromGridZ(j1, 9)));
            int i2 = gridY(l1 + 12) - -1;

            this.skipSamplingAboveY = fromGridY(i2, 11) - 1;
        }

        private int getIndex(int gridX, int gridY, int gridZ) {
            int l = gridX - this.minGridX;
            int i1 = gridY - this.minGridY;
            int j1 = gridZ - this.minGridZ;

            return (i1 * this.gridSizeZ + j1) * this.gridSizeX + l;
        }

        @Override
        public @Nullable BlockState computeSubstance(DensityFunction.FunctionContext context, double density) {
            if (density > 0.0D) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            } else {
                int i = context.blockX();
                int j = context.blockY();
                int k = context.blockZ();
                Aquifer.FluidStatus aquifer_fluidstatus = this.globalFluidPicker.computeFluid(i, j, k);

                if (j > this.skipSamplingAboveY) {
                    this.shouldScheduleFluidUpdate = false;
                    return aquifer_fluidstatus.at(j);
                } else if (aquifer_fluidstatus.at(j).is(Blocks.LAVA)) {
                    this.shouldScheduleFluidUpdate = false;
                    return SharedConstants.DEBUG_DISABLE_FLUID_GENERATION ? Blocks.AIR.defaultBlockState() : Blocks.LAVA.defaultBlockState();
                } else {
                    int l = gridX(i + -5);
                    int i1 = gridY(j + 1);
                    int j1 = gridZ(k + -5);
                    int k1 = Integer.MAX_VALUE;
                    int l1 = Integer.MAX_VALUE;
                    int i2 = Integer.MAX_VALUE;
                    int j2 = Integer.MAX_VALUE;
                    int k2 = 0;
                    int l2 = 0;
                    int i3 = 0;
                    int j3 = 0;

                    for (int k3 = 0; k3 <= 1; ++k3) {
                        for (int l3 = -1; l3 <= 1; ++l3) {
                            for (int i4 = 0; i4 <= 1; ++i4) {
                                int j4 = l + k3;
                                int k4 = i1 + l3;
                                int l4 = j1 + i4;
                                int i5 = this.getIndex(j4, k4, l4);
                                long j5 = this.aquiferLocationCache[i5];
                                long k5;

                                if (j5 != Long.MAX_VALUE) {
                                    k5 = j5;
                                } else {
                                    RandomSource randomsource = this.positionalRandomFactory.at(j4, k4, l4);

                                    k5 = BlockPos.asLong(fromGridX(j4, randomsource.nextInt(10)), fromGridY(k4, randomsource.nextInt(9)), fromGridZ(l4, randomsource.nextInt(10)));
                                    this.aquiferLocationCache[i5] = k5;
                                }

                                int l5 = BlockPos.getX(k5) - i;
                                int i6 = BlockPos.getY(k5) - j;
                                int j6 = BlockPos.getZ(k5) - k;
                                int k6 = l5 * l5 + i6 * i6 + j6 * j6;

                                if (k1 >= k6) {
                                    j3 = i3;
                                    i3 = l2;
                                    l2 = k2;
                                    k2 = i5;
                                    j2 = i2;
                                    i2 = l1;
                                    l1 = k1;
                                    k1 = k6;
                                } else if (l1 >= k6) {
                                    j3 = i3;
                                    i3 = l2;
                                    l2 = i5;
                                    j2 = i2;
                                    i2 = l1;
                                    l1 = k6;
                                } else if (i2 >= k6) {
                                    j3 = i3;
                                    i3 = i5;
                                    j2 = i2;
                                    i2 = k6;
                                } else if (j2 >= k6) {
                                    j3 = i5;
                                    j2 = k6;
                                }
                            }
                        }
                    }

                    Aquifer.FluidStatus aquifer_fluidstatus1 = this.getAquiferStatus(k2);
                    double d1 = similarity(k1, l1);
                    BlockState blockstate = aquifer_fluidstatus1.at(j);
                    BlockState blockstate1 = SharedConstants.DEBUG_DISABLE_FLUID_GENERATION ? Blocks.AIR.defaultBlockState() : blockstate;

                    if (d1 <= 0.0D) {
                        if (d1 >= Aquifer.NoiseBasedAquifer.FLOWING_UPDATE_SIMULARITY) {
                            Aquifer.FluidStatus aquifer_fluidstatus2 = this.getAquiferStatus(l2);

                            this.shouldScheduleFluidUpdate = !aquifer_fluidstatus1.equals(aquifer_fluidstatus2);
                        } else {
                            this.shouldScheduleFluidUpdate = false;
                        }

                        return blockstate1;
                    } else if (blockstate.is(Blocks.WATER) && this.globalFluidPicker.computeFluid(i, j - 1, k).at(j - 1).is(Blocks.LAVA)) {
                        this.shouldScheduleFluidUpdate = true;
                        return blockstate1;
                    } else {
                        MutableDouble mutabledouble = new MutableDouble(Double.NaN);
                        Aquifer.FluidStatus aquifer_fluidstatus3 = this.getAquiferStatus(l2);
                        double d2 = d1 * this.calculatePressure(context, mutabledouble, aquifer_fluidstatus1, aquifer_fluidstatus3);

                        if (density + d2 > 0.0D) {
                            this.shouldScheduleFluidUpdate = false;
                            return null;
                        } else {
                            Aquifer.FluidStatus aquifer_fluidstatus4 = this.getAquiferStatus(i3);
                            double d3 = similarity(k1, i2);

                            if (d3 > 0.0D) {
                                double d4 = d1 * d3 * this.calculatePressure(context, mutabledouble, aquifer_fluidstatus1, aquifer_fluidstatus4);

                                if (density + d4 > 0.0D) {
                                    this.shouldScheduleFluidUpdate = false;
                                    return null;
                                }
                            }

                            double d5 = similarity(l1, i2);

                            if (d5 > 0.0D) {
                                double d6 = d1 * d5 * this.calculatePressure(context, mutabledouble, aquifer_fluidstatus3, aquifer_fluidstatus4);

                                if (density + d6 > 0.0D) {
                                    this.shouldScheduleFluidUpdate = false;
                                    return null;
                                }
                            }

                            boolean flag = !aquifer_fluidstatus1.equals(aquifer_fluidstatus3);
                            boolean flag1 = d5 >= Aquifer.NoiseBasedAquifer.FLOWING_UPDATE_SIMULARITY && !aquifer_fluidstatus3.equals(aquifer_fluidstatus4);
                            boolean flag2 = d3 >= Aquifer.NoiseBasedAquifer.FLOWING_UPDATE_SIMULARITY && !aquifer_fluidstatus1.equals(aquifer_fluidstatus4);

                            if (!flag && !flag1 && !flag2) {
                                this.shouldScheduleFluidUpdate = d3 >= Aquifer.NoiseBasedAquifer.FLOWING_UPDATE_SIMULARITY && similarity(k1, j2) >= Aquifer.NoiseBasedAquifer.FLOWING_UPDATE_SIMULARITY && !aquifer_fluidstatus1.equals(this.getAquiferStatus(j3));
                            } else {
                                this.shouldScheduleFluidUpdate = true;
                            }

                            return blockstate1;
                        }
                    }
                }
            }
        }

        @Override
        public boolean shouldScheduleFluidUpdate() {
            return this.shouldScheduleFluidUpdate;
        }

        private static double similarity(int distanceSqr1, int distanceSqr2) {
            double d0 = 25.0D;

            return 1.0D - (double) (distanceSqr2 - distanceSqr1) / 25.0D;
        }

        private double calculatePressure(DensityFunction.FunctionContext context, MutableDouble barrierNoiseValue, Aquifer.FluidStatus statusClosest1, Aquifer.FluidStatus statusClosest2) {
            int i = context.blockY();
            BlockState blockstate = statusClosest1.at(i);
            BlockState blockstate1 = statusClosest2.at(i);

            if ((!blockstate.is(Blocks.LAVA) || !blockstate1.is(Blocks.WATER)) && (!blockstate.is(Blocks.WATER) || !blockstate1.is(Blocks.LAVA))) {
                int j = Math.abs(statusClosest1.fluidLevel - statusClosest2.fluidLevel);

                if (j == 0) {
                    return 0.0D;
                } else {
                    double d0 = 0.5D * (double) (statusClosest1.fluidLevel + statusClosest2.fluidLevel);
                    double d1 = (double) i + 0.5D - d0;
                    double d2 = (double) j / 2.0D;
                    double d3 = 0.0D;
                    double d4 = 2.5D;
                    double d5 = 1.5D;
                    double d6 = 3.0D;
                    double d7 = 10.0D;
                    double d8 = 3.0D;
                    double d9 = d2 - Math.abs(d1);
                    double d10;

                    if (d1 > 0.0D) {
                        double d11 = 0.0D + d9;

                        if (d11 > 0.0D) {
                            d10 = d11 / 1.5D;
                        } else {
                            d10 = d11 / 2.5D;
                        }
                    } else {
                        double d12 = 3.0D + d9;

                        if (d12 > 0.0D) {
                            d10 = d12 / 3.0D;
                        } else {
                            d10 = d12 / 10.0D;
                        }
                    }

                    double d13 = 2.0D;
                    double d14;

                    if (d10 >= -2.0D && d10 <= 2.0D) {
                        double d15 = barrierNoiseValue.doubleValue();

                        if (Double.isNaN(d15)) {
                            double d16 = this.barrierNoise.compute(context);

                            barrierNoiseValue.setValue(d16);
                            d14 = d16;
                        } else {
                            d14 = d15;
                        }
                    } else {
                        d14 = 0.0D;
                    }

                    return 2.0D * (d14 + d10);
                }
            } else {
                return 2.0D;
            }
        }

        private static int gridX(int blockCoord) {
            return blockCoord >> 4;
        }

        private static int fromGridX(int gridCoord, int blockOffset) {
            return (gridCoord << 4) + blockOffset;
        }

        private static int gridY(int blockCoord) {
            return Math.floorDiv(blockCoord, 12);
        }

        private static int fromGridY(int gridCoord, int blockOffset) {
            return gridCoord * 12 + blockOffset;
        }

        private static int gridZ(int blockCoord) {
            return blockCoord >> 4;
        }

        private static int fromGridZ(int gridCoord, int blockOffset) {
            return (gridCoord << 4) + blockOffset;
        }

        private Aquifer.FluidStatus getAquiferStatus(int index) {
            Aquifer.FluidStatus aquifer_fluidstatus = this.aquiferCache[index];

            if (aquifer_fluidstatus != null) {
                return aquifer_fluidstatus;
            } else {
                long j = this.aquiferLocationCache[index];
                Aquifer.FluidStatus aquifer_fluidstatus1 = this.computeFluid(BlockPos.getX(j), BlockPos.getY(j), BlockPos.getZ(j));

                this.aquiferCache[index] = aquifer_fluidstatus1;
                return aquifer_fluidstatus1;
            }
        }

        private Aquifer.FluidStatus computeFluid(int x, int y, int z) {
            Aquifer.FluidStatus aquifer_fluidstatus = this.globalFluidPicker.computeFluid(x, y, z);
            int l = Integer.MAX_VALUE;
            int i1 = y + 12;
            int j1 = y - 12;
            boolean flag = false;

            for (int[] aint : Aquifer.NoiseBasedAquifer.SURFACE_SAMPLING_OFFSETS_IN_CHUNKS) {
                int k1 = x + SectionPos.sectionToBlockCoord(aint[0]);
                int l1 = z + SectionPos.sectionToBlockCoord(aint[1]);
                int i2 = this.noiseChunk.preliminarySurfaceLevel(k1, l1);
                int j2 = this.adjustSurfaceLevel(i2);
                boolean flag1 = aint[0] == 0 && aint[1] == 0;

                if (flag1 && j1 > j2) {
                    return aquifer_fluidstatus;
                }

                boolean flag2 = i1 > j2;

                if (flag2 || flag1) {
                    Aquifer.FluidStatus aquifer_fluidstatus1 = this.globalFluidPicker.computeFluid(k1, j2, l1);

                    if (!aquifer_fluidstatus1.at(j2).isAir()) {
                        if (flag1) {
                            flag = true;
                        }

                        if (flag2) {
                            return aquifer_fluidstatus1;
                        }
                    }
                }

                l = Math.min(l, i2);
            }

            int k2 = this.computeSurfaceLevel(x, y, z, aquifer_fluidstatus, l, flag);

            return new Aquifer.FluidStatus(k2, this.computeFluidType(x, y, z, aquifer_fluidstatus, k2));
        }

        private int adjustSurfaceLevel(int preliminarySurfaceLevel) {
            return preliminarySurfaceLevel + 8;
        }

        private int computeSurfaceLevel(int x, int y, int z, Aquifer.FluidStatus globalFluid, int lowestPreliminarySurface, boolean surfaceAtCenterIsUnderGlobalFluidLevel) {
            DensityFunction.SinglePointContext densityfunction_singlepointcontext = new DensityFunction.SinglePointContext(x, y, z);
            double d0;
            double d1;

            if (OverworldBiomeBuilder.isDeepDarkRegion(this.erosion, this.depth, densityfunction_singlepointcontext)) {
                d0 = -1.0D;
                d1 = -1.0D;
            } else {
                int i1 = lowestPreliminarySurface + 8 - y;
                int j1 = 64;
                double d2 = surfaceAtCenterIsUnderGlobalFluidLevel ? Mth.clampedMap((double) i1, 0.0D, 64.0D, 1.0D, 0.0D) : 0.0D;
                double d3 = Mth.clamp(this.fluidLevelFloodednessNoise.compute(densityfunction_singlepointcontext), -1.0D, 1.0D);
                double d4 = Mth.map(d2, 1.0D, 0.0D, -0.3D, 0.8D);
                double d5 = Mth.map(d2, 1.0D, 0.0D, -0.8D, 0.4D);

                d0 = d3 - d5;
                d1 = d3 - d4;
            }

            int k1;

            if (d1 > 0.0D) {
                k1 = globalFluid.fluidLevel;
            } else if (d0 > 0.0D) {
                k1 = this.computeRandomizedFluidSurfaceLevel(x, y, z, lowestPreliminarySurface);
            } else {
                k1 = DimensionType.WAY_BELOW_MIN_Y;
            }

            return k1;
        }

        private int computeRandomizedFluidSurfaceLevel(int x, int y, int z, int lowestPreliminarySurface) {
            int i1 = 16;
            int j1 = 40;
            int k1 = Math.floorDiv(x, 16);
            int l1 = Math.floorDiv(y, 40);
            int i2 = Math.floorDiv(z, 16);
            int j2 = l1 * 40 + 20;
            int k2 = 10;
            double d0 = this.fluidLevelSpreadNoise.compute(new DensityFunction.SinglePointContext(k1, l1, i2)) * 10.0D;
            int l2 = Mth.quantize(d0, 3);
            int i3 = j2 + l2;

            return Math.min(lowestPreliminarySurface, i3);
        }

        private BlockState computeFluidType(int x, int y, int z, Aquifer.FluidStatus globalFluid, int fluidSurfaceLevel) {
            BlockState blockstate = globalFluid.fluidType;

            if (fluidSurfaceLevel <= -10 && fluidSurfaceLevel != DimensionType.WAY_BELOW_MIN_Y && globalFluid.fluidType != Blocks.LAVA.defaultBlockState()) {
                int i1 = 64;
                int j1 = 40;
                int k1 = Math.floorDiv(x, 64);
                int l1 = Math.floorDiv(y, 40);
                int i2 = Math.floorDiv(z, 64);
                double d0 = this.lavaNoise.compute(new DensityFunction.SinglePointContext(k1, l1, i2));

                if (Math.abs(d0) > 0.3D) {
                    blockstate = Blocks.LAVA.defaultBlockState();
                }
            }

            return blockstate;
        }
    }

    public static record FluidStatus(int fluidLevel, BlockState fluidType) {

        public BlockState at(int blockY) {
            return blockY < this.fluidLevel ? this.fluidType : Blocks.AIR.defaultBlockState();
        }
    }

    public interface FluidPicker {

        Aquifer.FluidStatus computeFluid(int blockX, int blockY, int blockZ);
    }
}

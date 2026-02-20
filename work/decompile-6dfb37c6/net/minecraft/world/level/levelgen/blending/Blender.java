package net.minecraft.world.level.levelgen.blending;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction8;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.data.worldgen.NoiseData;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.material.FluidState;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

public class Blender {

    private static final Blender EMPTY = new Blender(new Long2ObjectOpenHashMap(), new Long2ObjectOpenHashMap()) {
        @Override
        public Blender.BlendingOutput blendOffsetAndFactor(int blockX, int blockZ) {
            return new Blender.BlendingOutput(1.0D, 0.0D);
        }

        @Override
        public double blendDensity(DensityFunction.FunctionContext context, double noiseValue) {
            return noiseValue;
        }

        @Override
        public BiomeResolver getBiomeResolver(BiomeResolver biomeResolver) {
            return biomeResolver;
        }
    };
    private static final NormalNoise SHIFT_NOISE = NormalNoise.create(new XoroshiroRandomSource(42L), NoiseData.DEFAULT_SHIFT);
    private static final int HEIGHT_BLENDING_RANGE_CELLS = QuartPos.fromSection(7) - 1;
    private static final int HEIGHT_BLENDING_RANGE_CHUNKS = QuartPos.toSection(Blender.HEIGHT_BLENDING_RANGE_CELLS + 3);
    private static final int DENSITY_BLENDING_RANGE_CELLS = 2;
    private static final int DENSITY_BLENDING_RANGE_CHUNKS = QuartPos.toSection(5);
    private static final double OLD_CHUNK_XZ_RADIUS = 8.0D;
    private final Long2ObjectOpenHashMap<BlendingData> heightAndBiomeBlendingData;
    private final Long2ObjectOpenHashMap<BlendingData> densityBlendingData;

    public static Blender empty() {
        return Blender.EMPTY;
    }

    public static Blender of(@Nullable WorldGenRegion region) {
        if (!SharedConstants.DEBUG_DISABLE_BLENDING && region != null) {
            ChunkPos chunkpos = region.getCenter();

            if (!region.isOldChunkAround(chunkpos, Blender.HEIGHT_BLENDING_RANGE_CHUNKS)) {
                return Blender.EMPTY;
            } else {
                Long2ObjectOpenHashMap<BlendingData> long2objectopenhashmap = new Long2ObjectOpenHashMap();
                Long2ObjectOpenHashMap<BlendingData> long2objectopenhashmap1 = new Long2ObjectOpenHashMap();
                int i = Mth.square(Blender.HEIGHT_BLENDING_RANGE_CHUNKS + 1);

                for (int j = -Blender.HEIGHT_BLENDING_RANGE_CHUNKS; j <= Blender.HEIGHT_BLENDING_RANGE_CHUNKS; ++j) {
                    for (int k = -Blender.HEIGHT_BLENDING_RANGE_CHUNKS; k <= Blender.HEIGHT_BLENDING_RANGE_CHUNKS; ++k) {
                        if (j * j + k * k <= i) {
                            int l = chunkpos.x + j;
                            int i1 = chunkpos.z + k;
                            BlendingData blendingdata = BlendingData.getOrUpdateBlendingData(region, l, i1);

                            if (blendingdata != null) {
                                long2objectopenhashmap.put(ChunkPos.asLong(l, i1), blendingdata);
                                if (j >= -Blender.DENSITY_BLENDING_RANGE_CHUNKS && j <= Blender.DENSITY_BLENDING_RANGE_CHUNKS && k >= -Blender.DENSITY_BLENDING_RANGE_CHUNKS && k <= Blender.DENSITY_BLENDING_RANGE_CHUNKS) {
                                    long2objectopenhashmap1.put(ChunkPos.asLong(l, i1), blendingdata);
                                }
                            }
                        }
                    }
                }

                if (long2objectopenhashmap.isEmpty() && long2objectopenhashmap1.isEmpty()) {
                    return Blender.EMPTY;
                } else {
                    return new Blender(long2objectopenhashmap, long2objectopenhashmap1);
                }
            }
        } else {
            return Blender.EMPTY;
        }
    }

    private Blender(Long2ObjectOpenHashMap<BlendingData> heightAndBiomeBlendingData, Long2ObjectOpenHashMap<BlendingData> densityBlendingData) {
        this.heightAndBiomeBlendingData = heightAndBiomeBlendingData;
        this.densityBlendingData = densityBlendingData;
    }

    public boolean isEmpty() {
        return this.heightAndBiomeBlendingData.isEmpty() && this.densityBlendingData.isEmpty();
    }

    public Blender.BlendingOutput blendOffsetAndFactor(int blockX, int blockZ) {
        int k = QuartPos.fromBlock(blockX);
        int l = QuartPos.fromBlock(blockZ);
        double d0 = this.getBlendingDataValue(k, 0, l, BlendingData::getHeight);

        if (d0 != Double.MAX_VALUE) {
            return new Blender.BlendingOutput(0.0D, heightToOffset(d0));
        } else {
            MutableDouble mutabledouble = new MutableDouble(0.0D);
            MutableDouble mutabledouble1 = new MutableDouble(0.0D);
            MutableDouble mutabledouble2 = new MutableDouble(Double.POSITIVE_INFINITY);

            this.heightAndBiomeBlendingData.forEach((i1, blendingdata) -> {
                blendingdata.iterateHeights(QuartPos.fromSection(ChunkPos.getX(i1)), QuartPos.fromSection(ChunkPos.getZ(i1)), (j1, k1, d1) -> {
                    double d2 = (double) Mth.length((float) (k - j1), (float) (l - k1));

                    if (d2 <= (double) Blender.HEIGHT_BLENDING_RANGE_CELLS) {
                        if (d2 < mutabledouble2.doubleValue()) {
                            mutabledouble2.setValue(d2);
                        }

                        double d3 = 1.0D / (d2 * d2 * d2 * d2);

                        mutabledouble1.add(d1 * d3);
                        mutabledouble.add(d3);
                    }
                });
            });
            if (mutabledouble2.doubleValue() == Double.POSITIVE_INFINITY) {
                return new Blender.BlendingOutput(1.0D, 0.0D);
            } else {
                double d1 = mutabledouble1.doubleValue() / mutabledouble.doubleValue();
                double d2 = Mth.clamp(mutabledouble2.doubleValue() / (double) (Blender.HEIGHT_BLENDING_RANGE_CELLS + 1), 0.0D, 1.0D);

                d2 = 3.0D * d2 * d2 - 2.0D * d2 * d2 * d2;
                return new Blender.BlendingOutput(d2, heightToOffset(d1));
            }
        }
    }

    private static double heightToOffset(double height) {
        double d1 = 1.0D;
        double d2 = height + 0.5D;
        double d3 = Mth.positiveModulo(d2, 8.0D);

        return 1.0D * (32.0D * (d2 - 128.0D) - 3.0D * (d2 - 120.0D) * d3 + 3.0D * d3 * d3) / (128.0D * (32.0D - 3.0D * d3));
    }

    public double blendDensity(DensityFunction.FunctionContext context, double noiseValue) {
        int i = QuartPos.fromBlock(context.blockX());
        int j = context.blockY() / 8;
        int k = QuartPos.fromBlock(context.blockZ());
        double d1 = this.getBlendingDataValue(i, j, k, BlendingData::getDensity);

        if (d1 != Double.MAX_VALUE) {
            return d1;
        } else {
            MutableDouble mutabledouble = new MutableDouble(0.0D);
            MutableDouble mutabledouble1 = new MutableDouble(0.0D);
            MutableDouble mutabledouble2 = new MutableDouble(Double.POSITIVE_INFINITY);

            this.densityBlendingData.forEach((l, blendingdata) -> {
                blendingdata.iterateDensities(QuartPos.fromSection(ChunkPos.getX(l)), QuartPos.fromSection(ChunkPos.getZ(l)), j - 1, j + 1, (i1, j1, k1, d2) -> {
                    double d3 = Mth.length((double) (i - i1), (double) ((j - j1) * 2), (double) (k - k1));

                    if (d3 <= 2.0D) {
                        if (d3 < mutabledouble2.doubleValue()) {
                            mutabledouble2.setValue(d3);
                        }

                        double d4 = 1.0D / (d3 * d3 * d3 * d3);

                        mutabledouble1.add(d2 * d4);
                        mutabledouble.add(d4);
                    }
                });
            });
            if (mutabledouble2.doubleValue() == Double.POSITIVE_INFINITY) {
                return noiseValue;
            } else {
                double d2 = mutabledouble1.doubleValue() / mutabledouble.doubleValue();
                double d3 = Mth.clamp(mutabledouble2.doubleValue() / 3.0D, 0.0D, 1.0D);

                return Mth.lerp(d3, d2, noiseValue);
            }
        }
    }

    private double getBlendingDataValue(int cellX, int cellY, int cellZ, Blender.CellValueGetter cellValueGetter) {
        int l = QuartPos.toSection(cellX);
        int i1 = QuartPos.toSection(cellZ);
        boolean flag = (cellX & 3) == 0;
        boolean flag1 = (cellZ & 3) == 0;
        double d0 = this.getBlendingDataValue(cellValueGetter, l, i1, cellX, cellY, cellZ);

        if (d0 == Double.MAX_VALUE) {
            if (flag && flag1) {
                d0 = this.getBlendingDataValue(cellValueGetter, l - 1, i1 - 1, cellX, cellY, cellZ);
            }

            if (d0 == Double.MAX_VALUE) {
                if (flag) {
                    d0 = this.getBlendingDataValue(cellValueGetter, l - 1, i1, cellX, cellY, cellZ);
                }

                if (d0 == Double.MAX_VALUE && flag1) {
                    d0 = this.getBlendingDataValue(cellValueGetter, l, i1 - 1, cellX, cellY, cellZ);
                }
            }
        }

        return d0;
    }

    private double getBlendingDataValue(Blender.CellValueGetter cellValueGetter, int chunkX, int chunkZ, int cellX, int cellY, int cellZ) {
        BlendingData blendingdata = (BlendingData) this.heightAndBiomeBlendingData.get(ChunkPos.asLong(chunkX, chunkZ));

        return blendingdata != null ? cellValueGetter.get(blendingdata, cellX - QuartPos.fromSection(chunkX), cellY, cellZ - QuartPos.fromSection(chunkZ)) : Double.MAX_VALUE;
    }

    public BiomeResolver getBiomeResolver(BiomeResolver biomeResolver) {
        return (i, j, k, climate_sampler) -> {
            Holder<Biome> holder = this.blendBiome(i, j, k);

            return holder == null ? biomeResolver.getNoiseBiome(i, j, k, climate_sampler) : holder;
        };
    }

    private Holder<Biome> blendBiome(int quartX, int quartY, int quartZ) {
        MutableDouble mutabledouble = new MutableDouble(Double.POSITIVE_INFINITY);
        MutableObject<Holder<Biome>> mutableobject = new MutableObject();

        this.heightAndBiomeBlendingData.forEach((l, blendingdata) -> {
            blendingdata.iterateBiomes(QuartPos.fromSection(ChunkPos.getX(l)), quartY, QuartPos.fromSection(ChunkPos.getZ(l)), (i1, j1, holder) -> {
                double d0 = (double) Mth.length((float) (quartX - i1), (float) (quartZ - j1));

                if (d0 <= (double) Blender.HEIGHT_BLENDING_RANGE_CELLS) {
                    if (d0 < mutabledouble.doubleValue()) {
                        mutableobject.setValue(holder);
                        mutabledouble.setValue(d0);
                    }

                }
            });
        });
        if (mutabledouble.doubleValue() == Double.POSITIVE_INFINITY) {
            return null;
        } else {
            double d0 = Blender.SHIFT_NOISE.getValue((double) quartX, 0.0D, (double) quartZ) * 12.0D;
            double d1 = Mth.clamp((mutabledouble.doubleValue() + d0) / (double) (Blender.HEIGHT_BLENDING_RANGE_CELLS + 1), 0.0D, 1.0D);

            return d1 > 0.5D ? null : (Holder) mutableobject.get();
        }
    }

    public static void generateBorderTicks(WorldGenRegion region, ChunkAccess chunk) {
        if (!SharedConstants.DEBUG_DISABLE_BLENDING) {
            ChunkPos chunkpos = chunk.getPos();
            boolean flag = chunk.isOldNoiseGeneration();
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
            BlockPos blockpos = new BlockPos(chunkpos.getMinBlockX(), 0, chunkpos.getMinBlockZ());
            BlendingData blendingdata = chunk.getBlendingData();

            if (blendingdata != null) {
                int i = blendingdata.getAreaWithOldGeneration().getMinY();
                int j = blendingdata.getAreaWithOldGeneration().getMaxY();

                if (flag) {
                    for (int k = 0; k < 16; ++k) {
                        for (int l = 0; l < 16; ++l) {
                            generateBorderTick(chunk, blockpos_mutableblockpos.setWithOffset(blockpos, k, i - 1, l));
                            generateBorderTick(chunk, blockpos_mutableblockpos.setWithOffset(blockpos, k, i, l));
                            generateBorderTick(chunk, blockpos_mutableblockpos.setWithOffset(blockpos, k, j, l));
                            generateBorderTick(chunk, blockpos_mutableblockpos.setWithOffset(blockpos, k, j + 1, l));
                        }
                    }
                }

                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    if (region.getChunk(chunkpos.x + direction.getStepX(), chunkpos.z + direction.getStepZ()).isOldNoiseGeneration() != flag) {
                        int i1 = direction == Direction.EAST ? 15 : 0;
                        int j1 = direction == Direction.WEST ? 0 : 15;
                        int k1 = direction == Direction.SOUTH ? 15 : 0;
                        int l1 = direction == Direction.NORTH ? 0 : 15;

                        for (int i2 = i1; i2 <= j1; ++i2) {
                            for (int j2 = k1; j2 <= l1; ++j2) {
                                int k2 = Math.min(j, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, i2, j2)) + 1;

                                for (int l2 = i; l2 < k2; ++l2) {
                                    generateBorderTick(chunk, blockpos_mutableblockpos.setWithOffset(blockpos, i2, l2, j2));
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    private static void generateBorderTick(ChunkAccess chunk, BlockPos pos) {
        BlockState blockstate = chunk.getBlockState(pos);

        if (blockstate.is(BlockTags.LEAVES)) {
            chunk.markPosForPostprocessing(pos);
        }

        FluidState fluidstate = chunk.getFluidState(pos);

        if (!fluidstate.isEmpty()) {
            chunk.markPosForPostprocessing(pos);
        }

    }

    public static void addAroundOldChunksCarvingMaskFilter(WorldGenLevel region, ProtoChunk chunk) {
        if (!SharedConstants.DEBUG_DISABLE_BLENDING) {
            ChunkPos chunkpos = chunk.getPos();
            ImmutableMap.Builder<Direction8, BlendingData> immutablemap_builder = ImmutableMap.builder();

            for (Direction8 direction8 : Direction8.values()) {
                int i = chunkpos.x + direction8.getStepX();
                int j = chunkpos.z + direction8.getStepZ();
                BlendingData blendingdata = region.getChunk(i, j).getBlendingData();

                if (blendingdata != null) {
                    immutablemap_builder.put(direction8, blendingdata);
                }
            }

            ImmutableMap<Direction8, BlendingData> immutablemap = immutablemap_builder.build();

            if (chunk.isOldNoiseGeneration() || !immutablemap.isEmpty()) {
                Blender.DistanceGetter blender_distancegetter = makeOldChunkDistanceGetter(chunk.getBlendingData(), immutablemap);
                CarvingMask.Mask carvingmask_mask = (k, l, i1) -> {
                    double d0 = (double) k + 0.5D + Blender.SHIFT_NOISE.getValue((double) k, (double) l, (double) i1) * 4.0D;
                    double d1 = (double) l + 0.5D + Blender.SHIFT_NOISE.getValue((double) l, (double) i1, (double) k) * 4.0D;
                    double d2 = (double) i1 + 0.5D + Blender.SHIFT_NOISE.getValue((double) i1, (double) k, (double) l) * 4.0D;

                    return blender_distancegetter.getDistance(d0, d1, d2) < 4.0D;
                };

                chunk.getOrCreateCarvingMask().setAdditionalMask(carvingmask_mask);
            }
        }
    }

    public static Blender.DistanceGetter makeOldChunkDistanceGetter(@Nullable BlendingData centerBlendingData, Map<Direction8, BlendingData> oldSidesBlendingData) {
        List<Blender.DistanceGetter> list = Lists.newArrayList();

        if (centerBlendingData != null) {
            list.add(makeOffsetOldChunkDistanceGetter((Direction8) null, centerBlendingData));
        }

        oldSidesBlendingData.forEach((direction8, blendingdata1) -> {
            list.add(makeOffsetOldChunkDistanceGetter(direction8, blendingdata1));
        });
        return (d0, d1, d2) -> {
            double d3 = Double.POSITIVE_INFINITY;

            for (Blender.DistanceGetter blender_distancegetter : list) {
                double d4 = blender_distancegetter.getDistance(d0, d1, d2);

                if (d4 < d3) {
                    d3 = d4;
                }
            }

            return d3;
        };
    }

    private static Blender.DistanceGetter makeOffsetOldChunkDistanceGetter(@Nullable Direction8 offset, BlendingData blendingData) {
        double d0 = 0.0D;
        double d1 = 0.0D;

        if (offset != null) {
            for (Direction direction : offset.getDirections()) {
                d0 += (double) (direction.getStepX() * 16);
                d1 += (double) (direction.getStepZ() * 16);
            }
        }

        double d2 = (double) blendingData.getAreaWithOldGeneration().getHeight() / 2.0D;
        double d3 = (double) blendingData.getAreaWithOldGeneration().getMinY() + d2;

        return (d4, d5, d6) -> {
            return distanceToCube(d4 - 8.0D - d0, d5 - d3, d6 - 8.0D - d1, 8.0D, d2, 8.0D);
        };
    }

    private static double distanceToCube(double x, double y, double z, double radiusX, double radiusY, double radiusZ) {
        double d6 = Math.abs(x) - radiusX;
        double d7 = Math.abs(y) - radiusY;
        double d8 = Math.abs(z) - radiusZ;

        return Mth.length(Math.max(0.0D, d6), Math.max(0.0D, d7), Math.max(0.0D, d8));
    }

    public static record BlendingOutput(double alpha, double blendingOffset) {

    }

    private interface CellValueGetter {

        double get(BlendingData data, int cellX, int cellY, int cellZ);
    }

    public interface DistanceGetter {

        double getDistance(double x, double y, double z);
    }
}

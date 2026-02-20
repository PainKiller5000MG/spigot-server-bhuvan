package net.minecraft.world.level.levelgen.blending;

import com.google.common.primitives.Doubles;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleArrays;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction8;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

public class BlendingData {

    private static final double BLENDING_DENSITY_FACTOR = 0.1D;
    protected static final int CELL_WIDTH = 4;
    protected static final int CELL_HEIGHT = 8;
    protected static final int CELL_RATIO = 2;
    private static final double SOLID_DENSITY = 1.0D;
    private static final double AIR_DENSITY = -1.0D;
    private static final int CELLS_PER_SECTION_Y = 2;
    private static final int QUARTS_PER_SECTION = QuartPos.fromBlock(16);
    private static final int CELL_HORIZONTAL_MAX_INDEX_INSIDE = BlendingData.QUARTS_PER_SECTION - 1;
    private static final int CELL_HORIZONTAL_MAX_INDEX_OUTSIDE = BlendingData.QUARTS_PER_SECTION;
    private static final int CELL_COLUMN_INSIDE_COUNT = 2 * BlendingData.CELL_HORIZONTAL_MAX_INDEX_INSIDE + 1;
    private static final int CELL_COLUMN_OUTSIDE_COUNT = 2 * BlendingData.CELL_HORIZONTAL_MAX_INDEX_OUTSIDE + 1;
    private static final int CELL_COLUMN_COUNT = BlendingData.CELL_COLUMN_INSIDE_COUNT + BlendingData.CELL_COLUMN_OUTSIDE_COUNT;
    private final LevelHeightAccessor areaWithOldGeneration;
    private static final List<Block> SURFACE_BLOCKS = List.of(Blocks.PODZOL, Blocks.GRAVEL, Blocks.GRASS_BLOCK, Blocks.STONE, Blocks.COARSE_DIRT, Blocks.SAND, Blocks.RED_SAND, Blocks.MYCELIUM, Blocks.SNOW_BLOCK, Blocks.TERRACOTTA, Blocks.DIRT);
    protected static final double NO_VALUE = Double.MAX_VALUE;
    private boolean hasCalculatedData;
    private final double[] heights;
    private final List<@Nullable List<@Nullable Holder<Biome>>> biomes;
    private final transient double[][] densities;

    private BlendingData(int minSection, int maxSection, Optional<double[]> heights) {
        this.heights = (double[]) heights.orElseGet(() -> {
            return (double[]) Util.make(new double[BlendingData.CELL_COLUMN_COUNT], (adouble) -> {
                Arrays.fill(adouble, Double.MAX_VALUE);
            });
        });
        this.densities = new double[BlendingData.CELL_COLUMN_COUNT][];
        ObjectArrayList<List<Holder<Biome>>> objectarraylist = new ObjectArrayList(BlendingData.CELL_COLUMN_COUNT);

        objectarraylist.size(BlendingData.CELL_COLUMN_COUNT);
        this.biomes = objectarraylist;
        int k = SectionPos.sectionToBlockCoord(minSection);
        int l = SectionPos.sectionToBlockCoord(maxSection) - k;

        this.areaWithOldGeneration = LevelHeightAccessor.create(k, l);
    }

    public static @Nullable BlendingData unpack(BlendingData.@Nullable Packed packed) {
        return packed == null ? null : new BlendingData(packed.minSection(), packed.maxSection(), packed.heights());
    }

    public BlendingData.Packed pack() {
        boolean flag = false;

        for (double d0 : this.heights) {
            if (d0 != Double.MAX_VALUE) {
                flag = true;
                break;
            }
        }

        return new BlendingData.Packed(this.areaWithOldGeneration.getMinSectionY(), this.areaWithOldGeneration.getMaxSectionY() + 1, flag ? Optional.of(DoubleArrays.copy(this.heights)) : Optional.empty());
    }

    public static @Nullable BlendingData getOrUpdateBlendingData(WorldGenRegion region, int chunkX, int chunkZ) {
        ChunkAccess chunkaccess = region.getChunk(chunkX, chunkZ);
        BlendingData blendingdata = chunkaccess.getBlendingData();

        if (blendingdata != null && !chunkaccess.getHighestGeneratedStatus().isBefore(ChunkStatus.BIOMES)) {
            blendingdata.calculateData(chunkaccess, sideByGenerationAge(region, chunkX, chunkZ, false));
            return blendingdata;
        } else {
            return null;
        }
    }

    public static Set<Direction8> sideByGenerationAge(WorldGenLevel region, int chunkX, int chunkZ, boolean wantedOldGen) {
        Set<Direction8> set = EnumSet.noneOf(Direction8.class);

        for (Direction8 direction8 : Direction8.values()) {
            int k = chunkX + direction8.getStepX();
            int l = chunkZ + direction8.getStepZ();

            if (region.getChunk(k, l).isOldNoiseGeneration() == wantedOldGen) {
                set.add(direction8);
            }
        }

        return set;
    }

    private void calculateData(ChunkAccess chunk, Set<Direction8> newSides) {
        if (!this.hasCalculatedData) {
            if (newSides.contains(Direction8.NORTH) || newSides.contains(Direction8.WEST) || newSides.contains(Direction8.NORTH_WEST)) {
                this.addValuesForColumn(getInsideIndex(0, 0), chunk, 0, 0);
            }

            if (newSides.contains(Direction8.NORTH)) {
                for (int i = 1; i < BlendingData.QUARTS_PER_SECTION; ++i) {
                    this.addValuesForColumn(getInsideIndex(i, 0), chunk, 4 * i, 0);
                }
            }

            if (newSides.contains(Direction8.WEST)) {
                for (int j = 1; j < BlendingData.QUARTS_PER_SECTION; ++j) {
                    this.addValuesForColumn(getInsideIndex(0, j), chunk, 0, 4 * j);
                }
            }

            if (newSides.contains(Direction8.EAST)) {
                for (int k = 1; k < BlendingData.QUARTS_PER_SECTION; ++k) {
                    this.addValuesForColumn(getOutsideIndex(BlendingData.CELL_HORIZONTAL_MAX_INDEX_OUTSIDE, k), chunk, 15, 4 * k);
                }
            }

            if (newSides.contains(Direction8.SOUTH)) {
                for (int l = 0; l < BlendingData.QUARTS_PER_SECTION; ++l) {
                    this.addValuesForColumn(getOutsideIndex(l, BlendingData.CELL_HORIZONTAL_MAX_INDEX_OUTSIDE), chunk, 4 * l, 15);
                }
            }

            if (newSides.contains(Direction8.EAST) && newSides.contains(Direction8.NORTH_EAST)) {
                this.addValuesForColumn(getOutsideIndex(BlendingData.CELL_HORIZONTAL_MAX_INDEX_OUTSIDE, 0), chunk, 15, 0);
            }

            if (newSides.contains(Direction8.EAST) && newSides.contains(Direction8.SOUTH) && newSides.contains(Direction8.SOUTH_EAST)) {
                this.addValuesForColumn(getOutsideIndex(BlendingData.CELL_HORIZONTAL_MAX_INDEX_OUTSIDE, BlendingData.CELL_HORIZONTAL_MAX_INDEX_OUTSIDE), chunk, 15, 15);
            }

            this.hasCalculatedData = true;
        }
    }

    private void addValuesForColumn(int index, ChunkAccess chunk, int blockX, int blockZ) {
        if (this.heights[index] == Double.MAX_VALUE) {
            this.heights[index] = (double) this.getHeightAtXZ(chunk, blockX, blockZ);
        }

        this.densities[index] = this.getDensityColumn(chunk, blockX, blockZ, Mth.floor(this.heights[index]));
        this.biomes.set(index, this.getBiomeColumn(chunk, blockX, blockZ));
    }

    private int getHeightAtXZ(ChunkAccess chunk, int blockX, int blockZ) {
        int k;

        if (chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE_WG)) {
            k = Math.min(chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, blockX, blockZ), this.areaWithOldGeneration.getMaxY());
        } else {
            k = this.areaWithOldGeneration.getMaxY();
        }

        int l = this.areaWithOldGeneration.getMinY();
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos(blockX, k, blockZ);

        while (blockpos_mutableblockpos.getY() > l) {
            if (BlendingData.SURFACE_BLOCKS.contains(chunk.getBlockState(blockpos_mutableblockpos).getBlock())) {
                return blockpos_mutableblockpos.getY();
            }

            blockpos_mutableblockpos.move(Direction.DOWN);
        }

        return l;
    }

    private static double read1(ChunkAccess chunk, BlockPos.MutableBlockPos pos) {
        return isGround(chunk, pos.move(Direction.DOWN)) ? 1.0D : -1.0D;
    }

    private static double read7(ChunkAccess chunk, BlockPos.MutableBlockPos pos) {
        double d0 = 0.0D;

        for (int i = 0; i < 7; ++i) {
            d0 += read1(chunk, pos);
        }

        return d0;
    }

    private double[] getDensityColumn(ChunkAccess chunk, int x, int z, int height) {
        double[] adouble = new double[this.cellCountPerColumn()];

        Arrays.fill(adouble, -1.0D);
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos(x, this.areaWithOldGeneration.getMaxY() + 1, z);
        double d0 = read7(chunk, blockpos_mutableblockpos);

        for (int l = adouble.length - 2; l >= 0; --l) {
            double d1 = read1(chunk, blockpos_mutableblockpos);
            double d2 = read7(chunk, blockpos_mutableblockpos);

            adouble[l] = (d0 + d1 + d2) / 15.0D;
            d0 = d2;
        }

        int i1 = this.getCellYIndex(Mth.floorDiv(height, 8));

        if (i1 >= 0 && i1 < adouble.length - 1) {
            double d3 = ((double) height + 0.5D) % 8.0D / 8.0D;
            double d4 = (1.0D - d3) / d3;
            double d5 = Math.max(d4, 1.0D) * 0.25D;

            adouble[i1 + 1] = -d4 / d5;
            adouble[i1] = 1.0D / d5;
        }

        return adouble;
    }

    private List<Holder<Biome>> getBiomeColumn(ChunkAccess chunk, int blockX, int blockZ) {
        ObjectArrayList<Holder<Biome>> objectarraylist = new ObjectArrayList(this.quartCountPerColumn());

        objectarraylist.size(this.quartCountPerColumn());

        for (int k = 0; k < objectarraylist.size(); ++k) {
            int l = k + QuartPos.fromBlock(this.areaWithOldGeneration.getMinY());

            objectarraylist.set(k, chunk.getNoiseBiome(QuartPos.fromBlock(blockX), l, QuartPos.fromBlock(blockZ)));
        }

        return objectarraylist;
    }

    private static boolean isGround(ChunkAccess chunk, BlockPos pos) {
        BlockState blockstate = chunk.getBlockState(pos);

        return blockstate.isAir() ? false : (blockstate.is(BlockTags.LEAVES) ? false : (blockstate.is(BlockTags.LOGS) ? false : (!blockstate.is(Blocks.BROWN_MUSHROOM_BLOCK) && !blockstate.is(Blocks.RED_MUSHROOM_BLOCK) ? !blockstate.getCollisionShape(chunk, pos).isEmpty() : false)));
    }

    protected double getHeight(int cellX, int cellY, int cellZ) {
        return cellX != BlendingData.CELL_HORIZONTAL_MAX_INDEX_OUTSIDE && cellZ != BlendingData.CELL_HORIZONTAL_MAX_INDEX_OUTSIDE ? (cellX != 0 && cellZ != 0 ? Double.MAX_VALUE : this.heights[getInsideIndex(cellX, cellZ)]) : this.heights[getOutsideIndex(cellX, cellZ)];
    }

    private double getDensity(double @Nullable [] densityColumn, int cellY) {
        if (densityColumn == null) {
            return Double.MAX_VALUE;
        } else {
            int j = this.getCellYIndex(cellY);

            return j >= 0 && j < densityColumn.length ? densityColumn[j] * 0.1D : Double.MAX_VALUE;
        }
    }

    protected double getDensity(int cellX, int cellY, int cellZ) {
        return cellY == this.getMinY() ? 0.1D : (cellX != BlendingData.CELL_HORIZONTAL_MAX_INDEX_OUTSIDE && cellZ != BlendingData.CELL_HORIZONTAL_MAX_INDEX_OUTSIDE ? (cellX != 0 && cellZ != 0 ? Double.MAX_VALUE : this.getDensity(this.densities[getInsideIndex(cellX, cellZ)], cellY)) : this.getDensity(this.densities[getOutsideIndex(cellX, cellZ)], cellY));
    }

    protected void iterateBiomes(int minCellX, int quartY, int minCellZ, BlendingData.BiomeConsumer biomeConsumer) {
        if (quartY >= QuartPos.fromBlock(this.areaWithOldGeneration.getMinY()) && quartY <= QuartPos.fromBlock(this.areaWithOldGeneration.getMaxY())) {
            int l = quartY - QuartPos.fromBlock(this.areaWithOldGeneration.getMinY());

            for (int i1 = 0; i1 < this.biomes.size(); ++i1) {
                List<Holder<Biome>> list = (List) this.biomes.get(i1);

                if (list != null) {
                    Holder<Biome> holder = (Holder) list.get(l);

                    if (holder != null) {
                        biomeConsumer.consume(minCellX + getX(i1), minCellZ + getZ(i1), holder);
                    }
                }
            }

        }
    }

    protected void iterateHeights(int minCellX, int minCellZ, BlendingData.HeightConsumer heightConsumer) {
        for (int k = 0; k < this.heights.length; ++k) {
            double d0 = this.heights[k];

            if (d0 != Double.MAX_VALUE) {
                heightConsumer.consume(minCellX + getX(k), minCellZ + getZ(k), d0);
            }
        }

    }

    protected void iterateDensities(int minCellX, int minCellZ, int fromCellY, int toCellY, BlendingData.DensityConsumer densityConsumer) {
        int i1 = this.getColumnMinY();
        int j1 = Math.max(0, fromCellY - i1);
        int k1 = Math.min(this.cellCountPerColumn(), toCellY - i1);

        for (int l1 = 0; l1 < this.densities.length; ++l1) {
            double[] adouble = this.densities[l1];

            if (adouble != null) {
                int i2 = minCellX + getX(l1);
                int j2 = minCellZ + getZ(l1);

                for (int k2 = j1; k2 < k1; ++k2) {
                    densityConsumer.consume(i2, k2 + i1, j2, adouble[k2] * 0.1D);
                }
            }
        }

    }

    private int cellCountPerColumn() {
        return this.areaWithOldGeneration.getSectionsCount() * 2;
    }

    private int quartCountPerColumn() {
        return QuartPos.fromSection(this.areaWithOldGeneration.getSectionsCount());
    }

    private int getColumnMinY() {
        return this.getMinY() + 1;
    }

    private int getMinY() {
        return this.areaWithOldGeneration.getMinSectionY() * 2;
    }

    private int getCellYIndex(int cellY) {
        return cellY - this.getColumnMinY();
    }

    private static int getInsideIndex(int x, int z) {
        return BlendingData.CELL_HORIZONTAL_MAX_INDEX_INSIDE - x + z;
    }

    private static int getOutsideIndex(int x, int z) {
        return BlendingData.CELL_COLUMN_INSIDE_COUNT + x + BlendingData.CELL_HORIZONTAL_MAX_INDEX_OUTSIDE - z;
    }

    private static int getX(int index) {
        if (index < BlendingData.CELL_COLUMN_INSIDE_COUNT) {
            return zeroIfNegative(BlendingData.CELL_HORIZONTAL_MAX_INDEX_INSIDE - index);
        } else {
            int j = index - BlendingData.CELL_COLUMN_INSIDE_COUNT;

            return BlendingData.CELL_HORIZONTAL_MAX_INDEX_OUTSIDE - zeroIfNegative(BlendingData.CELL_HORIZONTAL_MAX_INDEX_OUTSIDE - j);
        }
    }

    private static int getZ(int index) {
        if (index < BlendingData.CELL_COLUMN_INSIDE_COUNT) {
            return zeroIfNegative(index - BlendingData.CELL_HORIZONTAL_MAX_INDEX_INSIDE);
        } else {
            int j = index - BlendingData.CELL_COLUMN_INSIDE_COUNT;

            return BlendingData.CELL_HORIZONTAL_MAX_INDEX_OUTSIDE - zeroIfNegative(j - BlendingData.CELL_HORIZONTAL_MAX_INDEX_OUTSIDE);
        }
    }

    private static int zeroIfNegative(int value) {
        return value & ~(value >> 31);
    }

    public LevelHeightAccessor getAreaWithOldGeneration() {
        return this.areaWithOldGeneration;
    }

    public static record Packed(int minSection, int maxSection, Optional<double[]> heights) {

        private static final Codec<double[]> DOUBLE_ARRAY_CODEC = Codec.DOUBLE.listOf().xmap(Doubles::toArray, Doubles::asList);
        public static final Codec<BlendingData.Packed> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.INT.fieldOf("min_section").forGetter(BlendingData.Packed::minSection), Codec.INT.fieldOf("max_section").forGetter(BlendingData.Packed::maxSection), BlendingData.Packed.DOUBLE_ARRAY_CODEC.lenientOptionalFieldOf("heights").forGetter(BlendingData.Packed::heights)).apply(instance, BlendingData.Packed::new);
        }).validate(BlendingData.Packed::validateArraySize);

        private static DataResult<BlendingData.Packed> validateArraySize(BlendingData.Packed blendingData) {
            return blendingData.heights.isPresent() && ((double[]) blendingData.heights.get()).length != BlendingData.CELL_COLUMN_COUNT ? DataResult.error(() -> {
                return "heights has to be of length " + BlendingData.CELL_COLUMN_COUNT;
            }) : DataResult.success(blendingData);
        }
    }

    protected interface BiomeConsumer {

        void consume(int cellX, int cellZ, Holder<Biome> biome);
    }

    protected interface DensityConsumer {

        void consume(int cellX, int cellY, int cellZ, double density);
    }

    protected interface HeightConsumer {

        void consume(int cellX, int cellZ, double height);
    }
}

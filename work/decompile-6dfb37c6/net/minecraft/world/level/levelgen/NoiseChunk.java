package net.minecraft.world.level.levelgen;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.material.MaterialRuleList;
import org.jspecify.annotations.Nullable;

public class NoiseChunk implements DensityFunction.FunctionContext, DensityFunction.ContextProvider {

    private final int cellCountXZ;
    private final int cellCountY;
    private final int cellNoiseMinY;
    private final int firstCellX;
    private final int firstCellZ;
    private final int firstNoiseX;
    private final int firstNoiseZ;
    private final List<NoiseChunk.NoiseInterpolator> interpolators;
    private final List<NoiseChunk.CacheAllInCell> cellCaches;
    private final Map<DensityFunction, DensityFunction> wrapped = new HashMap();
    private final Long2IntMap preliminarySurfaceLevelCache = new Long2IntOpenHashMap();
    private final Aquifer aquifer;
    private final DensityFunction preliminarySurfaceLevel;
    private final NoiseChunk.BlockStateFiller blockStateRule;
    private final Blender blender;
    private final NoiseChunk.FlatCache blendAlpha;
    private final NoiseChunk.FlatCache blendOffset;
    private final DensityFunctions.BeardifierOrMarker beardifier;
    private long lastBlendingDataPos;
    private Blender.BlendingOutput lastBlendingOutput;
    private final int noiseSizeXZ;
    private final int cellWidth;
    private final int cellHeight;
    private boolean interpolating;
    private boolean fillingCell;
    private int cellStartBlockX;
    private int cellStartBlockY;
    private int cellStartBlockZ;
    private int inCellX;
    private int inCellY;
    private int inCellZ;
    private long interpolationCounter;
    private long arrayInterpolationCounter;
    private int arrayIndex;
    private final DensityFunction.ContextProvider sliceFillingContextProvider;

    public static NoiseChunk forChunk(ChunkAccess chunk, RandomState randomState, DensityFunctions.BeardifierOrMarker beardifier, NoiseGeneratorSettings settings, Aquifer.FluidPicker globalFluidPicker, Blender blender) {
        NoiseSettings noisesettings = settings.noiseSettings().clampToHeightAccessor(chunk);
        ChunkPos chunkpos = chunk.getPos();
        int i = 16 / noisesettings.getCellWidth();

        return new NoiseChunk(i, randomState, chunkpos.getMinBlockX(), chunkpos.getMinBlockZ(), noisesettings, beardifier, settings, globalFluidPicker, blender);
    }

    public NoiseChunk(int cellCountXZ, RandomState randomState, int chunkMinBlockX, int chunkMinBlockZ, NoiseSettings noiseSettings, DensityFunctions.BeardifierOrMarker beardifier, NoiseGeneratorSettings settings, Aquifer.FluidPicker globalFluidPicker, Blender blender) {
        this.lastBlendingDataPos = ChunkPos.INVALID_CHUNK_POS;
        this.lastBlendingOutput = new Blender.BlendingOutput(1.0D, 0.0D);
        this.sliceFillingContextProvider = new DensityFunction.ContextProvider() {
            @Override
            public DensityFunction.FunctionContext forIndex(int cellYIndex) {
                NoiseChunk.this.cellStartBlockY = (cellYIndex + NoiseChunk.this.cellNoiseMinY) * NoiseChunk.this.cellHeight;
                ++NoiseChunk.this.interpolationCounter;
                NoiseChunk.this.inCellY = 0;
                NoiseChunk.this.arrayIndex = cellYIndex;
                return NoiseChunk.this;
            }

            @Override
            public void fillAllDirectly(double[] output, DensityFunction function) {
                for (int l = 0; l < NoiseChunk.this.cellCountY + 1; ++l) {
                    NoiseChunk.this.cellStartBlockY = (l + NoiseChunk.this.cellNoiseMinY) * NoiseChunk.this.cellHeight;
                    ++NoiseChunk.this.interpolationCounter;
                    NoiseChunk.this.inCellY = 0;
                    NoiseChunk.this.arrayIndex = l;
                    output[l] = function.compute(NoiseChunk.this);
                }

            }
        };
        this.cellWidth = noiseSettings.getCellWidth();
        this.cellHeight = noiseSettings.getCellHeight();
        this.cellCountXZ = cellCountXZ;
        this.cellCountY = Mth.floorDiv(noiseSettings.height(), this.cellHeight);
        this.cellNoiseMinY = Mth.floorDiv(noiseSettings.minY(), this.cellHeight);
        this.firstCellX = Math.floorDiv(chunkMinBlockX, this.cellWidth);
        this.firstCellZ = Math.floorDiv(chunkMinBlockZ, this.cellWidth);
        this.interpolators = Lists.newArrayList();
        this.cellCaches = Lists.newArrayList();
        this.firstNoiseX = QuartPos.fromBlock(chunkMinBlockX);
        this.firstNoiseZ = QuartPos.fromBlock(chunkMinBlockZ);
        this.noiseSizeXZ = QuartPos.fromBlock(cellCountXZ * this.cellWidth);
        this.blender = blender;
        this.beardifier = beardifier;
        this.blendAlpha = new NoiseChunk.FlatCache(new NoiseChunk.BlendAlpha(), false);
        this.blendOffset = new NoiseChunk.FlatCache(new NoiseChunk.BlendOffset(), false);
        if (!blender.isEmpty()) {
            for (int l = 0; l <= this.noiseSizeXZ; ++l) {
                int i1 = this.firstNoiseX + l;
                int j1 = QuartPos.toBlock(i1);

                for (int k1 = 0; k1 <= this.noiseSizeXZ; ++k1) {
                    int l1 = this.firstNoiseZ + k1;
                    int i2 = QuartPos.toBlock(l1);
                    Blender.BlendingOutput blender_blendingoutput = blender.blendOffsetAndFactor(j1, i2);

                    this.blendAlpha.values[l + k1 * this.blendAlpha.sizeXZ] = blender_blendingoutput.alpha();
                    this.blendOffset.values[l + k1 * this.blendOffset.sizeXZ] = blender_blendingoutput.blendingOffset();
                }
            }
        } else {
            Arrays.fill(this.blendAlpha.values, 1.0D);
            Arrays.fill(this.blendOffset.values, 0.0D);
        }

        NoiseRouter noiserouter = randomState.router();
        NoiseRouter noiserouter1 = noiserouter.mapAll(this::wrap);

        this.preliminarySurfaceLevel = noiserouter1.preliminarySurfaceLevel();
        if (!settings.isAquifersEnabled()) {
            this.aquifer = Aquifer.createDisabled(globalFluidPicker);
        } else {
            int j2 = SectionPos.blockToSectionCoord(chunkMinBlockX);
            int k2 = SectionPos.blockToSectionCoord(chunkMinBlockZ);

            this.aquifer = Aquifer.create(this, new ChunkPos(j2, k2), noiserouter1, randomState.aquiferRandom(), noiseSettings.minY(), noiseSettings.height(), globalFluidPicker);
        }

        List<NoiseChunk.BlockStateFiller> list = new ArrayList();
        DensityFunction densityfunction = DensityFunctions.cacheAllInCell(DensityFunctions.add(noiserouter1.finalDensity(), DensityFunctions.BeardifierMarker.INSTANCE)).mapAll(this::wrap);

        list.add((NoiseChunk.BlockStateFiller) (densityfunction_functioncontext) -> {
            return this.aquifer.computeSubstance(densityfunction_functioncontext, densityfunction.compute(densityfunction_functioncontext));
        });
        if (settings.oreVeinsEnabled()) {
            list.add(OreVeinifier.create(noiserouter1.veinToggle(), noiserouter1.veinRidged(), noiserouter1.veinGap(), randomState.oreRandom()));
        }

        this.blockStateRule = new MaterialRuleList((NoiseChunk.BlockStateFiller[]) list.toArray(new NoiseChunk.BlockStateFiller[0]));
    }

    protected Climate.Sampler cachedClimateSampler(NoiseRouter noises, List<Climate.ParameterPoint> spawnTarget) {
        return new Climate.Sampler(noises.temperature().mapAll(this::wrap), noises.vegetation().mapAll(this::wrap), noises.continents().mapAll(this::wrap), noises.erosion().mapAll(this::wrap), noises.depth().mapAll(this::wrap), noises.ridges().mapAll(this::wrap), spawnTarget);
    }

    protected @Nullable BlockState getInterpolatedState() {
        return this.blockStateRule.calculate(this);
    }

    @Override
    public int blockX() {
        return this.cellStartBlockX + this.inCellX;
    }

    @Override
    public int blockY() {
        return this.cellStartBlockY + this.inCellY;
    }

    @Override
    public int blockZ() {
        return this.cellStartBlockZ + this.inCellZ;
    }

    public int maxPreliminarySurfaceLevel(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        int i1 = Integer.MIN_VALUE;

        for (int j1 = minBlockZ; j1 <= maxBlockZ; j1 += 4) {
            for (int k1 = minBlockX; k1 <= maxBlockX; k1 += 4) {
                int l1 = this.preliminarySurfaceLevel(k1, j1);

                if (l1 > i1) {
                    i1 = l1;
                }
            }
        }

        return i1;
    }

    public int preliminarySurfaceLevel(int sampleX, int sampleZ) {
        int k = QuartPos.toBlock(QuartPos.fromBlock(sampleX));
        int l = QuartPos.toBlock(QuartPos.fromBlock(sampleZ));

        return this.preliminarySurfaceLevelCache.computeIfAbsent(ColumnPos.asLong(k, l), this::computePreliminarySurfaceLevel);
    }

    private int computePreliminarySurfaceLevel(long key) {
        int j = ColumnPos.getX(key);
        int k = ColumnPos.getZ(key);

        return Mth.floor(this.preliminarySurfaceLevel.compute(new DensityFunction.SinglePointContext(j, 0, k)));
    }

    @Override
    public Blender getBlender() {
        return this.blender;
    }

    private void fillSlice(boolean slice0, int cellX) {
        this.cellStartBlockX = cellX * this.cellWidth;
        this.inCellX = 0;

        for (int j = 0; j < this.cellCountXZ + 1; ++j) {
            int k = this.firstCellZ + j;

            this.cellStartBlockZ = k * this.cellWidth;
            this.inCellZ = 0;
            ++this.arrayInterpolationCounter;

            for (NoiseChunk.NoiseInterpolator noisechunk_noiseinterpolator : this.interpolators) {
                double[] adouble = (slice0 ? noisechunk_noiseinterpolator.slice0 : noisechunk_noiseinterpolator.slice1)[j];

                noisechunk_noiseinterpolator.fillArray(adouble, this.sliceFillingContextProvider);
            }
        }

        ++this.arrayInterpolationCounter;
    }

    public void initializeForFirstCellX() {
        if (this.interpolating) {
            throw new IllegalStateException("Staring interpolation twice");
        } else {
            this.interpolating = true;
            this.interpolationCounter = 0L;
            this.fillSlice(true, this.firstCellX);
        }
    }

    public void advanceCellX(int cellXIndex) {
        this.fillSlice(false, this.firstCellX + cellXIndex + 1);
        this.cellStartBlockX = (this.firstCellX + cellXIndex) * this.cellWidth;
    }

    @Override
    public NoiseChunk forIndex(int cellIndex) {
        int j = Math.floorMod(cellIndex, this.cellWidth);
        int k = Math.floorDiv(cellIndex, this.cellWidth);
        int l = Math.floorMod(k, this.cellWidth);
        int i1 = this.cellHeight - 1 - Math.floorDiv(k, this.cellWidth);

        this.inCellX = l;
        this.inCellY = i1;
        this.inCellZ = j;
        this.arrayIndex = cellIndex;
        return this;
    }

    @Override
    public void fillAllDirectly(double[] output, DensityFunction function) {
        this.arrayIndex = 0;

        for (int i = this.cellHeight - 1; i >= 0; --i) {
            this.inCellY = i;

            for (int j = 0; j < this.cellWidth; ++j) {
                this.inCellX = j;

                for (int k = 0; k < this.cellWidth; ++k) {
                    this.inCellZ = k;
                    output[this.arrayIndex++] = function.compute(this);
                }
            }
        }

    }

    public void selectCellYZ(int cellYIndex, int cellZIndex) {
        for (NoiseChunk.NoiseInterpolator noisechunk_noiseinterpolator : this.interpolators) {
            noisechunk_noiseinterpolator.selectCellYZ(cellYIndex, cellZIndex);
        }

        this.fillingCell = true;
        this.cellStartBlockY = (cellYIndex + this.cellNoiseMinY) * this.cellHeight;
        this.cellStartBlockZ = (this.firstCellZ + cellZIndex) * this.cellWidth;
        ++this.arrayInterpolationCounter;

        for (NoiseChunk.CacheAllInCell noisechunk_cacheallincell : this.cellCaches) {
            noisechunk_cacheallincell.noiseFiller.fillArray(noisechunk_cacheallincell.values, this);
        }

        ++this.arrayInterpolationCounter;
        this.fillingCell = false;
    }

    public void updateForY(int posY, double factorY) {
        this.inCellY = posY - this.cellStartBlockY;

        for (NoiseChunk.NoiseInterpolator noisechunk_noiseinterpolator : this.interpolators) {
            noisechunk_noiseinterpolator.updateForY(factorY);
        }

    }

    public void updateForX(int posX, double factorX) {
        this.inCellX = posX - this.cellStartBlockX;

        for (NoiseChunk.NoiseInterpolator noisechunk_noiseinterpolator : this.interpolators) {
            noisechunk_noiseinterpolator.updateForX(factorX);
        }

    }

    public void updateForZ(int posZ, double factorZ) {
        this.inCellZ = posZ - this.cellStartBlockZ;
        ++this.interpolationCounter;

        for (NoiseChunk.NoiseInterpolator noisechunk_noiseinterpolator : this.interpolators) {
            noisechunk_noiseinterpolator.updateForZ(factorZ);
        }

    }

    public void stopInterpolation() {
        if (!this.interpolating) {
            throw new IllegalStateException("Staring interpolation twice");
        } else {
            this.interpolating = false;
        }
    }

    public void swapSlices() {
        this.interpolators.forEach(NoiseChunk.NoiseInterpolator::swapSlices);
    }

    public Aquifer aquifer() {
        return this.aquifer;
    }

    protected int cellWidth() {
        return this.cellWidth;
    }

    protected int cellHeight() {
        return this.cellHeight;
    }

    private Blender.BlendingOutput getOrComputeBlendingOutput(int blockX, int blockZ) {
        long k = ChunkPos.asLong(blockX, blockZ);

        if (this.lastBlendingDataPos == k) {
            return this.lastBlendingOutput;
        } else {
            this.lastBlendingDataPos = k;
            Blender.BlendingOutput blender_blendingoutput = this.blender.blendOffsetAndFactor(blockX, blockZ);

            this.lastBlendingOutput = blender_blendingoutput;
            return blender_blendingoutput;
        }
    }

    protected DensityFunction wrap(DensityFunction function) {
        return (DensityFunction) this.wrapped.computeIfAbsent(function, this::wrapNew);
    }

    private DensityFunction wrapNew(DensityFunction function) {
        if (function instanceof DensityFunctions.Marker) {
            DensityFunctions.Marker densityfunctions_marker = (DensityFunctions.Marker) function;
            Object object;

            switch (densityfunctions_marker.type()) {
                case Interpolated:
                    object = new NoiseChunk.NoiseInterpolator(densityfunctions_marker.wrapped());
                    break;
                case FlatCache:
                    object = new NoiseChunk.FlatCache(densityfunctions_marker.wrapped(), true);
                    break;
                case Cache2D:
                    object = new NoiseChunk.Cache2D(densityfunctions_marker.wrapped());
                    break;
                case CacheOnce:
                    object = new NoiseChunk.CacheOnce(densityfunctions_marker.wrapped());
                    break;
                case CacheAllInCell:
                    object = new NoiseChunk.CacheAllInCell(densityfunctions_marker.wrapped());
                    break;
                default:
                    throw new MatchException((String) null, (Throwable) null);
            }

            return (DensityFunction) object;
        } else {
            if (this.blender != Blender.empty()) {
                if (function == DensityFunctions.BlendAlpha.INSTANCE) {
                    return this.blendAlpha;
                }

                if (function == DensityFunctions.BlendOffset.INSTANCE) {
                    return this.blendOffset;
                }
            }

            if (function == DensityFunctions.BeardifierMarker.INSTANCE) {
                return this.beardifier;
            } else if (function instanceof DensityFunctions.HolderHolder) {
                DensityFunctions.HolderHolder densityfunctions_holderholder = (DensityFunctions.HolderHolder) function;

                return (DensityFunction) densityfunctions_holderholder.function().value();
            } else {
                return function;
            }
        }
    }

    private interface NoiseChunkDensityFunction extends DensityFunction {

        DensityFunction wrapped();

        @Override
        default double minValue() {
            return this.wrapped().minValue();
        }

        @Override
        default double maxValue() {
            return this.wrapped().maxValue();
        }
    }

    private class FlatCache implements NoiseChunk.NoiseChunkDensityFunction, DensityFunctions.MarkerOrMarked {

        private final DensityFunction noiseFiller;
        private final double[] values;
        private final int sizeXZ;

        private FlatCache(DensityFunction noiseFiller, boolean fill) {
            this.noiseFiller = noiseFiller;
            this.sizeXZ = NoiseChunk.this.noiseSizeXZ + 1;
            this.values = new double[this.sizeXZ * this.sizeXZ];
            if (fill) {
                for (int i = 0; i <= NoiseChunk.this.noiseSizeXZ; ++i) {
                    int j = NoiseChunk.this.firstNoiseX + i;
                    int k = QuartPos.toBlock(j);

                    for (int l = 0; l <= NoiseChunk.this.noiseSizeXZ; ++l) {
                        int i1 = NoiseChunk.this.firstNoiseZ + l;
                        int j1 = QuartPos.toBlock(i1);

                        this.values[i + l * this.sizeXZ] = noiseFiller.compute(new DensityFunction.SinglePointContext(k, 0, j1));
                    }
                }
            }

        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            int i = QuartPos.fromBlock(context.blockX());
            int j = QuartPos.fromBlock(context.blockZ());
            int k = i - NoiseChunk.this.firstNoiseX;
            int l = j - NoiseChunk.this.firstNoiseZ;

            return k >= 0 && l >= 0 && k < this.sizeXZ && l < this.sizeXZ ? this.values[k + l * this.sizeXZ] : this.noiseFiller.compute(context);
        }

        @Override
        public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(output, this);
        }

        @Override
        public DensityFunction wrapped() {
            return this.noiseFiller;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.FlatCache;
        }
    }

    private class CacheAllInCell implements NoiseChunk.NoiseChunkDensityFunction, DensityFunctions.MarkerOrMarked {

        private final DensityFunction noiseFiller;
        private final double[] values;

        private CacheAllInCell(DensityFunction noiseFiller) {
            this.noiseFiller = noiseFiller;
            this.values = new double[NoiseChunk.this.cellWidth * NoiseChunk.this.cellWidth * NoiseChunk.this.cellHeight];
            NoiseChunk.this.cellCaches.add(this);
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            if (context != NoiseChunk.this) {
                return this.noiseFiller.compute(context);
            } else if (!NoiseChunk.this.interpolating) {
                throw new IllegalStateException("Trying to sample interpolator outside the interpolation loop");
            } else {
                int i = NoiseChunk.this.inCellX;
                int j = NoiseChunk.this.inCellY;
                int k = NoiseChunk.this.inCellZ;

                return i >= 0 && j >= 0 && k >= 0 && i < NoiseChunk.this.cellWidth && j < NoiseChunk.this.cellHeight && k < NoiseChunk.this.cellWidth ? this.values[((NoiseChunk.this.cellHeight - 1 - j) * NoiseChunk.this.cellWidth + i) * NoiseChunk.this.cellWidth + k] : this.noiseFiller.compute(context);
            }
        }

        @Override
        public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(output, this);
        }

        @Override
        public DensityFunction wrapped() {
            return this.noiseFiller;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.CacheAllInCell;
        }
    }

    public class NoiseInterpolator implements NoiseChunk.NoiseChunkDensityFunction, DensityFunctions.MarkerOrMarked {

        private double[][] slice0;
        private double[][] slice1;
        private final DensityFunction noiseFiller;
        private double noise000;
        private double noise001;
        private double noise100;
        private double noise101;
        private double noise010;
        private double noise011;
        private double noise110;
        private double noise111;
        private double valueXZ00;
        private double valueXZ10;
        private double valueXZ01;
        private double valueXZ11;
        private double valueZ0;
        private double valueZ1;
        private double value;

        private NoiseInterpolator(DensityFunction noiseFiller) {
            this.noiseFiller = noiseFiller;
            this.slice0 = this.allocateSlice(NoiseChunk.this.cellCountY, NoiseChunk.this.cellCountXZ);
            this.slice1 = this.allocateSlice(NoiseChunk.this.cellCountY, NoiseChunk.this.cellCountXZ);
            NoiseChunk.this.interpolators.add(this);
        }

        private double[][] allocateSlice(int cellCountY, int cellCountZ) {
            int k = cellCountZ + 1;
            int l = cellCountY + 1;
            double[][] adouble = new double[k][l];

            for (int i1 = 0; i1 < k; ++i1) {
                adouble[i1] = new double[l];
            }

            return adouble;
        }

        private void selectCellYZ(int cellYIndex, int cellZIndex) {
            this.noise000 = this.slice0[cellZIndex][cellYIndex];
            this.noise001 = this.slice0[cellZIndex + 1][cellYIndex];
            this.noise100 = this.slice1[cellZIndex][cellYIndex];
            this.noise101 = this.slice1[cellZIndex + 1][cellYIndex];
            this.noise010 = this.slice0[cellZIndex][cellYIndex + 1];
            this.noise011 = this.slice0[cellZIndex + 1][cellYIndex + 1];
            this.noise110 = this.slice1[cellZIndex][cellYIndex + 1];
            this.noise111 = this.slice1[cellZIndex + 1][cellYIndex + 1];
        }

        private void updateForY(double factorY) {
            this.valueXZ00 = Mth.lerp(factorY, this.noise000, this.noise010);
            this.valueXZ10 = Mth.lerp(factorY, this.noise100, this.noise110);
            this.valueXZ01 = Mth.lerp(factorY, this.noise001, this.noise011);
            this.valueXZ11 = Mth.lerp(factorY, this.noise101, this.noise111);
        }

        private void updateForX(double factorX) {
            this.valueZ0 = Mth.lerp(factorX, this.valueXZ00, this.valueXZ10);
            this.valueZ1 = Mth.lerp(factorX, this.valueXZ01, this.valueXZ11);
        }

        private void updateForZ(double factorZ) {
            this.value = Mth.lerp(factorZ, this.valueZ0, this.valueZ1);
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            if (context != NoiseChunk.this) {
                return this.noiseFiller.compute(context);
            } else if (!NoiseChunk.this.interpolating) {
                throw new IllegalStateException("Trying to sample interpolator outside the interpolation loop");
            } else {
                return NoiseChunk.this.fillingCell ? Mth.lerp3((double) NoiseChunk.this.inCellX / (double) NoiseChunk.this.cellWidth, (double) NoiseChunk.this.inCellY / (double) NoiseChunk.this.cellHeight, (double) NoiseChunk.this.inCellZ / (double) NoiseChunk.this.cellWidth, this.noise000, this.noise100, this.noise010, this.noise110, this.noise001, this.noise101, this.noise011, this.noise111) : this.value;
            }
        }

        @Override
        public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
            if (NoiseChunk.this.fillingCell) {
                contextProvider.fillAllDirectly(output, this);
            } else {
                this.wrapped().fillArray(output, contextProvider);
            }
        }

        @Override
        public DensityFunction wrapped() {
            return this.noiseFiller;
        }

        private void swapSlices() {
            double[][] adouble = this.slice0;

            this.slice0 = this.slice1;
            this.slice1 = adouble;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.Interpolated;
        }
    }

    private class CacheOnce implements NoiseChunk.NoiseChunkDensityFunction, DensityFunctions.MarkerOrMarked {

        private final DensityFunction function;
        private long lastCounter;
        private long lastArrayCounter;
        private double lastValue;
        private double @Nullable [] lastArray;

        private CacheOnce(DensityFunction function) {
            this.function = function;
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            if (context != NoiseChunk.this) {
                return this.function.compute(context);
            } else if (this.lastArray != null && this.lastArrayCounter == NoiseChunk.this.arrayInterpolationCounter) {
                return this.lastArray[NoiseChunk.this.arrayIndex];
            } else if (this.lastCounter == NoiseChunk.this.interpolationCounter) {
                return this.lastValue;
            } else {
                this.lastCounter = NoiseChunk.this.interpolationCounter;
                double d0 = this.function.compute(context);

                this.lastValue = d0;
                return d0;
            }
        }

        @Override
        public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
            if (this.lastArray != null && this.lastArrayCounter == NoiseChunk.this.arrayInterpolationCounter) {
                System.arraycopy(this.lastArray, 0, output, 0, output.length);
            } else {
                this.wrapped().fillArray(output, contextProvider);
                if (this.lastArray != null && this.lastArray.length == output.length) {
                    System.arraycopy(output, 0, this.lastArray, 0, output.length);
                } else {
                    this.lastArray = (double[]) output.clone();
                }

                this.lastArrayCounter = NoiseChunk.this.arrayInterpolationCounter;
            }
        }

        @Override
        public DensityFunction wrapped() {
            return this.function;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.CacheOnce;
        }
    }

    private static class Cache2D implements NoiseChunk.NoiseChunkDensityFunction, DensityFunctions.MarkerOrMarked {

        private final DensityFunction function;
        private long lastPos2D;
        private double lastValue;

        private Cache2D(DensityFunction function) {
            this.lastPos2D = ChunkPos.INVALID_CHUNK_POS;
            this.function = function;
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            int i = context.blockX();
            int j = context.blockZ();
            long k = ChunkPos.asLong(i, j);

            if (this.lastPos2D == k) {
                return this.lastValue;
            } else {
                this.lastPos2D = k;
                double d0 = this.function.compute(context);

                this.lastValue = d0;
                return d0;
            }
        }

        @Override
        public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
            this.function.fillArray(output, contextProvider);
        }

        @Override
        public DensityFunction wrapped() {
            return this.function;
        }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.Cache2D;
        }
    }

    private class BlendAlpha implements NoiseChunk.NoiseChunkDensityFunction {

        private BlendAlpha() {}

        @Override
        public DensityFunction wrapped() {
            return DensityFunctions.BlendAlpha.INSTANCE;
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return this.wrapped().mapAll(visitor);
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return NoiseChunk.this.getOrComputeBlendingOutput(context.blockX(), context.blockZ()).alpha();
        }

        @Override
        public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(output, this);
        }

        @Override
        public double minValue() {
            return 0.0D;
        }

        @Override
        public double maxValue() {
            return 1.0D;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return DensityFunctions.BlendAlpha.CODEC;
        }
    }

    private class BlendOffset implements NoiseChunk.NoiseChunkDensityFunction {

        private BlendOffset() {}

        @Override
        public DensityFunction wrapped() {
            return DensityFunctions.BlendOffset.INSTANCE;
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return this.wrapped().mapAll(visitor);
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return NoiseChunk.this.getOrComputeBlendingOutput(context.blockX(), context.blockZ()).blendingOffset();
        }

        @Override
        public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(output, this);
        }

        @Override
        public double minValue() {
            return Double.NEGATIVE_INFINITY;
        }

        @Override
        public double maxValue() {
            return Double.POSITIVE_INFINITY;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return DensityFunctions.BlendOffset.CODEC;
        }
    }

    @FunctionalInterface
    public interface BlockStateFiller {

        @Nullable
        BlockState calculate(DensityFunction.FunctionContext context);
    }
}

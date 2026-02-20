package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

public final class NoiseBasedChunkGenerator extends ChunkGenerator {

    public static final MapCodec<NoiseBasedChunkGenerator> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter((noisebasedchunkgenerator) -> {
            return noisebasedchunkgenerator.biomeSource;
        }), NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter((noisebasedchunkgenerator) -> {
            return noisebasedchunkgenerator.settings;
        })).apply(instance, instance.stable(NoiseBasedChunkGenerator::new));
    });
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    public final Holder<NoiseGeneratorSettings> settings;
    private final Supplier<Aquifer.FluidPicker> globalFluidPicker;

    public NoiseBasedChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource);
        this.settings = settings;
        this.globalFluidPicker = Suppliers.memoize(() -> {
            return createFluidPicker(settings.value());
        });
    }

    private static Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        Aquifer.FluidStatus aquifer_fluidstatus = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        int i = settings.seaLevel();
        Aquifer.FluidStatus aquifer_fluidstatus1 = new Aquifer.FluidStatus(i, settings.defaultFluid());
        Aquifer.FluidStatus aquifer_fluidstatus2 = new Aquifer.FluidStatus(DimensionType.MIN_Y * 2, Blocks.AIR.defaultBlockState());

        return (j, k, l) -> {
            return SharedConstants.DEBUG_DISABLE_FLUID_GENERATION ? aquifer_fluidstatus2 : (k < Math.min(-54, i) ? aquifer_fluidstatus : aquifer_fluidstatus1);
        };
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess protoChunk) {
        return CompletableFuture.supplyAsync(() -> {
            this.doCreateBiomes(blender, randomState, structureManager, protoChunk);
            return protoChunk;
        }, Util.backgroundExecutor().forName("init_biomes"));
    }

    private void doCreateBiomes(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess protoChunk) {
        NoiseChunk noisechunk = protoChunk.getOrCreateNoiseChunk((chunkaccess1) -> {
            return this.createNoiseChunk(chunkaccess1, structureManager, blender, randomState);
        });
        BiomeResolver biomeresolver = BelowZeroRetrogen.getBiomeResolver(blender.getBiomeResolver(this.biomeSource), protoChunk);

        protoChunk.fillBiomesFromNoise(biomeresolver, noisechunk.cachedClimateSampler(randomState.router(), (this.settings.value()).spawnTarget()));
    }

    private NoiseChunk createNoiseChunk(ChunkAccess chunk, StructureManager structureManager, Blender blender, RandomState randomState) {
        return NoiseChunk.forChunk(chunk, randomState, Beardifier.forStructuresInChunk(structureManager, chunk.getPos()), this.settings.value(), (Aquifer.FluidPicker) this.globalFluidPicker.get(), blender);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return NoiseBasedChunkGenerator.CODEC;
    }

    public Holder<NoiseGeneratorSettings> generatorSettings() {
        return this.settings;
    }

    public boolean stable(ResourceKey<NoiseGeneratorSettings> expectedPreset) {
        return this.settings.is(expectedPreset);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        return this.iterateNoiseColumn(heightAccessor, randomState, x, z, (MutableObject) null, type.isOpaque()).orElse(heightAccessor.getMinY());
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        MutableObject<NoiseColumn> mutableobject = new MutableObject();

        this.iterateNoiseColumn(heightAccessor, randomState, x, z, mutableobject, (Predicate) null);
        return (NoiseColumn) mutableobject.get();
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos) {
        DecimalFormat decimalformat = new DecimalFormat("0.000", DecimalFormatSymbols.getInstance(Locale.ROOT));
        NoiseRouter noiserouter = randomState.router();
        DensityFunction.SinglePointContext densityfunction_singlepointcontext = new DensityFunction.SinglePointContext(feetPos.getX(), feetPos.getY(), feetPos.getZ());
        double d0 = noiserouter.ridges().compute(densityfunction_singlepointcontext);
        String s = decimalformat.format(noiserouter.temperature().compute(densityfunction_singlepointcontext));

        result.add("NoiseRouter T: " + s + " V: " + decimalformat.format(noiserouter.vegetation().compute(densityfunction_singlepointcontext)) + " C: " + decimalformat.format(noiserouter.continents().compute(densityfunction_singlepointcontext)) + " E: " + decimalformat.format(noiserouter.erosion().compute(densityfunction_singlepointcontext)) + " D: " + decimalformat.format(noiserouter.depth().compute(densityfunction_singlepointcontext)) + " W: " + decimalformat.format(d0) + " PV: " + decimalformat.format((double) NoiseRouterData.peaksAndValleys((float) d0)) + " PS: " + decimalformat.format(noiserouter.preliminarySurfaceLevel().compute(densityfunction_singlepointcontext)) + " N: " + decimalformat.format(noiserouter.finalDensity().compute(densityfunction_singlepointcontext)));
    }

    private OptionalInt iterateNoiseColumn(LevelHeightAccessor heightAccessor, RandomState randomState, int blockX, int blockZ, @Nullable MutableObject<NoiseColumn> columnReference, @Nullable Predicate<BlockState> tester) {
        NoiseSettings noisesettings = ((NoiseGeneratorSettings) this.settings.value()).noiseSettings().clampToHeightAccessor(heightAccessor);
        int k = noisesettings.getCellHeight();
        int l = noisesettings.minY();
        int i1 = Mth.floorDiv(l, k);
        int j1 = Mth.floorDiv(noisesettings.height(), k);

        if (j1 <= 0) {
            return OptionalInt.empty();
        } else {
            BlockState[] ablockstate;

            if (columnReference == null) {
                ablockstate = null;
            } else {
                ablockstate = new BlockState[noisesettings.height()];
                columnReference.setValue(new NoiseColumn(l, ablockstate));
            }

            int k1 = noisesettings.getCellWidth();
            int l1 = Math.floorDiv(blockX, k1);
            int i2 = Math.floorDiv(blockZ, k1);
            int j2 = Math.floorMod(blockX, k1);
            int k2 = Math.floorMod(blockZ, k1);
            int l2 = l1 * k1;
            int i3 = i2 * k1;
            double d0 = (double) j2 / (double) k1;
            double d1 = (double) k2 / (double) k1;
            NoiseChunk noisechunk = new NoiseChunk(1, randomState, l2, i3, noisesettings, DensityFunctions.BeardifierMarker.INSTANCE, this.settings.value(), (Aquifer.FluidPicker) this.globalFluidPicker.get(), Blender.empty());

            noisechunk.initializeForFirstCellX();
            noisechunk.advanceCellX(0);

            for (int j3 = j1 - 1; j3 >= 0; --j3) {
                noisechunk.selectCellYZ(j3, 0);

                for (int k3 = k - 1; k3 >= 0; --k3) {
                    int l3 = (i1 + j3) * k + k3;
                    double d2 = (double) k3 / (double) k;

                    noisechunk.updateForY(l3, d2);
                    noisechunk.updateForX(blockX, d0);
                    noisechunk.updateForZ(blockZ, d1);
                    BlockState blockstate = noisechunk.getInterpolatedState();
                    BlockState blockstate1 = blockstate == null ? ((NoiseGeneratorSettings) this.settings.value()).defaultBlock() : blockstate;

                    if (ablockstate != null) {
                        int i4 = j3 * k + k3;

                        ablockstate[i4] = blockstate1;
                    }

                    if (tester != null && tester.test(blockstate1)) {
                        noisechunk.stopInterpolation();
                        return OptionalInt.of(l3 + 1);
                    }
                }
            }

            noisechunk.stopInterpolation();
            return OptionalInt.empty();
        }
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess protoChunk) {
        if (!SharedConstants.debugVoidTerrain(protoChunk.getPos()) && !SharedConstants.DEBUG_DISABLE_SURFACE) {
            WorldGenerationContext worldgenerationcontext = new WorldGenerationContext(this, region);

            this.buildSurface(protoChunk, worldgenerationcontext, randomState, structureManager, region.getBiomeManager(), region.registryAccess().lookupOrThrow(Registries.BIOME), Blender.of(region));
        }
    }

    @VisibleForTesting
    public void buildSurface(ChunkAccess protoChunk, WorldGenerationContext context, RandomState randomState, StructureManager structureManager, BiomeManager biomeManager, Registry<Biome> biomeRegistry, Blender blender) {
        NoiseChunk noisechunk = protoChunk.getOrCreateNoiseChunk((chunkaccess1) -> {
            return this.createNoiseChunk(chunkaccess1, structureManager, blender, randomState);
        });
        NoiseGeneratorSettings noisegeneratorsettings = this.settings.value();

        randomState.surfaceSystem().buildSurface(randomState, biomeManager, biomeRegistry, noisegeneratorsettings.useLegacyRandomSource(), context, protoChunk, noisechunk, noisegeneratorsettings.surfaceRule());
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
        if (!SharedConstants.DEBUG_DISABLE_CARVERS) {
            BiomeManager biomemanager1 = biomeManager.withDifferentSource((j, k, l) -> {
                return this.biomeSource.getNoiseBiome(j, k, l, randomState.sampler());
            });
            WorldgenRandom worldgenrandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
            int j = 8;
            ChunkPos chunkpos = chunk.getPos();
            NoiseChunk noisechunk = chunk.getOrCreateNoiseChunk((chunkaccess1) -> {
                return this.createNoiseChunk(chunkaccess1, structureManager, Blender.of(region), randomState);
            });
            Aquifer aquifer = noisechunk.aquifer();
            CarvingContext carvingcontext = new CarvingContext(this, region.registryAccess(), chunk.getHeightAccessorForGeneration(), noisechunk, randomState, ((NoiseGeneratorSettings) this.settings.value()).surfaceRule());
            CarvingMask carvingmask = ((ProtoChunk) chunk).getOrCreateCarvingMask();

            for (int k = -8; k <= 8; ++k) {
                for (int l = -8; l <= 8; ++l) {
                    ChunkPos chunkpos1 = new ChunkPos(chunkpos.x + k, chunkpos.z + l);
                    ChunkAccess chunkaccess1 = region.getChunk(chunkpos1.x, chunkpos1.z);
                    BiomeGenerationSettings biomegenerationsettings = chunkaccess1.carverBiome(() -> {
                        return this.getBiomeGenerationSettings(this.biomeSource.getNoiseBiome(QuartPos.fromBlock(chunkpos1.getMinBlockX()), 0, QuartPos.fromBlock(chunkpos1.getMinBlockZ()), randomState.sampler()));
                    });
                    Iterable<Holder<ConfiguredWorldCarver<?>>> iterable = biomegenerationsettings.getCarvers();
                    int i1 = 0;

                    for (Holder<ConfiguredWorldCarver<?>> holder : iterable) {
                        ConfiguredWorldCarver<?> configuredworldcarver = holder.value();

                        worldgenrandom.setLargeFeatureSeed(seed + (long) i1, chunkpos1.x, chunkpos1.z);
                        if (configuredworldcarver.isStartChunk(worldgenrandom)) {
                            Objects.requireNonNull(biomemanager1);
                            configuredworldcarver.carve(carvingcontext, chunk, biomemanager1::getBiome, worldgenrandom, aquifer, chunkpos1, carvingmask);
                        }

                        ++i1;
                    }
                }
            }

        }
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess centerChunk) {
        NoiseSettings noisesettings = ((NoiseGeneratorSettings) this.settings.value()).noiseSettings().clampToHeightAccessor(centerChunk.getHeightAccessorForGeneration());
        int i = noisesettings.minY();
        int j = Mth.floorDiv(i, noisesettings.getCellHeight());
        int k = Mth.floorDiv(noisesettings.height(), noisesettings.getCellHeight());

        return k <= 0 ? CompletableFuture.completedFuture(centerChunk) : CompletableFuture.supplyAsync(() -> {
            int l = centerChunk.getSectionIndex(k * noisesettings.getCellHeight() - 1 + i);
            int i1 = centerChunk.getSectionIndex(i);
            Set<LevelChunkSection> set = Sets.newHashSet();

            for (int j1 = l; j1 >= i1; --j1) {
                LevelChunkSection levelchunksection = centerChunk.getSection(j1);

                levelchunksection.acquire();
                set.add(levelchunksection);
            }

            ChunkAccess chunkaccess1;

            try {
                chunkaccess1 = this.doFill(blender, structureManager, randomState, centerChunk, j, k);
            } finally {
                for (LevelChunkSection levelchunksection1 : set) {
                    levelchunksection1.release();
                }

            }

            return chunkaccess1;
        }, Util.backgroundExecutor().forName("wgen_fill_noise"));
    }

    private ChunkAccess doFill(Blender blender, StructureManager structureManager, RandomState randomState, ChunkAccess centerChunk, int cellMinY, int cellCountY) {
        NoiseChunk noisechunk = centerChunk.getOrCreateNoiseChunk((chunkaccess1) -> {
            return this.createNoiseChunk(chunkaccess1, structureManager, blender, randomState);
        });
        Heightmap heightmap = centerChunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap heightmap1 = centerChunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        ChunkPos chunkpos = centerChunk.getPos();
        int k = chunkpos.getMinBlockX();
        int l = chunkpos.getMinBlockZ();
        Aquifer aquifer = noisechunk.aquifer();

        noisechunk.initializeForFirstCellX();
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        int i1 = noisechunk.cellWidth();
        int j1 = noisechunk.cellHeight();
        int k1 = 16 / i1;
        int l1 = 16 / i1;

        for (int i2 = 0; i2 < k1; ++i2) {
            noisechunk.advanceCellX(i2);

            for (int j2 = 0; j2 < l1; ++j2) {
                int k2 = centerChunk.getSectionsCount() - 1;
                LevelChunkSection levelchunksection = centerChunk.getSection(k2);

                for (int l2 = cellCountY - 1; l2 >= 0; --l2) {
                    noisechunk.selectCellYZ(l2, j2);

                    for (int i3 = j1 - 1; i3 >= 0; --i3) {
                        int j3 = (cellMinY + l2) * j1 + i3;
                        int k3 = j3 & 15;
                        int l3 = centerChunk.getSectionIndex(j3);

                        if (k2 != l3) {
                            k2 = l3;
                            levelchunksection = centerChunk.getSection(l3);
                        }

                        double d0 = (double) i3 / (double) j1;

                        noisechunk.updateForY(j3, d0);

                        for (int i4 = 0; i4 < i1; ++i4) {
                            int j4 = k + i2 * i1 + i4;
                            int k4 = j4 & 15;
                            double d1 = (double) i4 / (double) i1;

                            noisechunk.updateForX(j4, d1);

                            for (int l4 = 0; l4 < i1; ++l4) {
                                int i5 = l + j2 * i1 + l4;
                                int j5 = i5 & 15;
                                double d2 = (double) l4 / (double) i1;

                                noisechunk.updateForZ(i5, d2);
                                BlockState blockstate = noisechunk.getInterpolatedState();

                                if (blockstate == null) {
                                    blockstate = ((NoiseGeneratorSettings) this.settings.value()).defaultBlock();
                                }

                                blockstate = this.debugPreliminarySurfaceLevel(noisechunk, j4, j3, i5, blockstate);
                                if (blockstate != NoiseBasedChunkGenerator.AIR && !SharedConstants.debugVoidTerrain(centerChunk.getPos())) {
                                    levelchunksection.setBlockState(k4, k3, j5, blockstate, false);
                                    heightmap.update(k4, j3, j5, blockstate);
                                    heightmap1.update(k4, j3, j5, blockstate);
                                    if (aquifer.shouldScheduleFluidUpdate() && !blockstate.getFluidState().isEmpty()) {
                                        blockpos_mutableblockpos.set(j4, j3, i5);
                                        centerChunk.markPosForPostprocessing(blockpos_mutableblockpos);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            noisechunk.swapSlices();
        }

        noisechunk.stopInterpolation();
        return centerChunk;
    }

    private BlockState debugPreliminarySurfaceLevel(NoiseChunk noiseChunk, int posX, int posY, int posZ, BlockState state) {
        if (SharedConstants.DEBUG_AQUIFERS && posZ >= 0 && posZ % 4 == 0) {
            int l = noiseChunk.preliminarySurfaceLevel(posX, posZ);
            int i1 = l + 8;

            if (posY == i1) {
                state = i1 < this.getSeaLevel() ? Blocks.SLIME_BLOCK.defaultBlockState() : Blocks.HONEY_BLOCK.defaultBlockState();
            }
        }

        return state;
    }

    @Override
    public int getGenDepth() {
        return ((NoiseGeneratorSettings) this.settings.value()).noiseSettings().height();
    }

    @Override
    public int getSeaLevel() {
        return ((NoiseGeneratorSettings) this.settings.value()).seaLevel();
    }

    @Override
    public int getMinY() {
        return ((NoiseGeneratorSettings) this.settings.value()).noiseSettings().minY();
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
        if (!((NoiseGeneratorSettings) this.settings.value()).disableMobGeneration()) {
            ChunkPos chunkpos = worldGenRegion.getCenter();
            Holder<Biome> holder = worldGenRegion.getBiome(chunkpos.getWorldPosition().atY(worldGenRegion.getMaxY()));
            WorldgenRandom worldgenrandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));

            worldgenrandom.setDecorationSeed(worldGenRegion.getSeed(), chunkpos.getMinBlockX(), chunkpos.getMinBlockZ());
            NaturalSpawner.spawnMobsForChunkGeneration(worldGenRegion, holder, chunkpos, worldgenrandom);
        }
    }
}

package net.minecraft.world.level.chunk;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Util;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.feature.FeatureCountTracker;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;

public abstract class ChunkGenerator {

    public static final Codec<ChunkGenerator> CODEC = BuiltInRegistries.CHUNK_GENERATOR.byNameCodec().dispatchStable(ChunkGenerator::codec, Function.identity());
    protected final BiomeSource biomeSource;
    private final Supplier<List<FeatureSorter.StepFeatureData>> featuresPerStep;
    public final Function<Holder<Biome>, BiomeGenerationSettings> generationSettingsGetter;

    public ChunkGenerator(BiomeSource biomeSource) {
        this(biomeSource, (holder) -> {
            return ((Biome) holder.value()).getGenerationSettings();
        });
    }

    public ChunkGenerator(BiomeSource biomeSource, Function<Holder<Biome>, BiomeGenerationSettings> generationSettingsGetter) {
        this.biomeSource = biomeSource;
        this.generationSettingsGetter = generationSettingsGetter;
        this.featuresPerStep = Suppliers.memoize(() -> {
            return FeatureSorter.buildFeaturesPerStep(List.copyOf(biomeSource.possibleBiomes()), (holder) -> {
                return ((BiomeGenerationSettings) generationSettingsGetter.apply(holder)).features();
            }, true);
        });
    }

    public void validate() {
        this.featuresPerStep.get();
    }

    protected abstract MapCodec<? extends ChunkGenerator> codec();

    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long legacyLevelSeed) {
        return ChunkGeneratorStructureState.createForNormal(randomState, legacyLevelSeed, this.biomeSource, structureSets);
    }

    public Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> getTypeNameForDataFixer() {
        return BuiltInRegistries.CHUNK_GENERATOR.getResourceKey(this.codec());
    }

    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess protoChunk) {
        return CompletableFuture.supplyAsync(() -> {
            protoChunk.fillBiomesFromNoise(this.biomeSource, randomState.sampler());
            return protoChunk;
        }, Util.backgroundExecutor().forName("init_biomes"));
    }

    public abstract void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk);

    public @Nullable Pair<BlockPos, Holder<Structure>> findNearestMapStructure(ServerLevel level, HolderSet<Structure> wantedStructures, BlockPos pos, int maxSearchRadius, boolean createReference) {
        if (SharedConstants.DEBUG_DISABLE_FEATURES) {
            return null;
        } else {
            ChunkGeneratorStructureState chunkgeneratorstructurestate = level.getChunkSource().getGeneratorState();
            Map<StructurePlacement, Set<Holder<Structure>>> map = new Object2ObjectArrayMap();

            for (Holder<Structure> holder : wantedStructures) {
                for (StructurePlacement structureplacement : chunkgeneratorstructurestate.getPlacementsForStructure(holder)) {
                    ((Set) map.computeIfAbsent(structureplacement, (structureplacement1) -> {
                        return new ObjectArraySet();
                    })).add(holder);
                }
            }

            if (map.isEmpty()) {
                return null;
            } else {
                Pair<BlockPos, Holder<Structure>> pair = null;
                double d0 = Double.MAX_VALUE;
                StructureManager structuremanager = level.structureManager();
                List<Map.Entry<StructurePlacement, Set<Holder<Structure>>>> list = new ArrayList(map.size());

                for (Map.Entry<StructurePlacement, Set<Holder<Structure>>> map_entry : map.entrySet()) {
                    StructurePlacement structureplacement1 = (StructurePlacement) map_entry.getKey();

                    if (structureplacement1 instanceof ConcentricRingsStructurePlacement) {
                        ConcentricRingsStructurePlacement concentricringsstructureplacement = (ConcentricRingsStructurePlacement) structureplacement1;
                        Pair<BlockPos, Holder<Structure>> pair1 = this.getNearestGeneratedStructure((Set) map_entry.getValue(), level, structuremanager, pos, createReference, concentricringsstructureplacement);

                        if (pair1 != null) {
                            BlockPos blockpos1 = (BlockPos) pair1.getFirst();
                            double d1 = pos.distSqr(blockpos1);

                            if (d1 < d0) {
                                d0 = d1;
                                pair = pair1;
                            }
                        }
                    } else if (structureplacement1 instanceof RandomSpreadStructurePlacement) {
                        list.add(map_entry);
                    }
                }

                if (!list.isEmpty()) {
                    int j = SectionPos.blockToSectionCoord(pos.getX());
                    int k = SectionPos.blockToSectionCoord(pos.getZ());

                    for (int l = 0; l <= maxSearchRadius; ++l) {
                        boolean flag1 = false;

                        for (Map.Entry<StructurePlacement, Set<Holder<Structure>>> map_entry1 : list) {
                            RandomSpreadStructurePlacement randomspreadstructureplacement = (RandomSpreadStructurePlacement) map_entry1.getKey();
                            Pair<BlockPos, Holder<Structure>> pair2 = getNearestGeneratedStructure((Set) map_entry1.getValue(), level, structuremanager, j, k, l, createReference, chunkgeneratorstructurestate.getLevelSeed(), randomspreadstructureplacement);

                            if (pair2 != null) {
                                flag1 = true;
                                double d2 = pos.distSqr((Vec3i) pair2.getFirst());

                                if (d2 < d0) {
                                    d0 = d2;
                                    pair = pair2;
                                }
                            }
                        }

                        if (flag1) {
                            return pair;
                        }
                    }
                }

                return pair;
            }
        }
    }

    private @Nullable Pair<BlockPos, Holder<Structure>> getNearestGeneratedStructure(Set<Holder<Structure>> structures, ServerLevel level, StructureManager structureManager, BlockPos pos, boolean createReference, ConcentricRingsStructurePlacement rings) {
        List<ChunkPos> list = level.getChunkSource().getGeneratorState().getRingPositionsFor(rings);

        if (list == null) {
            throw new IllegalStateException("Somehow tried to find structures for a placement that doesn't exist");
        } else {
            Pair<BlockPos, Holder<Structure>> pair = null;
            double d0 = Double.MAX_VALUE;
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

            for (ChunkPos chunkpos : list) {
                blockpos_mutableblockpos.set(SectionPos.sectionToBlockCoord(chunkpos.x, 8), 32, SectionPos.sectionToBlockCoord(chunkpos.z, 8));
                double d1 = blockpos_mutableblockpos.distSqr(pos);
                boolean flag1 = pair == null || d1 < d0;

                if (flag1) {
                    Pair<BlockPos, Holder<Structure>> pair1 = getStructureGeneratingAt(structures, level, structureManager, createReference, rings, chunkpos);

                    if (pair1 != null) {
                        pair = pair1;
                        d0 = d1;
                    }
                }
            }

            return pair;
        }
    }

    private static @Nullable Pair<BlockPos, Holder<Structure>> getNearestGeneratedStructure(Set<Holder<Structure>> structures, LevelReader level, StructureManager structureManager, int chunkOriginX, int chunkOriginZ, int radius, boolean createReference, long seed, RandomSpreadStructurePlacement config) {
        int i1 = config.spacing();

        for (int j1 = -radius; j1 <= radius; ++j1) {
            boolean flag1 = j1 == -radius || j1 == radius;

            for (int k1 = -radius; k1 <= radius; ++k1) {
                boolean flag2 = k1 == -radius || k1 == radius;

                if (flag1 || flag2) {
                    int l1 = chunkOriginX + i1 * j1;
                    int i2 = chunkOriginZ + i1 * k1;
                    ChunkPos chunkpos = config.getPotentialStructureChunk(seed, l1, i2);
                    Pair<BlockPos, Holder<Structure>> pair = getStructureGeneratingAt(structures, level, structureManager, createReference, config, chunkpos);

                    if (pair != null) {
                        return pair;
                    }
                }
            }
        }

        return null;
    }

    private static @Nullable Pair<BlockPos, Holder<Structure>> getStructureGeneratingAt(Set<Holder<Structure>> structures, LevelReader level, StructureManager structureManager, boolean createReference, StructurePlacement config, ChunkPos chunkTarget) {
        for (Holder<Structure> holder : structures) {
            StructureCheckResult structurecheckresult = structureManager.checkStructurePresence(chunkTarget, holder.value(), config, createReference);

            if (structurecheckresult != StructureCheckResult.START_NOT_PRESENT) {
                if (!createReference && structurecheckresult == StructureCheckResult.START_PRESENT) {
                    return Pair.of(config.getLocatePos(chunkTarget), holder);
                }

                ChunkAccess chunkaccess = level.getChunk(chunkTarget.x, chunkTarget.z, ChunkStatus.STRUCTURE_STARTS);
                StructureStart structurestart = structureManager.getStartForStructure(SectionPos.bottomOf(chunkaccess), holder.value(), chunkaccess);

                if (structurestart != null && structurestart.isValid() && (!createReference || tryAddReference(structureManager, structurestart))) {
                    return Pair.of(config.getLocatePos(structurestart.getChunkPos()), holder);
                }
            }
        }

        return null;
    }

    private static boolean tryAddReference(StructureManager manager, StructureStart start) {
        if (start.canBeReferenced()) {
            manager.addReference(start);
            return true;
        } else {
            return false;
        }
    }

    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        ChunkPos chunkpos = chunk.getPos();

        if (!SharedConstants.debugVoidTerrain(chunkpos)) {
            SectionPos sectionpos = SectionPos.of(chunkpos, level.getMinSectionY());
            BlockPos blockpos = sectionpos.origin();
            Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            Map<Integer, List<Structure>> map = (Map) registry.stream().collect(Collectors.groupingBy((structure) -> {
                return structure.step().ordinal();
            }));
            List<FeatureSorter.StepFeatureData> list = (List) this.featuresPerStep.get();
            WorldgenRandom worldgenrandom = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
            long i = worldgenrandom.setDecorationSeed(level.getSeed(), blockpos.getX(), blockpos.getZ());
            Set<Holder<Biome>> set = new ObjectArraySet();

            ChunkPos.rangeClosed(sectionpos.chunk(), 1).forEach((chunkpos1) -> {
                ChunkAccess chunkaccess1 = level.getChunk(chunkpos1.x, chunkpos1.z);

                for (LevelChunkSection levelchunksection : chunkaccess1.getSections()) {
                    PalettedContainerRO palettedcontainerro = levelchunksection.getBiomes();

                    Objects.requireNonNull(set);
                    palettedcontainerro.getAll(set::add);
                }

            });
            set.retainAll(this.biomeSource.possibleBiomes());
            int j = list.size();

            try {
                Registry<PlacedFeature> registry1 = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
                int k = Math.max(GenerationStep.Decoration.values().length, j);

                for (int l = 0; l < k; ++l) {
                    int i1 = 0;

                    if (structureManager.shouldGenerateStructures()) {
                        for (Structure structure : (List) map.getOrDefault(l, Collections.emptyList())) {
                            worldgenrandom.setFeatureSeed(i, i1, l);
                            Supplier<String> supplier = () -> {
                                Optional optional = registry.getResourceKey(structure).map(Object::toString);

                                Objects.requireNonNull(structure);
                                return (String) optional.orElseGet(structure::toString);
                            };

                            try {
                                level.setCurrentlyGenerating(supplier);
                                structureManager.startsForStructure(sectionpos, structure).forEach((structurestart) -> {
                                    structurestart.placeInChunk(level, structureManager, this, worldgenrandom, getWritableArea(chunk), chunkpos);
                                });
                            } catch (Exception exception) {
                                CrashReport crashreport = CrashReport.forThrowable(exception, "Feature placement");
                                CrashReportCategory crashreportcategory = crashreport.addCategory("Feature");

                                Objects.requireNonNull(supplier);
                                crashreportcategory.setDetail("Description", supplier::get);
                                throw new ReportedException(crashreport);
                            }

                            ++i1;
                        }
                    }

                    if (l < j) {
                        IntSet intset = new IntArraySet();

                        for (Holder<Biome> holder : set) {
                            List<HolderSet<PlacedFeature>> list1 = ((BiomeGenerationSettings) this.generationSettingsGetter.apply(holder)).features();

                            if (l < list1.size()) {
                                HolderSet<PlacedFeature> holderset = (HolderSet) list1.get(l);
                                FeatureSorter.StepFeatureData featuresorter_stepfeaturedata = (FeatureSorter.StepFeatureData) list.get(l);

                                holderset.stream().map(Holder::value).forEach((placedfeature) -> {
                                    intset.add(featuresorter_stepfeaturedata.indexMapping().applyAsInt(placedfeature));
                                });
                            }
                        }

                        int j1 = intset.size();
                        int[] aint = intset.toIntArray();

                        Arrays.sort(aint);
                        FeatureSorter.StepFeatureData featuresorter_stepfeaturedata1 = (FeatureSorter.StepFeatureData) list.get(l);

                        for (int k1 = 0; k1 < j1; ++k1) {
                            int l1 = aint[k1];
                            PlacedFeature placedfeature = (PlacedFeature) featuresorter_stepfeaturedata1.features().get(l1);
                            Supplier<String> supplier1 = () -> {
                                Optional optional = registry1.getResourceKey(placedfeature).map(Object::toString);

                                Objects.requireNonNull(placedfeature);
                                return (String) optional.orElseGet(placedfeature::toString);
                            };

                            worldgenrandom.setFeatureSeed(i, l1, l);

                            try {
                                level.setCurrentlyGenerating(supplier1);
                                placedfeature.placeWithBiomeCheck(level, this, worldgenrandom, blockpos);
                            } catch (Exception exception1) {
                                CrashReport crashreport1 = CrashReport.forThrowable(exception1, "Feature placement");
                                CrashReportCategory crashreportcategory1 = crashreport1.addCategory("Feature");

                                Objects.requireNonNull(supplier1);
                                crashreportcategory1.setDetail("Description", supplier1::get);
                                throw new ReportedException(crashreport1);
                            }
                        }
                    }
                }

                level.setCurrentlyGenerating((Supplier) null);
                if (SharedConstants.DEBUG_FEATURE_COUNT) {
                    FeatureCountTracker.chunkDecorated(level.getLevel());
                }

            } catch (Exception exception2) {
                CrashReport crashreport2 = CrashReport.forThrowable(exception2, "Biome decoration");

                crashreport2.addCategory("Generation").setDetail("CenterX", chunkpos.x).setDetail("CenterZ", chunkpos.z).setDetail("Decoration Seed", i);
                throw new ReportedException(crashreport2);
            }
        }
    }

    private static BoundingBox getWritableArea(ChunkAccess chunk) {
        ChunkPos chunkpos = chunk.getPos();
        int i = chunkpos.getMinBlockX();
        int j = chunkpos.getMinBlockZ();
        LevelHeightAccessor levelheightaccessor = chunk.getHeightAccessorForGeneration();
        int k = levelheightaccessor.getMinY() + 1;
        int l = levelheightaccessor.getMaxY();

        return new BoundingBox(i, k, j, i + 15, l, j + 15);
    }

    public abstract void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess protoChunk);

    public abstract void spawnOriginalMobs(WorldGenRegion worldGenRegion);

    public int getSpawnHeight(LevelHeightAccessor heightAccessor) {
        return 64;
    }

    public BiomeSource getBiomeSource() {
        return this.biomeSource;
    }

    public abstract int getGenDepth();

    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome, StructureManager structureManager, MobCategory mobCategory, BlockPos pos) {
        Map<Structure, LongSet> map = structureManager.getAllStructuresAt(pos);

        for (Map.Entry<Structure, LongSet> map_entry : map.entrySet()) {
            Structure structure = (Structure) map_entry.getKey();
            StructureSpawnOverride structurespawnoverride = (StructureSpawnOverride) structure.spawnOverrides().get(mobCategory);

            if (structurespawnoverride != null) {
                MutableBoolean mutableboolean = new MutableBoolean(false);
                Predicate<StructureStart> predicate = structurespawnoverride.boundingBox() == StructureSpawnOverride.BoundingBoxType.PIECE ? (structurestart) -> {
                    return structureManager.structureHasPieceAt(pos, structurestart);
                } : (structurestart) -> {
                    return structurestart.getBoundingBox().isInside(pos);
                };

                structureManager.fillStartsForStructure(structure, (LongSet) map_entry.getValue(), (structurestart) -> {
                    if (mutableboolean.isFalse() && predicate.test(structurestart)) {
                        mutableboolean.setTrue();
                    }

                });
                if (mutableboolean.isTrue()) {
                    return structurespawnoverride.spawns();
                }
            }
        }

        return ((Biome) biome.value()).getMobSettings().getMobs(mobCategory);
    }

    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState state, StructureManager structureManager, ChunkAccess centerChunk, StructureTemplateManager structureTemplateManager, ResourceKey<Level> level) {
        if (!SharedConstants.DEBUG_DISABLE_STRUCTURES) {
            ChunkPos chunkpos = centerChunk.getPos();
            SectionPos sectionpos = SectionPos.bottomOf(centerChunk);
            RandomState randomstate = state.randomState();

            state.possibleStructureSets().forEach((holder) -> {
                StructurePlacement structureplacement = ((StructureSet) holder.value()).placement();
                List<StructureSet.StructureSelectionEntry> list = ((StructureSet) holder.value()).structures();

                for (StructureSet.StructureSelectionEntry structureset_structureselectionentry : list) {
                    StructureStart structurestart = structureManager.getStartForStructure(sectionpos, (Structure) structureset_structureselectionentry.structure().value(), centerChunk);

                    if (structurestart != null && structurestart.isValid()) {
                        return;
                    }
                }

                if (structureplacement.isStructureChunk(state, chunkpos.x, chunkpos.z)) {
                    if (list.size() == 1) {
                        this.tryGenerateStructure((StructureSet.StructureSelectionEntry) list.get(0), structureManager, registryAccess, randomstate, structureTemplateManager, state.getLevelSeed(), centerChunk, chunkpos, sectionpos, level);
                    } else {
                        ArrayList<StructureSet.StructureSelectionEntry> arraylist = new ArrayList(list.size());

                        arraylist.addAll(list);
                        WorldgenRandom worldgenrandom = new WorldgenRandom(new LegacyRandomSource(0L));

                        worldgenrandom.setLargeFeatureSeed(state.getLevelSeed(), chunkpos.x, chunkpos.z);
                        int i = 0;

                        for (StructureSet.StructureSelectionEntry structureset_structureselectionentry1 : arraylist) {
                            i += structureset_structureselectionentry1.weight();
                        }

                        while (!arraylist.isEmpty()) {
                            int j = worldgenrandom.nextInt(i);
                            int k = 0;

                            for (StructureSet.StructureSelectionEntry structureset_structureselectionentry2 : arraylist) {
                                j -= structureset_structureselectionentry2.weight();
                                if (j < 0) {
                                    break;
                                }

                                ++k;
                            }

                            StructureSet.StructureSelectionEntry structureset_structureselectionentry3 = (StructureSet.StructureSelectionEntry) arraylist.get(k);

                            if (this.tryGenerateStructure(structureset_structureselectionentry3, structureManager, registryAccess, randomstate, structureTemplateManager, state.getLevelSeed(), centerChunk, chunkpos, sectionpos, level)) {
                                return;
                            }

                            arraylist.remove(k);
                            i -= structureset_structureselectionentry3.weight();
                        }

                    }
                }
            });
        }
    }

    private boolean tryGenerateStructure(StructureSet.StructureSelectionEntry selected, StructureManager structureManager, RegistryAccess registryAccess, RandomState randomState, StructureTemplateManager structureTemplateManager, long seed, ChunkAccess centerChunk, ChunkPos sourceChunkPos, SectionPos sectionPos, ResourceKey<Level> level) {
        Structure structure = (Structure) selected.structure().value();
        int j = fetchReferences(structureManager, centerChunk, sectionPos, structure);
        HolderSet<Biome> holderset = structure.biomes();

        Objects.requireNonNull(holderset);
        Predicate<Holder<Biome>> predicate = holderset::contains;
        StructureStart structurestart = structure.generate(selected.structure(), level, registryAccess, this, this.biomeSource, randomState, structureTemplateManager, seed, sourceChunkPos, j, centerChunk, predicate);

        if (structurestart.isValid()) {
            structureManager.setStartForStructure(sectionPos, structure, structurestart, centerChunk);
            return true;
        } else {
            return false;
        }
    }

    private static int fetchReferences(StructureManager structureManager, ChunkAccess centerChunk, SectionPos sectionPos, Structure structure) {
        StructureStart structurestart = structureManager.getStartForStructure(sectionPos, structure, centerChunk);

        return structurestart != null ? structurestart.getReferences() : 0;
    }

    public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess centerChunk) {
        int i = 8;
        ChunkPos chunkpos = centerChunk.getPos();
        int j = chunkpos.x;
        int k = chunkpos.z;
        int l = chunkpos.getMinBlockX();
        int i1 = chunkpos.getMinBlockZ();
        SectionPos sectionpos = SectionPos.bottomOf(centerChunk);

        for (int j1 = j - 8; j1 <= j + 8; ++j1) {
            for (int k1 = k - 8; k1 <= k + 8; ++k1) {
                long l1 = ChunkPos.asLong(j1, k1);

                for (StructureStart structurestart : level.getChunk(j1, k1).getAllStarts().values()) {
                    try {
                        if (structurestart.isValid() && structurestart.getBoundingBox().intersects(l, i1, l + 15, i1 + 15)) {
                            structureManager.addReferenceForStructure(sectionpos, structurestart.getStructure(), l1, centerChunk);
                        }
                    } catch (Exception exception) {
                        CrashReport crashreport = CrashReport.forThrowable(exception, "Generating structure reference");
                        CrashReportCategory crashreportcategory = crashreport.addCategory("Structure");
                        Optional<? extends Registry<Structure>> optional = level.registryAccess().lookup(Registries.STRUCTURE);

                        crashreportcategory.setDetail("Id", () -> {
                            return (String) optional.map((registry) -> {
                                return registry.getKey(structurestart.getStructure()).toString();
                            }).orElse("UNKNOWN");
                        });
                        crashreportcategory.setDetail("Name", () -> {
                            return BuiltInRegistries.STRUCTURE_TYPE.getKey(structurestart.getStructure().type()).toString();
                        });
                        crashreportcategory.setDetail("Class", () -> {
                            return structurestart.getStructure().getClass().getCanonicalName();
                        });
                        throw new ReportedException(crashreport);
                    }
                }
            }
        }

    }

    public abstract CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess centerChunk);

    public abstract int getSeaLevel();

    public abstract int getMinY();

    public abstract int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState);

    public abstract NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState);

    public int getFirstFreeHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        return this.getBaseHeight(x, z, type, heightAccessor, randomState);
    }

    public int getFirstOccupiedHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        return this.getBaseHeight(x, z, type, heightAccessor, randomState) - 1;
    }

    public abstract void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos);

    /** @deprecated */
    @Deprecated
    public BiomeGenerationSettings getBiomeGenerationSettings(Holder<Biome> biome) {
        return (BiomeGenerationSettings) this.generationSettingsGetter.apply(biome);
    }
}

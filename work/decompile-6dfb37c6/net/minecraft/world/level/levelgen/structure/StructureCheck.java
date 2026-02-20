package net.minecraft.world.level.levelgen.structure;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class StructureCheck {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NO_STRUCTURE = -1;
    private final ChunkScanAccess storageAccess;
    private final RegistryAccess registryAccess;
    private final StructureTemplateManager structureTemplateManager;
    private final ResourceKey<Level> dimension;
    private final ChunkGenerator chunkGenerator;
    private final RandomState randomState;
    private final LevelHeightAccessor heightAccessor;
    private final BiomeSource biomeSource;
    private final long seed;
    private final DataFixer fixerUpper;
    private final Long2ObjectMap<Object2IntMap<Structure>> loadedChunks = new Long2ObjectOpenHashMap();
    private final Map<Structure, Long2BooleanMap> featureChecks = new HashMap();

    public StructureCheck(ChunkScanAccess storageAccess, RegistryAccess registryAccess, StructureTemplateManager structureTemplateManager, ResourceKey<Level> dimension, ChunkGenerator chunkGenerator, RandomState randomState, LevelHeightAccessor heightAccessor, BiomeSource biomeSource, long seed, DataFixer fixerUpper) {
        this.storageAccess = storageAccess;
        this.registryAccess = registryAccess;
        this.structureTemplateManager = structureTemplateManager;
        this.dimension = dimension;
        this.chunkGenerator = chunkGenerator;
        this.randomState = randomState;
        this.heightAccessor = heightAccessor;
        this.biomeSource = biomeSource;
        this.seed = seed;
        this.fixerUpper = fixerUpper;
    }

    public StructureCheckResult checkStart(ChunkPos pos, Structure structure, StructurePlacement placement, boolean requireUnreferenced) {
        long i = pos.toLong();
        Object2IntMap<Structure> object2intmap = (Object2IntMap) this.loadedChunks.get(i);

        if (object2intmap != null) {
            return this.checkStructureInfo(object2intmap, structure, requireUnreferenced);
        } else {
            StructureCheckResult structurecheckresult = this.tryLoadFromStorage(pos, structure, requireUnreferenced, i);

            if (structurecheckresult != null) {
                return structurecheckresult;
            } else if (!placement.applyAdditionalChunkRestrictions(pos.x, pos.z, this.seed)) {
                return StructureCheckResult.START_NOT_PRESENT;
            } else {
                boolean flag1 = ((Long2BooleanMap) this.featureChecks.computeIfAbsent(structure, (structure1) -> {
                    return new Long2BooleanOpenHashMap();
                })).computeIfAbsent(i, (j) -> {
                    return this.canCreateStructure(pos, structure);
                });

                return !flag1 ? StructureCheckResult.START_NOT_PRESENT : StructureCheckResult.CHUNK_LOAD_NEEDED;
            }
        }
    }

    private boolean canCreateStructure(ChunkPos pos, Structure structure) {
        RegistryAccess registryaccess = this.registryAccess;
        ChunkGenerator chunkgenerator = this.chunkGenerator;
        BiomeSource biomesource = this.biomeSource;
        RandomState randomstate = this.randomState;
        StructureTemplateManager structuretemplatemanager = this.structureTemplateManager;
        long i = this.seed;
        LevelHeightAccessor levelheightaccessor = this.heightAccessor;
        HolderSet holderset = structure.biomes();

        Objects.requireNonNull(holderset);
        return structure.findValidGenerationPoint(new Structure.GenerationContext(registryaccess, chunkgenerator, biomesource, randomstate, structuretemplatemanager, i, pos, levelheightaccessor, holderset::contains)).isPresent();
    }

    private @Nullable StructureCheckResult tryLoadFromStorage(ChunkPos pos, Structure structure, boolean requireUnreferenced, long posKey) {
        CollectFields collectfields = new CollectFields(new FieldSelector[]{new FieldSelector(IntTag.TYPE, "DataVersion"), new FieldSelector("Level", "Structures", CompoundTag.TYPE, "Starts"), new FieldSelector("structures", CompoundTag.TYPE, "starts")});

        try {
            this.storageAccess.scanChunk(pos, collectfields).join();
        } catch (Exception exception) {
            StructureCheck.LOGGER.warn("Failed to read chunk {}", pos, exception);
            return StructureCheckResult.CHUNK_LOAD_NEEDED;
        }

        Tag tag = collectfields.getResult();

        if (!(tag instanceof CompoundTag compoundtag)) {
            return null;
        } else {
            int j = NbtUtils.getDataVersion(compoundtag);

            if (j <= 1493) {
                return StructureCheckResult.CHUNK_LOAD_NEEDED;
            } else {
                SimpleRegionStorage.injectDatafixingContext(compoundtag, ChunkMap.getChunkDataFixContextTag(this.dimension, this.chunkGenerator.getTypeNameForDataFixer()));

                CompoundTag compoundtag1;

                try {
                    compoundtag1 = DataFixTypes.CHUNK.updateToCurrentVersion(this.fixerUpper, compoundtag, j);
                } catch (Exception exception1) {
                    StructureCheck.LOGGER.warn("Failed to partially datafix chunk {}", pos, exception1);
                    return StructureCheckResult.CHUNK_LOAD_NEEDED;
                }

                Object2IntMap<Structure> object2intmap = this.loadStructures(compoundtag1);

                if (object2intmap == null) {
                    return null;
                } else {
                    this.storeFullResults(posKey, object2intmap);
                    return this.checkStructureInfo(object2intmap, structure, requireUnreferenced);
                }
            }
        }
    }

    private @Nullable Object2IntMap<Structure> loadStructures(CompoundTag chunkTag) {
        Optional<CompoundTag> optional = chunkTag.getCompound("structures").flatMap((compoundtag1) -> {
            return compoundtag1.getCompound("starts");
        });

        if (optional.isEmpty()) {
            return null;
        } else {
            CompoundTag compoundtag1 = (CompoundTag) optional.get();

            if (compoundtag1.isEmpty()) {
                return Object2IntMaps.emptyMap();
            } else {
                Object2IntMap<Structure> object2intmap = new Object2IntOpenHashMap();
                Registry<Structure> registry = this.registryAccess.lookupOrThrow(Registries.STRUCTURE);

                compoundtag1.forEach((s, tag) -> {
                    Identifier identifier = Identifier.tryParse(s);

                    if (identifier != null) {
                        Structure structure = (Structure) registry.getValue(identifier);

                        if (structure != null) {
                            tag.asCompound().ifPresent((compoundtag2) -> {
                                String s1 = compoundtag2.getStringOr("id", "");

                                if (!"INVALID".equals(s1)) {
                                    int i = compoundtag2.getIntOr("references", 0);

                                    object2intmap.put(structure, i);
                                }

                            });
                        }
                    }
                });
                return object2intmap;
            }
        }
    }

    private static Object2IntMap<Structure> deduplicateEmptyMap(Object2IntMap<Structure> map) {
        return map.isEmpty() ? Object2IntMaps.emptyMap() : map;
    }

    private StructureCheckResult checkStructureInfo(Object2IntMap<Structure> cachedResult, Structure structure, boolean requireUnreferenced) {
        int i = cachedResult.getOrDefault(structure, -1);

        return i == -1 || requireUnreferenced && i != 0 ? StructureCheckResult.START_NOT_PRESENT : StructureCheckResult.START_PRESENT;
    }

    public void onStructureLoad(ChunkPos pos, Map<Structure, StructureStart> starts) {
        long i = pos.toLong();
        Object2IntMap<Structure> object2intmap = new Object2IntOpenHashMap();

        starts.forEach((structure, structurestart) -> {
            if (structurestart.isValid()) {
                object2intmap.put(structure, structurestart.getReferences());
            }

        });
        this.storeFullResults(i, object2intmap);
    }

    private void storeFullResults(long posKey, Object2IntMap<Structure> starts) {
        this.loadedChunks.put(posKey, deduplicateEmptyMap(starts));
        this.featureChecks.values().forEach((long2booleanmap) -> {
            long2booleanmap.remove(posKey);
        });
    }

    public void incrementReference(ChunkPos chunkPos, Structure structure) {
        this.loadedChunks.compute(chunkPos.toLong(), (olong, object2intmap) -> {
            if (object2intmap == null || object2intmap.isEmpty()) {
                object2intmap = new Object2IntOpenHashMap();
            }

            object2intmap.computeInt(structure, (structure1, integer) -> {
                return integer == null ? 1 : integer + 1;
            });
            return object2intmap;
        });
    }
}

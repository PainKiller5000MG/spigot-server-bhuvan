package net.minecraft.world.level.levelgen.structure;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.LegacyTagFixer;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jspecify.annotations.Nullable;

public class LegacyStructureDataHandler implements LegacyTagFixer {

    public static final int LAST_MONOLYTH_STRUCTURE_DATA_VERSION = 1493;
    private static final Map<String, String> CURRENT_TO_LEGACY_MAP = (Map) Util.make(Maps.newHashMap(), (hashmap) -> {
        hashmap.put("Village", "Village");
        hashmap.put("Mineshaft", "Mineshaft");
        hashmap.put("Mansion", "Mansion");
        hashmap.put("Igloo", "Temple");
        hashmap.put("Desert_Pyramid", "Temple");
        hashmap.put("Jungle_Pyramid", "Temple");
        hashmap.put("Swamp_Hut", "Temple");
        hashmap.put("Stronghold", "Stronghold");
        hashmap.put("Monument", "Monument");
        hashmap.put("Fortress", "Fortress");
        hashmap.put("EndCity", "EndCity");
    });
    private static final Map<String, String> LEGACY_TO_CURRENT_MAP = (Map) Util.make(Maps.newHashMap(), (hashmap) -> {
        hashmap.put("Iglu", "Igloo");
        hashmap.put("TeDP", "Desert_Pyramid");
        hashmap.put("TeJP", "Jungle_Pyramid");
        hashmap.put("TeSH", "Swamp_Hut");
    });
    private static final Set<String> OLD_STRUCTURE_REGISTRY_KEYS = Set.of("pillager_outpost", "mineshaft", "mansion", "jungle_pyramid", "desert_pyramid", "igloo", "ruined_portal", "shipwreck", "swamp_hut", "stronghold", "monument", "ocean_ruin", "fortress", "endcity", "buried_treasure", "village", "nether_fossil", "bastion_remnant");
    private final boolean hasLegacyData;
    private final Map<String, Long2ObjectMap<CompoundTag>> dataMap = Maps.newHashMap();
    private final Map<String, StructureFeatureIndexSavedData> indexMap = Maps.newHashMap();
    private final @Nullable DimensionDataStorage dimensionDataStorage;
    private final List<String> legacyKeys;
    private final List<String> currentKeys;
    private final DataFixer dataFixer;
    private boolean cachesInitialized;

    public LegacyStructureDataHandler(@Nullable DimensionDataStorage dimensionDataStorage, List<String> legacyKeys, List<String> currentKeys, DataFixer dataFixer) {
        this.dimensionDataStorage = dimensionDataStorage;
        this.legacyKeys = legacyKeys;
        this.currentKeys = currentKeys;
        this.dataFixer = dataFixer;
        boolean flag = false;

        for (String s : this.currentKeys) {
            flag |= this.dataMap.get(s) != null;
        }

        this.hasLegacyData = flag;
    }

    @Override
    public void markChunkDone(ChunkPos pos) {
        long i = pos.toLong();

        for (String s : this.legacyKeys) {
            StructureFeatureIndexSavedData structurefeatureindexsaveddata = (StructureFeatureIndexSavedData) this.indexMap.get(s);

            if (structurefeatureindexsaveddata != null && structurefeatureindexsaveddata.hasUnhandledIndex(i)) {
                structurefeatureindexsaveddata.removeIndex(i);
            }
        }

    }

    @Override
    public int targetDataVersion() {
        return 1493;
    }

    @Override
    public CompoundTag applyFix(CompoundTag chunkTag) {
        if (!this.cachesInitialized && this.dimensionDataStorage != null) {
            this.populateCaches(this.dimensionDataStorage);
        }

        int i = NbtUtils.getDataVersion(chunkTag);

        if (i < 1493) {
            chunkTag = DataFixTypes.CHUNK.update(this.dataFixer, chunkTag, i, 1493);
            if ((Boolean) chunkTag.getCompound("Level").flatMap((compoundtag1) -> {
                return compoundtag1.getBoolean("hasLegacyStructureData");
            }).orElse(false)) {
                chunkTag = this.updateFromLegacy(chunkTag);
            }
        }

        return chunkTag;
    }

    private CompoundTag updateFromLegacy(CompoundTag tag) {
        CompoundTag compoundtag1 = tag.getCompoundOrEmpty("Level");
        ChunkPos chunkpos = new ChunkPos(compoundtag1.getIntOr("xPos", 0), compoundtag1.getIntOr("zPos", 0));

        if (this.isUnhandledStructureStart(chunkpos.x, chunkpos.z)) {
            tag = this.updateStructureStart(tag, chunkpos);
        }

        CompoundTag compoundtag2 = compoundtag1.getCompoundOrEmpty("Structures");
        CompoundTag compoundtag3 = compoundtag2.getCompoundOrEmpty("References");

        for (String s : this.currentKeys) {
            boolean flag = LegacyStructureDataHandler.OLD_STRUCTURE_REGISTRY_KEYS.contains(s.toLowerCase(Locale.ROOT));

            if (!compoundtag3.getLongArray(s).isPresent() && flag) {
                int i = 8;
                LongList longlist = new LongArrayList();

                for (int j = chunkpos.x - 8; j <= chunkpos.x + 8; ++j) {
                    for (int k = chunkpos.z - 8; k <= chunkpos.z + 8; ++k) {
                        if (this.hasLegacyStart(j, k, s)) {
                            longlist.add(ChunkPos.asLong(j, k));
                        }
                    }
                }

                compoundtag3.putLongArray(s, longlist.toLongArray());
            }
        }

        compoundtag2.put("References", compoundtag3);
        compoundtag1.put("Structures", compoundtag2);
        tag.put("Level", compoundtag1);
        return tag;
    }

    private boolean hasLegacyStart(int x, int z, String feature) {
        return !this.hasLegacyData ? false : this.dataMap.get(feature) != null && ((StructureFeatureIndexSavedData) this.indexMap.get(LegacyStructureDataHandler.CURRENT_TO_LEGACY_MAP.get(feature))).hasStartIndex(ChunkPos.asLong(x, z));
    }

    private boolean isUnhandledStructureStart(int x, int z) {
        if (!this.hasLegacyData) {
            return false;
        } else {
            for (String s : this.currentKeys) {
                if (this.dataMap.get(s) != null && ((StructureFeatureIndexSavedData) this.indexMap.get(LegacyStructureDataHandler.CURRENT_TO_LEGACY_MAP.get(s))).hasUnhandledIndex(ChunkPos.asLong(x, z))) {
                    return true;
                }
            }

            return false;
        }
    }

    private CompoundTag updateStructureStart(CompoundTag tag, ChunkPos pos) {
        CompoundTag compoundtag1 = tag.getCompoundOrEmpty("Level");
        CompoundTag compoundtag2 = compoundtag1.getCompoundOrEmpty("Structures");
        CompoundTag compoundtag3 = compoundtag2.getCompoundOrEmpty("Starts");

        for (String s : this.currentKeys) {
            Long2ObjectMap<CompoundTag> long2objectmap = (Long2ObjectMap) this.dataMap.get(s);

            if (long2objectmap != null) {
                long i = pos.toLong();

                if (((StructureFeatureIndexSavedData) this.indexMap.get(LegacyStructureDataHandler.CURRENT_TO_LEGACY_MAP.get(s))).hasUnhandledIndex(i)) {
                    CompoundTag compoundtag4 = (CompoundTag) long2objectmap.get(i);

                    if (compoundtag4 != null) {
                        compoundtag3.put(s, compoundtag4);
                    }
                }
            }
        }

        compoundtag2.put("Starts", compoundtag3);
        compoundtag1.put("Structures", compoundtag2);
        tag.put("Level", compoundtag1);
        return tag;
    }

    private synchronized void populateCaches(DimensionDataStorage dimensionDataStorage) {
        if (!this.cachesInitialized) {
            for (String s : this.legacyKeys) {
                CompoundTag compoundtag = new CompoundTag();

                try {
                    compoundtag = dimensionDataStorage.readTagFromDisk(s, DataFixTypes.SAVED_DATA_STRUCTURE_FEATURE_INDICES, 1493).getCompoundOrEmpty("data").getCompoundOrEmpty("Features");
                    if (compoundtag.isEmpty()) {
                        continue;
                    }
                } catch (IOException ioexception) {
                    ;
                }

                compoundtag.forEach((s1, tag) -> {
                    if (tag instanceof CompoundTag compoundtag1) {
                        long i = ChunkPos.asLong(compoundtag1.getIntOr("ChunkX", 0), compoundtag1.getIntOr("ChunkZ", 0));
                        ListTag listtag = compoundtag1.getListOrEmpty("Children");

                        if (!listtag.isEmpty()) {
                            Optional<String> optional = listtag.getCompound(0).flatMap((compoundtag2) -> {
                                return compoundtag2.getString("id");
                            });
                            Map map = LegacyStructureDataHandler.LEGACY_TO_CURRENT_MAP;

                            Objects.requireNonNull(map);
                            optional.map(map::get).ifPresent((s2) -> {
                                compoundtag1.putString("id", s2);
                            });
                        }

                        compoundtag1.getString("id").ifPresent((s2) -> {
                            ((Long2ObjectMap) this.dataMap.computeIfAbsent(s2, (s3) -> {
                                return new Long2ObjectOpenHashMap();
                            })).put(i, compoundtag1);
                        });
                    }
                });
                String s1 = s + "_index";
                StructureFeatureIndexSavedData structurefeatureindexsaveddata = (StructureFeatureIndexSavedData) dimensionDataStorage.computeIfAbsent(StructureFeatureIndexSavedData.type(s1));

                if (structurefeatureindexsaveddata.getAll().isEmpty()) {
                    StructureFeatureIndexSavedData structurefeatureindexsaveddata1 = new StructureFeatureIndexSavedData();

                    this.indexMap.put(s, structurefeatureindexsaveddata1);
                    compoundtag.forEach((s2, tag) -> {
                        if (tag instanceof CompoundTag compoundtag1) {
                            structurefeatureindexsaveddata1.addIndex(ChunkPos.asLong(compoundtag1.getIntOr("ChunkX", 0), compoundtag1.getIntOr("ChunkZ", 0)));
                        }

                    });
                } else {
                    this.indexMap.put(s, structurefeatureindexsaveddata);
                }
            }

            this.cachesInitialized = true;
        }
    }

    public static Supplier<LegacyTagFixer> getLegacyTagFixer(ResourceKey<Level> dimension, Supplier<@Nullable DimensionDataStorage> dimensionDataStorage, DataFixer dataFixer) {
        if (dimension == Level.OVERWORLD) {
            return () -> {
                return new LegacyStructureDataHandler((DimensionDataStorage) dimensionDataStorage.get(), ImmutableList.of("Monument", "Stronghold", "Village", "Mineshaft", "Temple", "Mansion"), ImmutableList.of("Village", "Mineshaft", "Mansion", "Igloo", "Desert_Pyramid", "Jungle_Pyramid", "Swamp_Hut", "Stronghold", "Monument"), dataFixer);
            };
        } else if (dimension == Level.NETHER) {
            List<String> list = ImmutableList.of("Fortress");

            return () -> {
                return new LegacyStructureDataHandler((DimensionDataStorage) dimensionDataStorage.get(), list, list, dataFixer);
            };
        } else if (dimension == Level.END) {
            List<String> list1 = ImmutableList.of("EndCity");

            return () -> {
                return new LegacyStructureDataHandler((DimensionDataStorage) dimensionDataStorage.get(), list1, list1, dataFixer);
            };
        } else {
            return LegacyTagFixer.EMPTY;
        }
    }
}

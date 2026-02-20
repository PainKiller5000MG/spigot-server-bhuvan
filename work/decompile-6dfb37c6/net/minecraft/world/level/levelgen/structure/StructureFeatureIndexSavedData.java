package net.minecraft.world.level.levelgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class StructureFeatureIndexSavedData extends SavedData {

    private final LongSet all;
    private final LongSet remaining;
    private static final Codec<LongSet> LONG_SET = Codec.LONG_STREAM.xmap(LongOpenHashSet::toSet, LongCollection::longStream);
    public static final Codec<StructureFeatureIndexSavedData> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(StructureFeatureIndexSavedData.LONG_SET.fieldOf("All").forGetter((structurefeatureindexsaveddata) -> {
            return structurefeatureindexsaveddata.all;
        }), StructureFeatureIndexSavedData.LONG_SET.fieldOf("Remaining").forGetter((structurefeatureindexsaveddata) -> {
            return structurefeatureindexsaveddata.remaining;
        })).apply(instance, StructureFeatureIndexSavedData::new);
    });

    public static SavedDataType<StructureFeatureIndexSavedData> type(String id) {
        return new SavedDataType<StructureFeatureIndexSavedData>(id, StructureFeatureIndexSavedData::new, StructureFeatureIndexSavedData.CODEC, DataFixTypes.SAVED_DATA_STRUCTURE_FEATURE_INDICES);
    }

    private StructureFeatureIndexSavedData(LongSet all, LongSet remaining) {
        this.all = all;
        this.remaining = remaining;
    }

    public StructureFeatureIndexSavedData() {
        this(new LongOpenHashSet(), new LongOpenHashSet());
    }

    public void addIndex(long chunkPosKey) {
        this.all.add(chunkPosKey);
        this.remaining.add(chunkPosKey);
        this.setDirty();
    }

    public boolean hasStartIndex(long chunkPosKey) {
        return this.all.contains(chunkPosKey);
    }

    public boolean hasUnhandledIndex(long chunkPosKey) {
        return this.remaining.contains(chunkPosKey);
    }

    public void removeIndex(long chunkPosKey) {
        if (this.remaining.remove(chunkPosKey)) {
            this.setDirty();
        }

    }

    public LongSet getAll() {
        return this.all;
    }
}

package net.minecraft.world.level.saveddata.maps;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class MapIndex extends SavedData {

    private static final int NO_MAP_ID = -1;
    public static final Codec<MapIndex> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Codec.INT.optionalFieldOf("map", -1).forGetter((mapindex) -> {
            return mapindex.lastMapId;
        })).apply(instance, MapIndex::new);
    });
    public static final SavedDataType<MapIndex> TYPE = new SavedDataType<MapIndex>("idcounts", MapIndex::new, MapIndex.CODEC, DataFixTypes.SAVED_DATA_MAP_INDEX);
    private int lastMapId;

    public MapIndex() {
        this(-1);
    }

    public MapIndex(int lastMapId) {
        this.lastMapId = lastMapId;
    }

    public MapId getNextMapId() {
        MapId mapid = new MapId(++this.lastMapId);

        this.setDirty();
        return mapid;
    }
}

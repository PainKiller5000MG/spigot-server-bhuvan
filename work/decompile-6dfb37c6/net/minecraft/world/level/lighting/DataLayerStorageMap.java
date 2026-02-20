package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.chunk.DataLayer;
import org.jspecify.annotations.Nullable;

public abstract class DataLayerStorageMap<M extends DataLayerStorageMap<M>> {

    private static final int CACHE_SIZE = 2;
    private final long[] lastSectionKeys = new long[2];
    private final @Nullable DataLayer[] lastSections = new DataLayer[2];
    private boolean cacheEnabled;
    protected final Long2ObjectOpenHashMap<DataLayer> map;

    protected DataLayerStorageMap(Long2ObjectOpenHashMap<DataLayer> map) {
        this.map = map;
        this.clearCache();
        this.cacheEnabled = true;
    }

    public abstract M copy();

    public DataLayer copyDataLayer(long sectionNode) {
        DataLayer datalayer = ((DataLayer) this.map.get(sectionNode)).copy();

        this.map.put(sectionNode, datalayer);
        this.clearCache();
        return datalayer;
    }

    public boolean hasLayer(long sectionNode) {
        return this.map.containsKey(sectionNode);
    }

    public @Nullable DataLayer getLayer(long sectionNode) {
        if (this.cacheEnabled) {
            for (int j = 0; j < 2; ++j) {
                if (sectionNode == this.lastSectionKeys[j]) {
                    return this.lastSections[j];
                }
            }
        }

        DataLayer datalayer = (DataLayer) this.map.get(sectionNode);

        if (datalayer == null) {
            return null;
        } else {
            if (this.cacheEnabled) {
                for (int k = 1; k > 0; --k) {
                    this.lastSectionKeys[k] = this.lastSectionKeys[k - 1];
                    this.lastSections[k] = this.lastSections[k - 1];
                }

                this.lastSectionKeys[0] = sectionNode;
                this.lastSections[0] = datalayer;
            }

            return datalayer;
        }
    }

    public @Nullable DataLayer removeLayer(long sectionNode) {
        return (DataLayer) this.map.remove(sectionNode);
    }

    public void setLayer(long sectionNode, DataLayer layer) {
        this.map.put(sectionNode, layer);
    }

    public void clearCache() {
        for (int i = 0; i < 2; ++i) {
            this.lastSectionKeys[i] = Long.MAX_VALUE;
            this.lastSections[i] = null;
        }

    }

    public void disableCache() {
        this.cacheEnabled = false;
    }
}

package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.jspecify.annotations.Nullable;

public abstract class LayerLightSectionStorage<M extends DataLayerStorageMap<M>> {

    private final LightLayer layer;
    protected final LightChunkGetter chunkSource;
    protected final Long2ByteMap sectionStates = new Long2ByteOpenHashMap();
    private final LongSet columnsWithSources = new LongOpenHashSet();
    protected volatile M visibleSectionData;
    protected final M updatingSectionData;
    protected final LongSet changedSections = new LongOpenHashSet();
    protected final LongSet sectionsAffectedByLightUpdates = new LongOpenHashSet();
    protected final Long2ObjectMap<DataLayer> queuedSections = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap());
    private final LongSet columnsToRetainQueuedDataFor = new LongOpenHashSet();
    private final LongSet toRemove = new LongOpenHashSet();
    protected volatile boolean hasInconsistencies;

    protected LayerLightSectionStorage(LightLayer layer, LightChunkGetter chunkSource, M initialMap) {
        this.layer = layer;
        this.chunkSource = chunkSource;
        this.updatingSectionData = initialMap;
        this.visibleSectionData = ((DataLayerStorageMap) initialMap).copy();
        this.visibleSectionData.disableCache();
        this.sectionStates.defaultReturnValue((byte) 0);
    }

    protected boolean storingLightForSection(long sectionNode) {
        return this.getDataLayer(sectionNode, true) != null;
    }

    protected @Nullable DataLayer getDataLayer(long sectionNode, boolean updating) {
        return this.getDataLayer(updating ? this.updatingSectionData : this.visibleSectionData, sectionNode);
    }

    protected @Nullable DataLayer getDataLayer(M sections, long sectionNode) {
        return sections.getLayer(sectionNode);
    }

    protected @Nullable DataLayer getDataLayerToWrite(long sectionNode) {
        DataLayer datalayer = this.updatingSectionData.getLayer(sectionNode);

        if (datalayer == null) {
            return null;
        } else {
            if (this.changedSections.add(sectionNode)) {
                datalayer = datalayer.copy();
                this.updatingSectionData.setLayer(sectionNode, datalayer);
                this.updatingSectionData.clearCache();
            }

            return datalayer;
        }
    }

    public @Nullable DataLayer getDataLayerData(long sectionNode) {
        DataLayer datalayer = (DataLayer) this.queuedSections.get(sectionNode);

        return datalayer != null ? datalayer : this.getDataLayer(sectionNode, false);
    }

    protected abstract int getLightValue(long blockNode);

    protected int getStoredLevel(long blockNode) {
        long j = SectionPos.blockToSection(blockNode);
        DataLayer datalayer = this.getDataLayer(j, true);

        return datalayer.get(SectionPos.sectionRelative(BlockPos.getX(blockNode)), SectionPos.sectionRelative(BlockPos.getY(blockNode)), SectionPos.sectionRelative(BlockPos.getZ(blockNode)));
    }

    protected void setStoredLevel(long blockNode, int level) {
        long k = SectionPos.blockToSection(blockNode);
        DataLayer datalayer;

        if (this.changedSections.add(k)) {
            datalayer = this.updatingSectionData.copyDataLayer(k);
        } else {
            datalayer = this.getDataLayer(k, true);
        }

        datalayer.set(SectionPos.sectionRelative(BlockPos.getX(blockNode)), SectionPos.sectionRelative(BlockPos.getY(blockNode)), SectionPos.sectionRelative(BlockPos.getZ(blockNode)), level);
        LongSet longset = this.sectionsAffectedByLightUpdates;

        Objects.requireNonNull(this.sectionsAffectedByLightUpdates);
        SectionPos.aroundAndAtBlockPos(blockNode, longset::add);
    }

    protected void markSectionAndNeighborsAsAffected(long sectionNode) {
        int j = SectionPos.x(sectionNode);
        int k = SectionPos.y(sectionNode);
        int l = SectionPos.z(sectionNode);

        for (int i1 = -1; i1 <= 1; ++i1) {
            for (int j1 = -1; j1 <= 1; ++j1) {
                for (int k1 = -1; k1 <= 1; ++k1) {
                    this.sectionsAffectedByLightUpdates.add(SectionPos.asLong(j + j1, k + k1, l + i1));
                }
            }
        }

    }

    protected DataLayer createDataLayer(long sectionNode) {
        DataLayer datalayer = (DataLayer) this.queuedSections.get(sectionNode);

        return datalayer != null ? datalayer : new DataLayer();
    }

    protected boolean hasInconsistencies() {
        return this.hasInconsistencies;
    }

    protected void markNewInconsistencies(LightEngine<M, ?> engine) {
        if (this.hasInconsistencies) {
            this.hasInconsistencies = false;
            LongIterator longiterator = this.toRemove.iterator();

            while (longiterator.hasNext()) {
                long i = (Long) longiterator.next();
                DataLayer datalayer = (DataLayer) this.queuedSections.remove(i);
                DataLayer datalayer1 = this.updatingSectionData.removeLayer(i);

                if (this.columnsToRetainQueuedDataFor.contains(SectionPos.getZeroNode(i))) {
                    if (datalayer != null) {
                        this.queuedSections.put(i, datalayer);
                    } else if (datalayer1 != null) {
                        this.queuedSections.put(i, datalayer1);
                    }
                }
            }

            this.updatingSectionData.clearCache();
            longiterator = this.toRemove.iterator();

            while (longiterator.hasNext()) {
                long j = (Long) longiterator.next();

                this.onNodeRemoved(j);
                this.changedSections.add(j);
            }

            this.toRemove.clear();
            ObjectIterator<Long2ObjectMap.Entry<DataLayer>> objectiterator = Long2ObjectMaps.fastIterator(this.queuedSections);

            while (objectiterator.hasNext()) {
                Long2ObjectMap.Entry<DataLayer> long2objectmap_entry = (Entry) objectiterator.next();
                long k = long2objectmap_entry.getLongKey();

                if (this.storingLightForSection(k)) {
                    DataLayer datalayer2 = (DataLayer) long2objectmap_entry.getValue();

                    if (this.updatingSectionData.getLayer(k) != datalayer2) {
                        this.updatingSectionData.setLayer(k, datalayer2);
                        this.changedSections.add(k);
                    }

                    objectiterator.remove();
                }
            }

            this.updatingSectionData.clearCache();
        }
    }

    protected void onNodeAdded(long sectionNode) {}

    protected void onNodeRemoved(long sectionNode) {}

    protected void setLightEnabled(long zeroNode, boolean enable) {
        if (enable) {
            this.columnsWithSources.add(zeroNode);
        } else {
            this.columnsWithSources.remove(zeroNode);
        }

    }

    protected boolean lightOnInSection(long sectionNode) {
        long j = SectionPos.getZeroNode(sectionNode);

        return this.columnsWithSources.contains(j);
    }

    protected boolean lightOnInColumn(long sectionZeroNode) {
        return this.columnsWithSources.contains(sectionZeroNode);
    }

    public void retainData(long zeroNode, boolean retain) {
        if (retain) {
            this.columnsToRetainQueuedDataFor.add(zeroNode);
        } else {
            this.columnsToRetainQueuedDataFor.remove(zeroNode);
        }

    }

    protected void queueSectionData(long sectionNode, @Nullable DataLayer data) {
        if (data != null) {
            this.queuedSections.put(sectionNode, data);
            this.hasInconsistencies = true;
        } else {
            this.queuedSections.remove(sectionNode);
        }

    }

    protected void updateSectionStatus(long sectionNode, boolean sectionEmpty) {
        byte b0 = this.sectionStates.get(sectionNode);
        byte b1 = LayerLightSectionStorage.SectionState.hasData(b0, !sectionEmpty);

        if (b0 != b1) {
            this.putSectionState(sectionNode, b1);
            int j = sectionEmpty ? -1 : 1;

            for (int k = -1; k <= 1; ++k) {
                for (int l = -1; l <= 1; ++l) {
                    for (int i1 = -1; i1 <= 1; ++i1) {
                        if (k != 0 || l != 0 || i1 != 0) {
                            long j1 = SectionPos.offset(sectionNode, k, l, i1);
                            byte b2 = this.sectionStates.get(j1);

                            this.putSectionState(j1, LayerLightSectionStorage.SectionState.neighborCount(b2, LayerLightSectionStorage.SectionState.neighborCount(b2) + j));
                        }
                    }
                }
            }

        }
    }

    protected void putSectionState(long sectionNode, byte state) {
        if (state != 0) {
            if (this.sectionStates.put(sectionNode, state) == 0) {
                this.initializeSection(sectionNode);
            }
        } else if (this.sectionStates.remove(sectionNode) != 0) {
            this.removeSection(sectionNode);
        }

    }

    private void initializeSection(long sectionNode) {
        if (!this.toRemove.remove(sectionNode)) {
            this.updatingSectionData.setLayer(sectionNode, this.createDataLayer(sectionNode));
            this.changedSections.add(sectionNode);
            this.onNodeAdded(sectionNode);
            this.markSectionAndNeighborsAsAffected(sectionNode);
            this.hasInconsistencies = true;
        }

    }

    private void removeSection(long sectionNode) {
        this.toRemove.add(sectionNode);
        this.hasInconsistencies = true;
    }

    protected void swapSectionMap() {
        if (!this.changedSections.isEmpty()) {
            M m0 = this.updatingSectionData.copy();

            m0.disableCache();
            this.visibleSectionData = m0;
            this.changedSections.clear();
        }

        if (!this.sectionsAffectedByLightUpdates.isEmpty()) {
            LongIterator longiterator = this.sectionsAffectedByLightUpdates.iterator();

            while (longiterator.hasNext()) {
                long i = longiterator.nextLong();

                this.chunkSource.onLightUpdate(this.layer, SectionPos.of(i));
            }

            this.sectionsAffectedByLightUpdates.clear();
        }

    }

    public LayerLightSectionStorage.SectionType getDebugSectionType(long sectionNode) {
        return LayerLightSectionStorage.SectionState.type(this.sectionStates.get(sectionNode));
    }

    protected static class SectionState {

        public static final byte EMPTY = 0;
        private static final int MIN_NEIGHBORS = 0;
        private static final int MAX_NEIGHBORS = 26;
        private static final byte HAS_DATA_BIT = 32;
        private static final byte NEIGHBOR_COUNT_BITS = 31;

        protected SectionState() {}

        public static byte hasData(byte state, boolean hasData) {
            return (byte) (hasData ? state | 32 : state & -33);
        }

        public static byte neighborCount(byte state, int neighborCount) {
            if (neighborCount >= 0 && neighborCount <= 26) {
                return (byte) (state & -32 | neighborCount & 31);
            } else {
                throw new IllegalArgumentException("Neighbor count was not within range [0; 26]");
            }
        }

        public static boolean hasData(byte state) {
            return (state & 32) != 0;
        }

        public static int neighborCount(byte state) {
            return state & 31;
        }

        public static LayerLightSectionStorage.SectionType type(byte state) {
            return state == 0 ? LayerLightSectionStorage.SectionType.EMPTY : (hasData(state) ? LayerLightSectionStorage.SectionType.LIGHT_AND_DATA : LayerLightSectionStorage.SectionType.LIGHT_ONLY);
        }
    }

    public static enum SectionType {

        EMPTY("2"), LIGHT_ONLY("1"), LIGHT_AND_DATA("0");

        private final String display;

        private SectionType(String display) {
            this.display = display;
        }

        public String display() {
            return this.display;
        }
    }
}

package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

public class SkyLightSectionStorage extends LayerLightSectionStorage<SkyLightSectionStorage.SkyDataLayerStorageMap> {

    protected SkyLightSectionStorage(LightChunkGetter chunkSource) {
        super(LightLayer.SKY, chunkSource, new SkyLightSectionStorage.SkyDataLayerStorageMap(new Long2ObjectOpenHashMap(), new Long2IntOpenHashMap(), Integer.MAX_VALUE));
    }

    @Override
    protected int getLightValue(long blockNode) {
        return this.getLightValue(blockNode, false);
    }

    protected int getLightValue(long blockNode, boolean updating) {
        long j = SectionPos.blockToSection(blockNode);
        int k = SectionPos.y(j);
        SkyLightSectionStorage.SkyDataLayerStorageMap skylightsectionstorage_skydatalayerstoragemap = updating ? (SkyLightSectionStorage.SkyDataLayerStorageMap) this.updatingSectionData : (SkyLightSectionStorage.SkyDataLayerStorageMap) this.visibleSectionData;
        int l = skylightsectionstorage_skydatalayerstoragemap.topSections.get(SectionPos.getZeroNode(j));

        if (l != skylightsectionstorage_skydatalayerstoragemap.currentLowestY && k < l) {
            DataLayer datalayer = this.getDataLayer(skylightsectionstorage_skydatalayerstoragemap, j);

            if (datalayer == null) {
                for (blockNode = BlockPos.getFlatIndex(blockNode); datalayer == null; datalayer = this.getDataLayer(skylightsectionstorage_skydatalayerstoragemap, j)) {
                    ++k;
                    if (k >= l) {
                        return 15;
                    }

                    j = SectionPos.offset(j, Direction.UP);
                }
            }

            return datalayer.get(SectionPos.sectionRelative(BlockPos.getX(blockNode)), SectionPos.sectionRelative(BlockPos.getY(blockNode)), SectionPos.sectionRelative(BlockPos.getZ(blockNode)));
        } else {
            return updating && !this.lightOnInSection(j) ? 0 : 15;
        }
    }

    @Override
    protected void onNodeAdded(long sectionNode) {
        int j = SectionPos.y(sectionNode);

        if ((this.updatingSectionData).currentLowestY > j) {
            (this.updatingSectionData).currentLowestY = j;
            (this.updatingSectionData).topSections.defaultReturnValue((this.updatingSectionData).currentLowestY);
        }

        long k = SectionPos.getZeroNode(sectionNode);
        int l = (this.updatingSectionData).topSections.get(k);

        if (l < j + 1) {
            (this.updatingSectionData).topSections.put(k, j + 1);
        }

    }

    @Override
    protected void onNodeRemoved(long sectionNode) {
        long j = SectionPos.getZeroNode(sectionNode);
        int k = SectionPos.y(sectionNode);

        if ((this.updatingSectionData).topSections.get(j) == k + 1) {
            long l;

            for (l = sectionNode; !this.storingLightForSection(l) && this.hasLightDataAtOrBelow(k); l = SectionPos.offset(l, Direction.DOWN)) {
                --k;
            }

            if (this.storingLightForSection(l)) {
                (this.updatingSectionData).topSections.put(j, k + 1);
            } else {
                (this.updatingSectionData).topSections.remove(j);
            }
        }

    }

    @Override
    protected DataLayer createDataLayer(long sectionNode) {
        DataLayer datalayer = (DataLayer) this.queuedSections.get(sectionNode);

        if (datalayer != null) {
            return datalayer;
        } else {
            int j = (this.updatingSectionData).topSections.get(SectionPos.getZeroNode(sectionNode));

            if (j != (this.updatingSectionData).currentLowestY && SectionPos.y(sectionNode) < j) {
                DataLayer datalayer1;

                for (long k = SectionPos.offset(sectionNode, Direction.UP); (datalayer1 = this.getDataLayer(k, true)) == null; k = SectionPos.offset(k, Direction.UP)) {
                    ;
                }

                return repeatFirstLayer(datalayer1);
            } else {
                return this.lightOnInSection(sectionNode) ? new DataLayer(15) : new DataLayer();
            }
        }
    }

    private static DataLayer repeatFirstLayer(DataLayer data) {
        if (data.isDefinitelyHomogenous()) {
            return data.copy();
        } else {
            byte[] abyte = data.getData();
            byte[] abyte1 = new byte[2048];

            for (int i = 0; i < 16; ++i) {
                System.arraycopy(abyte, 0, abyte1, i * 128, 128);
            }

            return new DataLayer(abyte1);
        }
    }

    protected boolean hasLightDataAtOrBelow(int sectionY) {
        return sectionY >= (this.updatingSectionData).currentLowestY;
    }

    protected boolean isAboveData(long sectionNode) {
        long j = SectionPos.getZeroNode(sectionNode);
        int k = (this.updatingSectionData).topSections.get(j);

        return k == (this.updatingSectionData).currentLowestY || SectionPos.y(sectionNode) >= k;
    }

    protected int getTopSectionY(long zeroNode) {
        return (this.updatingSectionData).topSections.get(zeroNode);
    }

    protected int getBottomSectionY() {
        return (this.updatingSectionData).currentLowestY;
    }

    protected static final class SkyDataLayerStorageMap extends DataLayerStorageMap<SkyLightSectionStorage.SkyDataLayerStorageMap> {

        private int currentLowestY;
        private final Long2IntOpenHashMap topSections;

        public SkyDataLayerStorageMap(Long2ObjectOpenHashMap<DataLayer> map, Long2IntOpenHashMap topSections, int currentLowestY) {
            super(map);
            this.topSections = topSections;
            topSections.defaultReturnValue(currentLowestY);
            this.currentLowestY = currentLowestY;
        }

        @Override
        public SkyLightSectionStorage.SkyDataLayerStorageMap copy() {
            return new SkyLightSectionStorage.SkyDataLayerStorageMap(this.map.clone(), this.topSections.clone(), this.currentLowestY);
        }
    }
}

package net.minecraft.world.level.lighting;

import com.google.common.annotations.VisibleForTesting;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.jspecify.annotations.Nullable;

public final class SkyLightEngine extends LightEngine<SkyLightSectionStorage.SkyDataLayerStorageMap, SkyLightSectionStorage> {

    private static final long REMOVE_TOP_SKY_SOURCE_ENTRY = LightEngine.QueueEntry.decreaseAllDirections(15);
    private static final long REMOVE_SKY_SOURCE_ENTRY = LightEngine.QueueEntry.decreaseSkipOneDirection(15, Direction.UP);
    private static final long ADD_SKY_SOURCE_ENTRY = LightEngine.QueueEntry.increaseSkipOneDirection(15, false, Direction.UP);
    private final BlockPos.MutableBlockPos mutablePos;
    private final ChunkSkyLightSources emptyChunkSources;

    public SkyLightEngine(LightChunkGetter chunkSource) {
        this(chunkSource, new SkyLightSectionStorage(chunkSource));
    }

    @VisibleForTesting
    protected SkyLightEngine(LightChunkGetter chunkSource, SkyLightSectionStorage storage) {
        super(chunkSource, storage);
        this.mutablePos = new BlockPos.MutableBlockPos();
        this.emptyChunkSources = new ChunkSkyLightSources(chunkSource.getLevel());
    }

    private static boolean isSourceLevel(int value) {
        return value == 15;
    }

    private int getLowestSourceY(int x, int z, int defaultValue) {
        ChunkSkyLightSources chunkskylightsources = this.getChunkSources(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));

        return chunkskylightsources == null ? defaultValue : chunkskylightsources.getLowestSourceY(SectionPos.sectionRelative(x), SectionPos.sectionRelative(z));
    }

    private @Nullable ChunkSkyLightSources getChunkSources(int chunkX, int chunkZ) {
        LightChunk lightchunk = this.chunkSource.getChunkForLighting(chunkX, chunkZ);

        return lightchunk != null ? lightchunk.getSkyLightSources() : null;
    }

    @Override
    protected void checkNode(long blockNode) {
        int j = BlockPos.getX(blockNode);
        int k = BlockPos.getY(blockNode);
        int l = BlockPos.getZ(blockNode);
        long i1 = SectionPos.blockToSection(blockNode);
        int j1 = ((SkyLightSectionStorage) this.storage).lightOnInSection(i1) ? this.getLowestSourceY(j, l, Integer.MAX_VALUE) : Integer.MAX_VALUE;

        if (j1 != Integer.MAX_VALUE) {
            this.updateSourcesInColumn(j, l, j1);
        }

        if (((SkyLightSectionStorage) this.storage).storingLightForSection(i1)) {
            boolean flag = k >= j1;

            if (flag) {
                this.enqueueDecrease(blockNode, SkyLightEngine.REMOVE_SKY_SOURCE_ENTRY);
                this.enqueueIncrease(blockNode, SkyLightEngine.ADD_SKY_SOURCE_ENTRY);
            } else {
                int k1 = ((SkyLightSectionStorage) this.storage).getStoredLevel(blockNode);

                if (k1 > 0) {
                    ((SkyLightSectionStorage) this.storage).setStoredLevel(blockNode, 0);
                    this.enqueueDecrease(blockNode, LightEngine.QueueEntry.decreaseAllDirections(k1));
                } else {
                    this.enqueueDecrease(blockNode, SkyLightEngine.PULL_LIGHT_IN_ENTRY);
                }
            }

        }
    }

    private void updateSourcesInColumn(int x, int z, int lowestSourceY) {
        int l = SectionPos.sectionToBlockCoord(((SkyLightSectionStorage) this.storage).getBottomSectionY());

        this.removeSourcesBelow(x, z, lowestSourceY, l);
        this.addSourcesAbove(x, z, lowestSourceY, l);
    }

    private void removeSourcesBelow(int x, int z, int lowestSourceY, int worldBottomY) {
        if (lowestSourceY > worldBottomY) {
            int i1 = SectionPos.blockToSectionCoord(x);
            int j1 = SectionPos.blockToSectionCoord(z);
            int k1 = lowestSourceY - 1;

            for (int l1 = SectionPos.blockToSectionCoord(k1); ((SkyLightSectionStorage) this.storage).hasLightDataAtOrBelow(l1); --l1) {
                if (((SkyLightSectionStorage) this.storage).storingLightForSection(SectionPos.asLong(i1, l1, j1))) {
                    int i2 = SectionPos.sectionToBlockCoord(l1);
                    int j2 = i2 + 15;

                    for (int k2 = Math.min(j2, k1); k2 >= i2; --k2) {
                        long l2 = BlockPos.asLong(x, k2, z);

                        if (!isSourceLevel(((SkyLightSectionStorage) this.storage).getStoredLevel(l2))) {
                            return;
                        }

                        ((SkyLightSectionStorage) this.storage).setStoredLevel(l2, 0);
                        this.enqueueDecrease(l2, k2 == lowestSourceY - 1 ? SkyLightEngine.REMOVE_TOP_SKY_SOURCE_ENTRY : SkyLightEngine.REMOVE_SKY_SOURCE_ENTRY);
                    }
                }
            }

        }
    }

    private void addSourcesAbove(int x, int z, int lowestSourceY, int worldBottomY) {
        int i1 = SectionPos.blockToSectionCoord(x);
        int j1 = SectionPos.blockToSectionCoord(z);
        int k1 = Math.max(Math.max(this.getLowestSourceY(x - 1, z, Integer.MIN_VALUE), this.getLowestSourceY(x + 1, z, Integer.MIN_VALUE)), Math.max(this.getLowestSourceY(x, z - 1, Integer.MIN_VALUE), this.getLowestSourceY(x, z + 1, Integer.MIN_VALUE)));
        int l1 = Math.max(lowestSourceY, worldBottomY);

        for (long i2 = SectionPos.asLong(i1, SectionPos.blockToSectionCoord(l1), j1); !((SkyLightSectionStorage) this.storage).isAboveData(i2); i2 = SectionPos.offset(i2, Direction.UP)) {
            if (((SkyLightSectionStorage) this.storage).storingLightForSection(i2)) {
                int j2 = SectionPos.sectionToBlockCoord(SectionPos.y(i2));
                int k2 = j2 + 15;

                for (int l2 = Math.max(j2, l1); l2 <= k2; ++l2) {
                    long i3 = BlockPos.asLong(x, l2, z);

                    if (isSourceLevel(((SkyLightSectionStorage) this.storage).getStoredLevel(i3))) {
                        return;
                    }

                    ((SkyLightSectionStorage) this.storage).setStoredLevel(i3, 15);
                    if (l2 < k1 || l2 == lowestSourceY) {
                        this.enqueueIncrease(i3, SkyLightEngine.ADD_SKY_SOURCE_ENTRY);
                    }
                }
            }
        }

    }

    @Override
    protected void propagateIncrease(long fromNode, long increaseData, int fromLevel) {
        BlockState blockstate = null;
        int l = this.countEmptySectionsBelowIfAtBorder(fromNode);

        for (Direction direction : SkyLightEngine.PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(increaseData, direction)) {
                long i1 = BlockPos.offset(fromNode, direction);

                if (((SkyLightSectionStorage) this.storage).storingLightForSection(SectionPos.blockToSection(i1))) {
                    int j1 = ((SkyLightSectionStorage) this.storage).getStoredLevel(i1);
                    int k1 = fromLevel - 1;

                    if (k1 > j1) {
                        this.mutablePos.set(i1);
                        BlockState blockstate1 = this.getState(this.mutablePos);
                        int l1 = fromLevel - this.getOpacity(blockstate1);

                        if (l1 > j1) {
                            if (blockstate == null) {
                                blockstate = LightEngine.QueueEntry.isFromEmptyShape(increaseData) ? Blocks.AIR.defaultBlockState() : this.getState(this.mutablePos.set(fromNode));
                            }

                            if (!this.shapeOccludes(blockstate, blockstate1, direction)) {
                                ((SkyLightSectionStorage) this.storage).setStoredLevel(i1, l1);
                                if (l1 > 1) {
                                    this.enqueueIncrease(i1, LightEngine.QueueEntry.increaseSkipOneDirection(l1, isEmptyShape(blockstate1), direction.getOpposite()));
                                }

                                this.propagateFromEmptySections(i1, direction, l1, true, l);
                            }
                        }
                    }
                }
            }
        }

    }

    @Override
    protected void propagateDecrease(long fromNode, long decreaseData) {
        int k = this.countEmptySectionsBelowIfAtBorder(fromNode);
        int l = LightEngine.QueueEntry.getFromLevel(decreaseData);

        for (Direction direction : SkyLightEngine.PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(decreaseData, direction)) {
                long i1 = BlockPos.offset(fromNode, direction);

                if (((SkyLightSectionStorage) this.storage).storingLightForSection(SectionPos.blockToSection(i1))) {
                    int j1 = ((SkyLightSectionStorage) this.storage).getStoredLevel(i1);

                    if (j1 != 0) {
                        if (j1 <= l - 1) {
                            ((SkyLightSectionStorage) this.storage).setStoredLevel(i1, 0);
                            this.enqueueDecrease(i1, LightEngine.QueueEntry.decreaseSkipOneDirection(j1, direction.getOpposite()));
                            this.propagateFromEmptySections(i1, direction, j1, false, k);
                        } else {
                            this.enqueueIncrease(i1, LightEngine.QueueEntry.increaseOnlyOneDirection(j1, false, direction.getOpposite()));
                        }
                    }
                }
            }
        }

    }

    private int countEmptySectionsBelowIfAtBorder(long blockNode) {
        int j = BlockPos.getY(blockNode);
        int k = SectionPos.sectionRelative(j);

        if (k != 0) {
            return 0;
        } else {
            int l = BlockPos.getX(blockNode);
            int i1 = BlockPos.getZ(blockNode);
            int j1 = SectionPos.sectionRelative(l);
            int k1 = SectionPos.sectionRelative(i1);

            if (j1 != 0 && j1 != 15 && k1 != 0 && k1 != 15) {
                return 0;
            } else {
                int l1 = SectionPos.blockToSectionCoord(l);
                int i2 = SectionPos.blockToSectionCoord(j);
                int j2 = SectionPos.blockToSectionCoord(i1);

                int k2;

                for (k2 = 0; !((SkyLightSectionStorage) this.storage).storingLightForSection(SectionPos.asLong(l1, i2 - k2 - 1, j2)) && ((SkyLightSectionStorage) this.storage).hasLightDataAtOrBelow(i2 - k2 - 1); ++k2) {
                    ;
                }

                return k2;
            }
        }
    }

    private void propagateFromEmptySections(long toNode, Direction propagationDirection, int toLevel, boolean increase, int emptySectionsBelow) {
        if (emptySectionsBelow != 0) {
            int l = BlockPos.getX(toNode);
            int i1 = BlockPos.getZ(toNode);

            if (crossedSectionEdge(propagationDirection, SectionPos.sectionRelative(l), SectionPos.sectionRelative(i1))) {
                int j1 = BlockPos.getY(toNode);
                int k1 = SectionPos.blockToSectionCoord(l);
                int l1 = SectionPos.blockToSectionCoord(i1);
                int i2 = SectionPos.blockToSectionCoord(j1) - 1;
                int j2 = i2 - emptySectionsBelow + 1;

                while (i2 >= j2) {
                    if (!((SkyLightSectionStorage) this.storage).storingLightForSection(SectionPos.asLong(k1, i2, l1))) {
                        --i2;
                    } else {
                        int k2 = SectionPos.sectionToBlockCoord(i2);

                        for (int l2 = 15; l2 >= 0; --l2) {
                            long i3 = BlockPos.asLong(l, k2 + l2, i1);

                            if (increase) {
                                ((SkyLightSectionStorage) this.storage).setStoredLevel(i3, toLevel);
                                if (toLevel > 1) {
                                    this.enqueueIncrease(i3, LightEngine.QueueEntry.increaseSkipOneDirection(toLevel, true, propagationDirection.getOpposite()));
                                }
                            } else {
                                ((SkyLightSectionStorage) this.storage).setStoredLevel(i3, 0);
                                this.enqueueDecrease(i3, LightEngine.QueueEntry.decreaseSkipOneDirection(toLevel, propagationDirection.getOpposite()));
                            }
                        }

                        --i2;
                    }
                }

            }
        }
    }

    private static boolean crossedSectionEdge(Direction propagationDirection, int x, int z) {
        boolean flag;

        switch (propagationDirection) {
            case NORTH:
                flag = z == 15;
                break;
            case SOUTH:
                flag = z == 0;
                break;
            case WEST:
                flag = x == 15;
                break;
            case EAST:
                flag = x == 0;
                break;
            default:
                flag = false;
        }

        return flag;
    }

    @Override
    public void setLightEnabled(ChunkPos pos, boolean enable) {
        super.setLightEnabled(pos, enable);
        if (enable) {
            ChunkSkyLightSources chunkskylightsources = (ChunkSkyLightSources) Objects.requireNonNullElse(this.getChunkSources(pos.x, pos.z), this.emptyChunkSources);
            int i = chunkskylightsources.getHighestLowestSourceY() - 1;
            int j = SectionPos.blockToSectionCoord(i) + 1;
            long k = SectionPos.getZeroNode(pos.x, pos.z);
            int l = ((SkyLightSectionStorage) this.storage).getTopSectionY(k);
            int i1 = Math.max(((SkyLightSectionStorage) this.storage).getBottomSectionY(), j);

            for (int j1 = l - 1; j1 >= i1; --j1) {
                DataLayer datalayer = ((SkyLightSectionStorage) this.storage).getDataLayerToWrite(SectionPos.asLong(pos.x, j1, pos.z));

                if (datalayer != null && datalayer.isEmpty()) {
                    datalayer.fill(15);
                }
            }
        }

    }

    @Override
    public void propagateLightSources(ChunkPos pos) {
        long i = SectionPos.getZeroNode(pos.x, pos.z);

        ((SkyLightSectionStorage) this.storage).setLightEnabled(i, true);
        ChunkSkyLightSources chunkskylightsources = (ChunkSkyLightSources) Objects.requireNonNullElse(this.getChunkSources(pos.x, pos.z), this.emptyChunkSources);
        ChunkSkyLightSources chunkskylightsources1 = (ChunkSkyLightSources) Objects.requireNonNullElse(this.getChunkSources(pos.x, pos.z - 1), this.emptyChunkSources);
        ChunkSkyLightSources chunkskylightsources2 = (ChunkSkyLightSources) Objects.requireNonNullElse(this.getChunkSources(pos.x, pos.z + 1), this.emptyChunkSources);
        ChunkSkyLightSources chunkskylightsources3 = (ChunkSkyLightSources) Objects.requireNonNullElse(this.getChunkSources(pos.x - 1, pos.z), this.emptyChunkSources);
        ChunkSkyLightSources chunkskylightsources4 = (ChunkSkyLightSources) Objects.requireNonNullElse(this.getChunkSources(pos.x + 1, pos.z), this.emptyChunkSources);
        int j = ((SkyLightSectionStorage) this.storage).getTopSectionY(i);
        int k = ((SkyLightSectionStorage) this.storage).getBottomSectionY();
        int l = SectionPos.sectionToBlockCoord(pos.x);
        int i1 = SectionPos.sectionToBlockCoord(pos.z);

        for (int j1 = j - 1; j1 >= k; --j1) {
            long k1 = SectionPos.asLong(pos.x, j1, pos.z);
            DataLayer datalayer = ((SkyLightSectionStorage) this.storage).getDataLayerToWrite(k1);

            if (datalayer != null) {
                int l1 = SectionPos.sectionToBlockCoord(j1);
                int i2 = l1 + 15;
                boolean flag = false;

                for (int j2 = 0; j2 < 16; ++j2) {
                    for (int k2 = 0; k2 < 16; ++k2) {
                        int l2 = chunkskylightsources.getLowestSourceY(k2, j2);

                        if (l2 <= i2) {
                            int i3 = j2 == 0 ? chunkskylightsources1.getLowestSourceY(k2, 15) : chunkskylightsources.getLowestSourceY(k2, j2 - 1);
                            int j3 = j2 == 15 ? chunkskylightsources2.getLowestSourceY(k2, 0) : chunkskylightsources.getLowestSourceY(k2, j2 + 1);
                            int k3 = k2 == 0 ? chunkskylightsources3.getLowestSourceY(15, j2) : chunkskylightsources.getLowestSourceY(k2 - 1, j2);
                            int l3 = k2 == 15 ? chunkskylightsources4.getLowestSourceY(0, j2) : chunkskylightsources.getLowestSourceY(k2 + 1, j2);
                            int i4 = Math.max(Math.max(i3, j3), Math.max(k3, l3));

                            for (int j4 = i2; j4 >= Math.max(l1, l2); --j4) {
                                datalayer.set(k2, SectionPos.sectionRelative(j4), j2, 15);
                                if (j4 == l2 || j4 < i4) {
                                    long k4 = BlockPos.asLong(l + k2, j4, i1 + j2);

                                    this.enqueueIncrease(k4, LightEngine.QueueEntry.increaseSkySourceInDirections(j4 == l2, j4 < i3, j4 < j3, j4 < k3, j4 < l3));
                                }
                            }

                            if (l2 < l1) {
                                flag = true;
                            }
                        }
                    }
                }

                if (!flag) {
                    break;
                }
            }
        }

    }
}

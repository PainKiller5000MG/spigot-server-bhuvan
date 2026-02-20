package net.minecraft.server.level;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;

public abstract class ChunkTracker extends DynamicGraphMinFixedPoint {

    protected ChunkTracker(int levelCount, int minQueueSize, int minMapSize) {
        super(levelCount, minQueueSize, minMapSize);
    }

    @Override
    protected boolean isSource(long node) {
        return node == ChunkPos.INVALID_CHUNK_POS;
    }

    @Override
    protected void checkNeighborsAfterUpdate(long node, int level, boolean onlyDecrease) {
        if (!onlyDecrease || level < this.levelCount - 2) {
            ChunkPos chunkpos = new ChunkPos(node);
            int k = chunkpos.x;
            int l = chunkpos.z;

            for (int i1 = -1; i1 <= 1; ++i1) {
                for (int j1 = -1; j1 <= 1; ++j1) {
                    long k1 = ChunkPos.asLong(k + i1, l + j1);

                    if (k1 != node) {
                        this.checkNeighbor(node, k1, level, onlyDecrease);
                    }
                }
            }

        }
    }

    @Override
    protected int getComputedLevel(long node, long knownParent, int knownLevelFromParent) {
        int l = knownLevelFromParent;
        ChunkPos chunkpos = new ChunkPos(node);
        int i1 = chunkpos.x;
        int j1 = chunkpos.z;

        for (int k1 = -1; k1 <= 1; ++k1) {
            for (int l1 = -1; l1 <= 1; ++l1) {
                long i2 = ChunkPos.asLong(i1 + k1, j1 + l1);

                if (i2 == node) {
                    i2 = ChunkPos.INVALID_CHUNK_POS;
                }

                if (i2 != knownParent) {
                    int j2 = this.computeLevelFromNeighbor(i2, node, this.getLevel(i2));

                    if (l > j2) {
                        l = j2;
                    }

                    if (l == 0) {
                        return l;
                    }
                }
            }
        }

        return l;
    }

    @Override
    protected int computeLevelFromNeighbor(long from, long to, int fromLevel) {
        return from == ChunkPos.INVALID_CHUNK_POS ? this.getLevelFromSource(to) : fromLevel + 1;
    }

    protected abstract int getLevelFromSource(long to);

    public void update(long node, int newLevelFrom, boolean onlyDecreased) {
        this.checkEdge(ChunkPos.INVALID_CHUNK_POS, node, newLevelFrom, onlyDecreased);
    }
}

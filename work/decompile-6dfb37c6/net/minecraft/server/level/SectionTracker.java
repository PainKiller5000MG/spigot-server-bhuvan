package net.minecraft.server.level;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;

public abstract class SectionTracker extends DynamicGraphMinFixedPoint {

    protected SectionTracker(int levelCount, int minQueueSize, int minMapSize) {
        super(levelCount, minQueueSize, minMapSize);
    }

    @Override
    protected void checkNeighborsAfterUpdate(long node, int level, boolean onlyDecrease) {
        if (!onlyDecrease || level < this.levelCount - 2) {
            for (int k = -1; k <= 1; ++k) {
                for (int l = -1; l <= 1; ++l) {
                    for (int i1 = -1; i1 <= 1; ++i1) {
                        long j1 = SectionPos.offset(node, k, l, i1);

                        if (j1 != node) {
                            this.checkNeighbor(node, j1, level, onlyDecrease);
                        }
                    }
                }
            }

        }
    }

    @Override
    protected int getComputedLevel(long node, long knownParent, int knownLevelFromParent) {
        int l = knownLevelFromParent;

        for (int i1 = -1; i1 <= 1; ++i1) {
            for (int j1 = -1; j1 <= 1; ++j1) {
                for (int k1 = -1; k1 <= 1; ++k1) {
                    long l1 = SectionPos.offset(node, i1, j1, k1);

                    if (l1 == node) {
                        l1 = Long.MAX_VALUE;
                    }

                    if (l1 != knownParent) {
                        int i2 = this.computeLevelFromNeighbor(l1, node, this.getLevel(l1));

                        if (l > i2) {
                            l = i2;
                        }

                        if (l == 0) {
                            return l;
                        }
                    }
                }
            }
        }

        return l;
    }

    @Override
    protected int computeLevelFromNeighbor(long from, long to, int fromLevel) {
        return this.isSource(from) ? this.getLevelFromSource(to) : fromLevel + 1;
    }

    protected abstract int getLevelFromSource(long to);

    public void update(long node, int newLevelFrom, boolean onlyDecreased) {
        this.checkEdge(Long.MAX_VALUE, node, newLevelFrom, onlyDecreased);
    }
}

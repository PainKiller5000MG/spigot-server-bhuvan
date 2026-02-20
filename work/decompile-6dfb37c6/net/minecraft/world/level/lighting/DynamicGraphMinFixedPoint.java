package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.function.LongPredicate;
import net.minecraft.util.Mth;

public abstract class DynamicGraphMinFixedPoint {

    public static final long SOURCE = Long.MAX_VALUE;
    private static final int NO_COMPUTED_LEVEL = 255;
    protected final int levelCount;
    private final LeveledPriorityQueue priorityQueue;
    private final Long2ByteMap computedLevels;
    private volatile boolean hasWork;

    protected DynamicGraphMinFixedPoint(int levelCount, int minQueueSize, final int minMapSize) {
        if (levelCount >= 254) {
            throw new IllegalArgumentException("Level count must be < 254.");
        } else {
            this.levelCount = levelCount;
            this.priorityQueue = new LeveledPriorityQueue(levelCount, minQueueSize);
            this.computedLevels = new Long2ByteOpenHashMap(minMapSize, 0.5F) {
                protected void rehash(int newN) {
                    if (newN > minMapSize) {
                        super.rehash(newN);
                    }

                }
            };
            this.computedLevels.defaultReturnValue((byte) -1);
        }
    }

    protected void removeFromQueue(long node) {
        int j = this.computedLevels.remove(node) & 255;

        if (j != 255) {
            int k = this.getLevel(node);
            int l = this.calculatePriority(k, j);

            this.priorityQueue.dequeue(node, l, this.levelCount);
            this.hasWork = !this.priorityQueue.isEmpty();
        }
    }

    public void removeIf(LongPredicate pred) {
        LongList longlist = new LongArrayList();

        this.computedLevels.keySet().forEach((i) -> {
            if (pred.test(i)) {
                longlist.add(i);
            }

        });
        longlist.forEach(this::removeFromQueue);
    }

    private int calculatePriority(int level, int computedLevel) {
        return Math.min(Math.min(level, computedLevel), this.levelCount - 1);
    }

    protected void checkNode(long node) {
        this.checkEdge(node, node, this.levelCount - 1, false);
    }

    protected void checkEdge(long from, long to, int newLevelFrom, boolean onlyDecreased) {
        this.checkEdge(from, to, newLevelFrom, this.getLevel(to), this.computedLevels.get(to) & 255, onlyDecreased);
        this.hasWork = !this.priorityQueue.isEmpty();
    }

    private void checkEdge(long from, long to, int newLevelFrom, int levelTo, int oldComputedLevel, boolean onlyDecreased) {
        if (!this.isSource(to)) {
            newLevelFrom = Mth.clamp(newLevelFrom, 0, this.levelCount - 1);
            levelTo = Mth.clamp(levelTo, 0, this.levelCount - 1);
            boolean flag1 = oldComputedLevel == 255;

            if (flag1) {
                oldComputedLevel = levelTo;
            }

            int j1;

            if (onlyDecreased) {
                j1 = Math.min(oldComputedLevel, newLevelFrom);
            } else {
                j1 = Mth.clamp(this.getComputedLevel(to, from, newLevelFrom), 0, this.levelCount - 1);
            }

            int k1 = this.calculatePriority(levelTo, oldComputedLevel);

            if (levelTo != j1) {
                int l1 = this.calculatePriority(levelTo, j1);

                if (k1 != l1 && !flag1) {
                    this.priorityQueue.dequeue(to, k1, l1);
                }

                this.priorityQueue.enqueue(to, l1);
                this.computedLevels.put(to, (byte) j1);
            } else if (!flag1) {
                this.priorityQueue.dequeue(to, k1, this.levelCount);
                this.computedLevels.remove(to);
            }

        }
    }

    protected final void checkNeighbor(long from, long to, int level, boolean onlyDecreased) {
        int l = this.computedLevels.get(to) & 255;
        int i1 = Mth.clamp(this.computeLevelFromNeighbor(from, to, level), 0, this.levelCount - 1);

        if (onlyDecreased) {
            this.checkEdge(from, to, i1, this.getLevel(to), l, onlyDecreased);
        } else {
            boolean flag1 = l == 255;
            int j1;

            if (flag1) {
                j1 = Mth.clamp(this.getLevel(to), 0, this.levelCount - 1);
            } else {
                j1 = l;
            }

            if (i1 == j1) {
                this.checkEdge(from, to, this.levelCount - 1, flag1 ? j1 : this.getLevel(to), l, onlyDecreased);
            }
        }

    }

    protected final boolean hasWork() {
        return this.hasWork;
    }

    protected final int runUpdates(int count) {
        if (this.priorityQueue.isEmpty()) {
            return count;
        } else {
            while (!this.priorityQueue.isEmpty() && count > 0) {
                --count;
                long j = this.priorityQueue.removeFirstLong();
                int k = Mth.clamp(this.getLevel(j), 0, this.levelCount - 1);
                int l = this.computedLevels.remove(j) & 255;

                if (l < k) {
                    this.setLevel(j, l);
                    this.checkNeighborsAfterUpdate(j, l, true);
                } else if (l > k) {
                    this.setLevel(j, this.levelCount - 1);
                    if (l != this.levelCount - 1) {
                        this.priorityQueue.enqueue(j, this.calculatePriority(this.levelCount - 1, l));
                        this.computedLevels.put(j, (byte) l);
                    }

                    this.checkNeighborsAfterUpdate(j, k, false);
                }
            }

            this.hasWork = !this.priorityQueue.isEmpty();
            return count;
        }
    }

    public int getQueueSize() {
        return this.computedLevels.size();
    }

    protected boolean isSource(long node) {
        return node == Long.MAX_VALUE;
    }

    protected abstract int getComputedLevel(long node, long knownParent, int knownLevelFromParent);

    protected abstract void checkNeighborsAfterUpdate(long node, int level, boolean onlyDecrease);

    protected abstract int getLevel(long node);

    protected abstract void setLevel(long node, int level);

    protected abstract int computeLevelFromNeighbor(long from, long to, int fromLevel);
}

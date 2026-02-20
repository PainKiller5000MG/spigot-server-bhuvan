package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

public class LeveledPriorityQueue {

    private final int levelCount;
    private final LongLinkedOpenHashSet[] queues;
    private int firstQueuedLevel;

    public LeveledPriorityQueue(int levelCount, final int minSize) {
        this.levelCount = levelCount;
        this.queues = new LongLinkedOpenHashSet[levelCount];

        for (int k = 0; k < levelCount; ++k) {
            this.queues[k] = new LongLinkedOpenHashSet(minSize, 0.5F) {
                protected void rehash(int newN) {
                    if (newN > minSize) {
                        super.rehash(newN);
                    }

                }
            };
        }

        this.firstQueuedLevel = levelCount;
    }

    public long removeFirstLong() {
        LongLinkedOpenHashSet longlinkedopenhashset = this.queues[this.firstQueuedLevel];
        long i = longlinkedopenhashset.removeFirstLong();

        if (longlinkedopenhashset.isEmpty()) {
            this.checkFirstQueuedLevel(this.levelCount);
        }

        return i;
    }

    public boolean isEmpty() {
        return this.firstQueuedLevel >= this.levelCount;
    }

    public void dequeue(long node, int key, int upperBound) {
        LongLinkedOpenHashSet longlinkedopenhashset = this.queues[key];

        longlinkedopenhashset.remove(node);
        if (longlinkedopenhashset.isEmpty() && this.firstQueuedLevel == key) {
            this.checkFirstQueuedLevel(upperBound);
        }

    }

    public void enqueue(long node, int key) {
        this.queues[key].add(node);
        if (this.firstQueuedLevel > key) {
            this.firstQueuedLevel = key;
        }

    }

    private void checkFirstQueuedLevel(int upperBound) {
        int j = this.firstQueuedLevel;

        this.firstQueuedLevel = upperBound;

        for (int k = j + 1; k < upperBound; ++k) {
            if (!this.queues[k].isEmpty()) {
                this.firstQueuedLevel = k;
                break;
            }
        }

    }
}

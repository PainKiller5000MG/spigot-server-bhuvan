package net.minecraft.server.level;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public class ChunkTaskPriorityQueue {

    public static final int PRIORITY_LEVEL_COUNT = ChunkLevel.MAX_LEVEL + 2;
    private final List<Long2ObjectLinkedOpenHashMap<List<Runnable>>> queuesPerPriority;
    private volatile int topPriorityQueueIndex;
    private final String name;

    public ChunkTaskPriorityQueue(String name) {
        this.queuesPerPriority = IntStream.range(0, ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT).mapToObj((i) -> {
            return new Long2ObjectLinkedOpenHashMap();
        }).toList();
        this.topPriorityQueueIndex = ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT;
        this.name = name;
    }

    protected void resortChunkTasks(int oldPriority, ChunkPos pos, int newPriority) {
        if (oldPriority < ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT) {
            Long2ObjectLinkedOpenHashMap<List<Runnable>> long2objectlinkedopenhashmap = (Long2ObjectLinkedOpenHashMap) this.queuesPerPriority.get(oldPriority);
            List<Runnable> list = (List) long2objectlinkedopenhashmap.remove(pos.toLong());

            if (oldPriority == this.topPriorityQueueIndex) {
                while (this.hasWork() && ((Long2ObjectLinkedOpenHashMap) this.queuesPerPriority.get(this.topPriorityQueueIndex)).isEmpty()) {
                    ++this.topPriorityQueueIndex;
                }
            }

            if (list != null && !list.isEmpty()) {
                ((List) ((Long2ObjectLinkedOpenHashMap) this.queuesPerPriority.get(newPriority)).computeIfAbsent(pos.toLong(), (k) -> {
                    return Lists.newArrayList();
                })).addAll(list);
                this.topPriorityQueueIndex = Math.min(this.topPriorityQueueIndex, newPriority);
            }

        }
    }

    protected void submit(Runnable task, long chunkPos, int level) {
        ((List) ((Long2ObjectLinkedOpenHashMap) this.queuesPerPriority.get(level)).computeIfAbsent(chunkPos, (k) -> {
            return Lists.newArrayList();
        })).add(task);
        this.topPriorityQueueIndex = Math.min(this.topPriorityQueueIndex, level);
    }

    protected void release(long pos, boolean unschedule) {
        for (Long2ObjectLinkedOpenHashMap<List<Runnable>> long2objectlinkedopenhashmap : this.queuesPerPriority) {
            List<Runnable> list = (List) long2objectlinkedopenhashmap.get(pos);

            if (list != null) {
                if (unschedule) {
                    list.clear();
                }

                if (list.isEmpty()) {
                    long2objectlinkedopenhashmap.remove(pos);
                }
            }
        }

        while (this.hasWork() && ((Long2ObjectLinkedOpenHashMap) this.queuesPerPriority.get(this.topPriorityQueueIndex)).isEmpty()) {
            ++this.topPriorityQueueIndex;
        }

    }

    public ChunkTaskPriorityQueue.@Nullable TasksForChunk pop() {
        if (!this.hasWork()) {
            return null;
        } else {
            int i = this.topPriorityQueueIndex;
            Long2ObjectLinkedOpenHashMap<List<Runnable>> long2objectlinkedopenhashmap = (Long2ObjectLinkedOpenHashMap) this.queuesPerPriority.get(i);
            long j = long2objectlinkedopenhashmap.firstLongKey();

            List<Runnable> list;

            for (list = (List) long2objectlinkedopenhashmap.removeFirst(); this.hasWork() && ((Long2ObjectLinkedOpenHashMap) this.queuesPerPriority.get(this.topPriorityQueueIndex)).isEmpty(); ++this.topPriorityQueueIndex) {
                ;
            }

            return new ChunkTaskPriorityQueue.TasksForChunk(j, list);
        }
    }

    public boolean hasWork() {
        return this.topPriorityQueueIndex < ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT;
    }

    public String toString() {
        return this.name + " " + this.topPriorityQueueIndex + "...";
    }

    public static record TasksForChunk(long chunkPos, List<Runnable> tasks) {

    }
}

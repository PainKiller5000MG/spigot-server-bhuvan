package net.minecraft.util;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Queues;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Deque;
import org.jspecify.annotations.Nullable;

public final class SequencedPriorityIterator<T> extends AbstractIterator<T> {

    private static final int MIN_PRIO = Integer.MIN_VALUE;
    private @Nullable Deque<T> highestPrioQueue = null;
    private int highestPrio = Integer.MIN_VALUE;
    private final Int2ObjectMap<Deque<T>> queuesByPriority = new Int2ObjectOpenHashMap();

    public SequencedPriorityIterator() {}

    public void add(T data, int priority) {
        if (priority == this.highestPrio && this.highestPrioQueue != null) {
            this.highestPrioQueue.addLast(data);
        } else {
            Deque<T> deque = (Deque) this.queuesByPriority.computeIfAbsent(priority, (j) -> {
                return Queues.newArrayDeque();
            });

            deque.addLast(data);
            if (priority >= this.highestPrio) {
                this.highestPrioQueue = deque;
                this.highestPrio = priority;
            }

        }
    }

    protected @Nullable T computeNext() {
        if (this.highestPrioQueue == null) {
            return (T) this.endOfData();
        } else {
            T t0 = (T) this.highestPrioQueue.removeFirst();

            if (t0 == null) {
                return (T) this.endOfData();
            } else {
                if (this.highestPrioQueue.isEmpty()) {
                    this.switchCacheToNextHighestPrioQueue();
                }

                return t0;
            }
        }
    }

    private void switchCacheToNextHighestPrioQueue() {
        int i = Integer.MIN_VALUE;
        Deque<T> deque = null;
        ObjectIterator objectiterator = Int2ObjectMaps.fastIterable(this.queuesByPriority).iterator();

        while (objectiterator.hasNext()) {
            Int2ObjectMap.Entry<Deque<T>> int2objectmap_entry = (Entry) objectiterator.next();
            Deque<T> deque1 = (Deque) int2objectmap_entry.getValue();
            int j = int2objectmap_entry.getIntKey();

            if (j > i && !deque1.isEmpty()) {
                i = j;
                deque = deque1;
                if (j == this.highestPrio - 1) {
                    break;
                }
            }
        }

        this.highestPrio = i;
        this.highestPrioQueue = deque;
    }
}

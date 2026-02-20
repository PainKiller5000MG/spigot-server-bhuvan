package net.minecraft.util.thread;

import com.google.common.collect.Queues;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

public interface StrictQueue<T extends Runnable> {

    @Nullable
    Runnable pop();

    boolean push(T t);

    boolean isEmpty();

    int size();

    public static final class QueueStrictQueue implements StrictQueue<Runnable> {

        private final Queue<Runnable> queue;

        public QueueStrictQueue(Queue<Runnable> queue) {
            this.queue = queue;
        }

        @Override
        public @Nullable Runnable pop() {
            return (Runnable) this.queue.poll();
        }

        @Override
        public boolean push(Runnable t) {
            return this.queue.add(t);
        }

        @Override
        public boolean isEmpty() {
            return this.queue.isEmpty();
        }

        @Override
        public int size() {
            return this.queue.size();
        }
    }

    public static record RunnableWithPriority(int priority, Runnable task) implements Runnable {

        public void run() {
            this.task.run();
        }
    }

    public static final class FixedPriorityQueue implements StrictQueue<StrictQueue.RunnableWithPriority> {

        private final Queue<Runnable>[] queues;
        private final AtomicInteger size = new AtomicInteger();

        public FixedPriorityQueue(int size) {
            this.queues = new Queue[size];

            for (int j = 0; j < size; ++j) {
                this.queues[j] = Queues.newConcurrentLinkedQueue();
            }

        }

        @Override
        public @Nullable Runnable pop() {
            for (Queue<Runnable> queue : this.queues) {
                Runnable runnable = (Runnable) queue.poll();

                if (runnable != null) {
                    this.size.decrementAndGet();
                    return runnable;
                }
            }

            return null;
        }

        public boolean push(StrictQueue.RunnableWithPriority task) {
            int i = task.priority;

            if (i < this.queues.length && i >= 0) {
                this.queues[i].add(task);
                this.size.incrementAndGet();
                return true;
            } else {
                throw new IndexOutOfBoundsException(String.format(Locale.ROOT, "Priority %d not supported. Expected range [0-%d]", i, this.queues.length - 1));
            }
        }

        @Override
        public boolean isEmpty() {
            return this.size.get() == 0;
        }

        @Override
        public int size() {
            return this.size.get();
        }
    }
}

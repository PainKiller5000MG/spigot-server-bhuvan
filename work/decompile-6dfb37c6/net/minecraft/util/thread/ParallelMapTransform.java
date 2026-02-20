package net.minecraft.util.thread;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class ParallelMapTransform {

    private static final int DEFAULT_TASKS_PER_THREAD = 16;

    public ParallelMapTransform() {}

    public static <K, U, V> CompletableFuture<Map<K, V>> schedule(Map<K, U> input, BiFunction<K, U, @Nullable V> operation, int maxTaskCount, Executor executor) {
        int j = input.size();

        if (j == 0) {
            return CompletableFuture.completedFuture(Map.of());
        } else if (j == 1) {
            Map.Entry<K, U> map_entry = (Entry) input.entrySet().iterator().next();
            K k0 = (K) map_entry.getKey();
            U u0 = (U) map_entry.getValue();

            return CompletableFuture.supplyAsync(() -> {
                V v0 = (V) operation.apply(k0, u0);

                return v0 != null ? Map.of(k0, v0) : Map.of();
            }, executor);
        } else {
            ParallelMapTransform.SplitterBase<K, U, V> parallelmaptransform_splitterbase = (ParallelMapTransform.SplitterBase<K, U, V>) (j <= maxTaskCount ? new ParallelMapTransform.SingleTaskSplitter(operation, j) : new ParallelMapTransform.BatchedTaskSplitter(operation, j, maxTaskCount));

            return parallelmaptransform_splitterbase.scheduleTasks(input, executor);
        }
    }

    public static <K, U, V> CompletableFuture<Map<K, V>> schedule(Map<K, U> input, BiFunction<K, U, @Nullable V> operation, Executor executor) {
        int i = Util.maxAllowedExecutorThreads() * 16;

        return schedule(input, operation, i, executor);
    }

    private static record Container<K, U, V>(BiFunction<K, U, V> operation, @Nullable Object[] keys, @Nullable Object[] values) {

        public Container(BiFunction<K, U, V> operation, int size) {
            this(operation, new Object[size], new Object[size]);
        }

        public void put(int index, K key, U input) {
            this.keys[index] = key;
            this.values[index] = input;
        }

        private @Nullable K key(int index) {
            return (K) this.keys[index];
        }

        private @Nullable V output(int index) {
            return (V) this.values[index];
        }

        private @Nullable U input(int index) {
            return (U) this.values[index];
        }

        public void applyOperation(int index) {
            this.values[index] = this.operation.apply(this.key(index), this.input(index));
        }

        public void copyOut(int index, Map<K, V> output) {
            V v0 = (V) this.output(index);

            if (v0 != null) {
                K k0 = (K) this.key(index);

                output.put(k0, v0);
            }

        }

        public int size() {
            return this.keys.length;
        }
    }

    private abstract static class SplitterBase<K, U, V> {

        private int lastScheduledIndex;
        private int currentIndex;
        private final CompletableFuture<?>[] tasks;
        private int batchIndex;
        private final ParallelMapTransform.Container<K, U, V> container;

        private SplitterBase(BiFunction<K, U, V> operation, int size, int taskCount) {
            this.container = new ParallelMapTransform.Container<K, U, V>(operation, size);
            this.tasks = new CompletableFuture[taskCount];
        }

        private int pendingBatchSize() {
            return this.currentIndex - this.lastScheduledIndex;
        }

        public CompletableFuture<Map<K, V>> scheduleTasks(Map<K, U> input, Executor executor) {
            input.forEach((object, object1) -> {
                this.container.put(this.currentIndex++, object, object1);
                if (this.pendingBatchSize() == this.batchSize(this.batchIndex)) {
                    this.tasks[this.batchIndex++] = this.scheduleBatch(this.container, this.lastScheduledIndex, this.currentIndex, executor);
                    this.lastScheduledIndex = this.currentIndex;
                }

            });

            assert this.currentIndex == this.container.size();

            assert this.lastScheduledIndex == this.currentIndex;

            assert this.batchIndex == this.tasks.length;

            return this.scheduleFinalOperation(CompletableFuture.allOf(this.tasks), this.container);
        }

        protected abstract int batchSize(int index);

        protected abstract CompletableFuture<?> scheduleBatch(ParallelMapTransform.Container<K, U, V> container, int startIndex, int endIndex, Executor executor);

        protected abstract CompletableFuture<Map<K, V>> scheduleFinalOperation(CompletableFuture<?> allTasksDone, ParallelMapTransform.Container<K, U, V> container);
    }

    private static class SingleTaskSplitter<K, U, V> extends ParallelMapTransform.SplitterBase<K, U, V> {

        private SingleTaskSplitter(BiFunction<K, U, V> operation, int size) {
            super(operation, size, size);
        }

        @Override
        protected int batchSize(int index) {
            return 1;
        }

        @Override
        protected CompletableFuture<?> scheduleBatch(ParallelMapTransform.Container<K, U, V> container, int startIndex, int endIndex, Executor executor) {
            assert startIndex + 1 == endIndex;

            return CompletableFuture.runAsync(() -> {
                container.applyOperation(startIndex);
            }, executor);
        }

        @Override
        protected CompletableFuture<Map<K, V>> scheduleFinalOperation(CompletableFuture<?> allTasksDone, ParallelMapTransform.Container<K, U, V> container) {
            return allTasksDone.thenApply((object) -> {
                Map<K, V> map = new HashMap(container.size());

                for (int i = 0; i < container.size(); ++i) {
                    container.copyOut(i, map);
                }

                return map;
            });
        }
    }

    private static class BatchedTaskSplitter<K, U, V> extends ParallelMapTransform.SplitterBase<K, U, V> {

        private final Map<K, V> result;
        private final int batchSize;
        private final int firstUndersizedBatchIndex;

        private BatchedTaskSplitter(BiFunction<K, U, V> operation, int size, int maxTasks) {
            super(operation, size, maxTasks);
            this.result = new HashMap(size);
            this.batchSize = Mth.positiveCeilDiv(size, maxTasks);
            int k = this.batchSize * maxTasks;
            int l = k - size;

            this.firstUndersizedBatchIndex = maxTasks - l;

            assert this.firstUndersizedBatchIndex > 0 && this.firstUndersizedBatchIndex <= maxTasks;
        }

        @Override
        protected CompletableFuture<?> scheduleBatch(ParallelMapTransform.Container<K, U, V> container, int startIndex, int endIndex, Executor executor) {
            int k = endIndex - startIndex;

            assert k == this.batchSize || k == this.batchSize - 1;

            return CompletableFuture.runAsync(createTask(this.result, startIndex, endIndex, container), executor);
        }

        @Override
        protected int batchSize(int index) {
            return index < this.firstUndersizedBatchIndex ? this.batchSize : this.batchSize - 1;
        }

        private static <K, U, V> Runnable createTask(Map<K, V> result, int startIndex, int endIndex, ParallelMapTransform.Container<K, U, V> container) {
            return () -> {
                for (int k = startIndex; k < endIndex; ++k) {
                    container.applyOperation(k);
                }

                synchronized (result) {
                    for (int l = startIndex; l < endIndex; ++l) {
                        container.copyOut(l, result);
                    }

                }
            };
        }

        @Override
        protected CompletableFuture<Map<K, V>> scheduleFinalOperation(CompletableFuture<?> allTasksDone, ParallelMapTransform.Container<K, U, V> container) {
            Map<K, V> map = this.result;

            return allTasksDone.thenApply((object) -> {
                return map;
            });
        }
    }
}

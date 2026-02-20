package net.minecraft.world.ticks;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class LevelTicks<T> implements LevelTickAccess<T> {

    private static final Comparator<LevelChunkTicks<?>> CONTAINER_DRAIN_ORDER = (levelchunkticks, levelchunkticks1) -> {
        return ScheduledTick.INTRA_TICK_DRAIN_ORDER.compare(levelchunkticks.peek(), levelchunkticks1.peek());
    };
    private final LongPredicate tickCheck;
    private final Long2ObjectMap<LevelChunkTicks<T>> allContainers = new Long2ObjectOpenHashMap();
    private final Long2LongMap nextTickForContainer = (Long2LongMap) Util.make(new Long2LongOpenHashMap(), (long2longopenhashmap) -> {
        long2longopenhashmap.defaultReturnValue(Long.MAX_VALUE);
    });
    private final Queue<LevelChunkTicks<T>> containersToTick;
    private final Queue<ScheduledTick<T>> toRunThisTick;
    private final List<ScheduledTick<T>> alreadyRunThisTick;
    private final Set<ScheduledTick<?>> toRunThisTickSet;
    private final BiConsumer<LevelChunkTicks<T>, ScheduledTick<T>> chunkScheduleUpdater;

    public LevelTicks(LongPredicate tickCheck) {
        this.containersToTick = new PriorityQueue(LevelTicks.CONTAINER_DRAIN_ORDER);
        this.toRunThisTick = new ArrayDeque();
        this.alreadyRunThisTick = new ArrayList();
        this.toRunThisTickSet = new ObjectOpenCustomHashSet(ScheduledTick.UNIQUE_TICK_HASH);
        this.chunkScheduleUpdater = (levelchunkticks, scheduledtick) -> {
            if (scheduledtick.equals(levelchunkticks.peek())) {
                this.updateContainerScheduling(scheduledtick);
            }

        };
        this.tickCheck = tickCheck;
    }

    public void addContainer(ChunkPos pos, LevelChunkTicks<T> container) {
        long i = pos.toLong();

        this.allContainers.put(i, container);
        ScheduledTick<T> scheduledtick = container.peek();

        if (scheduledtick != null) {
            this.nextTickForContainer.put(i, scheduledtick.triggerTick());
        }

        container.setOnTickAdded(this.chunkScheduleUpdater);
    }

    public void removeContainer(ChunkPos pos) {
        long i = pos.toLong();
        LevelChunkTicks<T> levelchunkticks = (LevelChunkTicks) this.allContainers.remove(i);

        this.nextTickForContainer.remove(i);
        if (levelchunkticks != null) {
            levelchunkticks.setOnTickAdded((BiConsumer) null);
        }

    }

    @Override
    public void schedule(ScheduledTick<T> tick) {
        long i = ChunkPos.asLong(tick.pos());
        LevelChunkTicks<T> levelchunkticks = (LevelChunkTicks) this.allContainers.get(i);

        if (levelchunkticks == null) {
            Util.logAndPauseIfInIde("Trying to schedule tick in not loaded position " + String.valueOf(tick.pos()));
        } else {
            levelchunkticks.schedule(tick);
        }
    }

    public void tick(long currentTick, int maxTicksToProcess, BiConsumer<BlockPos, T> output) {
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("collect");
        this.collectTicks(currentTick, maxTicksToProcess, profilerfiller);
        profilerfiller.popPush("run");
        profilerfiller.incrementCounter("ticksToRun", this.toRunThisTick.size());
        this.runCollectedTicks(output);
        profilerfiller.popPush("cleanup");
        this.cleanupAfterTick();
        profilerfiller.pop();
    }

    private void collectTicks(long currentTick, int maxTicksToProcess, ProfilerFiller profiler) {
        this.sortContainersToTick(currentTick);
        profiler.incrementCounter("containersToTick", this.containersToTick.size());
        this.drainContainers(currentTick, maxTicksToProcess);
        this.rescheduleLeftoverContainers();
    }

    private void sortContainersToTick(long currentTick) {
        ObjectIterator<Entry> objectiterator = Long2LongMaps.fastIterator(this.nextTickForContainer);

        while (objectiterator.hasNext()) {
            Entry entry = (Entry) objectiterator.next();
            long j = entry.getLongKey();
            long k = entry.getLongValue();

            if (k <= currentTick) {
                LevelChunkTicks<T> levelchunkticks = (LevelChunkTicks) this.allContainers.get(j);

                if (levelchunkticks == null) {
                    objectiterator.remove();
                } else {
                    ScheduledTick<T> scheduledtick = levelchunkticks.peek();

                    if (scheduledtick == null) {
                        objectiterator.remove();
                    } else if (scheduledtick.triggerTick() > currentTick) {
                        entry.setValue(scheduledtick.triggerTick());
                    } else if (this.tickCheck.test(j)) {
                        objectiterator.remove();
                        this.containersToTick.add(levelchunkticks);
                    }
                }
            }
        }

    }

    private void drainContainers(long currentTick, int maxTicksToProcess) {
        LevelChunkTicks<T> levelchunkticks;

        while (this.canScheduleMoreTicks(maxTicksToProcess) && (levelchunkticks = (LevelChunkTicks) this.containersToTick.poll()) != null) {
            ScheduledTick<T> scheduledtick = levelchunkticks.poll();

            this.scheduleForThisTick(scheduledtick);
            this.drainFromCurrentContainer(this.containersToTick, levelchunkticks, currentTick, maxTicksToProcess);
            ScheduledTick<T> scheduledtick1 = levelchunkticks.peek();

            if (scheduledtick1 != null) {
                if (scheduledtick1.triggerTick() <= currentTick && this.canScheduleMoreTicks(maxTicksToProcess)) {
                    this.containersToTick.add(levelchunkticks);
                } else {
                    this.updateContainerScheduling(scheduledtick1);
                }
            }
        }

    }

    private void rescheduleLeftoverContainers() {
        for (LevelChunkTicks<T> levelchunkticks : this.containersToTick) {
            this.updateContainerScheduling(levelchunkticks.peek());
        }

    }

    private void updateContainerScheduling(ScheduledTick<T> nextTick) {
        this.nextTickForContainer.put(ChunkPos.asLong(nextTick.pos()), nextTick.triggerTick());
    }

    private void drainFromCurrentContainer(Queue<LevelChunkTicks<T>> containersToTick, LevelChunkTicks<T> currentContainer, long currentTick, int maxTicksToProcess) {
        if (this.canScheduleMoreTicks(maxTicksToProcess)) {
            LevelChunkTicks<T> levelchunkticks1 = (LevelChunkTicks) containersToTick.peek();
            ScheduledTick<T> scheduledtick = levelchunkticks1 != null ? levelchunkticks1.peek() : null;

            while (this.canScheduleMoreTicks(maxTicksToProcess)) {
                ScheduledTick<T> scheduledtick1 = currentContainer.peek();

                if (scheduledtick1 == null || scheduledtick1.triggerTick() > currentTick || scheduledtick != null && ScheduledTick.INTRA_TICK_DRAIN_ORDER.compare(scheduledtick1, scheduledtick) > 0) {
                    break;
                }

                currentContainer.poll();
                this.scheduleForThisTick(scheduledtick1);
            }

        }
    }

    private void scheduleForThisTick(ScheduledTick<T> tick) {
        this.toRunThisTick.add(tick);
    }

    private boolean canScheduleMoreTicks(int maxTicksToProcess) {
        return this.toRunThisTick.size() < maxTicksToProcess;
    }

    private void runCollectedTicks(BiConsumer<BlockPos, T> output) {
        while (!this.toRunThisTick.isEmpty()) {
            ScheduledTick<T> scheduledtick = (ScheduledTick) this.toRunThisTick.poll();

            if (!this.toRunThisTickSet.isEmpty()) {
                this.toRunThisTickSet.remove(scheduledtick);
            }

            this.alreadyRunThisTick.add(scheduledtick);
            output.accept(scheduledtick.pos(), scheduledtick.type());
        }

    }

    private void cleanupAfterTick() {
        this.toRunThisTick.clear();
        this.containersToTick.clear();
        this.alreadyRunThisTick.clear();
        this.toRunThisTickSet.clear();
    }

    @Override
    public boolean hasScheduledTick(BlockPos pos, T block) {
        LevelChunkTicks<T> levelchunkticks = (LevelChunkTicks) this.allContainers.get(ChunkPos.asLong(pos));

        return levelchunkticks != null && levelchunkticks.hasScheduledTick(pos, block);
    }

    @Override
    public boolean willTickThisTick(BlockPos pos, T type) {
        this.calculateTickSetIfNeeded();
        return this.toRunThisTickSet.contains(ScheduledTick.probe(type, pos));
    }

    private void calculateTickSetIfNeeded() {
        if (this.toRunThisTickSet.isEmpty() && !this.toRunThisTick.isEmpty()) {
            this.toRunThisTickSet.addAll(this.toRunThisTick);
        }

    }

    private void forContainersInArea(BoundingBox bb, LevelTicks.PosAndContainerConsumer<T> ouput) {
        int i = SectionPos.posToSectionCoord((double) bb.minX());
        int j = SectionPos.posToSectionCoord((double) bb.minZ());
        int k = SectionPos.posToSectionCoord((double) bb.maxX());
        int l = SectionPos.posToSectionCoord((double) bb.maxZ());

        for (int i1 = i; i1 <= k; ++i1) {
            for (int j1 = j; j1 <= l; ++j1) {
                long k1 = ChunkPos.asLong(i1, j1);
                LevelChunkTicks<T> levelchunkticks = (LevelChunkTicks) this.allContainers.get(k1);

                if (levelchunkticks != null) {
                    ouput.accept(k1, levelchunkticks);
                }
            }
        }

    }

    public void clearArea(BoundingBox area) {
        Predicate<ScheduledTick<T>> predicate = (scheduledtick) -> {
            return area.isInside(scheduledtick.pos());
        };

        this.forContainersInArea(area, (i, levelchunkticks) -> {
            ScheduledTick<T> scheduledtick = levelchunkticks.peek();

            levelchunkticks.removeIf(predicate);
            ScheduledTick<T> scheduledtick1 = levelchunkticks.peek();

            if (scheduledtick1 != scheduledtick) {
                if (scheduledtick1 != null) {
                    this.updateContainerScheduling(scheduledtick1);
                } else {
                    this.nextTickForContainer.remove(i);
                }
            }

        });
        this.alreadyRunThisTick.removeIf(predicate);
        this.toRunThisTick.removeIf(predicate);
    }

    public void copyArea(BoundingBox area, Vec3i offset) {
        this.copyAreaFrom(this, area, offset);
    }

    public void copyAreaFrom(LevelTicks<T> source, BoundingBox area, Vec3i offset) {
        List<ScheduledTick<T>> list = new ArrayList();
        Predicate<ScheduledTick<T>> predicate = (scheduledtick) -> {
            return area.isInside(scheduledtick.pos());
        };
        Stream stream = source.alreadyRunThisTick.stream().filter(predicate);

        Objects.requireNonNull(list);
        stream.forEach(list::add);
        stream = source.toRunThisTick.stream().filter(predicate);
        Objects.requireNonNull(list);
        stream.forEach(list::add);
        source.forContainersInArea(area, (i, levelchunkticks) -> {
            Stream stream1 = levelchunkticks.getAll().filter(predicate);

            Objects.requireNonNull(list);
            stream1.forEach(list::add);
        });
        LongSummaryStatistics longsummarystatistics = list.stream().mapToLong(ScheduledTick::subTickOrder).summaryStatistics();
        long i = longsummarystatistics.getMin();
        long j = longsummarystatistics.getMax();

        list.forEach((scheduledtick) -> {
            this.schedule(new ScheduledTick(scheduledtick.type(), scheduledtick.pos().offset(offset), scheduledtick.triggerTick(), scheduledtick.priority(), scheduledtick.subTickOrder() - i + j + 1L));
        });
    }

    @Override
    public int count() {
        return this.allContainers.values().stream().mapToInt(TickAccess::count).sum();
    }

    @FunctionalInterface
    private interface PosAndContainerConsumer<T> {

        void accept(long pos, LevelChunkTicks<T> container);
    }
}

package net.minecraft.world.ticks;

import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

public class LevelChunkTicks<T> implements TickContainerAccess<T>, SerializableTickContainer<T> {

    private final Queue<ScheduledTick<T>> tickQueue;
    private @Nullable List<SavedTick<T>> pendingTicks;
    private final Set<ScheduledTick<?>> ticksPerPosition;
    private @Nullable BiConsumer<LevelChunkTicks<T>, ScheduledTick<T>> onTickAdded;

    public LevelChunkTicks() {
        this.tickQueue = new PriorityQueue(ScheduledTick.DRAIN_ORDER);
        this.ticksPerPosition = new ObjectOpenCustomHashSet(ScheduledTick.UNIQUE_TICK_HASH);
    }

    public LevelChunkTicks(List<SavedTick<T>> pendingTicks) {
        this.tickQueue = new PriorityQueue(ScheduledTick.DRAIN_ORDER);
        this.ticksPerPosition = new ObjectOpenCustomHashSet(ScheduledTick.UNIQUE_TICK_HASH);
        this.pendingTicks = pendingTicks;

        for (SavedTick<T> savedtick : pendingTicks) {
            this.ticksPerPosition.add(ScheduledTick.probe(savedtick.type(), savedtick.pos()));
        }

    }

    public void setOnTickAdded(@Nullable BiConsumer<LevelChunkTicks<T>, ScheduledTick<T>> onTickAdded) {
        this.onTickAdded = onTickAdded;
    }

    public @Nullable ScheduledTick<T> peek() {
        return (ScheduledTick) this.tickQueue.peek();
    }

    public @Nullable ScheduledTick<T> poll() {
        ScheduledTick<T> scheduledtick = (ScheduledTick) this.tickQueue.poll();

        if (scheduledtick != null) {
            this.ticksPerPosition.remove(scheduledtick);
        }

        return scheduledtick;
    }

    @Override
    public void schedule(ScheduledTick<T> tick) {
        if (this.ticksPerPosition.add(tick)) {
            this.scheduleUnchecked(tick);
        }

    }

    private void scheduleUnchecked(ScheduledTick<T> tick) {
        this.tickQueue.add(tick);
        if (this.onTickAdded != null) {
            this.onTickAdded.accept(this, tick);
        }

    }

    @Override
    public boolean hasScheduledTick(BlockPos pos, T type) {
        return this.ticksPerPosition.contains(ScheduledTick.probe(type, pos));
    }

    public void removeIf(Predicate<ScheduledTick<T>> test) {
        Iterator<ScheduledTick<T>> iterator = this.tickQueue.iterator();

        while (iterator.hasNext()) {
            ScheduledTick<T> scheduledtick = (ScheduledTick) iterator.next();

            if (test.test(scheduledtick)) {
                iterator.remove();
                this.ticksPerPosition.remove(scheduledtick);
            }
        }

    }

    public Stream<ScheduledTick<T>> getAll() {
        return this.tickQueue.stream();
    }

    @Override
    public int count() {
        return this.tickQueue.size() + (this.pendingTicks != null ? this.pendingTicks.size() : 0);
    }

    @Override
    public List<SavedTick<T>> pack(long currentTick) {
        List<SavedTick<T>> list = new ArrayList(this.tickQueue.size());

        if (this.pendingTicks != null) {
            list.addAll(this.pendingTicks);
        }

        for (ScheduledTick<T> scheduledtick : this.tickQueue) {
            list.add(scheduledtick.toSavedTick(currentTick));
        }

        return list;
    }

    public void unpack(long currentTick) {
        if (this.pendingTicks != null) {
            int j = -this.pendingTicks.size();

            for (SavedTick<T> savedtick : this.pendingTicks) {
                this.scheduleUnchecked(savedtick.unpack(currentTick, (long) (j++)));
            }
        }

        this.pendingTicks = null;
    }
}

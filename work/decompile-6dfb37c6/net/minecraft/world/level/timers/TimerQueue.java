package net.minecraft.world.level.timers;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.primitives.UnsignedLong;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;

public class TimerQueue<T> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CALLBACK_DATA_TAG = "Callback";
    private static final String TIMER_NAME_TAG = "Name";
    private static final String TIMER_TRIGGER_TIME_TAG = "TriggerTime";
    private final TimerCallbacks<T> callbacksRegistry;
    private final Queue<TimerQueue.Event<T>> queue;
    private UnsignedLong sequentialId;
    private final Table<String, Long, TimerQueue.Event<T>> events;

    private static <T> Comparator<TimerQueue.Event<T>> createComparator() {
        return Comparator.comparingLong((timerqueue_event) -> {
            return timerqueue_event.triggerTime;
        }).thenComparing((timerqueue_event) -> {
            return timerqueue_event.sequentialId;
        });
    }

    public TimerQueue(TimerCallbacks<T> callbacksRegistry, Stream<? extends Dynamic<?>> eventData) {
        this(callbacksRegistry);
        this.queue.clear();
        this.events.clear();
        this.sequentialId = UnsignedLong.ZERO;
        eventData.forEach((dynamic) -> {
            Tag tag = (Tag) dynamic.convert(NbtOps.INSTANCE).getValue();

            if (tag instanceof CompoundTag compoundtag) {
                this.loadEvent(compoundtag);
            } else {
                TimerQueue.LOGGER.warn("Invalid format of events: {}", tag);
            }

        });
    }

    public TimerQueue(TimerCallbacks<T> callbacksRegistry) {
        this.queue = new PriorityQueue(createComparator());
        this.sequentialId = UnsignedLong.ZERO;
        this.events = HashBasedTable.create();
        this.callbacksRegistry = callbacksRegistry;
    }

    public void tick(T context, long currentTick) {
        while (true) {
            TimerQueue.Event<T> timerqueue_event = (TimerQueue.Event) this.queue.peek();

            if (timerqueue_event == null || timerqueue_event.triggerTime > currentTick) {
                return;
            }

            this.queue.remove();
            this.events.remove(timerqueue_event.id, currentTick);
            timerqueue_event.callback.handle(context, this, currentTick);
        }
    }

    public void schedule(String id, long time, TimerCallback<T> callback) {
        if (!this.events.contains(id, time)) {
            this.sequentialId = this.sequentialId.plus(UnsignedLong.ONE);
            TimerQueue.Event<T> timerqueue_event = new TimerQueue.Event<T>(time, this.sequentialId, id, callback);

            this.events.put(id, time, timerqueue_event);
            this.queue.add(timerqueue_event);
        }
    }

    public int remove(String id) {
        Collection<TimerQueue.Event<T>> collection = this.events.row(id).values();
        Queue queue = this.queue;

        Objects.requireNonNull(this.queue);
        collection.forEach(queue::remove);
        int i = collection.size();

        collection.clear();
        return i;
    }

    public Set<String> getEventsIds() {
        return Collections.unmodifiableSet(this.events.rowKeySet());
    }

    private void loadEvent(CompoundTag tag) {
        TimerCallback<T> timercallback = (TimerCallback) tag.read("Callback", this.callbacksRegistry.codec()).orElse((Object) null);

        if (timercallback != null) {
            String s = tag.getStringOr("Name", "");
            long i = tag.getLongOr("TriggerTime", 0L);

            this.schedule(s, i, timercallback);
        }

    }

    private CompoundTag storeEvent(TimerQueue.Event<T> event) {
        CompoundTag compoundtag = new CompoundTag();

        compoundtag.putString("Name", event.id);
        compoundtag.putLong("TriggerTime", event.triggerTime);
        compoundtag.store("Callback", this.callbacksRegistry.codec(), event.callback);
        return compoundtag;
    }

    public ListTag store() {
        ListTag listtag = new ListTag();
        Stream stream = this.queue.stream().sorted(createComparator()).map(this::storeEvent);

        Objects.requireNonNull(listtag);
        stream.forEach(listtag::add);
        return listtag;
    }

    public static class Event<T> {

        public final long triggerTime;
        public final UnsignedLong sequentialId;
        public final String id;
        public final TimerCallback<T> callback;

        private Event(long triggerTime, UnsignedLong sequentialId, String id, TimerCallback<T> callback) {
            this.triggerTime = triggerTime;
            this.sequentialId = sequentialId;
            this.id = id;
            this.callback = callback;
        }
    }
}

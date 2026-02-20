package net.minecraft.world.ticks;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.Hash.Strategy;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

public record ScheduledTick<T>(T type, BlockPos pos, long triggerTick, TickPriority priority, long subTickOrder) {

    public static final Comparator<ScheduledTick<?>> DRAIN_ORDER = (scheduledtick, scheduledtick1) -> {
        int i = Long.compare(scheduledtick.triggerTick, scheduledtick1.triggerTick);

        if (i != 0) {
            return i;
        } else {
            i = scheduledtick.priority.compareTo(scheduledtick1.priority);
            return i != 0 ? i : Long.compare(scheduledtick.subTickOrder, scheduledtick1.subTickOrder);
        }
    };
    public static final Comparator<ScheduledTick<?>> INTRA_TICK_DRAIN_ORDER = (scheduledtick, scheduledtick1) -> {
        int i = scheduledtick.priority.compareTo(scheduledtick1.priority);

        return i != 0 ? i : Long.compare(scheduledtick.subTickOrder, scheduledtick1.subTickOrder);
    };
    public static final Hash.Strategy<ScheduledTick<?>> UNIQUE_TICK_HASH = new Hash.Strategy<ScheduledTick<?>>() {
        public int hashCode(ScheduledTick<?> o) {
            return 31 * o.pos().hashCode() + o.type().hashCode();
        }

        public boolean equals(@Nullable ScheduledTick<?> a, @Nullable ScheduledTick<?> b) {
            return a == b ? true : (a != null && b != null ? a.type() == b.type() && a.pos().equals(b.pos()) : false);
        }
    };

    public ScheduledTick(T type, BlockPos pos, long triggerTick, long subTickOrder) {
        this(type, pos, triggerTick, TickPriority.NORMAL, subTickOrder);
    }

    public ScheduledTick(T type, BlockPos pos, long triggerTick, TickPriority priority, long subTickOrder) {
        pos = pos.immutable();
        this.type = type;
        this.pos = pos;
        this.triggerTick = triggerTick;
        this.priority = priority;
        this.subTickOrder = subTickOrder;
    }

    public static <T> ScheduledTick<T> probe(T type, BlockPos pos) {
        return new ScheduledTick<T>(type, pos, 0L, TickPriority.NORMAL, 0L);
    }

    public SavedTick<T> toSavedTick(long currentTick) {
        return new SavedTick<T>(this.type, this.pos, (int) (this.triggerTick - currentTick), this.priority);
    }
}

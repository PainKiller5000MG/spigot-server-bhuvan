package net.minecraft.world.entity.ai.behavior;

import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public abstract class Behavior<E extends LivingEntity> implements BehaviorControl<E> {

    public static final int DEFAULT_DURATION = 60;
    protected final Map<MemoryModuleType<?>, MemoryStatus> entryCondition;
    private Behavior.Status status;
    private long endTimestamp;
    private final int minDuration;
    private final int maxDuration;

    public Behavior(Map<MemoryModuleType<?>, MemoryStatus> entryCondition) {
        this(entryCondition, 60);
    }

    public Behavior(Map<MemoryModuleType<?>, MemoryStatus> entryCondition, int timeOutDuration) {
        this(entryCondition, timeOutDuration, timeOutDuration);
    }

    public Behavior(Map<MemoryModuleType<?>, MemoryStatus> entryCondition, int minDuration, int maxDuration) {
        this.status = Behavior.Status.STOPPED;
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
        this.entryCondition = entryCondition;
    }

    @Override
    public Behavior.Status getStatus() {
        return this.status;
    }

    @Override
    public final boolean tryStart(ServerLevel level, E body, long timestamp) {
        if (this.hasRequiredMemories(body) && this.checkExtraStartConditions(level, body)) {
            this.status = Behavior.Status.RUNNING;
            int j = this.minDuration + level.getRandom().nextInt(this.maxDuration + 1 - this.minDuration);

            this.endTimestamp = timestamp + (long) j;
            this.start(level, body, timestamp);
            return true;
        } else {
            return false;
        }
    }

    protected void start(ServerLevel level, E body, long timestamp) {}

    @Override
    public final void tickOrStop(ServerLevel level, E body, long timestamp) {
        if (!this.timedOut(timestamp) && this.canStillUse(level, body, timestamp)) {
            this.tick(level, body, timestamp);
        } else {
            this.doStop(level, body, timestamp);
        }

    }

    protected void tick(ServerLevel level, E body, long timestamp) {}

    @Override
    public final void doStop(ServerLevel level, E body, long timestamp) {
        this.status = Behavior.Status.STOPPED;
        this.stop(level, body, timestamp);
    }

    protected void stop(ServerLevel level, E body, long timestamp) {}

    protected boolean canStillUse(ServerLevel level, E body, long timestamp) {
        return false;
    }

    protected boolean timedOut(long timestamp) {
        return timestamp > this.endTimestamp;
    }

    protected boolean checkExtraStartConditions(ServerLevel level, E body) {
        return true;
    }

    @Override
    public String debugString() {
        return this.getClass().getSimpleName();
    }

    protected boolean hasRequiredMemories(E body) {
        for (Map.Entry<MemoryModuleType<?>, MemoryStatus> map_entry : this.entryCondition.entrySet()) {
            MemoryModuleType<?> memorymoduletype = (MemoryModuleType) map_entry.getKey();
            MemoryStatus memorystatus = (MemoryStatus) map_entry.getValue();

            if (!body.getBrain().checkMemory(memorymoduletype, memorystatus)) {
                return false;
            }
        }

        return true;
    }

    public static enum Status {

        STOPPED, RUNNING;

        private Status() {}
    }
}

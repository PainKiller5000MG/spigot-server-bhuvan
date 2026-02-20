package net.minecraft.world.entity.ai.behavior;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public class GateBehavior<E extends LivingEntity> implements BehaviorControl<E> {

    private final Map<MemoryModuleType<?>, MemoryStatus> entryCondition;
    private final Set<MemoryModuleType<?>> exitErasedMemories;
    private final GateBehavior.OrderPolicy orderPolicy;
    private final GateBehavior.RunningPolicy runningPolicy;
    private final ShufflingList<BehaviorControl<? super E>> behaviors = new ShufflingList<BehaviorControl<? super E>>();
    private Behavior.Status status;

    public GateBehavior(Map<MemoryModuleType<?>, MemoryStatus> entryCondition, Set<MemoryModuleType<?>> exitErasedMemories, GateBehavior.OrderPolicy orderPolicy, GateBehavior.RunningPolicy runningPolicy, List<Pair<? extends BehaviorControl<? super E>, Integer>> behaviors) {
        this.status = Behavior.Status.STOPPED;
        this.entryCondition = entryCondition;
        this.exitErasedMemories = exitErasedMemories;
        this.orderPolicy = orderPolicy;
        this.runningPolicy = runningPolicy;
        behaviors.forEach((pair) -> {
            this.behaviors.add((BehaviorControl) pair.getFirst(), (Integer) pair.getSecond());
        });
    }

    @Override
    public Behavior.Status getStatus() {
        return this.status;
    }

    private boolean hasRequiredMemories(E body) {
        for (Map.Entry<MemoryModuleType<?>, MemoryStatus> map_entry : this.entryCondition.entrySet()) {
            MemoryModuleType<?> memorymoduletype = (MemoryModuleType) map_entry.getKey();
            MemoryStatus memorystatus = (MemoryStatus) map_entry.getValue();

            if (!body.getBrain().checkMemory(memorymoduletype, memorystatus)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public final boolean tryStart(ServerLevel level, E body, long timestamp) {
        if (this.hasRequiredMemories(body)) {
            this.status = Behavior.Status.RUNNING;
            this.orderPolicy.apply(this.behaviors);
            this.runningPolicy.apply(this.behaviors.stream(), level, body, timestamp);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public final void tickOrStop(ServerLevel level, E body, long timestamp) {
        this.behaviors.stream().filter((behaviorcontrol) -> {
            return behaviorcontrol.getStatus() == Behavior.Status.RUNNING;
        }).forEach((behaviorcontrol) -> {
            behaviorcontrol.tickOrStop(level, body, timestamp);
        });
        if (this.behaviors.stream().noneMatch((behaviorcontrol) -> {
            return behaviorcontrol.getStatus() == Behavior.Status.RUNNING;
        })) {
            this.doStop(level, body, timestamp);
        }

    }

    @Override
    public final void doStop(ServerLevel level, E body, long timestamp) {
        this.status = Behavior.Status.STOPPED;
        this.behaviors.stream().filter((behaviorcontrol) -> {
            return behaviorcontrol.getStatus() == Behavior.Status.RUNNING;
        }).forEach((behaviorcontrol) -> {
            behaviorcontrol.doStop(level, body, timestamp);
        });
        Set set = this.exitErasedMemories;
        Brain brain = ((LivingEntity) body).getBrain();

        Objects.requireNonNull(brain);
        set.forEach(brain::eraseMemory);
    }

    @Override
    public String debugString() {
        return this.getClass().getSimpleName();
    }

    public String toString() {
        Set<? extends BehaviorControl<? super E>> set = (Set) this.behaviors.stream().filter((behaviorcontrol) -> {
            return behaviorcontrol.getStatus() == Behavior.Status.RUNNING;
        }).collect(Collectors.toSet());
        String s = this.getClass().getSimpleName();

        return "(" + s + "): " + String.valueOf(set);
    }

    public static enum OrderPolicy {

        ORDERED((shufflinglist) -> {
        }), SHUFFLED(ShufflingList::shuffle);

        private final Consumer<ShufflingList<?>> consumer;

        private OrderPolicy(Consumer<ShufflingList<?>> consumer) {
            this.consumer = consumer;
        }

        public void apply(ShufflingList<?> list) {
            this.consumer.accept(list);
        }
    }

    public static enum RunningPolicy {

        RUN_ONE {
            @Override
            public <E extends LivingEntity> void apply(Stream<BehaviorControl<? super E>> behaviors, ServerLevel level, E body, long timestamp) {
                behaviors.filter((behaviorcontrol) -> {
                    return behaviorcontrol.getStatus() == Behavior.Status.STOPPED;
                }).filter((behaviorcontrol) -> {
                    return behaviorcontrol.tryStart(level, body, timestamp);
                }).findFirst();
            }
        },
        TRY_ALL {
            @Override
            public <E extends LivingEntity> void apply(Stream<BehaviorControl<? super E>> behaviors, ServerLevel level, E body, long timestamp) {
                behaviors.filter((behaviorcontrol) -> {
                    return behaviorcontrol.getStatus() == Behavior.Status.STOPPED;
                }).forEach((behaviorcontrol) -> {
                    behaviorcontrol.tryStart(level, body, timestamp);
                });
            }
        };

        private RunningPolicy() {}

        public abstract <E extends LivingEntity> void apply(Stream<BehaviorControl<? super E>> behaviors, ServerLevel level, E body, long timestamp);
    }
}

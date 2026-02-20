package net.minecraft.world.entity.ai;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Brain<E extends LivingEntity> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Supplier<Codec<Brain<E>>> codec;
    private static final int SCHEDULE_UPDATE_DELAY = 20;
    private final Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories = Maps.newHashMap();
    private final Map<SensorType<? extends Sensor<? super E>>, Sensor<? super E>> sensors = Maps.newLinkedHashMap();
    private final Map<Integer, Map<Activity, Set<BehaviorControl<? super E>>>> availableBehaviorsByPriority = Maps.newTreeMap();
    private @Nullable EnvironmentAttribute<Activity> schedule;
    private final Map<Activity, Set<Pair<MemoryModuleType<?>, MemoryStatus>>> activityRequirements = Maps.newHashMap();
    private final Map<Activity, Set<MemoryModuleType<?>>> activityMemoriesToEraseWhenStopped = Maps.newHashMap();
    private Set<Activity> coreActivities = Sets.newHashSet();
    private final Set<Activity> activeActivities = Sets.newHashSet();
    private Activity defaultActivity;
    private long lastScheduleUpdate;

    public static <E extends LivingEntity> Brain.Provider<E> provider(Collection<? extends MemoryModuleType<?>> memoryTypes, Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes) {
        return new Brain.Provider<E>(memoryTypes, sensorTypes);
    }

    public static <E extends LivingEntity> Codec<Brain<E>> codec(final Collection<? extends MemoryModuleType<?>> memoryTypes, final Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes) {
        final MutableObject<Codec<Brain<E>>> mutableobject = new MutableObject();

        mutableobject.setValue((new MapCodec<Brain<E>>() {
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return memoryTypes.stream().flatMap((memorymoduletype) -> {
                    return memorymoduletype.getCodec().map((codec) -> {
                        return BuiltInRegistries.MEMORY_MODULE_TYPE.getKey(memorymoduletype);
                    }).stream();
                }).map((identifier) -> {
                    return ops.createString(identifier.toString());
                });
            }

            public <T> DataResult<Brain<E>> decode(DynamicOps<T> ops, MapLike<T> input) {
                MutableObject<DataResult<ImmutableList.Builder<Brain.MemoryValue<?>>>> mutableobject1 = new MutableObject(DataResult.success(ImmutableList.builder()));

                input.entries().forEach((pair) -> {
                    DataResult<MemoryModuleType<?>> dataresult = BuiltInRegistries.MEMORY_MODULE_TYPE.byNameCodec().parse(ops, pair.getFirst());
                    DataResult<? extends Brain.MemoryValue<?>> dataresult1 = dataresult.flatMap((memorymoduletype) -> {
                        return this.captureRead(memorymoduletype, ops, pair.getSecond());
                    });

                    mutableobject1.setValue(((DataResult) mutableobject1.get()).apply2(Builder::add, dataresult1));
                });
                DataResult dataresult = (DataResult) mutableobject1.get();
                Logger logger = Brain.LOGGER;

                Objects.requireNonNull(logger);
                ImmutableList<Brain.MemoryValue<?>> immutablelist = (ImmutableList) dataresult.resultOrPartial(logger::error).map(Builder::build).orElseGet(ImmutableList::of);

                return DataResult.success(new Brain(memoryTypes, sensorTypes, immutablelist, mutableobject));
            }

            private <T, U> DataResult<Brain.MemoryValue<U>> captureRead(MemoryModuleType<U> type, DynamicOps<T> ops, T input) {
                return ((DataResult) type.getCodec().map(DataResult::success).orElseGet(() -> {
                    return DataResult.error(() -> {
                        return "No codec for memory: " + String.valueOf(type);
                    });
                })).flatMap((codec) -> {
                    return codec.parse(ops, input);
                }).map((expirablevalue) -> {
                    return new Brain.MemoryValue(type, Optional.of(expirablevalue));
                });
            }

            public <T> RecordBuilder<T> encode(Brain<E> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                input.memories().forEach((brain_memoryvalue) -> {
                    brain_memoryvalue.serialize(ops, prefix);
                });
                return prefix;
            }
        }).fieldOf("memories").codec());
        return (Codec) mutableobject.get();
    }

    public Brain(Collection<? extends MemoryModuleType<?>> memoryTypes, Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes, ImmutableList<Brain.MemoryValue<?>> memories, Supplier<Codec<Brain<E>>> codec) {
        this.defaultActivity = Activity.IDLE;
        this.lastScheduleUpdate = -9999L;
        this.codec = codec;

        for (MemoryModuleType<?> memorymoduletype : memoryTypes) {
            this.memories.put(memorymoduletype, Optional.empty());
        }

        for (SensorType<? extends Sensor<? super E>> sensortype : sensorTypes) {
            this.sensors.put(sensortype, sensortype.create());
        }

        for (Sensor<? super E> sensor : this.sensors.values()) {
            for (MemoryModuleType<?> memorymoduletype1 : sensor.requires()) {
                this.memories.put(memorymoduletype1, Optional.empty());
            }
        }

        UnmodifiableIterator unmodifiableiterator = memories.iterator();

        while (unmodifiableiterator.hasNext()) {
            Brain.MemoryValue<?> brain_memoryvalue = (Brain.MemoryValue) unmodifiableiterator.next();

            brain_memoryvalue.setMemoryInternal(this);
        }

    }

    public <T> DataResult<T> serializeStart(DynamicOps<T> ops) {
        return ((Codec) this.codec.get()).encodeStart(ops, this);
    }

    private Stream<Brain.MemoryValue<?>> memories() {
        return this.memories.entrySet().stream().map((entry) -> {
            return Brain.MemoryValue.createUnchecked((MemoryModuleType) entry.getKey(), (Optional) entry.getValue());
        });
    }

    public boolean hasMemoryValue(MemoryModuleType<?> type) {
        return this.checkMemory(type, MemoryStatus.VALUE_PRESENT);
    }

    public void clearMemories() {
        this.memories.keySet().forEach((memorymoduletype) -> {
            this.memories.put(memorymoduletype, Optional.empty());
        });
    }

    public <U> void eraseMemory(MemoryModuleType<U> type) {
        this.setMemory(type, Optional.empty());
    }

    public <U> void setMemory(MemoryModuleType<U> type, @Nullable U value) {
        this.setMemory(type, Optional.ofNullable(value));
    }

    public <U> void setMemoryWithExpiry(MemoryModuleType<U> type, U value, long timeToLive) {
        this.setMemoryInternal(type, Optional.of(ExpirableValue.of(value, timeToLive)));
    }

    public <U> void setMemory(MemoryModuleType<U> type, Optional<? extends U> optionalValue) {
        this.setMemoryInternal(type, optionalValue.map(ExpirableValue::of));
    }

    private <U> void setMemoryInternal(MemoryModuleType<U> type, Optional<? extends ExpirableValue<?>> optionalExpirableValue) {
        if (this.memories.containsKey(type)) {
            if (optionalExpirableValue.isPresent() && this.isEmptyCollection(((ExpirableValue) optionalExpirableValue.get()).getValue())) {
                this.eraseMemory(type);
            } else {
                this.memories.put(type, optionalExpirableValue);
            }
        }

    }

    public <U> Optional<U> getMemory(MemoryModuleType<U> type) {
        Optional<? extends ExpirableValue<?>> optional = (Optional) this.memories.get(type);

        if (optional == null) {
            throw new IllegalStateException("Unregistered memory fetched: " + String.valueOf(type));
        } else {
            return optional.map(ExpirableValue::getValue);
        }
    }

    public <U> @Nullable Optional<U> getMemoryInternal(MemoryModuleType<U> type) {
        Optional<? extends ExpirableValue<?>> optional = (Optional) this.memories.get(type);

        return optional == null ? null : optional.map(ExpirableValue::getValue);
    }

    public <U> long getTimeUntilExpiry(MemoryModuleType<U> type) {
        Optional<? extends ExpirableValue<?>> optional = (Optional) this.memories.get(type);

        return (Long) optional.map(ExpirableValue::getTimeToLive).orElse(0L);
    }

    /** @deprecated */
    @Deprecated
    @VisibleForDebug
    public Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> getMemories() {
        return this.memories;
    }

    public <U> boolean isMemoryValue(MemoryModuleType<U> memoryType, U value) {
        return !this.hasMemoryValue(memoryType) ? false : this.getMemory(memoryType).filter((object) -> {
            return object.equals(value);
        }).isPresent();
    }

    public boolean checkMemory(MemoryModuleType<?> type, MemoryStatus status) {
        Optional<? extends ExpirableValue<?>> optional = (Optional) this.memories.get(type);

        return optional == null ? false : status == MemoryStatus.REGISTERED || status == MemoryStatus.VALUE_PRESENT && optional.isPresent() || status == MemoryStatus.VALUE_ABSENT && optional.isEmpty();
    }

    public void setSchedule(EnvironmentAttribute<Activity> schedule) {
        this.schedule = schedule;
    }

    public void setCoreActivities(Set<Activity> activities) {
        this.coreActivities = activities;
    }

    /** @deprecated */
    @Deprecated
    @VisibleForDebug
    public Set<Activity> getActiveActivities() {
        return this.activeActivities;
    }

    /** @deprecated */
    @Deprecated
    @VisibleForDebug
    public List<BehaviorControl<? super E>> getRunningBehaviors() {
        List<BehaviorControl<? super E>> list = new ObjectArrayList();

        for (Map<Activity, Set<BehaviorControl<? super E>>> map : this.availableBehaviorsByPriority.values()) {
            for (Set<BehaviorControl<? super E>> set : map.values()) {
                for (BehaviorControl<? super E> behaviorcontrol : set) {
                    if (behaviorcontrol.getStatus() == Behavior.Status.RUNNING) {
                        list.add(behaviorcontrol);
                    }
                }
            }
        }

        return list;
    }

    public void useDefaultActivity() {
        this.setActiveActivity(this.defaultActivity);
    }

    public Optional<Activity> getActiveNonCoreActivity() {
        for (Activity activity : this.activeActivities) {
            if (!this.coreActivities.contains(activity)) {
                return Optional.of(activity);
            }
        }

        return Optional.empty();
    }

    public void setActiveActivityIfPossible(Activity activity) {
        if (this.activityRequirementsAreMet(activity)) {
            this.setActiveActivity(activity);
        } else {
            this.useDefaultActivity();
        }

    }

    private void setActiveActivity(Activity activity) {
        if (!this.isActive(activity)) {
            this.eraseMemoriesForOtherActivitesThan(activity);
            this.activeActivities.clear();
            this.activeActivities.addAll(this.coreActivities);
            this.activeActivities.add(activity);
        }
    }

    private void eraseMemoriesForOtherActivitesThan(Activity activity) {
        for (Activity activity1 : this.activeActivities) {
            if (activity1 != activity) {
                Set<MemoryModuleType<?>> set = (Set) this.activityMemoriesToEraseWhenStopped.get(activity1);

                if (set != null) {
                    for (MemoryModuleType<?> memorymoduletype : set) {
                        this.eraseMemory(memorymoduletype);
                    }
                }
            }
        }

    }

    public void updateActivityFromSchedule(EnvironmentAttributeSystem environmentAttributes, long gameTime, Vec3 pos) {
        if (gameTime - this.lastScheduleUpdate > 20L) {
            this.lastScheduleUpdate = gameTime;
            Activity activity = this.schedule != null ? (Activity) environmentAttributes.getValue(this.schedule, pos) : Activity.IDLE;

            if (!this.activeActivities.contains(activity)) {
                this.setActiveActivityIfPossible(activity);
            }
        }

    }

    public void setActiveActivityToFirstValid(List<Activity> activities) {
        for (Activity activity : activities) {
            if (this.activityRequirementsAreMet(activity)) {
                this.setActiveActivity(activity);
                break;
            }
        }

    }

    public void setDefaultActivity(Activity activity) {
        this.defaultActivity = activity;
    }

    public void addActivity(Activity activity, int priorityOfFirstBehavior, ImmutableList<? extends BehaviorControl<? super E>> behaviorList) {
        this.addActivity(activity, this.createPriorityPairs(priorityOfFirstBehavior, behaviorList));
    }

    public void addActivityAndRemoveMemoryWhenStopped(Activity activity, int priorityOfFirstBehavior, ImmutableList<? extends BehaviorControl<? super E>> behaviorList, MemoryModuleType<?> memoryThatMustHaveValueAndWillBeErasedAfter) {
        Set<Pair<MemoryModuleType<?>, MemoryStatus>> set = ImmutableSet.of(Pair.of(memoryThatMustHaveValueAndWillBeErasedAfter, MemoryStatus.VALUE_PRESENT));
        Set<MemoryModuleType<?>> set1 = ImmutableSet.of(memoryThatMustHaveValueAndWillBeErasedAfter);

        this.addActivityAndRemoveMemoriesWhenStopped(activity, this.createPriorityPairs(priorityOfFirstBehavior, behaviorList), set, set1);
    }

    public void addActivity(Activity activity, ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> behaviorPriorityPairs) {
        this.addActivityAndRemoveMemoriesWhenStopped(activity, behaviorPriorityPairs, ImmutableSet.of(), Sets.newHashSet());
    }

    public void addActivityWithConditions(Activity activity, int priorityOfFirstBehavior, ImmutableList<? extends BehaviorControl<? super E>> behaviorList, Set<Pair<MemoryModuleType<?>, MemoryStatus>> conditions) {
        this.addActivityWithConditions(activity, this.createPriorityPairs(priorityOfFirstBehavior, behaviorList), conditions);
    }

    public void addActivityWithConditions(Activity activity, ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> behaviorPriorityPairs, Set<Pair<MemoryModuleType<?>, MemoryStatus>> conditions) {
        this.addActivityAndRemoveMemoriesWhenStopped(activity, behaviorPriorityPairs, conditions, Sets.newHashSet());
    }

    public void addActivityAndRemoveMemoriesWhenStopped(Activity activity, ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> behaviorPriorityPairs, Set<Pair<MemoryModuleType<?>, MemoryStatus>> conditions, Set<MemoryModuleType<?>> memoriesToEraseWhenStopped) {
        this.activityRequirements.put(activity, conditions);
        if (!memoriesToEraseWhenStopped.isEmpty()) {
            this.activityMemoriesToEraseWhenStopped.put(activity, memoriesToEraseWhenStopped);
        }

        UnmodifiableIterator unmodifiableiterator = behaviorPriorityPairs.iterator();

        while (unmodifiableiterator.hasNext()) {
            Pair<Integer, ? extends BehaviorControl<? super E>> pair = (Pair) unmodifiableiterator.next();

            ((Set) ((Map) this.availableBehaviorsByPriority.computeIfAbsent((Integer) pair.getFirst(), (integer) -> {
                return Maps.newHashMap();
            })).computeIfAbsent(activity, (activity1) -> {
                return Sets.newLinkedHashSet();
            })).add((BehaviorControl) pair.getSecond());
        }

    }

    @VisibleForTesting
    public void removeAllBehaviors() {
        this.availableBehaviorsByPriority.clear();
    }

    public boolean isActive(Activity activity) {
        return this.activeActivities.contains(activity);
    }

    public Brain<E> copyWithoutBehaviors() {
        Brain<E> brain = new Brain<E>(this.memories.keySet(), this.sensors.keySet(), ImmutableList.of(), this.codec);

        for (Map.Entry<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> map_entry : this.memories.entrySet()) {
            MemoryModuleType<?> memorymoduletype = (MemoryModuleType) map_entry.getKey();

            if (((Optional) map_entry.getValue()).isPresent()) {
                brain.memories.put(memorymoduletype, (Optional) map_entry.getValue());
            }
        }

        return brain;
    }

    public void tick(ServerLevel level, E body) {
        this.forgetOutdatedMemories();
        this.tickSensors(level, body);
        this.startEachNonRunningBehavior(level, body);
        this.tickEachRunningBehavior(level, body);
    }

    private void tickSensors(ServerLevel level, E body) {
        for (Sensor<? super E> sensor : this.sensors.values()) {
            sensor.tick(level, body);
        }

    }

    private void forgetOutdatedMemories() {
        for (Map.Entry<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> map_entry : this.memories.entrySet()) {
            if (((Optional) map_entry.getValue()).isPresent()) {
                ExpirableValue<?> expirablevalue = (ExpirableValue) ((Optional) map_entry.getValue()).get();

                if (expirablevalue.hasExpired()) {
                    this.eraseMemory((MemoryModuleType) map_entry.getKey());
                }

                expirablevalue.tick();
            }
        }

    }

    public void stopAll(ServerLevel level, E body) {
        long i = body.level().getGameTime();

        for (BehaviorControl<? super E> behaviorcontrol : this.getRunningBehaviors()) {
            behaviorcontrol.doStop(level, body, i);
        }

    }

    private void startEachNonRunningBehavior(ServerLevel level, E body) {
        long i = level.getGameTime();

        for (Map<Activity, Set<BehaviorControl<? super E>>> map : this.availableBehaviorsByPriority.values()) {
            for (Map.Entry<Activity, Set<BehaviorControl<? super E>>> map_entry : map.entrySet()) {
                Activity activity = (Activity) map_entry.getKey();

                if (this.activeActivities.contains(activity)) {
                    for (BehaviorControl<? super E> behaviorcontrol : (Set) map_entry.getValue()) {
                        if (behaviorcontrol.getStatus() == Behavior.Status.STOPPED) {
                            behaviorcontrol.tryStart(level, body, i);
                        }
                    }
                }
            }
        }

    }

    private void tickEachRunningBehavior(ServerLevel level, E body) {
        long i = level.getGameTime();

        for (BehaviorControl<? super E> behaviorcontrol : this.getRunningBehaviors()) {
            behaviorcontrol.tickOrStop(level, body, i);
        }

    }

    private boolean activityRequirementsAreMet(Activity activity) {
        if (!this.activityRequirements.containsKey(activity)) {
            return false;
        } else {
            for (Pair<MemoryModuleType<?>, MemoryStatus> pair : (Set) this.activityRequirements.get(activity)) {
                MemoryModuleType<?> memorymoduletype = (MemoryModuleType) pair.getFirst();
                MemoryStatus memorystatus = (MemoryStatus) pair.getSecond();

                if (!this.checkMemory(memorymoduletype, memorystatus)) {
                    return false;
                }
            }

            return true;
        }
    }

    private boolean isEmptyCollection(Object object) {
        return object instanceof Collection && ((Collection) object).isEmpty();
    }

    ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> createPriorityPairs(int priorityOfFirstBehavior, ImmutableList<? extends BehaviorControl<? super E>> behaviorList) {
        int j = priorityOfFirstBehavior;
        ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super E>>> immutablelist_builder = ImmutableList.builder();
        UnmodifiableIterator unmodifiableiterator = behaviorList.iterator();

        while (unmodifiableiterator.hasNext()) {
            BehaviorControl<? super E> behaviorcontrol = (BehaviorControl) unmodifiableiterator.next();

            immutablelist_builder.add(Pair.of(j++, behaviorcontrol));
        }

        return immutablelist_builder.build();
    }

    public boolean isBrainDead() {
        return this.memories.isEmpty() && this.sensors.isEmpty() && this.availableBehaviorsByPriority.isEmpty();
    }

    public static final class Provider<E extends LivingEntity> {

        private final Collection<? extends MemoryModuleType<?>> memoryTypes;
        private final Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes;
        private final Codec<Brain<E>> codec;

        private Provider(Collection<? extends MemoryModuleType<?>> memoryTypes, Collection<? extends SensorType<? extends Sensor<? super E>>> sensorTypes) {
            this.memoryTypes = memoryTypes;
            this.sensorTypes = sensorTypes;
            this.codec = Brain.codec(memoryTypes, sensorTypes);
        }

        public Brain<E> makeBrain(Dynamic<?> input) {
            DataResult dataresult = this.codec.parse(input);
            Logger logger = Brain.LOGGER;

            Objects.requireNonNull(logger);
            return (Brain) dataresult.resultOrPartial(logger::error).orElseGet(() -> {
                return new Brain(this.memoryTypes, this.sensorTypes, ImmutableList.of(), () -> {
                    return this.codec;
                });
            });
        }
    }

    private static final class MemoryValue<U> {

        private final MemoryModuleType<U> type;
        private final Optional<? extends ExpirableValue<U>> value;

        private static <U> Brain.MemoryValue<U> createUnchecked(MemoryModuleType<U> type, Optional<? extends ExpirableValue<?>> value) {
            return new Brain.MemoryValue<U>(type, value);
        }

        private MemoryValue(MemoryModuleType<U> type, Optional<? extends ExpirableValue<U>> value) {
            this.type = type;
            this.value = value;
        }

        private void setMemoryInternal(Brain<?> brain) {
            brain.setMemoryInternal(this.type, this.value);
        }

        public <T> void serialize(DynamicOps<T> ops, RecordBuilder<T> builder) {
            this.type.getCodec().ifPresent((codec) -> {
                this.value.ifPresent((expirablevalue) -> {
                    builder.add(BuiltInRegistries.MEMORY_MODULE_TYPE.byNameCodec().encodeStart(ops, this.type), codec.encodeStart(ops, expirablevalue));
                });
            });
        }
    }
}

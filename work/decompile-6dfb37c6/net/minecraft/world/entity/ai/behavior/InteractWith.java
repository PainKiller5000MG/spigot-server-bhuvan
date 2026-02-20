package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class InteractWith {

    public InteractWith() {}

    public static <T extends LivingEntity> BehaviorControl<LivingEntity> of(EntityType<? extends T> type, int interactionRange, MemoryModuleType<T> interactionTarget, float speedModifier, int stopDistance) {
        return of(type, interactionRange, (livingentity) -> {
            return true;
        }, (livingentity) -> {
            return true;
        }, interactionTarget, speedModifier, stopDistance);
    }

    public static <E extends LivingEntity, T extends LivingEntity> BehaviorControl<E> of(EntityType<? extends T> type, int interactionRange, Predicate<E> selfFilter, Predicate<T> targetFilter, MemoryModuleType<T> interactionTarget, float speedModifier, int stopDistance) {
        int k = interactionRange * interactionRange;
        Predicate<LivingEntity> predicate2 = (livingentity) -> {
            return type.equals(livingentity.getType()) && targetFilter.test(livingentity);
        };

        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.registered(interactionTarget), behaviorbuilder_instance.registered(MemoryModuleType.LOOK_TARGET), behaviorbuilder_instance.absent(MemoryModuleType.WALK_TARGET), behaviorbuilder_instance.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2, memoryaccessor3) -> {
                return (serverlevel, livingentity, l) -> {
                    NearestVisibleLivingEntities nearestvisiblelivingentities = (NearestVisibleLivingEntities) behaviorbuilder_instance.get(memoryaccessor3);

                    if (selfFilter.test(livingentity) && nearestvisiblelivingentities.contains(predicate2)) {
                        Optional<LivingEntity> optional = nearestvisiblelivingentities.findClosest((livingentity1) -> {
                            return livingentity1.distanceToSqr((Entity) livingentity) <= (double) k && predicate2.test(livingentity1);
                        });

                        optional.ifPresent((livingentity1) -> {
                            memoryaccessor.set(livingentity1);
                            memoryaccessor1.set(new EntityTracker(livingentity1, true));
                            memoryaccessor2.set(new WalkTarget(new EntityTracker(livingentity1, false), speedModifier, stopDistance));
                        });
                        return true;
                    } else {
                        return false;
                    }
                };
            });
        });
    }
}

package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class StayCloseToTarget {

    public StayCloseToTarget() {}

    public static BehaviorControl<LivingEntity> create(Function<LivingEntity, Optional<PositionTracker>> targetPositionGetter, Predicate<LivingEntity> shouldRunPredicate, int closeEnough, int tooFar, float speedModifier) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.registered(MemoryModuleType.LOOK_TARGET), behaviorbuilder_instance.registered(MemoryModuleType.WALK_TARGET)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1) -> {
                return (serverlevel, livingentity, k) -> {
                    Optional<PositionTracker> optional = (Optional) targetPositionGetter.apply(livingentity);

                    if (!optional.isEmpty() && shouldRunPredicate.test(livingentity)) {
                        PositionTracker positiontracker = (PositionTracker) optional.get();

                        if (livingentity.position().closerThan(positiontracker.currentPosition(), (double) tooFar)) {
                            return false;
                        } else {
                            PositionTracker positiontracker1 = (PositionTracker) optional.get();

                            memoryaccessor.set(positiontracker1);
                            memoryaccessor1.set(new WalkTarget(positiontracker1, speedModifier, closeEnough));
                            return true;
                        }
                    } else {
                        return false;
                    }
                };
            });
        });
    }
}

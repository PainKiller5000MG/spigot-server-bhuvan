package net.minecraft.world.entity.ai.behavior;

import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class SetWalkTargetFromLookTarget {

    public SetWalkTargetFromLookTarget() {}

    public static OneShot<LivingEntity> create(float speedModifier, int closeEnoughDistance) {
        return create((livingentity) -> {
            return true;
        }, (livingentity) -> {
            return speedModifier;
        }, closeEnoughDistance);
    }

    public static OneShot<LivingEntity> create(Predicate<LivingEntity> canSetWalkTargetPredicate, Function<LivingEntity, Float> speedModifier, int closeEnoughDistance) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.absent(MemoryModuleType.WALK_TARGET), behaviorbuilder_instance.present(MemoryModuleType.LOOK_TARGET)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1) -> {
                return (serverlevel, livingentity, j) -> {
                    if (!canSetWalkTargetPredicate.test(livingentity)) {
                        return false;
                    } else {
                        memoryaccessor.set(new WalkTarget((PositionTracker) behaviorbuilder_instance.get(memoryaccessor1), (Float) speedModifier.apply(livingentity), closeEnoughDistance));
                        return true;
                    }
                };
            });
        });
    }
}

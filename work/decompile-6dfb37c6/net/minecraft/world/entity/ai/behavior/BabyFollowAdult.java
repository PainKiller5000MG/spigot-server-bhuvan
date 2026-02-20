package net.minecraft.world.entity.ai.behavior;

import java.util.function.Function;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class BabyFollowAdult {

    public BabyFollowAdult() {}

    public static OneShot<LivingEntity> create(UniformInt followRange, float speedModifier) {
        return create(followRange, (livingentity) -> {
            return speedModifier;
        }, MemoryModuleType.NEAREST_VISIBLE_ADULT, false);
    }

    public static OneShot<LivingEntity> create(UniformInt followRange, Function<LivingEntity, Float> speedModifier, MemoryModuleType<? extends LivingEntity> nearestVisibleType, boolean targetEye) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.present(nearestVisibleType), behaviorbuilder_instance.registered(MemoryModuleType.LOOK_TARGET), behaviorbuilder_instance.absent(MemoryModuleType.WALK_TARGET)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2) -> {
                return (serverlevel, livingentity, i) -> {
                    if (!livingentity.isBaby()) {
                        return false;
                    } else {
                        LivingEntity livingentity1 = (LivingEntity) behaviorbuilder_instance.get(memoryaccessor);

                        if (livingentity.closerThan(livingentity1, (double) (followRange.getMaxValue() + 1)) && !livingentity.closerThan(livingentity1, (double) followRange.getMinValue())) {
                            WalkTarget walktarget = new WalkTarget(new EntityTracker(livingentity1, targetEye, targetEye), (Float) speedModifier.apply(livingentity), followRange.getMinValue() - 1);

                            memoryaccessor1.set(new EntityTracker(livingentity1, true, targetEye));
                            memoryaccessor2.set(walktarget);
                            return true;
                        } else {
                            return false;
                        }
                    }
                };
            });
        });
    }
}

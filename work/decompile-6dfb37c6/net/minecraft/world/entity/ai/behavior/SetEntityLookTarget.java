package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

public class SetEntityLookTarget {

    public SetEntityLookTarget() {}

    public static BehaviorControl<LivingEntity> create(MobCategory category, float maxDist) {
        return create((livingentity) -> {
            return category.equals(livingentity.getType().getCategory());
        }, maxDist);
    }

    public static OneShot<LivingEntity> create(EntityType<?> type, float maxDist) {
        return create((livingentity) -> {
            return type.equals(livingentity.getType());
        }, maxDist);
    }

    public static OneShot<LivingEntity> create(float maxDist) {
        return create((livingentity) -> {
            return true;
        }, maxDist);
    }

    public static OneShot<LivingEntity> create(Predicate<LivingEntity> predicate, float maxDist) {
        float f1 = maxDist * maxDist;

        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.absent(MemoryModuleType.LOOK_TARGET), behaviorbuilder_instance.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1) -> {
                return (serverlevel, livingentity, i) -> {
                    Optional<LivingEntity> optional = ((NearestVisibleLivingEntities) behaviorbuilder_instance.get(memoryaccessor1)).findClosest(predicate.and((livingentity1) -> {
                        return livingentity1.distanceToSqr((Entity) livingentity) <= (double) f1 && !livingentity.hasPassenger(livingentity1);
                    }));

                    if (optional.isEmpty()) {
                        return false;
                    } else {
                        memoryaccessor.set(new EntityTracker((Entity) optional.get(), true));
                        return true;
                    }
                };
            });
        });
    }
}

package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

public class SetLookAndInteract {

    public SetLookAndInteract() {}

    public static BehaviorControl<LivingEntity> create(EntityType<?> type, int interactionRange) {
        int j = interactionRange * interactionRange;

        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.registered(MemoryModuleType.LOOK_TARGET), behaviorbuilder_instance.absent(MemoryModuleType.INTERACTION_TARGET), behaviorbuilder_instance.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2) -> {
                return (serverlevel, livingentity, k) -> {
                    Optional<LivingEntity> optional = ((NearestVisibleLivingEntities) behaviorbuilder_instance.get(memoryaccessor2)).findClosest((livingentity1) -> {
                        return livingentity1.distanceToSqr((Entity) livingentity) <= (double) j && type.equals(livingentity1.getType());
                    });

                    if (optional.isEmpty()) {
                        return false;
                    } else {
                        LivingEntity livingentity1 = (LivingEntity) optional.get();

                        memoryaccessor1.set(livingentity1);
                        memoryaccessor.set(new EntityTracker(livingentity1, true));
                        return true;
                    }
                };
            });
        });
    }
}

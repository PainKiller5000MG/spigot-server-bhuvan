package net.minecraft.world.entity.ai.behavior;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class VillagerCalmDown {

    private static final int SAFE_DISTANCE_FROM_DANGER = 36;

    public VillagerCalmDown() {}

    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.registered(MemoryModuleType.HURT_BY), behaviorbuilder_instance.registered(MemoryModuleType.HURT_BY_ENTITY), behaviorbuilder_instance.registered(MemoryModuleType.NEAREST_HOSTILE)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2) -> {
                return (serverlevel, livingentity, i) -> {
                    boolean flag = behaviorbuilder_instance.tryGet(memoryaccessor).isPresent() || behaviorbuilder_instance.tryGet(memoryaccessor2).isPresent() || behaviorbuilder_instance.tryGet(memoryaccessor1).filter((livingentity1) -> {
                        return livingentity1.distanceToSqr((Entity) livingentity) <= 36.0D;
                    }).isPresent();

                    if (!flag) {
                        memoryaccessor.erase();
                        memoryaccessor1.erase();
                        livingentity.getBrain().updateActivityFromSchedule(serverlevel.environmentAttributes(), serverlevel.getGameTime(), livingentity.position());
                    }

                    return true;
                };
            });
        });
    }
}

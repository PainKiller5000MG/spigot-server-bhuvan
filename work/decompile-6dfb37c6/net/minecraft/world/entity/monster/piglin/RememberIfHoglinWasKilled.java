package net.minecraft.world.entity.monster.piglin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class RememberIfHoglinWasKilled {

    public RememberIfHoglinWasKilled() {}

    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.present(MemoryModuleType.ATTACK_TARGET), behaviorbuilder_instance.registered(MemoryModuleType.HUNTED_RECENTLY)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1) -> {
                return (serverlevel, livingentity, i) -> {
                    LivingEntity livingentity1 = (LivingEntity) behaviorbuilder_instance.get(memoryaccessor);

                    if (livingentity1.getType() == EntityType.HOGLIN && livingentity1.isDeadOrDying()) {
                        memoryaccessor1.setWithExpiry(true, (long) PiglinAi.TIME_BETWEEN_HUNTS.sample(livingentity.level().random));
                    }

                    return true;
                };
            });
        });
    }
}

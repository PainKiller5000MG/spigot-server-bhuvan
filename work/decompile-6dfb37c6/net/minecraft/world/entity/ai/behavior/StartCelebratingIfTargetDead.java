package net.minecraft.world.entity.ai.behavior;

import java.util.function.BiPredicate;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.gamerules.GameRules;

public class StartCelebratingIfTargetDead {

    public StartCelebratingIfTargetDead() {}

    public static BehaviorControl<LivingEntity> create(int celebrateDuration, BiPredicate<LivingEntity, LivingEntity> dancePredicate) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.present(MemoryModuleType.ATTACK_TARGET), behaviorbuilder_instance.registered(MemoryModuleType.ANGRY_AT), behaviorbuilder_instance.absent(MemoryModuleType.CELEBRATE_LOCATION), behaviorbuilder_instance.registered(MemoryModuleType.DANCING)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2, memoryaccessor3) -> {
                return (serverlevel, livingentity, j) -> {
                    LivingEntity livingentity1 = (LivingEntity) behaviorbuilder_instance.get(memoryaccessor);

                    if (!livingentity1.isDeadOrDying()) {
                        return false;
                    } else {
                        if (dancePredicate.test(livingentity, livingentity1)) {
                            memoryaccessor3.setWithExpiry(true, (long) celebrateDuration);
                        }

                        memoryaccessor2.setWithExpiry(livingentity1.blockPosition(), (long) celebrateDuration);
                        if (livingentity1.getType() != EntityType.PLAYER || (Boolean) serverlevel.getGameRules().get(GameRules.FORGIVE_DEAD_PLAYERS)) {
                            memoryaccessor.erase();
                            memoryaccessor1.erase();
                        }

                        return true;
                    }
                };
            });
        });
    }
}

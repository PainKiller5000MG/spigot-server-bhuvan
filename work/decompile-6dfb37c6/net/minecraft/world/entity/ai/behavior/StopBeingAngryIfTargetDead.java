package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.gamerules.GameRules;

public class StopBeingAngryIfTargetDead {

    public StopBeingAngryIfTargetDead() {}

    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.present(MemoryModuleType.ANGRY_AT)).apply(behaviorbuilder_instance, (memoryaccessor) -> {
                return (serverlevel, livingentity, i) -> {
                    Optional.ofNullable(serverlevel.getEntity((UUID) behaviorbuilder_instance.get(memoryaccessor))).map((entity) -> {
                        LivingEntity livingentity1;

                        if (entity instanceof LivingEntity livingentity2) {
                            livingentity1 = livingentity2;
                        } else {
                            livingentity1 = null;
                        }

                        return livingentity1;
                    }).filter(LivingEntity::isDeadOrDying).filter((livingentity1) -> {
                        return livingentity1.getType() != EntityType.PLAYER || (Boolean) serverlevel.getGameRules().get(GameRules.FORGIVE_DEAD_PLAYERS);
                    }).ifPresent((livingentity1) -> {
                        memoryaccessor.erase();
                    });
                    return true;
                };
            });
        });
    }
}

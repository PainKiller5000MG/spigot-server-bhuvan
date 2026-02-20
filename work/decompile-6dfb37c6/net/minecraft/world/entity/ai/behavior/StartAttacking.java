package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class StartAttacking {

    public StartAttacking() {}

    public static <E extends Mob> BehaviorControl<E> create(StartAttacking.TargetFinder<E> targetFinderFunction) {
        return create((serverlevel, mob) -> {
            return true;
        }, targetFinderFunction);
    }

    public static <E extends Mob> BehaviorControl<E> create(StartAttacking.StartAttackingCondition<E> canAttackPredicate, StartAttacking.TargetFinder<E> targetFinderFunction) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.absent(MemoryModuleType.ATTACK_TARGET), behaviorbuilder_instance.registered(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1) -> {
                return (serverlevel, mob, i) -> {
                    if (!canAttackPredicate.test(serverlevel, mob)) {
                        return false;
                    } else {
                        Optional<? extends LivingEntity> optional = targetFinderFunction.get(serverlevel, mob);

                        if (optional.isEmpty()) {
                            return false;
                        } else {
                            LivingEntity livingentity = (LivingEntity) optional.get();

                            if (!mob.canAttack(livingentity)) {
                                return false;
                            } else {
                                memoryaccessor.set(livingentity);
                                memoryaccessor1.erase();
                                return true;
                            }
                        }
                    }
                };
            });
        });
    }

    @FunctionalInterface
    public interface StartAttackingCondition<E> {

        boolean test(ServerLevel level, E body);
    }

    @FunctionalInterface
    public interface TargetFinder<E> {

        Optional<? extends LivingEntity> get(ServerLevel level, E body);
    }
}

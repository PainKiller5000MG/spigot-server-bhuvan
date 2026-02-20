package net.minecraft.world.entity.ai.behavior;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

public class MeleeAttack {

    public MeleeAttack() {}

    public static <T extends Mob> OneShot<T> create(int cooldownBetweenAttacks) {
        return create((mob) -> {
            return true;
        }, cooldownBetweenAttacks);
    }

    public static <T extends Mob> OneShot<T> create(Predicate<T> canAttackPredicate, int cooldownBetweenAttacks) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.registered(MemoryModuleType.LOOK_TARGET), behaviorbuilder_instance.present(MemoryModuleType.ATTACK_TARGET), behaviorbuilder_instance.absent(MemoryModuleType.ATTACK_COOLING_DOWN), behaviorbuilder_instance.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2, memoryaccessor3) -> {
                return (serverlevel, mob, j) -> {
                    LivingEntity livingentity = (LivingEntity) behaviorbuilder_instance.get(memoryaccessor1);

                    if (canAttackPredicate.test(mob) && !isHoldingUsableNonMeleeWeapon(mob) && mob.isWithinMeleeAttackRange(livingentity) && ((NearestVisibleLivingEntities) behaviorbuilder_instance.get(memoryaccessor3)).contains(livingentity)) {
                        memoryaccessor.set(new EntityTracker(livingentity, true));
                        mob.swing(InteractionHand.MAIN_HAND);
                        mob.doHurtTarget(serverlevel, livingentity);
                        memoryaccessor2.setWithExpiry(true, (long) cooldownBetweenAttacks);
                        return true;
                    } else {
                        return false;
                    }
                };
            });
        });
    }

    private static boolean isHoldingUsableNonMeleeWeapon(Mob body) {
        Objects.requireNonNull(body);
        return body.isHolding(body::canUseNonMeleeWeapon);
    }
}

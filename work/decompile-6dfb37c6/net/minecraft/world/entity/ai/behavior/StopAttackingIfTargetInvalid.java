package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class StopAttackingIfTargetInvalid {

    private static final int TIMEOUT_TO_GET_WITHIN_ATTACK_RANGE = 200;

    public StopAttackingIfTargetInvalid() {}

    public static <E extends Mob> BehaviorControl<E> create(StopAttackingIfTargetInvalid.TargetErasedCallback<E> onTargetErased) {
        return create((serverlevel, livingentity) -> {
            return false;
        }, onTargetErased, true);
    }

    public static <E extends Mob> BehaviorControl<E> create(StopAttackingIfTargetInvalid.StopAttackCondition stopAttackingWhen) {
        return create(stopAttackingWhen, (serverlevel, mob, livingentity) -> {
        }, true);
    }

    public static <E extends Mob> BehaviorControl<E> create() {
        return create((serverlevel, livingentity) -> {
            return false;
        }, (serverlevel, mob, livingentity) -> {
        }, true);
    }

    public static <E extends Mob> BehaviorControl<E> create(StopAttackingIfTargetInvalid.StopAttackCondition stopAttackingWhen, StopAttackingIfTargetInvalid.TargetErasedCallback<E> onTargetErased, boolean canGrowTiredOfTryingToReachTarget) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.present(MemoryModuleType.ATTACK_TARGET), behaviorbuilder_instance.registered(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1) -> {
                return (serverlevel, mob, i) -> {
                    LivingEntity livingentity = (LivingEntity) behaviorbuilder_instance.get(memoryaccessor);

                    if (mob.canAttack(livingentity) && (!canGrowTiredOfTryingToReachTarget || !isTiredOfTryingToReachTarget(mob, behaviorbuilder_instance.tryGet(memoryaccessor1))) && livingentity.isAlive() && livingentity.level() == mob.level() && !stopAttackingWhen.test(serverlevel, livingentity)) {
                        return true;
                    } else {
                        onTargetErased.accept(serverlevel, mob, livingentity);
                        memoryaccessor.erase();
                        return true;
                    }
                };
            });
        });
    }

    private static boolean isTiredOfTryingToReachTarget(LivingEntity body, Optional<Long> cantReachSince) {
        return cantReachSince.isPresent() && body.level().getGameTime() - (Long) cantReachSince.get() > 200L;
    }

    @FunctionalInterface
    public interface StopAttackCondition {

        boolean test(ServerLevel level, LivingEntity target);
    }

    @FunctionalInterface
    public interface TargetErasedCallback<E> {

        void accept(ServerLevel level, E body, LivingEntity target);
    }
}

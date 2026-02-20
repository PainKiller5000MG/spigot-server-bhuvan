package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class SetWalkTargetFromAttackTargetIfTargetOutOfReach {

    private static final int PROJECTILE_ATTACK_RANGE_BUFFER = 1;

    public SetWalkTargetFromAttackTargetIfTargetOutOfReach() {}

    public static BehaviorControl<Mob> create(float speedModifier) {
        return create((livingentity) -> {
            return speedModifier;
        });
    }

    public static BehaviorControl<Mob> create(Function<LivingEntity, Float> speedModifier) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.registered(MemoryModuleType.WALK_TARGET), behaviorbuilder_instance.registered(MemoryModuleType.LOOK_TARGET), behaviorbuilder_instance.present(MemoryModuleType.ATTACK_TARGET), behaviorbuilder_instance.registered(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2, memoryaccessor3) -> {
                return (serverlevel, mob, i) -> {
                    LivingEntity livingentity = (LivingEntity) behaviorbuilder_instance.get(memoryaccessor2);
                    Optional<NearestVisibleLivingEntities> optional = behaviorbuilder_instance.<NearestVisibleLivingEntities>tryGet(memoryaccessor3);

                    if (optional.isPresent() && ((NearestVisibleLivingEntities) optional.get()).contains(livingentity) && BehaviorUtils.isWithinAttackRange(mob, livingentity, 1)) {
                        memoryaccessor.erase();
                    } else {
                        memoryaccessor1.set(new EntityTracker(livingentity, true));
                        memoryaccessor.set(new WalkTarget(new EntityTracker(livingentity, false), (Float) speedModifier.apply(mob), 0));
                    }

                    return true;
                };
            });
        });
    }
}

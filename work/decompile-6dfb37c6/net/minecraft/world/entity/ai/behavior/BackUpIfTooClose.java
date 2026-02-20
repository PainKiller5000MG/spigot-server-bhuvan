package net.minecraft.world.entity.ai.behavior;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

public class BackUpIfTooClose {

    public BackUpIfTooClose() {}

    public static OneShot<Mob> create(int tooCloseDistance, float strafeSpeed) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.absent(MemoryModuleType.WALK_TARGET), behaviorbuilder_instance.registered(MemoryModuleType.LOOK_TARGET), behaviorbuilder_instance.present(MemoryModuleType.ATTACK_TARGET), behaviorbuilder_instance.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2, memoryaccessor3) -> {
                return (serverlevel, mob, j) -> {
                    LivingEntity livingentity = (LivingEntity) behaviorbuilder_instance.get(memoryaccessor2);

                    if (livingentity.closerThan(mob, (double) tooCloseDistance) && ((NearestVisibleLivingEntities) behaviorbuilder_instance.get(memoryaccessor3)).contains(livingentity)) {
                        memoryaccessor1.set(new EntityTracker(livingentity, true));
                        mob.getMoveControl().strafe(-strafeSpeed, 0.0F);
                        mob.setYRot(Mth.rotateIfNecessary(mob.getYRot(), mob.yHeadRot, 0.0F));
                        return true;
                    } else {
                        return false;
                    }
                };
            });
        });
    }
}

package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

public class SetWalkTargetFromBlockMemory {

    public SetWalkTargetFromBlockMemory() {}

    public static OneShot<Villager> create(MemoryModuleType<GlobalPos> memoryType, float speedModifier, int closeEnoughDist, int tooFarDistance, int tooLongUnreachableDuration) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.registered(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE), behaviorbuilder_instance.absent(MemoryModuleType.WALK_TARGET), behaviorbuilder_instance.present(memoryType)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2) -> {
                return (serverlevel, villager, l) -> {
                    GlobalPos globalpos = (GlobalPos) behaviorbuilder_instance.get(memoryaccessor2);
                    Optional<Long> optional = behaviorbuilder_instance.<Long>tryGet(memoryaccessor);

                    if (globalpos.dimension() == serverlevel.dimension() && (!optional.isPresent() || serverlevel.getGameTime() - (Long) optional.get() <= (long) tooLongUnreachableDuration)) {
                        if (globalpos.pos().distManhattan(villager.blockPosition()) > tooFarDistance) {
                            Vec3 vec3 = null;
                            int i1 = 0;
                            int j1 = 1000;

                            while (vec3 == null || BlockPos.containing(vec3).distManhattan(villager.blockPosition()) > tooFarDistance) {
                                vec3 = DefaultRandomPos.getPosTowards(villager, 15, 7, Vec3.atBottomCenterOf(globalpos.pos()), (double) ((float) Math.PI / 2F));
                                ++i1;
                                if (i1 == 1000) {
                                    villager.releasePoi(memoryType);
                                    memoryaccessor2.erase();
                                    memoryaccessor.set(l);
                                    return true;
                                }
                            }

                            memoryaccessor1.set(new WalkTarget(vec3, speedModifier, closeEnoughDist));
                        } else if (globalpos.pos().distManhattan(villager.blockPosition()) > closeEnoughDist) {
                            memoryaccessor1.set(new WalkTarget(globalpos.pos(), speedModifier, closeEnoughDist));
                        }
                    } else {
                        villager.releasePoi(memoryType);
                        memoryaccessor2.erase();
                        memoryaccessor.set(l);
                    }

                    return true;
                };
            });
        });
    }
}

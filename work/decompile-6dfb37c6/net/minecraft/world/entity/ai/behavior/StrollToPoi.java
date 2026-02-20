package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.apache.commons.lang3.mutable.MutableLong;

public class StrollToPoi {

    public StrollToPoi() {}

    public static BehaviorControl<PathfinderMob> create(MemoryModuleType<GlobalPos> memoryType, float speedModifier, int closeEnoughDist, int maxDistanceFromPoi) {
        MutableLong mutablelong = new MutableLong(0L);

        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.registered(MemoryModuleType.WALK_TARGET), behaviorbuilder_instance.present(memoryType)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1) -> {
                return (serverlevel, pathfindermob, k) -> {
                    GlobalPos globalpos = (GlobalPos) behaviorbuilder_instance.get(memoryaccessor1);

                    if (serverlevel.dimension() == globalpos.dimension() && globalpos.pos().closerToCenterThan(pathfindermob.position(), (double) maxDistanceFromPoi)) {
                        if (k <= mutablelong.longValue()) {
                            return true;
                        } else {
                            memoryaccessor.set(new WalkTarget(globalpos.pos(), speedModifier, closeEnoughDist));
                            mutablelong.setValue(k + 80L);
                            return true;
                        }
                    } else {
                        return false;
                    }
                };
            });
        });
    }
}

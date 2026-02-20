package net.minecraft.world.entity.ai.behavior;

import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import org.apache.commons.lang3.mutable.MutableLong;

public class StrollToPoiList {

    public StrollToPoiList() {}

    public static BehaviorControl<Villager> create(MemoryModuleType<List<GlobalPos>> strollToMemoryType, float speedModifier, int closeEnoughDist, int maxDistanceFromPoi, MemoryModuleType<GlobalPos> mustBeCloseToMemoryType) {
        MutableLong mutablelong = new MutableLong(0L);

        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.registered(MemoryModuleType.WALK_TARGET), behaviorbuilder_instance.present(strollToMemoryType), behaviorbuilder_instance.present(mustBeCloseToMemoryType)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2) -> {
                return (serverlevel, villager, k) -> {
                    List<GlobalPos> list = (List) behaviorbuilder_instance.get(memoryaccessor1);
                    GlobalPos globalpos = (GlobalPos) behaviorbuilder_instance.get(memoryaccessor2);

                    if (list.isEmpty()) {
                        return false;
                    } else {
                        GlobalPos globalpos1 = (GlobalPos) list.get(serverlevel.getRandom().nextInt(list.size()));

                        if (globalpos1 != null && serverlevel.dimension() == globalpos1.dimension() && globalpos.pos().closerToCenterThan(villager.position(), (double) maxDistanceFromPoi)) {
                            if (k > mutablelong.longValue()) {
                                memoryaccessor.set(new WalkTarget(globalpos1.pos(), speedModifier, closeEnoughDist));
                                mutablelong.setValue(k + 100L);
                            }

                            return true;
                        } else {
                            return false;
                        }
                    }
                };
            });
        });
    }
}

package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableLong;

public class StrollAroundPoi {

    private static final int MIN_TIME_BETWEEN_STROLLS = 180;
    private static final int STROLL_MAX_XZ_DIST = 8;
    private static final int STROLL_MAX_Y_DIST = 6;

    public StrollAroundPoi() {}

    public static OneShot<PathfinderMob> create(MemoryModuleType<GlobalPos> memoryType, float speedModifier, int maxDistanceFromPoi) {
        MutableLong mutablelong = new MutableLong(0L);

        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.registered(MemoryModuleType.WALK_TARGET), behaviorbuilder_instance.present(memoryType)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1) -> {
                return (serverlevel, pathfindermob, j) -> {
                    GlobalPos globalpos = (GlobalPos) behaviorbuilder_instance.get(memoryaccessor1);

                    if (serverlevel.dimension() == globalpos.dimension() && globalpos.pos().closerToCenterThan(pathfindermob.position(), (double) maxDistanceFromPoi)) {
                        if (j <= mutablelong.longValue()) {
                            return true;
                        } else {
                            Optional<Vec3> optional = Optional.ofNullable(LandRandomPos.getPos(pathfindermob, 8, 6));

                            memoryaccessor.setOrErase(optional.map((vec3) -> {
                                return new WalkTarget(vec3, speedModifier, 1);
                            }));
                            mutablelong.setValue(j + 180L);
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

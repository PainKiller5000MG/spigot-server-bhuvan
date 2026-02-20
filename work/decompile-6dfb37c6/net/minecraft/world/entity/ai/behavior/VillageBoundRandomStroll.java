package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

public class VillageBoundRandomStroll {

    private static final int MAX_XZ_DIST = 10;
    private static final int MAX_Y_DIST = 7;

    public VillageBoundRandomStroll() {}

    public static OneShot<PathfinderMob> create(float speedModifier) {
        return create(speedModifier, 10, 7);
    }

    public static OneShot<PathfinderMob> create(float speedModifier, int maxXyDist, int maxYDist) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.absent(MemoryModuleType.WALK_TARGET)).apply(behaviorbuilder_instance, (memoryaccessor) -> {
                return (serverlevel, pathfindermob, k) -> {
                    BlockPos blockpos = pathfindermob.blockPosition();
                    Vec3 vec3;

                    if (serverlevel.isVillage(blockpos)) {
                        vec3 = LandRandomPos.getPos(pathfindermob, maxXyDist, maxYDist);
                    } else {
                        SectionPos sectionpos = SectionPos.of(blockpos);
                        SectionPos sectionpos1 = BehaviorUtils.findSectionClosestToVillage(serverlevel, sectionpos, 2);

                        if (sectionpos1 != sectionpos) {
                            vec3 = DefaultRandomPos.getPosTowards(pathfindermob, maxXyDist, maxYDist, Vec3.atBottomCenterOf(sectionpos1.center()), (double) ((float) Math.PI / 2F));
                        } else {
                            vec3 = LandRandomPos.getPos(pathfindermob, maxXyDist, maxYDist);
                        }
                    }

                    memoryaccessor.setOrErase(Optional.ofNullable(vec3).map((vec31) -> {
                        return new WalkTarget(vec31, speedModifier, 0);
                    }));
                    return true;
                };
            });
        });
    }
}

package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

public class SetWalkTargetAwayFrom {

    public SetWalkTargetAwayFrom() {}

    public static BehaviorControl<PathfinderMob> pos(MemoryModuleType<BlockPos> memory, float speedModifier, int desiredDistance, boolean interruptCurrentWalk) {
        return create(memory, speedModifier, desiredDistance, interruptCurrentWalk, Vec3::atBottomCenterOf);
    }

    public static OneShot<PathfinderMob> entity(MemoryModuleType<? extends Entity> memory, float speedModifier, int desiredDistance, boolean interruptCurrentWalk) {
        return create(memory, speedModifier, desiredDistance, interruptCurrentWalk, Entity::position);
    }

    private static <T> OneShot<PathfinderMob> create(MemoryModuleType<T> walkAwayFromMemory, float speedModifier, int desiredDistance, boolean interruptCurrentWalk, Function<T, Vec3> toPosition) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.registered(MemoryModuleType.WALK_TARGET), behaviorbuilder_instance.present(walkAwayFromMemory)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1) -> {
                return (serverlevel, pathfindermob, j) -> {
                    Optional<WalkTarget> optional = behaviorbuilder_instance.<WalkTarget>tryGet(memoryaccessor);

                    if (optional.isPresent() && !interruptCurrentWalk) {
                        return false;
                    } else {
                        Vec3 vec3 = pathfindermob.position();
                        Vec3 vec31 = (Vec3) toPosition.apply(behaviorbuilder_instance.get(memoryaccessor1));

                        if (!vec3.closerThan(vec31, (double) desiredDistance)) {
                            return false;
                        } else {
                            if (optional.isPresent() && ((WalkTarget) optional.get()).getSpeedModifier() == speedModifier) {
                                Vec3 vec32 = ((WalkTarget) optional.get()).getTarget().currentPosition().subtract(vec3);
                                Vec3 vec33 = vec31.subtract(vec3);

                                if (vec32.dot(vec33) < 0.0D) {
                                    return false;
                                }
                            }

                            for (int k = 0; k < 10; ++k) {
                                Vec3 vec34 = LandRandomPos.getPosAway(pathfindermob, 16, 7, vec31);

                                if (vec34 != null) {
                                    memoryaccessor.set(new WalkTarget(vec34, speedModifier, 0));
                                    break;
                                }
                            }

                            return true;
                        }
                    }
                };
            });
        });
    }
}

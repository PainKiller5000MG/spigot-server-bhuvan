package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class RandomStroll {

    private static final int MAX_XZ_DIST = 10;
    private static final int MAX_Y_DIST = 7;
    private static final int[][] SWIM_XY_DISTANCE_TIERS = new int[][]{{1, 1}, {3, 3}, {5, 5}, {6, 5}, {7, 7}, {10, 7}};

    public RandomStroll() {}

    public static OneShot<PathfinderMob> stroll(float speedModifier) {
        return stroll(speedModifier, true);
    }

    public static OneShot<PathfinderMob> stroll(float speedModifier, boolean mayStrollFromWater) {
        return strollFlyOrSwim(speedModifier, (pathfindermob) -> {
            return LandRandomPos.getPos(pathfindermob, 10, 7);
        }, mayStrollFromWater ? (pathfindermob) -> {
            return true;
        } : (pathfindermob) -> {
            return !pathfindermob.isInWater();
        });
    }

    public static BehaviorControl<PathfinderMob> stroll(float speedModifier, int maxHorizontalDistance, int maxVerticalDistance) {
        return strollFlyOrSwim(speedModifier, (pathfindermob) -> {
            return LandRandomPos.getPos(pathfindermob, maxHorizontalDistance, maxVerticalDistance);
        }, (pathfindermob) -> {
            return true;
        });
    }

    public static BehaviorControl<PathfinderMob> fly(float speedModifier) {
        return strollFlyOrSwim(speedModifier, (pathfindermob) -> {
            return getTargetFlyPos(pathfindermob, 10, 7);
        }, (pathfindermob) -> {
            return true;
        });
    }

    public static BehaviorControl<PathfinderMob> swim(float speedModifier) {
        return strollFlyOrSwim(speedModifier, RandomStroll::getTargetSwimPos, Entity::isInWater);
    }

    private static OneShot<PathfinderMob> strollFlyOrSwim(float speedModifier, Function<PathfinderMob, Vec3> fetchTargetPos, Predicate<PathfinderMob> canRun) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.absent(MemoryModuleType.WALK_TARGET)).apply(behaviorbuilder_instance, (memoryaccessor) -> {
                return (serverlevel, pathfindermob, i) -> {
                    if (!canRun.test(pathfindermob)) {
                        return false;
                    } else {
                        Optional<Vec3> optional = Optional.ofNullable((Vec3) fetchTargetPos.apply(pathfindermob));

                        memoryaccessor.setOrErase(optional.map((vec3) -> {
                            return new WalkTarget(vec3, speedModifier, 0);
                        }));
                        return true;
                    }
                };
            });
        });
    }

    private static @Nullable Vec3 getTargetSwimPos(PathfinderMob body) {
        Vec3 vec3 = null;
        Vec3 vec31 = null;

        for (int[] aint : RandomStroll.SWIM_XY_DISTANCE_TIERS) {
            if (vec3 == null) {
                vec31 = BehaviorUtils.getRandomSwimmablePos(body, aint[0], aint[1]);
            } else {
                vec31 = body.position().add(body.position().vectorTo(vec3).normalize().multiply((double) aint[0], (double) aint[1], (double) aint[0]));
            }

            boolean flag = GoalUtils.mobRestricted(body, (double) aint[0]);

            if (vec31 == null || body.level().getFluidState(BlockPos.containing(vec31)).isEmpty() || GoalUtils.isRestricted(flag, body, vec31)) {
                return vec3;
            }

            vec3 = vec31;
        }

        return vec31;
    }

    private static @Nullable Vec3 getTargetFlyPos(PathfinderMob body, int maxHorizontalDistance, int maxVerticalDistance) {
        Vec3 vec3 = body.getViewVector(0.0F);

        return AirAndWaterRandomPos.getPos(body, maxHorizontalDistance, maxVerticalDistance, -2, vec3.x, vec3.z, (double) ((float) Math.PI / 2F));
    }
}

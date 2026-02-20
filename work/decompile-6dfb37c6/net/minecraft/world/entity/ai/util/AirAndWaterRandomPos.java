package net.minecraft.world.entity.ai.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class AirAndWaterRandomPos {

    public AirAndWaterRandomPos() {}

    public static @Nullable Vec3 getPos(PathfinderMob mob, int horizontalDist, int verticalDist, int flyingHeight, double xDir, double zDir, double maxXzRadiansDifference) {
        boolean flag = GoalUtils.mobRestricted(mob, (double) horizontalDist);

        return RandomPos.generateRandomPos(mob, () -> {
            return generateRandomPos(mob, horizontalDist, verticalDist, flyingHeight, xDir, zDir, maxXzRadiansDifference, flag);
        });
    }

    public static @Nullable BlockPos generateRandomPos(PathfinderMob mob, int horizontalDist, int verticalDist, int flyingHeight, double xDir, double zDir, double maxXzRadiansDifference, boolean restrict) {
        BlockPos blockpos = RandomPos.generateRandomDirectionWithinRadians(mob.getRandom(), 0.0D, (double) horizontalDist, verticalDist, flyingHeight, xDir, zDir, maxXzRadiansDifference);

        if (blockpos == null) {
            return null;
        } else {
            BlockPos blockpos1 = RandomPos.generateRandomPosTowardDirection(mob, (double) horizontalDist, mob.getRandom(), blockpos);

            if (!GoalUtils.isOutsideLimits(blockpos1, mob) && !GoalUtils.isRestricted(restrict, mob, blockpos1)) {
                blockpos1 = RandomPos.moveUpOutOfSolid(blockpos1, mob.level().getMaxY(), (blockpos2) -> {
                    return GoalUtils.isSolid(mob, blockpos2);
                });
                return GoalUtils.hasMalus(mob, blockpos1) ? null : blockpos1;
            } else {
                return null;
            }
        }
    }
}

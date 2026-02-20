package net.minecraft.world.entity.ai.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class DefaultRandomPos {

    public DefaultRandomPos() {}

    public static @Nullable Vec3 getPos(PathfinderMob mob, int horizontalDist, int verticalDist) {
        boolean flag = GoalUtils.mobRestricted(mob, (double) horizontalDist);

        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos blockpos = RandomPos.generateRandomDirection(mob.getRandom(), horizontalDist, verticalDist);

            return generateRandomPosTowardDirection(mob, horizontalDist, flag, blockpos);
        });
    }

    public static @Nullable Vec3 getPosTowards(PathfinderMob mob, int horizontalDist, int verticalDist, Vec3 towardsPos, double maxXzRadiansFromDir) {
        Vec3 vec31 = towardsPos.subtract(mob.getX(), mob.getY(), mob.getZ());
        boolean flag = GoalUtils.mobRestricted(mob, (double) horizontalDist);

        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos blockpos = RandomPos.generateRandomDirectionWithinRadians(mob.getRandom(), 0.0D, (double) horizontalDist, verticalDist, 0, vec31.x, vec31.z, maxXzRadiansFromDir);

            return blockpos == null ? null : generateRandomPosTowardDirection(mob, horizontalDist, flag, blockpos);
        });
    }

    public static @Nullable Vec3 getPosAway(PathfinderMob mob, int horizontalDist, int verticalDist, Vec3 avoidPos) {
        Vec3 vec31 = mob.position().subtract(avoidPos);
        boolean flag = GoalUtils.mobRestricted(mob, (double) horizontalDist);

        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos blockpos = RandomPos.generateRandomDirectionWithinRadians(mob.getRandom(), 0.0D, (double) horizontalDist, verticalDist, 0, vec31.x, vec31.z, (double) ((float) Math.PI / 2F));

            return blockpos == null ? null : generateRandomPosTowardDirection(mob, horizontalDist, flag, blockpos);
        });
    }

    private static @Nullable BlockPos generateRandomPosTowardDirection(PathfinderMob mob, int horizontalDist, boolean restrict, BlockPos direction) {
        BlockPos blockpos1 = RandomPos.generateRandomPosTowardDirection(mob, (double) horizontalDist, mob.getRandom(), direction);

        return !GoalUtils.isOutsideLimits(blockpos1, mob) && !GoalUtils.isRestricted(restrict, mob, blockpos1) && !GoalUtils.isNotStable(mob.getNavigation(), blockpos1) && !GoalUtils.hasMalus(mob, blockpos1) ? blockpos1 : null;
    }
}

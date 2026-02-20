package net.minecraft.world.entity.ai.util;

import java.util.Objects;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class LandRandomPos {

    public LandRandomPos() {}

    public static @Nullable Vec3 getPos(PathfinderMob mob, int horizontalDist, int verticalDist) {
        Objects.requireNonNull(mob);
        return getPos(mob, horizontalDist, verticalDist, mob::getWalkTargetValue);
    }

    public static @Nullable Vec3 getPos(PathfinderMob mob, int horizontalDist, int verticalDist, ToDoubleFunction<BlockPos> positionWeight) {
        boolean flag = GoalUtils.mobRestricted(mob, (double) horizontalDist);

        return RandomPos.generateRandomPos(() -> {
            BlockPos blockpos = RandomPos.generateRandomDirection(mob.getRandom(), horizontalDist, verticalDist);
            BlockPos blockpos1 = generateRandomPosTowardDirection(mob, (double) horizontalDist, flag, blockpos);

            return blockpos1 == null ? null : movePosUpOutOfSolid(mob, blockpos1);
        }, positionWeight);
    }

    public static @Nullable Vec3 getPosTowards(PathfinderMob mob, int horizontalDist, int verticalDist, Vec3 towardsPos) {
        Vec3 vec31 = towardsPos.subtract(mob.getX(), mob.getY(), mob.getZ());
        boolean flag = GoalUtils.mobRestricted(mob, (double) horizontalDist);

        return getPosInDirection(mob, 0.0D, (double) horizontalDist, verticalDist, vec31, flag);
    }

    public static @Nullable Vec3 getPosAway(PathfinderMob mob, int horizontalDist, int verticalDist, Vec3 avoidPos) {
        return getPosAway(mob, 0.0D, (double) horizontalDist, verticalDist, avoidPos);
    }

    public static @Nullable Vec3 getPosAway(PathfinderMob mob, double minHorizontalDist, double maxHorizontalDist, int verticalDist, Vec3 avoidPos) {
        Vec3 vec31 = mob.position().subtract(avoidPos);

        if (vec31.length() == 0.0D) {
            vec31 = new Vec3(mob.getRandom().nextDouble() - 0.5D, 0.0D, mob.getRandom().nextDouble() - 0.5D);
        }

        boolean flag = GoalUtils.mobRestricted(mob, maxHorizontalDist);

        return getPosInDirection(mob, minHorizontalDist, maxHorizontalDist, verticalDist, vec31, flag);
    }

    private static @Nullable Vec3 getPosInDirection(PathfinderMob mob, double minHorizontalDist, double maxHorizontalDist, int verticalDist, Vec3 dir, boolean restrict) {
        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos blockpos = RandomPos.generateRandomDirectionWithinRadians(mob.getRandom(), minHorizontalDist, maxHorizontalDist, verticalDist, 0, dir.x, dir.z, (double) ((float) Math.PI / 2F));

            if (blockpos == null) {
                return null;
            } else {
                BlockPos blockpos1 = generateRandomPosTowardDirection(mob, maxHorizontalDist, restrict, blockpos);

                return blockpos1 == null ? null : movePosUpOutOfSolid(mob, blockpos1);
            }
        });
    }

    public static @Nullable BlockPos movePosUpOutOfSolid(PathfinderMob mob, BlockPos pos) {
        pos = RandomPos.moveUpOutOfSolid(pos, mob.level().getMaxY(), (blockpos1) -> {
            return GoalUtils.isSolid(mob, blockpos1);
        });
        return !GoalUtils.isWater(mob, pos) && !GoalUtils.hasMalus(mob, pos) ? pos : null;
    }

    public static @Nullable BlockPos generateRandomPosTowardDirection(PathfinderMob mob, double horizontalDist, boolean restrict, BlockPos direction) {
        BlockPos blockpos1 = RandomPos.generateRandomPosTowardDirection(mob, horizontalDist, mob.getRandom(), direction);

        return !GoalUtils.isOutsideLimits(blockpos1, mob) && !GoalUtils.isRestricted(restrict, mob, blockpos1) && !GoalUtils.isNotStable(mob.getNavigation(), blockpos1) ? blockpos1 : null;
    }
}

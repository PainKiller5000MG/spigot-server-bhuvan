package net.minecraft.world.entity.ai.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class HoverRandomPos {

    public HoverRandomPos() {}

    public static @Nullable Vec3 getPos(PathfinderMob mob, int horizontalDist, int verticalDist, double xDir, double zDir, float maxXzRadiansDifference, int hoverMaxHeight, int hoverMinHeight) {
        boolean flag = GoalUtils.mobRestricted(mob, (double) horizontalDist);

        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos blockpos = RandomPos.generateRandomDirectionWithinRadians(mob.getRandom(), 0.0D, (double) horizontalDist, verticalDist, 0, xDir, zDir, (double) maxXzRadiansDifference);

            if (blockpos == null) {
                return null;
            } else {
                BlockPos blockpos1 = LandRandomPos.generateRandomPosTowardDirection(mob, (double) horizontalDist, flag, blockpos);

                if (blockpos1 == null) {
                    return null;
                } else {
                    blockpos1 = RandomPos.moveUpToAboveSolid(blockpos1, mob.getRandom().nextInt(hoverMaxHeight - hoverMinHeight + 1) + hoverMinHeight, mob.level().getMaxY(), (blockpos2) -> {
                        return GoalUtils.isSolid(mob, blockpos2);
                    });
                    return !GoalUtils.isWater(mob, blockpos1) && !GoalUtils.hasMalus(mob, blockpos1) ? blockpos1 : null;
                }
            }
        });
    }
}

package net.minecraft.world.entity.ai.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class AirRandomPos {

    public AirRandomPos() {}

    public static @Nullable Vec3 getPosTowards(PathfinderMob mob, int horizontalDist, int verticalDist, int flyingHeight, Vec3 towardsPos, double maxXzRadiansFromDir) {
        Vec3 vec31 = towardsPos.subtract(mob.getX(), mob.getY(), mob.getZ());
        boolean flag = GoalUtils.mobRestricted(mob, (double) horizontalDist);

        return RandomPos.generateRandomPos(mob, () -> {
            BlockPos blockpos = AirAndWaterRandomPos.generateRandomPos(mob, horizontalDist, verticalDist, flyingHeight, vec31.x, vec31.z, maxXzRadiansFromDir, flag);

            return blockpos != null && !GoalUtils.isWater(mob, blockpos) ? blockpos : null;
        });
    }
}

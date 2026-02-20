package net.minecraft.world.entity.monster.breeze;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class BreezeUtil {

    private static final double MAX_LINE_OF_SIGHT_TEST_RANGE = 50.0D;

    public BreezeUtil() {}

    public static Vec3 randomPointBehindTarget(LivingEntity enemy, RandomSource random) {
        int i = 90;
        float f = enemy.yHeadRot + 180.0F + (float) random.nextGaussian() * 90.0F / 2.0F;
        float f1 = Mth.lerp(random.nextFloat(), 4.0F, 8.0F);
        Vec3 vec3 = Vec3.directionFromRotation(0.0F, f).scale((double) f1);

        return enemy.position().add(vec3);
    }

    public static boolean hasLineOfSight(Breeze breeze, Vec3 target) {
        Vec3 vec31 = new Vec3(breeze.getX(), breeze.getY(), breeze.getZ());

        return target.distanceTo(vec31) > getMaxLineOfSightTestRange(breeze) ? false : breeze.level().clip(new ClipContext(vec31, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, breeze)).getType() == HitResult.Type.MISS;
    }

    private static double getMaxLineOfSightTestRange(Breeze breeze) {
        return Math.max(50.0D, breeze.getAttributeValue(Attributes.FOLLOW_RANGE));
    }
}

package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

public final class LongJumpUtil {

    public LongJumpUtil() {}

    public static Optional<Vec3> calculateJumpVectorForAngle(Mob body, Vec3 targetPos, float maxJumpVelocity, int angle, boolean checkCollision) {
        Vec3 vec31 = body.position();
        Vec3 vec32 = (new Vec3(targetPos.x - vec31.x, 0.0D, targetPos.z - vec31.z)).normalize().scale(0.5D);
        Vec3 vec33 = targetPos.subtract(vec32);
        Vec3 vec34 = vec33.subtract(vec31);
        float f1 = (float) angle * (float) Math.PI / 180.0F;
        double d0 = Math.atan2(vec34.z, vec34.x);
        double d1 = vec34.subtract(0.0D, vec34.y, 0.0D).lengthSqr();
        double d2 = Math.sqrt(d1);
        double d3 = vec34.y;
        double d4 = body.getGravity();
        double d5 = Math.sin((double) (2.0F * f1));
        double d6 = Math.pow(Math.cos((double) f1), 2.0D);
        double d7 = Math.sin((double) f1);
        double d8 = Math.cos((double) f1);
        double d9 = Math.sin(d0);
        double d10 = Math.cos(d0);
        double d11 = d1 * d4 / (d2 * d5 - 2.0D * d3 * d6);

        if (d11 < 0.0D) {
            return Optional.empty();
        } else {
            double d12 = Math.sqrt(d11);

            if (d12 > (double) maxJumpVelocity) {
                return Optional.empty();
            } else {
                double d13 = d12 * d8;
                double d14 = d12 * d7;

                if (checkCollision) {
                    int j = Mth.ceil(d2 / d13) * 2;
                    double d15 = 0.0D;
                    Vec3 vec35 = null;
                    EntityDimensions entitydimensions = body.getDimensions(Pose.LONG_JUMPING);

                    for (int k = 0; k < j - 1; ++k) {
                        d15 += d2 / (double) j;
                        double d16 = d7 / d8 * d15 - Math.pow(d15, 2.0D) * d4 / (2.0D * d11 * Math.pow(d8, 2.0D));
                        double d17 = d15 * d10;
                        double d18 = d15 * d9;
                        Vec3 vec36 = new Vec3(vec31.x + d17, vec31.y + d16, vec31.z + d18);

                        if (vec35 != null && !isClearTransition(body, entitydimensions, vec35, vec36)) {
                            return Optional.empty();
                        }

                        vec35 = vec36;
                    }
                }

                return Optional.of((new Vec3(d13 * d10, d14, d13 * d9)).scale((double) 0.95F));
            }
        }
    }

    private static boolean isClearTransition(Mob body, EntityDimensions entityDimensions, Vec3 position1, Vec3 position2) {
        Vec3 vec32 = position2.subtract(position1);
        double d0 = (double) Math.min(entityDimensions.width(), entityDimensions.height());
        int i = Mth.ceil(vec32.length() / d0);
        Vec3 vec33 = vec32.normalize();
        Vec3 vec34 = position1;

        for (int j = 0; j < i; ++j) {
            vec34 = j == i - 1 ? position2 : vec34.add(vec33.scale(d0 * (double) 0.9F));
            if (!body.level().noCollision(body, entityDimensions.makeBoundingBox(vec34))) {
                return false;
            }
        }

        return true;
    }
}

package net.minecraft.world.entity.monster.hoglin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

public interface HoglinBase {

    int ATTACK_ANIMATION_DURATION = 10;
    float PROBABILITY_OF_SPAWNING_AS_BABY = 0.2F;

    int getAttackAnimationRemainingTicks();

    static boolean hurtAndThrowTarget(ServerLevel level, LivingEntity body, LivingEntity target) {
        float f = (float) body.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float f1;

        if (!body.isBaby() && (int) f > 0) {
            f1 = f / 2.0F + (float) level.random.nextInt((int) f);
        } else {
            f1 = f;
        }

        DamageSource damagesource = body.damageSources().mobAttack(body);
        boolean flag = target.hurtServer(level, damagesource, f1);

        if (flag) {
            EnchantmentHelper.doPostAttackEffects(level, target, damagesource);
            if (!body.isBaby()) {
                throwTarget(body, target);
            }
        }

        return flag;
    }

    static void throwTarget(LivingEntity body, LivingEntity target) {
        double d0 = body.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        double d1 = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        double d2 = d0 - d1;

        if (d2 > 0.0D) {
            double d3 = target.getX() - body.getX();
            double d4 = target.getZ() - body.getZ();
            float f = (float) (body.level().random.nextInt(21) - 10);
            double d5 = d2 * (double) (body.level().random.nextFloat() * 0.5F + 0.2F);
            Vec3 vec3 = (new Vec3(d3, 0.0D, d4)).normalize().scale(d5).yRot(f);
            double d6 = d2 * (double) body.level().random.nextFloat() * 0.5D;

            target.push(vec3.x, d6, vec3.z);
            target.hurtMarked = true;
        }
    }
}

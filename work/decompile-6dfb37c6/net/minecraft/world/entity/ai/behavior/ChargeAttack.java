package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

public class ChargeAttack extends Behavior<Animal> {

    private final int timeBetweenAttacks;
    private final TargetingConditions chargeTargeting;
    private final float speed;
    private final float knockbackForce;
    private final double maxTargetDetectionDistance;
    private final double maxChargeDistance;
    private final SoundEvent chargeSound;
    private Vec3 chargeVelocityVector;
    private Vec3 startPosition;

    public ChargeAttack(int timeBetweenAttacks, TargetingConditions chargeTargeting, float speed, float knockbackForce, double maxChargeDistance, double maxTargetDetectionDistance, SoundEvent chargeSound) {
        super(ImmutableMap.of(MemoryModuleType.CHARGE_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT));
        this.timeBetweenAttacks = timeBetweenAttacks;
        this.chargeTargeting = chargeTargeting;
        this.speed = speed;
        this.knockbackForce = knockbackForce;
        this.maxChargeDistance = maxChargeDistance;
        this.maxTargetDetectionDistance = maxTargetDetectionDistance;
        this.chargeSound = chargeSound;
        this.chargeVelocityVector = Vec3.ZERO;
        this.startPosition = Vec3.ZERO;
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Animal body) {
        return body.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    protected boolean canStillUse(ServerLevel level, Animal body, long timestamp) {
        Brain<?> brain = body.getBrain();
        Optional<LivingEntity> optional = brain.<LivingEntity>getMemory(MemoryModuleType.ATTACK_TARGET);

        if (optional.isEmpty()) {
            return false;
        } else {
            LivingEntity livingentity = (LivingEntity) optional.get();

            if (body instanceof TamableAnimal) {
                TamableAnimal tamableanimal = (TamableAnimal) body;

                if (tamableanimal.isTame()) {
                    return false;
                }
            }

            return body.position().subtract(this.startPosition).lengthSqr() >= this.maxChargeDistance * this.maxChargeDistance ? false : (livingentity.position().subtract(body.position()).lengthSqr() >= this.maxTargetDetectionDistance * this.maxTargetDetectionDistance ? false : (!body.hasLineOfSight(livingentity) ? false : !brain.hasMemoryValue(MemoryModuleType.CHARGE_COOLDOWN_TICKS)));
        }
    }

    protected void start(ServerLevel level, Animal body, long timestamp) {
        Brain<?> brain = body.getBrain();

        this.startPosition = body.position();
        LivingEntity livingentity = (LivingEntity) brain.getMemory(MemoryModuleType.ATTACK_TARGET).get();
        Vec3 vec3 = livingentity.position().subtract(body.position()).normalize();

        this.chargeVelocityVector = vec3.scale((double) this.speed);
        if (this.canStillUse(level, body, timestamp)) {
            body.playSound(this.chargeSound);
        }

    }

    protected void tick(ServerLevel level, Animal body, long timestamp) {
        Brain<?> brain = body.getBrain();
        LivingEntity livingentity = (LivingEntity) brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElseThrow();

        body.lookAt(livingentity, 360.0F, 360.0F);
        body.setDeltaMovement(this.chargeVelocityVector);
        List<LivingEntity> list = new ArrayList(1);

        level.getEntities(EntityTypeTest.forClass(LivingEntity.class), body.getBoundingBox(), (livingentity1) -> {
            return this.chargeTargeting.test(level, body, livingentity1);
        }, list, 1);
        if (!list.isEmpty()) {
            LivingEntity livingentity1 = (LivingEntity) list.get(0);

            if (body.hasPassenger(livingentity1)) {
                return;
            }

            this.dealDamageToTarget(level, body, livingentity1);
            this.dealKnockBack(body, livingentity1);
            this.stop(level, body, timestamp);
        }

    }

    private void dealDamageToTarget(ServerLevel level, Animal body, LivingEntity target) {
        DamageSource damagesource = level.damageSources().mobAttack(body);
        float f = (float) body.getAttributeValue(Attributes.ATTACK_DAMAGE);

        if (target.hurtServer(level, damagesource, f)) {
            EnchantmentHelper.doPostAttackEffects(level, target, damagesource);
        }

    }

    private void dealKnockBack(Animal body, LivingEntity target) {
        int i = body.hasEffect(MobEffects.SPEED) ? body.getEffect(MobEffects.SPEED).getAmplifier() + 1 : 0;
        int j = body.hasEffect(MobEffects.SLOWNESS) ? body.getEffect(MobEffects.SLOWNESS).getAmplifier() + 1 : 0;
        float f = 0.25F * (float) (i - j);
        float f1 = Mth.clamp(this.speed * (float) body.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.2F, 2.0F) + f;

        body.causeExtraKnockback(target, f1 * this.knockbackForce, body.getDeltaMovement());
    }

    protected void stop(ServerLevel level, Animal body, long timestamp) {
        body.getBrain().setMemory(MemoryModuleType.CHARGE_COOLDOWN_TICKS, this.timeBetweenAttacks);
        body.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
    }
}

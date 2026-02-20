package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

public class RamTarget extends Behavior<Goat> {

    public static final int TIME_OUT_DURATION = 200;
    public static final float RAM_SPEED_FORCE_FACTOR = 1.65F;
    private final Function<Goat, UniformInt> getTimeBetweenRams;
    private final TargetingConditions ramTargeting;
    private final float speed;
    private final ToDoubleFunction<Goat> getKnockbackForce;
    private Vec3 ramDirection;
    private final Function<Goat, SoundEvent> getImpactSound;
    private final Function<Goat, SoundEvent> getHornBreakSound;

    public RamTarget(Function<Goat, UniformInt> getTimeBetweenRams, TargetingConditions ramTargeting, float speed, ToDoubleFunction<Goat> getKnockbackForce, Function<Goat, SoundEvent> getImpactSound, Function<Goat, SoundEvent> getHornBreakSound) {
        super(ImmutableMap.of(MemoryModuleType.RAM_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.RAM_TARGET, MemoryStatus.VALUE_PRESENT), 200);
        this.getTimeBetweenRams = getTimeBetweenRams;
        this.ramTargeting = ramTargeting;
        this.speed = speed;
        this.getKnockbackForce = getKnockbackForce;
        this.getImpactSound = getImpactSound;
        this.getHornBreakSound = getHornBreakSound;
        this.ramDirection = Vec3.ZERO;
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Goat body) {
        return body.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET);
    }

    protected boolean canStillUse(ServerLevel level, Goat body, long timestamp) {
        return body.getBrain().hasMemoryValue(MemoryModuleType.RAM_TARGET);
    }

    protected void start(ServerLevel level, Goat body, long timestamp) {
        BlockPos blockpos = body.blockPosition();
        Brain<?> brain = body.getBrain();
        Vec3 vec3 = (Vec3) brain.getMemory(MemoryModuleType.RAM_TARGET).get();

        this.ramDirection = (new Vec3((double) blockpos.getX() - vec3.x(), 0.0D, (double) blockpos.getZ() - vec3.z())).normalize();
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(vec3, this.speed, 0));
    }

    protected void tick(ServerLevel level, Goat body, long timestamp) {
        List<LivingEntity> list = level.<LivingEntity>getNearbyEntities(LivingEntity.class, this.ramTargeting, body, body.getBoundingBox());
        Brain<?> brain = body.getBrain();

        if (!list.isEmpty()) {
            LivingEntity livingentity = (LivingEntity) list.get(0);
            DamageSource damagesource = level.damageSources().noAggroMobAttack(body);
            float f = (float) body.getAttributeValue(Attributes.ATTACK_DAMAGE);

            if (livingentity.hurtServer(level, damagesource, f)) {
                EnchantmentHelper.doPostAttackEffects(level, livingentity, damagesource);
            }

            int j = body.hasEffect(MobEffects.SPEED) ? body.getEffect(MobEffects.SPEED).getAmplifier() + 1 : 0;
            int k = body.hasEffect(MobEffects.SLOWNESS) ? body.getEffect(MobEffects.SLOWNESS).getAmplifier() + 1 : 0;
            float f1 = 0.25F * (float) (j - k);
            float f2 = Mth.clamp(body.getSpeed() * 1.65F, 0.2F, 3.0F) + f1;
            DamageSource damagesource1 = level.damageSources().mobAttack(body);
            float f3 = livingentity.applyItemBlocking(level, damagesource1, f);
            float f4 = f3 > 0.0F ? 0.5F : 1.0F;

            livingentity.knockback((double) (f4 * f2) * this.getKnockbackForce.applyAsDouble(body), this.ramDirection.x(), this.ramDirection.z());
            this.finishRam(level, body);
            level.playSound((Entity) null, (Entity) body, (SoundEvent) this.getImpactSound.apply(body), SoundSource.NEUTRAL, 1.0F, 1.0F);
        } else if (this.hasRammedHornBreakingBlock(level, body)) {
            level.playSound((Entity) null, (Entity) body, (SoundEvent) this.getImpactSound.apply(body), SoundSource.NEUTRAL, 1.0F, 1.0F);
            boolean flag = body.dropHorn();

            if (flag) {
                level.playSound((Entity) null, (Entity) body, (SoundEvent) this.getHornBreakSound.apply(body), SoundSource.NEUTRAL, 1.0F, 1.0F);
            }

            this.finishRam(level, body);
        } else {
            Optional<WalkTarget> optional = brain.<WalkTarget>getMemory(MemoryModuleType.WALK_TARGET);
            Optional<Vec3> optional1 = brain.<Vec3>getMemory(MemoryModuleType.RAM_TARGET);
            boolean flag1 = optional.isEmpty() || optional1.isEmpty() || ((WalkTarget) optional.get()).getTarget().currentPosition().closerThan((Position) optional1.get(), 0.25D);

            if (flag1) {
                this.finishRam(level, body);
            }
        }

    }

    private boolean hasRammedHornBreakingBlock(ServerLevel level, Goat body) {
        Vec3 vec3 = body.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).normalize();
        BlockPos blockpos = BlockPos.containing(body.position().add(vec3));

        return level.getBlockState(blockpos).is(BlockTags.SNAPS_GOAT_HORN) || level.getBlockState(blockpos.above()).is(BlockTags.SNAPS_GOAT_HORN);
    }

    protected void finishRam(ServerLevel level, Goat body) {
        level.broadcastEntityEvent(body, (byte) 59);
        body.getBrain().setMemory(MemoryModuleType.RAM_COOLDOWN_TICKS, ((UniformInt) this.getTimeBetweenRams.apply(body)).sample(level.random));
        body.getBrain().eraseMemory(MemoryModuleType.RAM_TARGET);
    }
}

package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public record KineticWeapon(int contactCooldownTicks, int delayTicks, Optional<KineticWeapon.Condition> dismountConditions, Optional<KineticWeapon.Condition> knockbackConditions, Optional<KineticWeapon.Condition> damageConditions, float forwardMovement, float damageMultiplier, Optional<Holder<SoundEvent>> sound, Optional<Holder<SoundEvent>> hitSound) {

    public static final int HIT_FEEDBACK_TICKS = 10;
    public static final Codec<KineticWeapon> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("contact_cooldown_ticks", 10).forGetter(KineticWeapon::contactCooldownTicks), ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("delay_ticks", 0).forGetter(KineticWeapon::delayTicks), KineticWeapon.Condition.CODEC.optionalFieldOf("dismount_conditions").forGetter(KineticWeapon::dismountConditions), KineticWeapon.Condition.CODEC.optionalFieldOf("knockback_conditions").forGetter(KineticWeapon::knockbackConditions), KineticWeapon.Condition.CODEC.optionalFieldOf("damage_conditions").forGetter(KineticWeapon::damageConditions), Codec.FLOAT.optionalFieldOf("forward_movement", 0.0F).forGetter(KineticWeapon::forwardMovement), Codec.FLOAT.optionalFieldOf("damage_multiplier", 1.0F).forGetter(KineticWeapon::damageMultiplier), SoundEvent.CODEC.optionalFieldOf("sound").forGetter(KineticWeapon::sound), SoundEvent.CODEC.optionalFieldOf("hit_sound").forGetter(KineticWeapon::hitSound)).apply(instance, KineticWeapon::new);
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, KineticWeapon> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, KineticWeapon::contactCooldownTicks, ByteBufCodecs.VAR_INT, KineticWeapon::delayTicks, KineticWeapon.Condition.STREAM_CODEC.apply(ByteBufCodecs::optional), KineticWeapon::dismountConditions, KineticWeapon.Condition.STREAM_CODEC.apply(ByteBufCodecs::optional), KineticWeapon::knockbackConditions, KineticWeapon.Condition.STREAM_CODEC.apply(ByteBufCodecs::optional), KineticWeapon::damageConditions, ByteBufCodecs.FLOAT, KineticWeapon::forwardMovement, ByteBufCodecs.FLOAT, KineticWeapon::damageMultiplier, SoundEvent.STREAM_CODEC.apply(ByteBufCodecs::optional), KineticWeapon::sound, SoundEvent.STREAM_CODEC.apply(ByteBufCodecs::optional), KineticWeapon::hitSound, KineticWeapon::new);

    public static Vec3 getMotion(Entity livingEntity) {
        if (!(livingEntity instanceof Player) && livingEntity.isPassenger()) {
            livingEntity = livingEntity.getRootVehicle();
        }

        return livingEntity.getKnownSpeed().scale(20.0D);
    }

    public void makeSound(Entity causer) {
        this.sound.ifPresent((holder) -> {
            causer.level().playSound(causer, causer.getX(), causer.getY(), causer.getZ(), holder, causer.getSoundSource(), 1.0F, 1.0F);
        });
    }

    public void makeLocalHitSound(Entity causer) {
        this.hitSound.ifPresent((holder) -> {
            causer.level().playLocalSound(causer, (SoundEvent) holder.value(), causer.getSoundSource(), 1.0F, 1.0F);
        });
    }

    public int computeDamageUseDuration() {
        return this.delayTicks + (Integer) this.damageConditions.map(KineticWeapon.Condition::maxDurationTicks).orElse(0);
    }

    public void damageEntities(ItemStack stack, int ticksRemaining, LivingEntity livingEntity, EquipmentSlot equipmentSlot) {
        int j = stack.getUseDuration(livingEntity) - ticksRemaining;

        if (j >= this.delayTicks) {
            j -= this.delayTicks;
            Vec3 vec3 = livingEntity.getLookAngle();
            double d0 = vec3.dot(getMotion(livingEntity));
            float f = livingEntity instanceof Player ? 1.0F : 0.2F;
            AttackRange attackrange = livingEntity.entityAttackRange();
            double d1 = livingEntity.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
            boolean flag = false;

            for (EntityHitResult entityhitresult : (Collection) ProjectileUtil.getHitEntitiesAlong(livingEntity, attackrange, (entity) -> {
                return PiercingWeapon.canHitEntity(livingEntity, entity);
            }, ClipContext.Block.COLLIDER).map((blockhitresult) -> {
                return List.of();
            }, (collection) -> {
                return collection;
            })) {
                Entity entity = entityhitresult.getEntity();

                if (entity instanceof EnderDragonPart) {
                    EnderDragonPart enderdragonpart = (EnderDragonPart) entity;

                    entity = enderdragonpart.parentMob;
                }

                boolean flag1 = livingEntity.wasRecentlyStabbed(entity, this.contactCooldownTicks);

                if (!flag1) {
                    livingEntity.rememberStabbedEntity(entity);
                    double d2 = vec3.dot(getMotion(entity));
                    double d3 = Math.max(0.0D, d0 - d2);
                    boolean flag2 = this.dismountConditions.isPresent() && ((KineticWeapon.Condition) this.dismountConditions.get()).test(j, d0, d3, (double) f);
                    boolean flag3 = this.knockbackConditions.isPresent() && ((KineticWeapon.Condition) this.knockbackConditions.get()).test(j, d0, d3, (double) f);
                    boolean flag4 = this.damageConditions.isPresent() && ((KineticWeapon.Condition) this.damageConditions.get()).test(j, d0, d3, (double) f);

                    if (flag2 || flag3 || flag4) {
                        float f1 = (float) d1 + (float) Mth.floor(d3 * (double) this.damageMultiplier);

                        flag |= livingEntity.stabAttack(equipmentSlot, entity, f1, flag4, flag3, flag2);
                    }
                }
            }

            if (flag) {
                livingEntity.level().broadcastEntityEvent(livingEntity, (byte) 2);
                if (livingEntity instanceof ServerPlayer) {
                    ServerPlayer serverplayer = (ServerPlayer) livingEntity;

                    CriteriaTriggers.SPEAR_MOBS_TRIGGER.trigger(serverplayer, livingEntity.stabbedEntities((entity1) -> {
                        return entity1 instanceof LivingEntity;
                    }));
                }
            }

        }
    }

    public static record Condition(int maxDurationTicks, float minSpeed, float minRelativeSpeed) {

        public static final Codec<KineticWeapon.Condition> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(ExtraCodecs.NON_NEGATIVE_INT.fieldOf("max_duration_ticks").forGetter(KineticWeapon.Condition::maxDurationTicks), Codec.FLOAT.optionalFieldOf("min_speed", 0.0F).forGetter(KineticWeapon.Condition::minSpeed), Codec.FLOAT.optionalFieldOf("min_relative_speed", 0.0F).forGetter(KineticWeapon.Condition::minRelativeSpeed)).apply(instance, KineticWeapon.Condition::new);
        });
        public static final StreamCodec<ByteBuf, KineticWeapon.Condition> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, KineticWeapon.Condition::maxDurationTicks, ByteBufCodecs.FLOAT, KineticWeapon.Condition::minSpeed, ByteBufCodecs.FLOAT, KineticWeapon.Condition::minRelativeSpeed, KineticWeapon.Condition::new);

        public boolean test(int ticksUsed, double attackerSpeed, double relativeSpeed, double entityFactor) {
            return ticksUsed <= this.maxDurationTicks && attackerSpeed >= (double) this.minSpeed * entityFactor && relativeSpeed >= (double) this.minRelativeSpeed * entityFactor;
        }

        public static Optional<KineticWeapon.Condition> ofAttackerSpeed(int untilTicks, float minAttackerSpeed) {
            return Optional.of(new KineticWeapon.Condition(untilTicks, minAttackerSpeed, 0.0F));
        }

        public static Optional<KineticWeapon.Condition> ofRelativeSpeed(int untilTicks, float minRelativeSpeed) {
            return Optional.of(new KineticWeapon.Condition(untilTicks, 0.0F, minRelativeSpeed));
        }
    }
}

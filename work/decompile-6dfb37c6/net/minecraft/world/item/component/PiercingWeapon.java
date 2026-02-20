package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;

public record PiercingWeapon(boolean dealsKnockback, boolean dismounts, Optional<Holder<SoundEvent>> sound, Optional<Holder<SoundEvent>> hitSound) {

    public static final Codec<PiercingWeapon> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Codec.BOOL.optionalFieldOf("deals_knockback", true).forGetter(PiercingWeapon::dealsKnockback), Codec.BOOL.optionalFieldOf("dismounts", false).forGetter(PiercingWeapon::dismounts), SoundEvent.CODEC.optionalFieldOf("sound").forGetter(PiercingWeapon::sound), SoundEvent.CODEC.optionalFieldOf("hit_sound").forGetter(PiercingWeapon::hitSound)).apply(instance, PiercingWeapon::new);
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, PiercingWeapon> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, PiercingWeapon::dealsKnockback, ByteBufCodecs.BOOL, PiercingWeapon::dismounts, SoundEvent.STREAM_CODEC.apply(ByteBufCodecs::optional), PiercingWeapon::sound, SoundEvent.STREAM_CODEC.apply(ByteBufCodecs::optional), PiercingWeapon::hitSound, PiercingWeapon::new);

    public void makeSound(Entity causer) {
        this.sound.ifPresent((holder) -> {
            causer.level().playSound(causer, causer.getX(), causer.getY(), causer.getZ(), holder, causer.getSoundSource(), 1.0F, 1.0F);
        });
    }

    public void makeHitSound(Entity causer) {
        this.hitSound.ifPresent((holder) -> {
            causer.level().playSound((Entity) null, causer.getX(), causer.getY(), causer.getZ(), holder, causer.getSoundSource(), 1.0F, 1.0F);
        });
    }

    public static boolean canHitEntity(Entity jabber, Entity target) {
        if (!target.isInvulnerable() && target.isAlive()) {
            if (target instanceof Interaction) {
                return true;
            } else if (!target.canBeHitByProjectile()) {
                return false;
            } else {
                if (target instanceof Player) {
                    Player player = (Player) target;

                    if (jabber instanceof Player) {
                        Player player1 = (Player) jabber;

                        if (!player1.canHarmPlayer(player)) {
                            return false;
                        }
                    }
                }

                return !jabber.isPassengerOfSameVehicle(target);
            }
        } else {
            return false;
        }
    }

    public void attack(LivingEntity attacker, EquipmentSlot hand) {
        float f = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        AttackRange attackrange = attacker.entityAttackRange();
        boolean flag = false;

        for (EntityHitResult entityhitresult : (Collection) ProjectileUtil.getHitEntitiesAlong(attacker, attackrange, (entity) -> {
            return canHitEntity(attacker, entity);
        }, ClipContext.Block.COLLIDER).map((blockhitresult) -> {
            return List.of();
        }, (collection) -> {
            return collection;
        })) {
            flag |= attacker.stabAttack(hand, entityhitresult.getEntity(), f, true, this.dealsKnockback, this.dismounts);
        }

        attacker.onAttack();
        attacker.lungeForwardMaybe();
        if (flag) {
            this.makeHitSound(attacker);
        }

        this.makeSound(attacker);
        attacker.swing(InteractionHand.MAIN_HAND, false);
    }
}

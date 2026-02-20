package net.minecraft.world.item;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class MaceItem extends Item {

    private static final int DEFAULT_ATTACK_DAMAGE = 5;
    private static final float DEFAULT_ATTACK_SPEED = -3.4F;
    public static final float SMASH_ATTACK_FALL_THRESHOLD = 1.5F;
    private static final float SMASH_ATTACK_HEAVY_THRESHOLD = 5.0F;
    public static final float SMASH_ATTACK_KNOCKBACK_RADIUS = 3.5F;
    private static final float SMASH_ATTACK_KNOCKBACK_POWER = 0.7F;

    public MaceItem(Item.Properties properties) {
        super(properties);
    }

    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder().add(Attributes.ATTACK_DAMAGE, new AttributeModifier(MaceItem.BASE_ATTACK_DAMAGE_ID, 5.0D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).add(Attributes.ATTACK_SPEED, new AttributeModifier(MaceItem.BASE_ATTACK_SPEED_ID, (double) -3.4F, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build();
    }

    public static Tool createToolProperties() {
        return new Tool(List.of(), 1.0F, 2, false);
    }

    @Override
    public void hurtEnemy(ItemStack itemStack, LivingEntity mob, LivingEntity attacker) {
        if (canSmashAttack(attacker)) {
            ServerLevel serverlevel = (ServerLevel) attacker.level();

            attacker.setDeltaMovement(attacker.getDeltaMovement().with(Direction.Axis.Y, (double) 0.01F));
            if (attacker instanceof ServerPlayer) {
                ServerPlayer serverplayer = (ServerPlayer) attacker;

                serverplayer.currentImpulseImpactPos = this.calculateImpactPosition(serverplayer);
                serverplayer.setIgnoreFallDamageFromCurrentImpulse(true);
                serverplayer.connection.send(new ClientboundSetEntityMotionPacket(serverplayer));
            }

            if (mob.onGround()) {
                if (attacker instanceof ServerPlayer) {
                    ServerPlayer serverplayer1 = (ServerPlayer) attacker;

                    serverplayer1.setSpawnExtraParticlesOnFall(true);
                }

                SoundEvent soundevent = attacker.fallDistance > 5.0D ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND;

                serverlevel.playSound((Entity) null, attacker.getX(), attacker.getY(), attacker.getZ(), soundevent, attacker.getSoundSource(), 1.0F, 1.0F);
            } else {
                serverlevel.playSound((Entity) null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.MACE_SMASH_AIR, attacker.getSoundSource(), 1.0F, 1.0F);
            }

            knockback(serverlevel, attacker, mob);
        }

    }

    private Vec3 calculateImpactPosition(ServerPlayer player) {
        return player.isIgnoringFallDamageFromCurrentImpulse() && player.currentImpulseImpactPos != null && player.currentImpulseImpactPos.y <= player.position().y ? player.currentImpulseImpactPos : player.position();
    }

    @Override
    public void postHurtEnemy(ItemStack itemStack, LivingEntity mob, LivingEntity attacker) {
        if (canSmashAttack(attacker)) {
            attacker.resetFallDistance();
        }

    }

    @Override
    public float getAttackDamageBonus(Entity victim, float ignoredDamage, DamageSource damageSource) {
        Entity entity1 = damageSource.getDirectEntity();

        if (entity1 instanceof LivingEntity livingentity) {
            if (!canSmashAttack(livingentity)) {
                return 0.0F;
            } else {
                double d0 = 3.0D;
                double d1 = 8.0D;
                double d2 = livingentity.fallDistance;
                double d3;

                if (d2 <= 3.0D) {
                    d3 = 4.0D * d2;
                } else if (d2 <= 8.0D) {
                    d3 = 12.0D + 2.0D * (d2 - 3.0D);
                } else {
                    d3 = 22.0D + d2 - 8.0D;
                }

                Level level = livingentity.level();

                if (level instanceof ServerLevel) {
                    ServerLevel serverlevel = (ServerLevel) level;

                    return (float) (d3 + (double) EnchantmentHelper.modifyFallBasedDamage(serverlevel, livingentity.getWeaponItem(), victim, damageSource, 0.0F) * d2);
                } else {
                    return (float) d3;
                }
            }
        } else {
            return 0.0F;
        }
    }

    private static void knockback(Level level, Entity attacker, Entity entity) {
        level.levelEvent(2013, entity.getOnPos(), 750);
        level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(3.5D), knockbackPredicate(attacker, entity)).forEach((livingentity) -> {
            Vec3 vec3 = livingentity.position().subtract(entity.position());
            double d0 = getKnockbackPower(attacker, livingentity, vec3);
            Vec3 vec31 = vec3.normalize().scale(d0);

            if (d0 > 0.0D) {
                livingentity.push(vec31.x, (double) 0.7F, vec31.z);
                if (livingentity instanceof ServerPlayer) {
                    ServerPlayer serverplayer = (ServerPlayer) livingentity;

                    serverplayer.connection.send(new ClientboundSetEntityMotionPacket(serverplayer));
                }
            }

        });
    }

    private static Predicate<LivingEntity> knockbackPredicate(Entity attacker, Entity entity) {
        return (livingentity) -> {
            boolean flag;
            boolean flag1;
            boolean flag2;
            boolean flag3;
            label82:
            {
                flag = !livingentity.isSpectator();
                flag1 = livingentity != attacker && livingentity != entity;
                flag2 = !attacker.isAlliedTo((Entity) livingentity);
                if (livingentity instanceof TamableAnimal tamableanimal) {
                    if (entity instanceof LivingEntity livingentity1) {
                        if (tamableanimal.isTame() && tamableanimal.isOwnedBy(livingentity1)) {
                            flag3 = true;
                            break label82;
                        }
                    }
                }

                flag3 = false;
            }

            boolean flag4;
            label74:
            {
                flag4 = !flag3;
                if (livingentity instanceof ArmorStand armorstand) {
                    if (armorstand.isMarker()) {
                        flag3 = false;
                        break label74;
                    }
                }

                flag3 = true;
            }

            boolean flag5;
            boolean flag6;
            label68:
            {
                flag5 = flag3;
                flag6 = entity.distanceToSqr((Entity) livingentity) <= Math.pow(3.5D, 2.0D);
                if (livingentity instanceof Player player) {
                    if (player.isCreative() && player.getAbilities().flying) {
                        flag3 = true;
                        break label68;
                    }
                }

                flag3 = false;
            }

            boolean flag7 = !flag3;

            return flag && flag1 && flag2 && flag4 && flag5 && flag6 && flag7;
        };
    }

    private static double getKnockbackPower(Entity attacker, LivingEntity nearby, Vec3 direction) {
        return (3.5D - direction.length()) * (double) 0.7F * (double) (attacker.fallDistance > 5.0D ? 2 : 1) * (1.0D - nearby.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
    }

    public static boolean canSmashAttack(LivingEntity attacker) {
        return attacker.fallDistance > 1.5D && !attacker.isFallFlying();
    }

    @Override
    public @Nullable DamageSource getItemDamageSource(LivingEntity attacker) {
        return canSmashAttack(attacker) ? attacker.damageSources().mace(attacker) : super.getItemDamageSource(attacker);
    }
}

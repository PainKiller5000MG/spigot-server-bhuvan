package net.minecraft.world.item;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class TridentItem extends Item implements ProjectileItem {

    public static final int THROW_THRESHOLD_TIME = 10;
    public static final float BASE_DAMAGE = 8.0F;
    public static final float PROJECTILE_SHOOT_POWER = 2.5F;

    public TridentItem(Item.Properties properties) {
        super(properties);
    }

    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder().add(Attributes.ATTACK_DAMAGE, new AttributeModifier(TridentItem.BASE_ATTACK_DAMAGE_ID, 8.0D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).add(Attributes.ATTACK_SPEED, new AttributeModifier(TridentItem.BASE_ATTACK_SPEED_ID, (double) -2.9F, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build();
    }

    public static Tool createToolProperties() {
        return new Tool(List.of(), 1.0F, 2, false);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack itemStack) {
        return ItemUseAnimation.TRIDENT;
    }

    @Override
    public int getUseDuration(ItemStack itemStack, LivingEntity user) {
        return 72000;
    }

    @Override
    public boolean releaseUsing(ItemStack itemStack, Level level, LivingEntity entity, int remainingTime) {
        if (entity instanceof Player player) {
            int j = this.getUseDuration(itemStack, entity) - remainingTime;

            if (j < 10) {
                return false;
            } else {
                float f = EnchantmentHelper.getTridentSpinAttackStrength(itemStack, player);

                if (f > 0.0F && !player.isInWaterOrRain()) {
                    return false;
                } else if (itemStack.nextDamageWillBreak()) {
                    return false;
                } else {
                    Holder<SoundEvent> holder = (Holder) EnchantmentHelper.pickHighestLevel(itemStack, EnchantmentEffectComponents.TRIDENT_SOUND).orElse(SoundEvents.TRIDENT_THROW);

                    player.awardStat(Stats.ITEM_USED.get(this));
                    if (level instanceof ServerLevel) {
                        ServerLevel serverlevel = (ServerLevel) level;

                        itemStack.hurtWithoutBreaking(1, player);
                        if (f == 0.0F) {
                            ItemStack itemstack1 = itemStack.consumeAndReturn(1, player);
                            ThrownTrident throwntrident = (ThrownTrident) Projectile.spawnProjectileFromRotation(ThrownTrident::new, serverlevel, itemstack1, player, 0.0F, 2.5F, 1.0F);

                            if (player.hasInfiniteMaterials()) {
                                throwntrident.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                            }

                            level.playSound((Entity) null, (Entity) throwntrident, holder.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                            return true;
                        }
                    }

                    if (f > 0.0F) {
                        float f1 = player.getYRot();
                        float f2 = player.getXRot();
                        float f3 = -Mth.sin((double) (f1 * ((float) Math.PI / 180F))) * Mth.cos((double) (f2 * ((float) Math.PI / 180F)));
                        float f4 = -Mth.sin((double) (f2 * ((float) Math.PI / 180F)));
                        float f5 = Mth.cos((double) (f1 * ((float) Math.PI / 180F))) * Mth.cos((double) (f2 * ((float) Math.PI / 180F)));
                        float f6 = Mth.sqrt(f3 * f3 + f4 * f4 + f5 * f5);

                        f3 *= f / f6;
                        f4 *= f / f6;
                        f5 *= f / f6;
                        player.push((double) f3, (double) f4, (double) f5);
                        player.startAutoSpinAttack(20, 8.0F, itemStack);
                        if (player.onGround()) {
                            float f7 = 1.1999999F;

                            player.move(MoverType.SELF, new Vec3(0.0D, (double) 1.1999999F, 0.0D));
                        }

                        level.playSound((Entity) null, (Entity) player, holder.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                        return true;
                    } else {
                        return false;
                    }
                }
            }
        } else {
            return false;
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.nextDamageWillBreak()) {
            return InteractionResult.FAIL;
        } else if (EnchantmentHelper.getTridentSpinAttackStrength(itemstack, player) > 0.0F && !player.isInWaterOrRain()) {
            return InteractionResult.FAIL;
        } else {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public Projectile asProjectile(Level level, Position position, ItemStack itemStack, Direction direction) {
        ThrownTrident throwntrident = new ThrownTrident(level, position.x(), position.y(), position.z(), itemStack.copyWithCount(1));

        throwntrident.pickup = AbstractArrow.Pickup.ALLOWED;
        return throwntrident;
    }
}

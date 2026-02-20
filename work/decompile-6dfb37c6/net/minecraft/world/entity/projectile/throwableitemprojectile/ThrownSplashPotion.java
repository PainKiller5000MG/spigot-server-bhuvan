package net.minecraft.world.entity.projectile.throwableitemprojectile;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

public class ThrownSplashPotion extends AbstractThrownPotion {

    public ThrownSplashPotion(EntityType<? extends ThrownSplashPotion> type, Level level) {
        super(type, level);
    }

    public ThrownSplashPotion(Level level, LivingEntity owner, ItemStack itemStack) {
        super(EntityType.SPLASH_POTION, level, owner, itemStack);
    }

    public ThrownSplashPotion(Level level, double x, double y, double z, ItemStack itemStack) {
        super(EntityType.SPLASH_POTION, level, x, y, z, itemStack);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SPLASH_POTION;
    }

    @Override
    public void onHitAsPotion(ServerLevel level, ItemStack potionItem, HitResult hitResult) {
        PotionContents potioncontents = (PotionContents) potionItem.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        float f = (Float) potionItem.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0F);
        Iterable<MobEffectInstance> iterable = potioncontents.getAllEffects();
        AABB aabb = this.getBoundingBox().move(hitResult.getLocation().subtract(this.position()));
        AABB aabb1 = aabb.inflate(4.0D, 2.0D, 4.0D);
        List<LivingEntity> list = this.level().<LivingEntity>getEntitiesOfClass(LivingEntity.class, aabb1);
        float f1 = ProjectileUtil.computeMargin(this);

        if (!list.isEmpty()) {
            Entity entity = this.getEffectSource();

            for (LivingEntity livingentity : list) {
                if (livingentity.isAffectedByPotions()) {
                    double d0 = aabb.distanceToSqr(livingentity.getBoundingBox().inflate((double) f1));

                    if (d0 < 16.0D) {
                        double d1 = 1.0D - Math.sqrt(d0) / 4.0D;

                        for (MobEffectInstance mobeffectinstance : iterable) {
                            Holder<MobEffect> holder = mobeffectinstance.getEffect();

                            if (((MobEffect) holder.value()).isInstantenous()) {
                                ((MobEffect) holder.value()).applyInstantenousEffect(level, this, this.getOwner(), livingentity, mobeffectinstance.getAmplifier(), d1);
                            } else {
                                int i = mobeffectinstance.mapDuration((j) -> {
                                    return (int) (d1 * (double) j * (double) f + 0.5D);
                                });
                                MobEffectInstance mobeffectinstance1 = new MobEffectInstance(holder, i, mobeffectinstance.getAmplifier(), mobeffectinstance.isAmbient(), mobeffectinstance.isVisible());

                                if (!mobeffectinstance1.endsWithin(20)) {
                                    livingentity.addEffect(mobeffectinstance1, entity);
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}

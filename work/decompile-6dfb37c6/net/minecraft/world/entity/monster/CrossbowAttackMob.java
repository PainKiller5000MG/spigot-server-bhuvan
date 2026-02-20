package net.minecraft.world.entity.monster;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

public interface CrossbowAttackMob extends RangedAttackMob {

    void setChargingCrossbow(boolean isCharging);

    @Nullable
    LivingEntity getTarget();

    void onCrossbowAttackPerformed();

    default void performCrossbowAttack(LivingEntity body, float crossbowPower) {
        InteractionHand interactionhand = ProjectileUtil.getWeaponHoldingHand(body, Items.CROSSBOW);
        ItemStack itemstack = body.getItemInHand(interactionhand);
        Item item = itemstack.getItem();

        if (item instanceof CrossbowItem crossbowitem) {
            crossbowitem.performShooting(body.level(), body, interactionhand, itemstack, crossbowPower, (float) (14 - body.level().getDifficulty().getId() * 4), this.getTarget());
        }

        this.onCrossbowAttackPerformed();
    }
}

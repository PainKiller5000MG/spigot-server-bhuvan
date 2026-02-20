package net.minecraft.world.damagesource;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

public class CombatRules {

    public static final float MAX_ARMOR = 20.0F;
    public static final float ARMOR_PROTECTION_DIVIDER = 25.0F;
    public static final float BASE_ARMOR_TOUGHNESS = 2.0F;
    public static final float MIN_ARMOR_RATIO = 0.2F;
    private static final int NUM_ARMOR_ITEMS = 4;

    public CombatRules() {}

    public static float getDamageAfterAbsorb(LivingEntity victim, float damage, DamageSource source, float totalArmor, float armorToughness) {
        float f3;
        label12:
        {
            float f4 = 2.0F + armorToughness / 4.0F;
            float f5 = Mth.clamp(totalArmor - damage / f4, totalArmor * 0.2F, 20.0F);
            float f6 = f5 / 25.0F;
            ItemStack itemstack = source.getWeaponItem();

            if (itemstack != null) {
                Level level = victim.level();

                if (level instanceof ServerLevel) {
                    ServerLevel serverlevel = (ServerLevel) level;

                    f3 = Mth.clamp(EnchantmentHelper.modifyArmorEffectiveness(serverlevel, itemstack, victim, source, f6), 0.0F, 1.0F);
                    break label12;
                }
            }

            f3 = f6;
        }

        float f7 = 1.0F - f3;

        return damage * f7;
    }

    public static float getDamageAfterMagicAbsorb(float damage, float totalMagicArmor) {
        float f2 = Mth.clamp(totalMagicArmor, 0.0F, 20.0F);

        return damage * (1.0F - f2 / 25.0F);
    }
}

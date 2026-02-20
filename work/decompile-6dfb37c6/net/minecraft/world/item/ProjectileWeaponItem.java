package net.minecraft.world.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public abstract class ProjectileWeaponItem extends Item {

    public static final Predicate<ItemStack> ARROW_ONLY = (itemstack) -> {
        return itemstack.is(ItemTags.ARROWS);
    };
    public static final Predicate<ItemStack> ARROW_OR_FIREWORK = ProjectileWeaponItem.ARROW_ONLY.or((itemstack) -> {
        return itemstack.is(Items.FIREWORK_ROCKET);
    });

    public ProjectileWeaponItem(Item.Properties properties) {
        super(properties);
    }

    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return this.getAllSupportedProjectiles();
    }

    public abstract Predicate<ItemStack> getAllSupportedProjectiles();

    public static ItemStack getHeldProjectile(LivingEntity entity, Predicate<ItemStack> valid) {
        return valid.test(entity.getItemInHand(InteractionHand.OFF_HAND)) ? entity.getItemInHand(InteractionHand.OFF_HAND) : (valid.test(entity.getItemInHand(InteractionHand.MAIN_HAND)) ? entity.getItemInHand(InteractionHand.MAIN_HAND) : ItemStack.EMPTY);
    }

    public abstract int getDefaultProjectileRange();

    protected void shoot(ServerLevel level, LivingEntity shooter, InteractionHand hand, ItemStack weapon, List<ItemStack> projectiles, float power, float uncertainty, boolean isCrit, @Nullable LivingEntity targetOverride) {
        float f2 = EnchantmentHelper.processProjectileSpread(level, weapon, shooter, 0.0F);
        float f3 = projectiles.size() == 1 ? 0.0F : 2.0F * f2 / (float) (projectiles.size() - 1);
        float f4 = (float) ((projectiles.size() - 1) % 2) * f3 / 2.0F;
        float f5 = 1.0F;

        for (int i = 0; i < projectiles.size(); ++i) {
            ItemStack itemstack1 = (ItemStack) projectiles.get(i);

            if (!itemstack1.isEmpty()) {
                float f6 = f4 + f5 * (float) ((i + 1) / 2) * f3;

                f5 = -f5;
                Projectile.spawnProjectile(this.createProjectile(level, shooter, weapon, itemstack1, isCrit), level, itemstack1, (projectile) -> {
                    this.shootProjectile(shooter, projectile, i, power, uncertainty, f6, targetOverride);
                });
                weapon.hurtAndBreak(this.getDurabilityUse(itemstack1), shooter, hand.asEquipmentSlot());
                if (weapon.isEmpty()) {
                    break;
                }
            }
        }

    }

    protected int getDurabilityUse(ItemStack projectile) {
        return 1;
    }

    protected abstract void shootProjectile(LivingEntity shooter, Projectile projectileEntity, int index, float power, float uncertainty, float angle, @Nullable LivingEntity targetOverrride);

    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack projectile, boolean isCrit) {
        Item item = projectile.getItem();
        ArrowItem arrowitem;

        if (item instanceof ArrowItem arrowitem1) {
            arrowitem = arrowitem1;
        } else {
            arrowitem = (ArrowItem) Items.ARROW;
        }

        ArrowItem arrowitem2 = arrowitem;
        AbstractArrow abstractarrow = arrowitem2.createArrow(level, projectile, shooter, weapon);

        if (isCrit) {
            abstractarrow.setCritArrow(true);
        }

        return abstractarrow;
    }

    protected static List<ItemStack> draw(ItemStack weapon, ItemStack projectile, LivingEntity shooter) {
        if (projectile.isEmpty()) {
            return List.of();
        } else {
            Level level = shooter.level();
            int i;

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                i = EnchantmentHelper.processProjectileCount(serverlevel, weapon, shooter, 1);
            } else {
                i = 1;
            }

            int j = i;
            List<ItemStack> list = new ArrayList(j);
            ItemStack itemstack2 = projectile.copy();

            for (int k = 0; k < j; ++k) {
                ItemStack itemstack3 = useAmmo(weapon, k == 0 ? projectile : itemstack2, shooter, k > 0);

                if (!itemstack3.isEmpty()) {
                    list.add(itemstack3);
                }
            }

            return list;
        }
    }

    protected static ItemStack useAmmo(ItemStack weapon, ItemStack projectile, LivingEntity holder, boolean forceInfinite) {
        int i;
        label28:
        {
            if (!forceInfinite && !holder.hasInfiniteMaterials()) {
                Level level = holder.level();

                if (level instanceof ServerLevel) {
                    ServerLevel serverlevel = (ServerLevel) level;

                    i = EnchantmentHelper.processAmmoUse(serverlevel, weapon, projectile, 1);
                    break label28;
                }
            }

            i = 0;
        }

        int j = i;

        if (j > projectile.getCount()) {
            return ItemStack.EMPTY;
        } else if (j == 0) {
            ItemStack itemstack2 = projectile.copyWithCount(1);

            itemstack2.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
            return itemstack2;
        } else {
            ItemStack itemstack3 = projectile.split(j);

            if (projectile.isEmpty() && holder instanceof Player) {
                Player player = (Player) holder;

                player.getInventory().removeItem(projectile);
            }

            return itemstack3;
        }
    }
}

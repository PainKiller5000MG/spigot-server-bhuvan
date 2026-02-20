package net.minecraft.world.entity.projectile.throwableitemprojectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ThrownExperienceBottle extends ThrowableItemProjectile {

    public ThrownExperienceBottle(EntityType<? extends ThrownExperienceBottle> type, Level level) {
        super(type, level);
    }

    public ThrownExperienceBottle(Level level, LivingEntity mob, ItemStack itemStack) {
        super(EntityType.EXPERIENCE_BOTTLE, mob, level, itemStack);
    }

    public ThrownExperienceBottle(Level level, double x, double y, double z, ItemStack itemStack) {
        super(EntityType.EXPERIENCE_BOTTLE, x, y, z, level, itemStack);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.EXPERIENCE_BOTTLE;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.07D;
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            serverlevel.levelEvent(2002, this.blockPosition(), -13083194);
            int i = 3 + serverlevel.random.nextInt(5) + serverlevel.random.nextInt(5);

            if (hitResult instanceof BlockHitResult blockhitresult) {
                Vec3 vec3 = blockhitresult.getDirection().getUnitVec3();

                ExperienceOrb.awardWithDirection(serverlevel, hitResult.getLocation(), vec3, i);
            } else {
                ExperienceOrb.awardWithDirection(serverlevel, hitResult.getLocation(), this.getDeltaMovement().scale(-1.0D), i);
            }

            this.discard();
        }

    }
}

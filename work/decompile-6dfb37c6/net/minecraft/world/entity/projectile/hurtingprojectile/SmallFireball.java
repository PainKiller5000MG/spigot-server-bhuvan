package net.minecraft.world.entity.projectile.hurtingprojectile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SmallFireball extends Fireball {

    public SmallFireball(EntityType<? extends SmallFireball> type, Level level) {
        super(type, level);
    }

    public SmallFireball(Level level, LivingEntity mob, Vec3 direction) {
        super(EntityType.SMALL_FIREBALL, mob, direction, level);
    }

    public SmallFireball(Level level, double x, double y, double z, Vec3 direction) {
        super(EntityType.SMALL_FIREBALL, x, y, z, direction, level);
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            Entity entity = hitResult.getEntity();
            Entity entity1 = this.getOwner();
            int i = entity.getRemainingFireTicks();

            entity.igniteForSeconds(5.0F);
            DamageSource damagesource = this.damageSources().fireball(this, entity1);

            if (!entity.hurtServer(serverlevel, damagesource, 5.0F)) {
                entity.setRemainingFireTicks(i);
            } else {
                EnchantmentHelper.doPostAttackEffects(serverlevel, entity, damagesource);
            }

        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        super.onHitBlock(hitResult);
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            Entity entity = this.getOwner();

            if (!(entity instanceof Mob) || (Boolean) serverlevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
                BlockPos blockpos = hitResult.getBlockPos().relative(hitResult.getDirection());

                if (this.level().isEmptyBlock(blockpos)) {
                    this.level().setBlockAndUpdate(blockpos, BaseFireBlock.getState(this.level(), blockpos));
                }
            }

        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide()) {
            this.discard();
        }

    }
}

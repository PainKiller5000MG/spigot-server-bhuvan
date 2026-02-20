package net.minecraft.world.entity.projectile.hurtingprojectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class LargeFireball extends Fireball {

    private static final byte DEFAULT_EXPLOSION_POWER = 1;
    public int explosionPower = 1;

    public LargeFireball(EntityType<? extends LargeFireball> type, Level level) {
        super(type, level);
    }

    public LargeFireball(Level level, LivingEntity mob, Vec3 direction, int explosionPower) {
        super(EntityType.FIREBALL, mob, direction, level);
        this.explosionPower = explosionPower;
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            boolean flag = (Boolean) serverlevel.getGameRules().get(GameRules.MOB_GRIEFING);

            this.level().explode(this, this.getX(), this.getY(), this.getZ(), (float) this.explosionPower, flag, Level.ExplosionInteraction.MOB);
            this.discard();
        }

    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            Entity entity = hitResult.getEntity();
            Entity entity1 = this.getOwner();
            DamageSource damagesource = this.damageSources().fireball(this, entity1);

            entity.hurtServer(serverlevel, damagesource, 6.0F);
            EnchantmentHelper.doPostAttackEffects(serverlevel, entity, damagesource);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putByte("ExplosionPower", (byte) this.explosionPower);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.explosionPower = input.getByteOr("ExplosionPower", (byte) 1);
    }
}

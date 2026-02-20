package net.minecraft.world.entity.projectile.hurtingprojectile.windcharge;

import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractWindCharge extends AbstractHurtingProjectile implements ItemSupplier {

    public static final ExplosionDamageCalculator EXPLOSION_DAMAGE_CALCULATOR = new SimpleExplosionDamageCalculator(true, false, Optional.empty(), BuiltInRegistries.BLOCK.get(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity()));
    public static final double JUMP_SCALE = 0.25D;

    public AbstractWindCharge(EntityType<? extends AbstractWindCharge> type, Level level) {
        super(type, level);
        this.accelerationPower = 0.0D;
    }

    public AbstractWindCharge(EntityType<? extends AbstractWindCharge> type, Level level, Entity owner, double x, double y, double z) {
        super(type, x, y, z, level);
        this.setOwner(owner);
        this.accelerationPower = 0.0D;
    }

    AbstractWindCharge(EntityType<? extends AbstractWindCharge> type, double x, double y, double z, Vec3 direction, Level level) {
        super(type, x, y, z, direction, level);
        this.accelerationPower = 0.0D;
    }

    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        float f = this.getType().getDimensions().width() / 2.0F;
        float f1 = this.getType().getDimensions().height();
        float f2 = 0.15F;

        return new AABB(position.x - (double) f, position.y - (double) 0.15F, position.z - (double) f, position.x + (double) f, position.y - (double) 0.15F + (double) f1, position.z + (double) f);
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return entity instanceof AbstractWindCharge ? false : super.canCollideWith(entity);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity instanceof AbstractWindCharge ? false : (entity.getType() == EntityType.END_CRYSTAL ? false : super.canHitEntity(entity));
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            Entity entity = this.getOwner();
            LivingEntity livingentity;

            if (entity instanceof LivingEntity livingentity1) {
                livingentity = livingentity1;
            } else {
                livingentity = null;
            }

            LivingEntity livingentity2 = livingentity;
            Entity entity1 = hitResult.getEntity();

            if (livingentity2 != null) {
                livingentity2.setLastHurtMob(entity1);
            }

            DamageSource damagesource = this.damageSources().windCharge(this, livingentity2);

            if (entity1.hurtServer(serverlevel, damagesource, 1.0F) && entity1 instanceof LivingEntity livingentity3) {
                EnchantmentHelper.doPostAttackEffects(serverlevel, livingentity3, damagesource);
            }

            this.explode(this.position());
        }
    }

    @Override
    public void push(double xa, double ya, double za) {}

    public abstract void explode(Vec3 position);

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        super.onHitBlock(hitResult);
        if (!this.level().isClientSide()) {
            Vec3i vec3i = hitResult.getDirection().getUnitVec3i();
            Vec3 vec3 = Vec3.atLowerCornerOf(vec3i).multiply(0.25D, 0.25D, 0.25D);
            Vec3 vec31 = hitResult.getLocation().add(vec3);

            this.explode(vec31);
            this.discard();
        }

    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide()) {
            this.discard();
        }

    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    public ItemStack getItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected float getInertia() {
        return 1.0F;
    }

    @Override
    protected float getLiquidInertia() {
        return this.getInertia();
    }

    @Override
    protected @Nullable ParticleOptions getTrailParticle() {
        return null;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide() && this.getBlockY() > this.level().getMaxY() + 30) {
            this.explode(this.position());
            this.discard();
        } else {
            super.tick();
        }

    }
}

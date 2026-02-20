package net.minecraft.world.entity.projectile.hurtingprojectile;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractHurtingProjectile extends Projectile {

    public static final double INITAL_ACCELERATION_POWER = 0.1D;
    public static final double DEFLECTION_SCALE = 0.5D;
    public double accelerationPower;

    protected AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> type, Level level) {
        super(type, level);
        this.accelerationPower = 0.1D;
    }

    protected AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> type, double x, double y, double z, Level level) {
        this(type, level);
        this.setPos(x, y, z);
    }

    public AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> type, double x, double y, double z, Vec3 direction, Level level) {
        this(type, level);
        this.snapTo(x, y, z, this.getYRot(), this.getXRot());
        this.reapplyPosition();
        this.assignDirectionalMovement(direction, this.accelerationPower);
    }

    public AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> type, LivingEntity mob, Vec3 direction, Level level) {
        this(type, mob.getX(), mob.getY(), mob.getZ(), direction, level);
        this.setOwner(mob);
        this.setRot(mob.getYRot(), mob.getXRot());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {}

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = this.getBoundingBox().getSize() * 4.0D;

        if (Double.isNaN(d1)) {
            d1 = 4.0D;
        }

        d1 *= 64.0D;
        return distance < d1 * d1;
    }

    protected ClipContext.Block getClipType() {
        return ClipContext.Block.COLLIDER;
    }

    @Override
    public void tick() {
        Entity entity = this.getOwner();

        this.applyInertia();
        if (this.level().isClientSide() || (entity == null || !entity.isRemoved()) && this.level().hasChunkAt(this.blockPosition())) {
            HitResult hitresult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity, this.getClipType());
            Vec3 vec3;

            if (hitresult.getType() != HitResult.Type.MISS) {
                vec3 = hitresult.getLocation();
            } else {
                vec3 = this.position().add(this.getDeltaMovement());
            }

            ProjectileUtil.rotateTowardsMovement(this, 0.2F);
            this.setPos(vec3);
            this.applyEffectsFromBlocks();
            super.tick();
            if (this.shouldBurn()) {
                this.igniteForSeconds(1.0F);
            }

            if (hitresult.getType() != HitResult.Type.MISS && this.isAlive()) {
                this.hitTargetOrDeflectSelf(hitresult);
            }

            this.createParticleTrail();
        } else {
            this.discard();
        }
    }

    private void applyInertia() {
        Vec3 vec3 = this.getDeltaMovement();
        Vec3 vec31 = this.position();
        float f;

        if (this.isInWater()) {
            for (int i = 0; i < 4; ++i) {
                float f1 = 0.25F;

                this.level().addParticle(ParticleTypes.BUBBLE, vec31.x - vec3.x * 0.25D, vec31.y - vec3.y * 0.25D, vec31.z - vec3.z * 0.25D, vec3.x, vec3.y, vec3.z);
            }

            f = this.getLiquidInertia();
        } else {
            f = this.getInertia();
        }

        this.setDeltaMovement(vec3.add(vec3.normalize().scale(this.accelerationPower)).scale((double) f));
    }

    private void createParticleTrail() {
        ParticleOptions particleoptions = this.getTrailParticle();
        Vec3 vec3 = this.position();

        if (particleoptions != null) {
            this.level().addParticle(particleoptions, vec3.x, vec3.y + 0.5D, vec3.z, 0.0D, 0.0D, 0.0D);
        }

    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !entity.noPhysics;
    }

    protected boolean shouldBurn() {
        return true;
    }

    protected @Nullable ParticleOptions getTrailParticle() {
        return ParticleTypes.SMOKE;
    }

    protected float getInertia() {
        return 0.95F;
    }

    protected float getLiquidInertia() {
        return 0.8F;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putDouble("acceleration_power", this.accelerationPower);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.accelerationPower = input.getDoubleOr("acceleration_power", 0.1D);
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    public void assignDirectionalMovement(Vec3 direction, double speed) {
        this.setDeltaMovement(direction.normalize().scale(speed));
        this.needsSync = true;
    }

    @Override
    protected void onDeflection(boolean byAttack) {
        super.onDeflection(byAttack);
        if (byAttack) {
            this.accelerationPower = 0.1D;
        } else {
            this.accelerationPower *= 0.5D;
        }

    }
}

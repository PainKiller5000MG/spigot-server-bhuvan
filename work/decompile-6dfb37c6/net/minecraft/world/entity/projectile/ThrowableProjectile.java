package net.minecraft.world.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public abstract class ThrowableProjectile extends Projectile {

    private static final float MIN_CAMERA_DISTANCE_SQUARED = 12.25F;

    protected ThrowableProjectile(EntityType<? extends ThrowableProjectile> type, Level level) {
        super(type, level);
    }

    protected ThrowableProjectile(EntityType<? extends ThrowableProjectile> type, double x, double y, double z, Level level) {
        this(type, level);
        this.setPos(x, y, z);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        if (this.tickCount < 2 && distance < 12.25D) {
            return false;
        } else {
            double d1 = this.getBoundingBox().getSize() * 4.0D;

            if (Double.isNaN(d1)) {
                d1 = 4.0D;
            }

            d1 *= 64.0D;
            return distance < d1 * d1;
        }
    }

    @Override
    public boolean canUsePortal(boolean ignorePassenger) {
        return true;
    }

    @Override
    public void tick() {
        this.handleFirstTickBubbleColumn();
        this.applyGravity();
        this.applyInertia();
        HitResult hitresult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        Vec3 vec3;

        if (hitresult.getType() != HitResult.Type.MISS) {
            vec3 = hitresult.getLocation();
        } else {
            vec3 = this.position().add(this.getDeltaMovement());
        }

        this.setPos(vec3);
        this.updateRotation();
        this.applyEffectsFromBlocks();
        super.tick();
        if (hitresult.getType() != HitResult.Type.MISS && this.isAlive()) {
            this.hitTargetOrDeflectSelf(hitresult);
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

            f = 0.8F;
        } else {
            f = 0.99F;
        }

        this.setDeltaMovement(vec3.scale((double) f));
    }

    private void handleFirstTickBubbleColumn() {
        if (this.firstTick) {
            for (BlockPos blockpos : BlockPos.betweenClosed(this.getBoundingBox())) {
                BlockState blockstate = this.level().getBlockState(blockpos);

                if (blockstate.is(Blocks.BUBBLE_COLUMN)) {
                    blockstate.entityInside(this.level(), blockpos, this, InsideBlockEffectApplier.NOOP, true);
                }
            }
        }

    }

    @Override
    protected double getDefaultGravity() {
        return 0.03D;
    }
}

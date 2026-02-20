package net.minecraft.world.entity.projectile;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class LlamaSpit extends Projectile {

    public LlamaSpit(EntityType<? extends LlamaSpit> type, Level level) {
        super(type, level);
    }

    public LlamaSpit(Level level, Llama owner) {
        this(EntityType.LLAMA_SPIT, level);
        this.setOwner(owner);
        this.setPos(owner.getX() - (double) (owner.getBbWidth() + 1.0F) * 0.5D * (double) Mth.sin((double) (owner.yBodyRot * ((float) Math.PI / 180F))), owner.getEyeY() - (double) 0.1F, owner.getZ() + (double) (owner.getBbWidth() + 1.0F) * 0.5D * (double) Mth.cos((double) (owner.yBodyRot * ((float) Math.PI / 180F))));
    }

    @Override
    protected double getDefaultGravity() {
        return 0.06D;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 vec3 = this.getDeltaMovement();
        HitResult hitresult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);

        this.hitTargetOrDeflectSelf(hitresult);
        double d0 = this.getX() + vec3.x;
        double d1 = this.getY() + vec3.y;
        double d2 = this.getZ() + vec3.z;

        this.updateRotation();
        float f = 0.99F;

        if (this.level().getBlockStates(this.getBoundingBox()).noneMatch(BlockBehaviour.BlockStateBase::isAir)) {
            this.discard();
        } else if (this.isInWater()) {
            this.discard();
        } else {
            this.setDeltaMovement(vec3.scale((double) 0.99F));
            this.applyGravity();
            this.setPos(d0, d1, d2);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        Entity entity = this.getOwner();

        if (entity instanceof LivingEntity livingentity) {
            entity = hitResult.getEntity();
            DamageSource damagesource = this.damageSources().spit(this, livingentity);
            Level level = this.level();

            if (level instanceof ServerLevel serverlevel) {
                if (entity.hurtServer(serverlevel, damagesource, 1.0F)) {
                    EnchantmentHelper.doPostAttackEffects(serverlevel, entity, damagesource);
                }
            }
        }

    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        super.onHitBlock(hitResult);
        if (!this.level().isClientSide()) {
            this.discard();
        }

    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {}

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        Vec3 vec3 = packet.getMovement();

        for (int i = 0; i < 7; ++i) {
            double d0 = 0.4D + 0.1D * (double) i;

            this.level().addParticle(ParticleTypes.SPIT, this.getX(), this.getY(), this.getZ(), vec3.x * d0, vec3.y, vec3.z * d0);
        }

        this.setDeltaMovement(vec3);
    }
}

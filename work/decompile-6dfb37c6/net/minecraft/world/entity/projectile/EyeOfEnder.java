package net.minecraft.world.entity.projectile;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class EyeOfEnder extends Entity implements ItemSupplier {

    private static final float MIN_CAMERA_DISTANCE_SQUARED = 12.25F;
    private static final float TOO_FAR_SIGNAL_HEIGHT = 8.0F;
    private static final float TOO_FAR_DISTANCE = 12.0F;
    private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK = SynchedEntityData.<ItemStack>defineId(EyeOfEnder.class, EntityDataSerializers.ITEM_STACK);
    public @Nullable Vec3 target;
    public int life;
    public boolean surviveAfterDeath;

    public EyeOfEnder(EntityType<? extends EyeOfEnder> type, Level level) {
        super(type, level);
    }

    public EyeOfEnder(Level level, double x, double y, double z) {
        this(EntityType.EYE_OF_ENDER, level);
        this.setPos(x, y, z);
    }

    public void setItem(ItemStack source) {
        if (source.isEmpty()) {
            this.getEntityData().set(EyeOfEnder.DATA_ITEM_STACK, this.getDefaultItem());
        } else {
            this.getEntityData().set(EyeOfEnder.DATA_ITEM_STACK, source.copyWithCount(1));
        }

    }

    @Override
    public ItemStack getItem() {
        return (ItemStack) this.getEntityData().get(EyeOfEnder.DATA_ITEM_STACK);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(EyeOfEnder.DATA_ITEM_STACK, this.getDefaultItem());
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

    public void signalTo(Vec3 target) {
        Vec3 vec31 = target.subtract(this.position());
        double d0 = vec31.horizontalDistance();

        if (d0 > 12.0D) {
            this.target = this.position().add(vec31.x / d0 * 12.0D, 8.0D, vec31.z / d0 * 12.0D);
        } else {
            this.target = target;
        }

        this.life = 0;
        this.surviveAfterDeath = this.random.nextInt(5) > 0;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 vec3 = this.position().add(this.getDeltaMovement());

        if (!this.level().isClientSide() && this.target != null) {
            this.setDeltaMovement(updateDeltaMovement(this.getDeltaMovement(), vec3, this.target));
        }

        if (this.level().isClientSide()) {
            Vec3 vec31 = vec3.subtract(this.getDeltaMovement().scale(0.25D));

            this.spawnParticles(vec31, this.getDeltaMovement());
        }

        this.setPos(vec3);
        if (!this.level().isClientSide()) {
            ++this.life;
            if (this.life > 80 && !this.level().isClientSide()) {
                this.playSound(SoundEvents.ENDER_EYE_DEATH, 1.0F, 1.0F);
                this.discard();
                if (this.surviveAfterDeath) {
                    this.level().addFreshEntity(new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), this.getItem()));
                } else {
                    this.level().levelEvent(2003, this.blockPosition(), 0);
                }
            }
        }

    }

    private void spawnParticles(Vec3 origin, Vec3 movement) {
        if (this.isInWater()) {
            for (int i = 0; i < 4; ++i) {
                this.level().addParticle(ParticleTypes.BUBBLE, origin.x, origin.y, origin.z, movement.x, movement.y, movement.z);
            }
        } else {
            this.level().addParticle(ParticleTypes.PORTAL, origin.x + this.random.nextDouble() * 0.6D - 0.3D, origin.y - 0.5D, origin.z + this.random.nextDouble() * 0.6D - 0.3D, movement.x, movement.y, movement.z);
        }

    }

    private static Vec3 updateDeltaMovement(Vec3 oldMovement, Vec3 position, Vec3 target) {
        Vec3 vec33 = new Vec3(target.x - position.x, 0.0D, target.z - position.z);
        double d0 = vec33.length();
        double d1 = Mth.lerp(0.0025D, oldMovement.horizontalDistance(), d0);
        double d2 = oldMovement.y;

        if (d0 < 1.0D) {
            d1 *= 0.8D;
            d2 *= 0.8D;
        }

        double d3 = position.y - oldMovement.y < target.y ? 1.0D : -1.0D;

        return vec33.scale(d1 / d0).add(0.0D, d2 + (d3 - d2) * 0.015D, 0.0D);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("Item", ItemStack.CODEC, this.getItem());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setItem((ItemStack) input.read("Item", ItemStack.CODEC).orElse(this.getDefaultItem()));
    }

    private ItemStack getDefaultItem() {
        return new ItemStack(Items.ENDER_EYE);
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }
}

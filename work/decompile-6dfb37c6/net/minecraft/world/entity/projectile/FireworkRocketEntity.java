package net.minecraft.world.entity.projectile;

import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class FireworkRocketEntity extends Projectile implements ItemSupplier {

    public static final EntityDataAccessor<ItemStack> DATA_ID_FIREWORKS_ITEM = SynchedEntityData.<ItemStack>defineId(FireworkRocketEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<OptionalInt> DATA_ATTACHED_TO_TARGET = SynchedEntityData.<OptionalInt>defineId(FireworkRocketEntity.class, EntityDataSerializers.OPTIONAL_UNSIGNED_INT);
    public static final EntityDataAccessor<Boolean> DATA_SHOT_AT_ANGLE = SynchedEntityData.<Boolean>defineId(FireworkRocketEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int DEFAULT_LIFE = 0;
    private static final int DEFAULT_LIFE_TIME = 0;
    private static final boolean DEFAULT_SHOT_AT_ANGLE = false;
    public int life;
    public int lifetime;
    public @Nullable LivingEntity attachedToEntity;

    public FireworkRocketEntity(EntityType<? extends FireworkRocketEntity> type, Level level) {
        super(type, level);
        this.life = 0;
        this.lifetime = 0;
    }

    public FireworkRocketEntity(Level level, double x, double y, double z, ItemStack sourceItemStack) {
        super(EntityType.FIREWORK_ROCKET, level);
        this.life = 0;
        this.lifetime = 0;
        this.life = 0;
        this.setPos(x, y, z);
        this.entityData.set(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM, sourceItemStack.copy());
        int i = 1;
        Fireworks fireworks = (Fireworks) sourceItemStack.get(DataComponents.FIREWORKS);

        if (fireworks != null) {
            i += fireworks.flightDuration();
        }

        this.setDeltaMovement(this.random.triangle(0.0D, 0.002297D), 0.05D, this.random.triangle(0.0D, 0.002297D));
        this.lifetime = 10 * i + this.random.nextInt(6) + this.random.nextInt(7);
    }

    public FireworkRocketEntity(Level level, @Nullable Entity owner, double x, double y, double z, ItemStack sourceItemStack) {
        this(level, x, y, z, sourceItemStack);
        this.setOwner(owner);
    }

    public FireworkRocketEntity(Level level, ItemStack sourceItemStack, LivingEntity stuckTo) {
        this(level, stuckTo, stuckTo.getX(), stuckTo.getY(), stuckTo.getZ(), sourceItemStack);
        this.entityData.set(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET, OptionalInt.of(stuckTo.getId()));
        this.attachedToEntity = stuckTo;
    }

    public FireworkRocketEntity(Level level, ItemStack sourceItemStack, double x, double y, double z, boolean shotAtAngle) {
        this(level, x, y, z, sourceItemStack);
        this.entityData.set(FireworkRocketEntity.DATA_SHOT_AT_ANGLE, shotAtAngle);
    }

    public FireworkRocketEntity(Level level, ItemStack sourceItemStack, Entity owner, double x, double y, double z, boolean shotAtAngle) {
        this(level, sourceItemStack, x, y, z, shotAtAngle);
        this.setOwner(owner);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM, getDefaultItem());
        entityData.define(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET, OptionalInt.empty());
        entityData.define(FireworkRocketEntity.DATA_SHOT_AT_ANGLE, false);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D && !this.isAttachedToEntity();
    }

    @Override
    public boolean shouldRender(double camX, double camY, double camZ) {
        return super.shouldRender(camX, camY, camZ) && !this.isAttachedToEntity();
    }

    @Override
    public void tick() {
        super.tick();
        HitResult hitresult;

        if (this.isAttachedToEntity()) {
            if (this.attachedToEntity == null) {
                ((OptionalInt) this.entityData.get(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET)).ifPresent((i) -> {
                    Entity entity = this.level().getEntity(i);

                    if (entity instanceof LivingEntity) {
                        this.attachedToEntity = (LivingEntity) entity;
                    }

                });
            }

            if (this.attachedToEntity != null) {
                Vec3 vec3;

                if (this.attachedToEntity.isFallFlying()) {
                    Vec3 vec31 = this.attachedToEntity.getLookAngle();
                    double d0 = 1.5D;
                    double d1 = 0.1D;
                    Vec3 vec32 = this.attachedToEntity.getDeltaMovement();

                    this.attachedToEntity.setDeltaMovement(vec32.add(vec31.x * 0.1D + (vec31.x * 1.5D - vec32.x) * 0.5D, vec31.y * 0.1D + (vec31.y * 1.5D - vec32.y) * 0.5D, vec31.z * 0.1D + (vec31.z * 1.5D - vec32.z) * 0.5D));
                    vec3 = this.attachedToEntity.getHandHoldingItemAngle(Items.FIREWORK_ROCKET);
                } else {
                    vec3 = Vec3.ZERO;
                }

                this.setPos(this.attachedToEntity.getX() + vec3.x, this.attachedToEntity.getY() + vec3.y, this.attachedToEntity.getZ() + vec3.z);
                this.setDeltaMovement(this.attachedToEntity.getDeltaMovement());
            }

            hitresult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        } else {
            if (!this.isShotAtAngle()) {
                double d2 = this.horizontalCollision ? 1.0D : 1.15D;

                this.setDeltaMovement(this.getDeltaMovement().multiply(d2, 1.0D, d2).add(0.0D, 0.04D, 0.0D));
            }

            Vec3 vec33 = this.getDeltaMovement();

            hitresult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            this.move(MoverType.SELF, vec33);
            this.applyEffectsFromBlocks();
            this.setDeltaMovement(vec33);
        }

        if (!this.noPhysics && this.isAlive() && hitresult.getType() != HitResult.Type.MISS) {
            this.hitTargetOrDeflectSelf(hitresult);
            this.needsSync = true;
        }

        this.updateRotation();
        if (this.life == 0 && !this.isSilent()) {
            this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 3.0F, 1.0F);
        }

        ++this.life;
        if (this.level().isClientSide() && this.life % 2 < 2) {
            this.level().addParticle(ParticleTypes.FIREWORK, this.getX(), this.getY(), this.getZ(), this.random.nextGaussian() * 0.05D, -this.getDeltaMovement().y * 0.5D, this.random.nextGaussian() * 0.05D);
        }

        if (this.life > this.lifetime) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                this.explode(serverlevel);
            }
        }

    }

    private void explode(ServerLevel level) {
        level.broadcastEntityEvent(this, (byte) 17);
        this.gameEvent(GameEvent.EXPLODE, this.getOwner());
        this.dealExplosionDamage(level);
        this.discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            this.explode(serverlevel);
        }

    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        BlockPos blockpos = new BlockPos(hitResult.getBlockPos());

        this.level().getBlockState(blockpos).entityInside(this.level(), blockpos, this, InsideBlockEffectApplier.NOOP, true);
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            if (this.hasExplosion()) {
                this.explode(serverlevel);
            }
        }

        super.onHitBlock(hitResult);
    }

    private boolean hasExplosion() {
        return !this.getExplosions().isEmpty();
    }

    private void dealExplosionDamage(ServerLevel level) {
        float f = 0.0F;
        List<FireworkExplosion> list = this.getExplosions();

        if (!list.isEmpty()) {
            f = 5.0F + (float) (list.size() * 2);
        }

        if (f > 0.0F) {
            if (this.attachedToEntity != null) {
                this.attachedToEntity.hurtServer(level, this.damageSources().fireworks(this, this.getOwner()), 5.0F + (float) (list.size() * 2));
            }

            double d0 = 5.0D;
            Vec3 vec3 = this.position();

            for (LivingEntity livingentity : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(5.0D))) {
                if (livingentity != this.attachedToEntity && this.distanceToSqr((Entity) livingentity) <= 25.0D) {
                    boolean flag = false;

                    for (int i = 0; i < 2; ++i) {
                        Vec3 vec31 = new Vec3(livingentity.getX(), livingentity.getY(0.5D * (double) i), livingentity.getZ());
                        HitResult hitresult = this.level().clip(new ClipContext(vec3, vec31, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));

                        if (hitresult.getType() == HitResult.Type.MISS) {
                            flag = true;
                            break;
                        }
                    }

                    if (flag) {
                        float f1 = f * (float) Math.sqrt((5.0D - (double) this.distanceTo(livingentity)) / 5.0D);

                        livingentity.hurtServer(level, this.damageSources().fireworks(this, this.getOwner()), f1);
                    }
                }
            }
        }

    }

    private boolean isAttachedToEntity() {
        return ((OptionalInt) this.entityData.get(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET)).isPresent();
    }

    public boolean isShotAtAngle() {
        return (Boolean) this.entityData.get(FireworkRocketEntity.DATA_SHOT_AT_ANGLE);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 17 && this.level().isClientSide()) {
            Vec3 vec3 = this.getDeltaMovement();

            this.level().createFireworks(this.getX(), this.getY(), this.getZ(), vec3.x, vec3.y, vec3.z, this.getExplosions());
        }

        super.handleEntityEvent(id);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Life", this.life);
        output.putInt("LifeTime", this.lifetime);
        output.store("FireworksItem", ItemStack.CODEC, this.getItem());
        output.putBoolean("ShotAtAngle", (Boolean) this.entityData.get(FireworkRocketEntity.DATA_SHOT_AT_ANGLE));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.life = input.getIntOr("Life", 0);
        this.lifetime = input.getIntOr("LifeTime", 0);
        this.entityData.set(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM, (ItemStack) input.read("FireworksItem", ItemStack.CODEC).orElse(getDefaultItem()));
        this.entityData.set(FireworkRocketEntity.DATA_SHOT_AT_ANGLE, input.getBooleanOr("ShotAtAngle", false));
    }

    private List<FireworkExplosion> getExplosions() {
        ItemStack itemstack = (ItemStack) this.entityData.get(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM);
        Fireworks fireworks = (Fireworks) itemstack.get(DataComponents.FIREWORKS);

        return fireworks != null ? fireworks.explosions() : List.of();
    }

    @Override
    public ItemStack getItem() {
        return (ItemStack) this.entityData.get(FireworkRocketEntity.DATA_ID_FIREWORKS_ITEM);
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    private static ItemStack getDefaultItem() {
        return new ItemStack(Items.FIREWORK_ROCKET);
    }

    @Override
    public DoubleDoubleImmutablePair calculateHorizontalHurtKnockbackDirection(LivingEntity hurtEntity, DamageSource damageSource) {
        double d0 = hurtEntity.position().x - this.position().x;
        double d1 = hurtEntity.position().z - this.position().z;

        return DoubleDoubleImmutablePair.of(d0, d1);
    }
}

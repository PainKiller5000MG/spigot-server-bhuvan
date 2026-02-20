package net.minecraft.world.entity.projectile;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ShulkerBullet extends Projectile {

    private static final double SPEED = 0.15D;
    private @Nullable EntityReference<Entity> finalTarget;
    private @Nullable Direction currentMoveDirection;
    private int flightSteps;
    private double targetDeltaX;
    private double targetDeltaY;
    private double targetDeltaZ;

    public ShulkerBullet(EntityType<? extends ShulkerBullet> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public ShulkerBullet(Level level, LivingEntity owner, Entity target, Direction.Axis invalidStartAxis) {
        this(EntityType.SHULKER_BULLET, level);
        this.setOwner(owner);
        Vec3 vec3 = owner.getBoundingBox().getCenter();

        this.snapTo(vec3.x, vec3.y, vec3.z, this.getYRot(), this.getXRot());
        this.finalTarget = EntityReference.of(target);
        this.currentMoveDirection = Direction.UP;
        this.selectNextMoveDirection(invalidStartAxis, target);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (this.finalTarget != null) {
            output.store("Target", UUIDUtil.CODEC, this.finalTarget.getUUID());
        }

        output.storeNullable("Dir", Direction.LEGACY_ID_CODEC, this.currentMoveDirection);
        output.putInt("Steps", this.flightSteps);
        output.putDouble("TXD", this.targetDeltaX);
        output.putDouble("TYD", this.targetDeltaY);
        output.putDouble("TZD", this.targetDeltaZ);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.flightSteps = input.getIntOr("Steps", 0);
        this.targetDeltaX = input.getDoubleOr("TXD", 0.0D);
        this.targetDeltaY = input.getDoubleOr("TYD", 0.0D);
        this.targetDeltaZ = input.getDoubleOr("TZD", 0.0D);
        this.currentMoveDirection = (Direction) input.read("Dir", Direction.LEGACY_ID_CODEC).orElse((Object) null);
        this.finalTarget = EntityReference.<Entity>read(input, "Target");
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {}

    private @Nullable Direction getMoveDirection() {
        return this.currentMoveDirection;
    }

    private void setMoveDirection(@Nullable Direction direction) {
        this.currentMoveDirection = direction;
    }

    private void selectNextMoveDirection(Direction.@Nullable Axis avoidAxis, @Nullable Entity target) {
        double d0 = 0.5D;
        BlockPos blockpos;

        if (target == null) {
            blockpos = this.blockPosition().below();
        } else {
            d0 = (double) target.getBbHeight() * 0.5D;
            blockpos = BlockPos.containing(target.getX(), target.getY() + d0, target.getZ());
        }

        double d1 = (double) blockpos.getX() + 0.5D;
        double d2 = (double) blockpos.getY() + d0;
        double d3 = (double) blockpos.getZ() + 0.5D;
        Direction direction = null;

        if (!blockpos.closerToCenterThan(this.position(), 2.0D)) {
            BlockPos blockpos1 = this.blockPosition();
            List<Direction> list = Lists.newArrayList();

            if (avoidAxis != Direction.Axis.X) {
                if (blockpos1.getX() < blockpos.getX() && this.level().isEmptyBlock(blockpos1.east())) {
                    list.add(Direction.EAST);
                } else if (blockpos1.getX() > blockpos.getX() && this.level().isEmptyBlock(blockpos1.west())) {
                    list.add(Direction.WEST);
                }
            }

            if (avoidAxis != Direction.Axis.Y) {
                if (blockpos1.getY() < blockpos.getY() && this.level().isEmptyBlock(blockpos1.above())) {
                    list.add(Direction.UP);
                } else if (blockpos1.getY() > blockpos.getY() && this.level().isEmptyBlock(blockpos1.below())) {
                    list.add(Direction.DOWN);
                }
            }

            if (avoidAxis != Direction.Axis.Z) {
                if (blockpos1.getZ() < blockpos.getZ() && this.level().isEmptyBlock(blockpos1.south())) {
                    list.add(Direction.SOUTH);
                } else if (blockpos1.getZ() > blockpos.getZ() && this.level().isEmptyBlock(blockpos1.north())) {
                    list.add(Direction.NORTH);
                }
            }

            direction = Direction.getRandom(this.random);
            if (list.isEmpty()) {
                for (int i = 5; !this.level().isEmptyBlock(blockpos1.relative(direction)) && i > 0; --i) {
                    direction = Direction.getRandom(this.random);
                }
            } else {
                direction = (Direction) list.get(this.random.nextInt(list.size()));
            }

            d1 = this.getX() + (double) direction.getStepX();
            d2 = this.getY() + (double) direction.getStepY();
            d3 = this.getZ() + (double) direction.getStepZ();
        }

        this.setMoveDirection(direction);
        double d4 = d1 - this.getX();
        double d5 = d2 - this.getY();
        double d6 = d3 - this.getZ();
        double d7 = Math.sqrt(d4 * d4 + d5 * d5 + d6 * d6);

        if (d7 == 0.0D) {
            this.targetDeltaX = 0.0D;
            this.targetDeltaY = 0.0D;
            this.targetDeltaZ = 0.0D;
        } else {
            this.targetDeltaX = d4 / d7 * 0.15D;
            this.targetDeltaY = d5 / d7 * 0.15D;
            this.targetDeltaZ = d6 / d7 * 0.15D;
        }

        this.needsSync = true;
        this.flightSteps = 10 + this.random.nextInt(5) * 10;
    }

    @Override
    public void checkDespawn() {
        if (this.level().getDifficulty() == Difficulty.PEACEFUL) {
            this.discard();
        }

    }

    @Override
    protected double getDefaultGravity() {
        return 0.04D;
    }

    @Override
    public void tick() {
        super.tick();
        Entity entity = !this.level().isClientSide() ? EntityReference.getEntity(this.finalTarget, this.level()) : null;
        HitResult hitresult = null;

        if (!this.level().isClientSide()) {
            if (entity == null) {
                this.finalTarget = null;
            }

            if (entity == null || !entity.isAlive() || entity instanceof Player && entity.isSpectator()) {
                this.applyGravity();
            } else {
                this.targetDeltaX = Mth.clamp(this.targetDeltaX * 1.025D, -1.0D, 1.0D);
                this.targetDeltaY = Mth.clamp(this.targetDeltaY * 1.025D, -1.0D, 1.0D);
                this.targetDeltaZ = Mth.clamp(this.targetDeltaZ * 1.025D, -1.0D, 1.0D);
                Vec3 vec3 = this.getDeltaMovement();

                this.setDeltaMovement(vec3.add((this.targetDeltaX - vec3.x) * 0.2D, (this.targetDeltaY - vec3.y) * 0.2D, (this.targetDeltaZ - vec3.z) * 0.2D));
            }

            hitresult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        }

        Vec3 vec31 = this.getDeltaMovement();

        this.setPos(this.position().add(vec31));
        this.applyEffectsFromBlocks();
        if (this.portalProcess != null && this.portalProcess.isInsidePortalThisTick()) {
            this.handlePortal();
        }

        if (hitresult != null && this.isAlive() && hitresult.getType() != HitResult.Type.MISS) {
            this.hitTargetOrDeflectSelf(hitresult);
        }

        ProjectileUtil.rotateTowardsMovement(this, 0.5F);
        if (this.level().isClientSide()) {
            this.level().addParticle(ParticleTypes.END_ROD, this.getX() - vec31.x, this.getY() - vec31.y + 0.15D, this.getZ() - vec31.z, 0.0D, 0.0D, 0.0D);
        } else if (entity != null) {
            if (this.flightSteps > 0) {
                --this.flightSteps;
                if (this.flightSteps == 0) {
                    this.selectNextMoveDirection(this.currentMoveDirection == null ? null : this.currentMoveDirection.getAxis(), entity);
                }
            }

            if (this.currentMoveDirection != null) {
                BlockPos blockpos = this.blockPosition();
                Direction.Axis direction_axis = this.currentMoveDirection.getAxis();

                if (this.level().loadedAndEntityCanStandOn(blockpos.relative(this.currentMoveDirection), this)) {
                    this.selectNextMoveDirection(direction_axis, entity);
                } else {
                    BlockPos blockpos1 = entity.blockPosition();

                    if (direction_axis == Direction.Axis.X && blockpos.getX() == blockpos1.getX() || direction_axis == Direction.Axis.Z && blockpos.getZ() == blockpos1.getZ() || direction_axis == Direction.Axis.Y && blockpos.getY() == blockpos1.getY()) {
                        this.selectNextMoveDirection(direction_axis, entity);
                    }
                }
            }
        }

    }

    @Override
    protected boolean isAffectedByBlocks() {
        return !this.isRemoved();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !entity.noPhysics;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 16384.0D;
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        Entity entity = hitResult.getEntity();
        Entity entity1 = this.getOwner();
        LivingEntity livingentity = entity1 instanceof LivingEntity ? (LivingEntity) entity1 : null;
        DamageSource damagesource = this.damageSources().mobProjectile(this, livingentity);
        boolean flag = entity.hurtOrSimulate(damagesource, 4.0F);

        if (flag) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                EnchantmentHelper.doPostAttackEffects(serverlevel, entity, damagesource);
            }

            if (entity instanceof LivingEntity) {
                LivingEntity livingentity1 = (LivingEntity) entity;

                livingentity1.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200), (Entity) MoreObjects.firstNonNull(entity1, this));
            }
        }

    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        super.onHitBlock(hitResult);
        ((ServerLevel) this.level()).sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 2, 0.2D, 0.2D, 0.2D, 0.0D);
        this.playSound(SoundEvents.SHULKER_BULLET_HIT, 1.0F, 1.0F);
    }

    private void destroy() {
        this.discard();
        this.level().gameEvent(GameEvent.ENTITY_DAMAGE, this.position(), GameEvent.Context.of((Entity) this));
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        this.destroy();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurtClient(DamageSource source) {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        this.playSound(SoundEvents.SHULKER_BULLET_HURT, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 15, 0.2D, 0.2D, 0.2D, 0.0D);
        this.destroy();
        return true;
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.setDeltaMovement(packet.getMovement());
    }
}

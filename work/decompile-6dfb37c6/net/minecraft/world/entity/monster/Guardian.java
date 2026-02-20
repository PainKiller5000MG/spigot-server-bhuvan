package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Guardian extends Monster {

    protected static final int ATTACK_TIME = 80;
    private static final EntityDataAccessor<Boolean> DATA_ID_MOVING = SynchedEntityData.<Boolean>defineId(Guardian.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ID_ATTACK_TARGET = SynchedEntityData.<Integer>defineId(Guardian.class, EntityDataSerializers.INT);
    private float clientSideTailAnimation;
    private float clientSideTailAnimationO;
    private float clientSideTailAnimationSpeed;
    private float clientSideSpikesAnimation;
    private float clientSideSpikesAnimationO;
    private @Nullable LivingEntity clientSideCachedAttackTarget;
    private int clientSideAttackTime;
    private boolean clientSideTouchedGround;
    public @Nullable RandomStrollGoal randomStrollGoal;

    public Guardian(EntityType<? extends Guardian> type, Level level) {
        super(type, level);
        this.xpReward = 10;
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.moveControl = new Guardian.GuardianMoveControl(this);
        this.clientSideTailAnimation = this.random.nextFloat();
        this.clientSideTailAnimationO = this.clientSideTailAnimation;
    }

    @Override
    protected void registerGoals() {
        MoveTowardsRestrictionGoal movetowardsrestrictiongoal = new MoveTowardsRestrictionGoal(this, 1.0D);

        this.randomStrollGoal = new RandomStrollGoal(this, 1.0D, 80);
        this.goalSelector.addGoal(4, new Guardian.GuardianAttackGoal(this));
        this.goalSelector.addGoal(5, movetowardsrestrictiongoal);
        this.goalSelector.addGoal(7, this.randomStrollGoal);
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Guardian.class, 12.0F, 0.01F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.randomStrollGoal.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        movetowardsrestrictiongoal.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal(this, LivingEntity.class, 10, true, false, new Guardian.GuardianAttackSelector(this)));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.ATTACK_DAMAGE, 6.0D).add(Attributes.MOVEMENT_SPEED, 0.5D).add(Attributes.MAX_HEALTH, 30.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Guardian.DATA_ID_MOVING, false);
        entityData.define(Guardian.DATA_ID_ATTACK_TARGET, 0);
    }

    public boolean isMoving() {
        return (Boolean) this.entityData.get(Guardian.DATA_ID_MOVING);
    }

    private void setMoving(boolean value) {
        this.entityData.set(Guardian.DATA_ID_MOVING, value);
    }

    public int getAttackDuration() {
        return 80;
    }

    public void setActiveAttackTarget(int entityId) {
        this.entityData.set(Guardian.DATA_ID_ATTACK_TARGET, entityId);
    }

    public boolean hasActiveAttackTarget() {
        return (Integer) this.entityData.get(Guardian.DATA_ID_ATTACK_TARGET) != 0;
    }

    public @Nullable LivingEntity getActiveAttackTarget() {
        if (!this.hasActiveAttackTarget()) {
            return null;
        } else if (this.level().isClientSide()) {
            if (this.clientSideCachedAttackTarget != null) {
                return this.clientSideCachedAttackTarget;
            } else {
                Entity entity = this.level().getEntity((Integer) this.entityData.get(Guardian.DATA_ID_ATTACK_TARGET));

                if (entity instanceof LivingEntity) {
                    this.clientSideCachedAttackTarget = (LivingEntity) entity;
                    return this.clientSideCachedAttackTarget;
                } else {
                    return null;
                }
            }
        } else {
            return this.getTarget();
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (Guardian.DATA_ID_ATTACK_TARGET.equals(accessor)) {
            this.clientSideAttackTime = 0;
            this.clientSideCachedAttackTarget = null;
        }

    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isInWater() ? SoundEvents.GUARDIAN_AMBIENT : SoundEvents.GUARDIAN_AMBIENT_LAND;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.isInWater() ? SoundEvents.GUARDIAN_HURT : SoundEvents.GUARDIAN_HURT_LAND;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return this.isInWater() ? SoundEvents.GUARDIAN_DEATH : SoundEvents.GUARDIAN_DEATH_LAND;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.EVENTS;
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getFluidState(pos).is(FluidTags.WATER) ? 10.0F + level.getPathfindingCostFromLightLevels(pos) : super.getWalkTargetValue(pos, level);
    }

    @Override
    public void aiStep() {
        if (this.isAlive()) {
            if (this.level().isClientSide()) {
                this.clientSideTailAnimationO = this.clientSideTailAnimation;
                if (!this.isInWater()) {
                    this.clientSideTailAnimationSpeed = 2.0F;
                    Vec3 vec3 = this.getDeltaMovement();

                    if (vec3.y > 0.0D && this.clientSideTouchedGround && !this.isSilent()) {
                        this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), this.getFlopSound(), this.getSoundSource(), 1.0F, 1.0F, false);
                    }

                    this.clientSideTouchedGround = vec3.y < 0.0D && this.level().loadedAndEntityCanStandOn(this.blockPosition().below(), this);
                } else if (this.isMoving()) {
                    if (this.clientSideTailAnimationSpeed < 0.5F) {
                        this.clientSideTailAnimationSpeed = 4.0F;
                    } else {
                        this.clientSideTailAnimationSpeed += (0.5F - this.clientSideTailAnimationSpeed) * 0.1F;
                    }
                } else {
                    this.clientSideTailAnimationSpeed += (0.125F - this.clientSideTailAnimationSpeed) * 0.2F;
                }

                this.clientSideTailAnimation += this.clientSideTailAnimationSpeed;
                this.clientSideSpikesAnimationO = this.clientSideSpikesAnimation;
                if (!this.isInWater()) {
                    this.clientSideSpikesAnimation = this.random.nextFloat();
                } else if (this.isMoving()) {
                    this.clientSideSpikesAnimation += (0.0F - this.clientSideSpikesAnimation) * 0.25F;
                } else {
                    this.clientSideSpikesAnimation += (1.0F - this.clientSideSpikesAnimation) * 0.06F;
                }

                if (this.isMoving() && this.isInWater()) {
                    Vec3 vec31 = this.getViewVector(0.0F);

                    for (int i = 0; i < 2; ++i) {
                        this.level().addParticle(ParticleTypes.BUBBLE, this.getRandomX(0.5D) - vec31.x * 1.5D, this.getRandomY() - vec31.y * 1.5D, this.getRandomZ(0.5D) - vec31.z * 1.5D, 0.0D, 0.0D, 0.0D);
                    }
                }

                if (this.hasActiveAttackTarget()) {
                    if (this.clientSideAttackTime < this.getAttackDuration()) {
                        ++this.clientSideAttackTime;
                    }

                    LivingEntity livingentity = this.getActiveAttackTarget();

                    if (livingentity != null) {
                        this.getLookControl().setLookAt(livingentity, 90.0F, 90.0F);
                        this.getLookControl().tick();
                        double d0 = (double) this.getAttackAnimationScale(0.0F);
                        double d1 = livingentity.getX() - this.getX();
                        double d2 = livingentity.getY(0.5D) - this.getEyeY();
                        double d3 = livingentity.getZ() - this.getZ();
                        double d4 = Math.sqrt(d1 * d1 + d2 * d2 + d3 * d3);

                        d1 /= d4;
                        d2 /= d4;
                        d3 /= d4;
                        double d5 = this.random.nextDouble();

                        while (d5 < d4) {
                            d5 += 1.8D - d0 + this.random.nextDouble() * (1.7D - d0);
                            this.level().addParticle(ParticleTypes.BUBBLE, this.getX() + d1 * d5, this.getEyeY() + d2 * d5, this.getZ() + d3 * d5, 0.0D, 0.0D, 0.0D);
                        }
                    }
                }
            }

            if (this.isInWater()) {
                this.setAirSupply(300);
            } else if (this.onGround()) {
                this.setDeltaMovement(this.getDeltaMovement().add((double) ((this.random.nextFloat() * 2.0F - 1.0F) * 0.4F), 0.5D, (double) ((this.random.nextFloat() * 2.0F - 1.0F) * 0.4F)));
                this.setYRot(this.random.nextFloat() * 360.0F);
                this.setOnGround(false);
                this.needsSync = true;
            }

            if (this.hasActiveAttackTarget()) {
                this.setYRot(this.yHeadRot);
            }
        }

        super.aiStep();
    }

    protected SoundEvent getFlopSound() {
        return SoundEvents.GUARDIAN_FLOP;
    }

    public float getTailAnimation(float a) {
        return Mth.lerp(a, this.clientSideTailAnimationO, this.clientSideTailAnimation);
    }

    public float getSpikesAnimation(float a) {
        return Mth.lerp(a, this.clientSideSpikesAnimationO, this.clientSideSpikesAnimation);
    }

    public float getAttackAnimationScale(float a) {
        return ((float) this.clientSideAttackTime + a) / (float) this.getAttackDuration();
    }

    public float getClientSideAttackTime() {
        return (float) this.clientSideAttackTime;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    public static boolean checkGuardianSpawnRules(EntityType<? extends Guardian> type, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random) {
        return (random.nextInt(20) == 0 || !level.canSeeSkyFromBelowWater(pos)) && level.getDifficulty() != Difficulty.PEACEFUL && (EntitySpawnReason.isSpawner(spawnReason) || level.getFluidState(pos).is(FluidTags.WATER)) && level.getFluidState(pos.below()).is(FluidTags.WATER);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (!this.isMoving() && !source.is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS) && !source.is(DamageTypes.THORNS)) {
            Entity entity = source.getDirectEntity();

            if (entity instanceof LivingEntity) {
                LivingEntity livingentity = (LivingEntity) entity;

                livingentity.hurtServer(level, this.damageSources().thorns(this), 2.0F);
            }
        }

        if (this.randomStrollGoal != null) {
            this.randomStrollGoal.trigger();
        }

        return super.hurtServer(level, source, damage);
    }

    @Override
    public int getMaxHeadXRot() {
        return 180;
    }

    @Override
    protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
        this.moveRelative(0.1F, input);
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
        if (!this.isMoving() && this.getTarget() == null) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.005D, 0.0D));
        }

    }

    private static class GuardianAttackSelector implements TargetingConditions.Selector {

        private final Guardian guardian;

        public GuardianAttackSelector(Guardian guardian) {
            this.guardian = guardian;
        }

        @Override
        public boolean test(@Nullable LivingEntity target, ServerLevel level) {
            return (target instanceof Player || target instanceof Squid || target instanceof Axolotl) && target.distanceToSqr((Entity) this.guardian) > 9.0D;
        }
    }

    public static class GuardianAttackGoal extends Goal {

        private final Guardian guardian;
        public int attackTime;
        private final boolean elder;

        public GuardianAttackGoal(Guardian guardian) {
            this.guardian = guardian;
            this.elder = guardian instanceof ElderGuardian;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity livingentity = this.guardian.getTarget();

            return livingentity != null && livingentity.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && (this.elder || this.guardian.getTarget() != null && this.guardian.distanceToSqr((Entity) this.guardian.getTarget()) > 9.0D);
        }

        @Override
        public void start() {
            this.attackTime = -10;
            this.guardian.getNavigation().stop();
            LivingEntity livingentity = this.guardian.getTarget();

            if (livingentity != null) {
                this.guardian.getLookControl().setLookAt(livingentity, 90.0F, 90.0F);
            }

            this.guardian.needsSync = true;
        }

        @Override
        public void stop() {
            this.guardian.setActiveAttackTarget(0);
            this.guardian.setTarget((LivingEntity) null);
            this.guardian.randomStrollGoal.trigger();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity livingentity = this.guardian.getTarget();

            if (livingentity != null) {
                this.guardian.getNavigation().stop();
                this.guardian.getLookControl().setLookAt(livingentity, 90.0F, 90.0F);
                if (!this.guardian.hasLineOfSight(livingentity)) {
                    this.guardian.setTarget((LivingEntity) null);
                } else {
                    ++this.attackTime;
                    if (this.attackTime == 0) {
                        this.guardian.setActiveAttackTarget(livingentity.getId());
                        if (!this.guardian.isSilent()) {
                            this.guardian.level().broadcastEntityEvent(this.guardian, (byte) 21);
                        }
                    } else if (this.attackTime >= this.guardian.getAttackDuration()) {
                        float f = 1.0F;

                        if (this.guardian.level().getDifficulty() == Difficulty.HARD) {
                            f += 2.0F;
                        }

                        if (this.elder) {
                            f += 2.0F;
                        }

                        ServerLevel serverlevel = getServerLevel((Entity) this.guardian);

                        livingentity.hurtServer(serverlevel, this.guardian.damageSources().indirectMagic(this.guardian, this.guardian), f);
                        this.guardian.doHurtTarget(serverlevel, livingentity);
                        this.guardian.setTarget((LivingEntity) null);
                    }

                    super.tick();
                }
            }
        }
    }

    private static class GuardianMoveControl extends MoveControl {

        private final Guardian guardian;

        public GuardianMoveControl(Guardian guardian) {
            super(guardian);
            this.guardian = guardian;
        }

        @Override
        public void tick() {
            if (this.operation == MoveControl.Operation.MOVE_TO && !this.guardian.getNavigation().isDone()) {
                Vec3 vec3 = new Vec3(this.wantedX - this.guardian.getX(), this.wantedY - this.guardian.getY(), this.wantedZ - this.guardian.getZ());
                double d0 = vec3.length();
                double d1 = vec3.x / d0;
                double d2 = vec3.y / d0;
                double d3 = vec3.z / d0;
                float f = (float) (Mth.atan2(vec3.z, vec3.x) * (double) (180F / (float) Math.PI)) - 90.0F;

                this.guardian.setYRot(this.rotlerp(this.guardian.getYRot(), f, 90.0F));
                this.guardian.yBodyRot = this.guardian.getYRot();
                float f1 = (float) (this.speedModifier * this.guardian.getAttributeValue(Attributes.MOVEMENT_SPEED));
                float f2 = Mth.lerp(0.125F, this.guardian.getSpeed(), f1);

                this.guardian.setSpeed(f2);
                double d4 = Math.sin((double) (this.guardian.tickCount + this.guardian.getId()) * 0.5D) * 0.05D;
                double d5 = Math.cos((double) (this.guardian.getYRot() * ((float) Math.PI / 180F)));
                double d6 = Math.sin((double) (this.guardian.getYRot() * ((float) Math.PI / 180F)));
                double d7 = Math.sin((double) (this.guardian.tickCount + this.guardian.getId()) * 0.75D) * 0.05D;

                this.guardian.setDeltaMovement(this.guardian.getDeltaMovement().add(d4 * d5, d7 * (d6 + d5) * 0.25D + (double) f2 * d2 * 0.1D, d4 * d6));
                LookControl lookcontrol = this.guardian.getLookControl();
                double d8 = this.guardian.getX() + d1 * 2.0D;
                double d9 = this.guardian.getEyeY() + d2 / d0;
                double d10 = this.guardian.getZ() + d3 * 2.0D;
                double d11 = lookcontrol.getWantedX();
                double d12 = lookcontrol.getWantedY();
                double d13 = lookcontrol.getWantedZ();

                if (!lookcontrol.isLookingAtTarget()) {
                    d11 = d8;
                    d12 = d9;
                    d13 = d10;
                }

                this.guardian.getLookControl().setLookAt(Mth.lerp(0.125D, d11, d8), Mth.lerp(0.125D, d12, d9), Mth.lerp(0.125D, d13, d10), 10.0F, 40.0F);
                this.guardian.setMoving(true);
            } else {
                this.guardian.setSpeed(0.0F);
                this.guardian.setMoving(false);
            }
        }
    }
}

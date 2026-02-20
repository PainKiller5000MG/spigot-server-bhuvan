package net.minecraft.world.entity.animal.camel;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Camel extends AbstractHorse {

    public static final float BABY_SCALE = 0.45F;
    public static final int DASH_COOLDOWN_TICKS = 55;
    public static final int MAX_HEAD_Y_ROT = 30;
    private static final float RUNNING_SPEED_BONUS = 0.1F;
    private static final float DASH_VERTICAL_MOMENTUM = 1.4285F;
    private static final float DASH_HORIZONTAL_MOMENTUM = 22.2222F;
    private static final int DASH_MINIMUM_DURATION_TICKS = 5;
    private static final int SITDOWN_DURATION_TICKS = 40;
    private static final int STANDUP_DURATION_TICKS = 52;
    private static final int IDLE_MINIMAL_DURATION_TICKS = 80;
    private static final float SITTING_HEIGHT_DIFFERENCE = 1.43F;
    private static final long DEFAULT_LAST_POSE_CHANGE_TICK = 0L;
    public static final EntityDataAccessor<Boolean> DASH = SynchedEntityData.<Boolean>defineId(Camel.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Long> LAST_POSE_CHANGE_TICK = SynchedEntityData.<Long>defineId(Camel.class, EntityDataSerializers.LONG);
    public final AnimationState sitAnimationState = new AnimationState();
    public final AnimationState sitPoseAnimationState = new AnimationState();
    public final AnimationState sitUpAnimationState = new AnimationState();
    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState dashAnimationState = new AnimationState();
    private static final EntityDimensions SITTING_DIMENSIONS = EntityDimensions.scalable(EntityType.CAMEL.getWidth(), EntityType.CAMEL.getHeight() - 1.43F).withEyeHeight(0.845F);
    private int dashCooldown = 0;
    private int idleAnimationTimeout = 0;

    public Camel(EntityType<? extends Camel> type, Level level) {
        super(type, level);
        this.moveControl = new Camel.CamelMoveControl();
        this.lookControl = new Camel.CamelLookControl();
        GroundPathNavigation groundpathnavigation = (GroundPathNavigation) this.getNavigation();

        groundpathnavigation.setCanFloat(true);
        groundpathnavigation.setCanWalkOverFences(true);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putLong("LastPoseTick", (Long) this.entityData.get(Camel.LAST_POSE_CHANGE_TICK));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        long i = input.getLongOr("LastPoseTick", 0L);

        if (i < 0L) {
            this.setPose(Pose.SITTING);
        }

        this.resetLastPoseChangeTick(i);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseHorseAttributes().add(Attributes.MAX_HEALTH, 32.0D).add(Attributes.MOVEMENT_SPEED, (double) 0.09F).add(Attributes.JUMP_STRENGTH, (double) 0.42F).add(Attributes.STEP_HEIGHT, 1.5D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Camel.DASH, false);
        entityData.define(Camel.LAST_POSE_CHANGE_TICK, 0L);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        CamelAi.initMemories(this, level.getRandom());
        this.resetLastPoseChangeTickToFullStand(level.getLevel().getGameTime());
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    public static boolean checkCamelSpawnRules(EntityType<Camel> type, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.CAMELS_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    @Override
    protected Brain.Provider<Camel> brainProvider() {
        return CamelAi.brainProvider();
    }

    @Override
    protected void registerGoals() {}

    @Override
    protected Brain<?> makeBrain(Dynamic<?> input) {
        return CamelAi.makeBrain(this.brainProvider().makeBrain(input));
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return pose == Pose.SITTING ? Camel.SITTING_DIMENSIONS.scale(this.getAgeScale()) : super.getDefaultDimensions(pose);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("camelBrain");
        Brain<?> brain = this.getBrain();

        brain.tick(level, this);
        profilerfiller.pop();
        profilerfiller.push("camelActivityUpdate");
        CamelAi.updateActivity(this);
        profilerfiller.pop();
        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isDashing() && this.dashCooldown < 50 && (this.onGround() || this.isInLiquid() || this.isPassenger())) {
            this.setDashing(false);
        }

        if (this.dashCooldown > 0) {
            --this.dashCooldown;
            if (this.dashCooldown == 0) {
                this.level().playSound((Entity) null, this.blockPosition(), this.getDashReadySound(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
        }

        if (this.level().isClientSide()) {
            this.setupAnimationStates();
        }

        if (this.refuseToMove()) {
            this.clampHeadRotationToBody();
        }

        if (this.isCamelSitting() && this.isInWater()) {
            this.standUpInstantly();
        }

    }

    private void setupAnimationStates() {
        if (this.idleAnimationTimeout <= 0) {
            this.idleAnimationTimeout = this.random.nextInt(40) + 80;
            this.idleAnimationState.start(this.tickCount);
        } else {
            --this.idleAnimationTimeout;
        }

        if (this.isCamelVisuallySitting()) {
            this.sitUpAnimationState.stop();
            this.dashAnimationState.stop();
            if (this.isVisuallySittingDown()) {
                this.sitAnimationState.startIfStopped(this.tickCount);
                this.sitPoseAnimationState.stop();
            } else {
                this.sitAnimationState.stop();
                this.sitPoseAnimationState.startIfStopped(this.tickCount);
            }
        } else {
            this.sitAnimationState.stop();
            this.sitPoseAnimationState.stop();
            this.dashAnimationState.animateWhen(this.isDashing(), this.tickCount);
            this.sitUpAnimationState.animateWhen(this.isInPoseTransition() && this.getPoseTime() >= 0L, this.tickCount);
        }

    }

    @Override
    protected void updateWalkAnimation(float distance) {
        float f1;

        if (this.getPose() == Pose.STANDING && !this.dashAnimationState.isStarted()) {
            f1 = Math.min(distance * 6.0F, 1.0F);
        } else {
            f1 = 0.0F;
        }

        this.walkAnimation.update(f1, 0.2F, this.isBaby() ? 3.0F : 1.0F);
    }

    @Override
    public void travel(Vec3 input) {
        if (this.refuseToMove() && this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
            input = input.multiply(0.0D, 1.0D, 0.0D);
        }

        super.travel(input);
    }

    @Override
    protected void tickRidden(Player controller, Vec3 riddenInput) {
        super.tickRidden(controller, riddenInput);
        if (controller.zza > 0.0F && this.isCamelSitting() && !this.isInPoseTransition()) {
            this.standUp();
        }

    }

    public boolean refuseToMove() {
        return this.isCamelSitting() || this.isInPoseTransition();
    }

    @Override
    protected float getRiddenSpeed(Player controller) {
        float f = controller.isSprinting() && this.getJumpCooldown() == 0 ? 0.1F : 0.0F;

        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) + f;
    }

    @Override
    protected Vec2 getRiddenRotation(LivingEntity controller) {
        return this.refuseToMove() ? new Vec2(this.getXRot(), this.getYRot()) : super.getRiddenRotation(controller);
    }

    @Override
    protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
        return this.refuseToMove() ? Vec3.ZERO : super.getRiddenInput(controller, selfInput);
    }

    @Override
    public boolean canJump() {
        return !this.refuseToMove() && super.canJump();
    }

    @Override
    public void onPlayerJump(int jumpAmount) {
        if (this.isSaddled() && this.dashCooldown <= 0 && this.onGround()) {
            super.onPlayerJump(jumpAmount);
        }
    }

    @Override
    public boolean canSprint() {
        return true;
    }

    @Override
    protected void executeRidersJump(float amount, Vec3 input) {
        double d0 = (double) this.getJumpPower();

        this.addDeltaMovement(this.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize().scale((double) (22.2222F * amount) * this.getAttributeValue(Attributes.MOVEMENT_SPEED) * (double) this.getBlockSpeedFactor()).add(0.0D, (double) (1.4285F * amount) * d0, 0.0D));
        this.dashCooldown = 55;
        this.setDashing(true);
        this.needsSync = true;
    }

    public boolean isDashing() {
        return (Boolean) this.entityData.get(Camel.DASH);
    }

    public void setDashing(boolean isDashing) {
        this.entityData.set(Camel.DASH, isDashing);
    }

    @Override
    public void handleStartJump(int jumpScale) {
        this.makeSound(this.getDashingSound());
        this.gameEvent(GameEvent.ENTITY_ACTION);
        this.setDashing(true);
    }

    protected SoundEvent getDashingSound() {
        return SoundEvents.CAMEL_DASH;
    }

    protected SoundEvent getDashReadySound() {
        return SoundEvents.CAMEL_DASH_READY;
    }

    @Override
    public void handleStopJump() {}

    @Override
    public int getJumpCooldown() {
        return this.dashCooldown;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.CAMEL_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.CAMEL_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.CAMEL_HURT;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
        if (blockState.is(BlockTags.CAMEL_SAND_STEP_SOUND_BLOCKS)) {
            this.playSound(SoundEvents.CAMEL_STEP_SAND, 1.0F, 1.0F);
        } else {
            this.playSound(SoundEvents.CAMEL_STEP, 1.0F, 1.0F);
        }

    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return itemStack.is(ItemTags.CAMEL_FOOD);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (player.isSecondaryUseActive() && !this.isBaby()) {
            this.openCustomInventoryScreen(player);
            return InteractionResult.SUCCESS;
        } else {
            InteractionResult interactionresult = itemstack.interactLivingEntity(player, this, hand);

            if (interactionresult.consumesAction()) {
                return interactionresult;
            } else if (this.isFood(itemstack)) {
                return this.fedFood(player, itemstack);
            } else {
                if (this.getPassengers().size() < 2 && !this.isBaby()) {
                    this.doPlayerRide(player);
                }

                return InteractionResult.CONSUME;
            }
        }
    }

    @Override
    public void onElasticLeashPull() {
        super.onElasticLeashPull();
        if (this.isCamelSitting() && !this.isInPoseTransition() && this.canCamelChangePose()) {
            this.standUp();
        }

    }

    @Override
    public Vec3[] getQuadLeashOffsets() {
        return Leashable.createQuadLeashOffsets(this, 0.02D, 0.48D, 0.25D, 0.82D);
    }

    public boolean canCamelChangePose() {
        return this.wouldNotSuffocateAtTargetPose(this.isCamelSitting() ? Pose.STANDING : Pose.SITTING);
    }

    @Override
    protected boolean handleEating(Player player, ItemStack itemStack) {
        if (!this.isFood(itemStack)) {
            return false;
        } else {
            boolean flag = this.getHealth() < this.getMaxHealth();

            if (flag) {
                this.heal(2.0F);
            }

            boolean flag1 = this.isTamed() && this.getAge() == 0 && this.canFallInLove();

            if (flag1) {
                this.setInLove(player);
            }

            boolean flag2 = this.isBaby();

            if (flag2) {
                this.level().addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), 0.0D, 0.0D, 0.0D);
                if (!this.level().isClientSide()) {
                    this.ageUp(10);
                }
            }

            if (!flag && !flag1 && !flag2) {
                return false;
            } else {
                if (!this.isSilent()) {
                    SoundEvent soundevent = this.getEatingSound();

                    if (soundevent != null) {
                        this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), soundevent, this.getSoundSource(), 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
                    }
                }

                this.gameEvent(GameEvent.EAT);
                return true;
            }
        }
    }

    @Override
    protected boolean canPerformRearing() {
        return false;
    }

    @Override
    public boolean canMate(Animal partner) {
        boolean flag;

        if (partner != this && partner instanceof Camel camel) {
            if (this.canParent() && camel.canParent()) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    }

    @Override
    public @Nullable Camel getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return EntityType.CAMEL.create(level, EntitySpawnReason.BREEDING);
    }

    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.CAMEL_EAT;
    }

    @Override
    protected void actuallyHurt(ServerLevel level, DamageSource source, float dmg) {
        this.standUpInstantly();
        super.actuallyHurt(level, source, dmg);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        int i = Math.max(this.getPassengers().indexOf(passenger), 0);
        boolean flag = i == 0;
        float f1 = 0.5F;
        float f2 = (float) (this.isRemoved() ? (double) 0.01F : this.getBodyAnchorAnimationYOffset(flag, 0.0F, dimensions, scale));

        if (this.getPassengers().size() > 1) {
            if (!flag) {
                f1 = -0.7F;
            }

            if (passenger instanceof Animal) {
                f1 += 0.2F;
            }
        }

        return (new Vec3(0.0D, (double) f2, (double) (f1 * scale))).yRot(-this.getYRot() * ((float) Math.PI / 180F));
    }

    @Override
    public float getAgeScale() {
        return this.isBaby() ? 0.45F : 1.0F;
    }

    private double getBodyAnchorAnimationYOffset(boolean isFront, float partialTicks, EntityDimensions dimensions, float scale) {
        double d0 = (double) (dimensions.height() - 0.375F * scale);
        float f2 = scale * 1.43F;
        float f3 = f2 - scale * 0.2F;
        float f4 = f2 - f3;
        boolean flag1 = this.isInPoseTransition();
        boolean flag2 = this.isCamelSitting();

        if (flag1) {
            int i = flag2 ? 40 : 52;
            int j;
            float f5;

            if (flag2) {
                j = 28;
                f5 = isFront ? 0.5F : 0.1F;
            } else {
                j = isFront ? 24 : 32;
                f5 = isFront ? 0.6F : 0.35F;
            }

            float f6 = Mth.clamp((float) this.getPoseTime() + partialTicks, 0.0F, (float) i);
            boolean flag3 = f6 < (float) j;
            float f7 = flag3 ? f6 / (float) j : (f6 - (float) j) / (float) (i - j);
            float f8 = f2 - f5 * f3;

            d0 += flag2 ? (double) Mth.lerp(f7, flag3 ? f2 : f8, flag3 ? f8 : f4) : (double) Mth.lerp(f7, flag3 ? f4 - f2 : f4 - f8, flag3 ? f4 - f8 : 0.0F);
        }

        if (flag2 && !flag1) {
            d0 += (double) f4;
        }

        return d0;
    }

    @Override
    public Vec3 getLeashOffset(float partialTicks) {
        EntityDimensions entitydimensions = this.getDimensions(this.getPose());
        float f1 = this.getAgeScale();

        return new Vec3(0.0D, this.getBodyAnchorAnimationYOffset(true, partialTicks, entitydimensions, f1) - (double) (0.2F * f1), (double) (entitydimensions.width() * 0.56F));
    }

    @Override
    public int getMaxHeadYRot() {
        return 30;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() <= 2;
    }

    public boolean isCamelSitting() {
        return (Long) this.entityData.get(Camel.LAST_POSE_CHANGE_TICK) < 0L;
    }

    public boolean isCamelVisuallySitting() {
        return this.getPoseTime() < 0L != this.isCamelSitting();
    }

    public boolean isInPoseTransition() {
        long i = this.getPoseTime();

        return i < (long) (this.isCamelSitting() ? 40 : 52);
    }

    private boolean isVisuallySittingDown() {
        return this.isCamelSitting() && this.getPoseTime() < 40L && this.getPoseTime() >= 0L;
    }

    public void sitDown() {
        if (!this.isCamelSitting()) {
            this.makeSound(this.getSitDownSound());
            this.setPose(Pose.SITTING);
            this.gameEvent(GameEvent.ENTITY_ACTION);
            this.resetLastPoseChangeTick(-this.level().getGameTime());
        }
    }

    public void standUp() {
        if (this.isCamelSitting()) {
            this.makeSound(this.getStandUpSound());
            this.setPose(Pose.STANDING);
            this.gameEvent(GameEvent.ENTITY_ACTION);
            this.resetLastPoseChangeTick(this.level().getGameTime());
        }
    }

    protected SoundEvent getStandUpSound() {
        return SoundEvents.CAMEL_STAND;
    }

    protected SoundEvent getSitDownSound() {
        return SoundEvents.CAMEL_SIT;
    }

    public void standUpInstantly() {
        this.setPose(Pose.STANDING);
        this.gameEvent(GameEvent.ENTITY_ACTION);
        this.resetLastPoseChangeTickToFullStand(this.level().getGameTime());
    }

    @VisibleForTesting
    public void resetLastPoseChangeTick(long syncedPoseTickTime) {
        this.entityData.set(Camel.LAST_POSE_CHANGE_TICK, syncedPoseTickTime);
    }

    private void resetLastPoseChangeTickToFullStand(long currentTime) {
        this.resetLastPoseChangeTick(Math.max(0L, currentTime - 52L - 1L));
    }

    public long getPoseTime() {
        return this.level().getGameTime() - Math.abs((Long) this.entityData.get(Camel.LAST_POSE_CHANGE_TICK));
    }

    @Override
    protected Holder<SoundEvent> getEquipSound(EquipmentSlot slot, ItemStack stack, Equippable equippable) {
        return (Holder<SoundEvent>) (slot == EquipmentSlot.SADDLE ? this.getSaddleSound() : super.getEquipSound(slot, stack, equippable));
    }

    protected Holder.Reference<SoundEvent> getSaddleSound() {
        return SoundEvents.CAMEL_SADDLE;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (!this.firstTick && Camel.DASH.equals(accessor)) {
            this.dashCooldown = this.dashCooldown == 0 ? 55 : this.dashCooldown;
        }

        super.onSyncedDataUpdated(accessor);
    }

    @Override
    public boolean isTamed() {
        return true;
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        if (!this.level().isClientSide()) {
            player.openHorseInventory(this, this.inventory);
        }

    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new Camel.CamelBodyRotationControl(this);
    }

    private class CamelBodyRotationControl extends BodyRotationControl {

        public CamelBodyRotationControl(Camel camel) {
            super(camel);
        }

        @Override
        public void clientTick() {
            if (!Camel.this.refuseToMove()) {
                super.clientTick();
            }

        }
    }

    private class CamelLookControl extends LookControl {

        private CamelLookControl() {
            super(Camel.this);
        }

        @Override
        public void tick() {
            if (!Camel.this.hasControllingPassenger()) {
                super.tick();
            }

        }
    }

    private class CamelMoveControl extends MoveControl {

        public CamelMoveControl() {
            super(Camel.this);
        }

        @Override
        public void tick() {
            if (this.operation == MoveControl.Operation.MOVE_TO && !Camel.this.isLeashed() && Camel.this.isCamelSitting() && !Camel.this.isInPoseTransition() && Camel.this.canCamelChangePose()) {
                Camel.this.standUp();
            }

            super.tick();
        }
    }
}

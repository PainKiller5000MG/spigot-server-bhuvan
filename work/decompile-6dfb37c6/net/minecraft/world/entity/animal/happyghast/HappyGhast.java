package net.minecraft.world.entity.animal.happyghast;

import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class HappyGhast extends Animal {

    public static final float BABY_SCALE = 0.2375F;
    public static final int WANDER_GROUND_DISTANCE = 16;
    public static final int SMALL_RESTRICTION_RADIUS = 32;
    public static final int LARGE_RESTRICTION_RADIUS = 64;
    public static final int RESTRICTION_RADIUS_BUFFER = 16;
    public static final int FAST_HEALING_TICKS = 20;
    public static final int SLOW_HEALING_TICKS = 600;
    public static final int MAX_PASSANGERS = 4;
    private static final int STILL_TIMEOUT_ON_LOAD_GRACE_PERIOD = 60;
    private static final int MAX_STILL_TIMEOUT = 10;
    public static final float SPEED_MULTIPLIER_WHEN_PANICKING = 2.0F;
    private int leashHolderTime = 0;
    private int serverStillTimeout;
    private static final EntityDataAccessor<Boolean> IS_LEASH_HOLDER = SynchedEntityData.<Boolean>defineId(HappyGhast.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> STAYS_STILL = SynchedEntityData.<Boolean>defineId(HappyGhast.class, EntityDataSerializers.BOOLEAN);
    private static final float MAX_SCALE = 1.0F;

    public HappyGhast(EntityType<? extends HappyGhast> type, Level level) {
        super(type, level);
        this.moveControl = new Ghast.GhastMoveControl(this, true, this::isOnStillTimeout);
        this.lookControl = new HappyGhast.HappyGhastLookControl();
    }

    private void setServerStillTimeout(int serverStillTimeout) {
        if (this.serverStillTimeout <= 0 && serverStillTimeout > 0) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
                serverlevel.getChunkSource().chunkMap.sendToTrackingPlayers(this, ClientboundEntityPositionSyncPacket.of(this));
            }
        }

        this.serverStillTimeout = serverStillTimeout;
        this.syncStayStillFlag();
    }

    private PathNavigation createBabyNavigation(Level level) {
        return new HappyGhast.BabyFlyingPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(3, new HappyGhast.HappyGhastFloatGoal());
        this.goalSelector.addGoal(4, new TemptGoal.ForNonPathfinders(this, 1.0D, (itemstack) -> {
            return !this.isWearingBodyArmor() && !this.isBaby() ? itemstack.is(ItemTags.HAPPY_GHAST_TEMPT_ITEMS) : itemstack.is(ItemTags.HAPPY_GHAST_FOOD);
        }, false, 7.0D));
        this.goalSelector.addGoal(5, new Ghast.RandomFloatAroundGoal(this, 16));
    }

    private void adultGhastSetup() {
        this.moveControl = new Ghast.GhastMoveControl(this, true, this::isOnStillTimeout);
        this.lookControl = new HappyGhast.HappyGhastLookControl();
        this.navigation = this.createNavigation(this.level());
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            this.removeAllGoals((goal) -> {
                return true;
            });
            this.registerGoals();
            this.brain.stopAll(serverlevel, this);
            this.brain.clearMemories();
        }

    }

    private void babyGhastSetup() {
        this.moveControl = new FlyingMoveControl(this, 180, true);
        this.lookControl = new LookControl(this);
        this.navigation = this.createBabyNavigation(this.level());
        this.setServerStillTimeout(0);
        this.removeAllGoals((goal) -> {
            return true;
        });
    }

    @Override
    protected void ageBoundaryReached() {
        if (this.isBaby()) {
            this.babyGhastSetup();
        } else {
            this.adultGhastSetup();
        }

        super.ageBoundaryReached();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 20.0D).add(Attributes.TEMPT_RANGE, 16.0D).add(Attributes.FLYING_SPEED, 0.05D).add(Attributes.MOVEMENT_SPEED, 0.05D).add(Attributes.FOLLOW_RANGE, 16.0D).add(Attributes.CAMERA_DISTANCE, 8.0D);
    }

    @Override
    protected float sanitizeScale(float scale) {
        return Math.min(scale, 1.0F);
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {}

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public void travel(Vec3 input) {
        float f = (float) this.getAttributeValue(Attributes.FLYING_SPEED) * 5.0F / 3.0F;

        this.travelFlying(input, f, f, f);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return !level.isEmptyBlock(pos) ? 0.0F : (level.isEmptyBlock(pos.below()) && !level.isEmptyBlock(pos.below(2)) ? 10.0F : 5.0F);
    }

    @Override
    public boolean canBreatheUnderwater() {
        return this.isBaby() ? true : super.canBreatheUnderwater();
    }

    @Override
    protected boolean shouldStayCloseToLeashHolder() {
        return false;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {}

    @Override
    public float getVoicePitch() {
        return 1.0F;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

    @Override
    public int getAmbientSoundInterval() {
        int i = super.getAmbientSoundInterval();

        return this.isVehicle() ? i * 6 : i;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isBaby() ? SoundEvents.GHASTLING_AMBIENT : SoundEvents.HAPPY_GHAST_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.isBaby() ? SoundEvents.GHASTLING_HURT : SoundEvents.HAPPY_GHAST_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return this.isBaby() ? SoundEvents.GHASTLING_DEATH : SoundEvents.HAPPY_GHAST_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return this.isBaby() ? 1.0F : 4.0F;
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 1;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return EntityType.HAPPY_GHAST.create(level, EntitySpawnReason.BREEDING);
    }

    @Override
    public boolean canFallInLove() {
        return false;
    }

    @Override
    public float getAgeScale() {
        return this.isBaby() ? 0.2375F : 1.0F;
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return itemStack.is(ItemTags.HAPPY_GHAST_FOOD);
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return slot != EquipmentSlot.BODY ? super.canUseSlot(slot) : this.isAlive() && !this.isBaby();
    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.BODY;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.isBaby()) {
            return super.mobInteract(player, hand);
        } else {
            ItemStack itemstack = player.getItemInHand(hand);

            if (!itemstack.isEmpty()) {
                InteractionResult interactionresult = itemstack.interactLivingEntity(player, this, hand);

                if (interactionresult.consumesAction()) {
                    return interactionresult;
                }
            }

            if (this.isWearingBodyArmor() && !player.isSecondaryUseActive()) {
                this.doPlayerRide(player);
                return InteractionResult.SUCCESS;
            } else {
                return super.mobInteract(player, hand);
            }
        }
    }

    private void doPlayerRide(Player player) {
        if (!this.level().isClientSide()) {
            player.startRiding(this);
        }

    }

    @Override
    protected void addPassenger(Entity passenger) {
        if (!this.isVehicle()) {
            this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEvents.HARNESS_GOGGLES_DOWN, this.getSoundSource(), 1.0F, 1.0F);
        }

        super.addPassenger(passenger);
        if (!this.level().isClientSide()) {
            if (!this.scanPlayerAboveGhast()) {
                this.setServerStillTimeout(0);
            } else if (this.serverStillTimeout > 10) {
                this.setServerStillTimeout(10);
            }
        }

    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!this.level().isClientSide()) {
            this.setServerStillTimeout(10);
        }

        if (!this.isVehicle()) {
            this.clearHome();
            this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEvents.HARNESS_GOGGLES_UP, this.getSoundSource(), 1.0F, 1.0F);
        }

    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < 4;
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        Entity entity = this.getFirstPassenger();

        if (this.isWearingBodyArmor() && !this.isOnStillTimeout() && entity instanceof Player player) {
            return player;
        } else {
            return super.getControllingPassenger();
        }
    }

    @Override
    protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
        float f = controller.xxa;
        float f1 = 0.0F;
        float f2 = 0.0F;

        if (controller.zza != 0.0F) {
            float f3 = Mth.cos((double) (controller.getXRot() * ((float) Math.PI / 180F)));
            float f4 = -Mth.sin((double) (controller.getXRot() * ((float) Math.PI / 180F)));

            if (controller.zza < 0.0F) {
                f3 *= -0.5F;
                f4 *= -0.5F;
            }

            f2 = f4;
            f1 = f3;
        }

        if (controller.isJumping()) {
            f2 += 0.5F;
        }

        return (new Vec3((double) f, (double) f2, (double) f1)).scale((double) 3.9F * this.getAttributeValue(Attributes.FLYING_SPEED));
    }

    protected Vec2 getRiddenRotation(LivingEntity controller) {
        return new Vec2(controller.getXRot() * 0.5F, controller.getYRot());
    }

    @Override
    protected void tickRidden(Player controller, Vec3 riddenInput) {
        super.tickRidden(controller, riddenInput);
        Vec2 vec2 = this.getRiddenRotation(controller);
        float f = this.getYRot();
        float f1 = Mth.wrapDegrees(vec2.y - f);
        float f2 = 0.08F;

        f += f1 * 0.08F;
        this.setRot(f, vec2.x);
        this.yRotO = this.yBodyRot = this.yHeadRot = f;
    }

    @Override
    protected Brain.Provider<HappyGhast> brainProvider() {
        return HappyGhastAi.brainProvider();
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> input) {
        return HappyGhastAi.makeBrain(this.brainProvider().makeBrain(input));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (this.isBaby()) {
            ProfilerFiller profilerfiller = Profiler.get();

            profilerfiller.push("happyGhastBrain");
            this.brain.tick(level, this);
            profilerfiller.pop();
            profilerfiller.push("happyGhastActivityUpdate");
            HappyGhastAi.updateActivity(this);
            profilerfiller.pop();
        }

        this.checkRestriction();
        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            if (this.leashHolderTime > 0) {
                --this.leashHolderTime;
            }

            this.setLeashHolder(this.leashHolderTime > 0);
            if (this.serverStillTimeout > 0) {
                if (this.tickCount > 60) {
                    --this.serverStillTimeout;
                }

                this.setServerStillTimeout(this.serverStillTimeout);
            }

            if (this.scanPlayerAboveGhast()) {
                this.setServerStillTimeout(10);
            }

        }
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide()) {
            this.setRequiresPrecisePosition(this.isOnStillTimeout());
        }

        super.aiStep();
        this.continuousHeal();
    }

    private int getHappyGhastRestrictionRadius() {
        return !this.isBaby() && this.getItemBySlot(EquipmentSlot.BODY).isEmpty() ? 64 : 32;
    }

    private void checkRestriction() {
        if (!this.isLeashed() && !this.isVehicle()) {
            int i = this.getHappyGhastRestrictionRadius();

            if (!this.hasHome() || !this.getHomePosition().closerThan(this.blockPosition(), (double) (i + 16)) || i != this.getHomeRadius()) {
                this.setHomeTo(this.blockPosition(), i);
            }
        }
    }

    private void continuousHeal() {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            if (this.isAlive() && this.deathTime == 0 && this.getMaxHealth() != this.getHealth()) {
                boolean flag = this.isInClouds() || serverlevel.precipitationAt(this.blockPosition()) != Biome.Precipitation.NONE;

                if (this.tickCount % (flag ? 20 : 600) == 0) {
                    this.heal(1.0F);
                }

                return;
            }
        }

    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(HappyGhast.IS_LEASH_HOLDER, false);
        entityData.define(HappyGhast.STAYS_STILL, false);
    }

    private void setLeashHolder(boolean isLeashHolder) {
        this.entityData.set(HappyGhast.IS_LEASH_HOLDER, isLeashHolder);
    }

    public boolean isLeashHolder() {
        return (Boolean) this.entityData.get(HappyGhast.IS_LEASH_HOLDER);
    }

    private void syncStayStillFlag() {
        this.entityData.set(HappyGhast.STAYS_STILL, this.serverStillTimeout > 0);
    }

    public boolean staysStill() {
        return (Boolean) this.entityData.get(HappyGhast.STAYS_STILL);
    }

    @Override
    public boolean supportQuadLeashAsHolder() {
        return true;
    }

    @Override
    public Vec3[] getQuadLeashHolderOffsets() {
        return Leashable.createQuadLeashOffsets(this, -0.03125D, 0.4375D, 0.46875D, 0.03125D);
    }

    @Override
    public Vec3 getLeashOffset() {
        return Vec3.ZERO;
    }

    @Override
    public double leashElasticDistance() {
        return 10.0D;
    }

    @Override
    public double leashSnapDistance() {
        return 16.0D;
    }

    @Override
    public void onElasticLeashPull() {
        super.onElasticLeashPull();
        this.getMoveControl().setWait();
    }

    @Override
    public void notifyLeashHolder(Leashable entity) {
        if (entity.supportQuadLeash()) {
            this.leashHolderTime = 5;
        }

    }

    @Override
    public void addAdditionalSaveData(ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("still_timeout", this.serverStillTimeout);
    }

    @Override
    public void readAdditionalSaveData(ValueInput tag) {
        super.readAdditionalSaveData(tag);
        this.setServerStillTimeout(tag.getIntOr("still_timeout", 0));
    }

    public boolean isOnStillTimeout() {
        return this.staysStill() || this.serverStillTimeout > 0;
    }

    private boolean scanPlayerAboveGhast() {
        AABB aabb = this.getBoundingBox();
        AABB aabb1 = new AABB(aabb.minX - 1.0D, aabb.maxY - (double) 1.0E-5F, aabb.minZ - 1.0D, aabb.maxX + 1.0D, aabb.maxY + aabb.getYsize() / 2.0D, aabb.maxZ + 1.0D);

        for (Player player : this.level().players()) {
            if (!player.isSpectator()) {
                Entity entity = player.getRootVehicle();

                if (!(entity instanceof HappyGhast) && aabb1.contains(entity.position())) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new HappyGhast.HappyGhastBodyRotationControl();
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return !this.isBaby() && this.isAlive() ? (this.level().isClientSide() && other instanceof Player && other.position().y >= this.getBoundingBox().maxY ? true : (this.isVehicle() && other instanceof HappyGhast ? true : this.isOnStillTimeout())) : false;
    }

    @Override
    public boolean isFlyingVehicle() {
        return !this.isBaby();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return new Vec3(this.getX(), this.getBoundingBox().maxY, this.getZ());
    }

    private static class BabyFlyingPathNavigation extends FlyingPathNavigation {

        public BabyFlyingPathNavigation(HappyGhast mob, Level level) {
            super(mob, level);
            this.setCanOpenDoors(false);
            this.setCanFloat(true);
            this.setRequiredPathLength(48.0F);
        }

        @Override
        protected boolean canMoveDirectly(Vec3 startPos, Vec3 stopPos) {
            return isClearForMovementBetween(this.mob, startPos, stopPos, false);
        }
    }

    private class HappyGhastFloatGoal extends FloatGoal {

        public HappyGhastFloatGoal() {
            super(HappyGhast.this);
        }

        @Override
        public boolean canUse() {
            return !HappyGhast.this.isOnStillTimeout() && super.canUse();
        }
    }

    private class HappyGhastLookControl extends LookControl {

        private HappyGhastLookControl() {
            super(HappyGhast.this);
        }

        @Override
        public void tick() {
            if (HappyGhast.this.isOnStillTimeout()) {
                float f = wrapDegrees90(HappyGhast.this.getYRot());

                HappyGhast.this.setYRot(HappyGhast.this.getYRot() - f);
                HappyGhast.this.setYHeadRot(HappyGhast.this.getYRot());
            } else if (this.lookAtCooldown > 0) {
                --this.lookAtCooldown;
                double d0 = this.wantedX - HappyGhast.this.getX();
                double d1 = this.wantedZ - HappyGhast.this.getZ();

                HappyGhast.this.setYRot(-((float) Mth.atan2(d0, d1)) * (180F / (float) Math.PI));
                HappyGhast.this.yBodyRot = HappyGhast.this.getYRot();
                HappyGhast.this.yHeadRot = HappyGhast.this.yBodyRot;
            } else {
                Ghast.faceMovementDirection(this.mob);
            }
        }

        public static float wrapDegrees90(float angle) {
            float f1 = angle % 90.0F;

            if (f1 >= 45.0F) {
                f1 -= 90.0F;
            }

            if (f1 < -45.0F) {
                f1 += 90.0F;
            }

            return f1;
        }
    }

    private class HappyGhastBodyRotationControl extends BodyRotationControl {

        public HappyGhastBodyRotationControl() {
            super(HappyGhast.this);
        }

        @Override
        public void clientTick() {
            if (HappyGhast.this.isVehicle()) {
                HappyGhast.this.yHeadRot = HappyGhast.this.getYRot();
                HappyGhast.this.yBodyRot = HappyGhast.this.yHeadRot;
            }

            super.clientTick();
        }
    }
}

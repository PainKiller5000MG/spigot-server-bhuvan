package net.minecraft.world.entity.animal.dolphin;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreathAirGoal;
import net.minecraft.world.entity.ai.goal.DolphinJumpGoal;
import net.minecraft.world.entity.ai.goal.FollowBoatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.TryFindWaterGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.AgeableWaterCreature;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Dolphin extends AgeableWaterCreature {

    private static final EntityDataAccessor<Boolean> GOT_FISH = SynchedEntityData.<Boolean>defineId(Dolphin.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> MOISTNESS_LEVEL = SynchedEntityData.<Integer>defineId(Dolphin.class, EntityDataSerializers.INT);
    private static final TargetingConditions SWIM_WITH_PLAYER_TARGETING = TargetingConditions.forNonCombat().range(10.0D).ignoreLineOfSight();
    public static final int TOTAL_AIR_SUPPLY = 4800;
    private static final int TOTAL_MOISTNESS_LEVEL = 2400;
    public static final Predicate<ItemEntity> ALLOWED_ITEMS = (itementity) -> {
        return !itementity.hasPickUpDelay() && itementity.isAlive() && itementity.isInWater();
    };
    public static final float BABY_SCALE = 0.65F;
    private static final boolean DEFAULT_GOT_FISH = false;
    private @Nullable BlockPos treasurePos;

    public Dolphin(EntityType<? extends Dolphin> type, Level level) {
        super(type, level);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
        this.setCanPickUpLoot(true);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        this.setAirSupply(this.getMaxAirSupply());
        this.setXRot(0.0F);
        SpawnGroupData spawngroupdata1 = (SpawnGroupData) Objects.requireNonNullElseGet(groupData, () -> {
            return new AgeableMob.AgeableMobGroupData(0.1F);
        });

        return super.finalizeSpawn(level, difficulty, spawnReason, spawngroupdata1);
    }

    @Override
    public @Nullable Dolphin getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return EntityType.DOLPHIN.create(level, EntitySpawnReason.BREEDING);
    }

    @Override
    public float getAgeScale() {
        return this.isBaby() ? 0.65F : 1.0F;
    }

    @Override
    protected void handleAirSupply(int preTickAirSupply) {}

    public boolean gotFish() {
        return (Boolean) this.entityData.get(Dolphin.GOT_FISH);
    }

    public void setGotFish(boolean gotFish) {
        this.entityData.set(Dolphin.GOT_FISH, gotFish);
    }

    public int getMoistnessLevel() {
        return (Integer) this.entityData.get(Dolphin.MOISTNESS_LEVEL);
    }

    public void setMoisntessLevel(int level) {
        this.entityData.set(Dolphin.MOISTNESS_LEVEL, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Dolphin.GOT_FISH, false);
        entityData.define(Dolphin.MOISTNESS_LEVEL, 2400);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("GotFish", this.gotFish());
        output.putInt("Moistness", this.getMoistnessLevel());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setGotFish(input.getBooleanOr("GotFish", false));
        this.setMoisntessLevel(input.getIntOr("Moistness", 2400));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new BreathAirGoal(this));
        this.goalSelector.addGoal(0, new TryFindWaterGoal(this));
        this.goalSelector.addGoal(1, new Dolphin.DolphinSwimToTreasureGoal(this));
        this.goalSelector.addGoal(2, new Dolphin.DolphinSwimWithPlayerGoal(this, 4.0D));
        this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0D, 10));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(5, new DolphinJumpGoal(this, 10));
        this.goalSelector.addGoal(6, new MeleeAttackGoal(this, (double) 1.2F, true));
        this.goalSelector.addGoal(8, new Dolphin.PlayWithItemsGoal());
        this.goalSelector.addGoal(8, new FollowBoatGoal(this));
        this.goalSelector.addGoal(9, new AvoidEntityGoal(this, Guardian.class, 8.0F, 1.0D, 1.0D));
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this, new Class[]{Guardian.class})).setAlertOthers());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0D).add(Attributes.MOVEMENT_SPEED, (double) 1.2F).add(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    public void playAttackSound() {
        this.playSound(SoundEvents.DOLPHIN_ATTACK, 1.0F, 1.0F);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isBaby() && super.canAttack(target);
    }

    @Override
    public int getMaxAirSupply() {
        return 4800;
    }

    @Override
    protected int increaseAirSupply(int currentSupply) {
        return this.getMaxAirSupply();
    }

    @Override
    public int getMaxHeadXRot() {
        return 1;
    }

    @Override
    public int getMaxHeadYRot() {
        return 1;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return true;
    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND && this.canPickUpLoot();
    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity entity) {
        if (this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
            ItemStack itemstack = entity.getItem();

            if (this.canHoldItem(itemstack)) {
                this.onItemPickup(entity);
                this.setItemSlot(EquipmentSlot.MAINHAND, itemstack);
                this.setGuaranteedDrop(EquipmentSlot.MAINHAND);
                this.take(entity, itemstack.getCount());
                entity.discard();
            }
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.isNoAi()) {
            this.setAirSupply(this.getMaxAirSupply());
        } else {
            if (this.isInWaterOrRain()) {
                this.setMoisntessLevel(2400);
            } else {
                this.setMoisntessLevel(this.getMoistnessLevel() - 1);
                if (this.getMoistnessLevel() <= 0) {
                    this.hurt(this.damageSources().dryOut(), 1.0F);
                }

                if (this.onGround()) {
                    this.setDeltaMovement(this.getDeltaMovement().add((double) ((this.random.nextFloat() * 2.0F - 1.0F) * 0.2F), 0.5D, (double) ((this.random.nextFloat() * 2.0F - 1.0F) * 0.2F)));
                    this.setYRot(this.random.nextFloat() * 360.0F);
                    this.setOnGround(false);
                    this.needsSync = true;
                }
            }

            if (this.level().isClientSide() && this.isInWater() && this.getDeltaMovement().lengthSqr() > 0.03D) {
                Vec3 vec3 = this.getViewVector(0.0F);
                float f = Mth.cos((double) (this.getYRot() * ((float) Math.PI / 180F))) * 0.3F;
                float f1 = Mth.sin((double) (this.getYRot() * ((float) Math.PI / 180F))) * 0.3F;
                float f2 = 1.2F - this.random.nextFloat() * 0.7F;

                for (int i = 0; i < 2; ++i) {
                    this.level().addParticle(ParticleTypes.DOLPHIN, this.getX() - vec3.x * (double) f2 + (double) f, this.getY() - vec3.y, this.getZ() - vec3.z * (double) f2 + (double) f1, 0.0D, 0.0D, 0.0D);
                    this.level().addParticle(ParticleTypes.DOLPHIN, this.getX() - vec3.x * (double) f2 - (double) f, this.getY() - vec3.y, this.getZ() - vec3.z * (double) f2 - (double) f1, 0.0D, 0.0D, 0.0D);
                }
            }

        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 38) {
            this.addParticlesAroundSelf(ParticleTypes.HAPPY_VILLAGER);
        } else {
            super.handleEntityEvent(id);
        }

    }

    private void addParticlesAroundSelf(ParticleOptions particle) {
        for (int i = 0; i < 7; ++i) {
            double d0 = this.random.nextGaussian() * 0.01D;
            double d1 = this.random.nextGaussian() * 0.01D;
            double d2 = this.random.nextGaussian() * 0.01D;

            this.level().addParticle(particle, this.getRandomX(1.0D), this.getRandomY() + 0.2D, this.getRandomZ(1.0D), d0, d1, d2);
        }

    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (!itemstack.isEmpty() && itemstack.is(ItemTags.FISHES)) {
            if (!this.level().isClientSide()) {
                this.playSound(SoundEvents.DOLPHIN_EAT, 1.0F, 1.0F);
            }

            if (this.isBaby()) {
                itemstack.consume(1, player);
                this.ageUp(getSpeedUpSecondsWhenFeeding(-this.age), true);
            } else {
                this.setGotFish(true);
                itemstack.consume(1, player);
            }

            return InteractionResult.SUCCESS;
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.DOLPHIN_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.DOLPHIN_DEATH;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return this.isInWater() ? SoundEvents.DOLPHIN_AMBIENT_WATER : SoundEvents.DOLPHIN_AMBIENT;
    }

    @Override
    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.DOLPHIN_SPLASH;
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.DOLPHIN_SWIM;
    }

    protected boolean closeToNextPos() {
        BlockPos blockpos = this.getNavigation().getTargetPos();

        return blockpos != null ? blockpos.closerToCenterThan(this.position(), 12.0D) : false;
    }

    @Override
    protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
        this.moveRelative(this.getSpeed(), input);
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
        if (this.getTarget() == null) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.005D, 0.0D));
        }

    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    private class PlayWithItemsGoal extends Goal {

        private int cooldown;

        private PlayWithItemsGoal() {}

        @Override
        public boolean canUse() {
            if (this.cooldown > Dolphin.this.tickCount) {
                return false;
            } else {
                List<ItemEntity> list = Dolphin.this.level().<ItemEntity>getEntitiesOfClass(ItemEntity.class, Dolphin.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), Dolphin.ALLOWED_ITEMS);

                return !list.isEmpty() || !Dolphin.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
            }
        }

        @Override
        public void start() {
            List<ItemEntity> list = Dolphin.this.level().<ItemEntity>getEntitiesOfClass(ItemEntity.class, Dolphin.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), Dolphin.ALLOWED_ITEMS);

            if (!list.isEmpty()) {
                Dolphin.this.getNavigation().moveTo((Entity) list.get(0), (double) 1.2F);
                Dolphin.this.playSound(SoundEvents.DOLPHIN_PLAY, 1.0F, 1.0F);
            }

            this.cooldown = 0;
        }

        @Override
        public void stop() {
            ItemStack itemstack = Dolphin.this.getItemBySlot(EquipmentSlot.MAINHAND);

            if (!itemstack.isEmpty()) {
                this.drop(itemstack);
                Dolphin.this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                this.cooldown = Dolphin.this.tickCount + Dolphin.this.random.nextInt(100);
            }

        }

        @Override
        public void tick() {
            List<ItemEntity> list = Dolphin.this.level().<ItemEntity>getEntitiesOfClass(ItemEntity.class, Dolphin.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), Dolphin.ALLOWED_ITEMS);
            ItemStack itemstack = Dolphin.this.getItemBySlot(EquipmentSlot.MAINHAND);

            if (!itemstack.isEmpty()) {
                this.drop(itemstack);
                Dolphin.this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            } else if (!list.isEmpty()) {
                Dolphin.this.getNavigation().moveTo((Entity) list.get(0), (double) 1.2F);
            }

        }

        private void drop(ItemStack itemStack) {
            if (!itemStack.isEmpty()) {
                double d0 = Dolphin.this.getEyeY() - (double) 0.3F;
                ItemEntity itementity = new ItemEntity(Dolphin.this.level(), Dolphin.this.getX(), d0, Dolphin.this.getZ(), itemStack);

                itementity.setPickUpDelay(40);
                itementity.setThrower(Dolphin.this);
                float f = 0.3F;
                float f1 = Dolphin.this.random.nextFloat() * ((float) Math.PI * 2F);
                float f2 = 0.02F * Dolphin.this.random.nextFloat();

                itementity.setDeltaMovement((double) (0.3F * -Mth.sin((double) (Dolphin.this.getYRot() * ((float) Math.PI / 180F))) * Mth.cos((double) (Dolphin.this.getXRot() * ((float) Math.PI / 180F))) + Mth.cos((double) f1) * f2), (double) (0.3F * Mth.sin((double) (Dolphin.this.getXRot() * ((float) Math.PI / 180F))) * 1.5F), (double) (0.3F * Mth.cos((double) (Dolphin.this.getYRot() * ((float) Math.PI / 180F))) * Mth.cos((double) (Dolphin.this.getXRot() * ((float) Math.PI / 180F))) + Mth.sin((double) f1) * f2));
                Dolphin.this.level().addFreshEntity(itementity);
            }
        }
    }

    private static class DolphinSwimWithPlayerGoal extends Goal {

        private final Dolphin dolphin;
        private final double speedModifier;
        private @Nullable Player player;

        DolphinSwimWithPlayerGoal(Dolphin dolphin, double speedModifier) {
            this.dolphin = dolphin;
            this.speedModifier = speedModifier;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            this.player = getServerLevel((Entity) this.dolphin).getNearestPlayer(Dolphin.SWIM_WITH_PLAYER_TARGETING, this.dolphin);
            return this.player == null ? false : this.player.isSwimming() && this.dolphin.getTarget() != this.player;
        }

        @Override
        public boolean canContinueToUse() {
            return this.player != null && this.player.isSwimming() && this.dolphin.distanceToSqr((Entity) this.player) < 256.0D;
        }

        @Override
        public void start() {
            this.player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 100), this.dolphin);
        }

        @Override
        public void stop() {
            this.player = null;
            this.dolphin.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.dolphin.getLookControl().setLookAt(this.player, (float) (this.dolphin.getMaxHeadYRot() + 20), (float) this.dolphin.getMaxHeadXRot());
            if (this.dolphin.distanceToSqr((Entity) this.player) < 6.25D) {
                this.dolphin.getNavigation().stop();
            } else {
                this.dolphin.getNavigation().moveTo((Entity) this.player, this.speedModifier);
            }

            if (this.player.isSwimming() && this.player.level().random.nextInt(6) == 0) {
                this.player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 100), this.dolphin);
            }

        }
    }

    private static class DolphinSwimToTreasureGoal extends Goal {

        private final Dolphin dolphin;
        private boolean stuck;

        DolphinSwimToTreasureGoal(Dolphin dolphin) {
            this.dolphin = dolphin;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }

        @Override
        public boolean canUse() {
            return this.dolphin.gotFish() && this.dolphin.getAirSupply() >= 100;
        }

        @Override
        public boolean canContinueToUse() {
            BlockPos blockpos = this.dolphin.treasurePos;

            return blockpos == null ? false : !BlockPos.containing((double) blockpos.getX(), this.dolphin.getY(), (double) blockpos.getZ()).closerToCenterThan(this.dolphin.position(), 4.0D) && !this.stuck && this.dolphin.getAirSupply() >= 100;
        }

        @Override
        public void start() {
            if (this.dolphin.level() instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) this.dolphin.level();

                this.stuck = false;
                this.dolphin.getNavigation().stop();
                BlockPos blockpos = this.dolphin.blockPosition();
                BlockPos blockpos1 = serverlevel.findNearestMapStructure(StructureTags.DOLPHIN_LOCATED, blockpos, 50, false);

                if (blockpos1 != null) {
                    this.dolphin.treasurePos = blockpos1;
                    serverlevel.broadcastEntityEvent(this.dolphin, (byte) 38);
                } else {
                    this.stuck = true;
                }
            }
        }

        @Override
        public void stop() {
            BlockPos blockpos = this.dolphin.treasurePos;

            if (blockpos == null || BlockPos.containing((double) blockpos.getX(), this.dolphin.getY(), (double) blockpos.getZ()).closerToCenterThan(this.dolphin.position(), 4.0D) || this.stuck) {
                this.dolphin.setGotFish(false);
            }

        }

        @Override
        public void tick() {
            if (this.dolphin.treasurePos != null) {
                Level level = this.dolphin.level();

                if (this.dolphin.closeToNextPos() || this.dolphin.getNavigation().isDone()) {
                    Vec3 vec3 = Vec3.atCenterOf(this.dolphin.treasurePos);
                    Vec3 vec31 = DefaultRandomPos.getPosTowards(this.dolphin, 16, 1, vec3, (double) ((float) Math.PI / 8F));

                    if (vec31 == null) {
                        vec31 = DefaultRandomPos.getPosTowards(this.dolphin, 8, 4, vec3, (double) ((float) Math.PI / 2F));
                    }

                    if (vec31 != null) {
                        BlockPos blockpos = BlockPos.containing(vec31);

                        if (!level.getFluidState(blockpos).is(FluidTags.WATER) || !level.getBlockState(blockpos).isPathfindable(PathComputationType.WATER)) {
                            vec31 = DefaultRandomPos.getPosTowards(this.dolphin, 8, 5, vec3, (double) ((float) Math.PI / 2F));
                        }
                    }

                    if (vec31 == null) {
                        this.stuck = true;
                        return;
                    }

                    this.dolphin.getLookControl().setLookAt(vec31.x, vec31.y, vec31.z, (float) (this.dolphin.getMaxHeadYRot() + 20), (float) this.dolphin.getMaxHeadXRot());
                    this.dolphin.getNavigation().moveTo(vec31.x, vec31.y, vec31.z, 1.3D);
                    if (level.random.nextInt(this.adjustedTickDelay(80)) == 0) {
                        level.broadcastEntityEvent(this.dolphin, (byte) 38);
                    }
                }

            }
        }
    }
}

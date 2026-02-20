package net.minecraft.world.entity.animal.turtle;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Turtle extends Animal {

    private static final EntityDataAccessor<Boolean> HAS_EGG = SynchedEntityData.<Boolean>defineId(Turtle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LAYING_EGG = SynchedEntityData.<Boolean>defineId(Turtle.class, EntityDataSerializers.BOOLEAN);
    private static final float BABY_SCALE = 0.3F;
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.TURTLE.getDimensions().withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, EntityType.TURTLE.getHeight(), -0.25F)).scale(0.3F);
    private static final boolean DEFAULT_HAS_EGG = false;
    private int layEggCounter;
    public static final TargetingConditions.Selector BABY_ON_LAND_SELECTOR = (livingentity, serverlevel) -> {
        return livingentity.isBaby() && !livingentity.isInWater();
    };
    private BlockPos homePos;
    private @Nullable BlockPos travelPos;
    private boolean goingHome;

    public Turtle(EntityType<? extends Turtle> type, Level level) {
        super(type, level);
        this.homePos = BlockPos.ZERO;
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.setPathfindingMalus(PathType.DOOR_IRON_CLOSED, -1.0F);
        this.setPathfindingMalus(PathType.DOOR_WOOD_CLOSED, -1.0F);
        this.setPathfindingMalus(PathType.DOOR_OPEN, -1.0F);
        this.moveControl = new Turtle.TurtleMoveControl(this);
    }

    public void setHomePos(BlockPos pos) {
        this.homePos = pos;
    }

    public boolean hasEgg() {
        return (Boolean) this.entityData.get(Turtle.HAS_EGG);
    }

    private void setHasEgg(boolean onOff) {
        this.entityData.set(Turtle.HAS_EGG, onOff);
    }

    public boolean isLayingEgg() {
        return (Boolean) this.entityData.get(Turtle.LAYING_EGG);
    }

    private void setLayingEgg(boolean on) {
        this.layEggCounter = on ? 1 : 0;
        this.entityData.set(Turtle.LAYING_EGG, on);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Turtle.HAS_EGG, false);
        entityData.define(Turtle.LAYING_EGG, false);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("home_pos", BlockPos.CODEC, this.homePos);
        output.putBoolean("has_egg", this.hasEgg());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setHomePos((BlockPos) input.read("home_pos", BlockPos.CODEC).orElse(this.blockPosition()));
        super.readAdditionalSaveData(input);
        this.setHasEgg(input.getBooleanOr("has_egg", false));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        this.setHomePos(this.blockPosition());
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    public static boolean checkTurtleSpawnRules(EntityType<Turtle> type, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random) {
        return pos.getY() < level.getSeaLevel() + 4 && TurtleEggBlock.onSand(level, pos) && isBrightEnoughToSpawn(level, pos);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new Turtle.TurtlePanicGoal(this, 1.2D));
        this.goalSelector.addGoal(1, new Turtle.TurtleBreedGoal(this, 1.0D));
        this.goalSelector.addGoal(1, new Turtle.TurtleLayEggGoal(this, 1.0D));
        this.goalSelector.addGoal(2, new TemptGoal(this, 1.1D, (itemstack) -> {
            return itemstack.is(ItemTags.TURTLE_FOOD);
        }, false));
        this.goalSelector.addGoal(3, new Turtle.TurtleGoToWaterGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new Turtle.TurtleGoHomeGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new Turtle.TurtleTravelGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new Turtle.TurtleRandomStrollGoal(this, 1.0D, 100));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 30.0D).add(Attributes.MOVEMENT_SPEED, 0.25D).add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return !this.isInWater() && this.onGround() && !this.isBaby() ? SoundEvents.TURTLE_AMBIENT_LAND : super.getAmbientSound();
    }

    @Override
    protected void playSwimSound(float volume) {
        super.playSwimSound(volume * 1.5F);
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.TURTLE_SWIM;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return this.isBaby() ? SoundEvents.TURTLE_HURT_BABY : SoundEvents.TURTLE_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return this.isBaby() ? SoundEvents.TURTLE_DEATH_BABY : SoundEvents.TURTLE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
        SoundEvent soundevent = this.isBaby() ? SoundEvents.TURTLE_SHAMBLE_BABY : SoundEvents.TURTLE_SHAMBLE;

        this.playSound(soundevent, 0.15F, 1.0F);
    }

    @Override
    public boolean canFallInLove() {
        return super.canFallInLove() && !this.hasEgg();
    }

    @Override
    protected float nextStep() {
        return this.moveDist + 0.15F;
    }

    @Override
    public float getAgeScale() {
        return this.isBaby() ? 0.3F : 1.0F;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new Turtle.TurtlePathNavigation(this, level);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return EntityType.TURTLE.create(level, EntitySpawnReason.BREEDING);
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return itemStack.is(ItemTags.TURTLE_FOOD);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return !this.goingHome && level.getFluidState(pos).is(FluidTags.WATER) ? 10.0F : (TurtleEggBlock.onSand(level, pos) ? 10.0F : level.getPathfindingCostFromLightLevels(pos));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.isAlive() && this.isLayingEgg() && this.layEggCounter >= 1 && this.layEggCounter % 5 == 0) {
            BlockPos blockpos = this.blockPosition();

            if (TurtleEggBlock.onSand(this.level(), blockpos)) {
                this.level().levelEvent(2001, blockpos, Block.getId(this.level().getBlockState(blockpos.below())));
                this.gameEvent(GameEvent.ENTITY_ACTION);
            }
        }

    }

    @Override
    protected void ageBoundaryReached() {
        super.ageBoundaryReached();
        if (!this.isBaby()) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                if ((Boolean) serverlevel.getGameRules().get(GameRules.MOB_DROPS)) {
                    this.dropFromGiftLootTable(serverlevel, BuiltInLootTables.TURTLE_GROW, this::spawnAtLocation);
                }
            }
        }

    }

    @Override
    protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
        this.moveRelative(0.1F, input);
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
        if (this.getTarget() == null && (!this.goingHome || !this.homePos.closerToCenterThan(this.position(), 20.0D))) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.005D, 0.0D));
        }

    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightningBolt) {
        this.hurtServer(level, this.damageSources().lightningBolt(), Float.MAX_VALUE);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? Turtle.BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    private static class TurtlePanicGoal extends PanicGoal {

        TurtlePanicGoal(Turtle turtle, double speedModifier) {
            super(turtle, speedModifier);
        }

        @Override
        public boolean canUse() {
            if (!this.shouldPanic()) {
                return false;
            } else {
                BlockPos blockpos = this.lookForWater(this.mob.level(), this.mob, 7);

                if (blockpos != null) {
                    this.posX = (double) blockpos.getX();
                    this.posY = (double) blockpos.getY();
                    this.posZ = (double) blockpos.getZ();
                    return true;
                } else {
                    return this.findRandomPosition();
                }
            }
        }
    }

    private static class TurtleTravelGoal extends Goal {

        private final Turtle turtle;
        private final double speedModifier;
        private boolean stuck;

        TurtleTravelGoal(Turtle turtle, double speedModifier) {
            this.turtle = turtle;
            this.speedModifier = speedModifier;
        }

        @Override
        public boolean canUse() {
            return !this.turtle.goingHome && !this.turtle.hasEgg() && this.turtle.isInWater();
        }

        @Override
        public void start() {
            int i = 512;
            int j = 4;
            RandomSource randomsource = this.turtle.random;
            int k = randomsource.nextInt(1025) - 512;
            int l = randomsource.nextInt(9) - 4;
            int i1 = randomsource.nextInt(1025) - 512;

            if ((double) l + this.turtle.getY() > (double) (this.turtle.level().getSeaLevel() - 1)) {
                l = 0;
            }

            this.turtle.travelPos = BlockPos.containing((double) k + this.turtle.getX(), (double) l + this.turtle.getY(), (double) i1 + this.turtle.getZ());
            this.stuck = false;
        }

        @Override
        public void tick() {
            if (this.turtle.travelPos == null) {
                this.stuck = true;
            } else {
                if (this.turtle.getNavigation().isDone()) {
                    Vec3 vec3 = Vec3.atBottomCenterOf(this.turtle.travelPos);
                    Vec3 vec31 = DefaultRandomPos.getPosTowards(this.turtle, 16, 3, vec3, (double) ((float) Math.PI / 10F));

                    if (vec31 == null) {
                        vec31 = DefaultRandomPos.getPosTowards(this.turtle, 8, 7, vec3, (double) ((float) Math.PI / 2F));
                    }

                    if (vec31 != null) {
                        int i = Mth.floor(vec31.x);
                        int j = Mth.floor(vec31.z);
                        int k = 34;

                        if (!this.turtle.level().hasChunksAt(i - 34, j - 34, i + 34, j + 34)) {
                            vec31 = null;
                        }
                    }

                    if (vec31 == null) {
                        this.stuck = true;
                        return;
                    }

                    this.turtle.getNavigation().moveTo(vec31.x, vec31.y, vec31.z, this.speedModifier);
                }

            }
        }

        @Override
        public boolean canContinueToUse() {
            return !this.turtle.getNavigation().isDone() && !this.stuck && !this.turtle.goingHome && !this.turtle.isInLove() && !this.turtle.hasEgg();
        }

        @Override
        public void stop() {
            this.turtle.travelPos = null;
            super.stop();
        }
    }

    private static class TurtleGoHomeGoal extends Goal {

        private final Turtle turtle;
        private final double speedModifier;
        private boolean stuck;
        private int closeToHomeTryTicks;
        private static final int GIVE_UP_TICKS = 600;

        TurtleGoHomeGoal(Turtle turtle, double speedModifier) {
            this.turtle = turtle;
            this.speedModifier = speedModifier;
        }

        @Override
        public boolean canUse() {
            return this.turtle.isBaby() ? false : (this.turtle.hasEgg() ? true : (this.turtle.getRandom().nextInt(reducedTickDelay(700)) != 0 ? false : !this.turtle.homePos.closerToCenterThan(this.turtle.position(), 64.0D)));
        }

        @Override
        public void start() {
            this.turtle.goingHome = true;
            this.stuck = false;
            this.closeToHomeTryTicks = 0;
        }

        @Override
        public void stop() {
            this.turtle.goingHome = false;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.turtle.homePos.closerToCenterThan(this.turtle.position(), 7.0D) && !this.stuck && this.closeToHomeTryTicks <= this.adjustedTickDelay(600);
        }

        @Override
        public void tick() {
            BlockPos blockpos = this.turtle.homePos;
            boolean flag = blockpos.closerToCenterThan(this.turtle.position(), 16.0D);

            if (flag) {
                ++this.closeToHomeTryTicks;
            }

            if (this.turtle.getNavigation().isDone()) {
                Vec3 vec3 = Vec3.atBottomCenterOf(blockpos);
                Vec3 vec31 = DefaultRandomPos.getPosTowards(this.turtle, 16, 3, vec3, (double) ((float) Math.PI / 10F));

                if (vec31 == null) {
                    vec31 = DefaultRandomPos.getPosTowards(this.turtle, 8, 7, vec3, (double) ((float) Math.PI / 2F));
                }

                if (vec31 != null && !flag && !this.turtle.level().getBlockState(BlockPos.containing(vec31)).is(Blocks.WATER)) {
                    vec31 = DefaultRandomPos.getPosTowards(this.turtle, 16, 5, vec3, (double) ((float) Math.PI / 2F));
                }

                if (vec31 == null) {
                    this.stuck = true;
                    return;
                }

                this.turtle.getNavigation().moveTo(vec31.x, vec31.y, vec31.z, this.speedModifier);
            }

        }
    }

    private static class TurtleBreedGoal extends BreedGoal {

        private final Turtle turtle;

        TurtleBreedGoal(Turtle turtle, double speedModifier) {
            super(turtle, speedModifier);
            this.turtle = turtle;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !this.turtle.hasEgg();
        }

        @Override
        protected void breed() {
            ServerPlayer serverplayer = this.animal.getLoveCause();

            if (serverplayer == null && this.partner.getLoveCause() != null) {
                serverplayer = this.partner.getLoveCause();
            }

            if (serverplayer != null) {
                serverplayer.awardStat(Stats.ANIMALS_BRED);
                CriteriaTriggers.BRED_ANIMALS.trigger(serverplayer, this.animal, this.partner, (AgeableMob) null);
            }

            this.turtle.setHasEgg(true);
            this.animal.setAge(6000);
            this.partner.setAge(6000);
            this.animal.resetLove();
            this.partner.resetLove();
            RandomSource randomsource = this.animal.getRandom();

            if ((Boolean) getServerLevel((Level) this.level).getGameRules().get(GameRules.MOB_DROPS)) {
                this.level.addFreshEntity(new ExperienceOrb(this.level, this.animal.getX(), this.animal.getY(), this.animal.getZ(), randomsource.nextInt(7) + 1));
            }

        }
    }

    private static class TurtleLayEggGoal extends MoveToBlockGoal {

        private final Turtle turtle;

        TurtleLayEggGoal(Turtle turtle, double speedModifier) {
            super(turtle, speedModifier, 16);
            this.turtle = turtle;
        }

        @Override
        public boolean canUse() {
            return this.turtle.hasEgg() && this.turtle.homePos.closerToCenterThan(this.turtle.position(), 9.0D) ? super.canUse() : false;
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && this.turtle.hasEgg() && this.turtle.homePos.closerToCenterThan(this.turtle.position(), 9.0D);
        }

        @Override
        public void tick() {
            super.tick();
            BlockPos blockpos = this.turtle.blockPosition();

            if (!this.turtle.isInWater() && this.isReachedTarget()) {
                if (this.turtle.layEggCounter < 1) {
                    this.turtle.setLayingEgg(true);
                } else if (this.turtle.layEggCounter > this.adjustedTickDelay(200)) {
                    Level level = this.turtle.level();

                    level.playSound((Entity) null, blockpos, SoundEvents.TURTLE_LAY_EGG, SoundSource.BLOCKS, 0.3F, 0.9F + level.random.nextFloat() * 0.2F);
                    BlockPos blockpos1 = this.blockPos.above();
                    BlockState blockstate = (BlockState) Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, this.turtle.random.nextInt(4) + 1);

                    level.setBlock(blockpos1, blockstate, 3);
                    level.gameEvent(GameEvent.BLOCK_PLACE, blockpos1, GameEvent.Context.of(this.turtle, blockstate));
                    this.turtle.setHasEgg(false);
                    this.turtle.setLayingEgg(false);
                    this.turtle.setInLoveTime(600);
                }

                if (this.turtle.isLayingEgg()) {
                    ++this.turtle.layEggCounter;
                }
            }

        }

        @Override
        protected boolean isValidTarget(LevelReader level, BlockPos pos) {
            return !level.isEmptyBlock(pos.above()) ? false : TurtleEggBlock.isSand(level, pos);
        }
    }

    private static class TurtleRandomStrollGoal extends RandomStrollGoal {

        private final Turtle turtle;

        private TurtleRandomStrollGoal(Turtle turtle, double speedModifier, int interval) {
            super(turtle, speedModifier, interval);
            this.turtle = turtle;
        }

        @Override
        public boolean canUse() {
            return !this.mob.isInWater() && !this.turtle.goingHome && !this.turtle.hasEgg() ? super.canUse() : false;
        }
    }

    private static class TurtleGoToWaterGoal extends MoveToBlockGoal {

        private static final int GIVE_UP_TICKS = 1200;
        private final Turtle turtle;

        private TurtleGoToWaterGoal(Turtle turtle, double speedModifier) {
            super(turtle, turtle.isBaby() ? 2.0D : speedModifier, 24);
            this.turtle = turtle;
            this.verticalSearchStart = -1;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.turtle.isInWater() && this.tryTicks <= 1200 && this.isValidTarget(this.turtle.level(), this.blockPos);
        }

        @Override
        public boolean canUse() {
            return this.turtle.isBaby() && !this.turtle.isInWater() ? super.canUse() : (!this.turtle.goingHome && !this.turtle.isInWater() && !this.turtle.hasEgg() ? super.canUse() : false);
        }

        @Override
        public boolean shouldRecalculatePath() {
            return this.tryTicks % 160 == 0;
        }

        @Override
        protected boolean isValidTarget(LevelReader level, BlockPos pos) {
            return level.getBlockState(pos).is(Blocks.WATER);
        }
    }

    private static class TurtleMoveControl extends MoveControl {

        private final Turtle turtle;

        TurtleMoveControl(Turtle turtle) {
            super(turtle);
            this.turtle = turtle;
        }

        private void updateSpeed() {
            if (this.turtle.isInWater()) {
                this.turtle.setDeltaMovement(this.turtle.getDeltaMovement().add(0.0D, 0.005D, 0.0D));
                if (!this.turtle.homePos.closerToCenterThan(this.turtle.position(), 16.0D)) {
                    this.turtle.setSpeed(Math.max(this.turtle.getSpeed() / 2.0F, 0.08F));
                }

                if (this.turtle.isBaby()) {
                    this.turtle.setSpeed(Math.max(this.turtle.getSpeed() / 3.0F, 0.06F));
                }
            } else if (this.turtle.onGround()) {
                this.turtle.setSpeed(Math.max(this.turtle.getSpeed() / 2.0F, 0.06F));
            }

        }

        @Override
        public void tick() {
            this.updateSpeed();
            if (this.operation == MoveControl.Operation.MOVE_TO && !this.turtle.getNavigation().isDone()) {
                double d0 = this.wantedX - this.turtle.getX();
                double d1 = this.wantedY - this.turtle.getY();
                double d2 = this.wantedZ - this.turtle.getZ();
                double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);

                if (d3 < (double) 1.0E-5F) {
                    this.mob.setSpeed(0.0F);
                } else {
                    d1 /= d3;
                    float f = (float) (Mth.atan2(d2, d0) * (double) (180F / (float) Math.PI)) - 90.0F;

                    this.turtle.setYRot(this.rotlerp(this.turtle.getYRot(), f, 90.0F));
                    this.turtle.yBodyRot = this.turtle.getYRot();
                    float f1 = (float) (this.speedModifier * this.turtle.getAttributeValue(Attributes.MOVEMENT_SPEED));

                    this.turtle.setSpeed(Mth.lerp(0.125F, this.turtle.getSpeed(), f1));
                    this.turtle.setDeltaMovement(this.turtle.getDeltaMovement().add(0.0D, (double) this.turtle.getSpeed() * d1 * 0.1D, 0.0D));
                }
            } else {
                this.turtle.setSpeed(0.0F);
            }
        }
    }

    private static class TurtlePathNavigation extends AmphibiousPathNavigation {

        TurtlePathNavigation(Turtle mob, Level level) {
            super(mob, level);
        }

        @Override
        public boolean isStableDestination(BlockPos pos) {
            Mob mob = this.mob;

            if (mob instanceof Turtle turtle) {
                if (turtle.travelPos != null) {
                    return this.level.getBlockState(pos).is(Blocks.WATER);
                }
            }

            return !this.level.getBlockState(pos.below()).isAir();
        }
    }
}

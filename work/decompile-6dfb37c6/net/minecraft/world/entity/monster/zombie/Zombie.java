package net.minecraft.world.entity.monster.zombie;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SpecialDates;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.entity.ai.goal.SpearUseGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class Zombie extends Monster {

    private static final Identifier SPEED_MODIFIER_BABY_ID = Identifier.withDefaultNamespace("baby");
    private static final AttributeModifier SPEED_MODIFIER_BABY = new AttributeModifier(Zombie.SPEED_MODIFIER_BABY_ID, 0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    private static final Identifier REINFORCEMENT_CALLER_CHARGE_ID = Identifier.withDefaultNamespace("reinforcement_caller_charge");
    private static final AttributeModifier ZOMBIE_REINFORCEMENT_CALLEE_CHARGE = new AttributeModifier(Identifier.withDefaultNamespace("reinforcement_callee_charge"), (double) -0.05F, AttributeModifier.Operation.ADD_VALUE);
    private static final Identifier LEADER_ZOMBIE_BONUS_ID = Identifier.withDefaultNamespace("leader_zombie_bonus");
    private static final Identifier ZOMBIE_RANDOM_SPAWN_BONUS_ID = Identifier.withDefaultNamespace("zombie_random_spawn_bonus");
    private static final EntityDataAccessor<Boolean> DATA_BABY_ID = SynchedEntityData.<Boolean>defineId(Zombie.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_SPECIAL_TYPE_ID = SynchedEntityData.<Integer>defineId(Zombie.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> DATA_DROWNED_CONVERSION_ID = SynchedEntityData.<Boolean>defineId(Zombie.class, EntityDataSerializers.BOOLEAN);
    public static final float ZOMBIE_LEADER_CHANCE = 0.05F;
    public static final int REINFORCEMENT_ATTEMPTS = 50;
    public static final int REINFORCEMENT_RANGE_MAX = 40;
    public static final int REINFORCEMENT_RANGE_MIN = 7;
    private static final int NOT_CONVERTING = -1;
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.ZOMBIE.getDimensions().scale(0.5F).withEyeHeight(0.93F);
    private static final float BREAK_DOOR_CHANCE = 0.1F;
    private static final Predicate<Difficulty> DOOR_BREAKING_PREDICATE = (difficulty) -> {
        return difficulty == Difficulty.HARD;
    };
    private static final boolean DEFAULT_BABY = false;
    private static final boolean DEFAULT_CAN_BREAK_DOORS = false;
    private static final int DEFAULT_IN_WATER_TIME = 0;
    private final BreakDoorGoal breakDoorGoal;
    private boolean canBreakDoors;
    private int inWaterTime;
    public int conversionTime;

    public Zombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.breakDoorGoal = new BreakDoorGoal(this, Zombie.DOOR_BREAKING_PREDICATE);
        this.canBreakDoors = false;
        this.inWaterTime = 0;
    }

    public Zombie(Level level) {
        this(EntityType.ZOMBIE, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(4, new Zombie.ZombieAttackTurtleEggGoal(this, 1.0D, 3));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.addBehaviourGoals();
    }

    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(2, new SpearUseGoal(this, 1.0D, 1.0D, 10.0F, 2.0F));
        this.goalSelector.addGoal(3, new ZombieAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(6, new MoveThroughVillageGoal(this, 1.0D, true, 4, this::canBreakDoors));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this, new Class[0])).setAlertOthers(ZombifiedPiglin.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, AbstractVillager.class, false));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, IronGolem.class, true));
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal(this, Turtle.class, 10, true, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.FOLLOW_RANGE, 35.0D).add(Attributes.MOVEMENT_SPEED, (double) 0.23F).add(Attributes.ATTACK_DAMAGE, 3.0D).add(Attributes.ARMOR, 2.0D).add(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Zombie.DATA_BABY_ID, false);
        entityData.define(Zombie.DATA_SPECIAL_TYPE_ID, 0);
        entityData.define(Zombie.DATA_DROWNED_CONVERSION_ID, false);
    }

    public boolean isUnderWaterConverting() {
        return (Boolean) this.getEntityData().get(Zombie.DATA_DROWNED_CONVERSION_ID);
    }

    public boolean canBreakDoors() {
        return this.canBreakDoors;
    }

    public void setCanBreakDoors(boolean canBreakDoors) {
        if (this.navigation.canNavigateGround()) {
            if (this.canBreakDoors != canBreakDoors) {
                this.canBreakDoors = canBreakDoors;
                this.navigation.setCanOpenDoors(canBreakDoors);
                if (canBreakDoors) {
                    this.goalSelector.addGoal(1, this.breakDoorGoal);
                } else {
                    this.goalSelector.removeGoal(this.breakDoorGoal);
                }
            }
        } else if (this.canBreakDoors) {
            this.goalSelector.removeGoal(this.breakDoorGoal);
            this.canBreakDoors = false;
        }

    }

    @Override
    public boolean isBaby() {
        return (Boolean) this.getEntityData().get(Zombie.DATA_BABY_ID);
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        if (this.isBaby()) {
            this.xpReward = (int) ((double) this.xpReward * 2.5D);
        }

        return super.getBaseExperienceReward(level);
    }

    @Override
    public void setBaby(boolean baby) {
        this.getEntityData().set(Zombie.DATA_BABY_ID, baby);
        if (this.level() != null && !this.level().isClientSide()) {
            AttributeInstance attributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);

            attributeinstance.removeModifier(Zombie.SPEED_MODIFIER_BABY_ID);
            if (baby) {
                attributeinstance.addTransientModifier(Zombie.SPEED_MODIFIER_BABY);
            }
        }

    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (Zombie.DATA_BABY_ID.equals(accessor)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(accessor);
    }

    protected boolean convertsInWater() {
        return true;
    }

    @Override
    public void tick() {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            if (this.isAlive() && !this.isNoAi()) {
                if (this.isUnderWaterConverting()) {
                    --this.conversionTime;
                    if (this.conversionTime < 0) {
                        this.doUnderWaterConversion(serverlevel);
                    }
                } else if (this.convertsInWater()) {
                    if (this.isEyeInFluid(FluidTags.WATER)) {
                        ++this.inWaterTime;
                        if (this.inWaterTime >= 600) {
                            this.startUnderWaterConversion(300);
                        }
                    } else {
                        this.inWaterTime = -1;
                    }
                }
            }
        }

        super.tick();
    }

    public void startUnderWaterConversion(int time) {
        this.conversionTime = time;
        this.getEntityData().set(Zombie.DATA_DROWNED_CONVERSION_ID, true);
    }

    protected void doUnderWaterConversion(ServerLevel level) {
        this.convertToZombieType(level, EntityType.DROWNED);
        if (!this.isSilent()) {
            level.levelEvent((Entity) null, 1040, this.blockPosition(), 0);
        }

    }

    protected void convertToZombieType(ServerLevel level, EntityType<? extends Zombie> zombieType) {
        this.convertTo(zombieType, ConversionParams.single(this, true, true), (zombie) -> {
            zombie.handleAttributes(level.getCurrentDifficultyAt(zombie.blockPosition()).getSpecialMultiplier());
        });
    }

    @VisibleForTesting
    public boolean convertVillagerToZombieVillager(ServerLevel level, Villager villager) {
        ZombieVillager zombievillager = (ZombieVillager) villager.convertTo(EntityType.ZOMBIE_VILLAGER, ConversionParams.single(villager, true, true), (zombievillager1) -> {
            zombievillager1.finalizeSpawn(level, level.getCurrentDifficultyAt(zombievillager1.blockPosition()), EntitySpawnReason.CONVERSION, new Zombie.ZombieGroupData(false, true));
            zombievillager1.setVillagerData(villager.getVillagerData());
            zombievillager1.setGossips(villager.getGossips().copy());
            zombievillager1.setTradeOffers(villager.getOffers().copy());
            zombievillager1.setVillagerXp(villager.getVillagerXp());
            if (!this.isSilent()) {
                level.levelEvent((Entity) null, 1026, this.blockPosition(), 0);
            }

        });

        return zombievillager != null;
    }

    protected boolean isSunSensitive() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (!super.hurtServer(level, source, damage)) {
            return false;
        } else {
            LivingEntity livingentity = this.getTarget();

            if (livingentity == null && source.getEntity() instanceof LivingEntity) {
                livingentity = (LivingEntity) source.getEntity();
            }

            if (livingentity != null && level.getDifficulty() == Difficulty.HARD && (double) this.random.nextFloat() < this.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE) && level.isSpawningMonsters()) {
                int i = Mth.floor(this.getX());
                int j = Mth.floor(this.getY());
                int k = Mth.floor(this.getZ());
                EntityType<? extends Zombie> entitytype = this.getType();
                Zombie zombie = entitytype.create(level, EntitySpawnReason.REINFORCEMENT);

                if (zombie == null) {
                    return true;
                }

                for (int l = 0; l < 50; ++l) {
                    int i1 = i + Mth.nextInt(this.random, 7, 40) * Mth.nextInt(this.random, -1, 1);
                    int j1 = j + Mth.nextInt(this.random, 7, 40) * Mth.nextInt(this.random, -1, 1);
                    int k1 = k + Mth.nextInt(this.random, 7, 40) * Mth.nextInt(this.random, -1, 1);
                    BlockPos blockpos = new BlockPos(i1, j1, k1);

                    if (SpawnPlacements.isSpawnPositionOk(entitytype, level, blockpos) && SpawnPlacements.checkSpawnRules(entitytype, level, EntitySpawnReason.REINFORCEMENT, blockpos, level.random)) {
                        zombie.setPos((double) i1, (double) j1, (double) k1);
                        if (!level.hasNearbyAlivePlayer((double) i1, (double) j1, (double) k1, 7.0D) && level.isUnobstructed(zombie) && level.noCollision((Entity) zombie) && (zombie.canSpawnInLiquids() || !level.containsAnyLiquid(zombie.getBoundingBox()))) {
                            zombie.setTarget(livingentity);
                            zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(zombie.blockPosition()), EntitySpawnReason.REINFORCEMENT, (SpawnGroupData) null);
                            level.addFreshEntityWithPassengers(zombie);
                            AttributeInstance attributeinstance = this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
                            AttributeModifier attributemodifier = attributeinstance.getModifier(Zombie.REINFORCEMENT_CALLER_CHARGE_ID);
                            double d0 = attributemodifier != null ? attributemodifier.amount() : 0.0D;

                            attributeinstance.removeModifier(Zombie.REINFORCEMENT_CALLER_CHARGE_ID);
                            attributeinstance.addPermanentModifier(new AttributeModifier(Zombie.REINFORCEMENT_CALLER_CHARGE_ID, d0 - 0.05D, AttributeModifier.Operation.ADD_VALUE));
                            zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).addPermanentModifier(Zombie.ZOMBIE_REINFORCEMENT_CALLEE_CHARGE);
                            break;
                        }
                    }
                }
            }

            return true;
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean flag = super.doHurtTarget(level, target);

        if (flag) {
            float f = level.getCurrentDifficultyAt(this.blockPosition()).getEffectiveDifficulty();

            if (this.getMainHandItem().isEmpty() && this.isOnFire() && this.random.nextFloat() < f * 0.3F) {
                target.igniteForSeconds((float) (2 * (int) f));
            }
        }

        return flag;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_DEATH;
    }

    protected SoundEvent getStepSound() {
        return SoundEvents.ZOMBIE_STEP;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
        this.playSound(this.getStepSound(), 0.15F, 1.0F);
    }

    @Override
    public EntityType<? extends Zombie> getType() {
        return super.getType();
    }

    protected boolean canSpawnInLiquids() {
        return false;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        super.populateDefaultEquipmentSlots(random, difficulty);
        if (random.nextFloat() < (this.level().getDifficulty() == Difficulty.HARD ? 0.05F : 0.01F)) {
            int i = random.nextInt(6);

            if (i == 0) {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            } else if (i == 1) {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
            } else {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
            }
        }

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("IsBaby", this.isBaby());
        output.putBoolean("CanBreakDoors", this.canBreakDoors());
        output.putInt("InWaterTime", this.isInWater() ? this.inWaterTime : -1);
        output.putInt("DrownedConversionTime", this.isUnderWaterConverting() ? this.conversionTime : -1);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setBaby(input.getBooleanOr("IsBaby", false));
        this.setCanBreakDoors(input.getBooleanOr("CanBreakDoors", false));
        this.inWaterTime = input.getIntOr("InWaterTime", 0);
        int i = input.getIntOr("DrownedConversionTime", -1);

        if (i != -1) {
            this.startUnderWaterConversion(i);
        } else {
            this.getEntityData().set(Zombie.DATA_DROWNED_CONVERSION_ID, false);
        }

    }

    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity entity, DamageSource source) {
        boolean flag = super.killedEntity(level, entity, source);

        if ((level.getDifficulty() == Difficulty.NORMAL || level.getDifficulty() == Difficulty.HARD) && entity instanceof Villager villager) {
            if (level.getDifficulty() != Difficulty.HARD && this.random.nextBoolean()) {
                return flag;
            }

            if (this.convertVillagerToZombieVillager(level, villager)) {
                flag = false;
            }
        }

        return flag;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? Zombie.BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    @Override
    public boolean canHoldItem(ItemStack itemStack) {
        return itemStack.is(ItemTags.EGGS) && this.isBaby() && this.isPassenger() ? false : super.canHoldItem(itemStack);
    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack itemStack) {
        return itemStack.is(Items.GLOW_INK_SAC) ? false : super.wantsToPickUp(level, itemStack);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        RandomSource randomsource = level.getRandom();

        groupData = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        float f = difficulty.getSpecialMultiplier();

        if (spawnReason != EntitySpawnReason.CONVERSION) {
            this.setCanPickUpLoot(randomsource.nextFloat() < 0.55F * f);
        }

        if (groupData == null) {
            groupData = new Zombie.ZombieGroupData(getSpawnAsBabyOdds(randomsource), true);
        }

        if (groupData instanceof Zombie.ZombieGroupData zombie_zombiegroupdata) {
            if (zombie_zombiegroupdata.isBaby) {
                this.setBaby(true);
                if (zombie_zombiegroupdata.canSpawnJockey) {
                    if ((double) randomsource.nextFloat() < 0.05D) {
                        List<Chicken> list = level.<Chicken>getEntitiesOfClass(Chicken.class, this.getBoundingBox().inflate(5.0D, 3.0D, 5.0D), EntitySelector.ENTITY_NOT_BEING_RIDDEN);

                        if (!list.isEmpty()) {
                            Chicken chicken = (Chicken) list.get(0);

                            chicken.setChickenJockey(true);
                            this.startRiding(chicken, false, false);
                        }
                    } else if ((double) randomsource.nextFloat() < 0.05D) {
                        Chicken chicken1 = EntityType.CHICKEN.create(this.level(), EntitySpawnReason.JOCKEY);

                        if (chicken1 != null) {
                            chicken1.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                            chicken1.finalizeSpawn(level, difficulty, EntitySpawnReason.JOCKEY, (SpawnGroupData) null);
                            chicken1.setChickenJockey(true);
                            this.startRiding(chicken1, false, false);
                            level.addFreshEntity(chicken1);
                        }
                    }
                }
            }

            this.setCanBreakDoors(randomsource.nextFloat() < f * 0.1F);
            if (spawnReason != EntitySpawnReason.CONVERSION) {
                this.populateDefaultEquipmentSlots(randomsource, difficulty);
                this.populateDefaultEquipmentEnchantments(level, randomsource, difficulty);
            }
        }

        if (this.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && SpecialDates.isHalloween() && randomsource.nextFloat() < 0.25F) {
            this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(randomsource.nextFloat() < 0.1F ? Blocks.JACK_O_LANTERN : Blocks.CARVED_PUMPKIN));
            this.setDropChance(EquipmentSlot.HEAD, 0.0F);
        }

        this.handleAttributes(f);
        return groupData;
    }

    @VisibleForTesting
    public void setInWaterTime(int inWaterTime) {
        this.inWaterTime = inWaterTime;
    }

    @VisibleForTesting
    public void setConversionTime(int conversionTime) {
        this.conversionTime = conversionTime;
    }

    public static boolean getSpawnAsBabyOdds(RandomSource random) {
        return random.nextFloat() < 0.05F;
    }

    protected void handleAttributes(float difficultyModifier) {
        this.randomizeReinforcementsChance();
        this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).addOrReplacePermanentModifier(new AttributeModifier(Zombie.RANDOM_SPAWN_BONUS_ID, this.random.nextDouble() * (double) 0.05F, AttributeModifier.Operation.ADD_VALUE));
        double d0 = this.random.nextDouble() * 1.5D * (double) difficultyModifier;

        if (d0 > 1.0D) {
            this.getAttribute(Attributes.FOLLOW_RANGE).addOrReplacePermanentModifier(new AttributeModifier(Zombie.ZOMBIE_RANDOM_SPAWN_BONUS_ID, d0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        if (this.random.nextFloat() < difficultyModifier * 0.05F) {
            this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).addOrReplacePermanentModifier(new AttributeModifier(Zombie.LEADER_ZOMBIE_BONUS_ID, this.random.nextDouble() * 0.25D + 0.5D, AttributeModifier.Operation.ADD_VALUE));
            this.getAttribute(Attributes.MAX_HEALTH).addOrReplacePermanentModifier(new AttributeModifier(Zombie.LEADER_ZOMBIE_BONUS_ID, this.random.nextDouble() * 3.0D + 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            this.setCanBreakDoors(true);
        }

    }

    protected void randomizeReinforcementsChance() {
        this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(this.random.nextDouble() * (double) 0.1F);
    }

    public static class ZombieGroupData implements SpawnGroupData {

        public final boolean isBaby;
        public final boolean canSpawnJockey;

        public ZombieGroupData(boolean baby, boolean canSpawnJockey) {
            this.isBaby = baby;
            this.canSpawnJockey = canSpawnJockey;
        }
    }

    private class ZombieAttackTurtleEggGoal extends RemoveBlockGoal {

        ZombieAttackTurtleEggGoal(PathfinderMob mob, double speedModifier, int verticalSearchRange) {
            super(Blocks.TURTLE_EGG, mob, speedModifier, verticalSearchRange);
        }

        @Override
        public void playDestroyProgressSound(LevelAccessor level, BlockPos pos) {
            level.playSound((Entity) null, pos, SoundEvents.ZOMBIE_DESTROY_EGG, SoundSource.HOSTILE, 0.5F, 0.9F + Zombie.this.random.nextFloat() * 0.2F);
        }

        @Override
        public void playBreakSound(Level level, BlockPos pos) {
            level.playSound((Entity) null, pos, SoundEvents.TURTLE_EGG_BREAK, SoundSource.BLOCKS, 0.7F, 0.9F + level.random.nextFloat() * 0.2F);
        }

        @Override
        public double acceptedDistance() {
            return 1.14D;
        }
    }
}

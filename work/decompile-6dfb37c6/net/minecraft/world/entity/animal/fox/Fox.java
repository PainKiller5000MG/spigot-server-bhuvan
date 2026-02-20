package net.minecraft.world.entity.animal.fox;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.ClimbOnTopOfPowderSnowGoal;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.JumpGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.StrollThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.fish.AbstractSchoolingFish;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Fox extends Animal {

    private static final EntityDataAccessor<Integer> DATA_TYPE_ID = SynchedEntityData.<Integer>defineId(Fox.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.<Byte>defineId(Fox.class, EntityDataSerializers.BYTE);
    private static final int FLAG_SITTING = 1;
    public static final int FLAG_CROUCHING = 4;
    public static final int FLAG_INTERESTED = 8;
    public static final int FLAG_POUNCING = 16;
    private static final int FLAG_SLEEPING = 32;
    private static final int FLAG_FACEPLANTED = 64;
    private static final int FLAG_DEFENDING = 128;
    public static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_TRUSTED_ID_0 = SynchedEntityData.<Optional<EntityReference<LivingEntity>>>defineId(Fox.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
    public static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_TRUSTED_ID_1 = SynchedEntityData.<Optional<EntityReference<LivingEntity>>>defineId(Fox.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
    private static final Predicate<ItemEntity> ALLOWED_ITEMS = (itementity) -> {
        return !itementity.hasPickUpDelay() && itementity.isAlive();
    };
    private static final Predicate<Entity> TRUSTED_TARGET_SELECTOR = (entity) -> {
        if (!(entity instanceof LivingEntity livingentity)) {
            return false;
        } else {
            return livingentity.getLastHurtMob() != null && livingentity.getLastHurtMobTimestamp() < livingentity.tickCount + 600;
        }
    };
    private static final Predicate<Entity> STALKABLE_PREY = (entity) -> {
        return entity instanceof Chicken || entity instanceof Rabbit;
    };
    private static final Predicate<Entity> AVOID_PLAYERS = (entity) -> {
        return !entity.isDiscrete() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity);
    };
    private static final int MIN_TICKS_BEFORE_EAT = 600;
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.FOX.getDimensions().scale(0.5F).withEyeHeight(0.2975F);
    private static final Codec<List<EntityReference<LivingEntity>>> TRUSTED_LIST_CODEC = EntityReference.codec().listOf();
    private static final boolean DEFAULT_SLEEPING = false;
    private static final boolean DEFAULT_SITTING = false;
    private static final boolean DEFAULT_CROUCHING = false;
    private Goal landTargetGoal;
    private Goal turtleEggTargetGoal;
    private Goal fishTargetGoal;
    private float interestedAngle;
    private float interestedAngleO;
    private float crouchAmount;
    private float crouchAmountO;
    private int ticksSinceEaten;

    public Fox(EntityType<? extends Fox> type, Level level) {
        super(type, level);
        this.lookControl = new Fox.FoxLookControl();
        this.moveControl = new Fox.FoxMoveControl();
        this.setPathfindingMalus(PathType.DANGER_OTHER, 0.0F);
        this.setPathfindingMalus(PathType.DAMAGE_OTHER, 0.0F);
        this.setCanPickUpLoot(true);
        this.getNavigation().setRequiredPathLength(32.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Fox.DATA_TRUSTED_ID_0, Optional.empty());
        entityData.define(Fox.DATA_TRUSTED_ID_1, Optional.empty());
        entityData.define(Fox.DATA_TYPE_ID, Fox.Variant.DEFAULT.getId());
        entityData.define(Fox.DATA_FLAGS_ID, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        this.landTargetGoal = new NearestAttackableTargetGoal(this, Animal.class, 10, false, false, (livingentity, serverlevel) -> {
            return livingentity instanceof Chicken || livingentity instanceof Rabbit;
        });
        this.turtleEggTargetGoal = new NearestAttackableTargetGoal(this, Turtle.class, 10, false, false, Turtle.BABY_ON_LAND_SELECTOR);
        this.fishTargetGoal = new NearestAttackableTargetGoal(this, AbstractFish.class, 20, false, false, (livingentity, serverlevel) -> {
            return livingentity instanceof AbstractSchoolingFish;
        });
        this.goalSelector.addGoal(0, new Fox.FoxFloatGoal());
        this.goalSelector.addGoal(0, new ClimbOnTopOfPowderSnowGoal(this, this.level()));
        this.goalSelector.addGoal(1, new Fox.FaceplantGoal());
        this.goalSelector.addGoal(2, new Fox.FoxPanicGoal(2.2D));
        this.goalSelector.addGoal(3, new Fox.FoxBreedGoal(1.0D));
        this.goalSelector.addGoal(4, new AvoidEntityGoal(this, Player.class, 16.0F, 1.6D, 1.4D, (livingentity) -> {
            return Fox.AVOID_PLAYERS.test(livingentity) && !this.trusts(livingentity) && !this.isDefending();
        }));
        this.goalSelector.addGoal(4, new AvoidEntityGoal(this, Wolf.class, 8.0F, 1.6D, 1.4D, (livingentity) -> {
            return !((Wolf) livingentity).isTame() && !this.isDefending();
        }));
        this.goalSelector.addGoal(4, new AvoidEntityGoal(this, PolarBear.class, 8.0F, 1.6D, 1.4D, (livingentity) -> {
            return !this.isDefending();
        }));
        this.goalSelector.addGoal(5, new Fox.StalkPreyGoal());
        this.goalSelector.addGoal(6, new Fox.FoxPounceGoal());
        this.goalSelector.addGoal(6, new Fox.SeekShelterGoal(1.25D));
        this.goalSelector.addGoal(7, new Fox.FoxMeleeAttackGoal((double) 1.2F, true));
        this.goalSelector.addGoal(7, new Fox.SleepGoal());
        this.goalSelector.addGoal(8, new Fox.FoxFollowParentGoal(this, 1.25D));
        this.goalSelector.addGoal(9, new Fox.FoxStrollThroughVillageGoal(32, 200));
        this.goalSelector.addGoal(10, new Fox.FoxEatBerriesGoal((double) 1.2F, 12, 1));
        this.goalSelector.addGoal(10, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(11, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(11, new Fox.FoxSearchForItemsGoal());
        this.goalSelector.addGoal(12, new Fox.FoxLookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(13, new Fox.PerchAndSearchGoal());
        this.targetSelector.addGoal(3, new Fox.DefendTrustedTargetGoal(LivingEntity.class, false, false, (livingentity, serverlevel) -> {
            return Fox.TRUSTED_TARGET_SELECTOR.test(livingentity) && !this.trusts(livingentity);
        }));
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide() && this.isAlive() && this.isEffectiveAi()) {
            ++this.ticksSinceEaten;
            ItemStack itemstack = this.getItemBySlot(EquipmentSlot.MAINHAND);

            if (this.canEat(itemstack)) {
                if (this.ticksSinceEaten > 600) {
                    ItemStack itemstack1 = itemstack.finishUsingItem(this.level(), this);

                    if (!itemstack1.isEmpty()) {
                        this.setItemSlot(EquipmentSlot.MAINHAND, itemstack1);
                    }

                    this.ticksSinceEaten = 0;
                } else if (this.ticksSinceEaten > 560 && this.random.nextFloat() < 0.1F) {
                    this.playEatingSound();
                    this.level().broadcastEntityEvent(this, (byte) 45);
                }
            }

            LivingEntity livingentity = this.getTarget();

            if (livingentity == null || !livingentity.isAlive()) {
                this.setIsCrouching(false);
                this.setIsInterested(false);
            }
        }

        if (this.isSleeping() || this.isImmobile()) {
            this.jumping = false;
            this.xxa = 0.0F;
            this.zza = 0.0F;
        }

        super.aiStep();
        if (this.isDefending() && this.random.nextFloat() < 0.05F) {
            this.playSound(SoundEvents.FOX_AGGRO, 1.0F, 1.0F);
        }

    }

    @Override
    protected boolean isImmobile() {
        return this.isDeadOrDying();
    }

    private boolean canEat(ItemStack itemInMouth) {
        return this.isConsumableFood(itemInMouth) && this.getTarget() == null && this.onGround() && !this.isSleeping();
    }

    private boolean isConsumableFood(ItemStack itemStack) {
        return itemStack.has(DataComponents.FOOD) && itemStack.has(DataComponents.CONSUMABLE);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        if (random.nextFloat() < 0.2F) {
            float f = random.nextFloat();
            ItemStack itemstack;

            if (f < 0.05F) {
                itemstack = new ItemStack(Items.EMERALD);
            } else if (f < 0.2F) {
                itemstack = new ItemStack(Items.EGG);
            } else if (f < 0.4F) {
                itemstack = random.nextBoolean() ? new ItemStack(Items.RABBIT_FOOT) : new ItemStack(Items.RABBIT_HIDE);
            } else if (f < 0.6F) {
                itemstack = new ItemStack(Items.WHEAT);
            } else if (f < 0.8F) {
                itemstack = new ItemStack(Items.LEATHER);
            } else {
                itemstack = new ItemStack(Items.FEATHER);
            }

            this.setItemSlot(EquipmentSlot.MAINHAND, itemstack);
        }

    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 45) {
            ItemStack itemstack = this.getItemBySlot(EquipmentSlot.MAINHAND);

            if (!itemstack.isEmpty()) {
                for (int i = 0; i < 8; ++i) {
                    Vec3 vec3 = (new Vec3(((double) this.random.nextFloat() - 0.5D) * 0.1D, (double) this.random.nextFloat() * 0.1D + 0.1D, 0.0D)).xRot(-this.getXRot() * ((float) Math.PI / 180F)).yRot(-this.getYRot() * ((float) Math.PI / 180F));

                    this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, itemstack), this.getX() + this.getLookAngle().x / 2.0D, this.getY(), this.getZ() + this.getLookAngle().z / 2.0D, vec3.x, vec3.y + 0.05D, vec3.z);
                }
            }
        } else {
            super.handleEntityEvent(id);
        }

    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MOVEMENT_SPEED, (double) 0.3F).add(Attributes.MAX_HEALTH, 10.0D).add(Attributes.ATTACK_DAMAGE, 2.0D).add(Attributes.SAFE_FALL_DISTANCE, 5.0D).add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    public @Nullable Fox getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Fox fox = EntityType.FOX.create(level, EntitySpawnReason.BREEDING);

        if (fox != null) {
            fox.setVariant(this.random.nextBoolean() ? this.getVariant() : ((Fox) partner).getVariant());
        }

        return fox;
    }

    public static boolean checkFoxSpawnRules(EntityType<Fox> type, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.FOXES_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        Holder<Biome> holder = level.getBiome(this.blockPosition());
        Fox.Variant fox_variant = Fox.Variant.byBiome(holder);
        boolean flag = false;

        if (groupData instanceof Fox.FoxGroupData fox_foxgroupdata) {
            fox_variant = fox_foxgroupdata.variant;
            if (fox_foxgroupdata.getGroupSize() >= 2) {
                flag = true;
            }
        } else {
            groupData = new Fox.FoxGroupData(fox_variant);
        }

        this.setVariant(fox_variant);
        if (flag) {
            this.setAge(-24000);
        }

        if (level instanceof ServerLevel) {
            this.setTargetGoals();
        }

        this.populateDefaultEquipmentSlots(level.getRandom(), difficulty);
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    private void setTargetGoals() {
        if (this.getVariant() == Fox.Variant.RED) {
            this.targetSelector.addGoal(4, this.landTargetGoal);
            this.targetSelector.addGoal(4, this.turtleEggTargetGoal);
            this.targetSelector.addGoal(6, this.fishTargetGoal);
        } else {
            this.targetSelector.addGoal(4, this.fishTargetGoal);
            this.targetSelector.addGoal(6, this.landTargetGoal);
            this.targetSelector.addGoal(6, this.turtleEggTargetGoal);
        }

    }

    @Override
    protected void playEatingSound() {
        this.playSound(SoundEvents.FOX_EAT, 1.0F, 1.0F);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? Fox.BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    public Fox.Variant getVariant() {
        return Fox.Variant.byId((Integer) this.entityData.get(Fox.DATA_TYPE_ID));
    }

    public void setVariant(Fox.Variant fox_variant) {
        this.entityData.set(Fox.DATA_TYPE_ID, fox_variant.getId());
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        return (T) (type == DataComponents.FOX_VARIANT ? castComponentValue(type, this.getVariant()) : super.get(type));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        this.applyImplicitComponentIfPresent(components, DataComponents.FOX_VARIANT);
        super.applyImplicitComponents(components);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.FOX_VARIANT) {
            this.setVariant((Fox.Variant) castComponentValue(DataComponents.FOX_VARIANT, value));
            return true;
        } else {
            return super.applyImplicitComponent(type, value);
        }
    }

    private Stream<EntityReference<LivingEntity>> getTrustedEntities() {
        return Stream.concat(((Optional) this.entityData.get(Fox.DATA_TRUSTED_ID_0)).stream(), ((Optional) this.entityData.get(Fox.DATA_TRUSTED_ID_1)).stream());
    }

    private void addTrustedEntity(LivingEntity entity) {
        this.addTrustedEntity(EntityReference.of(entity));
    }

    private void addTrustedEntity(EntityReference<LivingEntity> reference) {
        if (((Optional) this.entityData.get(Fox.DATA_TRUSTED_ID_0)).isPresent()) {
            this.entityData.set(Fox.DATA_TRUSTED_ID_1, Optional.of(reference));
        } else {
            this.entityData.set(Fox.DATA_TRUSTED_ID_0, Optional.of(reference));
        }

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("Trusted", Fox.TRUSTED_LIST_CODEC, this.getTrustedEntities().toList());
        output.putBoolean("Sleeping", this.isSleeping());
        output.store("Type", Fox.Variant.CODEC, this.getVariant());
        output.putBoolean("Sitting", this.isSitting());
        output.putBoolean("Crouching", this.isCrouching());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.clearTrusted();
        ((List) input.read("Trusted", Fox.TRUSTED_LIST_CODEC).orElse(List.of())).forEach(this::addTrustedEntity);
        this.setSleeping(input.getBooleanOr("Sleeping", false));
        this.setVariant((Fox.Variant) input.read("Type", Fox.Variant.CODEC).orElse(Fox.Variant.DEFAULT));
        this.setSitting(input.getBooleanOr("Sitting", false));
        this.setIsCrouching(input.getBooleanOr("Crouching", false));
        if (this.level() instanceof ServerLevel) {
            this.setTargetGoals();
        }

    }

    private void clearTrusted() {
        this.entityData.set(Fox.DATA_TRUSTED_ID_0, Optional.empty());
        this.entityData.set(Fox.DATA_TRUSTED_ID_1, Optional.empty());
    }

    public boolean isSitting() {
        return this.getFlag(1);
    }

    public void setSitting(boolean value) {
        this.setFlag(1, value);
    }

    public boolean isFaceplanted() {
        return this.getFlag(64);
    }

    private void setFaceplanted(boolean faceplanted) {
        this.setFlag(64, faceplanted);
    }

    private boolean isDefending() {
        return this.getFlag(128);
    }

    private void setDefending(boolean defending) {
        this.setFlag(128, defending);
    }

    @Override
    public boolean isSleeping() {
        return this.getFlag(32);
    }

    public void setSleeping(boolean sleeping) {
        this.setFlag(32, sleeping);
    }

    private void setFlag(int flag, boolean value) {
        if (value) {
            this.entityData.set(Fox.DATA_FLAGS_ID, (byte) ((Byte) this.entityData.get(Fox.DATA_FLAGS_ID) | flag));
        } else {
            this.entityData.set(Fox.DATA_FLAGS_ID, (byte) ((Byte) this.entityData.get(Fox.DATA_FLAGS_ID) & ~flag));
        }

    }

    private boolean getFlag(int flag) {
        return ((Byte) this.entityData.get(Fox.DATA_FLAGS_ID) & flag) != 0;
    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND && this.canPickUpLoot();
    }

    @Override
    public boolean canHoldItem(ItemStack itemStack) {
        ItemStack itemstack1 = this.getItemBySlot(EquipmentSlot.MAINHAND);

        return itemstack1.isEmpty() || this.ticksSinceEaten > 0 && this.isConsumableFood(itemStack) && !this.isConsumableFood(itemstack1);
    }

    private void spitOutItem(ItemStack itemStack) {
        if (!itemStack.isEmpty() && !this.level().isClientSide()) {
            ItemEntity itementity = new ItemEntity(this.level(), this.getX() + this.getLookAngle().x, this.getY() + 1.0D, this.getZ() + this.getLookAngle().z, itemStack);

            itementity.setPickUpDelay(40);
            itementity.setThrower(this);
            this.playSound(SoundEvents.FOX_SPIT, 1.0F, 1.0F);
            this.level().addFreshEntity(itementity);
        }
    }

    private void dropItemStack(ItemStack itemStack) {
        ItemEntity itementity = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), itemStack);

        this.level().addFreshEntity(itementity);
    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity entity) {
        ItemStack itemstack = entity.getItem();

        if (this.canHoldItem(itemstack)) {
            int i = itemstack.getCount();

            if (i > 1) {
                this.dropItemStack(itemstack.split(i - 1));
            }

            this.spitOutItem(this.getItemBySlot(EquipmentSlot.MAINHAND));
            this.onItemPickup(entity);
            this.setItemSlot(EquipmentSlot.MAINHAND, itemstack.split(1));
            this.setGuaranteedDrop(EquipmentSlot.MAINHAND);
            this.take(entity, itemstack.getCount());
            entity.discard();
            this.ticksSinceEaten = 0;
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.isEffectiveAi()) {
            boolean flag = this.isInWater();

            if (flag || this.getTarget() != null || this.level().isThundering()) {
                this.wakeUp();
            }

            if (flag || this.isSleeping()) {
                this.setSitting(false);
            }

            if (this.isFaceplanted() && this.level().random.nextFloat() < 0.2F) {
                BlockPos blockpos = this.blockPosition();
                BlockState blockstate = this.level().getBlockState(blockpos);

                this.level().levelEvent(2001, blockpos, Block.getId(blockstate));
            }
        }

        this.interestedAngleO = this.interestedAngle;
        if (this.isInterested()) {
            this.interestedAngle += (1.0F - this.interestedAngle) * 0.4F;
        } else {
            this.interestedAngle += (0.0F - this.interestedAngle) * 0.4F;
        }

        this.crouchAmountO = this.crouchAmount;
        if (this.isCrouching()) {
            this.crouchAmount += 0.2F;
            if (this.crouchAmount > 3.0F) {
                this.crouchAmount = 3.0F;
            }
        } else {
            this.crouchAmount = 0.0F;
        }

    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return itemStack.is(ItemTags.FOX_FOOD);
    }

    @Override
    protected void onOffspringSpawnedFromEgg(Player spawner, Mob offspring) {
        ((Fox) offspring).addTrustedEntity(spawner);
    }

    public boolean isPouncing() {
        return this.getFlag(16);
    }

    public void setIsPouncing(boolean pouncing) {
        this.setFlag(16, pouncing);
    }

    public boolean isFullyCrouched() {
        return this.crouchAmount == 3.0F;
    }

    public void setIsCrouching(boolean isCrouching) {
        this.setFlag(4, isCrouching);
    }

    @Override
    public boolean isCrouching() {
        return this.getFlag(4);
    }

    public void setIsInterested(boolean value) {
        this.setFlag(8, value);
    }

    public boolean isInterested() {
        return this.getFlag(8);
    }

    public float getHeadRollAngle(float a) {
        return Mth.lerp(a, this.interestedAngleO, this.interestedAngle) * 0.11F * (float) Math.PI;
    }

    public float getCrouchAmount(float a) {
        return Mth.lerp(a, this.crouchAmountO, this.crouchAmount);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (this.isDefending() && target == null) {
            this.setDefending(false);
        }

        super.setTarget(target);
    }

    private void wakeUp() {
        this.setSleeping(false);
    }

    private void clearStates() {
        this.setIsInterested(false);
        this.setIsCrouching(false);
        this.setSitting(false);
        this.setSleeping(false);
        this.setDefending(false);
        this.setFaceplanted(false);
    }

    private boolean canMove() {
        return !this.isSleeping() && !this.isSitting() && !this.isFaceplanted();
    }

    @Override
    public void playAmbientSound() {
        SoundEvent soundevent = this.getAmbientSound();

        if (soundevent == SoundEvents.FOX_SCREECH) {
            this.playSound(soundevent, 2.0F, this.getVoicePitch());
        } else {
            super.playAmbientSound();
        }

    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        if (this.isSleeping()) {
            return SoundEvents.FOX_SLEEP;
        } else {
            if (!this.level().isBrightOutside() && this.random.nextFloat() < 0.1F) {
                List<Player> list = this.level().<Player>getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(16.0D, 16.0D, 16.0D), EntitySelector.NO_SPECTATORS);

                if (list.isEmpty()) {
                    return SoundEvents.FOX_SCREECH;
                }
            }

            return SoundEvents.FOX_AMBIENT;
        }
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.FOX_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.FOX_DEATH;
    }

    private boolean trusts(LivingEntity entity) {
        return this.getTrustedEntities().anyMatch((entityreference) -> {
            return entityreference.matches(entity);
        });
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        ItemStack itemstack = this.getItemBySlot(EquipmentSlot.MAINHAND);

        if (!itemstack.isEmpty()) {
            this.spawnAtLocation(level, itemstack);
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        super.dropAllDeathLoot(level, source);
    }

    public static boolean isPathClear(Fox fox, LivingEntity target) {
        double d0 = target.getZ() - fox.getZ();
        double d1 = target.getX() - fox.getX();
        double d2 = d0 / d1;
        int i = 6;

        for (int j = 0; j < 6; ++j) {
            double d3 = d2 == 0.0D ? 0.0D : d0 * (double) ((float) j / 6.0F);
            double d4 = d2 == 0.0D ? d1 * (double) ((float) j / 6.0F) : d3 / d2;

            for (int k = 1; k < 4; ++k) {
                if (!fox.level().getBlockState(BlockPos.containing(fox.getX() + d4, fox.getY() + (double) k, fox.getZ() + d3)).canBeReplaced()) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0D, (double) (0.55F * this.getEyeHeight()), (double) (this.getBbWidth() * 0.4F));
    }

    public static enum Variant implements StringRepresentable {

        RED(0, "red"), SNOW(1, "snow");

        public static final Fox.Variant DEFAULT = Fox.Variant.RED;
        public static final StringRepresentable.EnumCodec<Fox.Variant> CODEC = StringRepresentable.<Fox.Variant>fromEnum(Fox.Variant::values);
        private static final IntFunction<Fox.Variant> BY_ID = ByIdMap.<Fox.Variant>continuous(Fox.Variant::getId, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, Fox.Variant> STREAM_CODEC = ByteBufCodecs.idMapper(Fox.Variant.BY_ID, Fox.Variant::getId);
        private final int id;
        private final String name;

        private Variant(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public int getId() {
            return this.id;
        }

        public static Fox.Variant byId(int id) {
            return (Fox.Variant) Fox.Variant.BY_ID.apply(id);
        }

        public static Fox.Variant byBiome(Holder<Biome> biome) {
            return biome.is(BiomeTags.SPAWNS_SNOW_FOXES) ? Fox.Variant.SNOW : Fox.Variant.RED;
        }
    }

    private class FoxSearchForItemsGoal extends Goal {

        public FoxSearchForItemsGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!Fox.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
                return false;
            } else if (Fox.this.getTarget() == null && Fox.this.getLastHurtByMob() == null) {
                if (!Fox.this.canMove()) {
                    return false;
                } else if (Fox.this.getRandom().nextInt(reducedTickDelay(10)) != 0) {
                    return false;
                } else {
                    List<ItemEntity> list = Fox.this.level().<ItemEntity>getEntitiesOfClass(ItemEntity.class, Fox.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), Fox.ALLOWED_ITEMS);

                    return !list.isEmpty() && Fox.this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
                }
            } else {
                return false;
            }
        }

        @Override
        public void tick() {
            List<ItemEntity> list = Fox.this.level().<ItemEntity>getEntitiesOfClass(ItemEntity.class, Fox.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), Fox.ALLOWED_ITEMS);
            ItemStack itemstack = Fox.this.getItemBySlot(EquipmentSlot.MAINHAND);

            if (itemstack.isEmpty() && !list.isEmpty()) {
                Fox.this.getNavigation().moveTo((Entity) list.get(0), (double) 1.2F);
            }

        }

        @Override
        public void start() {
            List<ItemEntity> list = Fox.this.level().<ItemEntity>getEntitiesOfClass(ItemEntity.class, Fox.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), Fox.ALLOWED_ITEMS);

            if (!list.isEmpty()) {
                Fox.this.getNavigation().moveTo((Entity) list.get(0), (double) 1.2F);
            }

        }
    }

    private class FoxMoveControl extends MoveControl {

        public FoxMoveControl() {
            super(Fox.this);
        }

        @Override
        public void tick() {
            if (Fox.this.canMove()) {
                super.tick();
            }

        }
    }

    private class StalkPreyGoal extends Goal {

        public StalkPreyGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (Fox.this.isSleeping()) {
                return false;
            } else {
                LivingEntity livingentity = Fox.this.getTarget();

                return livingentity != null && livingentity.isAlive() && Fox.STALKABLE_PREY.test(livingentity) && Fox.this.distanceToSqr((Entity) livingentity) > 36.0D && !Fox.this.isCrouching() && !Fox.this.isInterested() && !Fox.this.jumping;
            }
        }

        @Override
        public void start() {
            Fox.this.setSitting(false);
            Fox.this.setFaceplanted(false);
        }

        @Override
        public void stop() {
            LivingEntity livingentity = Fox.this.getTarget();

            if (livingentity != null && Fox.isPathClear(Fox.this, livingentity)) {
                Fox.this.setIsInterested(true);
                Fox.this.setIsCrouching(true);
                Fox.this.getNavigation().stop();
                Fox.this.getLookControl().setLookAt(livingentity, (float) Fox.this.getMaxHeadYRot(), (float) Fox.this.getMaxHeadXRot());
            } else {
                Fox.this.setIsInterested(false);
                Fox.this.setIsCrouching(false);
            }

        }

        @Override
        public void tick() {
            LivingEntity livingentity = Fox.this.getTarget();

            if (livingentity != null) {
                Fox.this.getLookControl().setLookAt(livingentity, (float) Fox.this.getMaxHeadYRot(), (float) Fox.this.getMaxHeadXRot());
                if (Fox.this.distanceToSqr((Entity) livingentity) <= 36.0D) {
                    Fox.this.setIsInterested(true);
                    Fox.this.setIsCrouching(true);
                    Fox.this.getNavigation().stop();
                } else {
                    Fox.this.getNavigation().moveTo((Entity) livingentity, 1.5D);
                }

            }
        }
    }

    private class FoxMeleeAttackGoal extends MeleeAttackGoal {

        public FoxMeleeAttackGoal(double speedModifier, boolean trackTarget) {
            super(Fox.this, speedModifier, trackTarget);
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target) {
            if (this.canPerformAttack(target)) {
                this.resetAttackCooldown();
                this.mob.doHurtTarget(getServerLevel((Entity) this.mob), target);
                Fox.this.playSound(SoundEvents.FOX_BITE, 1.0F, 1.0F);
            }

        }

        @Override
        public void start() {
            Fox.this.setIsInterested(false);
            super.start();
        }

        @Override
        public boolean canUse() {
            return !Fox.this.isSitting() && !Fox.this.isSleeping() && !Fox.this.isCrouching() && !Fox.this.isFaceplanted() && super.canUse();
        }
    }

    private class FoxBreedGoal extends BreedGoal {

        public FoxBreedGoal(double speedModifier) {
            super(Fox.this, speedModifier);
        }

        @Override
        public void start() {
            ((Fox) this.animal).clearStates();
            ((Fox) this.partner).clearStates();
            super.start();
        }

        @Override
        protected void breed() {
            Fox fox = (Fox) this.animal.getBreedOffspring(this.level, this.partner);

            if (fox != null) {
                ServerPlayer serverplayer = this.animal.getLoveCause();
                ServerPlayer serverplayer1 = this.partner.getLoveCause();
                ServerPlayer serverplayer2 = serverplayer;

                if (serverplayer != null) {
                    fox.addTrustedEntity(serverplayer);
                } else {
                    serverplayer2 = serverplayer1;
                }

                if (serverplayer1 != null && serverplayer != serverplayer1) {
                    fox.addTrustedEntity(serverplayer1);
                }

                if (serverplayer2 != null) {
                    serverplayer2.awardStat(Stats.ANIMALS_BRED);
                    CriteriaTriggers.BRED_ANIMALS.trigger(serverplayer2, this.animal, this.partner, fox);
                }

                this.animal.setAge(6000);
                this.partner.setAge(6000);
                this.animal.resetLove();
                this.partner.resetLove();
                fox.setAge(-24000);
                fox.snapTo(this.animal.getX(), this.animal.getY(), this.animal.getZ(), 0.0F, 0.0F);
                this.level.addFreshEntityWithPassengers(fox);
                this.level.broadcastEntityEvent(this.animal, (byte) 18);
                if ((Boolean) this.level.getGameRules().get(GameRules.MOB_DROPS)) {
                    this.level.addFreshEntity(new ExperienceOrb(this.level, this.animal.getX(), this.animal.getY(), this.animal.getZ(), this.animal.getRandom().nextInt(7) + 1));
                }

            }
        }
    }

    private class DefendTrustedTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {

        private @Nullable LivingEntity trustedLastHurtBy;
        private @Nullable LivingEntity trustedLastHurt;
        private int timestamp;

        public DefendTrustedTargetGoal(Class<LivingEntity> targetType, boolean mustSee, @Nullable boolean mustReach, TargetingConditions.Selector subselector) {
            super(Fox.this, targetType, 10, mustSee, mustReach, subselector);
        }

        @Override
        public boolean canUse() {
            if (this.randomInterval > 0 && this.mob.getRandom().nextInt(this.randomInterval) != 0) {
                return false;
            } else {
                ServerLevel serverlevel = getServerLevel(Fox.this.level());

                for (EntityReference<LivingEntity> entityreference : Fox.this.getTrustedEntities().toList()) {
                    LivingEntity livingentity = (LivingEntity) entityreference.getEntity(serverlevel, LivingEntity.class);

                    if (livingentity != null) {
                        this.trustedLastHurt = livingentity;
                        this.trustedLastHurtBy = livingentity.getLastHurtByMob();
                        int i = livingentity.getLastHurtByMobTimestamp();

                        return i != this.timestamp && this.canAttack(this.trustedLastHurtBy, this.targetConditions);
                    }
                }

                return false;
            }
        }

        @Override
        public void start() {
            this.setTarget(this.trustedLastHurtBy);
            this.target = this.trustedLastHurtBy;
            if (this.trustedLastHurt != null) {
                this.timestamp = this.trustedLastHurt.getLastHurtByMobTimestamp();
            }

            Fox.this.playSound(SoundEvents.FOX_AGGRO, 1.0F, 1.0F);
            Fox.this.setDefending(true);
            Fox.this.wakeUp();
            super.start();
        }
    }

    private class SeekShelterGoal extends FleeSunGoal {

        private int interval = reducedTickDelay(100);

        public SeekShelterGoal(double speedModifier) {
            super(Fox.this, speedModifier);
        }

        @Override
        public boolean canUse() {
            if (!Fox.this.isSleeping() && this.mob.getTarget() == null) {
                if (Fox.this.level().isThundering() && Fox.this.level().canSeeSky(this.mob.blockPosition())) {
                    return this.setWantedPos();
                } else if (this.interval > 0) {
                    --this.interval;
                    return false;
                } else {
                    this.interval = 100;
                    BlockPos blockpos = this.mob.blockPosition();

                    return Fox.this.level().isBrightOutside() && Fox.this.level().canSeeSky(blockpos) && !((ServerLevel) Fox.this.level()).isVillage(blockpos) && this.setWantedPos();
                }
            } else {
                return false;
            }
        }

        @Override
        public void start() {
            Fox.this.clearStates();
            super.start();
        }
    }

    public class FoxAlertableEntitiesSelector implements TargetingConditions.Selector {

        public FoxAlertableEntitiesSelector() {}

        @Override
        public boolean test(LivingEntity target, ServerLevel level) {
            if (target instanceof Fox) {
                return false;
            } else if (!(target instanceof Chicken) && !(target instanceof Rabbit) && !(target instanceof Monster)) {
                if (target instanceof TamableAnimal) {
                    return !((TamableAnimal) target).isTame();
                } else {
                    if (target instanceof Player) {
                        Player player = (Player) target;

                        if (player.isSpectator() || player.isCreative()) {
                            return false;
                        }
                    }

                    if (Fox.this.trusts(target)) {
                        return false;
                    } else {
                        return !target.isSleeping() && !target.isDiscrete();
                    }
                }
            } else {
                return true;
            }
        }
    }

    private abstract class FoxBehaviorGoal extends Goal {

        private final TargetingConditions alertableTargeting = TargetingConditions.forCombat().range(12.0D).ignoreLineOfSight().selector(Fox.this.new FoxAlertableEntitiesSelector());

        private FoxBehaviorGoal() {}

        protected boolean hasShelter() {
            BlockPos blockpos = BlockPos.containing(Fox.this.getX(), Fox.this.getBoundingBox().maxY, Fox.this.getZ());

            return !Fox.this.level().canSeeSky(blockpos) && Fox.this.getWalkTargetValue(blockpos) >= 0.0F;
        }

        protected boolean alertable() {
            return !getServerLevel(Fox.this.level()).getNearbyEntities(LivingEntity.class, this.alertableTargeting, Fox.this, Fox.this.getBoundingBox().inflate(12.0D, 6.0D, 12.0D)).isEmpty();
        }
    }

    private class SleepGoal extends Fox.FoxBehaviorGoal {

        private static final int WAIT_TIME_BEFORE_SLEEP = reducedTickDelay(140);
        private int countdown;

        public SleepGoal() {
            this.countdown = Fox.this.random.nextInt(Fox.SleepGoal.WAIT_TIME_BEFORE_SLEEP);
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return Fox.this.xxa == 0.0F && Fox.this.yya == 0.0F && Fox.this.zza == 0.0F ? this.canSleep() || Fox.this.isSleeping() : false;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canSleep();
        }

        private boolean canSleep() {
            if (this.countdown > 0) {
                --this.countdown;
                return false;
            } else {
                return Fox.this.level().isBrightOutside() && this.hasShelter() && !this.alertable() && !Fox.this.isInPowderSnow;
            }
        }

        @Override
        public void stop() {
            this.countdown = Fox.this.random.nextInt(Fox.SleepGoal.WAIT_TIME_BEFORE_SLEEP);
            Fox.this.clearStates();
        }

        @Override
        public void start() {
            Fox.this.setSitting(false);
            Fox.this.setIsCrouching(false);
            Fox.this.setIsInterested(false);
            Fox.this.setJumping(false);
            Fox.this.setSleeping(true);
            Fox.this.getNavigation().stop();
            Fox.this.getMoveControl().setWantedPosition(Fox.this.getX(), Fox.this.getY(), Fox.this.getZ(), 0.0D);
        }
    }

    private class PerchAndSearchGoal extends Fox.FoxBehaviorGoal {

        private double relX;
        private double relZ;
        private int lookTime;
        private int looksRemaining;

        public PerchAndSearchGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return Fox.this.getLastHurtByMob() == null && Fox.this.getRandom().nextFloat() < 0.02F && !Fox.this.isSleeping() && Fox.this.getTarget() == null && Fox.this.getNavigation().isDone() && !this.alertable() && !Fox.this.isPouncing() && !Fox.this.isCrouching();
        }

        @Override
        public boolean canContinueToUse() {
            return this.looksRemaining > 0;
        }

        @Override
        public void start() {
            this.resetLook();
            this.looksRemaining = 2 + Fox.this.getRandom().nextInt(3);
            Fox.this.setSitting(true);
            Fox.this.getNavigation().stop();
        }

        @Override
        public void stop() {
            Fox.this.setSitting(false);
        }

        @Override
        public void tick() {
            --this.lookTime;
            if (this.lookTime <= 0) {
                --this.looksRemaining;
                this.resetLook();
            }

            Fox.this.getLookControl().setLookAt(Fox.this.getX() + this.relX, Fox.this.getEyeY(), Fox.this.getZ() + this.relZ, (float) Fox.this.getMaxHeadYRot(), (float) Fox.this.getMaxHeadXRot());
        }

        private void resetLook() {
            double d0 = (Math.PI * 2D) * Fox.this.getRandom().nextDouble();

            this.relX = Math.cos(d0);
            this.relZ = Math.sin(d0);
            this.lookTime = this.adjustedTickDelay(80 + Fox.this.getRandom().nextInt(20));
        }
    }

    public class FoxEatBerriesGoal extends MoveToBlockGoal {

        private static final int WAIT_TICKS = 40;
        protected int ticksWaited;

        public FoxEatBerriesGoal(double speedModifier, int searchRange, int verticalSearchRange) {
            super(Fox.this, speedModifier, searchRange, verticalSearchRange);
        }

        @Override
        public double acceptedDistance() {
            return 2.0D;
        }

        @Override
        public boolean shouldRecalculatePath() {
            return this.tryTicks % 100 == 0;
        }

        @Override
        protected boolean isValidTarget(LevelReader level, BlockPos pos) {
            BlockState blockstate = level.getBlockState(pos);

            return blockstate.is(Blocks.SWEET_BERRY_BUSH) && (Integer) blockstate.getValue(SweetBerryBushBlock.AGE) >= 2 || CaveVines.hasGlowBerries(blockstate);
        }

        @Override
        public void tick() {
            if (this.isReachedTarget()) {
                if (this.ticksWaited >= 40) {
                    this.onReachedTarget();
                } else {
                    ++this.ticksWaited;
                }
            } else if (!this.isReachedTarget() && Fox.this.random.nextFloat() < 0.05F) {
                Fox.this.playSound(SoundEvents.FOX_SNIFF, 1.0F, 1.0F);
            }

            super.tick();
        }

        protected void onReachedTarget() {
            if ((Boolean) getServerLevel(Fox.this.level()).getGameRules().get(GameRules.MOB_GRIEFING)) {
                BlockState blockstate = Fox.this.level().getBlockState(this.blockPos);

                if (blockstate.is(Blocks.SWEET_BERRY_BUSH)) {
                    this.pickSweetBerries(blockstate);
                } else if (CaveVines.hasGlowBerries(blockstate)) {
                    this.pickGlowBerry(blockstate);
                }

            }
        }

        private void pickGlowBerry(BlockState state) {
            CaveVines.use(Fox.this, state, Fox.this.level(), this.blockPos);
        }

        private void pickSweetBerries(BlockState state) {
            int i = (Integer) state.getValue(SweetBerryBushBlock.AGE);

            state.setValue(SweetBerryBushBlock.AGE, 1);
            int j = 1 + Fox.this.level().random.nextInt(2) + (i == 3 ? 1 : 0);
            ItemStack itemstack = Fox.this.getItemBySlot(EquipmentSlot.MAINHAND);

            if (itemstack.isEmpty()) {
                Fox.this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SWEET_BERRIES));
                --j;
            }

            if (j > 0) {
                Block.popResource(Fox.this.level(), this.blockPos, new ItemStack(Items.SWEET_BERRIES, j));
            }

            Fox.this.playSound(SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, 1.0F, 1.0F);
            Fox.this.level().setBlock(this.blockPos, (BlockState) state.setValue(SweetBerryBushBlock.AGE, 1), 2);
            Fox.this.level().gameEvent(GameEvent.BLOCK_CHANGE, this.blockPos, GameEvent.Context.of((Entity) Fox.this));
        }

        @Override
        public boolean canUse() {
            return !Fox.this.isSleeping() && super.canUse();
        }

        @Override
        public void start() {
            this.ticksWaited = 0;
            Fox.this.setSitting(false);
            super.start();
        }
    }

    public static class FoxGroupData extends AgeableMob.AgeableMobGroupData {

        public final Fox.Variant variant;

        public FoxGroupData(Fox.Variant fox_variant) {
            super(false);
            this.variant = fox_variant;
        }
    }

    private class FaceplantGoal extends Goal {

        int countdown;

        public FaceplantGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.LOOK, Goal.Flag.JUMP, Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return Fox.this.isFaceplanted();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse() && this.countdown > 0;
        }

        @Override
        public void start() {
            this.countdown = this.adjustedTickDelay(40);
        }

        @Override
        public void stop() {
            Fox.this.setFaceplanted(false);
        }

        @Override
        public void tick() {
            --this.countdown;
        }
    }

    private class FoxPanicGoal extends PanicGoal {

        public FoxPanicGoal(double speedModifier) {
            super(Fox.this, speedModifier);
        }

        @Override
        public boolean shouldPanic() {
            return !Fox.this.isDefending() && super.shouldPanic();
        }
    }

    private class FoxStrollThroughVillageGoal extends StrollThroughVillageGoal {

        public FoxStrollThroughVillageGoal(int searchRadius, int interval) {
            super(Fox.this, interval);
        }

        @Override
        public void start() {
            Fox.this.clearStates();
            super.start();
        }

        @Override
        public boolean canUse() {
            return super.canUse() && this.canFoxMove();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && this.canFoxMove();
        }

        private boolean canFoxMove() {
            return !Fox.this.isSleeping() && !Fox.this.isSitting() && !Fox.this.isDefending() && Fox.this.getTarget() == null;
        }
    }

    private class FoxFloatGoal extends FloatGoal {

        public FoxFloatGoal() {
            super(Fox.this);
        }

        @Override
        public void start() {
            super.start();
            Fox.this.clearStates();
        }

        @Override
        public boolean canUse() {
            return Fox.this.isInWater() && Fox.this.getFluidHeight(FluidTags.WATER) > 0.25D || Fox.this.isInLava();
        }
    }

    public class FoxPounceGoal extends JumpGoal {

        public FoxPounceGoal() {}

        @Override
        public boolean canUse() {
            if (!Fox.this.isFullyCrouched()) {
                return false;
            } else {
                LivingEntity livingentity = Fox.this.getTarget();

                if (livingentity != null && livingentity.isAlive()) {
                    if (livingentity.getMotionDirection() != livingentity.getDirection()) {
                        return false;
                    } else {
                        boolean flag = Fox.isPathClear(Fox.this, livingentity);

                        if (!flag) {
                            Fox.this.getNavigation().createPath(livingentity, 0);
                            Fox.this.setIsCrouching(false);
                            Fox.this.setIsInterested(false);
                        }

                        return flag;
                    }
                } else {
                    return false;
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity livingentity = Fox.this.getTarget();

            if (livingentity != null && livingentity.isAlive()) {
                double d0 = Fox.this.getDeltaMovement().y;

                return (d0 * d0 >= (double) 0.05F || Math.abs(Fox.this.getXRot()) >= 15.0F || !Fox.this.onGround()) && !Fox.this.isFaceplanted();
            } else {
                return false;
            }
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }

        @Override
        public void start() {
            Fox.this.setJumping(true);
            Fox.this.setIsPouncing(true);
            Fox.this.setIsInterested(false);
            LivingEntity livingentity = Fox.this.getTarget();

            if (livingentity != null) {
                Fox.this.getLookControl().setLookAt(livingentity, 60.0F, 30.0F);
                Vec3 vec3 = (new Vec3(livingentity.getX() - Fox.this.getX(), livingentity.getY() - Fox.this.getY(), livingentity.getZ() - Fox.this.getZ())).normalize();

                Fox.this.setDeltaMovement(Fox.this.getDeltaMovement().add(vec3.x * 0.8D, 0.9D, vec3.z * 0.8D));
            }

            Fox.this.getNavigation().stop();
        }

        @Override
        public void stop() {
            Fox.this.setIsCrouching(false);
            Fox.this.crouchAmount = 0.0F;
            Fox.this.crouchAmountO = 0.0F;
            Fox.this.setIsInterested(false);
            Fox.this.setIsPouncing(false);
        }

        @Override
        public void tick() {
            LivingEntity livingentity = Fox.this.getTarget();

            if (livingentity != null) {
                Fox.this.getLookControl().setLookAt(livingentity, 60.0F, 30.0F);
            }

            if (!Fox.this.isFaceplanted()) {
                Vec3 vec3 = Fox.this.getDeltaMovement();

                if (vec3.y * vec3.y < (double) 0.03F && Fox.this.getXRot() != 0.0F) {
                    Fox.this.setXRot(Mth.rotLerp(0.2F, Fox.this.getXRot(), 0.0F));
                } else {
                    double d0 = vec3.horizontalDistance();
                    double d1 = Math.signum(-vec3.y) * Math.acos(d0 / vec3.length()) * (double) (180F / (float) Math.PI);

                    Fox.this.setXRot((float) d1);
                }
            }

            if (livingentity != null && Fox.this.distanceTo(livingentity) <= 2.0F) {
                Fox.this.doHurtTarget(getServerLevel(Fox.this.level()), livingentity);
            } else if (Fox.this.getXRot() > 0.0F && Fox.this.onGround() && (float) Fox.this.getDeltaMovement().y != 0.0F && Fox.this.level().getBlockState(Fox.this.blockPosition()).is(Blocks.SNOW)) {
                Fox.this.setXRot(60.0F);
                Fox.this.setTarget((LivingEntity) null);
                Fox.this.setFaceplanted(true);
            }

        }
    }

    public class FoxLookControl extends LookControl {

        public FoxLookControl() {
            super(Fox.this);
        }

        @Override
        public void tick() {
            if (!Fox.this.isSleeping()) {
                super.tick();
            }

        }

        @Override
        protected boolean resetXRotOnTick() {
            return !Fox.this.isPouncing() && !Fox.this.isCrouching() && !Fox.this.isInterested() && !Fox.this.isFaceplanted();
        }
    }

    private static class FoxFollowParentGoal extends FollowParentGoal {

        private final Fox fox;

        public FoxFollowParentGoal(Fox fox, double speedModifier) {
            super(fox, speedModifier);
            this.fox = fox;
        }

        @Override
        public boolean canUse() {
            return !this.fox.isDefending() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.fox.isDefending() && super.canContinueToUse();
        }

        @Override
        public void start() {
            this.fox.clearStates();
            super.start();
        }
    }

    private class FoxLookAtPlayerGoal extends LookAtPlayerGoal {

        public FoxLookAtPlayerGoal(Mob mob, Class<? extends LivingEntity> lookAtType, float lookDistance) {
            super(mob, lookAtType, lookDistance);
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !Fox.this.isFaceplanted() && !Fox.this.isInterested();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && !Fox.this.isFaceplanted() && !Fox.this.isInterested();
        }
    }
}

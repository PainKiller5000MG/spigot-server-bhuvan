package net.minecraft.world.entity.animal.equine;

import com.google.common.collect.UnmodifiableIterator;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStandGoal;
import net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.inventory.AbstractMountInventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractHorse extends Animal implements PlayerRideableJumping, HasCustomInventoryScreen, OwnableEntity {

    public static final int CHEST_SLOT_OFFSET = 499;
    public static final int INVENTORY_SLOT_OFFSET = 500;
    public static final double BREEDING_CROSS_FACTOR = 0.15D;
    private static final float MIN_MOVEMENT_SPEED = (float) generateSpeed(() -> {
        return 0.0D;
    });
    private static final float MAX_MOVEMENT_SPEED = (float) generateSpeed(() -> {
        return 1.0D;
    });
    private static final float MIN_JUMP_STRENGTH = (float) generateJumpStrength(() -> {
        return 0.0D;
    });
    private static final float MAX_JUMP_STRENGTH = (float) generateJumpStrength(() -> {
        return 1.0D;
    });
    private static final float MIN_HEALTH = generateMaxHealth((i) -> {
        return 0;
    });
    private static final float MAX_HEALTH = generateMaxHealth((i) -> {
        return i - 1;
    });
    private static final float BACKWARDS_MOVE_SPEED_FACTOR = 0.25F;
    private static final float SIDEWAYS_MOVE_SPEED_FACTOR = 0.5F;
    private static final TargetingConditions.Selector PARENT_HORSE_SELECTOR = (livingentity, serverlevel) -> {
        boolean flag;

        if (livingentity instanceof AbstractHorse abstracthorse) {
            if (abstracthorse.isBred()) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    };
    private static final TargetingConditions MOMMY_TARGETING = TargetingConditions.forNonCombat().range(16.0D).ignoreLineOfSight().selector(AbstractHorse.PARENT_HORSE_SELECTOR);
    private static final EntityDataAccessor<Byte> DATA_ID_FLAGS = SynchedEntityData.<Byte>defineId(AbstractHorse.class, EntityDataSerializers.BYTE);
    private static final int FLAG_TAME = 2;
    private static final int FLAG_BRED = 8;
    private static final int FLAG_EATING = 16;
    private static final int FLAG_STANDING = 32;
    private static final int FLAG_OPEN_MOUTH = 64;
    public static final int INVENTORY_ROWS = 3;
    private static final int DEFAULT_TEMPER = 0;
    private static final boolean DEFAULT_EATING_HAYSTACK = false;
    private static final boolean DEFAULT_BRED = false;
    private static final boolean DEFAULT_TAME = false;
    private int eatingCounter;
    private int mouthCounter;
    private int standCounter;
    public int tailCounter;
    public int sprintCounter;
    public SimpleContainer inventory;
    protected int temper = 0;
    protected float playerJumpPendingScale;
    protected boolean allowStandSliding;
    private float eatAnim;
    private float eatAnimO;
    private float standAnim;
    private float standAnimO;
    private float mouthAnim;
    private float mouthAnimO;
    protected boolean canGallop = true;
    protected int gallopSoundCounter;
    public @Nullable EntityReference<LivingEntity> owner;

    protected AbstractHorse(EntityType<? extends AbstractHorse> type, Level level) {
        super(type, level);
        this.createInventory();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new AbstractHorse.MountPanicGoal(1.2D));
        this.goalSelector.addGoal(1, new RunAroundLikeCrazyGoal(this, 1.2D));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D, AbstractHorse.class));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        if (this.canPerformRearing()) {
            this.goalSelector.addGoal(9, new RandomStandGoal(this));
        }

        this.addBehaviourGoals();
    }

    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.25D, (itemstack) -> {
            return itemstack.is(ItemTags.HORSE_TEMPT_ITEMS);
        }, false));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(AbstractHorse.DATA_ID_FLAGS, (byte) 0);
    }

    protected boolean getFlag(int flag) {
        return ((Byte) this.entityData.get(AbstractHorse.DATA_ID_FLAGS) & flag) != 0;
    }

    protected void setFlag(int flag, boolean value) {
        byte b0 = (Byte) this.entityData.get(AbstractHorse.DATA_ID_FLAGS);

        if (value) {
            this.entityData.set(AbstractHorse.DATA_ID_FLAGS, (byte) (b0 | flag));
        } else {
            this.entityData.set(AbstractHorse.DATA_ID_FLAGS, (byte) (b0 & ~flag));
        }

    }

    public boolean isTamed() {
        return this.getFlag(2);
    }

    @Override
    public @Nullable EntityReference<LivingEntity> getOwnerReference() {
        return this.owner;
    }

    public void setOwner(@Nullable LivingEntity owner) {
        this.owner = EntityReference.of(owner);
    }

    public void setTamed(boolean flag) {
        this.setFlag(2, flag);
    }

    @Override
    public void onElasticLeashPull() {
        super.onElasticLeashPull();
        if (this.isEating()) {
            this.setEating(false);
        }

    }

    @Override
    public boolean supportQuadLeash() {
        return true;
    }

    @Override
    public Vec3[] getQuadLeashOffsets() {
        return Leashable.createQuadLeashOffsets(this, 0.04D, 0.52D, 0.23D, 0.87D);
    }

    public boolean isEating() {
        return this.getFlag(16);
    }

    public boolean isStanding() {
        return this.getFlag(32);
    }

    public boolean isBred() {
        return this.getFlag(8);
    }

    public void setBred(boolean flag) {
        this.setFlag(8, flag);
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return slot != EquipmentSlot.SADDLE ? super.canUseSlot(slot) : this.isAlive() && !this.isBaby() && this.isTamed();
    }

    public void equipBodyArmor(Player player, ItemStack itemStack) {
        if (this.isEquippableInSlot(itemStack, EquipmentSlot.BODY)) {
            this.setBodyArmorItem(itemStack.consumeAndReturn(1, player));
        }

    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
        return (slot == EquipmentSlot.BODY || slot == EquipmentSlot.SADDLE) && this.isTamed() || super.canDispenserEquipIntoSlot(slot);
    }

    public int getTemper() {
        return this.temper;
    }

    public void setTemper(int temper) {
        this.temper = temper;
    }

    public int modifyTemper(int amount) {
        int j = Mth.clamp(this.getTemper() + amount, 0, this.getMaxTemper());

        this.setTemper(j);
        return j;
    }

    @Override
    public boolean isPushable() {
        return !this.isVehicle();
    }

    private void eating() {
        this.openMouth();
        if (!this.isSilent()) {
            SoundEvent soundevent = this.getEatingSound();

            if (soundevent != null) {
                this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), soundevent, this.getSoundSource(), 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
            }
        }

    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        if (fallDistance > 1.0D) {
            this.playSound(SoundEvents.HORSE_LAND, 0.4F, 1.0F);
        }

        int i = this.calculateFallDamage(fallDistance, damageModifier);

        if (i <= 0) {
            return false;
        } else {
            this.hurt(damageSource, (float) i);
            this.propagateFallToPassengers(fallDistance, damageModifier, damageSource);
            this.playBlockFallSound();
            return true;
        }
    }

    public final int getInventorySize() {
        return AbstractMountInventoryMenu.getInventorySize(this.getInventoryColumns());
    }

    public void createInventory() {
        SimpleContainer simplecontainer = this.inventory;

        this.inventory = new SimpleContainer(this.getInventorySize());
        if (simplecontainer != null) {
            int i = Math.min(simplecontainer.getContainerSize(), this.inventory.getContainerSize());

            for (int j = 0; j < i; ++j) {
                ItemStack itemstack = simplecontainer.getItem(j);

                if (!itemstack.isEmpty()) {
                    this.inventory.setItem(j, itemstack.copy());
                }
            }
        }

    }

    @Override
    protected Holder<SoundEvent> getEquipSound(EquipmentSlot slot, ItemStack stack, Equippable equippable) {
        return (Holder<SoundEvent>) (slot == EquipmentSlot.SADDLE ? SoundEvents.HORSE_SADDLE : super.getEquipSound(slot, stack, equippable));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean flag = super.hurtServer(level, source, damage);

        if (flag && this.random.nextInt(3) == 0) {
            this.standIfPossible();
        }

        return flag;
    }

    protected boolean canPerformRearing() {
        return true;
    }

    protected @Nullable SoundEvent getEatingSound() {
        return null;
    }

    protected @Nullable SoundEvent getAngrySound() {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
        if (!blockState.liquid()) {
            BlockState blockstate1 = this.level().getBlockState(pos.above());
            SoundType soundtype = blockState.getSoundType();

            if (blockstate1.is(Blocks.SNOW)) {
                soundtype = blockstate1.getSoundType();
            }

            if (this.isVehicle() && this.canGallop) {
                ++this.gallopSoundCounter;
                if (this.gallopSoundCounter > 5 && this.gallopSoundCounter % 3 == 0) {
                    this.playGallopSound(soundtype);
                } else if (this.gallopSoundCounter <= 5) {
                    this.playSound(SoundEvents.HORSE_STEP_WOOD, soundtype.getVolume() * 0.15F, soundtype.getPitch());
                }
            } else if (this.isWoodSoundType(soundtype)) {
                this.playSound(SoundEvents.HORSE_STEP_WOOD, soundtype.getVolume() * 0.15F, soundtype.getPitch());
            } else {
                this.playSound(SoundEvents.HORSE_STEP, soundtype.getVolume() * 0.15F, soundtype.getPitch());
            }

        }
    }

    private boolean isWoodSoundType(SoundType soundType) {
        return soundType == SoundType.WOOD || soundType == SoundType.NETHER_WOOD || soundType == SoundType.STEM || soundType == SoundType.CHERRY_WOOD || soundType == SoundType.BAMBOO_WOOD;
    }

    protected void playGallopSound(SoundType soundType) {
        this.playSound(SoundEvents.HORSE_GALLOP, soundType.getVolume() * 0.15F, soundType.getPitch());
    }

    public static AttributeSupplier.Builder createBaseHorseAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.JUMP_STRENGTH, 0.7D).add(Attributes.MAX_HEALTH, 53.0D).add(Attributes.MOVEMENT_SPEED, (double) 0.225F).add(Attributes.STEP_HEIGHT, 1.0D).add(Attributes.SAFE_FALL_DISTANCE, 6.0D).add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.5D);
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 6;
    }

    public int getMaxTemper() {
        return 100;
    }

    @Override
    protected float getSoundVolume() {
        return 0.8F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 400;
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        if (!this.level().isClientSide() && (!this.isVehicle() || this.hasPassenger(player)) && this.isTamed()) {
            player.openHorseInventory(this, this.inventory);
        }

    }

    public InteractionResult fedFood(Player player, ItemStack itemStack) {
        boolean flag = this.handleEating(player, itemStack);

        if (flag) {
            itemStack.consume(1, player);
        }

        return (InteractionResult) (!flag && !this.level().isClientSide() ? InteractionResult.PASS : InteractionResult.SUCCESS_SERVER);
    }

    protected boolean handleEating(Player player, ItemStack itemStack) {
        boolean flag = false;
        float f = 0.0F;
        int i = 0;
        int j = 0;

        if (itemStack.is(Items.WHEAT)) {
            f = 2.0F;
            i = 20;
            j = 3;
        } else if (itemStack.is(Items.SUGAR)) {
            f = 1.0F;
            i = 30;
            j = 3;
        } else if (itemStack.is(Blocks.HAY_BLOCK.asItem())) {
            f = 20.0F;
            i = 180;
        } else if (itemStack.is(Items.APPLE)) {
            f = 3.0F;
            i = 60;
            j = 3;
        } else if (itemStack.is(Items.RED_MUSHROOM)) {
            f = 3.0F;
            i = 0;
            j = 3;
        } else if (itemStack.is(Items.CARROT)) {
            f = 3.0F;
            i = 60;
            j = 3;
        } else if (itemStack.is(Items.GOLDEN_CARROT)) {
            f = 4.0F;
            i = 60;
            j = 5;
            if (!this.level().isClientSide() && this.isTamed() && this.getAge() == 0 && !this.isInLove()) {
                flag = true;
                this.setInLove(player);
            }
        } else if (itemStack.is(Items.GOLDEN_APPLE) || itemStack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            f = 10.0F;
            i = 240;
            j = 10;
            if (!this.level().isClientSide() && this.isTamed() && this.getAge() == 0 && !this.isInLove()) {
                flag = true;
                this.setInLove(player);
            }
        }

        if (this.getHealth() < this.getMaxHealth() && f > 0.0F) {
            this.heal(f);
            flag = true;
        }

        if (this.isBaby() && i > 0) {
            this.level().addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), 0.0D, 0.0D, 0.0D);
            if (!this.level().isClientSide()) {
                this.ageUp(i);
                flag = true;
            }
        }

        if (j > 0 && (flag || !this.isTamed()) && this.getTemper() < this.getMaxTemper() && !this.level().isClientSide()) {
            this.modifyTemper(j);
            flag = true;
        }

        if (flag) {
            this.eating();
            this.gameEvent(GameEvent.EAT);
        }

        return flag;
    }

    protected void doPlayerRide(Player player) {
        this.setEating(false);
        this.clearStanding();
        if (!this.level().isClientSide()) {
            player.setYRot(this.getYRot());
            player.setXRot(this.getXRot());
            player.startRiding(this);
        }

    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() && this.isVehicle() && this.isSaddled() || this.isEating() || this.isStanding();
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return itemStack.is(ItemTags.HORSE_FOOD);
    }

    private void moveTail() {
        this.tailCounter = 1;
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        super.dropEquipment(level);
        if (this.inventory != null) {
            for (int i = 0; i < this.inventory.getContainerSize(); ++i) {
                ItemStack itemstack = this.inventory.getItem(i);

                if (!itemstack.isEmpty() && !EnchantmentHelper.has(itemstack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                    this.spawnAtLocation(level, itemstack);
                }
            }

        }
    }

    @Override
    public void aiStep() {
        if (this.random.nextInt(200) == 0) {
            this.moveTail();
        }

        super.aiStep();
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            if (this.isAlive()) {
                if (this.random.nextInt(900) == 0 && this.deathTime == 0) {
                    this.heal(1.0F);
                }

                if (this.canEatGrass()) {
                    if (!this.isEating() && !this.isVehicle() && this.random.nextInt(300) == 0 && serverlevel.getBlockState(this.blockPosition().below()).is(Blocks.GRASS_BLOCK)) {
                        this.setEating(true);
                    }

                    if (this.isEating() && ++this.eatingCounter > 50) {
                        this.eatingCounter = 0;
                        this.setEating(false);
                    }
                }

                this.followMommy(serverlevel);
                return;
            }
        }

    }

    protected void followMommy(ServerLevel level) {
        if (this.isBred() && this.isBaby() && !this.isEating()) {
            LivingEntity livingentity = level.getNearestEntity(AbstractHorse.class, AbstractHorse.MOMMY_TARGETING, this, this.getX(), this.getY(), this.getZ(), this.getBoundingBox().inflate(16.0D));

            if (livingentity != null && this.distanceToSqr((Entity) livingentity) > 4.0D) {
                this.navigation.createPath(livingentity, 0);
            }
        }

    }

    public boolean canEatGrass() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.mouthCounter > 0 && ++this.mouthCounter > 30) {
            this.mouthCounter = 0;
            this.setFlag(64, false);
        }

        if (this.standCounter > 0 && --this.standCounter <= 0) {
            this.clearStanding();
        }

        if (this.tailCounter > 0 && ++this.tailCounter > 8) {
            this.tailCounter = 0;
        }

        if (this.sprintCounter > 0) {
            ++this.sprintCounter;
            if (this.sprintCounter > 300) {
                this.sprintCounter = 0;
            }
        }

        this.eatAnimO = this.eatAnim;
        if (this.isEating()) {
            this.eatAnim += (1.0F - this.eatAnim) * 0.4F + 0.05F;
            if (this.eatAnim > 1.0F) {
                this.eatAnim = 1.0F;
            }
        } else {
            this.eatAnim += (0.0F - this.eatAnim) * 0.4F - 0.05F;
            if (this.eatAnim < 0.0F) {
                this.eatAnim = 0.0F;
            }
        }

        this.standAnimO = this.standAnim;
        if (this.isStanding()) {
            this.eatAnim = 0.0F;
            this.eatAnimO = this.eatAnim;
            this.standAnim += (1.0F - this.standAnim) * 0.4F + 0.05F;
            if (this.standAnim > 1.0F) {
                this.standAnim = 1.0F;
            }
        } else {
            this.allowStandSliding = false;
            this.standAnim += (0.8F * this.standAnim * this.standAnim * this.standAnim - this.standAnim) * 0.6F - 0.05F;
            if (this.standAnim < 0.0F) {
                this.standAnim = 0.0F;
            }
        }

        this.mouthAnimO = this.mouthAnim;
        if (this.getFlag(64)) {
            this.mouthAnim += (1.0F - this.mouthAnim) * 0.7F + 0.05F;
            if (this.mouthAnim > 1.0F) {
                this.mouthAnim = 1.0F;
            }
        } else {
            this.mouthAnim += (0.0F - this.mouthAnim) * 0.7F - 0.05F;
            if (this.mouthAnim < 0.0F) {
                this.mouthAnim = 0.0F;
            }
        }

    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.isVehicle() && !this.isBaby()) {
            if (this.isTamed() && player.isSecondaryUseActive()) {
                this.openCustomInventoryScreen(player);
                return InteractionResult.SUCCESS;
            } else {
                ItemStack itemstack = player.getItemInHand(hand);

                if (!itemstack.isEmpty()) {
                    InteractionResult interactionresult = itemstack.interactLivingEntity(player, this, hand);

                    if (interactionresult.consumesAction()) {
                        return interactionresult;
                    }

                    if (this.isEquippableInSlot(itemstack, EquipmentSlot.BODY) && !this.isWearingBodyArmor()) {
                        this.equipBodyArmor(player, itemstack);
                        return InteractionResult.SUCCESS;
                    }
                }

                this.doPlayerRide(player);
                return InteractionResult.SUCCESS;
            }
        } else {
            return super.mobInteract(player, hand);
        }
    }

    private void openMouth() {
        if (!this.level().isClientSide()) {
            this.mouthCounter = 1;
            this.setFlag(64, true);
        }

    }

    public void setEating(boolean flag) {
        this.setFlag(16, flag);
    }

    public void setStanding(int ticks) {
        this.setEating(false);
        this.setFlag(32, true);
        this.standCounter = ticks;
    }

    public void clearStanding() {
        this.setFlag(32, false);
        this.standCounter = 0;
    }

    public @Nullable SoundEvent getAmbientStandSound() {
        return this.getAmbientSound();
    }

    public void standIfPossible() {
        if (this.canPerformRearing() && (this.isEffectiveAi() || !this.level().isClientSide())) {
            this.setStanding(20);
        }

    }

    public void makeMad() {
        if (!this.isStanding() && !this.level().isClientSide()) {
            this.standIfPossible();
            this.makeSound(this.getAngrySound());
        }

    }

    public boolean tameWithName(Player player) {
        this.setOwner(player);
        this.setTamed(true);
        if (player instanceof ServerPlayer) {
            CriteriaTriggers.TAME_ANIMAL.trigger((ServerPlayer) player, this);
        }

        this.level().broadcastEntityEvent(this, (byte) 7);
        return true;
    }

    @Override
    protected void tickRidden(Player controller, Vec3 riddenInput) {
        super.tickRidden(controller, riddenInput);
        Vec2 vec2 = this.getRiddenRotation(controller);

        this.setRot(vec2.y, vec2.x);
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
        if (this.isLocalInstanceAuthoritative()) {
            if (riddenInput.z <= 0.0D) {
                this.gallopSoundCounter = 0;
            }

            if (this.onGround()) {
                if (this.playerJumpPendingScale > 0.0F && !this.isJumping()) {
                    this.executeRidersJump(this.playerJumpPendingScale, riddenInput);
                }

                this.playerJumpPendingScale = 0.0F;
            }
        }

    }

    protected Vec2 getRiddenRotation(LivingEntity controller) {
        return new Vec2(controller.getXRot() * 0.5F, controller.getYRot());
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        passenger.absSnapRotationTo(this.getViewYRot(0.0F), this.getViewXRot(0.0F));
    }

    @Override
    protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
        if (this.onGround() && this.playerJumpPendingScale == 0.0F && this.isStanding() && !this.allowStandSliding) {
            return Vec3.ZERO;
        } else {
            float f = controller.xxa * 0.5F;
            float f1 = controller.zza;

            if (f1 <= 0.0F) {
                f1 *= 0.25F;
            }

            return new Vec3((double) f, 0.0D, (double) f1);
        }
    }

    @Override
    protected float getRiddenSpeed(Player controller) {
        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    protected void executeRidersJump(float amount, Vec3 input) {
        double d0 = (double) this.getJumpPower(amount);
        Vec3 vec31 = this.getDeltaMovement();

        this.setDeltaMovement(vec31.x, d0, vec31.z);
        this.needsSync = true;
        if (input.z > 0.0D) {
            float f1 = Mth.sin((double) (this.getYRot() * ((float) Math.PI / 180F)));
            float f2 = Mth.cos((double) (this.getYRot() * ((float) Math.PI / 180F)));

            this.setDeltaMovement(this.getDeltaMovement().add((double) (-0.4F * f1 * amount), 0.0D, (double) (0.4F * f2 * amount)));
        }

    }

    protected void playJumpSound() {
        this.playSound(SoundEvents.HORSE_JUMP, 0.4F, 1.0F);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("EatingHaystack", this.isEating());
        output.putBoolean("Bred", this.isBred());
        output.putInt("Temper", this.getTemper());
        output.putBoolean("Tame", this.isTamed());
        EntityReference.store(this.owner, output, "Owner");
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setEating(input.getBooleanOr("EatingHaystack", false));
        this.setBred(input.getBooleanOr("Bred", false));
        this.setTemper(input.getIntOr("Temper", 0));
        this.setTamed(input.getBooleanOr("Tame", false));
        this.owner = EntityReference.<LivingEntity>readWithOldOwnerConversion(input, "Owner", this.level());
    }

    @Override
    public boolean canMate(Animal partner) {
        return false;
    }

    protected boolean canParent() {
        return !this.isVehicle() && !this.isPassenger() && this.isTamed() && !this.isBaby() && this.getHealth() >= this.getMaxHealth() && this.isInLove();
    }

    public boolean isMobControlled() {
        return false;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    protected void setOffspringAttributes(AgeableMob partner, AbstractHorse baby) {
        this.setOffspringAttribute(partner, baby, Attributes.MAX_HEALTH, (double) AbstractHorse.MIN_HEALTH, (double) AbstractHorse.MAX_HEALTH);
        this.setOffspringAttribute(partner, baby, Attributes.JUMP_STRENGTH, (double) AbstractHorse.MIN_JUMP_STRENGTH, (double) AbstractHorse.MAX_JUMP_STRENGTH);
        this.setOffspringAttribute(partner, baby, Attributes.MOVEMENT_SPEED, (double) AbstractHorse.MIN_MOVEMENT_SPEED, (double) AbstractHorse.MAX_MOVEMENT_SPEED);
    }

    private void setOffspringAttribute(AgeableMob partner, AbstractHorse baby, Holder<Attribute> attribute, double attributeRangeMin, double attributeRangeMax) {
        double d2 = createOffspringAttribute(this.getAttributeBaseValue(attribute), partner.getAttributeBaseValue(attribute), attributeRangeMin, attributeRangeMax, this.random);

        baby.getAttribute(attribute).setBaseValue(d2);
    }

    static double createOffspringAttribute(double parentAValue, double parentBValue, double attributeRangeMin, double attributeRangeMax, RandomSource random) {
        if (attributeRangeMax <= attributeRangeMin) {
            throw new IllegalArgumentException("Incorrect range for an attribute");
        } else {
            parentAValue = Mth.clamp(parentAValue, attributeRangeMin, attributeRangeMax);
            parentBValue = Mth.clamp(parentBValue, attributeRangeMin, attributeRangeMax);
            double d4 = 0.15D * (attributeRangeMax - attributeRangeMin);
            double d5 = Math.abs(parentAValue - parentBValue) + d4 * 2.0D;
            double d6 = (parentAValue + parentBValue) / 2.0D;
            double d7 = (random.nextDouble() + random.nextDouble() + random.nextDouble()) / 3.0D - 0.5D;
            double d8 = d6 + d5 * d7;

            if (d8 > attributeRangeMax) {
                double d9 = d8 - attributeRangeMax;

                return attributeRangeMax - d9;
            } else if (d8 < attributeRangeMin) {
                double d10 = attributeRangeMin - d8;

                return attributeRangeMin + d10;
            } else {
                return d8;
            }
        }
    }

    public float getEatAnim(float a) {
        return Mth.lerp(a, this.eatAnimO, this.eatAnim);
    }

    public float getStandAnim(float a) {
        return Mth.lerp(a, this.standAnimO, this.standAnim);
    }

    public float getMouthAnim(float a) {
        return Mth.lerp(a, this.mouthAnimO, this.mouthAnim);
    }

    @Override
    public void onPlayerJump(int jumpAmount) {
        if (this.isSaddled()) {
            if (jumpAmount < 0) {
                jumpAmount = 0;
            } else {
                this.allowStandSliding = true;
                this.standIfPossible();
            }

            this.playerJumpPendingScale = this.getPlayerJumpPendingScale(jumpAmount);
        }
    }

    @Override
    public boolean canJump() {
        return this.isSaddled();
    }

    @Override
    public void handleStartJump(int jumpScale) {
        this.allowStandSliding = true;
        this.standIfPossible();
        this.playJumpSound();
    }

    @Override
    public void handleStopJump() {}

    protected void spawnTamingParticles(boolean success) {
        ParticleOptions particleoptions = success ? ParticleTypes.HEART : ParticleTypes.SMOKE;

        for (int i = 0; i < 7; ++i) {
            double d0 = this.random.nextGaussian() * 0.02D;
            double d1 = this.random.nextGaussian() * 0.02D;
            double d2 = this.random.nextGaussian() * 0.02D;

            this.level().addParticle(particleoptions, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), d0, d1, d2);
        }

    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 7) {
            this.spawnTamingParticles(true);
        } else if (id == 6) {
            this.spawnTamingParticles(false);
        } else {
            super.handleEntityEvent(id);
        }

    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        super.positionRider(passenger, moveFunction);
        if (passenger instanceof LivingEntity) {
            ((LivingEntity) passenger).yBodyRot = this.yBodyRot;
        }

    }

    protected static float generateMaxHealth(IntUnaryOperator integerByBoundProvider) {
        return 15.0F + (float) integerByBoundProvider.applyAsInt(8) + (float) integerByBoundProvider.applyAsInt(9);
    }

    protected static double generateJumpStrength(DoubleSupplier probabilityProvider) {
        return (double) 0.4F + probabilityProvider.getAsDouble() * 0.2D + probabilityProvider.getAsDouble() * 0.2D + probabilityProvider.getAsDouble() * 0.2D;
    }

    protected static double generateSpeed(DoubleSupplier probabilityProvider) {
        return ((double) 0.45F + probabilityProvider.getAsDouble() * 0.3D + probabilityProvider.getAsDouble() * 0.3D + probabilityProvider.getAsDouble() * 0.3D) * 0.25D;
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public @Nullable SlotAccess getSlot(int slot) {
        int j = slot - 500;

        return j >= 0 && j < this.inventory.getContainerSize() ? this.inventory.getSlot(j) : super.getSlot(slot);
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        if (this.isSaddled()) {
            Entity entity = this.getFirstPassenger();

            if (entity instanceof Player) {
                Player player = (Player) entity;

                return player;
            }
        }

        return super.getControllingPassenger();
    }

    private @Nullable Vec3 getDismountLocationInDirection(Vec3 direction, LivingEntity passenger) {
        double d0 = this.getX() + direction.x;
        double d1 = this.getBoundingBox().minY;
        double d2 = this.getZ() + direction.z;
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        UnmodifiableIterator unmodifiableiterator = passenger.getDismountPoses().iterator();

        while (unmodifiableiterator.hasNext()) {
            Pose pose = (Pose) unmodifiableiterator.next();

            blockpos_mutableblockpos.set(d0, d1, d2);
            double d3 = this.getBoundingBox().maxY + 0.75D;

            while (true) {
                double d4 = this.level().getBlockFloorHeight(blockpos_mutableblockpos);

                if ((double) blockpos_mutableblockpos.getY() + d4 > d3) {
                    break;
                }

                if (DismountHelper.isBlockFloorValid(d4)) {
                    AABB aabb = passenger.getLocalBoundsForPose(pose);
                    Vec3 vec31 = new Vec3(d0, (double) blockpos_mutableblockpos.getY() + d4, d2);

                    if (DismountHelper.canDismountTo(this.level(), passenger, aabb.move(vec31))) {
                        passenger.setPose(pose);
                        return vec31;
                    }
                }

                blockpos_mutableblockpos.move(Direction.UP);
                if ((double) blockpos_mutableblockpos.getY() >= d3) {
                    break;
                }
            }
        }

        return null;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 vec3 = getCollisionHorizontalEscapeVector((double) this.getBbWidth(), (double) passenger.getBbWidth(), this.getYRot() + (passenger.getMainArm() == HumanoidArm.RIGHT ? 90.0F : -90.0F));
        Vec3 vec31 = this.getDismountLocationInDirection(vec3, passenger);

        if (vec31 != null) {
            return vec31;
        } else {
            Vec3 vec32 = getCollisionHorizontalEscapeVector((double) this.getBbWidth(), (double) passenger.getBbWidth(), this.getYRot() + (passenger.getMainArm() == HumanoidArm.LEFT ? 90.0F : -90.0F));
            Vec3 vec33 = this.getDismountLocationInDirection(vec32, passenger);

            return vec33 != null ? vec33 : this.position();
        }
    }

    protected void randomizeAttributes(RandomSource random) {}

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        if (groupData == null) {
            groupData = new AgeableMob.AgeableMobGroupData(0.2F);
        }

        this.randomizeAttributes(level.getRandom());
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    public boolean hasInventoryChanged(Container oldInventory) {
        return this.inventory != oldInventory;
    }

    public int getAmbientStandInterval() {
        return this.getAmbientSoundInterval();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return super.getPassengerAttachmentPoint(passenger, dimensions, scale).add((new Vec3(0.0D, 0.15D * (double) this.standAnimO * (double) scale, -0.7D * (double) this.standAnimO * (double) scale)).yRot(-this.getYRot() * ((float) Math.PI / 180F)));
    }

    public int getInventoryColumns() {
        return 0;
    }

    private class MountPanicGoal extends PanicGoal {

        public MountPanicGoal(double speedModifier) {
            super(AbstractHorse.this, speedModifier);
        }

        @Override
        public boolean shouldPanic() {
            return !AbstractHorse.this.isMobControlled() && super.shouldPanic();
        }
    }
}

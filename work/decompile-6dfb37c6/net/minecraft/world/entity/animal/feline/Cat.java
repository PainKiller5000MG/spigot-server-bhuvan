package net.minecraft.world.entity.animal.feline;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.CatLieOnBedGoal;
import net.minecraft.world.entity.ai.goal.CatSitOnBlockGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OcelotAttackGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class Cat extends TamableAnimal {

    public static final double TEMPT_SPEED_MOD = 0.6D;
    public static final double WALK_SPEED_MOD = 0.8D;
    public static final double SPRINT_SPEED_MOD = 1.33D;
    private static final EntityDataAccessor<Holder<CatVariant>> DATA_VARIANT_ID = SynchedEntityData.<Holder<CatVariant>>defineId(Cat.class, EntityDataSerializers.CAT_VARIANT);
    private static final EntityDataAccessor<Boolean> IS_LYING = SynchedEntityData.<Boolean>defineId(Cat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RELAX_STATE_ONE = SynchedEntityData.<Boolean>defineId(Cat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_COLLAR_COLOR = SynchedEntityData.<Integer>defineId(Cat.class, EntityDataSerializers.INT);
    private static final ResourceKey<CatVariant> DEFAULT_VARIANT = CatVariants.BLACK;
    private static final DyeColor DEFAULT_COLLAR_COLOR = DyeColor.RED;
    private Cat.@Nullable CatAvoidEntityGoal<Player> avoidPlayersGoal;
    private @Nullable TemptGoal temptGoal;
    private float lieDownAmount;
    private float lieDownAmountO;
    private float lieDownAmountTail;
    private float lieDownAmountOTail;
    private boolean isLyingOnTopOfSleepingPlayer;
    private float relaxStateOneAmount;
    private float relaxStateOneAmountO;

    public Cat(EntityType<? extends Cat> type, Level level) {
        super(type, level);
        this.reassessTameGoals();
    }

    @Override
    protected void registerGoals() {
        this.temptGoal = new Cat.CatTemptGoal(this, 0.6D, (itemstack) -> {
            return itemstack.is(ItemTags.CAT_FOOD);
        }, true);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TamableAnimal.TamableAnimalPanicGoal(1.5D));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(3, new Cat.CatRelaxOnOwnerGoal(this));
        this.goalSelector.addGoal(4, this.temptGoal);
        this.goalSelector.addGoal(5, new CatLieOnBedGoal(this, 1.1D, 8));
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0D, 10.0F, 5.0F));
        this.goalSelector.addGoal(7, new CatSitOnBlockGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LeapAtTargetGoal(this, 0.3F));
        this.goalSelector.addGoal(9, new OcelotAttackGoal(this));
        this.goalSelector.addGoal(10, new BreedGoal(this, 0.8D));
        this.goalSelector.addGoal(11, new WaterAvoidingRandomStrollGoal(this, 0.8D, 1.0000001E-5F));
        this.goalSelector.addGoal(12, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.targetSelector.addGoal(1, new NonTameRandomTargetGoal(this, Rabbit.class, false, (TargetingConditions.Selector) null));
        this.targetSelector.addGoal(1, new NonTameRandomTargetGoal(this, Turtle.class, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    public Holder<CatVariant> getVariant() {
        return (Holder) this.entityData.get(Cat.DATA_VARIANT_ID);
    }

    public void setVariant(Holder<CatVariant> holder) {
        this.entityData.set(Cat.DATA_VARIANT_ID, holder);
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        return (T) (type == DataComponents.CAT_VARIANT ? castComponentValue(type, this.getVariant()) : (type == DataComponents.CAT_COLLAR ? castComponentValue(type, this.getCollarColor()) : super.get(type)));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        this.applyImplicitComponentIfPresent(components, DataComponents.CAT_VARIANT);
        this.applyImplicitComponentIfPresent(components, DataComponents.CAT_COLLAR);
        super.applyImplicitComponents(components);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.CAT_VARIANT) {
            this.setVariant((Holder) castComponentValue(DataComponents.CAT_VARIANT, value));
            return true;
        } else if (type == DataComponents.CAT_COLLAR) {
            this.setCollarColor((DyeColor) castComponentValue(DataComponents.CAT_COLLAR, value));
            return true;
        } else {
            return super.applyImplicitComponent(type, value);
        }
    }

    public void setLying(boolean value) {
        this.entityData.set(Cat.IS_LYING, value);
    }

    public boolean isLying() {
        return (Boolean) this.entityData.get(Cat.IS_LYING);
    }

    private void setRelaxStateOne(boolean value) {
        this.entityData.set(Cat.RELAX_STATE_ONE, value);
    }

    private boolean isRelaxStateOne() {
        return (Boolean) this.entityData.get(Cat.RELAX_STATE_ONE);
    }

    public DyeColor getCollarColor() {
        return DyeColor.byId((Integer) this.entityData.get(Cat.DATA_COLLAR_COLOR));
    }

    public void setCollarColor(DyeColor color) {
        this.entityData.set(Cat.DATA_COLLAR_COLOR, color.getId());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Cat.DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), Cat.DEFAULT_VARIANT));
        entityData.define(Cat.IS_LYING, false);
        entityData.define(Cat.RELAX_STATE_ONE, false);
        entityData.define(Cat.DATA_COLLAR_COLOR, Cat.DEFAULT_COLLAR_COLOR.getId());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        VariantUtils.writeVariant(output, this.getVariant());
        output.store("CollarColor", DyeColor.LEGACY_ID_CODEC, this.getCollarColor());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        VariantUtils.readVariant(input, Registries.CAT_VARIANT).ifPresent(this::setVariant);
        this.setCollarColor((DyeColor) input.read("CollarColor", DyeColor.LEGACY_ID_CODEC).orElse(Cat.DEFAULT_COLLAR_COLOR));
    }

    @Override
    public void customServerAiStep(ServerLevel level) {
        if (this.getMoveControl().hasWanted()) {
            double d0 = this.getMoveControl().getSpeedModifier();

            if (d0 == 0.6D) {
                this.setPose(Pose.CROUCHING);
                this.setSprinting(false);
            } else if (d0 == 1.33D) {
                this.setPose(Pose.STANDING);
                this.setSprinting(true);
            } else {
                this.setPose(Pose.STANDING);
                this.setSprinting(false);
            }
        } else {
            this.setPose(Pose.STANDING);
            this.setSprinting(false);
        }

    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return this.isTame() ? (this.isInLove() ? SoundEvents.CAT_PURR : (this.random.nextInt(4) == 0 ? SoundEvents.CAT_PURREOW : SoundEvents.CAT_AMBIENT)) : SoundEvents.CAT_STRAY_AMBIENT;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    public void hiss() {
        this.makeSound(SoundEvents.CAT_HISS);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.CAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.CAT_DEATH;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 10.0D).add(Attributes.MOVEMENT_SPEED, (double) 0.3F).add(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected void playEatingSound() {
        this.playSound(SoundEvents.CAT_EAT, 1.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.temptGoal != null && this.temptGoal.isRunning() && !this.isTame() && this.tickCount % 100 == 0) {
            this.playSound(SoundEvents.CAT_BEG_FOR_FOOD, 1.0F, 1.0F);
        }

        this.handleLieDown();
    }

    private void handleLieDown() {
        if ((this.isLying() || this.isRelaxStateOne()) && this.tickCount % 5 == 0) {
            this.playSound(SoundEvents.CAT_PURR, 0.6F + 0.4F * (this.random.nextFloat() - this.random.nextFloat()), 1.0F);
        }

        this.updateLieDownAmount();
        this.updateRelaxStateOneAmount();
        this.isLyingOnTopOfSleepingPlayer = false;
        if (this.isLying()) {
            BlockPos blockpos = this.blockPosition();

            for (Player player : this.level().getEntitiesOfClass(Player.class, (new AABB(blockpos)).inflate(2.0D, 2.0D, 2.0D))) {
                if (player.isSleeping()) {
                    this.isLyingOnTopOfSleepingPlayer = true;
                    break;
                }
            }
        }

    }

    public boolean isLyingOnTopOfSleepingPlayer() {
        return this.isLyingOnTopOfSleepingPlayer;
    }

    private void updateLieDownAmount() {
        this.lieDownAmountO = this.lieDownAmount;
        this.lieDownAmountOTail = this.lieDownAmountTail;
        if (this.isLying()) {
            this.lieDownAmount = Math.min(1.0F, this.lieDownAmount + 0.15F);
            this.lieDownAmountTail = Math.min(1.0F, this.lieDownAmountTail + 0.08F);
        } else {
            this.lieDownAmount = Math.max(0.0F, this.lieDownAmount - 0.22F);
            this.lieDownAmountTail = Math.max(0.0F, this.lieDownAmountTail - 0.13F);
        }

    }

    private void updateRelaxStateOneAmount() {
        this.relaxStateOneAmountO = this.relaxStateOneAmount;
        if (this.isRelaxStateOne()) {
            this.relaxStateOneAmount = Math.min(1.0F, this.relaxStateOneAmount + 0.1F);
        } else {
            this.relaxStateOneAmount = Math.max(0.0F, this.relaxStateOneAmount - 0.13F);
        }

    }

    public float getLieDownAmount(float a) {
        return Mth.lerp(a, this.lieDownAmountO, this.lieDownAmount);
    }

    public float getLieDownAmountTail(float a) {
        return Mth.lerp(a, this.lieDownAmountOTail, this.lieDownAmountTail);
    }

    public float getRelaxStateOneAmount(float a) {
        return Mth.lerp(a, this.relaxStateOneAmountO, this.relaxStateOneAmount);
    }

    @Override
    public @Nullable Cat getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Cat cat = EntityType.CAT.create(level, EntitySpawnReason.BREEDING);

        if (cat != null && partner instanceof Cat cat1) {
            if (this.random.nextBoolean()) {
                cat.setVariant(this.getVariant());
            } else {
                cat.setVariant(cat1.getVariant());
            }

            if (this.isTame()) {
                cat.setOwnerReference(this.getOwnerReference());
                cat.setTame(true, true);
                DyeColor dyecolor = this.getCollarColor();
                DyeColor dyecolor1 = cat1.getCollarColor();

                cat.setCollarColor(DyeColor.getMixedColor(level, dyecolor, dyecolor1));
            }
        }

        return cat;
    }

    @Override
    public boolean canMate(Animal partner) {
        if (!this.isTame()) {
            return false;
        } else if (!(partner instanceof Cat)) {
            return false;
        } else {
            Cat cat = (Cat) partner;

            return cat.isTame() && super.canMate(partner);
        }
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        groupData = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        VariantUtils.selectVariantToSpawn(SpawnContext.create(level, this.blockPosition()), Registries.CAT_VARIANT).ifPresent(this::setVariant);
        return groupData;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        Item item = itemstack.getItem();

        if (this.isTame()) {
            if (this.isOwnedBy(player)) {
                if (item instanceof DyeItem) {
                    DyeItem dyeitem = (DyeItem) item;
                    DyeColor dyecolor = dyeitem.getDyeColor();

                    if (dyecolor != this.getCollarColor()) {
                        if (!this.level().isClientSide()) {
                            this.setCollarColor(dyecolor);
                            itemstack.consume(1, player);
                            this.setPersistenceRequired();
                        }

                        return InteractionResult.SUCCESS;
                    }
                } else if (this.isFood(itemstack) && this.getHealth() < this.getMaxHealth()) {
                    if (!this.level().isClientSide()) {
                        this.usePlayerItem(player, hand, itemstack);
                        FoodProperties foodproperties = (FoodProperties) itemstack.get(DataComponents.FOOD);

                        this.heal(foodproperties != null ? (float) foodproperties.nutrition() : 1.0F);
                        this.playEatingSound();
                    }

                    return InteractionResult.SUCCESS;
                }

                InteractionResult interactionresult = super.mobInteract(player, hand);

                if (!interactionresult.consumesAction()) {
                    this.setOrderedToSit(!this.isOrderedToSit());
                    return InteractionResult.SUCCESS;
                }

                return interactionresult;
            }
        } else if (this.isFood(itemstack)) {
            if (!this.level().isClientSide()) {
                this.usePlayerItem(player, hand, itemstack);
                this.tryToTame(player);
                this.setPersistenceRequired();
                this.playEatingSound();
            }

            return InteractionResult.SUCCESS;
        }

        InteractionResult interactionresult1 = super.mobInteract(player, hand);

        if (interactionresult1.consumesAction()) {
            this.setPersistenceRequired();
        }

        return interactionresult1;
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return itemStack.is(ItemTags.CAT_FOOD);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return !this.isTame() && this.tickCount > 2400;
    }

    @Override
    public void setTame(boolean isTame, boolean includeSideEffects) {
        super.setTame(isTame, includeSideEffects);
        this.reassessTameGoals();
    }

    protected void reassessTameGoals() {
        if (this.avoidPlayersGoal == null) {
            this.avoidPlayersGoal = new Cat.CatAvoidEntityGoal<Player>(this, Player.class, 16.0F, 0.8D, 1.33D);
        }

        this.goalSelector.removeGoal(this.avoidPlayersGoal);
        if (!this.isTame()) {
            this.goalSelector.addGoal(4, this.avoidPlayersGoal);
        }

    }

    private void tryToTame(Player player) {
        if (this.random.nextInt(3) == 0) {
            this.tame(player);
            this.setOrderedToSit(true);
            this.level().broadcastEntityEvent(this, (byte) 7);
        } else {
            this.level().broadcastEntityEvent(this, (byte) 6);
        }

    }

    @Override
    public boolean isSteppingCarefully() {
        return this.isCrouching() || super.isSteppingCarefully();
    }

    private static class CatAvoidEntityGoal<T extends LivingEntity> extends AvoidEntityGoal<T> {

        private final Cat cat;

        public CatAvoidEntityGoal(Cat cat, Class<T> avoidClass, float maxDist, double walkSpeedModifier, double sprintSpeedModifier) {
            super(cat, avoidClass, maxDist, walkSpeedModifier, sprintSpeedModifier, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
            this.cat = cat;
        }

        @Override
        public boolean canUse() {
            return !this.cat.isTame() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.cat.isTame() && super.canContinueToUse();
        }
    }

    private static class CatTemptGoal extends TemptGoal {

        private @Nullable Player selectedPlayer;
        private final Cat cat;

        public CatTemptGoal(Cat mob, double speedModifier, Predicate<ItemStack> items, boolean canScare) {
            super(mob, speedModifier, items, canScare);
            this.cat = mob;
        }

        @Override
        public void tick() {
            super.tick();
            if (this.selectedPlayer == null && this.mob.getRandom().nextInt(this.adjustedTickDelay(600)) == 0) {
                this.selectedPlayer = this.player;
            } else if (this.mob.getRandom().nextInt(this.adjustedTickDelay(500)) == 0) {
                this.selectedPlayer = null;
            }

        }

        @Override
        protected boolean canScare() {
            return this.selectedPlayer != null && this.selectedPlayer.equals(this.player) ? false : super.canScare();
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !this.cat.isTame();
        }
    }

    private static class CatRelaxOnOwnerGoal extends Goal {

        private final Cat cat;
        private @Nullable Player ownerPlayer;
        private @Nullable BlockPos goalPos;
        private int onBedTicks;

        public CatRelaxOnOwnerGoal(Cat cat) {
            this.cat = cat;
        }

        @Override
        public boolean canUse() {
            if (!this.cat.isTame()) {
                return false;
            } else if (this.cat.isOrderedToSit()) {
                return false;
            } else {
                LivingEntity livingentity = this.cat.getOwner();

                if (livingentity instanceof Player) {
                    Player player = (Player) livingentity;

                    this.ownerPlayer = player;
                    if (!livingentity.isSleeping()) {
                        return false;
                    }

                    if (this.cat.distanceToSqr((Entity) this.ownerPlayer) > 100.0D) {
                        return false;
                    }

                    BlockPos blockpos = this.ownerPlayer.blockPosition();
                    BlockState blockstate = this.cat.level().getBlockState(blockpos);

                    if (blockstate.is(BlockTags.BEDS)) {
                        this.goalPos = (BlockPos) blockstate.getOptionalValue(BedBlock.FACING).map((direction) -> {
                            return blockpos.relative(direction.getOpposite());
                        }).orElseGet(() -> {
                            return new BlockPos(blockpos);
                        });
                        return !this.spaceIsOccupied();
                    }
                }

                return false;
            }
        }

        private boolean spaceIsOccupied() {
            for (Cat cat : this.cat.level().getEntitiesOfClass(Cat.class, (new AABB(this.goalPos)).inflate(2.0D))) {
                if (cat != this.cat && (cat.isLying() || cat.isRelaxStateOne())) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return this.cat.isTame() && !this.cat.isOrderedToSit() && this.ownerPlayer != null && this.ownerPlayer.isSleeping() && this.goalPos != null && !this.spaceIsOccupied();
        }

        @Override
        public void start() {
            if (this.goalPos != null) {
                this.cat.setInSittingPose(false);
                this.cat.getNavigation().moveTo((double) this.goalPos.getX(), (double) this.goalPos.getY(), (double) this.goalPos.getZ(), (double) 1.1F);
            }

        }

        @Override
        public void stop() {
            this.cat.setLying(false);
            if (this.ownerPlayer.getSleepTimer() >= 100 && this.cat.level().getRandom().nextFloat() < (Float) this.cat.level().environmentAttributes().getValue(EnvironmentAttributes.CAT_WAKING_UP_GIFT_CHANCE, this.cat.position())) {
                this.giveMorningGift();
            }

            this.onBedTicks = 0;
            this.cat.setRelaxStateOne(false);
            this.cat.getNavigation().stop();
        }

        private void giveMorningGift() {
            RandomSource randomsource = this.cat.getRandom();
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

            blockpos_mutableblockpos.set(this.cat.isLeashed() ? this.cat.getLeashHolder().blockPosition() : this.cat.blockPosition());
            this.cat.randomTeleport((double) (blockpos_mutableblockpos.getX() + randomsource.nextInt(11) - 5), (double) (blockpos_mutableblockpos.getY() + randomsource.nextInt(5) - 2), (double) (blockpos_mutableblockpos.getZ() + randomsource.nextInt(11) - 5), false);
            blockpos_mutableblockpos.set(this.cat.blockPosition());
            this.cat.dropFromGiftLootTable(getServerLevel((Entity) this.cat), BuiltInLootTables.CAT_MORNING_GIFT, (serverlevel, itemstack) -> {
                serverlevel.addFreshEntity(new ItemEntity(serverlevel, (double) blockpos_mutableblockpos.getX() - (double) Mth.sin((double) (this.cat.yBodyRot * ((float) Math.PI / 180F))), (double) blockpos_mutableblockpos.getY(), (double) blockpos_mutableblockpos.getZ() + (double) Mth.cos((double) (this.cat.yBodyRot * ((float) Math.PI / 180F))), itemstack));
            });
        }

        @Override
        public void tick() {
            if (this.ownerPlayer != null && this.goalPos != null) {
                this.cat.setInSittingPose(false);
                this.cat.getNavigation().moveTo((double) this.goalPos.getX(), (double) this.goalPos.getY(), (double) this.goalPos.getZ(), (double) 1.1F);
                if (this.cat.distanceToSqr((Entity) this.ownerPlayer) < 2.5D) {
                    ++this.onBedTicks;
                    if (this.onBedTicks > this.adjustedTickDelay(16)) {
                        this.cat.setLying(true);
                        this.cat.setRelaxStateOne(false);
                    } else {
                        this.cat.lookAt(this.ownerPlayer, 45.0F, 45.0F);
                        this.cat.setRelaxStateOne(true);
                    }
                } else {
                    this.cat.setLying(false);
                }
            }

        }
    }
}

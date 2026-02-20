package net.minecraft.world.entity.monster.piglin;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class Piglin extends AbstractPiglin implements CrossbowAttackMob, InventoryCarrier {

    private static final EntityDataAccessor<Boolean> DATA_BABY_ID = SynchedEntityData.<Boolean>defineId(Piglin.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING_CROSSBOW = SynchedEntityData.<Boolean>defineId(Piglin.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_IS_DANCING = SynchedEntityData.<Boolean>defineId(Piglin.class, EntityDataSerializers.BOOLEAN);
    private static final Identifier SPEED_MODIFIER_BABY_ID = Identifier.withDefaultNamespace("baby");
    private static final AttributeModifier SPEED_MODIFIER_BABY = new AttributeModifier(Piglin.SPEED_MODIFIER_BABY_ID, (double) 0.2F, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    private static final int MAX_HEALTH = 16;
    private static final float MOVEMENT_SPEED_WHEN_FIGHTING = 0.35F;
    private static final int ATTACK_DAMAGE = 5;
    private static final float CHANCE_OF_WEARING_EACH_ARMOUR_ITEM = 0.1F;
    private static final int MAX_PASSENGERS_ON_ONE_HOGLIN = 3;
    private static final float PROBABILITY_OF_SPAWNING_AS_BABY = 0.2F;
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.PIGLIN.getDimensions().scale(0.5F).withEyeHeight(0.97F);
    private static final double PROBABILITY_OF_SPAWNING_WITH_CROSSBOW_INSTEAD_OF_SWORD = 0.5D;
    private static final boolean DEFAULT_IS_BABY = false;
    private static final boolean DEFAULT_CANNOT_HUNT = false;
    public final SimpleContainer inventory = new SimpleContainer(8);
    public boolean cannotHunt = false;
    protected static final ImmutableList<SensorType<? extends Sensor<? super Piglin>>> SENSOR_TYPES = ImmutableList.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.NEAREST_ITEMS, SensorType.HURT_BY, SensorType.PIGLIN_SPECIFIC_SENSOR);
    protected static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(MemoryModuleType.LOOK_TARGET, MemoryModuleType.DOORS_TO_CLOSE, MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLINS, MemoryModuleType.NEARBY_ADULT_PIGLINS, MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY, new MemoryModuleType[]{MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.ATTACK_TARGET, MemoryModuleType.ATTACK_COOLING_DOWN, MemoryModuleType.INTERACTION_TARGET, MemoryModuleType.PATH, MemoryModuleType.ANGRY_AT, MemoryModuleType.UNIVERSAL_ANGER, MemoryModuleType.AVOID_TARGET, MemoryModuleType.ADMIRING_ITEM, MemoryModuleType.TIME_TRYING_TO_REACH_ADMIRE_ITEM, MemoryModuleType.ADMIRING_DISABLED, MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM, MemoryModuleType.CELEBRATE_LOCATION, MemoryModuleType.DANCING, MemoryModuleType.HUNTED_RECENTLY, MemoryModuleType.NEAREST_VISIBLE_BABY_HOGLIN, MemoryModuleType.NEAREST_VISIBLE_NEMESIS, MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED, MemoryModuleType.RIDE_TARGET, MemoryModuleType.VISIBLE_ADULT_PIGLIN_COUNT, MemoryModuleType.VISIBLE_ADULT_HOGLIN_COUNT, MemoryModuleType.NEAREST_VISIBLE_HUNTABLE_HOGLIN, MemoryModuleType.NEAREST_TARGETABLE_PLAYER_NOT_WEARING_GOLD, MemoryModuleType.NEAREST_PLAYER_HOLDING_WANTED_ITEM, MemoryModuleType.ATE_RECENTLY, MemoryModuleType.NEAREST_REPELLENT, MemoryModuleType.SPEAR_FLEEING_TIME, MemoryModuleType.SPEAR_FLEEING_POSITION, MemoryModuleType.SPEAR_CHARGE_POSITION, MemoryModuleType.SPEAR_ENGAGE_TIME, MemoryModuleType.SPEAR_STATUS});

    public Piglin(EntityType<? extends AbstractPiglin> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("IsBaby", this.isBaby());
        output.putBoolean("CannotHunt", this.cannotHunt);
        this.writeInventoryToTag(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setBaby(input.getBooleanOr("IsBaby", false));
        this.setCannotHunt(input.getBooleanOr("CannotHunt", false));
        this.readInventoryFromTag(input);
    }

    @VisibleForDebug
    @Override
    public SimpleContainer getInventory() {
        return this.inventory;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        this.inventory.removeAllItems().forEach((itemstack) -> {
            this.spawnAtLocation(level, itemstack);
        });
    }

    protected ItemStack addToInventory(ItemStack itemStack) {
        return this.inventory.addItem(itemStack);
    }

    protected boolean canAddToInventory(ItemStack itemStack) {
        return this.inventory.canAddItem(itemStack);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Piglin.DATA_BABY_ID, false);
        entityData.define(Piglin.DATA_IS_CHARGING_CROSSBOW, false);
        entityData.define(Piglin.DATA_IS_DANCING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (Piglin.DATA_BABY_ID.equals(accessor)) {
            this.refreshDimensions();
        }

    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 16.0D).add(Attributes.MOVEMENT_SPEED, (double) 0.35F).add(Attributes.ATTACK_DAMAGE, 5.0D);
    }

    public static boolean checkPiglinSpawnRules(EntityType<Piglin> type, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random) {
        return !level.getBlockState(pos.below()).is(Blocks.NETHER_WART_BLOCK);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        RandomSource randomsource = level.getRandom();

        if (spawnReason != EntitySpawnReason.STRUCTURE) {
            if (randomsource.nextFloat() < 0.2F) {
                this.setBaby(true);
            } else if (this.isAdult()) {
                this.setItemSlot(EquipmentSlot.MAINHAND, this.createSpawnWeapon());
            }
        }

        PiglinAi.initMemories(this, level.getRandom());
        this.populateDefaultEquipmentSlots(randomsource, difficulty);
        this.populateDefaultEquipmentEnchantments(level, randomsource, difficulty);
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return !this.isPersistenceRequired();
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        if (this.isAdult()) {
            this.maybeWearArmor(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET), random);
            this.maybeWearArmor(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE), random);
            this.maybeWearArmor(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS), random);
            this.maybeWearArmor(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS), random);
        }

    }

    private void maybeWearArmor(EquipmentSlot slot, ItemStack itemStack, RandomSource random) {
        if (random.nextFloat() < 0.1F) {
            this.setItemSlot(slot, itemStack);
        }

    }

    @Override
    protected Brain.Provider<Piglin> brainProvider() {
        return Brain.<Piglin>provider(Piglin.MEMORY_TYPES, Piglin.SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> input) {
        return PiglinAi.makeBrain(this, this.brainProvider().makeBrain(input));
    }

    @Override
    public Brain<Piglin> getBrain() {
        return super.getBrain();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        InteractionResult interactionresult = super.mobInteract(player, hand);

        if (interactionresult.consumesAction()) {
            return interactionresult;
        } else {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                return PiglinAi.mobInteract(serverlevel, this, player, hand);
            } else {
                boolean flag = PiglinAi.canAdmire(this, player.getItemInHand(hand)) && this.getArmPose() != PiglinArmPose.ADMIRING_ITEM;

                return (InteractionResult) (flag ? InteractionResult.SUCCESS : InteractionResult.PASS);
            }
        }
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? Piglin.BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    @Override
    public void setBaby(boolean baby) {
        this.getEntityData().set(Piglin.DATA_BABY_ID, baby);
        if (!this.level().isClientSide()) {
            AttributeInstance attributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);

            attributeinstance.removeModifier(Piglin.SPEED_MODIFIER_BABY.id());
            if (baby) {
                attributeinstance.addTransientModifier(Piglin.SPEED_MODIFIER_BABY);
            }
        }

    }

    @Override
    public boolean isBaby() {
        return (Boolean) this.getEntityData().get(Piglin.DATA_BABY_ID);
    }

    private void setCannotHunt(boolean cannotHunt) {
        this.cannotHunt = cannotHunt;
    }

    @Override
    protected boolean canHunt() {
        return !this.cannotHunt;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("piglinBrain");
        this.getBrain().tick(level, this);
        profilerfiller.pop();
        PiglinAi.updateActivity(this);
        super.customServerAiStep(level);
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return this.xpReward;
    }

    @Override
    protected void finishConversion(ServerLevel level) {
        PiglinAi.cancelAdmiring(level, this);
        this.inventory.removeAllItems().forEach((itemstack) -> {
            this.spawnAtLocation(level, itemstack);
        });
        super.finishConversion(level);
    }

    private ItemStack createSpawnWeapon() {
        return (double) this.random.nextFloat() < 0.5D ? new ItemStack(Items.CROSSBOW) : new ItemStack(this.random.nextInt(10) == 0 ? Items.GOLDEN_SPEAR : Items.GOLDEN_SWORD);
    }

    @Override
    public @Nullable TagKey<Item> getPreferredWeaponType() {
        return this.isBaby() ? null : ItemTags.PIGLIN_PREFERRED_WEAPONS;
    }

    private boolean isChargingCrossbow() {
        return (Boolean) this.entityData.get(Piglin.DATA_IS_CHARGING_CROSSBOW);
    }

    @Override
    public void setChargingCrossbow(boolean isCharging) {
        this.entityData.set(Piglin.DATA_IS_CHARGING_CROSSBOW, isCharging);
    }

    @Override
    public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

    @Override
    public PiglinArmPose getArmPose() {
        return this.isDancing() ? PiglinArmPose.DANCING : (PiglinAi.isLovedItem(this.getOffhandItem()) ? PiglinArmPose.ADMIRING_ITEM : (this.isAggressive() && this.isHoldingMeleeWeapon() ? PiglinArmPose.ATTACKING_WITH_MELEE_WEAPON : (this.isChargingCrossbow() ? PiglinArmPose.CROSSBOW_CHARGE : (this.isHolding(Items.CROSSBOW) && CrossbowItem.isCharged(this.getWeaponItem()) ? PiglinArmPose.CROSSBOW_HOLD : PiglinArmPose.DEFAULT))));
    }

    public boolean isDancing() {
        return (Boolean) this.entityData.get(Piglin.DATA_IS_DANCING);
    }

    public void setDancing(boolean dancing) {
        this.entityData.set(Piglin.DATA_IS_DANCING, dancing);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean flag = super.hurtServer(level, source, damage);

        if (flag) {
            Entity entity = source.getEntity();

            if (entity instanceof LivingEntity) {
                LivingEntity livingentity = (LivingEntity) entity;

                PiglinAi.wasHurtBy(level, this, livingentity);
            }
        }

        return flag;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        this.performCrossbowAttack(this, 1.6F);
    }

    @Override
    public boolean canUseNonMeleeWeapon(ItemStack item) {
        return item.getItem() == Items.CROSSBOW || item.has(DataComponents.KINETIC_WEAPON);
    }

    protected void holdInMainHand(ItemStack itemStack) {
        this.setItemSlotAndDropWhenKilled(EquipmentSlot.MAINHAND, itemStack);
    }

    protected void holdInOffHand(ItemStack itemStack) {
        if (itemStack.is(PiglinAi.BARTERING_ITEM)) {
            this.setItemSlot(EquipmentSlot.OFFHAND, itemStack);
            this.setGuaranteedDrop(EquipmentSlot.OFFHAND);
        } else {
            this.setItemSlotAndDropWhenKilled(EquipmentSlot.OFFHAND, itemStack);
        }

    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack itemStack) {
        return (Boolean) level.getGameRules().get(GameRules.MOB_GRIEFING) && this.canPickUpLoot() && PiglinAi.wantsToPickup(this, itemStack);
    }

    protected boolean canReplaceCurrentItem(ItemStack newItemStack) {
        EquipmentSlot equipmentslot = this.getEquipmentSlotForItem(newItemStack);
        ItemStack itemstack1 = this.getItemBySlot(equipmentslot);

        return this.canReplaceCurrentItem(newItemStack, itemstack1, equipmentslot);
    }

    @Override
    protected boolean canReplaceCurrentItem(ItemStack newItemStack, ItemStack currentItemStack, EquipmentSlot slot) {
        if (EnchantmentHelper.has(currentItemStack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
            return false;
        } else {
            TagKey<Item> tagkey = this.getPreferredWeaponType();
            boolean flag = PiglinAi.isLovedItem(newItemStack) || tagkey != null && newItemStack.is(tagkey);
            boolean flag1 = PiglinAi.isLovedItem(currentItemStack) || tagkey != null && currentItemStack.is(tagkey);

            return flag && !flag1 ? true : (!flag && flag1 ? false : super.canReplaceCurrentItem(newItemStack, currentItemStack, slot));
        }
    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity entity) {
        this.onItemPickup(entity);
        PiglinAi.pickUpItem(level, this, entity);
    }

    @Override
    public boolean startRiding(Entity entityToRide, boolean force, boolean sendEventAndTriggers) {
        if (this.isBaby() && entityToRide.getType() == EntityType.HOGLIN) {
            entityToRide = this.getTopPassenger(entityToRide, 3);
        }

        return super.startRiding(entityToRide, force, sendEventAndTriggers);
    }

    private Entity getTopPassenger(Entity vehicle, int counter) {
        List<Entity> list = vehicle.getPassengers();

        return counter != 1 && !list.isEmpty() ? this.getTopPassenger((Entity) list.getFirst(), counter - 1) : vehicle;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return this.level().isClientSide() ? null : (SoundEvent) PiglinAi.getSoundForCurrentActivity(this).orElse((Object) null);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PIGLIN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PIGLIN_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
        this.playSound(SoundEvents.PIGLIN_STEP, 0.15F, 1.0F);
    }

    @Override
    protected void playConvertedSound() {
        this.makeSound(SoundEvents.PIGLIN_CONVERTED_TO_ZOMBIFIED);
    }
}

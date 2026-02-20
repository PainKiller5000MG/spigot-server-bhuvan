package net.minecraft.world.entity;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.debug.DebugBrainDump;
import net.minecraft.util.debug.DebugGoalInfo;
import net.minecraft.util.debug.DebugPathInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensing;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ContainerSingleItem;
import org.jspecify.annotations.Nullable;

public abstract class Mob extends LivingEntity implements Targeting, EquipmentUser, Leashable {

    private static final EntityDataAccessor<Byte> DATA_MOB_FLAGS_ID = SynchedEntityData.<Byte>defineId(Mob.class, EntityDataSerializers.BYTE);
    private static final int MOB_FLAG_NO_AI = 1;
    private static final int MOB_FLAG_LEFTHANDED = 2;
    private static final int MOB_FLAG_AGGRESSIVE = 4;
    protected static final int PICKUP_REACH = 1;
    private static final Vec3i ITEM_PICKUP_REACH = new Vec3i(1, 0, 1);
    private static final List<EquipmentSlot> EQUIPMENT_POPULATION_ORDER = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    public static final float MAX_WEARING_ARMOR_CHANCE = 0.15F;
    public static final float WEARING_ARMOR_UPGRADE_MATERIAL_CHANCE = 0.1087F;
    public static final float WEARING_ARMOR_UPGRADE_MATERIAL_ATTEMPTS = 3.0F;
    public static final float MAX_PICKUP_LOOT_CHANCE = 0.55F;
    public static final float MAX_ENCHANTED_ARMOR_CHANCE = 0.5F;
    public static final float MAX_ENCHANTED_WEAPON_CHANCE = 0.25F;
    public static final int UPDATE_GOAL_SELECTOR_EVERY_N_TICKS = 2;
    private static final double DEFAULT_ATTACK_REACH = Math.sqrt((double) 2.04F) - (double) 0.6F;
    private static final boolean DEFAULT_CAN_PICK_UP_LOOT = false;
    private static final boolean DEFAULT_PERSISTENCE_REQUIRED = false;
    private static final boolean DEFAULT_LEFT_HANDED = false;
    private static final boolean DEFAULT_NO_AI = false;
    protected static final Identifier RANDOM_SPAWN_BONUS_ID = Identifier.withDefaultNamespace("random_spawn_bonus");
    public static final String TAG_DROP_CHANCES = "drop_chances";
    public static final String TAG_LEFT_HANDED = "LeftHanded";
    public static final String TAG_CAN_PICK_UP_LOOT = "CanPickUpLoot";
    public static final String TAG_NO_AI = "NoAI";
    public int ambientSoundTime;
    protected int xpReward;
    protected LookControl lookControl;
    protected MoveControl moveControl;
    protected JumpControl jumpControl;
    private final BodyRotationControl bodyRotationControl;
    protected PathNavigation navigation;
    public GoalSelector goalSelector;
    public GoalSelector targetSelector;
    private @Nullable LivingEntity target;
    private final Sensing sensing;
    private DropChances dropChances;
    private boolean canPickUpLoot;
    private boolean persistenceRequired;
    private final Map<PathType, Float> pathfindingMalus;
    public Optional<ResourceKey<LootTable>> lootTable;
    public long lootTableSeed;
    private Leashable.@Nullable LeashData leashData;
    private BlockPos homePosition;
    private int homeRadius;

    protected Mob(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.dropChances = DropChances.DEFAULT;
        this.canPickUpLoot = false;
        this.persistenceRequired = false;
        this.pathfindingMalus = Maps.newEnumMap(PathType.class);
        this.lootTable = Optional.empty();
        this.homePosition = BlockPos.ZERO;
        this.homeRadius = -1;
        this.goalSelector = new GoalSelector();
        this.targetSelector = new GoalSelector();
        this.lookControl = new LookControl(this);
        this.moveControl = new MoveControl(this);
        this.jumpControl = new JumpControl(this);
        this.bodyRotationControl = this.createBodyControl();
        this.navigation = this.createNavigation(level);
        this.sensing = new Sensing(this);
        if (level instanceof ServerLevel) {
            this.registerGoals();
        }

    }

    protected void registerGoals() {}

    public static AttributeSupplier.Builder createMobAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    protected PathNavigation createNavigation(Level level) {
        return new GroundPathNavigation(this, level);
    }

    protected boolean shouldPassengersInheritMalus() {
        return false;
    }

    public float getPathfindingMalus(PathType pathType) {
        Mob mob;
        label17:
        {
            Entity entity = this.getControlledVehicle();

            if (entity instanceof Mob mob1) {
                if (mob1.shouldPassengersInheritMalus()) {
                    mob = mob1;
                    break label17;
                }
            }

            mob = this;
        }

        Float ofloat = (Float) mob.pathfindingMalus.get(pathType);

        return ofloat == null ? pathType.getMalus() : ofloat;
    }

    public void setPathfindingMalus(PathType pathType, float cost) {
        this.pathfindingMalus.put(pathType, cost);
    }

    public void onPathfindingStart() {}

    public void onPathfindingDone() {}

    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this);
    }

    public LookControl getLookControl() {
        return this.lookControl;
    }

    public MoveControl getMoveControl() {
        Entity entity = this.getControlledVehicle();

        if (entity instanceof Mob mob) {
            return mob.getMoveControl();
        } else {
            return this.moveControl;
        }
    }

    public JumpControl getJumpControl() {
        return this.jumpControl;
    }

    public PathNavigation getNavigation() {
        Entity entity = this.getControlledVehicle();

        if (entity instanceof Mob mob) {
            return mob.getNavigation();
        } else {
            return this.navigation;
        }
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        Entity entity = this.getFirstPassenger();
        Mob mob;

        if (!this.isNoAi() && entity instanceof Mob mob1) {
            if (entity.canControlVehicle()) {
                mob = mob1;
                return mob;
            }
        }

        mob = null;
        return mob;
    }

    public Sensing getSensing() {
        return this.sensing;
    }

    @Override
    public @Nullable LivingEntity getTarget() {
        return this.target;
    }

    protected final @Nullable LivingEntity getTargetFromBrain() {
        return (LivingEntity) this.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse((Object) null);
    }

    public void setTarget(@Nullable LivingEntity target) {
        this.target = target;
    }

    @Override
    public boolean canAttackType(EntityType<?> targetType) {
        return targetType != EntityType.GHAST;
    }

    public boolean canUseNonMeleeWeapon(ItemStack item) {
        return false;
    }

    public void ate() {
        this.gameEvent(GameEvent.EAT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Mob.DATA_MOB_FLAGS_ID, (byte) 0);
    }

    public int getAmbientSoundInterval() {
        return 80;
    }

    public void playAmbientSound() {
        this.makeSound(this.getAmbientSound());
    }

    @Override
    public void baseTick() {
        super.baseTick();
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("mobBaseTick");
        if (this.isAlive() && this.random.nextInt(1000) < this.ambientSoundTime++) {
            this.resetAmbientSoundTime();
            this.playAmbientSound();
        }

        profilerfiller.pop();
    }

    @Override
    protected void playHurtSound(DamageSource source) {
        this.resetAmbientSoundTime();
        super.playHurtSound(source);
    }

    private void resetAmbientSoundTime() {
        this.ambientSoundTime = -this.getAmbientSoundInterval();
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        if (this.xpReward > 0) {
            int i = this.xpReward;

            for (EquipmentSlot equipmentslot : EquipmentSlot.VALUES) {
                if (equipmentslot.canIncreaseExperience()) {
                    ItemStack itemstack = this.getItemBySlot(equipmentslot);

                    if (!itemstack.isEmpty() && this.dropChances.byEquipment(equipmentslot) <= 1.0F) {
                        i += 1 + this.random.nextInt(3);
                    }
                }
            }

            return i;
        } else {
            return this.xpReward;
        }
    }

    public void spawnAnim() {
        if (this.level().isClientSide()) {
            this.makePoofParticles();
        } else {
            this.level().broadcastEntityEvent(this, (byte) 20);
        }

    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 20) {
            this.spawnAnim();
        } else {
            super.handleEntityEvent(id);
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.tickCount % 5 == 0) {
            this.updateControlFlags();
        }

    }

    protected void updateControlFlags() {
        boolean flag = !(this.getControllingPassenger() instanceof Mob);
        boolean flag1 = !(this.getVehicle() instanceof AbstractBoat);

        this.goalSelector.setControlFlag(Goal.Flag.MOVE, flag);
        this.goalSelector.setControlFlag(Goal.Flag.JUMP, flag && flag1);
        this.goalSelector.setControlFlag(Goal.Flag.LOOK, flag);
    }

    @Override
    protected void tickHeadTurn(float yBodyRotT) {
        this.bodyRotationControl.clientTick();
    }

    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("CanPickUpLoot", this.canPickUpLoot());
        output.putBoolean("PersistenceRequired", this.persistenceRequired);
        if (!this.dropChances.equals(DropChances.DEFAULT)) {
            output.store("drop_chances", DropChances.CODEC, this.dropChances);
        }

        this.writeLeashData(output, this.leashData);
        if (this.hasHome()) {
            output.putInt("home_radius", this.homeRadius);
            output.store("home_pos", BlockPos.CODEC, this.homePosition);
        }

        output.putBoolean("LeftHanded", this.isLeftHanded());
        this.lootTable.ifPresent((resourcekey) -> {
            output.store("DeathLootTable", LootTable.KEY_CODEC, resourcekey);
        });
        if (this.lootTableSeed != 0L) {
            output.putLong("DeathLootTableSeed", this.lootTableSeed);
        }

        if (this.isNoAi()) {
            output.putBoolean("NoAI", this.isNoAi());
        }

    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setCanPickUpLoot(input.getBooleanOr("CanPickUpLoot", false));
        this.persistenceRequired = input.getBooleanOr("PersistenceRequired", false);
        this.dropChances = (DropChances) input.read("drop_chances", DropChances.CODEC).orElse(DropChances.DEFAULT);
        this.readLeashData(input);
        this.homeRadius = input.getIntOr("home_radius", -1);
        if (this.homeRadius >= 0) {
            this.homePosition = (BlockPos) input.read("home_pos", BlockPos.CODEC).orElse(BlockPos.ZERO);
        }

        this.setLeftHanded(input.getBooleanOr("LeftHanded", false));
        this.lootTable = input.<ResourceKey<LootTable>>read("DeathLootTable", LootTable.KEY_CODEC);
        this.lootTableSeed = input.getLongOr("DeathLootTableSeed", 0L);
        this.setNoAi(input.getBooleanOr("NoAI", false));
    }

    @Override
    protected void dropFromLootTable(ServerLevel level, DamageSource source, boolean playerKilled) {
        super.dropFromLootTable(level, source, playerKilled);
        this.lootTable = Optional.empty();
    }

    @Override
    public final Optional<ResourceKey<LootTable>> getLootTable() {
        return this.lootTable.isPresent() ? this.lootTable : super.getLootTable();
    }

    @Override
    public long getLootTableSeed() {
        return this.lootTableSeed;
    }

    public void setZza(float zza) {
        this.zza = zza;
    }

    public void setYya(float yya) {
        this.yya = yya;
    }

    public void setXxa(float xxa) {
        this.xxa = xxa;
    }

    @Override
    public void setSpeed(float speed) {
        super.setSpeed(speed);
        this.setZza(speed);
    }

    public void stopInPlace() {
        this.getNavigation().stop();
        this.setXxa(0.0F);
        this.setYya(0.0F);
        this.setSpeed(0.0F);
        this.setDeltaMovement(0.0D, 0.0D, 0.0D);
        this.resetAngularLeashMomentum();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.getType().is(EntityTypeTags.BURN_IN_DAYLIGHT)) {
            this.burnUndead();
        }

        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("looting");
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            if (this.canPickUpLoot() && this.isAlive() && !this.dead && (Boolean) serverlevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
                Vec3i vec3i = this.getPickupReach();

                for (ItemEntity itementity : this.level().getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate((double) vec3i.getX(), (double) vec3i.getY(), (double) vec3i.getZ()))) {
                    if (!itementity.isRemoved() && !itementity.getItem().isEmpty() && !itementity.hasPickUpDelay() && this.wantsToPickUp(serverlevel, itementity.getItem())) {
                        this.pickUpItem(serverlevel, itementity);
                    }
                }
            }
        }

        profilerfiller.pop();
    }

    protected EquipmentSlot sunProtectionSlot() {
        return EquipmentSlot.HEAD;
    }

    private void burnUndead() {
        if (this.isAlive() && this.isSunBurnTick()) {
            EquipmentSlot equipmentslot = this.sunProtectionSlot();
            ItemStack itemstack = this.getItemBySlot(equipmentslot);

            if (!itemstack.isEmpty()) {
                if (itemstack.isDamageableItem()) {
                    Item item = itemstack.getItem();

                    itemstack.setDamageValue(itemstack.getDamageValue() + this.random.nextInt(2));
                    if (itemstack.getDamageValue() >= itemstack.getMaxDamage()) {
                        this.onEquippedItemBroken(item, equipmentslot);
                        this.setItemSlot(equipmentslot, ItemStack.EMPTY);
                    }
                }

            } else {
                this.igniteForSeconds(8.0F);
            }
        }
    }

    private boolean isSunBurnTick() {
        if (!this.level().isClientSide() && (Boolean) this.level().environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, this.position())) {
            float f = this.getLightLevelDependentMagicValue();
            BlockPos blockpos = BlockPos.containing(this.getX(), this.getEyeY(), this.getZ());
            boolean flag = this.isInWaterOrRain() || this.isInPowderSnow || this.wasInPowderSnow;

            if (f > 0.5F && this.random.nextFloat() * 30.0F < (f - 0.4F) * 2.0F && !flag && this.level().canSeeSky(blockpos)) {
                return true;
            }
        }

        return false;
    }

    protected Vec3i getPickupReach() {
        return Mob.ITEM_PICKUP_REACH;
    }

    protected void pickUpItem(ServerLevel level, ItemEntity entity) {
        ItemStack itemstack = entity.getItem();
        ItemStack itemstack1 = this.equipItemIfPossible(level, itemstack.copy());

        if (!itemstack1.isEmpty()) {
            this.onItemPickup(entity);
            this.take(entity, itemstack1.getCount());
            itemstack.shrink(itemstack1.getCount());
            if (itemstack.isEmpty()) {
                entity.discard();
            }
        }

    }

    public ItemStack equipItemIfPossible(ServerLevel level, ItemStack itemStack) {
        EquipmentSlot equipmentslot = this.getEquipmentSlotForItem(itemStack);

        if (!this.isEquippableInSlot(itemStack, equipmentslot)) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack1 = this.getItemBySlot(equipmentslot);
            boolean flag = this.canReplaceCurrentItem(itemStack, itemstack1, equipmentslot);

            if (equipmentslot.isArmor() && !flag) {
                equipmentslot = EquipmentSlot.MAINHAND;
                itemstack1 = this.getItemBySlot(equipmentslot);
                flag = itemstack1.isEmpty();
            }

            if (flag && this.canHoldItem(itemStack)) {
                double d0 = (double) this.dropChances.byEquipment(equipmentslot);

                if (!itemstack1.isEmpty() && (double) Math.max(this.random.nextFloat() - 0.1F, 0.0F) < d0) {
                    this.spawnAtLocation(level, itemstack1);
                }

                ItemStack itemstack2 = equipmentslot.limit(itemStack);

                this.setItemSlotAndDropWhenKilled(equipmentslot, itemstack2);
                return itemstack2;
            } else {
                return ItemStack.EMPTY;
            }
        }
    }

    protected void setItemSlotAndDropWhenKilled(EquipmentSlot slot, ItemStack itemStack) {
        this.setItemSlot(slot, itemStack);
        this.setGuaranteedDrop(slot);
        this.persistenceRequired = true;
    }

    protected boolean canShearEquipment(Player player) {
        return !this.isVehicle();
    }

    public void setGuaranteedDrop(EquipmentSlot slot) {
        this.dropChances = this.dropChances.withGuaranteedDrop(slot);
    }

    protected boolean canReplaceCurrentItem(ItemStack newItemStack, ItemStack currentItemStack, EquipmentSlot slot) {
        return currentItemStack.isEmpty() ? true : (slot.isArmor() ? this.compareArmor(newItemStack, currentItemStack, slot) : (slot == EquipmentSlot.MAINHAND ? this.compareWeapons(newItemStack, currentItemStack, slot) : false));
    }

    private boolean compareArmor(ItemStack newItemStack, ItemStack currentItemStack, EquipmentSlot slot) {
        if (EnchantmentHelper.has(currentItemStack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
            return false;
        } else {
            double d0 = this.getApproximateAttributeWith(newItemStack, Attributes.ARMOR, slot);
            double d1 = this.getApproximateAttributeWith(currentItemStack, Attributes.ARMOR, slot);
            double d2 = this.getApproximateAttributeWith(newItemStack, Attributes.ARMOR_TOUGHNESS, slot);
            double d3 = this.getApproximateAttributeWith(currentItemStack, Attributes.ARMOR_TOUGHNESS, slot);

            return d0 != d1 ? d0 > d1 : (d2 != d3 ? d2 > d3 : this.canReplaceEqualItem(newItemStack, currentItemStack));
        }
    }

    private boolean compareWeapons(ItemStack newItemStack, ItemStack currentItemStack, EquipmentSlot slot) {
        TagKey<Item> tagkey = this.getPreferredWeaponType();

        if (tagkey != null) {
            if (currentItemStack.is(tagkey) && !newItemStack.is(tagkey)) {
                return false;
            }

            if (!currentItemStack.is(tagkey) && newItemStack.is(tagkey)) {
                return true;
            }
        }

        double d0 = this.getApproximateAttributeWith(newItemStack, Attributes.ATTACK_DAMAGE, slot);
        double d1 = this.getApproximateAttributeWith(currentItemStack, Attributes.ATTACK_DAMAGE, slot);

        return d0 != d1 ? d0 > d1 : this.canReplaceEqualItem(newItemStack, currentItemStack);
    }

    private double getApproximateAttributeWith(ItemStack itemStack, Holder<Attribute> attribute, EquipmentSlot slot) {
        double d0 = this.getAttributes().hasAttribute(attribute) ? this.getAttributeBaseValue(attribute) : 0.0D;
        ItemAttributeModifiers itemattributemodifiers = (ItemAttributeModifiers) itemStack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        return itemattributemodifiers.compute(attribute, d0, slot);
    }

    public boolean canReplaceEqualItem(ItemStack newItemStack, ItemStack currentItemStack) {
        Set<Object2IntMap.Entry<Holder<Enchantment>>> set = ((ItemEnchantments) currentItemStack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)).entrySet();
        Set<Object2IntMap.Entry<Holder<Enchantment>>> set1 = ((ItemEnchantments) newItemStack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)).entrySet();

        if (set1.size() != set.size()) {
            return set1.size() > set.size();
        } else {
            int i = newItemStack.getDamageValue();
            int j = currentItemStack.getDamageValue();

            return i != j ? i < j : newItemStack.has(DataComponents.CUSTOM_NAME) && !currentItemStack.has(DataComponents.CUSTOM_NAME);
        }
    }

    public boolean canHoldItem(ItemStack itemStack) {
        return true;
    }

    public boolean wantsToPickUp(ServerLevel level, ItemStack itemStack) {
        return this.canHoldItem(itemStack);
    }

    public @Nullable TagKey<Item> getPreferredWeaponType() {
        return null;
    }

    public boolean removeWhenFarAway(double distSqr) {
        return true;
    }

    public boolean requiresCustomPersistence() {
        return this.isPassenger();
    }

    @Override
    public void checkDespawn() {
        if (this.level().getDifficulty() == Difficulty.PEACEFUL && !this.getType().isAllowedInPeaceful()) {
            this.discard();
        } else if (!this.isPersistenceRequired() && !this.requiresCustomPersistence()) {
            Entity entity = this.level().getNearestPlayer(this, -1.0D);

            if (entity != null) {
                double d0 = entity.distanceToSqr((Entity) this);
                int i = this.getType().getCategory().getDespawnDistance();
                int j = i * i;

                if (d0 > (double) j && this.removeWhenFarAway(d0)) {
                    this.discard();
                }

                int k = this.getType().getCategory().getNoDespawnDistance();
                int l = k * k;

                if (this.noActionTime > 600 && this.random.nextInt(800) == 0 && d0 > (double) l && this.removeWhenFarAway(d0)) {
                    this.discard();
                } else if (d0 < (double) l) {
                    this.noActionTime = 0;
                }
            }

        } else {
            this.noActionTime = 0;
        }
    }

    @Override
    protected final void serverAiStep() {
        ++this.noActionTime;
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("sensing");
        this.sensing.tick();
        profilerfiller.pop();
        int i = this.tickCount + this.getId();

        if (i % 2 != 0 && this.tickCount > 1) {
            profilerfiller.push("targetSelector");
            this.targetSelector.tickRunningGoals(false);
            profilerfiller.pop();
            profilerfiller.push("goalSelector");
            this.goalSelector.tickRunningGoals(false);
            profilerfiller.pop();
        } else {
            profilerfiller.push("targetSelector");
            this.targetSelector.tick();
            profilerfiller.pop();
            profilerfiller.push("goalSelector");
            this.goalSelector.tick();
            profilerfiller.pop();
        }

        profilerfiller.push("navigation");
        this.navigation.tick();
        profilerfiller.pop();
        profilerfiller.push("mob tick");
        this.customServerAiStep((ServerLevel) this.level());
        profilerfiller.pop();
        profilerfiller.push("controls");
        profilerfiller.push("move");
        this.moveControl.tick();
        profilerfiller.popPush("look");
        this.lookControl.tick();
        profilerfiller.popPush("jump");
        this.jumpControl.tick();
        profilerfiller.pop();
        profilerfiller.pop();
    }

    protected void customServerAiStep(ServerLevel level) {}

    public int getMaxHeadXRot() {
        return 40;
    }

    public int getMaxHeadYRot() {
        return 75;
    }

    protected void clampHeadRotationToBody() {
        float f = (float) this.getMaxHeadYRot();
        float f1 = this.getYHeadRot();
        float f2 = Mth.wrapDegrees(this.yBodyRot - f1);
        float f3 = Mth.clamp(Mth.wrapDegrees(this.yBodyRot - f1), -f, f);
        float f4 = f1 + f2 - f3;

        this.setYHeadRot(f4);
    }

    public int getHeadRotSpeed() {
        return 10;
    }

    public void lookAt(Entity entity, float yMax, float xMax) {
        double d0 = entity.getX() - this.getX();
        double d1 = entity.getZ() - this.getZ();
        double d2;

        if (entity instanceof LivingEntity livingentity) {
            d2 = livingentity.getEyeY() - this.getEyeY();
        } else {
            d2 = (entity.getBoundingBox().minY + entity.getBoundingBox().maxY) / 2.0D - this.getEyeY();
        }

        double d3 = Math.sqrt(d0 * d0 + d1 * d1);
        float f2 = (float) (Mth.atan2(d1, d0) * (double) (180F / (float) Math.PI)) - 90.0F;
        float f3 = (float) (-(Mth.atan2(d2, d3) * (double) (180F / (float) Math.PI)));

        this.setXRot(this.rotlerp(this.getXRot(), f3, xMax));
        this.setYRot(this.rotlerp(this.getYRot(), f2, yMax));
    }

    private float rotlerp(float a, float b, float max) {
        float f3 = Mth.wrapDegrees(b - a);

        if (f3 > max) {
            f3 = max;
        }

        if (f3 < -max) {
            f3 = -max;
        }

        return a + f3;
    }

    public static boolean checkMobSpawnRules(EntityType<? extends Mob> type, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random) {
        BlockPos blockpos1 = pos.below();

        return EntitySpawnReason.isSpawner(spawnReason) || level.getBlockState(blockpos1).isValidSpawn(level, blockpos1, type);
    }

    public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason spawnReason) {
        return true;
    }

    public boolean checkSpawnObstruction(LevelReader level) {
        return !level.containsAnyLiquid(this.getBoundingBox()) && level.isUnobstructed(this);
    }

    public int getMaxSpawnClusterSize() {
        return 4;
    }

    public boolean isMaxGroupSizeReached(int groupSize) {
        return false;
    }

    @Override
    public int getMaxFallDistance() {
        if (this.getTarget() == null) {
            return this.getComfortableFallDistance(0.0F);
        } else {
            int i = (int) (this.getHealth() - this.getMaxHealth() * 0.33F);

            i -= (3 - this.level().getDifficulty().getId()) * 4;
            if (i < 0) {
                i = 0;
            }

            return this.getComfortableFallDistance((float) i);
        }
    }

    public ItemStack getBodyArmorItem() {
        return this.getItemBySlot(EquipmentSlot.BODY);
    }

    public boolean isSaddled() {
        return this.hasValidEquippableItemForSlot(EquipmentSlot.SADDLE);
    }

    public boolean isWearingBodyArmor() {
        return this.hasValidEquippableItemForSlot(EquipmentSlot.BODY);
    }

    private boolean hasValidEquippableItemForSlot(EquipmentSlot slot) {
        return this.hasItemInSlot(slot) && this.isEquippableInSlot(this.getItemBySlot(slot), slot);
    }

    public void setBodyArmorItem(ItemStack item) {
        this.setItemSlotAndDropWhenKilled(EquipmentSlot.BODY, item);
    }

    public Container createEquipmentSlotContainer(final EquipmentSlot slot) {
        return new ContainerSingleItem() {
            @Override
            public ItemStack getTheItem() {
                return Mob.this.getItemBySlot(slot);
            }

            @Override
            public void setTheItem(ItemStack itemStack) {
                Mob.this.setItemSlot(slot, itemStack);
                if (!itemStack.isEmpty()) {
                    Mob.this.setGuaranteedDrop(slot);
                    Mob.this.setPersistenceRequired();
                }

            }

            @Override
            public void setChanged() {}

            @Override
            public boolean stillValid(Player player) {
                return player.getVehicle() == Mob.this || player.isWithinEntityInteractionRange((Entity) Mob.this, 4.0D);
            }
        };
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);

        for (EquipmentSlot equipmentslot : EquipmentSlot.VALUES) {
            ItemStack itemstack = this.getItemBySlot(equipmentslot);
            float f = this.dropChances.byEquipment(equipmentslot);

            if (f != 0.0F) {
                boolean flag1 = this.dropChances.isPreserved(equipmentslot);
                Entity entity = source.getEntity();

                if (entity instanceof LivingEntity) {
                    LivingEntity livingentity = (LivingEntity) entity;
                    Level level1 = this.level();

                    if (level1 instanceof ServerLevel) {
                        ServerLevel serverlevel1 = (ServerLevel) level1;

                        f = EnchantmentHelper.processEquipmentDropChance(serverlevel1, livingentity, source, f);
                    }
                }

                if (!itemstack.isEmpty() && !EnchantmentHelper.has(itemstack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP) && (killedByPlayer || flag1) && this.random.nextFloat() < f) {
                    if (!flag1 && itemstack.isDamageableItem()) {
                        itemstack.setDamageValue(itemstack.getMaxDamage() - this.random.nextInt(1 + this.random.nextInt(Math.max(itemstack.getMaxDamage() - 3, 1))));
                    }

                    this.spawnAtLocation(level, itemstack);
                    this.setItemSlot(equipmentslot, ItemStack.EMPTY);
                }
            }
        }

    }

    public DropChances getDropChances() {
        return this.dropChances;
    }

    public void dropPreservedEquipment(ServerLevel level) {
        this.dropPreservedEquipment(level, (itemstack) -> {
            return true;
        });
    }

    public Set<EquipmentSlot> dropPreservedEquipment(ServerLevel level, Predicate<ItemStack> shouldDrop) {
        Set<EquipmentSlot> set = new HashSet();

        for (EquipmentSlot equipmentslot : EquipmentSlot.VALUES) {
            ItemStack itemstack = this.getItemBySlot(equipmentslot);

            if (!itemstack.isEmpty()) {
                if (!shouldDrop.test(itemstack)) {
                    set.add(equipmentslot);
                } else if (this.dropChances.isPreserved(equipmentslot)) {
                    this.setItemSlot(equipmentslot, ItemStack.EMPTY);
                    this.spawnAtLocation(level, itemstack);
                }
            }
        }

        return set;
    }

    private LootParams createEquipmentParams(ServerLevel serverLevel) {
        return (new LootParams.Builder(serverLevel)).withParameter(LootContextParams.ORIGIN, this.position()).withParameter(LootContextParams.THIS_ENTITY, this).create(LootContextParamSets.EQUIPMENT);
    }

    public void equip(EquipmentTable equipment) {
        this.equip(equipment.lootTable(), equipment.slotDropChances());
    }

    public void equip(ResourceKey<LootTable> lootTable, Map<EquipmentSlot, Float> dropChances) {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            this.equip(lootTable, this.createEquipmentParams(serverlevel), dropChances);
        }

    }

    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        if (random.nextFloat() < 0.15F * difficulty.getSpecialMultiplier()) {
            int i = random.nextInt(3);

            for (int j = 1; (float) j <= 3.0F; ++j) {
                if (random.nextFloat() < 0.1087F) {
                    ++i;
                }
            }

            float f = this.level().getDifficulty() == Difficulty.HARD ? 0.1F : 0.25F;
            boolean flag = true;

            for (EquipmentSlot equipmentslot : Mob.EQUIPMENT_POPULATION_ORDER) {
                ItemStack itemstack = this.getItemBySlot(equipmentslot);

                if (!flag && random.nextFloat() < f) {
                    break;
                }

                flag = false;
                if (itemstack.isEmpty()) {
                    Item item = getEquipmentForSlot(equipmentslot, i);

                    if (item != null) {
                        this.setItemSlot(equipmentslot, new ItemStack(item));
                    }
                }
            }
        }

    }

    public static @Nullable Item getEquipmentForSlot(EquipmentSlot slot, int type) {
        switch (slot) {
            case HEAD:
                if (type == 0) {
                    return Items.LEATHER_HELMET;
                } else if (type == 1) {
                    return Items.COPPER_HELMET;
                } else if (type == 2) {
                    return Items.GOLDEN_HELMET;
                } else if (type == 3) {
                    return Items.CHAINMAIL_HELMET;
                } else if (type == 4) {
                    return Items.IRON_HELMET;
                } else if (type == 5) {
                    return Items.DIAMOND_HELMET;
                }
            case CHEST:
                if (type == 0) {
                    return Items.LEATHER_CHESTPLATE;
                } else if (type == 1) {
                    return Items.COPPER_CHESTPLATE;
                } else if (type == 2) {
                    return Items.GOLDEN_CHESTPLATE;
                } else if (type == 3) {
                    return Items.CHAINMAIL_CHESTPLATE;
                } else if (type == 4) {
                    return Items.IRON_CHESTPLATE;
                } else if (type == 5) {
                    return Items.DIAMOND_CHESTPLATE;
                }
            case LEGS:
                if (type == 0) {
                    return Items.LEATHER_LEGGINGS;
                } else if (type == 1) {
                    return Items.COPPER_LEGGINGS;
                } else if (type == 2) {
                    return Items.GOLDEN_LEGGINGS;
                } else if (type == 3) {
                    return Items.CHAINMAIL_LEGGINGS;
                } else if (type == 4) {
                    return Items.IRON_LEGGINGS;
                } else if (type == 5) {
                    return Items.DIAMOND_LEGGINGS;
                }
            case FEET:
                if (type == 0) {
                    return Items.LEATHER_BOOTS;
                } else if (type == 1) {
                    return Items.COPPER_BOOTS;
                } else if (type == 2) {
                    return Items.GOLDEN_BOOTS;
                } else if (type == 3) {
                    return Items.CHAINMAIL_BOOTS;
                } else if (type == 4) {
                    return Items.IRON_BOOTS;
                } else if (type == 5) {
                    return Items.DIAMOND_BOOTS;
                }
            default:
                return null;
        }
    }

    protected void populateDefaultEquipmentEnchantments(ServerLevelAccessor level, RandomSource random, DifficultyInstance localDifficulty) {
        this.enchantSpawnedWeapon(level, random, localDifficulty);

        for (EquipmentSlot equipmentslot : EquipmentSlot.VALUES) {
            if (equipmentslot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                this.enchantSpawnedArmor(level, random, equipmentslot, localDifficulty);
            }
        }

    }

    protected void enchantSpawnedWeapon(ServerLevelAccessor level, RandomSource random, DifficultyInstance difficulty) {
        this.enchantSpawnedEquipment(level, EquipmentSlot.MAINHAND, random, 0.25F, difficulty);
    }

    protected void enchantSpawnedArmor(ServerLevelAccessor level, RandomSource random, EquipmentSlot slot, DifficultyInstance difficulty) {
        this.enchantSpawnedEquipment(level, slot, random, 0.5F, difficulty);
    }

    private void enchantSpawnedEquipment(ServerLevelAccessor level, EquipmentSlot slot, RandomSource random, float chance, DifficultyInstance difficulty) {
        ItemStack itemstack = this.getItemBySlot(slot);

        if (!itemstack.isEmpty() && random.nextFloat() < chance * difficulty.getSpecialMultiplier()) {
            EnchantmentHelper.enchantItemFromProvider(itemstack, level.registryAccess(), VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT, difficulty, random);
            this.setItemSlot(slot, itemstack);
        }

    }

    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        RandomSource randomsource = level.getRandom();
        AttributeInstance attributeinstance = (AttributeInstance) Objects.requireNonNull(this.getAttribute(Attributes.FOLLOW_RANGE));

        if (!attributeinstance.hasModifier(Mob.RANDOM_SPAWN_BONUS_ID)) {
            attributeinstance.addPermanentModifier(new AttributeModifier(Mob.RANDOM_SPAWN_BONUS_ID, randomsource.triangle(0.0D, 0.11485000000000001D), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }

        this.setLeftHanded(randomsource.nextFloat() < 0.05F);
        return groupData;
    }

    public void setPersistenceRequired() {
        this.persistenceRequired = true;
    }

    @Override
    public void setDropChance(EquipmentSlot slot, float percent) {
        this.dropChances = this.dropChances.withEquipmentChance(slot, percent);
    }

    @Override
    public boolean canPickUpLoot() {
        return this.canPickUpLoot;
    }

    public void setCanPickUpLoot(boolean canPickUpLoot) {
        this.canPickUpLoot = canPickUpLoot;
    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
        return this.canPickUpLoot();
    }

    public boolean isPersistenceRequired() {
        return this.persistenceRequired;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.isAlive()) {
            return InteractionResult.PASS;
        } else {
            InteractionResult interactionresult = this.checkAndHandleImportantInteractions(player, hand);

            if (interactionresult.consumesAction()) {
                this.gameEvent(GameEvent.ENTITY_INTERACT, player);
                return interactionresult;
            } else {
                InteractionResult interactionresult1 = super.interact(player, hand);

                if (interactionresult1 != InteractionResult.PASS) {
                    return interactionresult1;
                } else {
                    interactionresult = this.mobInteract(player, hand);
                    if (interactionresult.consumesAction()) {
                        this.gameEvent(GameEvent.ENTITY_INTERACT, player);
                        return interactionresult;
                    } else {
                        return InteractionResult.PASS;
                    }
                }
            }
        }
    }

    private InteractionResult checkAndHandleImportantInteractions(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.is(Items.NAME_TAG)) {
            InteractionResult interactionresult = itemstack.interactLivingEntity(player, this, hand);

            if (interactionresult.consumesAction()) {
                return interactionresult;
            }
        }

        Item item = itemstack.getItem();

        if (item instanceof SpawnEggItem spawneggitem) {
            if (this.level() instanceof ServerLevel) {
                Optional<Mob> optional = spawneggitem.spawnOffspringFromSpawnEgg(player, this, this.getType(), (ServerLevel) this.level(), this.position(), itemstack);

                optional.ifPresent((mob) -> {
                    this.onOffspringSpawnedFromEgg(player, mob);
                });
                if (optional.isEmpty()) {
                    return InteractionResult.PASS;
                }
            }

            return InteractionResult.SUCCESS_SERVER;
        } else {
            return InteractionResult.PASS;
        }
    }

    protected void onOffspringSpawnedFromEgg(Player spawner, Mob offspring) {}

    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    protected void usePlayerItem(Player player, InteractionHand hand, ItemStack itemStack) {
        int i = itemStack.getCount();
        UseRemainder useremainder = (UseRemainder) itemStack.get(DataComponents.USE_REMAINDER);

        itemStack.consume(1, player);
        if (useremainder != null) {
            boolean flag = player.hasInfiniteMaterials();

            Objects.requireNonNull(player);
            ItemStack itemstack1 = useremainder.convertIntoRemainder(itemStack, i, flag, player::handleExtraItemsCreatedOnUse);

            player.setItemInHand(hand, itemstack1);
        }

    }

    public boolean isWithinHome() {
        return this.isWithinHome(this.blockPosition());
    }

    public boolean isWithinHome(BlockPos pos) {
        return this.homeRadius == -1 ? true : this.homePosition.distSqr(pos) < (double) (this.homeRadius * this.homeRadius);
    }

    public boolean isWithinHome(Vec3 pos) {
        return this.homeRadius == -1 ? true : this.homePosition.distToCenterSqr(pos) < (double) (this.homeRadius * this.homeRadius);
    }

    public void setHomeTo(BlockPos newCenter, int radius) {
        this.homePosition = newCenter;
        this.homeRadius = radius;
    }

    public BlockPos getHomePosition() {
        return this.homePosition;
    }

    public int getHomeRadius() {
        return this.homeRadius;
    }

    public void clearHome() {
        this.homeRadius = -1;
    }

    public boolean hasHome() {
        return this.homeRadius != -1;
    }

    public <T extends Mob> @Nullable T convertTo(EntityType<T> entityType, ConversionParams params, EntitySpawnReason spawnReason, ConversionParams.AfterConversion<T> afterConversion) {
        if (this.isRemoved()) {
            return null;
        } else {
            T t0 = entityType.create(this.level(), spawnReason);

            if (t0 == null) {
                return null;
            } else {
                params.type().convert(this, t0, params);
                afterConversion.finalizeConversion(t0);
                Level level = this.level();

                if (level instanceof ServerLevel) {
                    ServerLevel serverlevel = (ServerLevel) level;

                    serverlevel.addFreshEntity(t0);
                }

                if (params.type().shouldDiscardAfterConversion()) {
                    this.discard();
                }

                return t0;
            }
        }
    }

    public <T extends Mob> @Nullable T convertTo(EntityType<T> entityType, ConversionParams params, ConversionParams.AfterConversion<T> afterConversion) {
        return (T) this.convertTo(entityType, params, EntitySpawnReason.CONVERSION, afterConversion);
    }

    @Override
    public Leashable.@Nullable LeashData getLeashData() {
        return this.leashData;
    }

    private void resetAngularLeashMomentum() {
        if (this.leashData != null) {
            this.leashData.angularMomentum = 0.0D;
        }

    }

    @Override
    public void setLeashData(Leashable.@Nullable LeashData leashData) {
        this.leashData = leashData;
    }

    @Override
    public void onLeashRemoved() {
        if (this.getLeashData() == null) {
            this.clearHome();
        }

    }

    @Override
    public void leashTooFarBehaviour() {
        Leashable.super.leashTooFarBehaviour();
        this.goalSelector.disableControlFlag(Goal.Flag.MOVE);
    }

    @Override
    public boolean canBeLeashed() {
        return !(this instanceof Enemy);
    }

    @Override
    public boolean startRiding(Entity entity, boolean force, boolean sendEventAndTriggers) {
        boolean flag2 = super.startRiding(entity, force, sendEventAndTriggers);

        if (flag2 && this.isLeashed()) {
            this.dropLeash();
        }

        return flag2;
    }

    @Override
    public boolean isEffectiveAi() {
        return super.isEffectiveAi() && !this.isNoAi();
    }

    public void setNoAi(boolean flag) {
        byte b0 = (Byte) this.entityData.get(Mob.DATA_MOB_FLAGS_ID);

        this.entityData.set(Mob.DATA_MOB_FLAGS_ID, flag ? (byte) (b0 | 1) : (byte) (b0 & -2));
    }

    public void setLeftHanded(boolean flag) {
        byte b0 = (Byte) this.entityData.get(Mob.DATA_MOB_FLAGS_ID);

        this.entityData.set(Mob.DATA_MOB_FLAGS_ID, flag ? (byte) (b0 | 2) : (byte) (b0 & -3));
    }

    public void setAggressive(boolean flag) {
        byte b0 = (Byte) this.entityData.get(Mob.DATA_MOB_FLAGS_ID);

        this.entityData.set(Mob.DATA_MOB_FLAGS_ID, flag ? (byte) (b0 | 4) : (byte) (b0 & -5));
    }

    public boolean isNoAi() {
        return ((Byte) this.entityData.get(Mob.DATA_MOB_FLAGS_ID) & 1) != 0;
    }

    public boolean isLeftHanded() {
        return ((Byte) this.entityData.get(Mob.DATA_MOB_FLAGS_ID) & 2) != 0;
    }

    public boolean isAggressive() {
        return ((Byte) this.entityData.get(Mob.DATA_MOB_FLAGS_ID) & 4) != 0;
    }

    public void setBaby(boolean baby) {}

    @Override
    public HumanoidArm getMainArm() {
        return this.isLeftHanded() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }

    public boolean isWithinMeleeAttackRange(LivingEntity target) {
        AttackRange attackrange = (AttackRange) this.getActiveItem().get(DataComponents.ATTACK_RANGE);
        double d0;
        double d1;

        if (attackrange == null) {
            d0 = Mob.DEFAULT_ATTACK_REACH;
            d1 = 0.0D;
        } else {
            d0 = (double) attackrange.effectiveMaxRange(this);
            d1 = (double) attackrange.effectiveMinRange(this);
        }

        AABB aabb = target.getHitbox();

        return this.getAttackBoundingBox(d0).intersects(aabb) && (d1 <= 0.0D || !this.getAttackBoundingBox(d1).intersects(aabb));
    }

    protected AABB getAttackBoundingBox(double horizontalExpansion) {
        Entity entity = this.getVehicle();
        AABB aabb;

        if (entity != null) {
            AABB aabb1 = entity.getBoundingBox();
            AABB aabb2 = this.getBoundingBox();

            aabb = new AABB(Math.min(aabb2.minX, aabb1.minX), aabb2.minY, Math.min(aabb2.minZ, aabb1.minZ), Math.max(aabb2.maxX, aabb1.maxX), aabb2.maxY, Math.max(aabb2.maxZ, aabb1.maxZ));
        } else {
            aabb = this.getBoundingBox();
        }

        return aabb.inflate(horizontalExpansion, 0.0D, horizontalExpansion);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        float f = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        ItemStack itemstack = this.getWeaponItem();
        DamageSource damagesource = itemstack.getDamageSource(this, () -> {
            return this.damageSources().mobAttack(this);
        });

        f = EnchantmentHelper.modifyDamage(level, itemstack, target, damagesource, f);
        f += itemstack.getItem().getAttackDamageBonus(target, f, damagesource);
        Vec3 vec3 = target.getDeltaMovement();
        boolean flag = target.hurtServer(level, damagesource, f);

        if (flag) {
            this.causeExtraKnockback(target, this.getKnockback(target, damagesource), vec3);
            if (target instanceof LivingEntity) {
                LivingEntity livingentity = (LivingEntity) target;

                itemstack.hurtEnemy(livingentity, this);
            }

            EnchantmentHelper.doPostAttackEffects(level, target, damagesource);
            this.setLastHurtMob(target);
            this.playAttackSound();
        }

        this.lungeForwardMaybe();
        return flag;
    }

    @Override
    protected void jumpInLiquid(TagKey<Fluid> type) {
        if (this.getNavigation().canFloat()) {
            super.jumpInLiquid(type);
        } else {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.3D, 0.0D));
        }

    }

    @VisibleForTesting
    public void removeFreeWill() {
        this.removeAllGoals((goal) -> {
            return true;
        });
        this.getBrain().removeAllBehaviors();
    }

    public void removeAllGoals(Predicate<Goal> predicate) {
        this.goalSelector.removeAllGoals(predicate);
    }

    @Override
    protected void removeAfterChangingDimensions() {
        super.removeAfterChangingDimensions();

        for (EquipmentSlot equipmentslot : EquipmentSlot.VALUES) {
            ItemStack itemstack = this.getItemBySlot(equipmentslot);

            if (!itemstack.isEmpty()) {
                itemstack.setCount(0);
            }
        }

    }

    @Override
    public @Nullable ItemStack getPickResult() {
        SpawnEggItem spawneggitem = SpawnEggItem.byId(this.getType());

        return spawneggitem == null ? null : new ItemStack(spawneggitem);
    }

    @Override
    protected void onAttributeUpdated(Holder<Attribute> attribute) {
        super.onAttributeUpdated(attribute);
        if (attribute.is(Attributes.FOLLOW_RANGE) || attribute.is(Attributes.TEMPT_RANGE)) {
            this.getNavigation().updatePathfinderMaxVisitedNodes();
        }

    }

    @Override
    public void registerDebugValues(ServerLevel level, DebugValueSource.Registration registration) {
        registration.register(DebugSubscriptions.ENTITY_PATHS, () -> {
            Path path = this.getNavigation().getPath();

            return path != null && path.debugData() != null ? new DebugPathInfo(path.copy(), this.getNavigation().getMaxDistanceToWaypoint()) : null;
        });
        registration.register(DebugSubscriptions.GOAL_SELECTORS, () -> {
            Set<WrappedGoal> set = this.goalSelector.getAvailableGoals();
            List<DebugGoalInfo.DebugGoal> list = new ArrayList(set.size());

            set.forEach((wrappedgoal) -> {
                list.add(new DebugGoalInfo.DebugGoal(wrappedgoal.getPriority(), wrappedgoal.isRunning(), wrappedgoal.getGoal().getClass().getSimpleName()));
            });
            return new DebugGoalInfo(list);
        });
        if (!this.brain.isBrainDead()) {
            registration.register(DebugSubscriptions.BRAINS, () -> {
                return DebugBrainDump.takeBrainDump(level, this);
            });
        }

    }

    public float chargeSpeedModifier() {
        return 1.0F;
    }
}

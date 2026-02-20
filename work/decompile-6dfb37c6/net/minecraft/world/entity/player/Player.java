package net.minecraft.world.entity.player;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.math.IntMath;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.vehicle.minecart.MinecartCommandBlock;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.entity.TestBlockEntity;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.jspecify.annotations.Nullable;

public abstract class Player extends Avatar implements ContainerUser {

    public static final int MAX_HEALTH = 20;
    public static final int SLEEP_DURATION = 100;
    public static final int WAKE_UP_DURATION = 10;
    public static final int ENDER_SLOT_OFFSET = 200;
    public static final int HELD_ITEM_SLOT = 499;
    public static final int CRAFTING_SLOT_OFFSET = 500;
    public static final float DEFAULT_BLOCK_INTERACTION_RANGE = 4.5F;
    public static final float DEFAULT_ENTITY_INTERACTION_RANGE = 3.0F;
    private static final int CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME_TICKS = 40;
    private static final EntityDataAccessor<Float> DATA_PLAYER_ABSORPTION_ID = SynchedEntityData.<Float>defineId(Player.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_SCORE_ID = SynchedEntityData.<Integer>defineId(Player.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<OptionalInt> DATA_SHOULDER_PARROT_LEFT = SynchedEntityData.<OptionalInt>defineId(Player.class, EntityDataSerializers.OPTIONAL_UNSIGNED_INT);
    private static final EntityDataAccessor<OptionalInt> DATA_SHOULDER_PARROT_RIGHT = SynchedEntityData.<OptionalInt>defineId(Player.class, EntityDataSerializers.OPTIONAL_UNSIGNED_INT);
    private static final short DEFAULT_SLEEP_TIMER = 0;
    private static final float DEFAULT_EXPERIENCE_PROGRESS = 0.0F;
    private static final int DEFAULT_EXPERIENCE_LEVEL = 0;
    private static final int DEFAULT_TOTAL_EXPERIENCE = 0;
    private static final int NO_ENCHANTMENT_SEED = 0;
    private static final int DEFAULT_SELECTED_SLOT = 0;
    private static final int DEFAULT_SCORE = 0;
    private static final boolean DEFAULT_IGNORE_FALL_DAMAGE_FROM_CURRENT_IMPULSE = false;
    private static final int DEFAULT_CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME = 0;
    public static final float CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER_VALUE = 2.0F;
    private final Inventory inventory;
    protected PlayerEnderChestContainer enderChestInventory = new PlayerEnderChestContainer();
    public final InventoryMenu inventoryMenu;
    public AbstractContainerMenu containerMenu;
    protected FoodData foodData = new FoodData();
    protected int jumpTriggerTime;
    public int takeXpDelay;
    public int sleepCounter = 0;
    protected boolean wasUnderwater;
    private final Abilities abilities = new Abilities();
    public int experienceLevel = 0;
    public int totalExperience = 0;
    public float experienceProgress = 0.0F;
    public int enchantmentSeed = 0;
    protected final float defaultFlySpeed = 0.02F;
    private int lastLevelUpTime;
    private final GameProfile gameProfile;
    private boolean reducedDebugInfo;
    private ItemStack lastItemInMainHand;
    private final ItemCooldowns cooldowns;
    private Optional<GlobalPos> lastDeathLocation;
    public @Nullable FishingHook fishing;
    protected float hurtDir;
    public @Nullable Vec3 currentImpulseImpactPos;
    public @Nullable Entity currentExplosionCause;
    private boolean ignoreFallDamageFromCurrentImpulse;
    private int currentImpulseContextResetGraceTime;

    public Player(Level level, GameProfile gameProfile) {
        super(EntityType.PLAYER, level);
        this.lastItemInMainHand = ItemStack.EMPTY;
        this.cooldowns = this.createItemCooldowns();
        this.lastDeathLocation = Optional.empty();
        this.ignoreFallDamageFromCurrentImpulse = false;
        this.currentImpulseContextResetGraceTime = 0;
        this.setUUID(gameProfile.id());
        this.gameProfile = gameProfile;
        this.inventory = new Inventory(this, this.equipment);
        this.inventoryMenu = new InventoryMenu(this.inventory, !level.isClientSide(), this);
        this.containerMenu = this.inventoryMenu;
    }

    @Override
    protected EntityEquipment createEquipment() {
        return new PlayerEquipment(this);
    }

    public boolean blockActionRestricted(Level level, BlockPos pos, GameType gameType) {
        if (!gameType.isBlockPlacingRestricted()) {
            return false;
        } else if (gameType == GameType.SPECTATOR) {
            return true;
        } else if (this.mayBuild()) {
            return false;
        } else {
            ItemStack itemstack = this.getMainHandItem();

            return itemstack.isEmpty() || !itemstack.canBreakBlockInAdventureMode(new BlockInWorld(level, pos, false));
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.ATTACK_DAMAGE, 1.0D).add(Attributes.MOVEMENT_SPEED, (double) 0.1F).add(Attributes.ATTACK_SPEED).add(Attributes.LUCK).add(Attributes.BLOCK_INTERACTION_RANGE, 4.5D).add(Attributes.ENTITY_INTERACTION_RANGE, 3.0D).add(Attributes.BLOCK_BREAK_SPEED).add(Attributes.SUBMERGED_MINING_SPEED).add(Attributes.SNEAKING_SPEED).add(Attributes.MINING_EFFICIENCY).add(Attributes.SWEEPING_DAMAGE_RATIO).add(Attributes.WAYPOINT_TRANSMIT_RANGE, 6.0E7D).add(Attributes.WAYPOINT_RECEIVE_RANGE, 6.0E7D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Player.DATA_PLAYER_ABSORPTION_ID, 0.0F);
        entityData.define(Player.DATA_SCORE_ID, 0);
        entityData.define(Player.DATA_SHOULDER_PARROT_LEFT, OptionalInt.empty());
        entityData.define(Player.DATA_SHOULDER_PARROT_RIGHT, OptionalInt.empty());
    }

    @Override
    public void tick() {
        this.noPhysics = this.isSpectator();
        if (this.isSpectator() || this.isPassenger()) {
            this.setOnGround(false);
        }

        if (this.takeXpDelay > 0) {
            --this.takeXpDelay;
        }

        if (this.isSleeping()) {
            ++this.sleepCounter;
            if (this.sleepCounter > 100) {
                this.sleepCounter = 100;
            }

            if (!this.level().isClientSide() && !((BedRule) this.level().environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, this.position())).canSleep(this.level())) {
                this.stopSleepInBed(false, true);
            }
        } else if (this.sleepCounter > 0) {
            ++this.sleepCounter;
            if (this.sleepCounter >= 110) {
                this.sleepCounter = 0;
            }
        }

        this.updateIsUnderwater();
        super.tick();
        int i = 29999999;
        double d0 = Mth.clamp(this.getX(), -2.9999999E7D, 2.9999999E7D);
        double d1 = Mth.clamp(this.getZ(), -2.9999999E7D, 2.9999999E7D);

        if (d0 != this.getX() || d1 != this.getZ()) {
            this.setPos(d0, this.getY(), d1);
        }

        ++this.attackStrengthTicker;
        ++this.itemSwapTicker;
        ItemStack itemstack = this.getMainHandItem();

        if (!ItemStack.matches(this.lastItemInMainHand, itemstack)) {
            if (!ItemStack.isSameItem(this.lastItemInMainHand, itemstack)) {
                this.resetAttackStrengthTicker();
            }

            this.lastItemInMainHand = itemstack.copy();
        }

        if (!this.isEyeInFluid(FluidTags.WATER) && this.isEquipped(Items.TURTLE_HELMET)) {
            this.turtleHelmetTick();
        }

        this.cooldowns.tick();
        this.updatePlayerPose();
        if (this.currentImpulseContextResetGraceTime > 0) {
            --this.currentImpulseContextResetGraceTime;
        }

    }

    @Override
    protected float getMaxHeadRotationRelativeToBody() {
        return this.isBlocking() ? 15.0F : super.getMaxHeadRotationRelativeToBody();
    }

    public boolean isSecondaryUseActive() {
        return this.isShiftKeyDown();
    }

    protected boolean wantsToStopRiding() {
        return this.isShiftKeyDown();
    }

    protected boolean isStayingOnGroundSurface() {
        return this.isShiftKeyDown();
    }

    protected boolean updateIsUnderwater() {
        this.wasUnderwater = this.isEyeInFluid(FluidTags.WATER);
        return this.wasUnderwater;
    }

    @Override
    public void onAboveBubbleColumn(boolean dragDown, BlockPos pos) {
        if (!this.getAbilities().flying) {
            super.onAboveBubbleColumn(dragDown, pos);
        }

    }

    @Override
    public void onInsideBubbleColumn(boolean dragDown) {
        if (!this.getAbilities().flying) {
            super.onInsideBubbleColumn(dragDown);
        }

    }

    private void turtleHelmetTick() {
        this.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200, 0, false, false, true));
    }

    private boolean isEquipped(Item item) {
        for (EquipmentSlot equipmentslot : EquipmentSlot.VALUES) {
            ItemStack itemstack = this.getItemBySlot(equipmentslot);
            Equippable equippable = (Equippable) itemstack.get(DataComponents.EQUIPPABLE);

            if (itemstack.is(item) && equippable != null && equippable.slot() == equipmentslot) {
                return true;
            }
        }

        return false;
    }

    protected ItemCooldowns createItemCooldowns() {
        return new ItemCooldowns();
    }

    protected void updatePlayerPose() {
        if (this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.SWIMMING)) {
            Pose pose = this.getDesiredPose();
            Pose pose1;

            if (!this.isSpectator() && !this.isPassenger() && !this.canPlayerFitWithinBlocksAndEntitiesWhen(pose)) {
                if (this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.CROUCHING)) {
                    pose1 = Pose.CROUCHING;
                } else {
                    pose1 = Pose.SWIMMING;
                }
            } else {
                pose1 = pose;
            }

            this.setPose(pose1);
        }
    }

    private Pose getDesiredPose() {
        return this.isSleeping() ? Pose.SLEEPING : (this.isSwimming() ? Pose.SWIMMING : (this.isFallFlying() ? Pose.FALL_FLYING : (this.isAutoSpinAttack() ? Pose.SPIN_ATTACK : (this.isShiftKeyDown() && !this.abilities.flying ? Pose.CROUCHING : Pose.STANDING))));
    }

    protected boolean canPlayerFitWithinBlocksAndEntitiesWhen(Pose newPose) {
        return this.level().noCollision(this, this.getDimensions(newPose).makeBoundingBox(this.position()).deflate(1.0E-7D));
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.PLAYER_SWIM;
    }

    @Override
    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.PLAYER_SPLASH;
    }

    @Override
    protected SoundEvent getSwimHighSpeedSplashSound() {
        return SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
    }

    @Override
    public int getDimensionChangingDelay() {
        return 10;
    }

    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        this.level().playSound(this, this.getX(), this.getY(), this.getZ(), sound, this.getSoundSource(), volume, pitch);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.PLAYERS;
    }

    @Override
    public int getFireImmuneTicks() {
        return 20;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 9) {
            this.completeUsingItem();
        } else if (id == 23) {
            this.setReducedDebugInfo(false);
        } else if (id == 22) {
            this.setReducedDebugInfo(true);
        } else {
            super.handleEntityEvent(id);
        }

    }

    public void closeContainer() {
        this.containerMenu = this.inventoryMenu;
    }

    protected void doCloseContainer() {}

    @Override
    public void rideTick() {
        if (!this.level().isClientSide() && this.wantsToStopRiding() && this.isPassenger()) {
            this.stopRiding();
            this.setShiftKeyDown(false);
        } else {
            super.rideTick();
        }
    }

    @Override
    public void aiStep() {
        if (this.jumpTriggerTime > 0) {
            --this.jumpTriggerTime;
        }

        this.tickRegeneration();
        this.inventory.tick();
        if (this.abilities.flying && !this.isPassenger()) {
            this.resetFallDistance();
        }

        super.aiStep();
        this.updateSwingTime();
        this.yHeadRot = this.getYRot();
        this.setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED));
        if (this.getHealth() > 0.0F && !this.isSpectator()) {
            AABB aabb;

            if (this.isPassenger() && !this.getVehicle().isRemoved()) {
                aabb = this.getBoundingBox().minmax(this.getVehicle().getBoundingBox()).inflate(1.0D, 0.0D, 1.0D);
            } else {
                aabb = this.getBoundingBox().inflate(1.0D, 0.5D, 1.0D);
            }

            List<Entity> list = this.level().getEntities(this, aabb);
            List<Entity> list1 = Lists.newArrayList();

            for (Entity entity : list) {
                if (entity.getType() == EntityType.EXPERIENCE_ORB) {
                    list1.add(entity);
                } else if (!entity.isRemoved()) {
                    this.touch(entity);
                }
            }

            if (!list1.isEmpty()) {
                this.touch((Entity) Util.getRandom(list1, this.random));
            }
        }

        this.handleShoulderEntities();
    }

    protected void tickRegeneration() {}

    public void handleShoulderEntities() {}

    protected void removeEntitiesOnShoulder() {}

    private void touch(Entity entity) {
        entity.playerTouch(this);
    }

    public int getScore() {
        return (Integer) this.entityData.get(Player.DATA_SCORE_ID);
    }

    public void setScore(int value) {
        this.entityData.set(Player.DATA_SCORE_ID, value);
    }

    public void increaseScore(int amount) {
        int j = this.getScore();

        this.entityData.set(Player.DATA_SCORE_ID, j + amount);
    }

    public void startAutoSpinAttack(int activationTicks, float dmg, ItemStack itemStackUsed) {
        this.autoSpinAttackTicks = activationTicks;
        this.autoSpinAttackDmg = dmg;
        this.autoSpinAttackItemStack = itemStackUsed;
        if (!this.level().isClientSide()) {
            this.removeEntitiesOnShoulder();
            this.setLivingEntityFlag(4, true);
        }

    }

    @Override
    public ItemStack getWeaponItem() {
        return this.isAutoSpinAttack() && this.autoSpinAttackItemStack != null ? this.autoSpinAttackItemStack : super.getWeaponItem();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        this.reapplyPosition();
        if (!this.isSpectator()) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                this.dropAllDeathLoot(serverlevel, source);
            }
        }

        if (source != null) {
            this.setDeltaMovement((double) (-Mth.cos((double) ((this.getHurtDir() + this.getYRot()) * ((float) Math.PI / 180F))) * 0.1F), (double) 0.1F, (double) (-Mth.sin((double) ((this.getHurtDir() + this.getYRot()) * ((float) Math.PI / 180F))) * 0.1F));
        } else {
            this.setDeltaMovement(0.0D, 0.1D, 0.0D);
        }

        this.awardStat(Stats.DEATHS);
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        this.clearFire();
        this.setSharedFlagOnFire(false);
        this.setLastDeathLocation(Optional.of(GlobalPos.of(this.level().dimension(), this.blockPosition())));
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        super.dropEquipment(level);
        if (!(Boolean) level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
            this.destroyVanishingCursedItems();
            this.inventory.dropAll();
        }

    }

    protected void destroyVanishingCursedItems() {
        for (int i = 0; i < this.inventory.getContainerSize(); ++i) {
            ItemStack itemstack = this.inventory.getItem(i);

            if (!itemstack.isEmpty() && EnchantmentHelper.has(itemstack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                this.inventory.removeItemNoUpdate(i);
            }
        }

    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return source.type().effects().sound();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    public void handleCreativeModeItemDrop(ItemStack stack) {}

    public @Nullable ItemEntity drop(ItemStack itemStack, boolean thrownFromHand) {
        return this.drop(itemStack, false, thrownFromHand);
    }

    public float getDestroySpeed(BlockState state) {
        float f = this.inventory.getSelectedItem().getDestroySpeed(state);

        if (f > 1.0F) {
            f += (float) this.getAttributeValue(Attributes.MINING_EFFICIENCY);
        }

        if (MobEffectUtil.hasDigSpeed(this)) {
            f *= 1.0F + (float) (MobEffectUtil.getDigSpeedAmplification(this) + 1) * 0.2F;
        }

        if (this.hasEffect(MobEffects.MINING_FATIGUE)) {
            float f1;

            switch (this.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) {
                case 0:
                    f1 = 0.3F;
                    break;
                case 1:
                    f1 = 0.09F;
                    break;
                case 2:
                    f1 = 0.0027F;
                    break;
                default:
                    f1 = 8.1E-4F;
            }

            float f2 = f1;

            f *= f2;
        }

        f *= (float) this.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
        if (this.isEyeInFluid(FluidTags.WATER)) {
            f *= (float) this.getAttribute(Attributes.SUBMERGED_MINING_SPEED).getValue();
        }

        if (!this.onGround()) {
            f /= 5.0F;
        }

        return f;
    }

    public boolean hasCorrectToolForDrops(BlockState state) {
        return !state.requiresCorrectToolForDrops() || this.inventory.getSelectedItem().isCorrectToolForDrops(state);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setUUID(this.gameProfile.id());
        this.inventory.load(input.listOrEmpty("Inventory", ItemStackWithSlot.CODEC));
        this.inventory.setSelectedSlot(input.getIntOr("SelectedItemSlot", 0));
        this.sleepCounter = input.getShortOr("SleepTimer", (short) 0);
        this.experienceProgress = input.getFloatOr("XpP", 0.0F);
        this.experienceLevel = input.getIntOr("XpLevel", 0);
        this.totalExperience = input.getIntOr("XpTotal", 0);
        this.enchantmentSeed = input.getIntOr("XpSeed", 0);
        if (this.enchantmentSeed == 0) {
            this.enchantmentSeed = this.random.nextInt();
        }

        this.setScore(input.getIntOr("Score", 0));
        this.foodData.readAdditionalSaveData(input);
        Optional optional = input.read("abilities", Abilities.Packed.CODEC);
        Abilities abilities = this.abilities;

        Objects.requireNonNull(this.abilities);
        optional.ifPresent(abilities::apply);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue((double) this.abilities.getWalkingSpeed());
        this.enderChestInventory.fromSlots(input.listOrEmpty("EnderItems", ItemStackWithSlot.CODEC));
        this.setLastDeathLocation(input.read("LastDeathLocation", GlobalPos.CODEC));
        this.currentImpulseImpactPos = (Vec3) input.read("current_explosion_impact_pos", Vec3.CODEC).orElse((Object) null);
        this.ignoreFallDamageFromCurrentImpulse = input.getBooleanOr("ignore_fall_damage_from_current_explosion", false);
        this.currentImpulseContextResetGraceTime = input.getIntOr("current_impulse_context_reset_grace_time", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        NbtUtils.addCurrentDataVersion(output);
        this.inventory.save(output.list("Inventory", ItemStackWithSlot.CODEC));
        output.putInt("SelectedItemSlot", this.inventory.getSelectedSlot());
        output.putShort("SleepTimer", (short) this.sleepCounter);
        output.putFloat("XpP", this.experienceProgress);
        output.putInt("XpLevel", this.experienceLevel);
        output.putInt("XpTotal", this.totalExperience);
        output.putInt("XpSeed", this.enchantmentSeed);
        output.putInt("Score", this.getScore());
        this.foodData.addAdditionalSaveData(output);
        output.store("abilities", Abilities.Packed.CODEC, this.abilities.pack());
        this.enderChestInventory.storeAsSlots(output.list("EnderItems", ItemStackWithSlot.CODEC));
        this.lastDeathLocation.ifPresent((globalpos) -> {
            output.store("LastDeathLocation", GlobalPos.CODEC, globalpos);
        });
        output.storeNullable("current_explosion_impact_pos", Vec3.CODEC, this.currentImpulseImpactPos);
        output.putBoolean("ignore_fall_damage_from_current_explosion", this.ignoreFallDamageFromCurrentImpulse);
        output.putInt("current_impulse_context_reset_grace_time", this.currentImpulseContextResetGraceTime);
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return super.isInvulnerableTo(level, source) ? true : (source.is(DamageTypeTags.IS_DROWNING) ? !(Boolean) level.getGameRules().get(GameRules.DROWNING_DAMAGE) : (source.is(DamageTypeTags.IS_FALL) ? !(Boolean) level.getGameRules().get(GameRules.FALL_DAMAGE) : (source.is(DamageTypeTags.IS_FIRE) ? !(Boolean) level.getGameRules().get(GameRules.FIRE_DAMAGE) : (source.is(DamageTypeTags.IS_FREEZING) ? !(Boolean) level.getGameRules().get(GameRules.FREEZE_DAMAGE) : false))));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isInvulnerableTo(level, source)) {
            return false;
        } else if (this.abilities.invulnerable && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        } else {
            this.noActionTime = 0;
            if (this.isDeadOrDying()) {
                return false;
            } else {
                this.removeEntitiesOnShoulder();
                if (source.scalesWithDifficulty()) {
                    if (level.getDifficulty() == Difficulty.PEACEFUL) {
                        damage = 0.0F;
                    }

                    if (level.getDifficulty() == Difficulty.EASY) {
                        damage = Math.min(damage / 2.0F + 1.0F, damage);
                    }

                    if (level.getDifficulty() == Difficulty.HARD) {
                        damage = damage * 3.0F / 2.0F;
                    }
                }

                return damage == 0.0F ? false : super.hurtServer(level, source, damage);
            }
        }
    }

    @Override
    protected void blockUsingItem(ServerLevel level, LivingEntity attacker) {
        super.blockUsingItem(level, attacker);
        ItemStack itemstack = this.getItemBlockingWith();
        BlocksAttacks blocksattacks = itemstack != null ? (BlocksAttacks) itemstack.get(DataComponents.BLOCKS_ATTACKS) : null;
        float f = attacker.getSecondsToDisableBlocking();

        if (f > 0.0F && blocksattacks != null) {
            blocksattacks.disable(level, this, f, itemstack);
        }

    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !this.getAbilities().invulnerable && super.canBeSeenAsEnemy();
    }

    public boolean canHarmPlayer(Player target) {
        Team team = this.getTeam();
        Team team1 = target.getTeam();

        return team == null ? true : (!team.isAlliedTo(team1) ? true : team.isAllowFriendlyFire());
    }

    @Override
    protected void hurtArmor(DamageSource damageSource, float damage) {
        this.doHurtEquipment(damageSource, damage, new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD});
    }

    @Override
    protected void hurtHelmet(DamageSource damageSource, float damage) {
        this.doHurtEquipment(damageSource, damage, new EquipmentSlot[]{EquipmentSlot.HEAD});
    }

    @Override
    protected void actuallyHurt(ServerLevel level, DamageSource source, float dmg) {
        if (!this.isInvulnerableTo(level, source)) {
            dmg = this.getDamageAfterArmorAbsorb(source, dmg);
            dmg = this.getDamageAfterMagicAbsorb(source, dmg);
            float f1 = dmg;

            dmg = Math.max(dmg - this.getAbsorptionAmount(), 0.0F);
            this.setAbsorptionAmount(this.getAbsorptionAmount() - (f1 - dmg));
            float f2 = f1 - dmg;

            if (f2 > 0.0F && f2 < 3.4028235E37F) {
                this.awardStat(Stats.DAMAGE_ABSORBED, Math.round(f2 * 10.0F));
            }

            if (dmg != 0.0F) {
                this.causeFoodExhaustion(source.getFoodExhaustion());
                this.getCombatTracker().recordDamage(source, dmg);
                this.setHealth(this.getHealth() - dmg);
                if (dmg < 3.4028235E37F) {
                    this.awardStat(Stats.DAMAGE_TAKEN, Math.round(dmg * 10.0F));
                }

                this.gameEvent(GameEvent.ENTITY_DAMAGE);
            }
        }
    }

    public boolean isTextFilteringEnabled() {
        return false;
    }

    public void openTextEdit(SignBlockEntity sign, boolean isFrontText) {}

    public void openMinecartCommandBlock(MinecartCommandBlock commandBlock) {}

    public void openCommandBlock(CommandBlockEntity commandBlock) {}

    public void openStructureBlock(StructureBlockEntity structureBlock) {}

    public void openTestBlock(TestBlockEntity testBlock) {}

    public void openTestInstanceBlock(TestInstanceBlockEntity testInstanceBlock) {}

    public void openJigsawBlock(JigsawBlockEntity jigsawBlock) {}

    public void openHorseInventory(AbstractHorse horse, Container container) {}

    public void openNautilusInventory(AbstractNautilus nautilus, Container container) {}

    public OptionalInt openMenu(@Nullable MenuProvider provider) {
        return OptionalInt.empty();
    }

    public void openDialog(Holder<Dialog> dialog) {}

    public void sendMerchantOffers(int containerId, MerchantOffers offers, int merchantLevel, int merchantXp, boolean showProgressBar, boolean canRestock) {}

    public void openItemGui(ItemStack itemStack, InteractionHand hand) {}

    public InteractionResult interactOn(Entity entity, InteractionHand hand) {
        if (this.isSpectator()) {
            if (entity instanceof MenuProvider) {
                this.openMenu((MenuProvider) entity);
            }

            return InteractionResult.PASS;
        } else {
            ItemStack itemstack = this.getItemInHand(hand);
            ItemStack itemstack1 = itemstack.copy();
            InteractionResult interactionresult = entity.interact(this, hand);

            if (interactionresult.consumesAction()) {
                if (this.hasInfiniteMaterials() && itemstack == this.getItemInHand(hand) && itemstack.getCount() < itemstack1.getCount()) {
                    itemstack.setCount(itemstack1.getCount());
                }

                return interactionresult;
            } else {
                if (!itemstack.isEmpty() && entity instanceof LivingEntity) {
                    if (this.hasInfiniteMaterials()) {
                        itemstack = itemstack1;
                    }

                    InteractionResult interactionresult1 = itemstack.interactLivingEntity(this, (LivingEntity) entity, hand);

                    if (interactionresult1.consumesAction()) {
                        this.level().gameEvent(GameEvent.ENTITY_INTERACT, entity.position(), GameEvent.Context.of((Entity) this));
                        if (itemstack.isEmpty() && !this.hasInfiniteMaterials()) {
                            this.setItemInHand(hand, ItemStack.EMPTY);
                        }

                        return interactionresult1;
                    }
                }

                return InteractionResult.PASS;
            }
        }
    }

    @Override
    public void removeVehicle() {
        super.removeVehicle();
        this.boardingCooldown = 0;
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || this.isSleeping();
    }

    @Override
    public boolean isAffectedByFluids() {
        return !this.abilities.flying;
    }

    @Override
    protected Vec3 maybeBackOffFromEdge(Vec3 delta, MoverType moverType) {
        float f = this.maxUpStep();

        if (!this.abilities.flying && delta.y <= 0.0D && (moverType == MoverType.SELF || moverType == MoverType.PLAYER) && this.isStayingOnGroundSurface() && this.isAboveGround(f)) {
            double d0 = delta.x;
            double d1 = delta.z;
            double d2 = 0.05D;
            double d3 = Math.signum(d0) * 0.05D;

            double d4;

            for (d4 = Math.signum(d1) * 0.05D; d0 != 0.0D && this.canFallAtLeast(d0, 0.0D, (double) f); d0 -= d3) {
                if (Math.abs(d0) <= 0.05D) {
                    d0 = 0.0D;
                    break;
                }
            }

            while (d1 != 0.0D && this.canFallAtLeast(0.0D, d1, (double) f)) {
                if (Math.abs(d1) <= 0.05D) {
                    d1 = 0.0D;
                    break;
                }

                d1 -= d4;
            }

            while (d0 != 0.0D && d1 != 0.0D && this.canFallAtLeast(d0, d1, (double) f)) {
                if (Math.abs(d0) <= 0.05D) {
                    d0 = 0.0D;
                } else {
                    d0 -= d3;
                }

                if (Math.abs(d1) <= 0.05D) {
                    d1 = 0.0D;
                } else {
                    d1 -= d4;
                }
            }

            return new Vec3(d0, delta.y, d1);
        } else {
            return delta;
        }
    }

    private boolean isAboveGround(float maxDownStep) {
        return this.onGround() || this.fallDistance < (double) maxDownStep && !this.canFallAtLeast(0.0D, 0.0D, (double) maxDownStep - this.fallDistance);
    }

    private boolean canFallAtLeast(double deltaX, double deltaZ, double minHeight) {
        AABB aabb = this.getBoundingBox();

        return this.level().noCollision(this, new AABB(aabb.minX + 1.0E-7D + deltaX, aabb.minY - minHeight - 1.0E-7D, aabb.minZ + 1.0E-7D + deltaZ, aabb.maxX - 1.0E-7D + deltaX, aabb.minY, aabb.maxZ - 1.0E-7D + deltaZ));
    }

    public void attack(Entity entity) {
        if (!this.cannotAttack(entity)) {
            float f = this.isAutoSpinAttack() ? this.autoSpinAttackDmg : (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
            ItemStack itemstack = this.getWeaponItem();
            DamageSource damagesource = this.createAttackSource(itemstack);
            float f1 = this.getAttackStrengthScale(0.5F);
            float f2 = f1 * (this.getEnchantedDamage(entity, f, damagesource) - f);

            f *= this.baseDamageScaleFactor();
            this.onAttack();
            if (!this.deflectProjectile(entity)) {
                if (f > 0.0F || f2 > 0.0F) {
                    boolean flag = f1 > 0.9F;
                    boolean flag1;

                    if (this.isSprinting() && flag) {
                        this.playServerSideSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK);
                        flag1 = true;
                    } else {
                        flag1 = false;
                    }

                    f += itemstack.getItem().getAttackDamageBonus(entity, f, damagesource);
                    boolean flag2 = flag && this.canCriticalAttack(entity);

                    if (flag2) {
                        f *= 1.5F;
                    }

                    float f3 = f + f2;
                    boolean flag3 = this.isSweepAttack(flag, flag2, flag1);
                    float f4 = 0.0F;

                    if (entity instanceof LivingEntity) {
                        LivingEntity livingentity = (LivingEntity) entity;

                        f4 = livingentity.getHealth();
                    }

                    Vec3 vec3 = entity.getDeltaMovement();
                    boolean flag4 = entity.hurtOrSimulate(damagesource, f3);

                    if (flag4) {
                        this.causeExtraKnockback(entity, this.getKnockback(entity, damagesource) + (flag1 ? 0.5F : 0.0F), vec3);
                        if (flag3) {
                            this.doSweepAttack(entity, f, damagesource, f1);
                        }

                        this.attackVisualEffects(entity, flag2, flag3, flag, false, f2);
                        this.setLastHurtMob(entity);
                        this.itemAttackInteraction(entity, itemstack, damagesource, true);
                        this.damageStatsAndHearts(entity, f4);
                        this.causeFoodExhaustion(0.1F);
                    } else {
                        this.playServerSideSound(SoundEvents.PLAYER_ATTACK_NODAMAGE);
                    }
                }

                this.lungeForwardMaybe();
            }
        }
    }

    private void playServerSideSound(SoundEvent sound) {
        this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), sound, this.getSoundSource(), 1.0F, 1.0F);
    }

    private DamageSource createAttackSource(ItemStack attackingItemStack) {
        return attackingItemStack.getDamageSource(this, () -> {
            return this.damageSources().playerAttack(this);
        });
    }

    private boolean cannotAttack(Entity entity) {
        return !entity.isAttackable() ? true : entity.skipAttackInteraction(this);
    }

    private boolean deflectProjectile(Entity entity) {
        if (entity.getType().is(EntityTypeTags.REDIRECTABLE_PROJECTILE) && entity instanceof Projectile projectile) {
            if (projectile.deflect(ProjectileDeflection.AIM_DEFLECT, this, EntityReference.of(this), true)) {
                this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, this.getSoundSource());
                return true;
            }
        }

        return false;
    }

    private boolean canCriticalAttack(Entity entity) {
        return this.fallDistance > 0.0D && !this.onGround() && !this.onClimbable() && !this.isInWater() && !this.isMobilityRestricted() && !this.isPassenger() && entity instanceof LivingEntity && !this.isSprinting();
    }

    private boolean isSweepAttack(boolean fullStrengthAttack, boolean criticalAttack, boolean knockbackAttack) {
        if (fullStrengthAttack && !criticalAttack && !knockbackAttack && this.onGround()) {
            double d0 = this.getKnownMovement().horizontalDistanceSqr();
            double d1 = (double) this.getSpeed() * 2.5D;

            if (d0 < Mth.square(d1)) {
                return this.getItemInHand(InteractionHand.MAIN_HAND).is(ItemTags.SWORDS);
            }
        }

        return false;
    }

    private void attackVisualEffects(Entity entity, boolean criticalAttack, boolean sweepAttack, boolean fullStrengthAttack, boolean stabAttack, float magicBoost) {
        if (criticalAttack) {
            this.playServerSideSound(SoundEvents.PLAYER_ATTACK_CRIT);
            this.crit(entity);
        }

        if (!criticalAttack && !sweepAttack && !stabAttack) {
            this.playServerSideSound(fullStrengthAttack ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK);
        }

        if (magicBoost > 0.0F) {
            this.magicCrit(entity);
        }

    }

    private void damageStatsAndHearts(Entity entity, float oldLivingEntityHealth) {
        if (entity instanceof LivingEntity) {
            float f1 = oldLivingEntityHealth - ((LivingEntity) entity).getHealth();

            this.awardStat(Stats.DAMAGE_DEALT, Math.round(f1 * 10.0F));
            if (this.level() instanceof ServerLevel && f1 > 2.0F) {
                int i = (int) ((double) f1 * 0.5D);

                ((ServerLevel) this.level()).sendParticles(ParticleTypes.DAMAGE_INDICATOR, entity.getX(), entity.getY(0.5D), entity.getZ(), i, 0.1D, 0.0D, 0.1D, 0.2D);
            }
        }

    }

    private void itemAttackInteraction(Entity entity, ItemStack attackingItemStack, DamageSource damageSource, boolean applyToTarget) {
        Entity entity1 = entity;

        if (entity instanceof EnderDragonPart) {
            entity1 = ((EnderDragonPart) entity).parentMob;
        }

        boolean flag1 = false;
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            if (entity1 instanceof LivingEntity livingentity) {
                flag1 = attackingItemStack.hurtEnemy(livingentity, this);
            }

            if (applyToTarget) {
                EnchantmentHelper.doPostAttackEffectsWithItemSource(serverlevel, entity, damageSource, attackingItemStack);
            }
        }

        if (!this.level().isClientSide() && !attackingItemStack.isEmpty() && entity1 instanceof LivingEntity) {
            if (flag1) {
                attackingItemStack.postHurtEnemy((LivingEntity) entity1, this);
            }

            if (attackingItemStack.isEmpty()) {
                if (attackingItemStack == this.getMainHandItem()) {
                    this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                } else {
                    this.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                }
            }
        }

    }

    @Override
    public void causeExtraKnockback(Entity entity, float knockbackAmount, Vec3 oldMovement) {
        if (knockbackAmount > 0.0F) {
            if (entity instanceof LivingEntity) {
                LivingEntity livingentity = (LivingEntity) entity;

                livingentity.knockback((double) knockbackAmount, (double) Mth.sin((double) (this.getYRot() * ((float) Math.PI / 180F))), (double) (-Mth.cos((double) (this.getYRot() * ((float) Math.PI / 180F)))));
            } else {
                entity.push((double) (-Mth.sin((double) (this.getYRot() * ((float) Math.PI / 180F))) * knockbackAmount), 0.1D, (double) (Mth.cos((double) (this.getYRot() * ((float) Math.PI / 180F))) * knockbackAmount));
            }

            this.setDeltaMovement(this.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
            this.setSprinting(false);
        }

        if (entity instanceof ServerPlayer && entity.hurtMarked) {
            ((ServerPlayer) entity).connection.send(new ClientboundSetEntityMotionPacket(entity));
            entity.hurtMarked = false;
            entity.setDeltaMovement(oldMovement);
        }

    }

    @Override
    public float getVoicePitch() {
        return 1.0F;
    }

    private void doSweepAttack(Entity entity, float baseDamage, DamageSource damageSource, float attackStrengthScale) {
        this.playServerSideSound(SoundEvents.PLAYER_ATTACK_SWEEP);
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            float f2 = 1.0F + (float) this.getAttributeValue(Attributes.SWEEPING_DAMAGE_RATIO) * baseDamage;

            for (LivingEntity livingentity : this.level().getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(1.0D, 0.25D, 1.0D))) {
                if (livingentity != this && livingentity != entity && !this.isAlliedTo((Entity) livingentity)) {
                    if (livingentity instanceof ArmorStand) {
                        ArmorStand armorstand = (ArmorStand) livingentity;

                        if (armorstand.isMarker()) {
                            continue;
                        }
                    }

                    if (this.distanceToSqr((Entity) livingentity) < 9.0D) {
                        float f3 = this.getEnchantedDamage(livingentity, f2, damageSource) * attackStrengthScale;

                        if (livingentity.hurtServer(serverlevel, damageSource, f3)) {
                            livingentity.knockback((double) 0.4F, (double) Mth.sin((double) (this.getYRot() * ((float) Math.PI / 180F))), (double) (-Mth.cos((double) (this.getYRot() * ((float) Math.PI / 180F)))));
                            EnchantmentHelper.doPostAttackEffects(serverlevel, livingentity, damageSource);
                        }
                    }
                }
            }

            double d0 = (double) (-Mth.sin((double) (this.getYRot() * ((float) Math.PI / 180F))));
            double d1 = (double) Mth.cos((double) (this.getYRot() * ((float) Math.PI / 180F)));

            serverlevel.sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX() + d0, this.getY(0.5D), this.getZ() + d1, 0, d0, 0.0D, d1, 0.0D);
        }
    }

    protected float getEnchantedDamage(Entity entity, float dmg, DamageSource damageSource) {
        return dmg;
    }

    @Override
    protected void doAutoAttackOnTouch(LivingEntity entity) {
        this.attack(entity);
    }

    public void crit(Entity entity) {}

    private float baseDamageScaleFactor() {
        float f = this.getAttackStrengthScale(0.5F);

        return 0.2F + f * f * 0.8F;
    }

    @Override
    public boolean stabAttack(EquipmentSlot slot, Entity target, float baseDamage, boolean dealsDamage, boolean dealsKnockback, boolean dismounts) {
        if (this.cannotAttack(target)) {
            return false;
        } else {
            ItemStack itemstack = this.getItemBySlot(slot);
            DamageSource damagesource = this.createAttackSource(itemstack);
            float f1 = this.getEnchantedDamage(target, baseDamage, damagesource) - baseDamage;

            if (!this.isUsingItem() || this.getUsedItemHand().asEquipmentSlot() != slot) {
                f1 *= this.getAttackStrengthScale(0.5F);
                baseDamage *= this.baseDamageScaleFactor();
            }

            if (dealsKnockback && this.deflectProjectile(target)) {
                return true;
            } else {
                float f2 = dealsDamage ? baseDamage + f1 : 0.0F;
                float f3 = 0.0F;

                if (target instanceof LivingEntity) {
                    LivingEntity livingentity = (LivingEntity) target;

                    f3 = livingentity.getHealth();
                }

                Vec3 vec3 = target.getDeltaMovement();
                boolean flag3 = dealsDamage && target.hurtOrSimulate(damagesource, f2);

                if (dealsKnockback) {
                    this.causeExtraKnockback(target, 0.4F + this.getKnockback(target, damagesource), vec3);
                }

                boolean flag4 = false;

                if (dismounts && target.isPassenger()) {
                    flag4 = true;
                    target.stopRiding();
                }

                if (!flag3 && !dealsKnockback && !flag4) {
                    return false;
                } else {
                    this.attackVisualEffects(target, false, false, dealsDamage, true, f1);
                    this.setLastHurtMob(target);
                    this.itemAttackInteraction(target, itemstack, damagesource, flag3);
                    this.damageStatsAndHearts(target, f3);
                    this.causeFoodExhaustion(0.1F);
                    return true;
                }
            }
        }
    }

    public void magicCrit(Entity entity) {}

    @Override
    public void remove(Entity.RemovalReason reason) {
        super.remove(reason);
        this.inventoryMenu.removed(this);
        if (this.hasContainerOpen()) {
            this.doCloseContainer();
        }

    }

    @Override
    public boolean isClientAuthoritative() {
        return true;
    }

    @Override
    protected boolean isLocalClientAuthoritative() {
        return this.isLocalPlayer();
    }

    public boolean isLocalPlayer() {
        return false;
    }

    @Override
    public boolean canSimulateMovement() {
        return !this.level().isClientSide() || this.isLocalPlayer();
    }

    @Override
    public boolean isEffectiveAi() {
        return !this.level().isClientSide() || this.isLocalPlayer();
    }

    public GameProfile getGameProfile() {
        return this.gameProfile;
    }

    public NameAndId nameAndId() {
        return new NameAndId(this.gameProfile);
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    public Abilities getAbilities() {
        return this.abilities;
    }

    @Override
    public boolean hasInfiniteMaterials() {
        return this.abilities.instabuild;
    }

    public boolean preventsBlockDrops() {
        return this.abilities.instabuild;
    }

    public void updateTutorialInventoryAction(ItemStack itemCarried, ItemStack itemInSlot, ClickAction clickAction) {}

    public boolean hasContainerOpen() {
        return this.containerMenu != this.inventoryMenu;
    }

    public boolean canDropItems() {
        return true;
    }

    public Either<Player.BedSleepingProblem, Unit> startSleepInBed(BlockPos pos) {
        this.startSleeping(pos);
        this.sleepCounter = 0;
        return Either.right(Unit.INSTANCE);
    }

    public void stopSleepInBed(boolean forcefulWakeUp, boolean updateLevelList) {
        super.stopSleeping();
        if (this.level() instanceof ServerLevel && updateLevelList) {
            ((ServerLevel) this.level()).updateSleepingPlayerList();
        }

        this.sleepCounter = forcefulWakeUp ? 0 : 100;
    }

    @Override
    public void stopSleeping() {
        this.stopSleepInBed(true, true);
    }

    public boolean isSleepingLongEnough() {
        return this.isSleeping() && this.sleepCounter >= 100;
    }

    public int getSleepTimer() {
        return this.sleepCounter;
    }

    public void displayClientMessage(Component component, boolean overlayMessage) {}

    public void awardStat(Identifier location) {
        this.awardStat(Stats.CUSTOM.get(location));
    }

    public void awardStat(Identifier location, int count) {
        this.awardStat(Stats.CUSTOM.get(location), count);
    }

    public void awardStat(Stat<?> stat) {
        this.awardStat(stat, 1);
    }

    public void awardStat(Stat<?> stat, int count) {}

    public void resetStat(Stat<?> stat) {}

    public int awardRecipes(Collection<RecipeHolder<?>> recipes) {
        return 0;
    }

    public void triggerRecipeCrafted(RecipeHolder<?> recipe, List<ItemStack> itemStacks) {}

    public void awardRecipesByKey(List<ResourceKey<Recipe<?>>> recipeIds) {}

    public int resetRecipes(Collection<RecipeHolder<?>> recipe) {
        return 0;
    }

    @Override
    public void travel(Vec3 input) {
        if (this.isPassenger()) {
            super.travel(input);
        } else {
            if (this.isSwimming()) {
                double d0 = this.getLookAngle().y;
                double d1 = d0 < -0.2D ? 0.085D : 0.06D;

                if (d0 <= 0.0D || this.jumping || !this.level().getFluidState(BlockPos.containing(this.getX(), this.getY() + 1.0D - 0.1D, this.getZ())).isEmpty()) {
                    Vec3 vec31 = this.getDeltaMovement();

                    this.setDeltaMovement(vec31.add(0.0D, (d0 - vec31.y) * d1, 0.0D));
                }
            }

            if (this.getAbilities().flying) {
                double d2 = this.getDeltaMovement().y;

                super.travel(input);
                this.setDeltaMovement(this.getDeltaMovement().with(Direction.Axis.Y, d2 * 0.6D));
            } else {
                super.travel(input);
            }

        }
    }

    @Override
    protected boolean canGlide() {
        return !this.abilities.flying && super.canGlide();
    }

    @Override
    public void updateSwimming() {
        if (this.abilities.flying) {
            this.setSwimming(false);
        } else {
            super.updateSwimming();
        }

    }

    protected boolean freeAt(BlockPos pos) {
        return !this.level().getBlockState(pos).isSuffocating(this.level(), pos);
    }

    @Override
    public float getSpeed() {
        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        if (this.abilities.mayfly) {
            return false;
        } else {
            if (fallDistance >= 2.0D) {
                this.awardStat(Stats.FALL_ONE_CM, (int) Math.round(fallDistance * 100.0D));
            }

            boolean flag = this.currentImpulseImpactPos != null && this.ignoreFallDamageFromCurrentImpulse;
            double d1;

            if (flag) {
                d1 = Math.min(fallDistance, this.currentImpulseImpactPos.y - this.getY());
                boolean flag1 = d1 <= 0.0D;

                if (flag1) {
                    this.resetCurrentImpulseContext();
                } else {
                    this.tryResetCurrentImpulseContext();
                }
            } else {
                d1 = fallDistance;
            }

            if (d1 > 0.0D && super.causeFallDamage(d1, damageModifier, damageSource)) {
                this.resetCurrentImpulseContext();
                return true;
            } else {
                this.propagateFallToPassengers(fallDistance, damageModifier, damageSource);
                return false;
            }
        }
    }

    public boolean tryToStartFallFlying() {
        if (!this.isFallFlying() && this.canGlide() && !this.isInWater()) {
            this.startFallFlying();
            return true;
        } else {
            return false;
        }
    }

    public void startFallFlying() {
        this.setSharedFlag(7, true);
    }

    @Override
    protected void doWaterSplashEffect() {
        if (!this.isSpectator()) {
            super.doWaterSplashEffect();
        }

    }

    @Override
    protected void playStepSound(BlockPos onPos, BlockState onState) {
        if (this.isInWater()) {
            this.waterSwimSound();
            this.playMuffledStepSound(onState);
        } else {
            BlockPos blockpos1 = this.getPrimaryStepSoundBlockPos(onPos);

            if (!onPos.equals(blockpos1)) {
                BlockState blockstate1 = this.level().getBlockState(blockpos1);

                if (blockstate1.is(BlockTags.COMBINATION_STEP_SOUND_BLOCKS)) {
                    this.playCombinationStepSounds(blockstate1, onState);
                } else {
                    super.playStepSound(blockpos1, blockstate1);
                }
            } else {
                super.playStepSound(onPos, onState);
            }
        }

    }

    @Override
    public LivingEntity.Fallsounds getFallSounds() {
        return new LivingEntity.Fallsounds(SoundEvents.PLAYER_SMALL_FALL, SoundEvents.PLAYER_BIG_FALL);
    }

    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity entity, DamageSource source) {
        this.awardStat(Stats.ENTITY_KILLED.get(entity.getType()));
        return true;
    }

    @Override
    public void makeStuckInBlock(BlockState blockState, Vec3 speedMultiplier) {
        if (!this.abilities.flying) {
            super.makeStuckInBlock(blockState, speedMultiplier);
        }

        this.tryResetCurrentImpulseContext();
    }

    public void giveExperiencePoints(int i) {
        this.increaseScore(i);
        this.experienceProgress += (float) i / (float) this.getXpNeededForNextLevel();
        this.totalExperience = Mth.clamp(this.totalExperience + i, 0, Integer.MAX_VALUE);

        while (this.experienceProgress < 0.0F) {
            float f = this.experienceProgress * (float) this.getXpNeededForNextLevel();

            if (this.experienceLevel > 0) {
                this.giveExperienceLevels(-1);
                this.experienceProgress = 1.0F + f / (float) this.getXpNeededForNextLevel();
            } else {
                this.giveExperienceLevels(-1);
                this.experienceProgress = 0.0F;
            }
        }

        while (this.experienceProgress >= 1.0F) {
            this.experienceProgress = (this.experienceProgress - 1.0F) * (float) this.getXpNeededForNextLevel();
            this.giveExperienceLevels(1);
            this.experienceProgress /= (float) this.getXpNeededForNextLevel();
        }

    }

    public int getEnchantmentSeed() {
        return this.enchantmentSeed;
    }

    public void onEnchantmentPerformed(ItemStack itemStack, int enchantmentCost) {
        this.experienceLevel -= enchantmentCost;
        if (this.experienceLevel < 0) {
            this.experienceLevel = 0;
            this.experienceProgress = 0.0F;
            this.totalExperience = 0;
        }

        this.enchantmentSeed = this.random.nextInt();
    }

    public void giveExperienceLevels(int amount) {
        this.experienceLevel = IntMath.saturatedAdd(this.experienceLevel, amount);
        if (this.experienceLevel < 0) {
            this.experienceLevel = 0;
            this.experienceProgress = 0.0F;
            this.totalExperience = 0;
        }

        if (amount > 0 && this.experienceLevel % 5 == 0 && (float) this.lastLevelUpTime < (float) this.tickCount - 100.0F) {
            float f = this.experienceLevel > 30 ? 1.0F : (float) this.experienceLevel / 30.0F;

            this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_LEVELUP, this.getSoundSource(), f * 0.75F, 1.0F);
            this.lastLevelUpTime = this.tickCount;
        }

    }

    public int getXpNeededForNextLevel() {
        return this.experienceLevel >= 30 ? 112 + (this.experienceLevel - 30) * 9 : (this.experienceLevel >= 15 ? 37 + (this.experienceLevel - 15) * 5 : 7 + this.experienceLevel * 2);
    }

    public void causeFoodExhaustion(float amount) {
        if (!this.abilities.invulnerable) {
            if (!this.level().isClientSide()) {
                this.foodData.addExhaustion(amount);
            }

        }
    }

    @Override
    public void lungeForwardMaybe() {
        if (this.hasEnoughFoodToDoExhaustiveManoeuvres()) {
            super.lungeForwardMaybe();
        }

    }

    protected boolean hasEnoughFoodToDoExhaustiveManoeuvres() {
        return this.getFoodData().hasEnoughFood() || this.getAbilities().mayfly;
    }

    public Optional<WardenSpawnTracker> getWardenSpawnTracker() {
        return Optional.empty();
    }

    public FoodData getFoodData() {
        return this.foodData;
    }

    public boolean canEat(boolean canAlwaysEat) {
        return this.abilities.invulnerable || canAlwaysEat || this.foodData.needsFood();
    }

    public boolean isHurt() {
        return this.getHealth() > 0.0F && this.getHealth() < this.getMaxHealth();
    }

    public boolean mayBuild() {
        return this.abilities.mayBuild;
    }

    public boolean mayUseItemAt(BlockPos pos, Direction direction, ItemStack itemStack) {
        if (this.abilities.mayBuild) {
            return true;
        } else {
            BlockPos blockpos1 = pos.relative(direction.getOpposite());
            BlockInWorld blockinworld = new BlockInWorld(this.level(), blockpos1, false);

            return itemStack.canPlaceOnBlockInAdventureMode(blockinworld);
        }
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return !(Boolean) level.getGameRules().get(GameRules.KEEP_INVENTORY) && !this.isSpectator() ? Math.min(this.experienceLevel * 7, 100) : 0;
    }

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return true;
    }

    @Override
    public boolean shouldShowName() {
        return true;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return this.abilities.flying || this.onGround() && this.isDiscrete() ? Entity.MovementEmission.NONE : Entity.MovementEmission.ALL;
    }

    public void onUpdateAbilities() {}

    @Override
    public Component getName() {
        return Component.literal(this.gameProfile.name());
    }

    @Override
    public String getPlainTextName() {
        return this.gameProfile.name();
    }

    public PlayerEnderChestContainer getEnderChestInventory() {
        return this.enderChestInventory;
    }

    @Override
    protected boolean doesEmitEquipEvent(EquipmentSlot slot) {
        return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    public boolean addItem(ItemStack itemStack) {
        return this.inventory.add(itemStack);
    }

    public abstract @Nullable GameType gameMode();

    @Override
    public boolean isSpectator() {
        return this.gameMode() == GameType.SPECTATOR;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return !this.isSpectator() && super.canBeHitByProjectile();
    }

    @Override
    public boolean isSwimming() {
        return !this.abilities.flying && !this.isSpectator() && super.isSwimming();
    }

    public boolean isCreative() {
        return this.gameMode() == GameType.CREATIVE;
    }

    @Override
    public boolean isPushedByFluid() {
        return !this.abilities.flying;
    }

    @Override
    public Component getDisplayName() {
        MutableComponent mutablecomponent = PlayerTeam.formatNameForTeam(this.getTeam(), this.getName());

        return this.decorateDisplayNameComponent(mutablecomponent);
    }

    private MutableComponent decorateDisplayNameComponent(MutableComponent nameComponent) {
        String s = this.getGameProfile().name();

        return nameComponent.withStyle((style) -> {
            return style.withClickEvent(new ClickEvent.SuggestCommand("/tell " + s + " ")).withHoverEvent(this.createHoverEvent()).withInsertion(s);
        });
    }

    @Override
    public String getScoreboardName() {
        return this.getGameProfile().name();
    }

    @Override
    protected void internalSetAbsorptionAmount(float absorptionAmount) {
        this.getEntityData().set(Player.DATA_PLAYER_ABSORPTION_ID, absorptionAmount);
    }

    @Override
    public float getAbsorptionAmount() {
        return (Float) this.getEntityData().get(Player.DATA_PLAYER_ABSORPTION_ID);
    }

    @Override
    public @Nullable SlotAccess getSlot(int slot) {
        if (slot == 499) {
            return new SlotAccess() {
                @Override
                public ItemStack get() {
                    return Player.this.containerMenu.getCarried();
                }

                @Override
                public boolean set(ItemStack itemStack) {
                    Player.this.containerMenu.setCarried(itemStack);
                    return true;
                }
            };
        } else {
            final int j = slot - 500;

            if (j >= 0 && j < 4) {
                return new SlotAccess() {
                    @Override
                    public ItemStack get() {
                        return Player.this.inventoryMenu.getCraftSlots().getItem(j);
                    }

                    @Override
                    public boolean set(ItemStack itemStack) {
                        Player.this.inventoryMenu.getCraftSlots().setItem(j, itemStack);
                        Player.this.inventoryMenu.slotsChanged(Player.this.inventory);
                        return true;
                    }
                };
            } else if (slot >= 0 && slot < this.inventory.getNonEquipmentItems().size()) {
                return this.inventory.getSlot(slot);
            } else {
                int k = slot - 200;

                return k >= 0 && k < this.enderChestInventory.getContainerSize() ? this.enderChestInventory.getSlot(k) : super.getSlot(slot);
            }
        }
    }

    public boolean isReducedDebugInfo() {
        return this.reducedDebugInfo;
    }

    public void setReducedDebugInfo(boolean reducedDebugInfo) {
        this.reducedDebugInfo = reducedDebugInfo;
    }

    @Override
    public void setRemainingFireTicks(int remainingTicks) {
        super.setRemainingFireTicks(this.abilities.invulnerable ? Math.min(remainingTicks, 1) : remainingTicks);
    }

    protected static Optional<Parrot.Variant> extractParrotVariant(CompoundTag tag) {
        if (!tag.isEmpty()) {
            EntityType<?> entitytype = (EntityType) tag.read("id", EntityType.CODEC).orElse((Object) null);

            if (entitytype == EntityType.PARROT) {
                return tag.read("Variant", Parrot.Variant.LEGACY_CODEC);
            }
        }

        return Optional.empty();
    }

    protected static OptionalInt convertParrotVariant(Optional<Parrot.Variant> optional) {
        return (OptionalInt) optional.map((parrot_variant) -> {
            return OptionalInt.of(parrot_variant.getId());
        }).orElse(OptionalInt.empty());
    }

    private static Optional<Parrot.Variant> convertParrotVariant(OptionalInt optionalint) {
        return optionalint.isPresent() ? Optional.of(Parrot.Variant.byId(optionalint.getAsInt())) : Optional.empty();
    }

    public void setShoulderParrotLeft(Optional<Parrot.Variant> optional) {
        this.entityData.set(Player.DATA_SHOULDER_PARROT_LEFT, convertParrotVariant(optional));
    }

    public Optional<Parrot.Variant> getShoulderParrotLeft() {
        return convertParrotVariant((OptionalInt) this.entityData.get(Player.DATA_SHOULDER_PARROT_LEFT));
    }

    public void setShoulderParrotRight(Optional<Parrot.Variant> optional) {
        this.entityData.set(Player.DATA_SHOULDER_PARROT_RIGHT, convertParrotVariant(optional));
    }

    public Optional<Parrot.Variant> getShoulderParrotRight() {
        return convertParrotVariant((OptionalInt) this.entityData.get(Player.DATA_SHOULDER_PARROT_RIGHT));
    }

    public float getCurrentItemAttackStrengthDelay() {
        return (float) (1.0D / this.getAttributeValue(Attributes.ATTACK_SPEED) * 20.0D);
    }

    public boolean cannotAttackWithItem(ItemStack itemStack, int tolerance) {
        float f = (Float) itemStack.getOrDefault(DataComponents.MINIMUM_ATTACK_CHARGE, 0.0F);
        float f1 = (float) (this.attackStrengthTicker + tolerance) / this.getCurrentItemAttackStrengthDelay();

        return f > 0.0F && f1 < f;
    }

    public float getAttackStrengthScale(float a) {
        return Mth.clamp(((float) this.attackStrengthTicker + a) / this.getCurrentItemAttackStrengthDelay(), 0.0F, 1.0F);
    }

    public float getItemSwapScale(float a) {
        return Mth.clamp(((float) this.itemSwapTicker + a) / this.getCurrentItemAttackStrengthDelay(), 0.0F, 1.0F);
    }

    public void resetAttackStrengthTicker() {
        this.attackStrengthTicker = 0;
        this.itemSwapTicker = 0;
    }

    @Override
    public void onAttack() {
        this.resetOnlyAttackStrengthTicker();
        super.onAttack();
    }

    public void resetOnlyAttackStrengthTicker() {
        this.attackStrengthTicker = 0;
    }

    public ItemCooldowns getCooldowns() {
        return this.cooldowns;
    }

    @Override
    protected float getBlockSpeedFactor() {
        return !this.abilities.flying && !this.isFallFlying() ? super.getBlockSpeedFactor() : 1.0F;
    }

    @Override
    public float getLuck() {
        return (float) this.getAttributeValue(Attributes.LUCK);
    }

    public boolean canUseGameMasterBlocks() {
        return this.abilities.instabuild && this.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public PermissionSet permissions() {
        return PermissionSet.NO_PERMISSIONS;
    }

    @Override
    public ImmutableList<Pose> getDismountPoses() {
        return ImmutableList.of(Pose.STANDING, Pose.CROUCHING, Pose.SWIMMING);
    }

    @Override
    public ItemStack getProjectile(ItemStack heldWeapon) {
        if (!(heldWeapon.getItem() instanceof ProjectileWeaponItem)) {
            return ItemStack.EMPTY;
        } else {
            Predicate<ItemStack> predicate = ((ProjectileWeaponItem) heldWeapon.getItem()).getSupportedHeldProjectiles();
            ItemStack itemstack1 = ProjectileWeaponItem.getHeldProjectile(this, predicate);

            if (!itemstack1.isEmpty()) {
                return itemstack1;
            } else {
                predicate = ((ProjectileWeaponItem) heldWeapon.getItem()).getAllSupportedProjectiles();

                for (int i = 0; i < this.inventory.getContainerSize(); ++i) {
                    ItemStack itemstack2 = this.inventory.getItem(i);

                    if (predicate.test(itemstack2)) {
                        return itemstack2;
                    }
                }

                return this.hasInfiniteMaterials() ? new ItemStack(Items.ARROW) : ItemStack.EMPTY;
            }
        }
    }

    @Override
    public Vec3 getRopeHoldPosition(float partialTickTime) {
        double d0 = 0.22D * (this.getMainArm() == HumanoidArm.RIGHT ? -1.0D : 1.0D);
        float f1 = Mth.lerp(partialTickTime * 0.5F, this.getXRot(), this.xRotO) * ((float) Math.PI / 180F);
        float f2 = Mth.lerp(partialTickTime, this.yBodyRotO, this.yBodyRot) * ((float) Math.PI / 180F);

        if (!this.isFallFlying() && !this.isAutoSpinAttack()) {
            if (this.isVisuallySwimming()) {
                return this.getPosition(partialTickTime).add((new Vec3(d0, 0.2D, -0.15D)).xRot(-f1).yRot(-f2));
            } else {
                double d1 = this.getBoundingBox().getYsize() - 1.0D;
                double d2 = this.isCrouching() ? -0.2D : 0.07D;

                return this.getPosition(partialTickTime).add((new Vec3(d0, d1, d2)).yRot(-f2));
            }
        } else {
            Vec3 vec3 = this.getViewVector(partialTickTime);
            Vec3 vec31 = this.getDeltaMovement();
            double d3 = vec31.horizontalDistanceSqr();
            double d4 = vec3.horizontalDistanceSqr();
            float f3;

            if (d3 > 0.0D && d4 > 0.0D) {
                double d5 = (vec31.x * vec3.x + vec31.z * vec3.z) / Math.sqrt(d3 * d4);
                double d6 = vec31.x * vec3.z - vec31.z * vec3.x;

                f3 = (float) (Math.signum(d6) * Math.acos(d5));
            } else {
                f3 = 0.0F;
            }

            return this.getPosition(partialTickTime).add((new Vec3(d0, -0.11D, 0.85D)).zRot(-f3).xRot(-f1).yRot(-f2));
        }
    }

    @Override
    public boolean isAlwaysTicking() {
        return true;
    }

    public boolean isScoping() {
        return this.isUsingItem() && this.getUseItem().is(Items.SPYGLASS);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    public Optional<GlobalPos> getLastDeathLocation() {
        return this.lastDeathLocation;
    }

    public void setLastDeathLocation(Optional<GlobalPos> pos) {
        this.lastDeathLocation = pos;
    }

    @Override
    public float getHurtDir() {
        return this.hurtDir;
    }

    @Override
    public void animateHurt(float yaw) {
        super.animateHurt(yaw);
        this.hurtDir = yaw;
    }

    public boolean isMobilityRestricted() {
        return this.hasEffect(MobEffects.BLINDNESS);
    }

    @Override
    public boolean canSprint() {
        return true;
    }

    @Override
    protected float getFlyingSpeed() {
        return this.abilities.flying && !this.isPassenger() ? (this.isSprinting() ? this.abilities.getFlyingSpeed() * 2.0F : this.abilities.getFlyingSpeed()) : (this.isSprinting() ? 0.025999999F : 0.02F);
    }

    @Override
    public boolean hasContainerOpen(ContainerOpenersCounter container, BlockPos blockPos) {
        return container.isOwnContainer(this);
    }

    @Override
    public double getContainerInteractionRange() {
        return this.blockInteractionRange();
    }

    public double blockInteractionRange() {
        return this.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
    }

    public double entityInteractionRange() {
        return this.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
    }

    public boolean isWithinEntityInteractionRange(Entity entity, double buffer) {
        return entity.isRemoved() ? false : this.isWithinEntityInteractionRange(entity.getBoundingBox(), buffer);
    }

    public boolean isWithinEntityInteractionRange(AABB aabb, double buffer) {
        double d1 = this.entityInteractionRange() + buffer;
        double d2 = aabb.distanceToSqr(this.getEyePosition());

        return d2 < d1 * d1;
    }

    public boolean isWithinAttackRange(AABB aabb, double buffer) {
        return this.entityAttackRange().isInRange(this, aabb, buffer);
    }

    public boolean isWithinBlockInteractionRange(BlockPos pos, double buffer) {
        double d1 = this.blockInteractionRange() + buffer;

        return (new AABB(pos)).distanceToSqr(this.getEyePosition()) < d1 * d1;
    }

    public void setIgnoreFallDamageFromCurrentImpulse(boolean ignoreFallDamage) {
        this.ignoreFallDamageFromCurrentImpulse = ignoreFallDamage;
        if (ignoreFallDamage) {
            this.applyPostImpulseGraceTime(40);
        } else {
            this.currentImpulseContextResetGraceTime = 0;
        }

    }

    public void applyPostImpulseGraceTime(int ticks) {
        this.currentImpulseContextResetGraceTime = Math.max(this.currentImpulseContextResetGraceTime, ticks);
    }

    public boolean isIgnoringFallDamageFromCurrentImpulse() {
        return this.ignoreFallDamageFromCurrentImpulse;
    }

    public void tryResetCurrentImpulseContext() {
        if (this.currentImpulseContextResetGraceTime == 0) {
            this.resetCurrentImpulseContext();
        }

    }

    public boolean isInPostImpulseGraceTime() {
        return this.currentImpulseContextResetGraceTime > 0;
    }

    public void resetCurrentImpulseContext() {
        this.currentImpulseContextResetGraceTime = 0;
        this.currentExplosionCause = null;
        this.currentImpulseImpactPos = null;
        this.ignoreFallDamageFromCurrentImpulse = false;
    }

    public boolean shouldRotateWithMinecart() {
        return false;
    }

    @Override
    public boolean onClimbable() {
        return this.abilities.flying ? false : super.onClimbable();
    }

    public String debugInfo() {
        return MoreObjects.toStringHelper(this).add("name", this.getPlainTextName()).add("id", this.getId()).add("pos", this.position()).add("mode", this.gameMode()).add("permission", this.permissions()).toString();
    }

    public static record BedSleepingProblem(@Nullable Component message) {

        public static final Player.BedSleepingProblem TOO_FAR_AWAY = new Player.BedSleepingProblem(Component.translatable("block.minecraft.bed.too_far_away"));
        public static final Player.BedSleepingProblem OBSTRUCTED = new Player.BedSleepingProblem(Component.translatable("block.minecraft.bed.obstructed"));
        public static final Player.BedSleepingProblem OTHER_PROBLEM = new Player.BedSleepingProblem((Component) null);
        public static final Player.BedSleepingProblem NOT_SAFE = new Player.BedSleepingProblem(Component.translatable("block.minecraft.bed.not_safe"));
    }
}

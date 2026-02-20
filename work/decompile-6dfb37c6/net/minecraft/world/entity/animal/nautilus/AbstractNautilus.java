package net.minecraft.world.entity.animal.nautilus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractMountInventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractNautilus extends TamableAnimal implements PlayerRideableJumping, HasCustomInventoryScreen {

    public static final int INVENTORY_SLOT_OFFSET = 500;
    public static final int INVENTORY_ROWS = 3;
    public static final int SMALL_RESTRICTION_RADIUS = 16;
    public static final int LARGE_RESTRICTION_RADIUS = 32;
    public static final int RESTRICTION_RADIUS_BUFFER = 8;
    private static final int EFFECT_DURATION = 60;
    private static final int EFFECT_REFRESH_RATE = 40;
    private static final double NAUTILUS_WATER_RESISTANCE = 0.9D;
    private static final float IN_WATER_SPEED_MODIFIER = 0.011F;
    private static final float RIDDEN_SPEED_MODIFIER_IN_WATER = 0.0325F;
    private static final float RIDDEN_SPEED_MODIFIER_ON_LAND = 0.02F;
    private static final EntityDataAccessor<Boolean> DASH = SynchedEntityData.<Boolean>defineId(AbstractNautilus.class, EntityDataSerializers.BOOLEAN);
    private static final int DASH_COOLDOWN_TICKS = 40;
    private static final int DASH_MINIMUM_DURATION_TICKS = 5;
    private static final float DASH_MOMENTUM_IN_WATER = 1.2F;
    private static final float DASH_MOMENTUM_ON_LAND = 0.5F;
    private int dashCooldown = 0;
    protected float playerJumpPendingScale;
    public SimpleContainer inventory;
    private static final double BUBBLE_SPREAD_FACTOR = 0.8D;
    private static final double BUBBLE_DIRECTION_SCALE = 1.1D;
    private static final double BUBBLE_Y_OFFSET = 0.25D;
    private static final double BUBBLE_PROBABILITY_MULTIPLIER = 2.0D;
    private static final float BUBBLE_PROBABILITY_MIN = 0.15F;
    private static final float BUBBLE_PROBABILITY_MAX = 1.0F;

    protected AbstractNautilus(EntityType<? extends AbstractNautilus> type, Level level) {
        super(type, level);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.011F, 0.0F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.createInventory();
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return !this.isTame() && !this.isBaby() ? itemStack.is(ItemTags.NAUTILUS_TAMING_ITEMS) : itemStack.is(ItemTags.NAUTILUS_FOOD);
    }

    @Override
    protected void usePlayerItem(Player player, InteractionHand hand, ItemStack itemStack) {
        if (itemStack.is(ItemTags.NAUTILUS_BUCKET_FOOD)) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(itemStack, player, new ItemStack(Items.WATER_BUCKET)));
        } else {
            super.usePlayerItem(player, hand, itemStack);
        }

    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 15.0D).add(Attributes.MOVEMENT_SPEED, 1.0D).add(Attributes.ATTACK_DAMAGE, 3.0D).add(Attributes.KNOCKBACK_RESISTANCE, (double) 0.3F);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F;
    }

    public static boolean checkNautilusSpawnRules(EntityType<? extends AbstractNautilus> type, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random) {
        int i = level.getSeaLevel();
        int j = i - 25;

        return pos.getY() >= j && pos.getY() <= i - 5 && level.getFluidState(pos.below()).is(FluidTags.WATER) && level.getBlockState(pos.above()).is(Blocks.WATER);
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return slot != EquipmentSlot.SADDLE && slot != EquipmentSlot.BODY ? super.canUseSlot(slot) : this.isAlive() && !this.isBaby() && this.isTame();
    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.BODY || slot == EquipmentSlot.SADDLE || super.canDispenserEquipIntoSlot(slot);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return !this.isVehicle();
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        Entity entity = this.getFirstPassenger();

        if (this.isSaddled() && entity instanceof Player player) {
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

        return new Vec3((double) f, (double) f2, (double) f1);
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
        float f2 = 0.5F;

        f += f1 * 0.5F;
        this.setRot(f, vec2.x);
        this.yRotO = this.yBodyRot = this.yHeadRot = f;
        if (this.isLocalInstanceAuthoritative()) {
            if (this.playerJumpPendingScale > 0.0F && !this.isJumping()) {
                this.executeRidersJump(this.playerJumpPendingScale, controller);
            }

            this.playerJumpPendingScale = 0.0F;
        }

    }

    @Override
    protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
        float f = this.getSpeed();

        this.moveRelative(f, input);
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
    }

    @Override
    protected float getRiddenSpeed(Player controller) {
        return this.isInWater() ? 0.0325F * (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) : 0.02F * (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    protected void doPlayerRide(Player player) {
        if (!this.level().isClientSide()) {
            player.startRiding(this);
            if (!this.isVehicle()) {
                this.clearHome();
            }
        }

    }

    private int getNautilusRestrictionRadius() {
        return !this.isBaby() && this.getItemBySlot(EquipmentSlot.SADDLE).isEmpty() ? 32 : 16;
    }

    protected void checkRestriction() {
        if (!this.isLeashed() && !this.isVehicle() && this.isTame()) {
            int i = this.getNautilusRestrictionRadius();

            if (!this.hasHome() || !this.getHomePosition().closerThan(this.blockPosition(), (double) (i + 8)) || i != this.getHomeRadius()) {
                this.setHomeTo(this.blockPosition(), i);
            }
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.checkRestriction();
        super.customServerAiStep(level);
    }

    private void applyEffects(Level level) {
        Entity entity = this.getFirstPassenger();

        if (entity instanceof Player player) {
            boolean flag = player.hasEffect(MobEffects.BREATH_OF_THE_NAUTILUS);
            boolean flag1 = level.getGameTime() % 40L == 0L;

            if (!flag || flag1) {
                player.addEffect(new MobEffectInstance(MobEffects.BREATH_OF_THE_NAUTILUS, 60, 0, true, true, true));
            }
        }

    }

    private void spawnBubbles() {
        double d0 = this.getDeltaMovement().length();
        double d1 = Mth.clamp(d0 * 2.0D, (double) 0.15F, 1.0D);

        if ((double) this.random.nextFloat() < d1) {
            float f = this.getYRot();
            float f1 = Mth.clamp(this.getXRot(), -10.0F, 10.0F);
            Vec3 vec3 = this.calculateViewVector(f1, f);
            double d2 = this.random.nextDouble() * 0.8D * (1.0D + d0);
            double d3 = ((double) this.random.nextFloat() - 0.5D) * d2;
            double d4 = ((double) this.random.nextFloat() - 0.5D) * d2;
            double d5 = ((double) this.random.nextFloat() - 0.5D) * d2;

            this.level().addParticle(ParticleTypes.BUBBLE, this.getX() - vec3.x * 1.1D, this.getY() - vec3.y + 0.25D, this.getZ() - vec3.z * 1.1D, d3, d4, d5);
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            this.applyEffects(this.level());
        }

        if (this.isDashing() && this.dashCooldown < 35) {
            this.setDashing(false);
        }

        if (this.dashCooldown > 0) {
            --this.dashCooldown;
            if (this.dashCooldown == 0) {
                this.makeSound(this.getDashReadySound());
            }
        }

        if (this.isInWater()) {
            this.spawnBubbles();
        }

    }

    @Override
    public boolean canJump() {
        return this.isSaddled();
    }

    @Override
    public void onPlayerJump(int jumpAmount) {
        if (this.isSaddled() && this.dashCooldown <= 0) {
            this.playerJumpPendingScale = this.getPlayerJumpPendingScale(jumpAmount);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(AbstractNautilus.DASH, false);
    }

    public boolean isDashing() {
        return (Boolean) this.entityData.get(AbstractNautilus.DASH);
    }

    public void setDashing(boolean isDashing) {
        this.entityData.set(AbstractNautilus.DASH, isDashing);
    }

    protected void executeRidersJump(float amount, Player controller) {
        this.addDeltaMovement(controller.getLookAngle().scale((double) ((this.isInWater() ? 1.2F : 0.5F) * amount) * this.getAttributeValue(Attributes.MOVEMENT_SPEED) * (double) this.getBlockSpeedFactor()));
        this.dashCooldown = 40;
        this.setDashing(true);
        this.needsSync = true;
    }

    @Override
    public void handleStartJump(int jumpScale) {
        this.makeSound(this.getDashSound());
        this.gameEvent(GameEvent.ENTITY_ACTION);
        this.setDashing(true);
    }

    @Override
    public int getJumpCooldown() {
        return this.dashCooldown;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (!this.firstTick && AbstractNautilus.DASH.equals(accessor)) {
            this.dashCooldown = this.dashCooldown == 0 ? 40 : this.dashCooldown;
        }

        super.onSyncedDataUpdated(accessor);
    }

    @Override
    public void handleStopJump() {}

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {}

    protected @Nullable SoundEvent getDashSound() {
        return null;
    }

    protected @Nullable SoundEvent getDashReadySound() {
        return null;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        this.setPersistenceRequired();
        return super.interact(player, hand);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (this.isBaby()) {
            return super.mobInteract(player, hand);
        } else if (this.isTame() && player.isSecondaryUseActive()) {
            this.openCustomInventoryScreen(player);
            return InteractionResult.SUCCESS;
        } else {
            if (!itemstack.isEmpty()) {
                if (!this.level().isClientSide() && !this.isTame() && this.isFood(itemstack)) {
                    this.usePlayerItem(player, hand, itemstack);
                    this.tryToTame(player);
                    return InteractionResult.SUCCESS_SERVER;
                }

                if (this.isFood(itemstack) && this.getHealth() < this.getMaxHealth()) {
                    FoodProperties foodproperties = (FoodProperties) itemstack.get(DataComponents.FOOD);

                    this.heal(foodproperties != null ? (float) (2 * foodproperties.nutrition()) : 1.0F);
                    this.usePlayerItem(player, hand, itemstack);
                    this.playEatingSound();
                    return InteractionResult.SUCCESS;
                }

                InteractionResult interactionresult = itemstack.interactLivingEntity(player, this, hand);

                if (interactionresult.consumesAction()) {
                    return interactionresult;
                }
            }

            if (this.isTame() && !player.isSecondaryUseActive() && !this.isFood(itemstack)) {
                this.doPlayerRide(player);
                return InteractionResult.SUCCESS;
            } else {
                return super.mobInteract(player, hand);
            }
        }
    }

    private void tryToTame(Player player) {
        if (this.random.nextInt(3) == 0) {
            this.tame(player);
            this.navigation.stop();
            this.level().broadcastEntityEvent(this, (byte) 7);
        } else {
            this.level().broadcastEntityEvent(this, (byte) 6);
        }

        this.playEatingSound();
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean flag = super.hurtServer(level, source, damage);

        if (flag) {
            Entity entity = source.getEntity();

            if (entity instanceof LivingEntity) {
                LivingEntity livingentity = (LivingEntity) entity;

                NautilusAi.setAngerTarget(level, this, livingentity);
            }
        }

        return flag;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance newEffect) {
        return newEffect.getEffect() == MobEffects.POISON ? false : super.canBeAffected(newEffect);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        RandomSource randomsource = level.getRandom();

        NautilusAi.initMemories(this, randomsource);
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    @Override
    protected Holder<SoundEvent> getEquipSound(EquipmentSlot slot, ItemStack stack, Equippable equippable) {
        return (Holder<SoundEvent>) (slot == EquipmentSlot.SADDLE && this.isUnderWater() ? SoundEvents.NAUTILUS_SADDLE_UNDERWATER_EQUIP : (slot == EquipmentSlot.SADDLE ? SoundEvents.NAUTILUS_SADDLE_EQUIP : super.getEquipSound(slot, stack, equippable)));
    }

    public final int getInventorySize() {
        return AbstractMountInventoryMenu.getInventorySize(this.getInventoryColumns());
    }

    protected void createInventory() {
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
    public void openCustomInventoryScreen(Player player) {
        if (!this.level().isClientSide() && (!this.isVehicle() || this.hasPassenger(player)) && this.isTame()) {
            player.openNautilusInventory(this, this.inventory);
        }

    }

    @Override
    public @Nullable SlotAccess getSlot(int slot) {
        int j = slot - 500;

        return j >= 0 && j < this.inventory.getContainerSize() ? this.inventory.getSlot(j) : super.getSlot(slot);
    }

    public boolean hasInventoryChanged(Container oldInventory) {
        return this.inventory != oldInventory;
    }

    public int getInventoryColumns() {
        return 0;
    }

    protected boolean isMobControlled() {
        return this.getFirstPassenger() instanceof Mob;
    }

    protected boolean isAggravated() {
        return this.getBrain().hasMemoryValue(MemoryModuleType.ANGRY_AT) || this.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }
}

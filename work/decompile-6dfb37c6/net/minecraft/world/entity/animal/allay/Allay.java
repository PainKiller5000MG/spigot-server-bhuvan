package net.minecraft.world.entity.animal.allay;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.GameEventTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Allay extends PathfinderMob implements InventoryCarrier, VibrationSystem {

    private static final Vec3i ITEM_PICKUP_REACH = new Vec3i(1, 1, 1);
    private static final int LIFTING_ITEM_ANIMATION_DURATION = 5;
    private static final float DANCING_LOOP_DURATION = 55.0F;
    private static final float SPINNING_ANIMATION_DURATION = 15.0F;
    private static final int DEFAULT_DUPLICATION_COOLDOWN = 0;
    private static final int DUPLICATION_COOLDOWN_TICKS = 6000;
    private static final int NUM_OF_DUPLICATION_HEARTS = 3;
    public static final int MAX_NOTEBLOCK_DISTANCE = 1024;
    private static final EntityDataAccessor<Boolean> DATA_DANCING = SynchedEntityData.<Boolean>defineId(Allay.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CAN_DUPLICATE = SynchedEntityData.<Boolean>defineId(Allay.class, EntityDataSerializers.BOOLEAN);
    protected static final ImmutableList<SensorType<? extends Sensor<? super Allay>>> SENSOR_TYPES = ImmutableList.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.HURT_BY, SensorType.NEAREST_ITEMS);
    protected static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(MemoryModuleType.PATH, MemoryModuleType.LOOK_TARGET, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.HURT_BY, MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, MemoryModuleType.LIKED_PLAYER, MemoryModuleType.LIKED_NOTEBLOCK_POSITION, MemoryModuleType.LIKED_NOTEBLOCK_COOLDOWN_TICKS, MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, MemoryModuleType.IS_PANICKING, new MemoryModuleType[0]);
    public static final ImmutableList<Float> THROW_SOUND_PITCHES = ImmutableList.of(0.5625F, 0.625F, 0.75F, 0.9375F, 1.0F, 1.0F, 1.125F, 1.25F, 1.5F, 1.875F, 2.0F, 2.25F, new Float[]{2.5F, 3.0F, 3.75F, 4.0F});
    private final DynamicGameEventListener<VibrationSystem.Listener> dynamicVibrationListener;
    private VibrationSystem.Data vibrationData;
    private final VibrationSystem.User vibrationUser;
    private final DynamicGameEventListener<Allay.JukeboxListener> dynamicJukeboxListener;
    private final SimpleContainer inventory = new SimpleContainer(1);
    public @Nullable BlockPos jukeboxPos;
    public long duplicationCooldown = 0L;
    private float holdingItemAnimationTicks;
    private float holdingItemAnimationTicks0;
    private float dancingAnimationTicks;
    private float spinningAnimationTicks;
    private float spinningAnimationTicks0;

    public Allay(EntityType<? extends Allay> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setCanPickUpLoot(this.canPickUpLoot());
        this.vibrationUser = new Allay.VibrationUser();
        this.vibrationData = new VibrationSystem.Data();
        this.dynamicVibrationListener = new DynamicGameEventListener<VibrationSystem.Listener>(new VibrationSystem.Listener(this));
        this.dynamicJukeboxListener = new DynamicGameEventListener<Allay.JukeboxListener>(new Allay.JukeboxListener(this.vibrationUser.getPositionSource(), ((GameEvent) GameEvent.JUKEBOX_PLAY.value()).notificationRadius()));
    }

    @Override
    protected Brain.Provider<Allay> brainProvider() {
        return Brain.<Allay>provider(Allay.MEMORY_TYPES, Allay.SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> input) {
        return AllayAi.makeBrain(this.brainProvider().makeBrain(input));
    }

    @Override
    public Brain<Allay> getBrain() {
        return super.getBrain();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0D).add(Attributes.FLYING_SPEED, (double) 0.1F).add(Attributes.MOVEMENT_SPEED, (double) 0.1F).add(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation flyingpathnavigation = new FlyingPathNavigation(this, level);

        flyingpathnavigation.setCanOpenDoors(false);
        flyingpathnavigation.setCanFloat(true);
        flyingpathnavigation.setRequiredPathLength(48.0F);
        return flyingpathnavigation;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Allay.DATA_DANCING, false);
        entityData.define(Allay.DATA_CAN_DUPLICATE, true);
    }

    @Override
    public void travel(Vec3 input) {
        this.travelFlying(input, this.getSpeed());
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return this.isLikedPlayer(source.getEntity()) ? false : super.hurtServer(level, source, damage);
    }

    @Override
    protected boolean considersEntityAsAlly(Entity other) {
        return this.isLikedPlayer(other) || super.considersEntityAsAlly(other);
    }

    private boolean isLikedPlayer(@Nullable Entity other) {
        if (!(other instanceof Player player)) {
            return false;
        } else {
            Optional<UUID> optional = this.getBrain().<UUID>getMemory(MemoryModuleType.LIKED_PLAYER);

            return optional.isPresent() && player.getUUID().equals(optional.get());
        }
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {}

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {}

    @Override
    protected SoundEvent getAmbientSound() {
        return this.hasItemInSlot(EquipmentSlot.MAINHAND) ? SoundEvents.ALLAY_AMBIENT_WITH_ITEM : SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ALLAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("allayBrain");
        this.getBrain().tick(level, this);
        profilerfiller.pop();
        profilerfiller.push("allayActivityUpdate");
        AllayAi.updateActivity(this);
        profilerfiller.pop();
        super.customServerAiStep(level);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide() && this.isAlive() && this.tickCount % 10 == 0) {
            this.heal(1.0F);
        }

        if (this.isDancing() && this.shouldStopDancing() && this.tickCount % 20 == 0) {
            this.setDancing(false);
            this.jukeboxPos = null;
        }

        this.updateDuplicationCooldown();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.holdingItemAnimationTicks0 = this.holdingItemAnimationTicks;
            if (this.hasItemInHand()) {
                this.holdingItemAnimationTicks = Mth.clamp(this.holdingItemAnimationTicks + 1.0F, 0.0F, 5.0F);
            } else {
                this.holdingItemAnimationTicks = Mth.clamp(this.holdingItemAnimationTicks - 1.0F, 0.0F, 5.0F);
            }

            if (this.isDancing()) {
                ++this.dancingAnimationTicks;
                this.spinningAnimationTicks0 = this.spinningAnimationTicks;
                if (this.isSpinning()) {
                    ++this.spinningAnimationTicks;
                } else {
                    --this.spinningAnimationTicks;
                }

                this.spinningAnimationTicks = Mth.clamp(this.spinningAnimationTicks, 0.0F, 15.0F);
            } else {
                this.dancingAnimationTicks = 0.0F;
                this.spinningAnimationTicks = 0.0F;
                this.spinningAnimationTicks0 = 0.0F;
            }
        } else {
            VibrationSystem.Ticker.tick(this.level(), this.vibrationData, this.vibrationUser);
            if (this.isPanicking()) {
                this.setDancing(false);
            }
        }

    }

    @Override
    public boolean canPickUpLoot() {
        return !this.isOnPickupCooldown() && this.hasItemInHand();
    }

    public boolean hasItemInHand() {
        return !this.getItemInHand(InteractionHand.MAIN_HAND).isEmpty();
    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
        return false;
    }

    private boolean isOnPickupCooldown() {
        return this.getBrain().checkMemory(MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, MemoryStatus.VALUE_PRESENT);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        ItemStack itemstack1 = this.getItemInHand(InteractionHand.MAIN_HAND);

        if (this.isDancing() && itemstack.is(ItemTags.DUPLICATES_ALLAYS) && this.canDuplicate()) {
            this.duplicateAllay();
            this.level().broadcastEntityEvent(this, (byte) 18);
            this.level().playSound(player, (Entity) this, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 2.0F, 1.0F);
            this.removeInteractionItem(player, itemstack);
            return InteractionResult.SUCCESS;
        } else if (itemstack1.isEmpty() && !itemstack.isEmpty()) {
            ItemStack itemstack2 = itemstack.copyWithCount(1);

            this.setItemInHand(InteractionHand.MAIN_HAND, itemstack2);
            this.removeInteractionItem(player, itemstack);
            this.level().playSound(player, (Entity) this, SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 2.0F, 1.0F);
            this.getBrain().setMemory(MemoryModuleType.LIKED_PLAYER, player.getUUID());
            return InteractionResult.SUCCESS;
        } else if (!itemstack1.isEmpty() && hand == InteractionHand.MAIN_HAND && itemstack.isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            this.level().playSound(player, (Entity) this, SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.NEUTRAL, 2.0F, 1.0F);
            this.swing(InteractionHand.MAIN_HAND);

            for (ItemStack itemstack3 : this.getInventory().removeAllItems()) {
                BehaviorUtils.throwItem(this, itemstack3, this.position());
            }

            this.getBrain().eraseMemory(MemoryModuleType.LIKED_PLAYER);
            player.addItem(itemstack1);
            return InteractionResult.SUCCESS;
        } else {
            return super.mobInteract(player, hand);
        }
    }

    public void setJukeboxPlaying(BlockPos jukebox, boolean isPlaying) {
        if (isPlaying) {
            if (!this.isDancing()) {
                this.jukeboxPos = jukebox;
                this.setDancing(true);
            }
        } else if (jukebox.equals(this.jukeboxPos) || this.jukeboxPos == null) {
            this.jukeboxPos = null;
            this.setDancing(false);
        }

    }

    @Override
    public SimpleContainer getInventory() {
        return this.inventory;
    }

    @Override
    protected Vec3i getPickupReach() {
        return Allay.ITEM_PICKUP_REACH;
    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack itemStack) {
        ItemStack itemstack1 = this.getItemInHand(InteractionHand.MAIN_HAND);

        return !itemstack1.isEmpty() && (Boolean) level.getGameRules().get(GameRules.MOB_GRIEFING) && this.inventory.canAddItem(itemStack) && this.allayConsidersItemEqual(itemstack1, itemStack);
    }

    private boolean allayConsidersItemEqual(ItemStack item1, ItemStack item2) {
        return ItemStack.isSameItem(item1, item2) && !this.hasNonMatchingPotion(item1, item2);
    }

    private boolean hasNonMatchingPotion(ItemStack itemInHand, ItemStack pickupItem) {
        PotionContents potioncontents = (PotionContents) itemInHand.get(DataComponents.POTION_CONTENTS);
        PotionContents potioncontents1 = (PotionContents) pickupItem.get(DataComponents.POTION_CONTENTS);

        return !Objects.equals(potioncontents, potioncontents1);
    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity entity) {
        InventoryCarrier.pickUpItem(level, this, this, entity);
    }

    @Override
    public boolean isFlapping() {
        return !this.onGround();
    }

    @Override
    public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> action) {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            action.accept(this.dynamicVibrationListener, serverlevel);
            action.accept(this.dynamicJukeboxListener, serverlevel);
        }

    }

    public boolean isDancing() {
        return (Boolean) this.entityData.get(Allay.DATA_DANCING);
    }

    public void setDancing(boolean isDancing) {
        if (!this.level().isClientSide() && this.isEffectiveAi() && (!isDancing || !this.isPanicking())) {
            this.entityData.set(Allay.DATA_DANCING, isDancing);
        }
    }

    private boolean shouldStopDancing() {
        return this.jukeboxPos == null || !this.jukeboxPos.closerToCenterThan(this.position(), (double) ((GameEvent) GameEvent.JUKEBOX_PLAY.value()).notificationRadius()) || !this.level().getBlockState(this.jukeboxPos).is(Blocks.JUKEBOX);
    }

    public float getHoldingItemAnimationProgress(float a) {
        return Mth.lerp(a, this.holdingItemAnimationTicks0, this.holdingItemAnimationTicks) / 5.0F;
    }

    public boolean isSpinning() {
        float f = this.dancingAnimationTicks % 55.0F;

        return f < 15.0F;
    }

    public float getSpinningProgress(float a) {
        return Mth.lerp(a, this.spinningAnimationTicks0, this.spinningAnimationTicks) / 15.0F;
    }

    @Override
    public boolean equipmentHasChanged(ItemStack previous, ItemStack current) {
        return !this.allayConsidersItemEqual(previous, current);
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        super.dropEquipment(level);
        this.inventory.removeAllItems().forEach((itemstack) -> {
            this.spawnAtLocation(level, itemstack);
        });
        ItemStack itemstack = this.getItemBySlot(EquipmentSlot.MAINHAND);

        if (!itemstack.isEmpty() && !EnchantmentHelper.has(itemstack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
            this.spawnAtLocation(level, itemstack);
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        this.writeInventoryToTag(output);
        output.store("listener", VibrationSystem.Data.CODEC, this.vibrationData);
        output.putLong("DuplicationCooldown", this.duplicationCooldown);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.readInventoryFromTag(input);
        this.vibrationData = (VibrationSystem.Data) input.read("listener", VibrationSystem.Data.CODEC).orElseGet(VibrationSystem.Data::new);
        this.setDuplicationCooldown((long) input.getIntOr("DuplicationCooldown", 0));
    }

    @Override
    protected boolean shouldStayCloseToLeashHolder() {
        return false;
    }

    private void updateDuplicationCooldown() {
        if (!this.level().isClientSide() && this.duplicationCooldown > 0L) {
            this.setDuplicationCooldown(this.duplicationCooldown - 1L);
        }

    }

    private void setDuplicationCooldown(long duplicationCooldown) {
        this.duplicationCooldown = duplicationCooldown;
        this.entityData.set(Allay.DATA_CAN_DUPLICATE, duplicationCooldown == 0L);
    }

    public void duplicateAllay() {
        Allay allay = EntityType.ALLAY.create(this.level(), EntitySpawnReason.BREEDING);

        if (allay != null) {
            allay.snapTo(this.position());
            allay.setPersistenceRequired();
            allay.resetDuplicationCooldown();
            this.resetDuplicationCooldown();
            this.level().addFreshEntity(allay);
        }

    }

    public void resetDuplicationCooldown() {
        this.setDuplicationCooldown(6000L);
    }

    public boolean canDuplicate() {
        return (Boolean) this.entityData.get(Allay.DATA_CAN_DUPLICATE);
    }

    private void removeInteractionItem(Player player, ItemStack interactionItem) {
        interactionItem.consume(1, player);
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0D, (double) this.getEyeHeight() * 0.6D, (double) this.getBbWidth() * 0.1D);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 18) {
            for (int i = 0; i < 3; ++i) {
                this.spawnHeartParticle();
            }
        } else {
            super.handleEntityEvent(id);
        }

    }

    private void spawnHeartParticle() {
        double d0 = this.random.nextGaussian() * 0.02D;
        double d1 = this.random.nextGaussian() * 0.02D;
        double d2 = this.random.nextGaussian() * 0.02D;

        this.level().addParticle(ParticleTypes.HEART, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), d0, d1, d2);
    }

    @Override
    public VibrationSystem.Data getVibrationData() {
        return this.vibrationData;
    }

    @Override
    public VibrationSystem.User getVibrationUser() {
        return this.vibrationUser;
    }

    private class JukeboxListener implements GameEventListener {

        private final PositionSource listenerSource;
        private final int listenerRadius;

        public JukeboxListener(PositionSource listenerSource, int listenerRadius) {
            this.listenerSource = listenerSource;
            this.listenerRadius = listenerRadius;
        }

        @Override
        public PositionSource getListenerSource() {
            return this.listenerSource;
        }

        @Override
        public int getListenerRadius() {
            return this.listenerRadius;
        }

        @Override
        public boolean handleGameEvent(ServerLevel level, Holder<GameEvent> event, GameEvent.Context context, Vec3 sourcePosition) {
            if (event.is((Holder) GameEvent.JUKEBOX_PLAY)) {
                Allay.this.setJukeboxPlaying(BlockPos.containing(sourcePosition), true);
                return true;
            } else if (event.is((Holder) GameEvent.JUKEBOX_STOP_PLAY)) {
                Allay.this.setJukeboxPlaying(BlockPos.containing(sourcePosition), false);
                return true;
            } else {
                return false;
            }
        }
    }

    private class VibrationUser implements VibrationSystem.User {

        private static final int VIBRATION_EVENT_LISTENER_RANGE = 16;
        private final PositionSource positionSource = new EntityPositionSource(Allay.this, Allay.this.getEyeHeight());

        private VibrationUser() {}

        @Override
        public int getListenerRadius() {
            return 16;
        }

        @Override
        public PositionSource getPositionSource() {
            return this.positionSource;
        }

        @Override
        public boolean canReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> event, GameEvent.Context context) {
            if (Allay.this.isNoAi()) {
                return false;
            } else {
                Optional<GlobalPos> optional = Allay.this.getBrain().<GlobalPos>getMemory(MemoryModuleType.LIKED_NOTEBLOCK_POSITION);

                if (optional.isEmpty()) {
                    return true;
                } else {
                    GlobalPos globalpos = (GlobalPos) optional.get();

                    return globalpos.isCloseEnough(level.dimension(), Allay.this.blockPosition(), 1024) && globalpos.pos().equals(pos);
                }
            }
        }

        @Override
        public void onReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> event, @Nullable Entity sourceEntity, @Nullable Entity projectileOwner, float receivingDistance) {
            if (event.is((Holder) GameEvent.NOTE_BLOCK_PLAY)) {
                AllayAi.hearNoteblock(Allay.this, new BlockPos(pos));
            }

        }

        @Override
        public TagKey<GameEvent> getListenableEvents() {
            return GameEventTags.ALLAY_CAN_LISTEN;
        }
    }
}

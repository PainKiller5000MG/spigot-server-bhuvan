package net.minecraft.world.entity.animal.axolotl;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.PrimitiveCodec;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.BinaryAnimator;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.EasingType;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Axolotl extends Animal implements Bucketable {

    public static final int TOTAL_PLAYDEAD_TIME = 200;
    private static final int POSE_ANIMATION_TICKS = 10;
    protected static final ImmutableList<? extends SensorType<? extends Sensor<? super Axolotl>>> SENSOR_TYPES = ImmutableList.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_ADULT, SensorType.HURT_BY, SensorType.AXOLOTL_ATTACKABLES, SensorType.FOOD_TEMPTATIONS);
    protected static final ImmutableList<? extends MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(MemoryModuleType.BREED_TARGET, MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, MemoryModuleType.LOOK_TARGET, MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH, MemoryModuleType.ATTACK_TARGET, MemoryModuleType.ATTACK_COOLING_DOWN, MemoryModuleType.NEAREST_VISIBLE_ADULT, new MemoryModuleType[]{MemoryModuleType.HURT_BY_ENTITY, MemoryModuleType.PLAY_DEAD_TICKS, MemoryModuleType.NEAREST_ATTACKABLE, MemoryModuleType.TEMPTING_PLAYER, MemoryModuleType.TEMPTATION_COOLDOWN_TICKS, MemoryModuleType.IS_TEMPTED, MemoryModuleType.HAS_HUNTING_COOLDOWN, MemoryModuleType.IS_PANICKING});
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.<Integer>defineId(Axolotl.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PLAYING_DEAD = SynchedEntityData.<Boolean>defineId(Axolotl.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FROM_BUCKET = SynchedEntityData.<Boolean>defineId(Axolotl.class, EntityDataSerializers.BOOLEAN);
    public static final double PLAYER_REGEN_DETECTION_RANGE = 20.0D;
    public static final int RARE_VARIANT_CHANCE = 1200;
    private static final int AXOLOTL_TOTAL_AIR_SUPPLY = 6000;
    public static final String VARIANT_TAG = "Variant";
    private static final int REHYDRATE_AIR_SUPPLY = 1800;
    private static final int REGEN_BUFF_MAX_DURATION = 2400;
    private static final boolean DEFAULT_FROM_BUCKET = false;
    public final BinaryAnimator playingDeadAnimator;
    public final BinaryAnimator inWaterAnimator;
    public final BinaryAnimator onGroundAnimator;
    public final BinaryAnimator movingAnimator;
    private static final int REGEN_BUFF_BASE_DURATION = 100;

    public Axolotl(EntityType<? extends Axolotl> type, Level level) {
        super(type, level);
        this.playingDeadAnimator = new BinaryAnimator(10, EasingType.IN_OUT_SINE);
        this.inWaterAnimator = new BinaryAnimator(10, EasingType.IN_OUT_SINE);
        this.onGroundAnimator = new BinaryAnimator(10, EasingType.IN_OUT_SINE);
        this.movingAnimator = new BinaryAnimator(10, EasingType.IN_OUT_SINE);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.moveControl = new Axolotl.AxolotlMoveControl(this);
        this.lookControl = new Axolotl.AxolotlLookControl(this, 20);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Axolotl.DATA_VARIANT, 0);
        entityData.define(Axolotl.DATA_PLAYING_DEAD, false);
        entityData.define(Axolotl.FROM_BUCKET, false);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("Variant", Axolotl.Variant.LEGACY_CODEC, this.getVariant());
        output.putBoolean("FromBucket", this.fromBucket());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setVariant((Axolotl.Variant) input.read("Variant", Axolotl.Variant.LEGACY_CODEC).orElse(Axolotl.Variant.DEFAULT));
        this.setFromBucket(input.getBooleanOr("FromBucket", false));
    }

    @Override
    public void playAmbientSound() {
        if (!this.isPlayingDead()) {
            super.playAmbientSound();
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        boolean flag = false;

        if (spawnReason == EntitySpawnReason.BUCKET) {
            return groupData;
        } else {
            RandomSource randomsource = level.getRandom();

            if (groupData instanceof Axolotl.AxolotlGroupData) {
                if (((Axolotl.AxolotlGroupData) groupData).getGroupSize() >= 2) {
                    flag = true;
                }
            } else {
                groupData = new Axolotl.AxolotlGroupData(new Axolotl.Variant[]{Axolotl.Variant.getCommonSpawnVariant(randomsource), Axolotl.Variant.getCommonSpawnVariant(randomsource)});
            }

            this.setVariant(((Axolotl.AxolotlGroupData) groupData).getVariant(randomsource));
            if (flag) {
                this.setAge(-24000);
            }

            return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        }
    }

    @Override
    public void baseTick() {
        int i = this.getAirSupply();

        super.baseTick();
        if (!this.isNoAi()) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                this.handleAirSupply(serverlevel, i);
            }
        }

        if (this.level().isClientSide()) {
            this.tickAnimations();
        }

    }

    private void tickAnimations() {
        Axolotl.AnimationState axolotl_animationstate;

        if (this.isPlayingDead()) {
            axolotl_animationstate = Axolotl.AnimationState.PLAYING_DEAD;
        } else if (this.isInWater()) {
            axolotl_animationstate = Axolotl.AnimationState.IN_WATER;
        } else if (this.onGround()) {
            axolotl_animationstate = Axolotl.AnimationState.ON_GROUND;
        } else {
            axolotl_animationstate = Axolotl.AnimationState.IN_AIR;
        }

        this.playingDeadAnimator.tick(axolotl_animationstate == Axolotl.AnimationState.PLAYING_DEAD);
        this.inWaterAnimator.tick(axolotl_animationstate == Axolotl.AnimationState.IN_WATER);
        this.onGroundAnimator.tick(axolotl_animationstate == Axolotl.AnimationState.ON_GROUND);
        boolean flag = this.walkAnimation.isMoving() || this.getXRot() != this.xRotO || this.getYRot() != this.yRotO;

        this.movingAnimator.tick(flag);
    }

    protected void handleAirSupply(ServerLevel level, int preTickAirSupply) {
        if (this.isAlive() && !this.isInWaterOrRain()) {
            this.setAirSupply(preTickAirSupply - 1);
            if (this.shouldTakeDrowningDamage()) {
                this.setAirSupply(0);
                this.hurtServer(level, this.damageSources().dryOut(), 2.0F);
            }
        } else {
            this.setAirSupply(this.getMaxAirSupply());
        }

    }

    public void rehydrate() {
        int i = this.getAirSupply() + 1800;

        this.setAirSupply(Math.min(i, this.getMaxAirSupply()));
    }

    @Override
    public int getMaxAirSupply() {
        return 6000;
    }

    public Axolotl.Variant getVariant() {
        return Axolotl.Variant.byId((Integer) this.entityData.get(Axolotl.DATA_VARIANT));
    }

    public void setVariant(Axolotl.Variant axolotl_variant) {
        this.entityData.set(Axolotl.DATA_VARIANT, axolotl_variant.getId());
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        return (T) (type == DataComponents.AXOLOTL_VARIANT ? castComponentValue(type, this.getVariant()) : super.get(type));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        this.applyImplicitComponentIfPresent(components, DataComponents.AXOLOTL_VARIANT);
        super.applyImplicitComponents(components);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.AXOLOTL_VARIANT) {
            this.setVariant((Axolotl.Variant) castComponentValue(DataComponents.AXOLOTL_VARIANT, value));
            return true;
        } else {
            return super.applyImplicitComponent(type, value);
        }
    }

    private static boolean useRareVariant(RandomSource random) {
        return random.nextInt(1200) == 0;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    public void setPlayingDead(boolean playingDead) {
        this.entityData.set(Axolotl.DATA_PLAYING_DEAD, playingDead);
    }

    public boolean isPlayingDead() {
        return (Boolean) this.entityData.get(Axolotl.DATA_PLAYING_DEAD);
    }

    @Override
    public boolean fromBucket() {
        return (Boolean) this.entityData.get(Axolotl.FROM_BUCKET);
    }

    @Override
    public void setFromBucket(boolean fromBucket) {
        this.entityData.set(Axolotl.FROM_BUCKET, fromBucket);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Axolotl axolotl = EntityType.AXOLOTL.create(level, EntitySpawnReason.BREEDING);

        if (axolotl != null) {
            Axolotl.Variant axolotl_variant;

            if (useRareVariant(this.random)) {
                axolotl_variant = Axolotl.Variant.getRareSpawnVariant(this.random);
            } else {
                axolotl_variant = this.random.nextBoolean() ? this.getVariant() : ((Axolotl) partner).getVariant();
            }

            axolotl.setVariant(axolotl_variant);
            axolotl.setPersistenceRequired();
        }

        return axolotl;
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return itemStack.is(ItemTags.AXOLOTL_FOOD);
    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("axolotlBrain");
        this.getBrain().tick(level, this);
        profilerfiller.pop();
        profilerfiller.push("axolotlActivityUpdate");
        AxolotlAi.updateActivity(this);
        profilerfiller.pop();
        if (!this.isNoAi()) {
            Optional<Integer> optional = this.getBrain().<Integer>getMemory(MemoryModuleType.PLAY_DEAD_TICKS);

            this.setPlayingDead(optional.isPresent() && (Integer) optional.get() > 0);
        }

    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 14.0D).add(Attributes.MOVEMENT_SPEED, 1.0D).add(Attributes.ATTACK_DAMAGE, 2.0D).add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    public void playAttackSound() {
        this.playSound(SoundEvents.AXOLOTL_ATTACK, 1.0F, 1.0F);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        float f1 = this.getHealth();

        if (!this.isNoAi() && this.level().random.nextInt(3) == 0 && ((float) this.level().random.nextInt(3) < damage || f1 / this.getMaxHealth() < 0.5F) && damage < f1 && this.isInWater() && (source.getEntity() != null || source.getDirectEntity() != null) && !this.isPlayingDead()) {
            this.brain.setMemory(MemoryModuleType.PLAY_DEAD_TICKS, 200);
        }

        return super.hurtServer(level, source, damage);
    }

    @Override
    public int getMaxHeadXRot() {
        return 1;
    }

    @Override
    public int getMaxHeadYRot() {
        return 1;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return (InteractionResult) Bucketable.bucketMobPickup(player, hand, this).orElse(super.mobInteract(player, hand));
    }

    @Override
    public void saveToBucketTag(ItemStack bucket) {
        Bucketable.saveDefaultDataToBucketTag(this, bucket);
        bucket.copyFrom(DataComponents.AXOLOTL_VARIANT, this);
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, bucket, (compoundtag) -> {
            compoundtag.putInt("Age", this.getAge());
            Brain<?> brain = this.getBrain();

            if (brain.hasMemoryValue(MemoryModuleType.HAS_HUNTING_COOLDOWN)) {
                compoundtag.putLong("HuntingCooldown", brain.getTimeUntilExpiry(MemoryModuleType.HAS_HUNTING_COOLDOWN));
            }

        });
    }

    @Override
    public void loadFromBucketTag(CompoundTag tag) {
        Bucketable.loadDefaultDataFromBucketTag(this, tag);
        this.setAge(tag.getIntOr("Age", 0));
        tag.getLong("HuntingCooldown").ifPresentOrElse((olong) -> {
            this.getBrain().setMemoryWithExpiry(MemoryModuleType.HAS_HUNTING_COOLDOWN, true, tag.getLongOr("HuntingCooldown", 0L));
        }, () -> {
            this.getBrain().setMemory(MemoryModuleType.HAS_HUNTING_COOLDOWN, Optional.empty());
        });
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(Items.AXOLOTL_BUCKET);
    }

    @Override
    public SoundEvent getPickupSound() {
        return SoundEvents.BUCKET_FILL_AXOLOTL;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !this.isPlayingDead() && super.canBeSeenAsEnemy();
    }

    public static void onStopAttacking(ServerLevel level, Axolotl body, LivingEntity target) {
        if (target.isDeadOrDying()) {
            DamageSource damagesource = target.getLastDamageSource();

            if (damagesource != null) {
                Entity entity = damagesource.getEntity();

                if (entity != null && entity.getType() == EntityType.PLAYER) {
                    Player player = (Player) entity;
                    List<Player> list = level.<Player>getEntitiesOfClass(Player.class, body.getBoundingBox().inflate(20.0D));

                    if (list.contains(player)) {
                        body.applySupportingEffects(player);
                    }
                }
            }
        }

    }

    public void applySupportingEffects(Player player) {
        MobEffectInstance mobeffectinstance = player.getEffect(MobEffects.REGENERATION);

        if (mobeffectinstance == null || mobeffectinstance.endsWithin(2399)) {
            int i = mobeffectinstance != null ? mobeffectinstance.getDuration() : 0;
            int j = Math.min(2400, 100 + i);

            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, j, 0), this);
        }

        player.removeEffect(MobEffects.MINING_FATIGUE);
    }

    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.fromBucket();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AXOLOTL_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.AXOLOTL_DEATH;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return this.isInWater() ? SoundEvents.AXOLOTL_IDLE_WATER : SoundEvents.AXOLOTL_IDLE_AIR;
    }

    @Override
    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.AXOLOTL_SPLASH;
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.AXOLOTL_SWIM;
    }

    @Override
    protected Brain.Provider<Axolotl> brainProvider() {
        return Brain.<Axolotl>provider(Axolotl.MEMORY_TYPES, Axolotl.SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> input) {
        return AxolotlAi.makeBrain(this.brainProvider().makeBrain(input));
    }

    @Override
    public Brain<Axolotl> getBrain() {
        return super.getBrain();
    }

    @Override
    protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
        this.moveRelative(this.getSpeed(), input);
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
    }

    @Override
    protected void usePlayerItem(Player player, InteractionHand hand, ItemStack itemStack) {
        if (itemStack.is(Items.TROPICAL_FISH_BUCKET)) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(itemStack, player, new ItemStack(Items.WATER_BUCKET)));
        } else {
            super.usePlayerItem(player, hand, itemStack);
        }

    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return !this.fromBucket() && !this.hasCustomName();
    }

    @Override
    public @Nullable LivingEntity getTarget() {
        return this.getTargetFromBrain();
    }

    public static boolean checkAxolotlSpawnRules(EntityType<? extends LivingEntity> type, ServerLevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.AXOLOTLS_SPAWNABLE_ON);
    }

    public static enum Variant implements StringRepresentable {

        LUCY(0, "lucy", true), WILD(1, "wild", true), GOLD(2, "gold", true), CYAN(3, "cyan", true), BLUE(4, "blue", false);

        public static final Axolotl.Variant DEFAULT = Axolotl.Variant.LUCY;
        private static final IntFunction<Axolotl.Variant> BY_ID = ByIdMap.<Axolotl.Variant>continuous(Axolotl.Variant::getId, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, Axolotl.Variant> STREAM_CODEC = ByteBufCodecs.idMapper(Axolotl.Variant.BY_ID, Axolotl.Variant::getId);
        public static final Codec<Axolotl.Variant> CODEC = StringRepresentable.<Axolotl.Variant>fromEnum(Axolotl.Variant::values);
        /** @deprecated */
        @Deprecated
        public static final Codec<Axolotl.Variant> LEGACY_CODEC;
        private final int id;
        private final String name;
        private final boolean common;

        private Variant(int id, String name, boolean common) {
            this.id = id;
            this.name = name;
            this.common = common;
        }

        public int getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public static Axolotl.Variant byId(int id) {
            return (Axolotl.Variant) Axolotl.Variant.BY_ID.apply(id);
        }

        public static Axolotl.Variant getCommonSpawnVariant(RandomSource random) {
            return getSpawnVariant(random, true);
        }

        public static Axolotl.Variant getRareSpawnVariant(RandomSource random) {
            return getSpawnVariant(random, false);
        }

        private static Axolotl.Variant getSpawnVariant(RandomSource random, boolean common) {
            Axolotl.Variant[] aaxolotl_variant = (Axolotl.Variant[]) Arrays.stream(values()).filter((axolotl_variant) -> {
                return axolotl_variant.common == common;
            }).toArray((i) -> {
                return new Axolotl.Variant[i];
            });

            return (Axolotl.Variant) Util.getRandom(aaxolotl_variant, random);
        }

        static {
            PrimitiveCodec primitivecodec = Codec.INT;
            IntFunction intfunction = Axolotl.Variant.BY_ID;

            Objects.requireNonNull(intfunction);
            LEGACY_CODEC = primitivecodec.xmap(intfunction::apply, Axolotl.Variant::getId);
        }
    }

    private static class AxolotlMoveControl extends SmoothSwimmingMoveControl {

        private final Axolotl axolotl;

        public AxolotlMoveControl(Axolotl axolotl) {
            super(axolotl, 85, 10, 0.1F, 0.5F, false);
            this.axolotl = axolotl;
        }

        @Override
        public void tick() {
            if (!this.axolotl.isPlayingDead()) {
                super.tick();
            }

        }
    }

    private class AxolotlLookControl extends SmoothSwimmingLookControl {

        public AxolotlLookControl(Axolotl axolotl, int maxYRotFromCenter) {
            super(axolotl, maxYRotFromCenter);
        }

        @Override
        public void tick() {
            if (!Axolotl.this.isPlayingDead()) {
                super.tick();
            }

        }
    }

    public static class AxolotlGroupData extends AgeableMob.AgeableMobGroupData {

        public final Axolotl.Variant[] types;

        public AxolotlGroupData(Axolotl.Variant... types) {
            super(false);
            this.types = types;
        }

        public Axolotl.Variant getVariant(RandomSource random) {
            return this.types[random.nextInt(this.types.length)];
        }
    }

    public static enum AnimationState {

        PLAYING_DEAD, IN_WATER, ON_GROUND, IN_AIR;

        private AnimationState() {}
    }
}

package net.minecraft.world.entity.animal.parrot;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.PrimitiveCodec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowMobGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LandOnOwnersShoulderGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Parrot extends ShoulderRidingEntity implements FlyingAnimal {

    private static final EntityDataAccessor<Integer> DATA_VARIANT_ID = SynchedEntityData.<Integer>defineId(Parrot.class, EntityDataSerializers.INT);
    private static final Predicate<Mob> NOT_PARROT_PREDICATE = new Predicate<Mob>() {
        public boolean test(@Nullable Mob input) {
            return input != null && Parrot.MOB_SOUND_MAP.containsKey(input.getType());
        }
    };
    private static final Map<EntityType<?>, SoundEvent> MOB_SOUND_MAP = (Map) Util.make(Maps.newHashMap(), (hashmap) -> {
        hashmap.put(EntityType.BLAZE, SoundEvents.PARROT_IMITATE_BLAZE);
        hashmap.put(EntityType.BOGGED, SoundEvents.PARROT_IMITATE_BOGGED);
        hashmap.put(EntityType.BREEZE, SoundEvents.PARROT_IMITATE_BREEZE);
        hashmap.put(EntityType.CAMEL_HUSK, SoundEvents.PARROT_IMITATE_CAMEL_HUSK);
        hashmap.put(EntityType.CAVE_SPIDER, SoundEvents.PARROT_IMITATE_SPIDER);
        hashmap.put(EntityType.CREAKING, SoundEvents.PARROT_IMITATE_CREAKING);
        hashmap.put(EntityType.CREEPER, SoundEvents.PARROT_IMITATE_CREEPER);
        hashmap.put(EntityType.DROWNED, SoundEvents.PARROT_IMITATE_DROWNED);
        hashmap.put(EntityType.ELDER_GUARDIAN, SoundEvents.PARROT_IMITATE_ELDER_GUARDIAN);
        hashmap.put(EntityType.ENDER_DRAGON, SoundEvents.PARROT_IMITATE_ENDER_DRAGON);
        hashmap.put(EntityType.ENDERMITE, SoundEvents.PARROT_IMITATE_ENDERMITE);
        hashmap.put(EntityType.EVOKER, SoundEvents.PARROT_IMITATE_EVOKER);
        hashmap.put(EntityType.GHAST, SoundEvents.PARROT_IMITATE_GHAST);
        hashmap.put(EntityType.HAPPY_GHAST, SoundEvents.EMPTY);
        hashmap.put(EntityType.GUARDIAN, SoundEvents.PARROT_IMITATE_GUARDIAN);
        hashmap.put(EntityType.HOGLIN, SoundEvents.PARROT_IMITATE_HOGLIN);
        hashmap.put(EntityType.HUSK, SoundEvents.PARROT_IMITATE_HUSK);
        hashmap.put(EntityType.ILLUSIONER, SoundEvents.PARROT_IMITATE_ILLUSIONER);
        hashmap.put(EntityType.MAGMA_CUBE, SoundEvents.PARROT_IMITATE_MAGMA_CUBE);
        hashmap.put(EntityType.PARCHED, SoundEvents.PARROT_IMITATE_PARCHED);
        hashmap.put(EntityType.PHANTOM, SoundEvents.PARROT_IMITATE_PHANTOM);
        hashmap.put(EntityType.PIGLIN, SoundEvents.PARROT_IMITATE_PIGLIN);
        hashmap.put(EntityType.PIGLIN_BRUTE, SoundEvents.PARROT_IMITATE_PIGLIN_BRUTE);
        hashmap.put(EntityType.PILLAGER, SoundEvents.PARROT_IMITATE_PILLAGER);
        hashmap.put(EntityType.RAVAGER, SoundEvents.PARROT_IMITATE_RAVAGER);
        hashmap.put(EntityType.SHULKER, SoundEvents.PARROT_IMITATE_SHULKER);
        hashmap.put(EntityType.SILVERFISH, SoundEvents.PARROT_IMITATE_SILVERFISH);
        hashmap.put(EntityType.SKELETON, SoundEvents.PARROT_IMITATE_SKELETON);
        hashmap.put(EntityType.SLIME, SoundEvents.PARROT_IMITATE_SLIME);
        hashmap.put(EntityType.SPIDER, SoundEvents.PARROT_IMITATE_SPIDER);
        hashmap.put(EntityType.STRAY, SoundEvents.PARROT_IMITATE_STRAY);
        hashmap.put(EntityType.VEX, SoundEvents.PARROT_IMITATE_VEX);
        hashmap.put(EntityType.VINDICATOR, SoundEvents.PARROT_IMITATE_VINDICATOR);
        hashmap.put(EntityType.WARDEN, SoundEvents.PARROT_IMITATE_WARDEN);
        hashmap.put(EntityType.WITCH, SoundEvents.PARROT_IMITATE_WITCH);
        hashmap.put(EntityType.WITHER, SoundEvents.PARROT_IMITATE_WITHER);
        hashmap.put(EntityType.WITHER_SKELETON, SoundEvents.PARROT_IMITATE_WITHER_SKELETON);
        hashmap.put(EntityType.ZOGLIN, SoundEvents.PARROT_IMITATE_ZOGLIN);
        hashmap.put(EntityType.ZOMBIE, SoundEvents.PARROT_IMITATE_ZOMBIE);
        hashmap.put(EntityType.ZOMBIE_HORSE, SoundEvents.PARROT_IMITATE_ZOMBIE_HORSE);
        hashmap.put(EntityType.ZOMBIE_NAUTILUS, SoundEvents.PARROT_IMITATE_ZOMBIE_NAUTILUS);
        hashmap.put(EntityType.ZOMBIE_VILLAGER, SoundEvents.PARROT_IMITATE_ZOMBIE_VILLAGER);
    });
    public float flap;
    public float flapSpeed;
    public float oFlapSpeed;
    public float oFlap;
    private float flapping = 1.0F;
    private float nextFlap = 1.0F;
    private boolean partyParrot;
    private @Nullable BlockPos jukebox;

    public Parrot(EntityType<? extends Parrot> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 10, false);
        this.setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.COCOA, -1.0F);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        this.setVariant((Parrot.Variant) Util.getRandom(Parrot.Variant.values(), level.getRandom()));
        if (groupData == null) {
            groupData = new AgeableMob.AgeableMobGroupData(false);
        }

        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new TamableAnimal.TamableAnimalPanicGoal(1.25D));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.0D, 5.0F, 1.0F));
        this.goalSelector.addGoal(2, new Parrot.ParrotWanderGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new LandOnOwnersShoulderGoal(this));
        this.goalSelector.addGoal(3, new FollowMobGoal(this, 1.0D, 3.0F, 7.0F));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 6.0D).add(Attributes.FLYING_SPEED, (double) 0.4F).add(Attributes.MOVEMENT_SPEED, (double) 0.2F).add(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation flyingpathnavigation = new FlyingPathNavigation(this, level);

        flyingpathnavigation.setCanOpenDoors(false);
        flyingpathnavigation.setCanFloat(true);
        return flyingpathnavigation;
    }

    @Override
    public void aiStep() {
        if (this.jukebox == null || !this.jukebox.closerToCenterThan(this.position(), 3.46D) || !this.level().getBlockState(this.jukebox).is(Blocks.JUKEBOX)) {
            this.partyParrot = false;
            this.jukebox = null;
        }

        if (this.level().random.nextInt(400) == 0) {
            imitateNearbyMobs(this.level(), this);
        }

        super.aiStep();
        this.calculateFlapping();
    }

    @Override
    public void setRecordPlayingNearby(BlockPos jukebox, boolean isPlaying) {
        this.jukebox = jukebox;
        this.partyParrot = isPlaying;
    }

    public boolean isPartyParrot() {
        return this.partyParrot;
    }

    private void calculateFlapping() {
        this.oFlap = this.flap;
        this.oFlapSpeed = this.flapSpeed;
        this.flapSpeed += (float) (!this.onGround() && !this.isPassenger() ? 4 : -1) * 0.3F;
        this.flapSpeed = Mth.clamp(this.flapSpeed, 0.0F, 1.0F);
        if (!this.onGround() && this.flapping < 1.0F) {
            this.flapping = 1.0F;
        }

        this.flapping *= 0.9F;
        Vec3 vec3 = this.getDeltaMovement();

        if (!this.onGround() && vec3.y < 0.0D) {
            this.setDeltaMovement(vec3.multiply(1.0D, 0.6D, 1.0D));
        }

        this.flap += this.flapping * 2.0F;
    }

    public static boolean imitateNearbyMobs(Level level, Entity entity) {
        if (entity.isAlive() && !entity.isSilent() && level.random.nextInt(2) == 0) {
            List<Mob> list = level.<Mob>getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(20.0D), Parrot.NOT_PARROT_PREDICATE);

            if (!list.isEmpty()) {
                Mob mob = (Mob) list.get(level.random.nextInt(list.size()));

                if (!mob.isSilent()) {
                    SoundEvent soundevent = getImitatedSound(mob.getType());

                    level.playSound((Entity) null, entity.getX(), entity.getY(), entity.getZ(), soundevent, entity.getSoundSource(), 0.7F, getPitch(level.random));
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (!this.isTame() && itemstack.is(ItemTags.PARROT_FOOD)) {
            this.usePlayerItem(player, hand, itemstack);
            if (!this.isSilent()) {
                this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEvents.PARROT_EAT, this.getSoundSource(), 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
            }

            if (!this.level().isClientSide()) {
                if (this.random.nextInt(10) == 0) {
                    this.tame(player);
                    this.level().broadcastEntityEvent(this, (byte) 7);
                } else {
                    this.level().broadcastEntityEvent(this, (byte) 6);
                }
            }

            return InteractionResult.SUCCESS;
        } else if (!itemstack.is(ItemTags.PARROT_POISONOUS_FOOD)) {
            if (!this.isFlying() && this.isTame() && this.isOwnedBy(player)) {
                if (!this.level().isClientSide()) {
                    this.setOrderedToSit(!this.isOrderedToSit());
                }

                return InteractionResult.SUCCESS;
            } else {
                return super.mobInteract(player, hand);
            }
        } else {
            this.usePlayerItem(player, hand, itemstack);
            this.addEffect(new MobEffectInstance(MobEffects.POISON, 900));
            if (player.isCreative() || !this.isInvulnerable()) {
                this.hurt(this.damageSources().playerAttack(player), Float.MAX_VALUE);
            }

            return InteractionResult.SUCCESS;
        }
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return false;
    }

    public static boolean checkParrotSpawnRules(EntityType<Parrot> type, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.PARROTS_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {}

    @Override
    public boolean canMate(Animal partner) {
        return false;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public @Nullable SoundEvent getAmbientSound() {
        return getAmbient(this.level(), this.level().random);
    }

    public static SoundEvent getAmbient(Level level, RandomSource random) {
        if (level.getDifficulty() != Difficulty.PEACEFUL && random.nextInt(1000) == 0) {
            List<EntityType<?>> list = Lists.newArrayList(Parrot.MOB_SOUND_MAP.keySet());

            return getImitatedSound((EntityType) list.get(random.nextInt(list.size())));
        } else {
            return SoundEvents.PARROT_AMBIENT;
        }
    }

    private static SoundEvent getImitatedSound(EntityType<?> id) {
        return (SoundEvent) Parrot.MOB_SOUND_MAP.getOrDefault(id, SoundEvents.PARROT_AMBIENT);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PARROT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PARROT_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
        this.playSound(SoundEvents.PARROT_STEP, 0.15F, 1.0F);
    }

    @Override
    protected boolean isFlapping() {
        return this.flyDist > this.nextFlap;
    }

    @Override
    protected void onFlap() {
        this.playSound(SoundEvents.PARROT_FLY, 0.15F, 1.0F);
        this.nextFlap = this.flyDist + this.flapSpeed / 2.0F;
    }

    @Override
    public float getVoicePitch() {
        return getPitch(this.random);
    }

    public static float getPitch(RandomSource random) {
        return (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    protected void doPush(Entity entity) {
        if (!(entity instanceof Player)) {
            super.doPush(entity);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isInvulnerableTo(level, source)) {
            return false;
        } else {
            this.setOrderedToSit(false);
            return super.hurtServer(level, source, damage);
        }
    }

    public Parrot.Variant getVariant() {
        return Parrot.Variant.byId((Integer) this.entityData.get(Parrot.DATA_VARIANT_ID));
    }

    public void setVariant(Parrot.Variant parrot_variant) {
        this.entityData.set(Parrot.DATA_VARIANT_ID, parrot_variant.id);
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        return (T) (type == DataComponents.PARROT_VARIANT ? castComponentValue(type, this.getVariant()) : super.get(type));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        this.applyImplicitComponentIfPresent(components, DataComponents.PARROT_VARIANT);
        super.applyImplicitComponents(components);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.PARROT_VARIANT) {
            this.setVariant((Parrot.Variant) castComponentValue(DataComponents.PARROT_VARIANT, value));
            return true;
        } else {
            return super.applyImplicitComponent(type, value);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Parrot.DATA_VARIANT_ID, Parrot.Variant.DEFAULT.id);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("Variant", Parrot.Variant.LEGACY_CODEC, this.getVariant());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setVariant((Parrot.Variant) input.read("Variant", Parrot.Variant.LEGACY_CODEC).orElse(Parrot.Variant.DEFAULT));
    }

    @Override
    public boolean isFlying() {
        return !this.onGround();
    }

    @Override
    protected boolean canFlyToOwner() {
        return true;
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0D, (double) (0.5F * this.getEyeHeight()), (double) (this.getBbWidth() * 0.4F));
    }

    public static enum Variant implements StringRepresentable {

        RED_BLUE(0, "red_blue"), BLUE(1, "blue"), GREEN(2, "green"), YELLOW_BLUE(3, "yellow_blue"), GRAY(4, "gray");

        public static final Parrot.Variant DEFAULT = Parrot.Variant.RED_BLUE;
        private static final IntFunction<Parrot.Variant> BY_ID = ByIdMap.<Parrot.Variant>continuous(Parrot.Variant::getId, values(), ByIdMap.OutOfBoundsStrategy.CLAMP);
        public static final Codec<Parrot.Variant> CODEC = StringRepresentable.<Parrot.Variant>fromEnum(Parrot.Variant::values);
        /** @deprecated */
        @Deprecated
        public static final Codec<Parrot.Variant> LEGACY_CODEC;
        public static final StreamCodec<ByteBuf, Parrot.Variant> STREAM_CODEC;
        private final int id;
        private final String name;

        private Variant(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return this.id;
        }

        public static Parrot.Variant byId(int id) {
            return (Parrot.Variant) Parrot.Variant.BY_ID.apply(id);
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        static {
            PrimitiveCodec primitivecodec = Codec.INT;
            IntFunction intfunction = Parrot.Variant.BY_ID;

            Objects.requireNonNull(intfunction);
            LEGACY_CODEC = primitivecodec.xmap(intfunction::apply, Parrot.Variant::getId);
            STREAM_CODEC = ByteBufCodecs.idMapper(Parrot.Variant.BY_ID, Parrot.Variant::getId);
        }
    }

    private static class ParrotWanderGoal extends WaterAvoidingRandomFlyingGoal {

        public ParrotWanderGoal(PathfinderMob mob, double speedModifier) {
            super(mob, speedModifier);
        }

        @Override
        protected @Nullable Vec3 getPosition() {
            Vec3 vec3 = null;

            if (this.mob.isInWater()) {
                vec3 = LandRandomPos.getPos(this.mob, 15, 15);
            }

            if (this.mob.getRandom().nextFloat() >= this.probability) {
                vec3 = this.getTreePos();
            }

            return vec3 == null ? super.getPosition() : vec3;
        }

        private @Nullable Vec3 getTreePos() {
            BlockPos blockpos = this.mob.blockPosition();
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos blockpos_mutableblockpos1 = new BlockPos.MutableBlockPos();

            for (BlockPos blockpos1 : BlockPos.betweenClosed(Mth.floor(this.mob.getX() - 3.0D), Mth.floor(this.mob.getY() - 6.0D), Mth.floor(this.mob.getZ() - 3.0D), Mth.floor(this.mob.getX() + 3.0D), Mth.floor(this.mob.getY() + 6.0D), Mth.floor(this.mob.getZ() + 3.0D))) {
                if (!blockpos.equals(blockpos1)) {
                    BlockState blockstate = this.mob.level().getBlockState(blockpos_mutableblockpos1.setWithOffset(blockpos1, Direction.DOWN));
                    boolean flag = blockstate.getBlock() instanceof LeavesBlock || blockstate.is(BlockTags.LOGS);

                    if (flag && this.mob.level().isEmptyBlock(blockpos1) && this.mob.level().isEmptyBlock(blockpos_mutableblockpos.setWithOffset(blockpos1, Direction.UP))) {
                        return Vec3.atBottomCenterOf(blockpos1);
                    }
                }
            }

            return null;
        }
    }
}

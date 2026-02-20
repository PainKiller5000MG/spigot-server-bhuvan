package net.minecraft.world.entity.animal.goat;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.InstrumentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Goat extends Animal {

    public static final EntityDimensions LONG_JUMPING_DIMENSIONS = EntityDimensions.scalable(0.9F, 1.3F).scale(0.7F);
    private static final int ADULT_ATTACK_DAMAGE = 2;
    private static final int BABY_ATTACK_DAMAGE = 1;
    protected static final ImmutableList<SensorType<? extends Sensor<? super Goat>>> SENSOR_TYPES = ImmutableList.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.NEAREST_ITEMS, SensorType.NEAREST_ADULT, SensorType.HURT_BY, SensorType.FOOD_TEMPTATIONS);
    protected static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(MemoryModuleType.LOOK_TARGET, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH, MemoryModuleType.ATE_RECENTLY, MemoryModuleType.BREED_TARGET, MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, MemoryModuleType.LONG_JUMP_MID_JUMP, MemoryModuleType.TEMPTING_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ADULT, MemoryModuleType.TEMPTATION_COOLDOWN_TICKS, new MemoryModuleType[]{MemoryModuleType.IS_TEMPTED, MemoryModuleType.RAM_COOLDOWN_TICKS, MemoryModuleType.RAM_TARGET, MemoryModuleType.IS_PANICKING});
    public static final int GOAT_FALL_DAMAGE_REDUCTION = 10;
    public static final double GOAT_SCREAMING_CHANCE = 0.02D;
    public static final double UNIHORN_CHANCE = (double) 0.1F;
    private static final EntityDataAccessor<Boolean> DATA_IS_SCREAMING_GOAT = SynchedEntityData.<Boolean>defineId(Goat.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> DATA_HAS_LEFT_HORN = SynchedEntityData.<Boolean>defineId(Goat.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> DATA_HAS_RIGHT_HORN = SynchedEntityData.<Boolean>defineId(Goat.class, EntityDataSerializers.BOOLEAN);
    private static final boolean DEFAULT_IS_SCREAMING = false;
    private static final boolean DEFAULT_HAS_LEFT_HORN = true;
    private static final boolean DEFAULT_HAS_RIGHT_HORN = true;
    private boolean isLoweringHead;
    private int lowerHeadTick;

    public Goat(EntityType<? extends Goat> type, Level level) {
        super(type, level);
        this.getNavigation().setCanFloat(true);
        this.setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
        this.setPathfindingMalus(PathType.DANGER_POWDER_SNOW, -1.0F);
    }

    public ItemStack createHorn() {
        RandomSource randomsource = RandomSource.create((long) this.getUUID().hashCode());
        TagKey<Instrument> tagkey = this.isScreamingGoat() ? InstrumentTags.SCREAMING_GOAT_HORNS : InstrumentTags.REGULAR_GOAT_HORNS;

        return (ItemStack) this.level().registryAccess().lookupOrThrow(Registries.INSTRUMENT).getRandomElementOf(tagkey, randomsource).map((holder) -> {
            return InstrumentItem.create(Items.GOAT_HORN, holder);
        }).orElseGet(() -> {
            return new ItemStack(Items.GOAT_HORN);
        });
    }

    @Override
    protected Brain.Provider<Goat> brainProvider() {
        return Brain.<Goat>provider(Goat.MEMORY_TYPES, Goat.SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> input) {
        return GoatAi.makeBrain(this.brainProvider().makeBrain(input));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 10.0D).add(Attributes.MOVEMENT_SPEED, (double) 0.2F).add(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    @Override
    protected void ageBoundaryReached() {
        if (this.isBaby()) {
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0D);
            this.removeHorns();
        } else {
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2.0D);
            this.addHorns();
        }

    }

    @Override
    protected int calculateFallDamage(double fallDistance, float damageModifier) {
        return super.calculateFallDamage(fallDistance, damageModifier) - 10;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_AMBIENT : SoundEvents.GOAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_HURT : SoundEvents.GOAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_DEATH : SoundEvents.GOAT_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
        this.playSound(SoundEvents.GOAT_STEP, 0.15F, 1.0F);
    }

    protected SoundEvent getMilkingSound() {
        return this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_MILK : SoundEvents.GOAT_MILK;
    }

    @Override
    public @Nullable Goat getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Goat goat = EntityType.GOAT.create(level, EntitySpawnReason.BREEDING);

        if (goat != null) {
            boolean flag;
            label22:
            {
                label21:
                {
                    GoatAi.initMemories(goat, level.getRandom());
                    AgeableMob ageablemob1 = (AgeableMob) (level.getRandom().nextBoolean() ? this : partner);

                    if (ageablemob1 instanceof Goat) {
                        Goat goat1 = (Goat) ageablemob1;

                        if (goat1.isScreamingGoat()) {
                            break label21;
                        }
                    }

                    if (level.getRandom().nextDouble() >= 0.02D) {
                        flag = false;
                        break label22;
                    }
                }

                flag = true;
            }

            boolean flag1 = flag;

            goat.setScreamingGoat(flag1);
        }

        return goat;
    }

    @Override
    public Brain<Goat> getBrain() {
        return super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("goatBrain");
        this.getBrain().tick(level, this);
        profilerfiller.pop();
        profilerfiller.push("goatActivityUpdate");
        GoatAi.updateActivity(this);
        profilerfiller.pop();
        super.customServerAiStep(level);
    }

    @Override
    public int getMaxHeadYRot() {
        return 15;
    }

    @Override
    public void setYHeadRot(float yHeadRot) {
        int i = this.getMaxHeadYRot();
        float f1 = Mth.degreesDifference(this.yBodyRot, yHeadRot);
        float f2 = Mth.clamp(f1, (float) (-i), (float) i);

        super.setYHeadRot(this.yBodyRot + f2);
    }

    @Override
    protected void playEatingSound() {
        this.level().playSound((Entity) null, (Entity) this, this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_EAT : SoundEvents.GOAT_EAT, SoundSource.NEUTRAL, 1.0F, Mth.randomBetween(this.level().random, 0.8F, 1.2F));
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return itemStack.is(ItemTags.GOAT_FOOD);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.is(Items.BUCKET) && !this.isBaby()) {
            player.playSound(this.getMilkingSound(), 1.0F, 1.0F);
            ItemStack itemstack1 = ItemUtils.createFilledResult(itemstack, player, Items.MILK_BUCKET.getDefaultInstance());

            player.setItemInHand(hand, itemstack1);
            return InteractionResult.SUCCESS;
        } else {
            InteractionResult interactionresult = super.mobInteract(player, hand);

            if (interactionresult.consumesAction() && this.isFood(itemstack)) {
                this.playEatingSound();
            }

            return interactionresult;
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        RandomSource randomsource = level.getRandom();

        GoatAi.initMemories(this, randomsource);
        this.setScreamingGoat(randomsource.nextDouble() < 0.02D);
        this.ageBoundaryReached();
        if (!this.isBaby() && (double) randomsource.nextFloat() < (double) 0.1F) {
            EntityDataAccessor<Boolean> entitydataaccessor = randomsource.nextBoolean() ? Goat.DATA_HAS_LEFT_HORN : Goat.DATA_HAS_RIGHT_HORN;

            this.entityData.set(entitydataaccessor, false);
        }

        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return pose == Pose.LONG_JUMPING ? Goat.LONG_JUMPING_DIMENSIONS.scale(this.getAgeScale()) : super.getDefaultDimensions(pose);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("IsScreamingGoat", this.isScreamingGoat());
        output.putBoolean("HasLeftHorn", this.hasLeftHorn());
        output.putBoolean("HasRightHorn", this.hasRightHorn());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setScreamingGoat(input.getBooleanOr("IsScreamingGoat", false));
        this.entityData.set(Goat.DATA_HAS_LEFT_HORN, input.getBooleanOr("HasLeftHorn", true));
        this.entityData.set(Goat.DATA_HAS_RIGHT_HORN, input.getBooleanOr("HasRightHorn", true));
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 58) {
            this.isLoweringHead = true;
        } else if (id == 59) {
            this.isLoweringHead = false;
        } else {
            super.handleEntityEvent(id);
        }

    }

    @Override
    public void aiStep() {
        if (this.isLoweringHead) {
            ++this.lowerHeadTick;
        } else {
            this.lowerHeadTick -= 2;
        }

        this.lowerHeadTick = Mth.clamp(this.lowerHeadTick, 0, 20);
        super.aiStep();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Goat.DATA_IS_SCREAMING_GOAT, false);
        entityData.define(Goat.DATA_HAS_LEFT_HORN, true);
        entityData.define(Goat.DATA_HAS_RIGHT_HORN, true);
    }

    public boolean hasLeftHorn() {
        return (Boolean) this.entityData.get(Goat.DATA_HAS_LEFT_HORN);
    }

    public boolean hasRightHorn() {
        return (Boolean) this.entityData.get(Goat.DATA_HAS_RIGHT_HORN);
    }

    public boolean dropHorn() {
        boolean flag = this.hasLeftHorn();
        boolean flag1 = this.hasRightHorn();

        if (!flag && !flag1) {
            return false;
        } else {
            EntityDataAccessor<Boolean> entitydataaccessor;

            if (!flag) {
                entitydataaccessor = Goat.DATA_HAS_RIGHT_HORN;
            } else if (!flag1) {
                entitydataaccessor = Goat.DATA_HAS_LEFT_HORN;
            } else {
                entitydataaccessor = this.random.nextBoolean() ? Goat.DATA_HAS_LEFT_HORN : Goat.DATA_HAS_RIGHT_HORN;
            }

            this.entityData.set(entitydataaccessor, false);
            Vec3 vec3 = this.position();
            ItemStack itemstack = this.createHorn();
            double d0 = (double) Mth.randomBetween(this.random, -0.2F, 0.2F);
            double d1 = (double) Mth.randomBetween(this.random, 0.3F, 0.7F);
            double d2 = (double) Mth.randomBetween(this.random, -0.2F, 0.2F);
            ItemEntity itementity = new ItemEntity(this.level(), vec3.x(), vec3.y(), vec3.z(), itemstack, d0, d1, d2);

            this.level().addFreshEntity(itementity);
            return true;
        }
    }

    public void addHorns() {
        this.entityData.set(Goat.DATA_HAS_LEFT_HORN, true);
        this.entityData.set(Goat.DATA_HAS_RIGHT_HORN, true);
    }

    public void removeHorns() {
        this.entityData.set(Goat.DATA_HAS_LEFT_HORN, false);
        this.entityData.set(Goat.DATA_HAS_RIGHT_HORN, false);
    }

    public boolean isScreamingGoat() {
        return (Boolean) this.entityData.get(Goat.DATA_IS_SCREAMING_GOAT);
    }

    public void setScreamingGoat(boolean isScreamingGoat) {
        this.entityData.set(Goat.DATA_IS_SCREAMING_GOAT, isScreamingGoat);
    }

    public float getRammingXHeadRot() {
        return (float) this.lowerHeadTick / 20.0F * 30.0F * ((float) Math.PI / 180F);
    }

    public static boolean checkGoatSpawnRules(EntityType<? extends Animal> type, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.GOATS_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }
}

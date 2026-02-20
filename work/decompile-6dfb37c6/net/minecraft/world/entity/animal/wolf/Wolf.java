package net.minecraft.world.entity.animal.wolf;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Crackiness;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BegGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Wolf extends TamableAnimal implements NeutralMob {

    private static final EntityDataAccessor<Boolean> DATA_INTERESTED_ID = SynchedEntityData.<Boolean>defineId(Wolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_COLLAR_COLOR = SynchedEntityData.<Integer>defineId(Wolf.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_ANGER_END_TIME = SynchedEntityData.<Long>defineId(Wolf.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Holder<WolfVariant>> DATA_VARIANT_ID = SynchedEntityData.<Holder<WolfVariant>>defineId(Wolf.class, EntityDataSerializers.WOLF_VARIANT);
    private static final EntityDataAccessor<Holder<WolfSoundVariant>> DATA_SOUND_VARIANT_ID = SynchedEntityData.<Holder<WolfSoundVariant>>defineId(Wolf.class, EntityDataSerializers.WOLF_SOUND_VARIANT);
    public static final TargetingConditions.Selector PREY_SELECTOR = (livingentity, serverlevel) -> {
        EntityType<?> entitytype = livingentity.getType();

        return entitytype == EntityType.SHEEP || entitytype == EntityType.RABBIT || entitytype == EntityType.FOX;
    };
    private static final float START_HEALTH = 8.0F;
    private static final float TAME_HEALTH = 40.0F;
    private static final float ARMOR_REPAIR_UNIT = 0.125F;
    public static final float DEFAULT_TAIL_ANGLE = ((float) Math.PI / 5F);
    private static final DyeColor DEFAULT_COLLAR_COLOR = DyeColor.RED;
    private float interestedAngle;
    private float interestedAngleO;
    public boolean isWet;
    private boolean isShaking;
    private float shakeAnim;
    private float shakeAnimO;
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private @Nullable EntityReference<LivingEntity> persistentAngerTarget;

    public Wolf(EntityType<? extends Wolf> type, Level level) {
        super(type, level);
        this.setTame(false, false);
        this.setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
        this.setPathfindingMalus(PathType.DANGER_POWDER_SNOW, -1.0F);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TamableAnimal.TamableAnimalPanicGoal(1.5D, DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(3, new Wolf.WolfAvoidEntityGoal(this, Llama.class, 24.0F, 1.5D, 1.5D));
        this.goalSelector.addGoal(4, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F));
        this.goalSelector.addGoal(7, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(9, new BegGoal(this, 8.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, (new HurtByTargetGoal(this, new Class[0])).setAlertOthers());
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(5, new NonTameRandomTargetGoal(this, Animal.class, false, Wolf.PREY_SELECTOR));
        this.targetSelector.addGoal(6, new NonTameRandomTargetGoal(this, Turtle.class, false, Turtle.BABY_ON_LAND_SELECTOR));
        this.targetSelector.addGoal(7, new NearestAttackableTargetGoal(this, AbstractSkeleton.class, false));
        this.targetSelector.addGoal(8, new ResetUniversalAngerTargetGoal(this, true));
    }

    public Identifier getTexture() {
        WolfVariant wolfvariant = (WolfVariant) this.getVariant().value();

        return this.isTame() ? wolfvariant.assetInfo().tame().texturePath() : (this.isAngry() ? wolfvariant.assetInfo().angry().texturePath() : wolfvariant.assetInfo().wild().texturePath());
    }

    public Holder<WolfVariant> getVariant() {
        return (Holder) this.entityData.get(Wolf.DATA_VARIANT_ID);
    }

    public void setVariant(Holder<WolfVariant> holder) {
        this.entityData.set(Wolf.DATA_VARIANT_ID, holder);
    }

    private Holder<WolfSoundVariant> getSoundVariant() {
        return (Holder) this.entityData.get(Wolf.DATA_SOUND_VARIANT_ID);
    }

    private void setSoundVariant(Holder<WolfSoundVariant> soundVariant) {
        this.entityData.set(Wolf.DATA_SOUND_VARIANT_ID, soundVariant);
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        return (T) (type == DataComponents.WOLF_VARIANT ? castComponentValue(type, this.getVariant()) : (type == DataComponents.WOLF_SOUND_VARIANT ? castComponentValue(type, this.getSoundVariant()) : (type == DataComponents.WOLF_COLLAR ? castComponentValue(type, this.getCollarColor()) : super.get(type))));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        this.applyImplicitComponentIfPresent(components, DataComponents.WOLF_VARIANT);
        this.applyImplicitComponentIfPresent(components, DataComponents.WOLF_SOUND_VARIANT);
        this.applyImplicitComponentIfPresent(components, DataComponents.WOLF_COLLAR);
        super.applyImplicitComponents(components);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.WOLF_VARIANT) {
            this.setVariant((Holder) castComponentValue(DataComponents.WOLF_VARIANT, value));
            return true;
        } else if (type == DataComponents.WOLF_SOUND_VARIANT) {
            this.setSoundVariant((Holder) castComponentValue(DataComponents.WOLF_SOUND_VARIANT, value));
            return true;
        } else if (type == DataComponents.WOLF_COLLAR) {
            this.setCollarColor((DyeColor) castComponentValue(DataComponents.WOLF_COLLAR, value));
            return true;
        } else {
            return super.applyImplicitComponent(type, value);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MOVEMENT_SPEED, (double) 0.3F).add(Attributes.MAX_HEALTH, 8.0D).add(Attributes.ATTACK_DAMAGE, 4.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        Registry<WolfSoundVariant> registry = this.registryAccess().lookupOrThrow(Registries.WOLF_SOUND_VARIANT);

        entityData.define(Wolf.DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), WolfVariants.DEFAULT));
        EntityDataAccessor entitydataaccessor = Wolf.DATA_SOUND_VARIANT_ID;
        Optional optional = registry.get(WolfSoundVariants.CLASSIC);

        Objects.requireNonNull(registry);
        entityData.define(entitydataaccessor, (Holder) optional.or(registry::getAny).orElseThrow());
        entityData.define(Wolf.DATA_INTERESTED_ID, false);
        entityData.define(Wolf.DATA_COLLAR_COLOR, Wolf.DEFAULT_COLLAR_COLOR.getId());
        entityData.define(Wolf.DATA_ANGER_END_TIME, -1L);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
        this.playSound(SoundEvents.WOLF_STEP, 0.15F, 1.0F);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("CollarColor", DyeColor.LEGACY_ID_CODEC, this.getCollarColor());
        VariantUtils.writeVariant(output, this.getVariant());
        this.addPersistentAngerSaveData(output);
        this.getSoundVariant().unwrapKey().ifPresent((resourcekey) -> {
            output.store("sound_variant", ResourceKey.codec(Registries.WOLF_SOUND_VARIANT), resourcekey);
        });
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        VariantUtils.readVariant(input, Registries.WOLF_VARIANT).ifPresent(this::setVariant);
        this.setCollarColor((DyeColor) input.read("CollarColor", DyeColor.LEGACY_ID_CODEC).orElse(Wolf.DEFAULT_COLLAR_COLOR));
        this.readPersistentAngerSaveData(this.level(), input);
        input.read("sound_variant", ResourceKey.codec(Registries.WOLF_SOUND_VARIANT)).flatMap((resourcekey) -> {
            return this.registryAccess().lookupOrThrow(Registries.WOLF_SOUND_VARIANT).get(resourcekey);
        }).ifPresent(this::setSoundVariant);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        if (groupData instanceof Wolf.WolfPackData wolf_wolfpackdata) {
            this.setVariant(wolf_wolfpackdata.type);
        } else {
            Optional<? extends Holder<WolfVariant>> optional = VariantUtils.selectVariantToSpawn(SpawnContext.create(level, this.blockPosition()), Registries.WOLF_VARIANT);

            if (optional.isPresent()) {
                this.setVariant((Holder) optional.get());
                groupData = new Wolf.WolfPackData((Holder) optional.get());
            }
        }

        this.setSoundVariant(WolfSoundVariants.pickRandomSoundVariant(this.registryAccess(), level.getRandom()));
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isAngry() ? (SoundEvent) ((WolfSoundVariant) this.getSoundVariant().value()).growlSound().value() : (this.random.nextInt(3) == 0 ? (this.isTame() && this.getHealth() < 20.0F ? (SoundEvent) ((WolfSoundVariant) this.getSoundVariant().value()).whineSound().value() : (SoundEvent) ((WolfSoundVariant) this.getSoundVariant().value()).pantSound().value()) : (SoundEvent) ((WolfSoundVariant) this.getSoundVariant().value()).ambientSound().value());
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.canArmorAbsorb(source) ? SoundEvents.WOLF_ARMOR_DAMAGE : (SoundEvent) ((WolfSoundVariant) this.getSoundVariant().value()).hurtSound().value();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return (SoundEvent) ((WolfSoundVariant) this.getSoundVariant().value()).deathSound().value();
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide() && this.isWet && !this.isShaking && !this.isPathFinding() && this.onGround()) {
            this.isShaking = true;
            this.shakeAnim = 0.0F;
            this.shakeAnimO = 0.0F;
            this.level().broadcastEntityEvent(this, (byte) 8);
        }

        if (!this.level().isClientSide()) {
            this.updatePersistentAnger((ServerLevel) this.level(), true);
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.isAlive()) {
            this.interestedAngleO = this.interestedAngle;
            if (this.isInterested()) {
                this.interestedAngle += (1.0F - this.interestedAngle) * 0.4F;
            } else {
                this.interestedAngle += (0.0F - this.interestedAngle) * 0.4F;
            }

            if (this.isInWaterOrRain()) {
                this.isWet = true;
                if (this.isShaking && !this.level().isClientSide()) {
                    this.level().broadcastEntityEvent(this, (byte) 56);
                    this.cancelShake();
                }
            } else if ((this.isWet || this.isShaking) && this.isShaking) {
                if (this.shakeAnim == 0.0F) {
                    this.playSound(SoundEvents.WOLF_SHAKE, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                    this.gameEvent(GameEvent.ENTITY_ACTION);
                }

                this.shakeAnimO = this.shakeAnim;
                this.shakeAnim += 0.05F;
                if (this.shakeAnimO >= 2.0F) {
                    this.isWet = false;
                    this.isShaking = false;
                    this.shakeAnimO = 0.0F;
                    this.shakeAnim = 0.0F;
                }

                if (this.shakeAnim > 0.4F) {
                    float f = (float) this.getY();
                    int i = (int) (Mth.sin((double) ((this.shakeAnim - 0.4F) * (float) Math.PI)) * 7.0F);
                    Vec3 vec3 = this.getDeltaMovement();

                    for (int j = 0; j < i; ++j) {
                        float f1 = (this.random.nextFloat() * 2.0F - 1.0F) * this.getBbWidth() * 0.5F;
                        float f2 = (this.random.nextFloat() * 2.0F - 1.0F) * this.getBbWidth() * 0.5F;

                        this.level().addParticle(ParticleTypes.SPLASH, this.getX() + (double) f1, (double) (f + 0.8F), this.getZ() + (double) f2, vec3.x, vec3.y, vec3.z);
                    }
                }
            }

        }
    }

    private void cancelShake() {
        this.isShaking = false;
        this.shakeAnim = 0.0F;
        this.shakeAnimO = 0.0F;
    }

    @Override
    public void die(DamageSource source) {
        this.isWet = false;
        this.isShaking = false;
        this.shakeAnimO = 0.0F;
        this.shakeAnim = 0.0F;
        super.die(source);
    }

    public float getWetShade(float a) {
        return !this.isWet ? 1.0F : Math.min(0.75F + Mth.lerp(a, this.shakeAnimO, this.shakeAnim) / 2.0F * 0.25F, 1.0F);
    }

    public float getShakeAnim(float a) {
        return Mth.lerp(a, this.shakeAnimO, this.shakeAnim);
    }

    public float getHeadRollAngle(float a) {
        return Mth.lerp(a, this.interestedAngleO, this.interestedAngle) * 0.15F * (float) Math.PI;
    }

    @Override
    public int getMaxHeadXRot() {
        return this.isInSittingPose() ? 20 : super.getMaxHeadXRot();
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

    @Override
    protected void actuallyHurt(ServerLevel level, DamageSource source, float damage) {
        if (!this.canArmorAbsorb(source)) {
            super.actuallyHurt(level, source, damage);
        } else {
            ItemStack itemstack = this.getBodyArmorItem();
            int i = itemstack.getDamageValue();
            int j = itemstack.getMaxDamage();

            itemstack.hurtAndBreak(Mth.ceil(damage), this, EquipmentSlot.BODY);
            if (Crackiness.WOLF_ARMOR.byDamage(i, j) != Crackiness.WOLF_ARMOR.byDamage(this.getBodyArmorItem())) {
                this.playSound(SoundEvents.WOLF_ARMOR_CRACK);
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.ARMADILLO_SCUTE.getDefaultInstance()), this.getX(), this.getY() + 1.0D, this.getZ(), 20, 0.2D, 0.1D, 0.2D, 0.1D);
            }

        }
    }

    private boolean canArmorAbsorb(DamageSource source) {
        return this.getBodyArmorItem().is(Items.WOLF_ARMOR) && !source.is(DamageTypeTags.BYPASSES_WOLF_ARMOR);
    }

    @Override
    protected void applyTamingSideEffects() {
        if (this.isTame()) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0D);
            this.setHealth(40.0F);
        } else {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(8.0D);
        }

    }

    @Override
    protected void hurtArmor(DamageSource damageSource, float damage) {
        this.doHurtEquipment(damageSource, damage, new EquipmentSlot[]{EquipmentSlot.BODY});
    }

    @Override
    protected boolean canShearEquipment(Player player) {
        return this.isOwnedBy(player);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        Item item = itemstack.getItem();

        if (this.isTame()) {
            if (this.isFood(itemstack) && this.getHealth() < this.getMaxHealth()) {
                this.usePlayerItem(player, hand, itemstack);
                FoodProperties foodproperties = (FoodProperties) itemstack.get(DataComponents.FOOD);
                float f = foodproperties != null ? (float) foodproperties.nutrition() : 1.0F;

                this.heal(2.0F * f);
                return InteractionResult.SUCCESS;
            } else {
                if (item instanceof DyeItem) {
                    DyeItem dyeitem = (DyeItem) item;

                    if (this.isOwnedBy(player)) {
                        DyeColor dyecolor = dyeitem.getDyeColor();

                        if (dyecolor != this.getCollarColor()) {
                            this.setCollarColor(dyecolor);
                            itemstack.consume(1, player);
                            return InteractionResult.SUCCESS;
                        }

                        return super.mobInteract(player, hand);
                    }
                }

                if (this.isEquippableInSlot(itemstack, EquipmentSlot.BODY) && !this.isWearingBodyArmor() && this.isOwnedBy(player) && !this.isBaby()) {
                    this.setBodyArmorItem(itemstack.copyWithCount(1));
                    itemstack.consume(1, player);
                    return InteractionResult.SUCCESS;
                } else if (this.isInSittingPose() && this.isWearingBodyArmor() && this.isOwnedBy(player) && this.getBodyArmorItem().isDamaged() && this.getBodyArmorItem().isValidRepairItem(itemstack)) {
                    itemstack.shrink(1);
                    this.playSound(SoundEvents.WOLF_ARMOR_REPAIR);
                    ItemStack itemstack1 = this.getBodyArmorItem();
                    int i = (int) ((float) itemstack1.getMaxDamage() * 0.125F);

                    itemstack1.setDamageValue(Math.max(0, itemstack1.getDamageValue() - i));
                    return InteractionResult.SUCCESS;
                } else {
                    InteractionResult interactionresult = super.mobInteract(player, hand);

                    if (!interactionresult.consumesAction() && this.isOwnedBy(player)) {
                        this.setOrderedToSit(!this.isOrderedToSit());
                        this.jumping = false;
                        this.navigation.stop();
                        this.setTarget((LivingEntity) null);
                        return InteractionResult.SUCCESS.withoutItem();
                    } else {
                        return interactionresult;
                    }
                }
            }
        } else if (!this.level().isClientSide() && itemstack.is(Items.BONE) && !this.isAngry()) {
            itemstack.consume(1, player);
            this.tryToTame(player);
            return InteractionResult.SUCCESS_SERVER;
        } else {
            return super.mobInteract(player, hand);
        }
    }

    private void tryToTame(Player player) {
        if (this.random.nextInt(3) == 0) {
            this.tame(player);
            this.navigation.stop();
            this.setTarget((LivingEntity) null);
            this.setOrderedToSit(true);
            this.level().broadcastEntityEvent(this, (byte) 7);
        } else {
            this.level().broadcastEntityEvent(this, (byte) 6);
        }

    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 8) {
            this.isShaking = true;
            this.shakeAnim = 0.0F;
            this.shakeAnimO = 0.0F;
        } else if (id == 56) {
            this.cancelShake();
        } else {
            super.handleEntityEvent(id);
        }

    }

    public float getTailAngle() {
        if (this.isAngry()) {
            return 1.5393804F;
        } else if (this.isTame()) {
            float f = this.getMaxHealth();
            float f1 = (f - this.getHealth()) / f;

            return (0.55F - f1 * 0.4F) * (float) Math.PI;
        } else {
            return ((float) Math.PI / 5F);
        }
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return itemStack.is(ItemTags.WOLF_FOOD);
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 8;
    }

    @Override
    public long getPersistentAngerEndTime() {
        return (Long) this.entityData.get(Wolf.DATA_ANGER_END_TIME);
    }

    @Override
    public void setPersistentAngerEndTime(long endTime) {
        this.entityData.set(Wolf.DATA_ANGER_END_TIME, endTime);
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setTimeToRemainAngry((long) Wolf.PERSISTENT_ANGER_TIME.sample(this.random));
    }

    @Override
    public @Nullable EntityReference<LivingEntity> getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable EntityReference<LivingEntity> persistentAngerTarget) {
        this.persistentAngerTarget = persistentAngerTarget;
    }

    public DyeColor getCollarColor() {
        return DyeColor.byId((Integer) this.entityData.get(Wolf.DATA_COLLAR_COLOR));
    }

    public void setCollarColor(DyeColor color) {
        this.entityData.set(Wolf.DATA_COLLAR_COLOR, color.getId());
    }

    @Override
    public @Nullable Wolf getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Wolf wolf = EntityType.WOLF.create(level, EntitySpawnReason.BREEDING);

        if (wolf != null && partner instanceof Wolf wolf1) {
            if (this.random.nextBoolean()) {
                wolf.setVariant(this.getVariant());
            } else {
                wolf.setVariant(wolf1.getVariant());
            }

            if (this.isTame()) {
                wolf.setOwnerReference(this.getOwnerReference());
                wolf.setTame(true, true);
                DyeColor dyecolor = this.getCollarColor();
                DyeColor dyecolor1 = wolf1.getCollarColor();

                wolf.setCollarColor(DyeColor.getMixedColor(level, dyecolor, dyecolor1));
            }

            wolf.setSoundVariant(WolfSoundVariants.pickRandomSoundVariant(this.registryAccess(), this.random));
        }

        return wolf;
    }

    public void setIsInterested(boolean value) {
        this.entityData.set(Wolf.DATA_INTERESTED_ID, value);
    }

    @Override
    public boolean canMate(Animal partner) {
        if (partner == this) {
            return false;
        } else if (!this.isTame()) {
            return false;
        } else if (!(partner instanceof Wolf)) {
            return false;
        } else {
            Wolf wolf = (Wolf) partner;

            return !wolf.isTame() ? false : (wolf.isInSittingPose() ? false : this.isInLove() && wolf.isInLove());
        }
    }

    public boolean isInterested() {
        return (Boolean) this.entityData.get(Wolf.DATA_INTERESTED_ID);
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (!(target instanceof Creeper) && !(target instanceof Ghast) && !(target instanceof ArmorStand)) {
            if (target instanceof Wolf) {
                Wolf wolf = (Wolf) target;

                return !wolf.isTame() || wolf.getOwner() != owner;
            } else {
                if (target instanceof Player) {
                    Player player = (Player) target;

                    if (owner instanceof Player) {
                        Player player1 = (Player) owner;

                        if (!player1.canHarmPlayer(player)) {
                            return false;
                        }
                    }
                }

                if (target instanceof AbstractHorse) {
                    AbstractHorse abstracthorse = (AbstractHorse) target;

                    if (abstracthorse.isTamed()) {
                        return false;
                    }
                }

                boolean flag;

                if (target instanceof TamableAnimal) {
                    TamableAnimal tamableanimal = (TamableAnimal) target;

                    if (tamableanimal.isTame()) {
                        flag = false;
                        return flag;
                    }
                }

                flag = true;
                return flag;
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean canBeLeashed() {
        return !this.isAngry();
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0D, (double) (0.6F * this.getEyeHeight()), (double) (this.getBbWidth() * 0.4F));
    }

    public static boolean checkWolfSpawnRules(EntityType<Wolf> type, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    public static class WolfPackData extends AgeableMob.AgeableMobGroupData {

        public final Holder<WolfVariant> type;

        public WolfPackData(Holder<WolfVariant> type) {
            super(false);
            this.type = type;
        }
    }

    private class WolfAvoidEntityGoal<T extends LivingEntity> extends AvoidEntityGoal<T> {

        private final Wolf wolf;

        public WolfAvoidEntityGoal(Wolf wolf, Class<T> avoidClass, float maxDist, double walkSpeedModifier, double sprintSpeedModifier) {
            super(wolf, avoidClass, maxDist, walkSpeedModifier, sprintSpeedModifier);
            this.wolf = wolf;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && this.toAvoid instanceof Llama ? !this.wolf.isTame() && this.avoidLlama((Llama) this.toAvoid) : false;
        }

        private boolean avoidLlama(Llama llama) {
            return llama.getStrength() >= Wolf.this.random.nextInt(5);
        }

        @Override
        public void start() {
            Wolf.this.setTarget((LivingEntity) null);
            super.start();
        }

        @Override
        public void tick() {
            Wolf.this.setTarget((LivingEntity) null);
            super.tick();
        }
    }
}

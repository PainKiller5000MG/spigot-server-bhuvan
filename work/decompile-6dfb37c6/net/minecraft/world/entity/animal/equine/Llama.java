package net.minecraft.world.entity.animal.equine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.PrimitiveCodec;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LlamaFollowCaravanGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Llama extends AbstractChestedHorse implements RangedAttackMob {

    private static final int MAX_STRENGTH = 5;
    private static final EntityDataAccessor<Integer> DATA_STRENGTH_ID = SynchedEntityData.<Integer>defineId(Llama.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT_ID = SynchedEntityData.<Integer>defineId(Llama.class, EntityDataSerializers.INT);
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.LLAMA.getDimensions().withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, EntityType.LLAMA.getHeight() - 0.8125F, -0.3F)).scale(0.5F);
    private boolean didSpit;
    private @Nullable Llama caravanHead;
    private @Nullable Llama caravanTail;

    public Llama(EntityType<? extends Llama> type, Level level) {
        super(type, level);
        this.getNavigation().setRequiredPathLength(40.0F);
    }

    public boolean isTraderLlama() {
        return false;
    }

    private void setStrength(int strength) {
        this.entityData.set(Llama.DATA_STRENGTH_ID, Math.max(1, Math.min(5, strength)));
    }

    private void setRandomStrength(RandomSource random) {
        int i = random.nextFloat() < 0.04F ? 5 : 3;

        this.setStrength(1 + random.nextInt(i));
    }

    public int getStrength() {
        return (Integer) this.entityData.get(Llama.DATA_STRENGTH_ID);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("Variant", Llama.Variant.LEGACY_CODEC, this.getVariant());
        output.putInt("Strength", this.getStrength());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setStrength(input.getIntOr("Strength", 0));
        super.readAdditionalSaveData(input);
        this.setVariant((Llama.Variant) input.read("Variant", Llama.Variant.LEGACY_CODEC).orElse(Llama.Variant.DEFAULT));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new RunAroundLikeCrazyGoal(this, 1.2D));
        this.goalSelector.addGoal(2, new LlamaFollowCaravanGoal(this, (double) 2.1F));
        this.goalSelector.addGoal(3, new RangedAttackGoal(this, 1.25D, 40, 20.0F));
        this.goalSelector.addGoal(3, new PanicGoal(this, 1.2D));
        this.goalSelector.addGoal(4, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new TemptGoal(this, 1.25D, (itemstack) -> {
            return itemstack.is(ItemTags.LLAMA_TEMPT_ITEMS);
        }, false));
        this.goalSelector.addGoal(6, new FollowParentGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new Llama.LlamaHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new Llama.LlamaAttackWolfGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseChestedHorseAttributes();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Llama.DATA_STRENGTH_ID, 0);
        entityData.define(Llama.DATA_VARIANT_ID, 0);
    }

    public Llama.Variant getVariant() {
        return Llama.Variant.byId((Integer) this.entityData.get(Llama.DATA_VARIANT_ID));
    }

    public void setVariant(Llama.Variant llama_variant) {
        this.entityData.set(Llama.DATA_VARIANT_ID, llama_variant.id);
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        return (T) (type == DataComponents.LLAMA_VARIANT ? castComponentValue(type, this.getVariant()) : super.get(type));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        this.applyImplicitComponentIfPresent(components, DataComponents.LLAMA_VARIANT);
        super.applyImplicitComponents(components);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.LLAMA_VARIANT) {
            this.setVariant((Llama.Variant) castComponentValue(DataComponents.LLAMA_VARIANT, value));
            return true;
        } else {
            return super.applyImplicitComponent(type, value);
        }
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return itemStack.is(ItemTags.LLAMA_FOOD);
    }

    @Override
    protected boolean handleEating(Player player, ItemStack itemStack) {
        int i = 0;
        int j = 0;
        float f = 0.0F;
        boolean flag = false;

        if (itemStack.is(Items.WHEAT)) {
            i = 10;
            j = 3;
            f = 2.0F;
        } else if (itemStack.is(Blocks.HAY_BLOCK.asItem())) {
            i = 90;
            j = 6;
            f = 10.0F;
            if (this.isTamed() && this.getAge() == 0 && this.canFallInLove()) {
                flag = true;
                this.setInLove(player);
            }
        }

        if (this.getHealth() < this.getMaxHealth() && f > 0.0F) {
            this.heal(f);
            flag = true;
        }

        if (this.isBaby() && i > 0) {
            this.level().addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), 0.0D, 0.0D, 0.0D);
            if (!this.level().isClientSide()) {
                this.ageUp(i);
                flag = true;
            }
        }

        if (j > 0 && (flag || !this.isTamed()) && this.getTemper() < this.getMaxTemper() && !this.level().isClientSide()) {
            this.modifyTemper(j);
            flag = true;
        }

        if (flag && !this.isSilent()) {
            SoundEvent soundevent = this.getEatingSound();

            if (soundevent != null) {
                this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), this.getEatingSound(), this.getSoundSource(), 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
            }
        }

        return flag;
    }

    @Override
    public boolean isImmobile() {
        return this.isDeadOrDying() || this.isEating();
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        RandomSource randomsource = level.getRandom();

        this.setRandomStrength(randomsource);
        Llama.Variant llama_variant;

        if (groupData instanceof Llama.LlamaGroupData) {
            llama_variant = ((Llama.LlamaGroupData) groupData).variant;
        } else {
            llama_variant = (Llama.Variant) Util.getRandom(Llama.Variant.values(), randomsource);
            groupData = new Llama.LlamaGroupData(llama_variant);
        }

        this.setVariant(llama_variant);
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    @Override
    protected boolean canPerformRearing() {
        return false;
    }

    @Override
    protected SoundEvent getAngrySound() {
        return SoundEvents.LLAMA_ANGRY;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.LLAMA_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.LLAMA_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.LLAMA_DEATH;
    }

    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.LLAMA_EAT;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
        this.playSound(SoundEvents.LLAMA_STEP, 0.15F, 1.0F);
    }

    @Override
    protected void playChestEquipsSound() {
        this.playSound(SoundEvents.LLAMA_CHEST, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
    }

    @Override
    public int getInventoryColumns() {
        return this.hasChest() ? this.getStrength() : 0;
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return true;
    }

    @Override
    public int getMaxTemper() {
        return 30;
    }

    @Override
    public boolean canMate(Animal partner) {
        return partner != this && partner instanceof Llama && this.canParent() && ((Llama) partner).canParent();
    }

    @Override
    public @Nullable Llama getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Llama llama = this.makeNewLlama();

        if (llama != null) {
            this.setOffspringAttributes(partner, llama);
            Llama llama1 = (Llama) partner;
            int i = this.random.nextInt(Math.max(this.getStrength(), llama1.getStrength())) + 1;

            if (this.random.nextFloat() < 0.03F) {
                ++i;
            }

            llama.setStrength(i);
            llama.setVariant(this.random.nextBoolean() ? this.getVariant() : llama1.getVariant());
        }

        return llama;
    }

    protected @Nullable Llama makeNewLlama() {
        return EntityType.LLAMA.create(this.level(), EntitySpawnReason.BREEDING);
    }

    private void spit(LivingEntity target) {
        LlamaSpit llamaspit = new LlamaSpit(this.level(), this);
        double d0 = target.getX() - this.getX();
        double d1 = target.getY(0.3333333333333333D) - llamaspit.getY();
        double d2 = target.getZ() - this.getZ();
        double d3 = Math.sqrt(d0 * d0 + d2 * d2) * (double) 0.2F;
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            Projectile.spawnProjectileUsingShoot(llamaspit, serverlevel, ItemStack.EMPTY, d0, d1 + d3, d2, 1.5F, 10.0F);
        }

        if (!this.isSilent()) {
            this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEvents.LLAMA_SPIT, this.getSoundSource(), 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
        }

        this.didSpit = true;
    }

    private void setDidSpit(boolean b) {
        this.didSpit = b;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        int i = this.calculateFallDamage(fallDistance, damageModifier);

        if (i <= 0) {
            return false;
        } else {
            if (fallDistance >= 6.0D) {
                this.hurt(damageSource, (float) i);
                this.propagateFallToPassengers(fallDistance, damageModifier, damageSource);
            }

            this.playBlockFallSound();
            return true;
        }
    }

    public void leaveCaravan() {
        if (this.caravanHead != null) {
            this.caravanHead.caravanTail = null;
        }

        this.caravanHead = null;
    }

    public void joinCaravan(Llama tail) {
        this.caravanHead = tail;
        this.caravanHead.caravanTail = this;
    }

    public boolean hasCaravanTail() {
        return this.caravanTail != null;
    }

    public boolean inCaravan() {
        return this.caravanHead != null;
    }

    public @Nullable Llama getCaravanHead() {
        return this.caravanHead;
    }

    @Override
    protected double followLeashSpeed() {
        return 2.0D;
    }

    @Override
    public boolean supportQuadLeash() {
        return false;
    }

    @Override
    protected void followMommy(ServerLevel level) {
        if (!this.inCaravan() && this.isBaby()) {
            super.followMommy(level);
        }

    }

    @Override
    public boolean canEatGrass() {
        return false;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        this.spit(target);
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0D, 0.75D * (double) this.getEyeHeight(), (double) this.getBbWidth() * 0.5D);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? Llama.BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return getDefaultPassengerAttachmentPoint(this, passenger, dimensions.attachments());
    }

    public static enum Variant implements StringRepresentable {

        CREAMY(0, "creamy"), WHITE(1, "white"), BROWN(2, "brown"), GRAY(3, "gray");

        public static final Llama.Variant DEFAULT = Llama.Variant.CREAMY;
        private static final IntFunction<Llama.Variant> BY_ID = ByIdMap.<Llama.Variant>continuous(Llama.Variant::getId, values(), ByIdMap.OutOfBoundsStrategy.CLAMP);
        public static final Codec<Llama.Variant> CODEC = StringRepresentable.<Llama.Variant>fromEnum(Llama.Variant::values);
        /** @deprecated */
        @Deprecated
        public static final Codec<Llama.Variant> LEGACY_CODEC;
        public static final StreamCodec<ByteBuf, Llama.Variant> STREAM_CODEC;
        private final int id;
        private final String name;

        private Variant(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return this.id;
        }

        public static Llama.Variant byId(int id) {
            return (Llama.Variant) Llama.Variant.BY_ID.apply(id);
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        static {
            PrimitiveCodec primitivecodec = Codec.INT;
            IntFunction intfunction = Llama.Variant.BY_ID;

            Objects.requireNonNull(intfunction);
            LEGACY_CODEC = primitivecodec.xmap(intfunction::apply, Llama.Variant::getId);
            STREAM_CODEC = ByteBufCodecs.idMapper(Llama.Variant.BY_ID, Llama.Variant::getId);
        }
    }

    private static class LlamaGroupData extends AgeableMob.AgeableMobGroupData {

        public final Llama.Variant variant;

        private LlamaGroupData(Llama.Variant llama_variant) {
            super(true);
            this.variant = llama_variant;
        }
    }

    private static class LlamaHurtByTargetGoal extends HurtByTargetGoal {

        public LlamaHurtByTargetGoal(Llama llama) {
            super(llama);
        }

        @Override
        public boolean canContinueToUse() {
            Mob mob = this.mob;

            if (mob instanceof Llama llama) {
                if (llama.didSpit) {
                    llama.setDidSpit(false);
                    return false;
                }
            }

            return super.canContinueToUse();
        }
    }

    private static class LlamaAttackWolfGoal extends NearestAttackableTargetGoal<Wolf> {

        public LlamaAttackWolfGoal(Llama llama) {
            super(llama, Wolf.class, 16, false, true, (livingentity, serverlevel) -> {
                return !((Wolf) livingentity).isTame();
            });
        }

        @Override
        protected double getFollowDistance() {
            return super.getFollowDistance() * 0.25D;
        }
    }
}

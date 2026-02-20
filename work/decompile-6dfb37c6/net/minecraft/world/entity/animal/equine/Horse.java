package net.minecraft.world.entity.animal.equine;

import java.util.Objects;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class Horse extends AbstractHorse {

    private static final EntityDataAccessor<Integer> DATA_ID_TYPE_VARIANT = SynchedEntityData.<Integer>defineId(Horse.class, EntityDataSerializers.INT);
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.HORSE.getDimensions().withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, EntityType.HORSE.getHeight() + 0.125F, 0.0F)).scale(0.5F);
    private static final int DEFAULT_VARIANT = 0;

    public Horse(EntityType<? extends Horse> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.DANGER_OTHER, -1.0F);
        this.setPathfindingMalus(PathType.DAMAGE_OTHER, -1.0F);
    }

    @Override
    protected void randomizeAttributes(RandomSource random) {
        AttributeInstance attributeinstance = this.getAttribute(Attributes.MAX_HEALTH);

        Objects.requireNonNull(random);
        attributeinstance.setBaseValue((double) generateMaxHealth(random::nextInt));
        attributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
        Objects.requireNonNull(random);
        attributeinstance.setBaseValue(generateSpeed(random::nextDouble));
        attributeinstance = this.getAttribute(Attributes.JUMP_STRENGTH);
        Objects.requireNonNull(random);
        attributeinstance.setBaseValue(generateJumpStrength(random::nextDouble));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Horse.DATA_ID_TYPE_VARIANT, 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Variant", this.getTypeVariant());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setTypeVariant(input.getIntOr("Variant", 0));
    }

    private void setTypeVariant(int i) {
        this.entityData.set(Horse.DATA_ID_TYPE_VARIANT, i);
    }

    private int getTypeVariant() {
        return (Integer) this.entityData.get(Horse.DATA_ID_TYPE_VARIANT);
    }

    public void setVariantAndMarkings(Variant variant, Markings markings) {
        this.setTypeVariant(variant.getId() & 255 | markings.getId() << 8 & '\uff00');
    }

    public Variant getVariant() {
        return Variant.byId(this.getTypeVariant() & 255);
    }

    private void setVariant(Variant variant) {
        this.setTypeVariant(variant.getId() & 255 | this.getTypeVariant() & -256);
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        return (T) (type == DataComponents.HORSE_VARIANT ? castComponentValue(type, this.getVariant()) : super.get(type));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        this.applyImplicitComponentIfPresent(components, DataComponents.HORSE_VARIANT);
        super.applyImplicitComponents(components);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.HORSE_VARIANT) {
            this.setVariant((Variant) castComponentValue(DataComponents.HORSE_VARIANT, value));
            return true;
        } else {
            return super.applyImplicitComponent(type, value);
        }
    }

    public Markings getMarkings() {
        return Markings.byId((this.getTypeVariant() & '\uff00') >> 8);
    }

    @Override
    protected void playGallopSound(SoundType soundType) {
        super.playGallopSound(soundType);
        if (this.random.nextInt(10) == 0) {
            this.playSound(SoundEvents.HORSE_BREATHE, soundType.getVolume() * 0.6F, soundType.getPitch());
        }

    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.HORSE_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.HORSE_DEATH;
    }

    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.HORSE_EAT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.HORSE_HURT;
    }

    @Override
    protected SoundEvent getAngrySound() {
        return SoundEvents.HORSE_ANGRY;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        boolean flag = !this.isBaby() && this.isTamed() && player.isSecondaryUseActive();

        if (!this.isVehicle() && !flag) {
            ItemStack itemstack = player.getItemInHand(hand);

            if (!itemstack.isEmpty()) {
                if (this.isFood(itemstack)) {
                    return this.fedFood(player, itemstack);
                }

                if (!this.isTamed()) {
                    this.makeMad();
                    return InteractionResult.SUCCESS;
                }
            }

            return super.mobInteract(player, hand);
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    public boolean canMate(Animal partner) {
        return partner == this ? false : (!(partner instanceof Donkey) && !(partner instanceof Horse) ? false : this.canParent() && ((AbstractHorse) partner).canParent());
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        if (partner instanceof Donkey) {
            Mule mule = EntityType.MULE.create(level, EntitySpawnReason.BREEDING);

            if (mule != null) {
                this.setOffspringAttributes(partner, mule);
            }

            return mule;
        } else {
            Horse horse = (Horse) partner;
            Horse horse1 = EntityType.HORSE.create(level, EntitySpawnReason.BREEDING);

            if (horse1 != null) {
                int i = this.random.nextInt(9);
                Variant variant;

                if (i < 4) {
                    variant = this.getVariant();
                } else if (i < 8) {
                    variant = horse.getVariant();
                } else {
                    variant = (Variant) Util.getRandom(Variant.values(), this.random);
                }

                int j = this.random.nextInt(5);
                Markings markings;

                if (j < 2) {
                    markings = this.getMarkings();
                } else if (j < 4) {
                    markings = horse.getMarkings();
                } else {
                    markings = (Markings) Util.getRandom(Markings.values(), this.random);
                }

                horse1.setVariantAndMarkings(variant, markings);
                this.setOffspringAttributes(partner, horse1);
            }

            return horse1;
        }
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return true;
    }

    @Override
    protected void hurtArmor(DamageSource damageSource, float damage) {
        this.doHurtEquipment(damageSource, damage, new EquipmentSlot[]{EquipmentSlot.BODY});
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        RandomSource randomsource = level.getRandom();
        Variant variant;

        if (groupData instanceof Horse.HorseGroupData) {
            variant = ((Horse.HorseGroupData) groupData).variant;
        } else {
            variant = (Variant) Util.getRandom(Variant.values(), randomsource);
            groupData = new Horse.HorseGroupData(variant);
        }

        this.setVariantAndMarkings(variant, (Markings) Util.getRandom(Markings.values(), randomsource));
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? Horse.BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    public static class HorseGroupData extends AgeableMob.AgeableMobGroupData {

        public final Variant variant;

        public HorseGroupData(Variant variant) {
            super(true);
            this.variant = variant;
        }
    }
}

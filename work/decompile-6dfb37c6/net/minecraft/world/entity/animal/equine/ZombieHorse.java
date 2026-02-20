package net.minecraft.world.entity.animal.equine;

import java.util.Objects;
import java.util.function.DoubleSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ZombieHorse extends AbstractHorse {

    private static final float SPEED_FACTOR = 42.16F;
    private static final double BASE_JUMP_STRENGTH = 0.5D;
    private static final double PER_RANDOM_JUMP_STRENGTH = 0.06666666666666667D;
    private static final double BASE_SPEED = 9.0D;
    private static final double PER_RANDOM_SPEED = 1.0D;
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.ZOMBIE_HORSE.getDimensions().withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, EntityType.ZOMBIE_HORSE.getHeight() - 0.03125F, 0.0F)).scale(0.5F);

    public ZombieHorse(EntityType<? extends ZombieHorse> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.DANGER_OTHER, -1.0F);
        this.setPathfindingMalus(PathType.DAMAGE_OTHER, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseHorseAttributes().add(Attributes.MAX_HEALTH, 25.0D);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        this.setPersistenceRequired();
        return super.interact(player, hand);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return true;
    }

    @Override
    public boolean isMobControlled() {
        return this.getFirstPassenger() instanceof Mob;
    }

    @Override
    protected void randomizeAttributes(RandomSource random) {
        AttributeInstance attributeinstance = this.getAttribute(Attributes.JUMP_STRENGTH);

        Objects.requireNonNull(random);
        attributeinstance.setBaseValue(generateZombieHorseJumpStrength(random::nextDouble));
        attributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
        Objects.requireNonNull(random);
        attributeinstance.setBaseValue(generateZombieHorseSpeed(random::nextDouble));
    }

    private static double generateZombieHorseJumpStrength(DoubleSupplier probabilityProvider) {
        return 0.5D + probabilityProvider.getAsDouble() * 0.06666666666666667D + probabilityProvider.getAsDouble() * 0.06666666666666667D + probabilityProvider.getAsDouble() * 0.06666666666666667D;
    }

    private static double generateZombieHorseSpeed(DoubleSupplier probabilityProvider) {
        return (9.0D + probabilityProvider.getAsDouble() * 1.0D + probabilityProvider.getAsDouble() * 1.0D + probabilityProvider.getAsDouble() * 1.0D) / (double) 42.16F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_HORSE_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_HORSE_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_HORSE_HURT;
    }

    @Override
    protected SoundEvent getAngrySound() {
        return SoundEvents.ZOMBIE_HORSE_ANGRY;
    }

    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.ZOMBIE_HORSE_EAT;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean canFallInLove() {
        return false;
    }

    @Override
    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.25D, (itemstack) -> {
            return itemstack.is(ItemTags.ZOMBIE_HORSE_FOOD);
        }, false));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        if (spawnReason == EntitySpawnReason.NATURAL) {
            Zombie zombie = EntityType.ZOMBIE.create(this.level(), EntitySpawnReason.JOCKEY);

            if (zombie != null) {
                zombie.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                zombie.finalizeSpawn(level, difficulty, spawnReason, (SpawnGroupData) null);
                zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
                zombie.startRiding(this, false, false);
            }
        }

        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
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
    public boolean canUseSlot(EquipmentSlot slot) {
        return true;
    }

    @Override
    public boolean canBeLeashed() {
        return this.isTamed() || !this.isMobControlled();
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return itemStack.is(ItemTags.ZOMBIE_HORSE_FOOD);
    }

    @Override
    protected EquipmentSlot sunProtectionSlot() {
        return EquipmentSlot.BODY;
    }

    @Override
    public Vec3[] getQuadLeashOffsets() {
        return Leashable.createQuadLeashOffsets(this, 0.04D, 0.41D, 0.18D, 0.73D);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? ZombieHorse.BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    @Override
    public float chargeSpeedModifier() {
        return 1.4F;
    }
}

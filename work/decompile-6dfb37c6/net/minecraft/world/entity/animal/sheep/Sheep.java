package net.minecraft.world.entity.animal.sheep;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

public class Sheep extends Animal implements Shearable {

    private static final int EAT_ANIMATION_TICKS = 40;
    private static final EntityDataAccessor<Byte> DATA_WOOL_ID = SynchedEntityData.<Byte>defineId(Sheep.class, EntityDataSerializers.BYTE);
    private static final DyeColor DEFAULT_COLOR = DyeColor.WHITE;
    private static final boolean DEFAULT_SHEARED = false;
    private int eatAnimationTick;
    private EatBlockGoal eatBlockGoal;

    public Sheep(EntityType<? extends Sheep> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.eatBlockGoal = new EatBlockGoal(this);
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.1D, (itemstack) -> {
            return itemstack.is(ItemTags.SHEEP_FOOD);
        }, false));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1D));
        this.goalSelector.addGoal(5, this.eatBlockGoal);
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return itemStack.is(ItemTags.SHEEP_FOOD);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.eatAnimationTick = this.eatBlockGoal.getEatAnimationTick();
        super.customServerAiStep(level);
    }

    @Override
    public void aiStep() {
        if (this.level().isClientSide()) {
            this.eatAnimationTick = Math.max(0, this.eatAnimationTick - 1);
        }

        super.aiStep();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 8.0D).add(Attributes.MOVEMENT_SPEED, (double) 0.23F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Sheep.DATA_WOOL_ID, (byte) 0);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 10) {
            this.eatAnimationTick = 40;
        } else {
            super.handleEntityEvent(id);
        }

    }

    public float getHeadEatPositionScale(float a) {
        return this.eatAnimationTick <= 0 ? 0.0F : (this.eatAnimationTick >= 4 && this.eatAnimationTick <= 36 ? 1.0F : (this.eatAnimationTick < 4 ? ((float) this.eatAnimationTick - a) / 4.0F : -((float) (this.eatAnimationTick - 40) - a) / 4.0F));
    }

    public float getHeadEatAngleScale(float a) {
        if (this.eatAnimationTick > 4 && this.eatAnimationTick <= 36) {
            float f1 = ((float) (this.eatAnimationTick - 4) - a) / 32.0F;

            return ((float) Math.PI / 5F) + 0.21991149F * Mth.sin((double) (f1 * 28.7F));
        } else {
            return this.eatAnimationTick > 0 ? ((float) Math.PI / 5F) : this.getXRot(a) * ((float) Math.PI / 180F);
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.is(Items.SHEARS)) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                if (this.readyForShearing()) {
                    this.shear(serverlevel, SoundSource.PLAYERS, itemstack);
                    this.gameEvent(GameEvent.SHEAR, player);
                    itemstack.hurtAndBreak(1, player, hand.asEquipmentSlot());
                    return InteractionResult.SUCCESS_SERVER;
                }
            }

            return InteractionResult.CONSUME;
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    public void shear(ServerLevel level, SoundSource soundSource, ItemStack tool) {
        level.playSound((Entity) null, (Entity) this, SoundEvents.SHEEP_SHEAR, soundSource, 1.0F, 1.0F);
        this.dropFromShearingLootTable(level, BuiltInLootTables.SHEAR_SHEEP, tool, (serverlevel1, itemstack1) -> {
            for (int i = 0; i < itemstack1.getCount(); ++i) {
                ItemEntity itementity = this.spawnAtLocation(serverlevel1, itemstack1.copyWithCount(1), 1.0F);

                if (itementity != null) {
                    itementity.setDeltaMovement(itementity.getDeltaMovement().add((double) ((this.random.nextFloat() - this.random.nextFloat()) * 0.1F), (double) (this.random.nextFloat() * 0.05F), (double) ((this.random.nextFloat() - this.random.nextFloat()) * 0.1F)));
                }
            }

        });
        this.setSheared(true);
    }

    @Override
    public boolean readyForShearing() {
        return this.isAlive() && !this.isSheared() && !this.isBaby();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Sheared", this.isSheared());
        output.store("Color", DyeColor.LEGACY_ID_CODEC, this.getColor());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setSheared(input.getBooleanOr("Sheared", false));
        this.setColor((DyeColor) input.read("Color", DyeColor.LEGACY_ID_CODEC).orElse(Sheep.DEFAULT_COLOR));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SHEEP_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SHEEP_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SHEEP_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
        this.playSound(SoundEvents.SHEEP_STEP, 0.15F, 1.0F);
    }

    public DyeColor getColor() {
        return DyeColor.byId((Byte) this.entityData.get(Sheep.DATA_WOOL_ID) & 15);
    }

    public void setColor(DyeColor color) {
        byte b0 = (Byte) this.entityData.get(Sheep.DATA_WOOL_ID);

        this.entityData.set(Sheep.DATA_WOOL_ID, (byte) (b0 & 240 | color.getId() & 15));
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        return (T) (type == DataComponents.SHEEP_COLOR ? castComponentValue(type, this.getColor()) : super.get(type));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        this.applyImplicitComponentIfPresent(components, DataComponents.SHEEP_COLOR);
        super.applyImplicitComponents(components);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.SHEEP_COLOR) {
            this.setColor((DyeColor) castComponentValue(DataComponents.SHEEP_COLOR, value));
            return true;
        } else {
            return super.applyImplicitComponent(type, value);
        }
    }

    public boolean isSheared() {
        return ((Byte) this.entityData.get(Sheep.DATA_WOOL_ID) & 16) != 0;
    }

    public void setSheared(boolean value) {
        byte b0 = (Byte) this.entityData.get(Sheep.DATA_WOOL_ID);

        if (value) {
            this.entityData.set(Sheep.DATA_WOOL_ID, (byte) (b0 | 16));
        } else {
            this.entityData.set(Sheep.DATA_WOOL_ID, (byte) (b0 & -17));
        }

    }

    public static DyeColor getRandomSheepColor(ServerLevelAccessor level, BlockPos pos) {
        Holder<Biome> holder = level.getBiome(pos);

        return SheepColorSpawnRules.getSheepColor(holder, level.getRandom());
    }

    @Override
    public @Nullable Sheep getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Sheep sheep = EntityType.SHEEP.create(level, EntitySpawnReason.BREEDING);

        if (sheep != null) {
            DyeColor dyecolor = this.getColor();
            DyeColor dyecolor1 = ((Sheep) partner).getColor();

            sheep.setColor(DyeColor.getMixedColor(level, dyecolor, dyecolor1));
        }

        return sheep;
    }

    @Override
    public void ate() {
        super.ate();
        this.setSheared(false);
        if (this.isBaby()) {
            this.ageUp(60);
        }

    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        this.setColor(getRandomSheepColor(level, this.blockPosition()));
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }
}

package net.minecraft.world.entity.animal.fish;

import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class Salmon extends AbstractSchoolingFish {

    private static final String TAG_TYPE = "type";
    private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.<Integer>defineId(Salmon.class, EntityDataSerializers.INT);

    public Salmon(EntityType<? extends Salmon> type, Level level) {
        super(type, level);
        this.refreshDimensions();
    }

    @Override
    public int getMaxSchoolSize() {
        return 5;
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(Items.SALMON_BUCKET);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SALMON_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SALMON_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SALMON_HURT;
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.SALMON_FLOP;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Salmon.DATA_TYPE, Salmon.Variant.DEFAULT.id());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (Salmon.DATA_TYPE.equals(accessor)) {
            this.refreshDimensions();
        }

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("type", Salmon.Variant.CODEC, this.getVariant());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setVariant((Salmon.Variant) input.read("type", Salmon.Variant.CODEC).orElse(Salmon.Variant.DEFAULT));
    }

    @Override
    public void saveToBucketTag(ItemStack bucket) {
        Bucketable.saveDefaultDataToBucketTag(this, bucket);
        bucket.copyFrom(DataComponents.SALMON_SIZE, this);
    }

    public void setVariant(Salmon.Variant salmon_variant) {
        this.entityData.set(Salmon.DATA_TYPE, salmon_variant.id);
    }

    public Salmon.Variant getVariant() {
        return (Salmon.Variant) Salmon.Variant.BY_ID.apply((Integer) this.entityData.get(Salmon.DATA_TYPE));
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        return (T) (type == DataComponents.SALMON_SIZE ? castComponentValue(type, this.getVariant()) : super.get(type));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        this.applyImplicitComponentIfPresent(components, DataComponents.SALMON_SIZE);
        super.applyImplicitComponents(components);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.SALMON_SIZE) {
            this.setVariant((Salmon.Variant) castComponentValue(DataComponents.SALMON_SIZE, value));
            return true;
        } else {
            return super.applyImplicitComponent(type, value);
        }
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        WeightedList.Builder<Salmon.Variant> weightedlist_builder = WeightedList.<Salmon.Variant>builder();

        weightedlist_builder.add(Salmon.Variant.SMALL, 30);
        weightedlist_builder.add(Salmon.Variant.MEDIUM, 50);
        weightedlist_builder.add(Salmon.Variant.LARGE, 15);
        weightedlist_builder.build().getRandom(this.random).ifPresent(this::setVariant);
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    public float getSalmonScale() {
        return this.getVariant().boundingBoxScale;
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return super.getDefaultDimensions(pose).scale(this.getSalmonScale());
    }

    public static enum Variant implements StringRepresentable {

        SMALL("small", 0, 0.5F), MEDIUM("medium", 1, 1.0F), LARGE("large", 2, 1.5F);

        public static final Salmon.Variant DEFAULT = Salmon.Variant.MEDIUM;
        public static final StringRepresentable.EnumCodec<Salmon.Variant> CODEC = StringRepresentable.<Salmon.Variant>fromEnum(Salmon.Variant::values);
        private static final IntFunction<Salmon.Variant> BY_ID = ByIdMap.<Salmon.Variant>continuous(Salmon.Variant::id, values(), ByIdMap.OutOfBoundsStrategy.CLAMP);
        public static final StreamCodec<ByteBuf, Salmon.Variant> STREAM_CODEC = ByteBufCodecs.idMapper(Salmon.Variant.BY_ID, Salmon.Variant::id);
        private final String name;
        private final int id;
        private final float boundingBoxScale;

        private Variant(String name, int id, float boundingBoxScale) {
            this.name = name;
            this.id = id;
            this.boundingBoxScale = boundingBoxScale;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        private int id() {
            return this.id;
        }
    }
}

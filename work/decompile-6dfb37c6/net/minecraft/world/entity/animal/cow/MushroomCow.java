package net.minecraft.world.entity.animal.cow;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SpellParticleOption;
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
import net.minecraft.util.ByIdMap;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SuspiciousEffectHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

public class MushroomCow extends AbstractCow implements Shearable {

    private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.<Integer>defineId(MushroomCow.class, EntityDataSerializers.INT);
    private static final int MUTATE_CHANCE = 1024;
    private static final String TAG_STEW_EFFECTS = "stew_effects";
    public @Nullable SuspiciousStewEffects stewEffects;
    private @Nullable UUID lastLightningBoltUUID;

    public MushroomCow(EntityType<? extends MushroomCow> type, Level level) {
        super(type, level);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getBlockState(pos.below()).is(Blocks.MYCELIUM) ? 10.0F : level.getPathfindingCostFromLightLevels(pos);
    }

    public static boolean checkMushroomSpawnRules(EntityType<MushroomCow> type, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.MOOSHROOMS_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightningBolt) {
        UUID uuid = lightningBolt.getUUID();

        if (!uuid.equals(this.lastLightningBoltUUID)) {
            this.setVariant(this.getVariant() == MushroomCow.Variant.RED ? MushroomCow.Variant.BROWN : MushroomCow.Variant.RED);
            this.lastLightningBoltUUID = uuid;
            this.playSound(SoundEvents.MOOSHROOM_CONVERT, 2.0F, 1.0F);
        }

    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(MushroomCow.DATA_TYPE, MushroomCow.Variant.DEFAULT.id);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.is(Items.BOWL) && !this.isBaby()) {
            boolean flag = false;
            ItemStack itemstack1;

            if (this.stewEffects != null) {
                flag = true;
                itemstack1 = new ItemStack(Items.SUSPICIOUS_STEW);
                itemstack1.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, this.stewEffects);
                this.stewEffects = null;
            } else {
                itemstack1 = new ItemStack(Items.MUSHROOM_STEW);
            }

            ItemStack itemstack2 = ItemUtils.createFilledResult(itemstack, player, itemstack1, false);

            player.setItemInHand(hand, itemstack2);
            SoundEvent soundevent;

            if (flag) {
                soundevent = SoundEvents.MOOSHROOM_MILK_SUSPICIOUSLY;
            } else {
                soundevent = SoundEvents.MOOSHROOM_MILK;
            }

            this.playSound(soundevent, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        } else if (itemstack.is(Items.SHEARS) && this.readyForShearing()) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                this.shear(serverlevel, SoundSource.PLAYERS, itemstack);
                this.gameEvent(GameEvent.SHEAR, player);
                itemstack.hurtAndBreak(1, player, hand.asEquipmentSlot());
            }

            return InteractionResult.SUCCESS;
        } else if (this.getVariant() == MushroomCow.Variant.BROWN) {
            Optional<SuspiciousStewEffects> optional = this.getEffectsFromItemStack(itemstack);

            if (optional.isEmpty()) {
                return super.mobInteract(player, hand);
            } else {
                if (this.stewEffects != null) {
                    for (int i = 0; i < 2; ++i) {
                        this.level().addParticle(ParticleTypes.SMOKE, this.getX() + this.random.nextDouble() / 2.0D, this.getY(0.5D), this.getZ() + this.random.nextDouble() / 2.0D, 0.0D, this.random.nextDouble() / 5.0D, 0.0D);
                    }
                } else {
                    itemstack.consume(1, player);
                    SpellParticleOption spellparticleoption = SpellParticleOption.create(ParticleTypes.EFFECT, -1, 1.0F);

                    for (int j = 0; j < 4; ++j) {
                        this.level().addParticle(spellparticleoption, this.getX() + this.random.nextDouble() / 2.0D, this.getY(0.5D), this.getZ() + this.random.nextDouble() / 2.0D, 0.0D, this.random.nextDouble() / 5.0D, 0.0D);
                    }

                    this.stewEffects = (SuspiciousStewEffects) optional.get();
                    this.playSound(SoundEvents.MOOSHROOM_EAT, 2.0F, 1.0F);
                }

                return InteractionResult.SUCCESS;
            }
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    public void shear(ServerLevel level, SoundSource soundSource, ItemStack tool) {
        level.playSound((Entity) null, (Entity) this, SoundEvents.MOOSHROOM_SHEAR, soundSource, 1.0F, 1.0F);
        this.convertTo(EntityType.COW, ConversionParams.single(this, false, false), (cow) -> {
            level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(0.5D), this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            this.dropFromShearingLootTable(level, BuiltInLootTables.SHEAR_MOOSHROOM, tool, (serverlevel1, itemstack1) -> {
                for (int i = 0; i < itemstack1.getCount(); ++i) {
                    serverlevel1.addFreshEntity(new ItemEntity(this.level(), this.getX(), this.getY(1.0D), this.getZ(), itemstack1.copyWithCount(1)));
                }

            });
        });
    }

    @Override
    public boolean readyForShearing() {
        return this.isAlive() && !this.isBaby();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("Type", MushroomCow.Variant.CODEC, this.getVariant());
        output.storeNullable("stew_effects", SuspiciousStewEffects.CODEC, this.stewEffects);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setVariant((MushroomCow.Variant) input.read("Type", MushroomCow.Variant.CODEC).orElse(MushroomCow.Variant.DEFAULT));
        this.stewEffects = (SuspiciousStewEffects) input.read("stew_effects", SuspiciousStewEffects.CODEC).orElse((Object) null);
    }

    private Optional<SuspiciousStewEffects> getEffectsFromItemStack(ItemStack itemStack) {
        SuspiciousEffectHolder suspiciouseffectholder = SuspiciousEffectHolder.tryGet(itemStack.getItem());

        return suspiciouseffectholder != null ? Optional.of(suspiciouseffectholder.getSuspiciousEffects()) : Optional.empty();
    }

    public void setVariant(MushroomCow.Variant mushroomcow_variant) {
        this.entityData.set(MushroomCow.DATA_TYPE, mushroomcow_variant.id);
    }

    public MushroomCow.Variant getVariant() {
        return MushroomCow.Variant.byId((Integer) this.entityData.get(MushroomCow.DATA_TYPE));
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        return (T) (type == DataComponents.MOOSHROOM_VARIANT ? castComponentValue(type, this.getVariant()) : super.get(type));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        this.applyImplicitComponentIfPresent(components, DataComponents.MOOSHROOM_VARIANT);
        super.applyImplicitComponents(components);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.MOOSHROOM_VARIANT) {
            this.setVariant((MushroomCow.Variant) castComponentValue(DataComponents.MOOSHROOM_VARIANT, value));
            return true;
        } else {
            return super.applyImplicitComponent(type, value);
        }
    }

    @Override
    public @Nullable MushroomCow getBreedOffspring(ServerLevel level, AgeableMob partner) {
        MushroomCow mushroomcow = EntityType.MOOSHROOM.create(level, EntitySpawnReason.BREEDING);

        if (mushroomcow != null) {
            mushroomcow.setVariant(this.getOffspringVariant((MushroomCow) partner));
        }

        return mushroomcow;
    }

    private MushroomCow.Variant getOffspringVariant(MushroomCow mate) {
        MushroomCow.Variant mushroomcow_variant = this.getVariant();
        MushroomCow.Variant mushroomcow_variant1 = mate.getVariant();
        MushroomCow.Variant mushroomcow_variant2;

        if (mushroomcow_variant == mushroomcow_variant1 && this.random.nextInt(1024) == 0) {
            mushroomcow_variant2 = mushroomcow_variant == MushroomCow.Variant.BROWN ? MushroomCow.Variant.RED : MushroomCow.Variant.BROWN;
        } else {
            mushroomcow_variant2 = this.random.nextBoolean() ? mushroomcow_variant : mushroomcow_variant1;
        }

        return mushroomcow_variant2;
    }

    public static enum Variant implements StringRepresentable {

        RED("red", 0, Blocks.RED_MUSHROOM.defaultBlockState()), BROWN("brown", 1, Blocks.BROWN_MUSHROOM.defaultBlockState());

        public static final MushroomCow.Variant DEFAULT = MushroomCow.Variant.RED;
        public static final Codec<MushroomCow.Variant> CODEC = StringRepresentable.<MushroomCow.Variant>fromEnum(MushroomCow.Variant::values);
        private static final IntFunction<MushroomCow.Variant> BY_ID = ByIdMap.<MushroomCow.Variant>continuous(MushroomCow.Variant::id, values(), ByIdMap.OutOfBoundsStrategy.CLAMP);
        public static final StreamCodec<ByteBuf, MushroomCow.Variant> STREAM_CODEC = ByteBufCodecs.idMapper(MushroomCow.Variant.BY_ID, MushroomCow.Variant::id);
        private final String type;
        private final int id;
        private final BlockState blockState;

        private Variant(String type, int id, BlockState blockState) {
            this.type = type;
            this.id = id;
            this.blockState = blockState;
        }

        public BlockState getBlockState() {
            return this.blockState;
        }

        @Override
        public String getSerializedName() {
            return this.type;
        }

        private int id() {
            return this.id;
        }

        private static MushroomCow.Variant byId(int id) {
            return (MushroomCow.Variant) MushroomCow.Variant.BY_ID.apply(id);
        }
    }
}

package net.minecraft.network.syncher;

import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Rotations;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;
import net.minecraft.world.entity.animal.cow.CowVariant;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.world.entity.animal.frog.FrogVariant;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilusVariant;
import net.minecraft.world.entity.animal.pig.PigVariant;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public class EntityDataSerializers {

    private static final CrudeIncrementalIntIdentityHashBiMap<EntityDataSerializer<?>> SERIALIZERS = CrudeIncrementalIntIdentityHashBiMap.<EntityDataSerializer<?>>create(16);
    public static final EntityDataSerializer<Byte> BYTE = EntityDataSerializer.<Byte>forValueType(ByteBufCodecs.BYTE);
    public static final EntityDataSerializer<Integer> INT = EntityDataSerializer.<Integer>forValueType(ByteBufCodecs.VAR_INT);
    public static final EntityDataSerializer<Long> LONG = EntityDataSerializer.<Long>forValueType(ByteBufCodecs.VAR_LONG);
    public static final EntityDataSerializer<Float> FLOAT = EntityDataSerializer.<Float>forValueType(ByteBufCodecs.FLOAT);
    public static final EntityDataSerializer<String> STRING = EntityDataSerializer.<String>forValueType(ByteBufCodecs.STRING_UTF8);
    public static final EntityDataSerializer<Component> COMPONENT = EntityDataSerializer.<Component>forValueType(ComponentSerialization.TRUSTED_STREAM_CODEC);
    public static final EntityDataSerializer<Optional<Component>> OPTIONAL_COMPONENT = EntityDataSerializer.<Optional<Component>>forValueType(ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC);
    public static final EntityDataSerializer<ItemStack> ITEM_STACK = new EntityDataSerializer<ItemStack>() {
        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, ItemStack> codec() {
            return ItemStack.OPTIONAL_STREAM_CODEC;
        }

        public ItemStack copy(ItemStack value) {
            return value.copy();
        }
    };
    public static final EntityDataSerializer<BlockState> BLOCK_STATE = EntityDataSerializer.<BlockState>forValueType(ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY));
    private static final StreamCodec<ByteBuf, Optional<BlockState>> OPTIONAL_BLOCK_STATE_CODEC = new StreamCodec<ByteBuf, Optional<BlockState>>() {
        public void encode(ByteBuf output, Optional<BlockState> value) {
            if (value.isPresent()) {
                VarInt.write(output, Block.getId((BlockState) value.get()));
            } else {
                VarInt.write(output, 0);
            }

        }

        public Optional<BlockState> decode(ByteBuf input) {
            int i = VarInt.read(input);

            return i == 0 ? Optional.empty() : Optional.of(Block.stateById(i));
        }
    };
    public static final EntityDataSerializer<Optional<BlockState>> OPTIONAL_BLOCK_STATE = EntityDataSerializer.<Optional<BlockState>>forValueType(EntityDataSerializers.OPTIONAL_BLOCK_STATE_CODEC);
    public static final EntityDataSerializer<Boolean> BOOLEAN = EntityDataSerializer.<Boolean>forValueType(ByteBufCodecs.BOOL);
    public static final EntityDataSerializer<ParticleOptions> PARTICLE = EntityDataSerializer.<ParticleOptions>forValueType(ParticleTypes.STREAM_CODEC);
    public static final EntityDataSerializer<List<ParticleOptions>> PARTICLES = EntityDataSerializer.<List<ParticleOptions>>forValueType(ParticleTypes.STREAM_CODEC.apply(ByteBufCodecs.list()));
    public static final EntityDataSerializer<Rotations> ROTATIONS = EntityDataSerializer.<Rotations>forValueType(Rotations.STREAM_CODEC);
    public static final EntityDataSerializer<BlockPos> BLOCK_POS = EntityDataSerializer.<BlockPos>forValueType(BlockPos.STREAM_CODEC);
    public static final EntityDataSerializer<Optional<BlockPos>> OPTIONAL_BLOCK_POS = EntityDataSerializer.<Optional<BlockPos>>forValueType(BlockPos.STREAM_CODEC.apply(ByteBufCodecs::optional));
    public static final EntityDataSerializer<Direction> DIRECTION = EntityDataSerializer.<Direction>forValueType(Direction.STREAM_CODEC);
    public static final EntityDataSerializer<Optional<EntityReference<LivingEntity>>> OPTIONAL_LIVING_ENTITY_REFERENCE = EntityDataSerializer.<Optional<EntityReference<LivingEntity>>>forValueType(EntityReference.streamCodec().apply(ByteBufCodecs::optional));
    public static final EntityDataSerializer<Optional<GlobalPos>> OPTIONAL_GLOBAL_POS = EntityDataSerializer.<Optional<GlobalPos>>forValueType(GlobalPos.STREAM_CODEC.apply(ByteBufCodecs::optional));
    public static final EntityDataSerializer<VillagerData> VILLAGER_DATA = EntityDataSerializer.<VillagerData>forValueType(VillagerData.STREAM_CODEC);
    private static final StreamCodec<ByteBuf, OptionalInt> OPTIONAL_UNSIGNED_INT_CODEC = new StreamCodec<ByteBuf, OptionalInt>() {
        public OptionalInt decode(ByteBuf input) {
            int i = VarInt.read(input);

            return i == 0 ? OptionalInt.empty() : OptionalInt.of(i - 1);
        }

        public void encode(ByteBuf output, OptionalInt value) {
            VarInt.write(output, value.orElse(-1) + 1);
        }
    };
    public static final EntityDataSerializer<OptionalInt> OPTIONAL_UNSIGNED_INT = EntityDataSerializer.<OptionalInt>forValueType(EntityDataSerializers.OPTIONAL_UNSIGNED_INT_CODEC);
    public static final EntityDataSerializer<Pose> POSE = EntityDataSerializer.<Pose>forValueType(Pose.STREAM_CODEC);
    public static final EntityDataSerializer<Holder<CatVariant>> CAT_VARIANT = EntityDataSerializer.<Holder<CatVariant>>forValueType(CatVariant.STREAM_CODEC);
    public static final EntityDataSerializer<Holder<ChickenVariant>> CHICKEN_VARIANT = EntityDataSerializer.<Holder<ChickenVariant>>forValueType(ChickenVariant.STREAM_CODEC);
    public static final EntityDataSerializer<Holder<CowVariant>> COW_VARIANT = EntityDataSerializer.<Holder<CowVariant>>forValueType(CowVariant.STREAM_CODEC);
    public static final EntityDataSerializer<Holder<WolfVariant>> WOLF_VARIANT = EntityDataSerializer.<Holder<WolfVariant>>forValueType(WolfVariant.STREAM_CODEC);
    public static final EntityDataSerializer<Holder<WolfSoundVariant>> WOLF_SOUND_VARIANT = EntityDataSerializer.<Holder<WolfSoundVariant>>forValueType(WolfSoundVariant.STREAM_CODEC);
    public static final EntityDataSerializer<Holder<FrogVariant>> FROG_VARIANT = EntityDataSerializer.<Holder<FrogVariant>>forValueType(FrogVariant.STREAM_CODEC);
    public static final EntityDataSerializer<Holder<PigVariant>> PIG_VARIANT = EntityDataSerializer.<Holder<PigVariant>>forValueType(PigVariant.STREAM_CODEC);
    public static final EntityDataSerializer<Holder<ZombieNautilusVariant>> ZOMBIE_NAUTILUS_VARIANT = EntityDataSerializer.<Holder<ZombieNautilusVariant>>forValueType(ZombieNautilusVariant.STREAM_CODEC);
    public static final EntityDataSerializer<Holder<PaintingVariant>> PAINTING_VARIANT = EntityDataSerializer.<Holder<PaintingVariant>>forValueType(PaintingVariant.STREAM_CODEC);
    public static final EntityDataSerializer<Armadillo.ArmadilloState> ARMADILLO_STATE = EntityDataSerializer.<Armadillo.ArmadilloState>forValueType(Armadillo.ArmadilloState.STREAM_CODEC);
    public static final EntityDataSerializer<Sniffer.State> SNIFFER_STATE = EntityDataSerializer.<Sniffer.State>forValueType(Sniffer.State.STREAM_CODEC);
    public static final EntityDataSerializer<WeatheringCopper.WeatherState> WEATHERING_COPPER_STATE = EntityDataSerializer.<WeatheringCopper.WeatherState>forValueType(WeatheringCopper.WeatherState.STREAM_CODEC);
    public static final EntityDataSerializer<CopperGolemState> COPPER_GOLEM_STATE = EntityDataSerializer.<CopperGolemState>forValueType(CopperGolemState.STREAM_CODEC);
    public static final EntityDataSerializer<Vector3fc> VECTOR3 = EntityDataSerializer.<Vector3fc>forValueType(ByteBufCodecs.VECTOR3F);
    public static final EntityDataSerializer<Quaternionfc> QUATERNION = EntityDataSerializer.<Quaternionfc>forValueType(ByteBufCodecs.QUATERNIONF);
    public static final EntityDataSerializer<ResolvableProfile> RESOLVABLE_PROFILE = EntityDataSerializer.<ResolvableProfile>forValueType(ResolvableProfile.STREAM_CODEC);
    public static final EntityDataSerializer<HumanoidArm> HUMANOID_ARM = EntityDataSerializer.<HumanoidArm>forValueType(HumanoidArm.STREAM_CODEC);

    public static void registerSerializer(EntityDataSerializer<?> serializer) {
        EntityDataSerializers.SERIALIZERS.add(serializer);
    }

    public static @Nullable EntityDataSerializer<?> getSerializer(int id) {
        return (EntityDataSerializer) EntityDataSerializers.SERIALIZERS.byId(id);
    }

    public static int getSerializedId(EntityDataSerializer<?> serializer) {
        return EntityDataSerializers.SERIALIZERS.getId(serializer);
    }

    private EntityDataSerializers() {}

    static {
        registerSerializer(EntityDataSerializers.BYTE);
        registerSerializer(EntityDataSerializers.INT);
        registerSerializer(EntityDataSerializers.LONG);
        registerSerializer(EntityDataSerializers.FLOAT);
        registerSerializer(EntityDataSerializers.STRING);
        registerSerializer(EntityDataSerializers.COMPONENT);
        registerSerializer(EntityDataSerializers.OPTIONAL_COMPONENT);
        registerSerializer(EntityDataSerializers.ITEM_STACK);
        registerSerializer(EntityDataSerializers.BOOLEAN);
        registerSerializer(EntityDataSerializers.ROTATIONS);
        registerSerializer(EntityDataSerializers.BLOCK_POS);
        registerSerializer(EntityDataSerializers.OPTIONAL_BLOCK_POS);
        registerSerializer(EntityDataSerializers.DIRECTION);
        registerSerializer(EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
        registerSerializer(EntityDataSerializers.BLOCK_STATE);
        registerSerializer(EntityDataSerializers.OPTIONAL_BLOCK_STATE);
        registerSerializer(EntityDataSerializers.PARTICLE);
        registerSerializer(EntityDataSerializers.PARTICLES);
        registerSerializer(EntityDataSerializers.VILLAGER_DATA);
        registerSerializer(EntityDataSerializers.OPTIONAL_UNSIGNED_INT);
        registerSerializer(EntityDataSerializers.POSE);
        registerSerializer(EntityDataSerializers.CAT_VARIANT);
        registerSerializer(EntityDataSerializers.COW_VARIANT);
        registerSerializer(EntityDataSerializers.WOLF_VARIANT);
        registerSerializer(EntityDataSerializers.WOLF_SOUND_VARIANT);
        registerSerializer(EntityDataSerializers.FROG_VARIANT);
        registerSerializer(EntityDataSerializers.PIG_VARIANT);
        registerSerializer(EntityDataSerializers.CHICKEN_VARIANT);
        registerSerializer(EntityDataSerializers.ZOMBIE_NAUTILUS_VARIANT);
        registerSerializer(EntityDataSerializers.OPTIONAL_GLOBAL_POS);
        registerSerializer(EntityDataSerializers.PAINTING_VARIANT);
        registerSerializer(EntityDataSerializers.SNIFFER_STATE);
        registerSerializer(EntityDataSerializers.ARMADILLO_STATE);
        registerSerializer(EntityDataSerializers.COPPER_GOLEM_STATE);
        registerSerializer(EntityDataSerializers.WEATHERING_COPPER_STATE);
        registerSerializer(EntityDataSerializers.VECTOR3);
        registerSerializer(EntityDataSerializers.QUATERNION);
        registerSerializer(EntityDataSerializers.RESOLVABLE_PROFILE);
        registerSerializer(EntityDataSerializers.HUMANOID_ARM);
    }
}

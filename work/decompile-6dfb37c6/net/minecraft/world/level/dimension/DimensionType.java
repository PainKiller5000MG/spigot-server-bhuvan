package net.minecraft.world.level.dimension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.timeline.Timeline;

public record DimensionType(boolean hasFixedTime, boolean hasSkyLight, boolean hasCeiling, double coordinateScale, int minY, int height, int logicalHeight, TagKey<Block> infiniburn, float ambientLight, DimensionType.MonsterSettings monsterSettings, DimensionType.Skybox skybox, DimensionType.CardinalLightType cardinalLightType, EnvironmentAttributeMap attributes, HolderSet<Timeline> timelines) {

    public static final int BITS_FOR_Y = BlockPos.PACKED_Y_LENGTH;
    public static final int MIN_HEIGHT = 16;
    public static final int Y_SIZE = (1 << DimensionType.BITS_FOR_Y) - 32;
    public static final int MAX_Y = (DimensionType.Y_SIZE >> 1) - 1;
    public static final int MIN_Y = DimensionType.MAX_Y - DimensionType.Y_SIZE + 1;
    public static final int WAY_ABOVE_MAX_Y = DimensionType.MAX_Y << 4;
    public static final int WAY_BELOW_MIN_Y = DimensionType.MIN_Y << 4;
    public static final Codec<DimensionType> DIRECT_CODEC = createDirectCodec(EnvironmentAttributeMap.CODEC);
    public static final Codec<DimensionType> NETWORK_CODEC = createDirectCodec(EnvironmentAttributeMap.NETWORK_CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<DimensionType>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.DIMENSION_TYPE);
    public static final float[] MOON_BRIGHTNESS_PER_PHASE = new float[]{1.0F, 0.75F, 0.5F, 0.25F, 0.0F, 0.25F, 0.5F, 0.75F};
    public static final Codec<Holder<DimensionType>> CODEC = RegistryFileCodec.<Holder<DimensionType>>create(Registries.DIMENSION_TYPE, DimensionType.DIRECT_CODEC);

    public DimensionType {
        if (height < 16) {
            throw new IllegalStateException("height has to be at least 16");
        } else if (minY + height > DimensionType.MAX_Y + 1) {
            throw new IllegalStateException("min_y + height cannot be higher than: " + (DimensionType.MAX_Y + 1));
        } else if (logicalHeight > height) {
            throw new IllegalStateException("logical_height cannot be higher than height");
        } else if (height % 16 != 0) {
            throw new IllegalStateException("height has to be multiple of 16");
        } else if (minY % 16 != 0) {
            throw new IllegalStateException("min_y has to be a multiple of 16");
        }
    }

    private static Codec<DimensionType> createDirectCodec(Codec<EnvironmentAttributeMap> attributeMapCodec) {
        return ExtraCodecs.<DimensionType>catchDecoderException(RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.BOOL.optionalFieldOf("has_fixed_time", false).forGetter(DimensionType::hasFixedTime), Codec.BOOL.fieldOf("has_skylight").forGetter(DimensionType::hasSkyLight), Codec.BOOL.fieldOf("has_ceiling").forGetter(DimensionType::hasCeiling), Codec.doubleRange((double) 1.0E-5F, 3.0E7D).fieldOf("coordinate_scale").forGetter(DimensionType::coordinateScale), Codec.intRange(DimensionType.MIN_Y, DimensionType.MAX_Y).fieldOf("min_y").forGetter(DimensionType::minY), Codec.intRange(16, DimensionType.Y_SIZE).fieldOf("height").forGetter(DimensionType::height), Codec.intRange(0, DimensionType.Y_SIZE).fieldOf("logical_height").forGetter(DimensionType::logicalHeight), TagKey.hashedCodec(Registries.BLOCK).fieldOf("infiniburn").forGetter(DimensionType::infiniburn), Codec.FLOAT.fieldOf("ambient_light").forGetter(DimensionType::ambientLight), DimensionType.MonsterSettings.CODEC.forGetter(DimensionType::monsterSettings), DimensionType.Skybox.CODEC.optionalFieldOf("skybox", DimensionType.Skybox.OVERWORLD).forGetter(DimensionType::skybox), DimensionType.CardinalLightType.CODEC.optionalFieldOf("cardinal_light", DimensionType.CardinalLightType.DEFAULT).forGetter(DimensionType::cardinalLightType), attributeMapCodec.optionalFieldOf("attributes", EnvironmentAttributeMap.EMPTY).forGetter(DimensionType::attributes), RegistryCodecs.homogeneousList(Registries.TIMELINE).optionalFieldOf("timelines", HolderSet.empty()).forGetter(DimensionType::timelines)).apply(instance, DimensionType::new);
        }));
    }

    public static double getTeleportationScale(DimensionType lastDimensionType, DimensionType newDimensionType) {
        double d0 = lastDimensionType.coordinateScale();
        double d1 = newDimensionType.coordinateScale();

        return d0 / d1;
    }

    public static Path getStorageFolder(ResourceKey<Level> name, Path baseFolder) {
        return name == Level.OVERWORLD ? baseFolder : (name == Level.END ? baseFolder.resolve("DIM1") : (name == Level.NETHER ? baseFolder.resolve("DIM-1") : baseFolder.resolve("dimensions").resolve(name.identifier().getNamespace()).resolve(name.identifier().getPath())));
    }

    public IntProvider monsterSpawnLightTest() {
        return this.monsterSettings.monsterSpawnLightTest();
    }

    public int monsterSpawnBlockLightLimit() {
        return this.monsterSettings.monsterSpawnBlockLightLimit();
    }

    public boolean hasEndFlashes() {
        return this.skybox == DimensionType.Skybox.END;
    }

    public static record MonsterSettings(IntProvider monsterSpawnLightTest, int monsterSpawnBlockLightLimit) {

        public static final MapCodec<DimensionType.MonsterSettings> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(IntProvider.codec(0, 15).fieldOf("monster_spawn_light_level").forGetter(DimensionType.MonsterSettings::monsterSpawnLightTest), Codec.intRange(0, 15).fieldOf("monster_spawn_block_light_limit").forGetter(DimensionType.MonsterSettings::monsterSpawnBlockLightLimit)).apply(instance, DimensionType.MonsterSettings::new);
        });
    }

    public static enum Skybox implements StringRepresentable {

        NONE("none"), OVERWORLD("overworld"), END("end");

        public static final Codec<DimensionType.Skybox> CODEC = StringRepresentable.<DimensionType.Skybox>fromEnum(DimensionType.Skybox::values);
        private final String name;

        private Skybox(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static enum CardinalLightType implements StringRepresentable {

        DEFAULT("default"), NETHER("nether");

        public static final Codec<DimensionType.CardinalLightType> CODEC = StringRepresentable.<DimensionType.CardinalLightType>fromEnum(DimensionType.CardinalLightType::values);
        private final String name;

        private CardinalLightType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}

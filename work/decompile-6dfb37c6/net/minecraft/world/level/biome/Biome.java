package net.minecraft.world.level.biome;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import net.minecraft.world.level.DryFoliageColor;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

public final class Biome {

    public static final Codec<Biome> DIRECT_CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Biome.ClimateSettings.CODEC.forGetter((biome) -> {
            return biome.climateSettings;
        }), EnvironmentAttributeMap.CODEC_ONLY_POSITIONAL.optionalFieldOf("attributes", EnvironmentAttributeMap.EMPTY).forGetter((biome) -> {
            return biome.attributes;
        }), BiomeSpecialEffects.CODEC.fieldOf("effects").forGetter((biome) -> {
            return biome.specialEffects;
        }), BiomeGenerationSettings.CODEC.forGetter((biome) -> {
            return biome.generationSettings;
        }), MobSpawnSettings.CODEC.forGetter((biome) -> {
            return biome.mobSettings;
        })).apply(instance, Biome::new);
    });
    public static final Codec<Biome> NETWORK_CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Biome.ClimateSettings.CODEC.forGetter((biome) -> {
            return biome.climateSettings;
        }), EnvironmentAttributeMap.NETWORK_CODEC.optionalFieldOf("attributes", EnvironmentAttributeMap.EMPTY).forGetter((biome) -> {
            return biome.attributes;
        }), BiomeSpecialEffects.CODEC.fieldOf("effects").forGetter((biome) -> {
            return biome.specialEffects;
        })).apply(instance, (biome_climatesettings, environmentattributemap, biomespecialeffects) -> {
            return new Biome(biome_climatesettings, environmentattributemap, biomespecialeffects, BiomeGenerationSettings.EMPTY, MobSpawnSettings.EMPTY);
        });
    });
    public static final Codec<Holder<Biome>> CODEC = RegistryFileCodec.<Holder<Biome>>create(Registries.BIOME, Biome.DIRECT_CODEC);
    public static final Codec<HolderSet<Biome>> LIST_CODEC = RegistryCodecs.homogeneousList(Registries.BIOME, Biome.DIRECT_CODEC);
    private static final PerlinSimplexNoise TEMPERATURE_NOISE = new PerlinSimplexNoise(new WorldgenRandom(new LegacyRandomSource(1234L)), ImmutableList.of(0));
    private static final PerlinSimplexNoise FROZEN_TEMPERATURE_NOISE = new PerlinSimplexNoise(new WorldgenRandom(new LegacyRandomSource(3456L)), ImmutableList.of(-2, -1, 0));
    /** @deprecated */
    @Deprecated(forRemoval = true)
    public static final PerlinSimplexNoise BIOME_INFO_NOISE = new PerlinSimplexNoise(new WorldgenRandom(new LegacyRandomSource(2345L)), ImmutableList.of(0));
    private static final int TEMPERATURE_CACHE_SIZE = 1024;
    public final Biome.ClimateSettings climateSettings;
    private final BiomeGenerationSettings generationSettings;
    private final MobSpawnSettings mobSettings;
    private final EnvironmentAttributeMap attributes;
    private final BiomeSpecialEffects specialEffects;
    private final ThreadLocal<Long2FloatLinkedOpenHashMap> temperatureCache = ThreadLocal.withInitial(() -> {
        Long2FloatLinkedOpenHashMap long2floatlinkedopenhashmap = new Long2FloatLinkedOpenHashMap(1024, 0.25F) {
            protected void rehash(int newN) {}
        };

        long2floatlinkedopenhashmap.defaultReturnValue(Float.NaN);
        return long2floatlinkedopenhashmap;
    });

    private Biome(Biome.ClimateSettings climateSettings, EnvironmentAttributeMap attributes, BiomeSpecialEffects specialEffects, BiomeGenerationSettings generationSettings, MobSpawnSettings mobSettings) {
        this.climateSettings = climateSettings;
        this.generationSettings = generationSettings;
        this.mobSettings = mobSettings;
        this.attributes = attributes;
        this.specialEffects = specialEffects;
    }

    public MobSpawnSettings getMobSettings() {
        return this.mobSettings;
    }

    public boolean hasPrecipitation() {
        return this.climateSettings.hasPrecipitation();
    }

    public Biome.Precipitation getPrecipitationAt(BlockPos pos, int seaLevel) {
        return !this.hasPrecipitation() ? Biome.Precipitation.NONE : (this.coldEnoughToSnow(pos, seaLevel) ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN);
    }

    private float getHeightAdjustedTemperature(BlockPos pos, int seaLevel) {
        float f = this.climateSettings.temperatureModifier.modifyTemperature(pos, this.getBaseTemperature());
        int j = seaLevel + 17;

        if (pos.getY() > j) {
            float f1 = (float) (Biome.TEMPERATURE_NOISE.getValue((double) ((float) pos.getX() / 8.0F), (double) ((float) pos.getZ() / 8.0F), false) * 8.0D);

            return f - (f1 + (float) pos.getY() - (float) j) * 0.05F / 40.0F;
        } else {
            return f;
        }
    }

    /** @deprecated */
    @Deprecated
    public float getTemperature(BlockPos pos, int seaLevel) {
        long j = pos.asLong();
        Long2FloatLinkedOpenHashMap long2floatlinkedopenhashmap = (Long2FloatLinkedOpenHashMap) this.temperatureCache.get();
        float f = long2floatlinkedopenhashmap.get(j);

        if (!Float.isNaN(f)) {
            return f;
        } else {
            float f1 = this.getHeightAdjustedTemperature(pos, seaLevel);

            if (long2floatlinkedopenhashmap.size() == 1024) {
                long2floatlinkedopenhashmap.removeFirstFloat();
            }

            long2floatlinkedopenhashmap.put(j, f1);
            return f1;
        }
    }

    public boolean shouldFreeze(LevelReader level, BlockPos pos) {
        return this.shouldFreeze(level, pos, true);
    }

    public boolean shouldFreeze(LevelReader level, BlockPos pos, boolean checkNeighbors) {
        if (this.warmEnoughToRain(pos, level.getSeaLevel())) {
            return false;
        } else {
            if (level.isInsideBuildHeight(pos.getY()) && level.getBrightness(LightLayer.BLOCK, pos) < 10) {
                BlockState blockstate = level.getBlockState(pos);
                FluidState fluidstate = level.getFluidState(pos);

                if (fluidstate.getType() == Fluids.WATER && blockstate.getBlock() instanceof LiquidBlock) {
                    if (!checkNeighbors) {
                        return true;
                    }

                    boolean flag1 = level.isWaterAt(pos.west()) && level.isWaterAt(pos.east()) && level.isWaterAt(pos.north()) && level.isWaterAt(pos.south());

                    if (!flag1) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    public boolean coldEnoughToSnow(BlockPos pos, int seaLevel) {
        return !this.warmEnoughToRain(pos, seaLevel);
    }

    public boolean warmEnoughToRain(BlockPos pos, int seaLevel) {
        return this.getTemperature(pos, seaLevel) >= 0.15F;
    }

    public boolean shouldMeltFrozenOceanIcebergSlightly(BlockPos pos, int seaLevel) {
        return this.getTemperature(pos, seaLevel) > 0.1F;
    }

    public boolean shouldSnow(LevelReader level, BlockPos pos) {
        if (this.getPrecipitationAt(pos, level.getSeaLevel()) != Biome.Precipitation.SNOW) {
            return false;
        } else {
            if (level.isInsideBuildHeight(pos.getY()) && level.getBrightness(LightLayer.BLOCK, pos) < 10) {
                BlockState blockstate = level.getBlockState(pos);

                if ((blockstate.isAir() || blockstate.is(Blocks.SNOW)) && Blocks.SNOW.defaultBlockState().canSurvive(level, pos)) {
                    return true;
                }
            }

            return false;
        }
    }

    public BiomeGenerationSettings getGenerationSettings() {
        return this.generationSettings;
    }

    public int getGrassColor(double x, double z) {
        int i = this.getBaseGrassColor();

        return this.specialEffects.grassColorModifier().modifyColor(x, z, i);
    }

    private int getBaseGrassColor() {
        Optional<Integer> optional = this.specialEffects.grassColorOverride();

        return optional.isPresent() ? (Integer) optional.get() : this.getGrassColorFromTexture();
    }

    private int getGrassColorFromTexture() {
        double d0 = (double) Mth.clamp(this.climateSettings.temperature, 0.0F, 1.0F);
        double d1 = (double) Mth.clamp(this.climateSettings.downfall, 0.0F, 1.0F);

        return GrassColor.get(d0, d1);
    }

    public int getFoliageColor() {
        return (Integer) this.specialEffects.foliageColorOverride().orElseGet(this::getFoliageColorFromTexture);
    }

    private int getFoliageColorFromTexture() {
        double d0 = (double) Mth.clamp(this.climateSettings.temperature, 0.0F, 1.0F);
        double d1 = (double) Mth.clamp(this.climateSettings.downfall, 0.0F, 1.0F);

        return FoliageColor.get(d0, d1);
    }

    public int getDryFoliageColor() {
        return (Integer) this.specialEffects.dryFoliageColorOverride().orElseGet(this::getDryFoliageColorFromTexture);
    }

    private int getDryFoliageColorFromTexture() {
        double d0 = (double) Mth.clamp(this.climateSettings.temperature, 0.0F, 1.0F);
        double d1 = (double) Mth.clamp(this.climateSettings.downfall, 0.0F, 1.0F);

        return DryFoliageColor.get(d0, d1);
    }

    public float getBaseTemperature() {
        return this.climateSettings.temperature;
    }

    public EnvironmentAttributeMap getAttributes() {
        return this.attributes;
    }

    public BiomeSpecialEffects getSpecialEffects() {
        return this.specialEffects;
    }

    public int getWaterColor() {
        return this.specialEffects.waterColor();
    }

    public static enum Precipitation implements StringRepresentable {

        NONE("none"), RAIN("rain"), SNOW("snow");

        public static final Codec<Biome.Precipitation> CODEC = StringRepresentable.<Biome.Precipitation>fromEnum(Biome.Precipitation::values);
        private final String name;

        private Precipitation(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static enum TemperatureModifier implements StringRepresentable {

        NONE("none") {
            @Override
            public float modifyTemperature(BlockPos pos, float baseTemperature) {
                return baseTemperature;
            }
        },
        FROZEN("frozen") {
            @Override
            public float modifyTemperature(BlockPos pos, float baseTemperature) {
                double d0 = Biome.FROZEN_TEMPERATURE_NOISE.getValue((double) pos.getX() * 0.05D, (double) pos.getZ() * 0.05D, false) * 7.0D;
                double d1 = Biome.BIOME_INFO_NOISE.getValue((double) pos.getX() * 0.2D, (double) pos.getZ() * 0.2D, false);
                double d2 = d0 + d1;

                if (d2 < 0.3D) {
                    double d3 = Biome.BIOME_INFO_NOISE.getValue((double) pos.getX() * 0.09D, (double) pos.getZ() * 0.09D, false);

                    if (d3 < 0.8D) {
                        return 0.2F;
                    }
                }

                return baseTemperature;
            }
        };

        private final String name;
        public static final Codec<Biome.TemperatureModifier> CODEC = StringRepresentable.<Biome.TemperatureModifier>fromEnum(Biome.TemperatureModifier::values);

        public abstract float modifyTemperature(BlockPos pos, float baseTemperature);

        private TemperatureModifier(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static class BiomeBuilder {

        private boolean hasPrecipitation = true;
        private @Nullable Float temperature;
        private Biome.TemperatureModifier temperatureModifier;
        private @Nullable Float downfall;
        private final EnvironmentAttributeMap.Builder attributes;
        private @Nullable BiomeSpecialEffects specialEffects;
        private @Nullable MobSpawnSettings mobSpawnSettings;
        private @Nullable BiomeGenerationSettings generationSettings;

        public BiomeBuilder() {
            this.temperatureModifier = Biome.TemperatureModifier.NONE;
            this.attributes = EnvironmentAttributeMap.builder();
        }

        public Biome.BiomeBuilder hasPrecipitation(boolean hasPrecipitation) {
            this.hasPrecipitation = hasPrecipitation;
            return this;
        }

        public Biome.BiomeBuilder temperature(float temperature) {
            this.temperature = temperature;
            return this;
        }

        public Biome.BiomeBuilder downfall(float downfall) {
            this.downfall = downfall;
            return this;
        }

        public Biome.BiomeBuilder putAttributes(EnvironmentAttributeMap attributes) {
            this.attributes.putAll(attributes);
            return this;
        }

        public Biome.BiomeBuilder putAttributes(EnvironmentAttributeMap.Builder attributes) {
            return this.putAttributes(attributes.build());
        }

        public <Value> Biome.BiomeBuilder setAttribute(EnvironmentAttribute<Value> attribute, Value value) {
            this.attributes.set(attribute, value);
            return this;
        }

        public <Value, Parameter> Biome.BiomeBuilder modifyAttribute(EnvironmentAttribute<Value> attribute, AttributeModifier<Value, Parameter> modifier, Parameter value) {
            this.attributes.modify(attribute, modifier, value);
            return this;
        }

        public Biome.BiomeBuilder specialEffects(BiomeSpecialEffects specialEffects) {
            this.specialEffects = specialEffects;
            return this;
        }

        public Biome.BiomeBuilder mobSpawnSettings(MobSpawnSettings mobSpawnSettings) {
            this.mobSpawnSettings = mobSpawnSettings;
            return this;
        }

        public Biome.BiomeBuilder generationSettings(BiomeGenerationSettings generationSettings) {
            this.generationSettings = generationSettings;
            return this;
        }

        public Biome.BiomeBuilder temperatureAdjustment(Biome.TemperatureModifier temperatureModifier) {
            this.temperatureModifier = temperatureModifier;
            return this;
        }

        public Biome build() {
            if (this.temperature != null && this.downfall != null && this.specialEffects != null && this.mobSpawnSettings != null && this.generationSettings != null) {
                return new Biome(new Biome.ClimateSettings(this.hasPrecipitation, this.temperature, this.temperatureModifier, this.downfall), this.attributes.build(), this.specialEffects, this.generationSettings, this.mobSpawnSettings);
            } else {
                throw new IllegalStateException("You are missing parameters to build a proper biome\n" + String.valueOf(this));
            }
        }

        public String toString() {
            return "BiomeBuilder{\nhasPrecipitation=" + this.hasPrecipitation + ",\ntemperature=" + this.temperature + ",\ntemperatureModifier=" + String.valueOf(this.temperatureModifier) + ",\ndownfall=" + this.downfall + ",\nspecialEffects=" + String.valueOf(this.specialEffects) + ",\nmobSpawnSettings=" + String.valueOf(this.mobSpawnSettings) + ",\ngenerationSettings=" + String.valueOf(this.generationSettings) + ",\n}";
        }
    }

    public static record ClimateSettings(boolean hasPrecipitation, float temperature, Biome.TemperatureModifier temperatureModifier, float downfall) {

        public static final MapCodec<Biome.ClimateSettings> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Codec.BOOL.fieldOf("has_precipitation").forGetter((biome_climatesettings) -> {
                return biome_climatesettings.hasPrecipitation;
            }), Codec.FLOAT.fieldOf("temperature").forGetter((biome_climatesettings) -> {
                return biome_climatesettings.temperature;
            }), Biome.TemperatureModifier.CODEC.optionalFieldOf("temperature_modifier", Biome.TemperatureModifier.NONE).forGetter((biome_climatesettings) -> {
                return biome_climatesettings.temperatureModifier;
            }), Codec.FLOAT.fieldOf("downfall").forGetter((biome_climatesettings) -> {
                return biome_climatesettings.downfall;
            })).apply(instance, Biome.ClimateSettings::new);
        });
    }
}

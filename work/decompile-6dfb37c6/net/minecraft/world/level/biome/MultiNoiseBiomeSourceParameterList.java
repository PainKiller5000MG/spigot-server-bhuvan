package net.minecraft.world.level.biome;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;

public class MultiNoiseBiomeSourceParameterList {

    public static final Codec<MultiNoiseBiomeSourceParameterList> DIRECT_CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(MultiNoiseBiomeSourceParameterList.Preset.CODEC.fieldOf("preset").forGetter((multinoisebiomesourceparameterlist) -> {
            return multinoisebiomesourceparameterlist.preset;
        }), RegistryOps.retrieveGetter(Registries.BIOME)).apply(instance, MultiNoiseBiomeSourceParameterList::new);
    });
    public static final Codec<Holder<MultiNoiseBiomeSourceParameterList>> CODEC = RegistryFileCodec.<Holder<MultiNoiseBiomeSourceParameterList>>create(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, MultiNoiseBiomeSourceParameterList.DIRECT_CODEC);
    private final MultiNoiseBiomeSourceParameterList.Preset preset;
    private final Climate.ParameterList<Holder<Biome>> parameters;

    public MultiNoiseBiomeSourceParameterList(MultiNoiseBiomeSourceParameterList.Preset preset, HolderGetter<Biome> biomes) {
        this.preset = preset;
        MultiNoiseBiomeSourceParameterList.Preset.SourceProvider multinoisebiomesourceparameterlist_preset_sourceprovider = preset.provider;

        Objects.requireNonNull(biomes);
        this.parameters = multinoisebiomesourceparameterlist_preset_sourceprovider.<Holder<Biome>>apply(biomes::getOrThrow);
    }

    public Climate.ParameterList<Holder<Biome>> parameters() {
        return this.parameters;
    }

    public static Map<MultiNoiseBiomeSourceParameterList.Preset, Climate.ParameterList<ResourceKey<Biome>>> knownPresets() {
        return (Map) MultiNoiseBiomeSourceParameterList.Preset.BY_NAME.values().stream().collect(Collectors.toMap((multinoisebiomesourceparameterlist_preset) -> {
            return multinoisebiomesourceparameterlist_preset;
        }, (multinoisebiomesourceparameterlist_preset) -> {
            return multinoisebiomesourceparameterlist_preset.provider().apply((resourcekey) -> {
                return resourcekey;
            });
        }));
    }

    public static record Preset(Identifier id, MultiNoiseBiomeSourceParameterList.Preset.SourceProvider provider) {

        public static final MultiNoiseBiomeSourceParameterList.Preset NETHER = new MultiNoiseBiomeSourceParameterList.Preset(Identifier.withDefaultNamespace("nether"), new MultiNoiseBiomeSourceParameterList.Preset.SourceProvider() {
            @Override
            public <T> Climate.ParameterList<T> apply(Function<ResourceKey<Biome>, T> lookup) {
                return new Climate.ParameterList<T>(List.of(Pair.of(Climate.parameters(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F), lookup.apply(Biomes.NETHER_WASTES)), Pair.of(Climate.parameters(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F), lookup.apply(Biomes.SOUL_SAND_VALLEY)), Pair.of(Climate.parameters(0.4F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F), lookup.apply(Biomes.CRIMSON_FOREST)), Pair.of(Climate.parameters(0.0F, 0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.375F), lookup.apply(Biomes.WARPED_FOREST)), Pair.of(Climate.parameters(-0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.175F), lookup.apply(Biomes.BASALT_DELTAS))));
            }
        });
        public static final MultiNoiseBiomeSourceParameterList.Preset OVERWORLD = new MultiNoiseBiomeSourceParameterList.Preset(Identifier.withDefaultNamespace("overworld"), new MultiNoiseBiomeSourceParameterList.Preset.SourceProvider() {
            @Override
            public <T> Climate.ParameterList<T> apply(Function<ResourceKey<Biome>, T> lookup) {
                return MultiNoiseBiomeSourceParameterList.Preset.<T>generateOverworldBiomes(lookup);
            }
        });
        private static final Map<Identifier, MultiNoiseBiomeSourceParameterList.Preset> BY_NAME = (Map) Stream.of(MultiNoiseBiomeSourceParameterList.Preset.NETHER, MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD).collect(Collectors.toMap(MultiNoiseBiomeSourceParameterList.Preset::id, (multinoisebiomesourceparameterlist_preset) -> {
            return multinoisebiomesourceparameterlist_preset;
        }));
        public static final Codec<MultiNoiseBiomeSourceParameterList.Preset> CODEC = Identifier.CODEC.flatXmap((identifier) -> {
            return (DataResult) Optional.ofNullable((MultiNoiseBiomeSourceParameterList.Preset) MultiNoiseBiomeSourceParameterList.Preset.BY_NAME.get(identifier)).map(DataResult::success).orElseGet(() -> {
                return DataResult.error(() -> {
                    return "Unknown preset: " + String.valueOf(identifier);
                });
            });
        }, (multinoisebiomesourceparameterlist_preset) -> {
            return DataResult.success(multinoisebiomesourceparameterlist_preset.id);
        });

        private static <T> Climate.ParameterList<T> generateOverworldBiomes(Function<ResourceKey<Biome>, T> lookup) {
            ImmutableList.Builder<Pair<Climate.ParameterPoint, T>> immutablelist_builder = ImmutableList.builder();

            (new OverworldBiomeBuilder()).addBiomes((pair) -> {
                immutablelist_builder.add(pair.mapSecond(lookup));
            });
            return new Climate.ParameterList<T>(immutablelist_builder.build());
        }

        public Stream<ResourceKey<Biome>> usedBiomes() {
            return this.provider.apply((resourcekey) -> {
                return resourcekey;
            }).values().stream().map(Pair::getSecond).distinct();
        }

        @FunctionalInterface
        private interface SourceProvider {

            <T> Climate.ParameterList<T> apply(Function<ResourceKey<Biome>, T> lookup);
        }
    }
}

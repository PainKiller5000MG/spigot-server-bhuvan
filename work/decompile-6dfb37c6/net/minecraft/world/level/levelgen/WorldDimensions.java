package net.minecraft.world.level.levelgen;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.PrimaryLevelData;

public record WorldDimensions(Map<ResourceKey<LevelStem>, LevelStem> dimensions) {

    public static final MapCodec<WorldDimensions> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.unboundedMap(ResourceKey.codec(Registries.LEVEL_STEM), LevelStem.CODEC).fieldOf("dimensions").forGetter(WorldDimensions::dimensions)).apply(instance, instance.stable(WorldDimensions::new));
    });
    private static final Set<ResourceKey<LevelStem>> BUILTIN_ORDER = ImmutableSet.of(LevelStem.OVERWORLD, LevelStem.NETHER, LevelStem.END);
    private static final int VANILLA_DIMENSION_COUNT = WorldDimensions.BUILTIN_ORDER.size();

    public WorldDimensions {
        LevelStem levelstem = (LevelStem) dimensions.get(LevelStem.OVERWORLD);

        if (levelstem == null) {
            throw new IllegalStateException("Overworld settings missing");
        }
    }

    public WorldDimensions(Registry<LevelStem> registry) {
        this((Map) registry.listElements().collect(Collectors.toMap(Holder.Reference::key, Holder.Reference::value)));
    }

    public static Stream<ResourceKey<LevelStem>> keysInOrder(Stream<ResourceKey<LevelStem>> knownKeys) {
        return Stream.concat(WorldDimensions.BUILTIN_ORDER.stream(), knownKeys.filter((resourcekey) -> {
            return !WorldDimensions.BUILTIN_ORDER.contains(resourcekey);
        }));
    }

    public WorldDimensions replaceOverworldGenerator(HolderLookup.Provider registries, ChunkGenerator generator) {
        HolderLookup<DimensionType> holderlookup = registries.lookupOrThrow(Registries.DIMENSION_TYPE);
        Map<ResourceKey<LevelStem>, LevelStem> map = withOverworld(holderlookup, this.dimensions, generator);

        return new WorldDimensions(map);
    }

    public static Map<ResourceKey<LevelStem>, LevelStem> withOverworld(HolderLookup<DimensionType> dimensionTypes, Map<ResourceKey<LevelStem>, LevelStem> dimensions, ChunkGenerator generator) {
        LevelStem levelstem = (LevelStem) dimensions.get(LevelStem.OVERWORLD);
        Holder<DimensionType> holder = (Holder<DimensionType>) (levelstem == null ? dimensionTypes.getOrThrow(BuiltinDimensionTypes.OVERWORLD) : levelstem.type());

        return withOverworld(dimensions, holder, generator);
    }

    public static Map<ResourceKey<LevelStem>, LevelStem> withOverworld(Map<ResourceKey<LevelStem>, LevelStem> dimensions, Holder<DimensionType> type, ChunkGenerator generator) {
        ImmutableMap.Builder<ResourceKey<LevelStem>, LevelStem> immutablemap_builder = ImmutableMap.builder();

        immutablemap_builder.putAll(dimensions);
        immutablemap_builder.put(LevelStem.OVERWORLD, new LevelStem(type, generator));
        return immutablemap_builder.buildKeepingLast();
    }

    public ChunkGenerator overworld() {
        LevelStem levelstem = (LevelStem) this.dimensions.get(LevelStem.OVERWORLD);

        if (levelstem == null) {
            throw new IllegalStateException("Overworld settings missing");
        } else {
            return levelstem.generator();
        }
    }

    public Optional<LevelStem> get(ResourceKey<LevelStem> key) {
        return Optional.ofNullable((LevelStem) this.dimensions.get(key));
    }

    public ImmutableSet<ResourceKey<Level>> levels() {
        return (ImmutableSet) this.dimensions().keySet().stream().map(Registries::levelStemToLevel).collect(ImmutableSet.toImmutableSet());
    }

    public boolean isDebug() {
        return this.overworld() instanceof DebugLevelSource;
    }

    private static PrimaryLevelData.SpecialWorldProperty specialWorldProperty(Registry<LevelStem> registry) {
        return (PrimaryLevelData.SpecialWorldProperty) registry.getOptional(LevelStem.OVERWORLD).map((levelstem) -> {
            ChunkGenerator chunkgenerator = levelstem.generator();

            return chunkgenerator instanceof DebugLevelSource ? PrimaryLevelData.SpecialWorldProperty.DEBUG : (chunkgenerator instanceof FlatLevelSource ? PrimaryLevelData.SpecialWorldProperty.FLAT : PrimaryLevelData.SpecialWorldProperty.NONE);
        }).orElse(PrimaryLevelData.SpecialWorldProperty.NONE);
    }

    private static Lifecycle checkStability(ResourceKey<LevelStem> key, LevelStem dimension) {
        return isVanillaLike(key, dimension) ? Lifecycle.stable() : Lifecycle.experimental();
    }

    private static boolean isVanillaLike(ResourceKey<LevelStem> key, LevelStem dimension) {
        return key == LevelStem.OVERWORLD ? isStableOverworld(dimension) : (key == LevelStem.NETHER ? isStableNether(dimension) : (key == LevelStem.END ? isStableEnd(dimension) : false));
    }

    private static boolean isStableOverworld(LevelStem dimension) {
        Holder<DimensionType> holder = dimension.type();

        if (!holder.is(BuiltinDimensionTypes.OVERWORLD) && !holder.is(BuiltinDimensionTypes.OVERWORLD_CAVES)) {
            return false;
        } else {
            BiomeSource biomesource = dimension.generator().getBiomeSource();

            if (biomesource instanceof MultiNoiseBiomeSource) {
                MultiNoiseBiomeSource multinoisebiomesource = (MultiNoiseBiomeSource) biomesource;

                if (!multinoisebiomesource.stable(MultiNoiseBiomeSourceParameterLists.OVERWORLD)) {
                    return false;
                }
            }

            return true;
        }
    }

    private static boolean isStableNether(LevelStem dimension) {
        boolean flag;

        if (dimension.type().is(BuiltinDimensionTypes.NETHER)) {
            ChunkGenerator chunkgenerator = dimension.generator();

            if (chunkgenerator instanceof NoiseBasedChunkGenerator) {
                NoiseBasedChunkGenerator noisebasedchunkgenerator = (NoiseBasedChunkGenerator) chunkgenerator;

                if (noisebasedchunkgenerator.stable(NoiseGeneratorSettings.NETHER)) {
                    BiomeSource biomesource = noisebasedchunkgenerator.getBiomeSource();

                    if (biomesource instanceof MultiNoiseBiomeSource) {
                        MultiNoiseBiomeSource multinoisebiomesource = (MultiNoiseBiomeSource) biomesource;

                        if (multinoisebiomesource.stable(MultiNoiseBiomeSourceParameterLists.NETHER)) {
                            flag = true;
                            return flag;
                        }
                    }
                }
            }
        }

        flag = false;
        return flag;
    }

    private static boolean isStableEnd(LevelStem dimension) {
        boolean flag;

        if (dimension.type().is(BuiltinDimensionTypes.END)) {
            ChunkGenerator chunkgenerator = dimension.generator();

            if (chunkgenerator instanceof NoiseBasedChunkGenerator) {
                NoiseBasedChunkGenerator noisebasedchunkgenerator = (NoiseBasedChunkGenerator) chunkgenerator;

                if (noisebasedchunkgenerator.stable(NoiseGeneratorSettings.END) && noisebasedchunkgenerator.getBiomeSource() instanceof TheEndBiomeSource) {
                    flag = true;
                    return flag;
                }
            }
        }

        flag = false;
        return flag;
    }

    public WorldDimensions.Complete bake(Registry<LevelStem> baseDimensions) {
        Stream<ResourceKey<LevelStem>> stream = Stream.concat(baseDimensions.registryKeySet().stream(), this.dimensions.keySet().stream()).distinct();
        List<1Entry> list = new ArrayList();

        keysInOrder(stream).forEach((resourcekey) -> {
            baseDimensions.getOptional(resourcekey).or(() -> {
                return Optional.ofNullable((LevelStem)this.dimensions.get(resourcekey));
            }).ifPresent((levelstem) -> {
                record 1Entry(ResourceKey<LevelStem> key, LevelStem value) {

                    private RegistrationInfo registrationInfo() {
                        return new RegistrationInfo(Optional.empty(), WorldDimensions.checkStability(this.key, this.value));
                    }
                }


                list.add(new 1Entry(resourcekey, levelstem));
            });
        });
        Lifecycle lifecycle = list.size() == WorldDimensions.VANILLA_DIMENSION_COUNT ? Lifecycle.stable() : Lifecycle.experimental();
        WritableRegistry<LevelStem> writableregistry = new MappedRegistry<LevelStem>(Registries.LEVEL_STEM, lifecycle);

        list.forEach((1entry) -> {
            writableregistry.register(1entry.key, 1entry.value, 1entry.registrationInfo());
        });
        Registry<LevelStem> registry1 = writableregistry.freeze();
        PrimaryLevelData.SpecialWorldProperty primaryleveldata_specialworldproperty = specialWorldProperty(registry1);

        return new WorldDimensions.Complete(registry1.freeze(), primaryleveldata_specialworldproperty);
    }

    public static record Complete(Registry<LevelStem> dimensions, PrimaryLevelData.SpecialWorldProperty specialWorldProperty) {

        public Lifecycle lifecycle() {
            return this.dimensions.registryLifecycle();
        }

        public RegistryAccess.Frozen dimensionsRegistryAccess() {
            return (new RegistryAccess.ImmutableRegistryAccess(List.of(this.dimensions))).freeze();
        }
    }
}

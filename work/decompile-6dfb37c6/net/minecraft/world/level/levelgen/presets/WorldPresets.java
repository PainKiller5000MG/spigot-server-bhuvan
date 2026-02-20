package net.minecraft.world.level.levelgen.presets;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public class WorldPresets {

    public static final ResourceKey<WorldPreset> NORMAL = register("normal");
    public static final ResourceKey<WorldPreset> FLAT = register("flat");
    public static final ResourceKey<WorldPreset> LARGE_BIOMES = register("large_biomes");
    public static final ResourceKey<WorldPreset> AMPLIFIED = register("amplified");
    public static final ResourceKey<WorldPreset> SINGLE_BIOME_SURFACE = register("single_biome_surface");
    public static final ResourceKey<WorldPreset> DEBUG = register("debug_all_block_states");

    public WorldPresets() {}

    public static void bootstrap(BootstrapContext<WorldPreset> context) {
        (new WorldPresets.Bootstrap(context)).bootstrap();
    }

    private static ResourceKey<WorldPreset> register(String name) {
        return ResourceKey.create(Registries.WORLD_PRESET, Identifier.withDefaultNamespace(name));
    }

    public static Optional<ResourceKey<WorldPreset>> fromSettings(WorldDimensions dimensions) {
        return dimensions.get(LevelStem.OVERWORLD).flatMap((levelstem) -> {
            ChunkGenerator chunkgenerator = levelstem.generator();

            Objects.requireNonNull(chunkgenerator);
            ChunkGenerator chunkgenerator1 = chunkgenerator;
            int i = 0;
            Optional optional;

            //$FF: i->value
            //0->net/minecraft/world/level/levelgen/FlatLevelSource
            //1->net/minecraft/world/level/levelgen/DebugLevelSource
            //2->net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator
            switch (chunkgenerator1.typeSwitch<invokedynamic>(chunkgenerator1, i)) {
                case 0:
                    FlatLevelSource flatlevelsource = (FlatLevelSource)chunkgenerator1;

                    optional = Optional.of(WorldPresets.FLAT);
                    break;
                case 1:
                    DebugLevelSource debuglevelsource = (DebugLevelSource)chunkgenerator1;

                    optional = Optional.of(WorldPresets.DEBUG);
                    break;
                case 2:
                    NoiseBasedChunkGenerator noisebasedchunkgenerator = (NoiseBasedChunkGenerator)chunkgenerator1;

                    optional = Optional.of(WorldPresets.NORMAL);
                    break;
                default:
                    optional = Optional.empty();
            }

            return optional;
        });
    }

    public static WorldDimensions createNormalWorldDimensions(HolderLookup.Provider registries) {
        return ((WorldPreset) registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value()).createWorldDimensions();
    }

    public static LevelStem getNormalOverworld(HolderLookup.Provider registries) {
        return (LevelStem) ((WorldPreset) registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value()).overworld().orElseThrow();
    }

    public static WorldDimensions createFlatWorldDimensions(HolderLookup.Provider registries) {
        return ((WorldPreset) registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value()).createWorldDimensions();
    }

    private static class Bootstrap {

        private final BootstrapContext<WorldPreset> context;
        private final HolderGetter<NoiseGeneratorSettings> noiseSettings;
        private final HolderGetter<Biome> biomes;
        private final HolderGetter<PlacedFeature> placedFeatures;
        private final HolderGetter<StructureSet> structureSets;
        private final HolderGetter<MultiNoiseBiomeSourceParameterList> multiNoiseBiomeSourceParameterLists;
        private final Holder<DimensionType> overworldDimensionType;
        private final LevelStem netherStem;
        private final LevelStem endStem;

        private Bootstrap(BootstrapContext<WorldPreset> context) {
            this.context = context;
            HolderGetter<DimensionType> holdergetter = context.<DimensionType>lookup(Registries.DIMENSION_TYPE);

            this.noiseSettings = context.<NoiseGeneratorSettings>lookup(Registries.NOISE_SETTINGS);
            this.biomes = context.<Biome>lookup(Registries.BIOME);
            this.placedFeatures = context.<PlacedFeature>lookup(Registries.PLACED_FEATURE);
            this.structureSets = context.<StructureSet>lookup(Registries.STRUCTURE_SET);
            this.multiNoiseBiomeSourceParameterLists = context.<MultiNoiseBiomeSourceParameterList>lookup(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
            this.overworldDimensionType = holdergetter.getOrThrow(BuiltinDimensionTypes.OVERWORLD);
            Holder<DimensionType> holder = holdergetter.getOrThrow(BuiltinDimensionTypes.NETHER);
            Holder<NoiseGeneratorSettings> holder1 = this.noiseSettings.getOrThrow(NoiseGeneratorSettings.NETHER);
            Holder.Reference<MultiNoiseBiomeSourceParameterList> holder_reference = this.multiNoiseBiomeSourceParameterLists.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER);

            this.netherStem = new LevelStem(holder, new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(holder_reference), holder1));
            Holder<DimensionType> holder2 = holdergetter.getOrThrow(BuiltinDimensionTypes.END);
            Holder<NoiseGeneratorSettings> holder3 = this.noiseSettings.getOrThrow(NoiseGeneratorSettings.END);

            this.endStem = new LevelStem(holder2, new NoiseBasedChunkGenerator(TheEndBiomeSource.create(this.biomes), holder3));
        }

        private LevelStem makeOverworld(ChunkGenerator generator) {
            return new LevelStem(this.overworldDimensionType, generator);
        }

        private LevelStem makeNoiseBasedOverworld(BiomeSource overworldBiomeSource, Holder<NoiseGeneratorSettings> noiseSettings) {
            return this.makeOverworld(new NoiseBasedChunkGenerator(overworldBiomeSource, noiseSettings));
        }

        private WorldPreset createPresetWithCustomOverworld(LevelStem overworldStem) {
            return new WorldPreset(Map.of(LevelStem.OVERWORLD, overworldStem, LevelStem.NETHER, this.netherStem, LevelStem.END, this.endStem));
        }

        private void registerCustomOverworldPreset(ResourceKey<WorldPreset> debug, LevelStem overworld) {
            this.context.register(debug, this.createPresetWithCustomOverworld(overworld));
        }

        private void registerOverworlds(BiomeSource biomeSource) {
            Holder<NoiseGeneratorSettings> holder = this.noiseSettings.getOrThrow(NoiseGeneratorSettings.OVERWORLD);

            this.registerCustomOverworldPreset(WorldPresets.NORMAL, this.makeNoiseBasedOverworld(biomeSource, holder));
            Holder<NoiseGeneratorSettings> holder1 = this.noiseSettings.getOrThrow(NoiseGeneratorSettings.LARGE_BIOMES);

            this.registerCustomOverworldPreset(WorldPresets.LARGE_BIOMES, this.makeNoiseBasedOverworld(biomeSource, holder1));
            Holder<NoiseGeneratorSettings> holder2 = this.noiseSettings.getOrThrow(NoiseGeneratorSettings.AMPLIFIED);

            this.registerCustomOverworldPreset(WorldPresets.AMPLIFIED, this.makeNoiseBasedOverworld(biomeSource, holder2));
        }

        public void bootstrap() {
            Holder.Reference<MultiNoiseBiomeSourceParameterList> holder_reference = this.multiNoiseBiomeSourceParameterLists.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);

            this.registerOverworlds(MultiNoiseBiomeSource.createFromPreset(holder_reference));
            Holder<NoiseGeneratorSettings> holder = this.noiseSettings.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            Holder.Reference<Biome> holder_reference1 = this.biomes.getOrThrow(Biomes.PLAINS);

            this.registerCustomOverworldPreset(WorldPresets.SINGLE_BIOME_SURFACE, this.makeNoiseBasedOverworld(new FixedBiomeSource(holder_reference1), holder));
            this.registerCustomOverworldPreset(WorldPresets.FLAT, this.makeOverworld(new FlatLevelSource(FlatLevelGeneratorSettings.getDefault(this.biomes, this.structureSets, this.placedFeatures))));
            this.registerCustomOverworldPreset(WorldPresets.DEBUG, this.makeOverworld(new DebugLevelSource(holder_reference1)));
        }
    }
}

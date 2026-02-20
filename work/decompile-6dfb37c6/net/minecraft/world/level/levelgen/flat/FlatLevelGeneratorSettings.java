package net.minecraft.world.level.levelgen.flat;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.MiscOverworldPlacements;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.LayerConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.slf4j.Logger;

public class FlatLevelGeneratorSettings {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<FlatLevelGeneratorSettings> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(RegistryCodecs.homogeneousList(Registries.STRUCTURE_SET).lenientOptionalFieldOf("structure_overrides").forGetter((flatlevelgeneratorsettings) -> {
            return flatlevelgeneratorsettings.structureOverrides;
        }), FlatLayerInfo.CODEC.listOf().fieldOf("layers").forGetter(FlatLevelGeneratorSettings::getLayersInfo), Codec.BOOL.fieldOf("lakes").orElse(false).forGetter((flatlevelgeneratorsettings) -> {
            return flatlevelgeneratorsettings.addLakes;
        }), Codec.BOOL.fieldOf("features").orElse(false).forGetter((flatlevelgeneratorsettings) -> {
            return flatlevelgeneratorsettings.decoration;
        }), Biome.CODEC.lenientOptionalFieldOf("biome").orElseGet(Optional::empty).forGetter((flatlevelgeneratorsettings) -> {
            return Optional.of(flatlevelgeneratorsettings.biome);
        }), RegistryOps.retrieveElement(Biomes.PLAINS), RegistryOps.retrieveElement(MiscOverworldPlacements.LAKE_LAVA_UNDERGROUND), RegistryOps.retrieveElement(MiscOverworldPlacements.LAKE_LAVA_SURFACE)).apply(instance, FlatLevelGeneratorSettings::new);
    }).comapFlatMap(FlatLevelGeneratorSettings::validateHeight, Function.identity()).stable();
    private final Optional<HolderSet<StructureSet>> structureOverrides;
    private final List<FlatLayerInfo> layersInfo;
    private final Holder<Biome> biome;
    private final List<BlockState> layers;
    private boolean voidGen;
    private boolean decoration;
    private boolean addLakes;
    private final List<Holder<PlacedFeature>> lakes;

    private static DataResult<FlatLevelGeneratorSettings> validateHeight(FlatLevelGeneratorSettings settings) {
        int i = settings.layersInfo.stream().mapToInt(FlatLayerInfo::getHeight).sum();

        return i > DimensionType.Y_SIZE ? DataResult.error(() -> {
            return "Sum of layer heights is > " + DimensionType.Y_SIZE;
        }, settings) : DataResult.success(settings);
    }

    private FlatLevelGeneratorSettings(Optional<HolderSet<StructureSet>> structureOverrides, List<FlatLayerInfo> layers, boolean lakes, boolean features, Optional<Holder<Biome>> biome, Holder.Reference<Biome> fallbackBiome, Holder<PlacedFeature> lavaUnderground, Holder<PlacedFeature> lavaSurface) {
        this(structureOverrides, getBiome(biome, fallbackBiome), List.of(lavaUnderground, lavaSurface));
        if (lakes) {
            this.setAddLakes();
        }

        if (features) {
            this.setDecoration();
        }

        this.layersInfo.addAll(layers);
        this.updateLayers();
    }

    private static Holder<Biome> getBiome(Optional<? extends Holder<Biome>> biome, Holder<Biome> fallbackBiome) {
        if (biome.isEmpty()) {
            FlatLevelGeneratorSettings.LOGGER.error("Unknown biome, defaulting to plains");
            return fallbackBiome;
        } else {
            return (Holder) biome.get();
        }
    }

    public FlatLevelGeneratorSettings(Optional<HolderSet<StructureSet>> structureOverrides, Holder<Biome> biome, List<Holder<PlacedFeature>> lakes) {
        this.layersInfo = Lists.newArrayList();
        this.structureOverrides = structureOverrides;
        this.biome = biome;
        this.layers = Lists.newArrayList();
        this.lakes = lakes;
    }

    public FlatLevelGeneratorSettings withBiomeAndLayers(List<FlatLayerInfo> layers, Optional<HolderSet<StructureSet>> structureOverrides, Holder<Biome> biome) {
        FlatLevelGeneratorSettings flatlevelgeneratorsettings = new FlatLevelGeneratorSettings(structureOverrides, biome, this.lakes);

        for (FlatLayerInfo flatlayerinfo : layers) {
            flatlevelgeneratorsettings.layersInfo.add(new FlatLayerInfo(flatlayerinfo.getHeight(), flatlayerinfo.getBlockState().getBlock()));
            flatlevelgeneratorsettings.updateLayers();
        }

        if (this.decoration) {
            flatlevelgeneratorsettings.setDecoration();
        }

        if (this.addLakes) {
            flatlevelgeneratorsettings.setAddLakes();
        }

        return flatlevelgeneratorsettings;
    }

    public void setDecoration() {
        this.decoration = true;
    }

    public void setAddLakes() {
        this.addLakes = true;
    }

    public BiomeGenerationSettings adjustGenerationSettings(Holder<Biome> sourceBiome) {
        if (!sourceBiome.equals(this.biome)) {
            return ((Biome) sourceBiome.value()).getGenerationSettings();
        } else {
            BiomeGenerationSettings biomegenerationsettings = ((Biome) this.getBiome().value()).getGenerationSettings();
            BiomeGenerationSettings.PlainBuilder biomegenerationsettings_plainbuilder = new BiomeGenerationSettings.PlainBuilder();

            if (this.addLakes) {
                for (Holder<PlacedFeature> holder1 : this.lakes) {
                    biomegenerationsettings_plainbuilder.addFeature(GenerationStep.Decoration.LAKES, holder1);
                }
            }

            boolean flag = (!this.voidGen || sourceBiome.is(Biomes.THE_VOID)) && this.decoration;

            if (flag) {
                List<HolderSet<PlacedFeature>> list = biomegenerationsettings.features();

                for (int i = 0; i < list.size(); ++i) {
                    if (i != GenerationStep.Decoration.UNDERGROUND_STRUCTURES.ordinal() && i != GenerationStep.Decoration.SURFACE_STRUCTURES.ordinal() && (!this.addLakes || i != GenerationStep.Decoration.LAKES.ordinal())) {
                        for (Holder<PlacedFeature> holder2 : (HolderSet) list.get(i)) {
                            biomegenerationsettings_plainbuilder.addFeature(i, holder2);
                        }
                    }
                }
            }

            List<BlockState> list1 = this.getLayers();

            for (int j = 0; j < list1.size(); ++j) {
                BlockState blockstate = (BlockState) list1.get(j);

                if (!Heightmap.Types.MOTION_BLOCKING.isOpaque().test(blockstate)) {
                    list1.set(j, (Object) null);
                    biomegenerationsettings_plainbuilder.addFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, PlacementUtils.inlinePlaced(Feature.FILL_LAYER, new LayerConfiguration(j, blockstate)));
                }
            }

            return biomegenerationsettings_plainbuilder.build();
        }
    }

    public Optional<HolderSet<StructureSet>> structureOverrides() {
        return this.structureOverrides;
    }

    public Holder<Biome> getBiome() {
        return this.biome;
    }

    public List<FlatLayerInfo> getLayersInfo() {
        return this.layersInfo;
    }

    public List<BlockState> getLayers() {
        return this.layers;
    }

    public void updateLayers() {
        this.layers.clear();

        for (FlatLayerInfo flatlayerinfo : this.layersInfo) {
            for (int i = 0; i < flatlayerinfo.getHeight(); ++i) {
                this.layers.add(flatlayerinfo.getBlockState());
            }
        }

        this.voidGen = this.layers.stream().allMatch((blockstate) -> {
            return blockstate.is(Blocks.AIR);
        });
    }

    public static FlatLevelGeneratorSettings getDefault(HolderGetter<Biome> biomes, HolderGetter<StructureSet> structureSets, HolderGetter<PlacedFeature> placedFeatures) {
        HolderSet<StructureSet> holderset = HolderSet.direct(structureSets.getOrThrow(BuiltinStructureSets.STRONGHOLDS), structureSets.getOrThrow(BuiltinStructureSets.VILLAGES));
        FlatLevelGeneratorSettings flatlevelgeneratorsettings = new FlatLevelGeneratorSettings(Optional.of(holderset), getDefaultBiome(biomes), createLakesList(placedFeatures));

        flatlevelgeneratorsettings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.BEDROCK));
        flatlevelgeneratorsettings.getLayersInfo().add(new FlatLayerInfo(2, Blocks.DIRT));
        flatlevelgeneratorsettings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));
        flatlevelgeneratorsettings.updateLayers();
        return flatlevelgeneratorsettings;
    }

    public static Holder<Biome> getDefaultBiome(HolderGetter<Biome> biomes) {
        return biomes.getOrThrow(Biomes.PLAINS);
    }

    public static List<Holder<PlacedFeature>> createLakesList(HolderGetter<PlacedFeature> placedFeatures) {
        return List.of(placedFeatures.getOrThrow(MiscOverworldPlacements.LAKE_LAVA_UNDERGROUND), placedFeatures.getOrThrow(MiscOverworldPlacements.LAKE_LAVA_SURFACE));
    }
}

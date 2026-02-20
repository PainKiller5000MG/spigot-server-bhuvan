package net.minecraft.world.level.biome;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.NoiseData;
import net.minecraft.data.worldgen.TerrainProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.BoundedFloatFunction;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouterData;

public final class OverworldBiomeBuilder {

    private static final float VALLEY_SIZE = 0.05F;
    private static final float LOW_START = 0.26666668F;
    public static final float HIGH_START = 0.4F;
    private static final float HIGH_END = 0.93333334F;
    private static final float PEAK_SIZE = 0.1F;
    public static final float PEAK_START = 0.56666666F;
    private static final float PEAK_END = 0.7666667F;
    public static final float NEAR_INLAND_START = -0.11F;
    public static final float MID_INLAND_START = 0.03F;
    public static final float FAR_INLAND_START = 0.3F;
    public static final float EROSION_INDEX_1_START = -0.78F;
    public static final float EROSION_INDEX_2_START = -0.375F;
    private static final float EROSION_DEEP_DARK_DRYNESS_THRESHOLD = -0.225F;
    private static final float DEPTH_DEEP_DARK_DRYNESS_THRESHOLD = 0.9F;
    private final Climate.Parameter FULL_RANGE = Climate.Parameter.span(-1.0F, 1.0F);
    private final Climate.Parameter[] temperatures = new Climate.Parameter[]{Climate.Parameter.span(-1.0F, -0.45F), Climate.Parameter.span(-0.45F, -0.15F), Climate.Parameter.span(-0.15F, 0.2F), Climate.Parameter.span(0.2F, 0.55F), Climate.Parameter.span(0.55F, 1.0F)};
    private final Climate.Parameter[] humidities = new Climate.Parameter[]{Climate.Parameter.span(-1.0F, -0.35F), Climate.Parameter.span(-0.35F, -0.1F), Climate.Parameter.span(-0.1F, 0.1F), Climate.Parameter.span(0.1F, 0.3F), Climate.Parameter.span(0.3F, 1.0F)};
    private final Climate.Parameter[] erosions = new Climate.Parameter[]{Climate.Parameter.span(-1.0F, -0.78F), Climate.Parameter.span(-0.78F, -0.375F), Climate.Parameter.span(-0.375F, -0.2225F), Climate.Parameter.span(-0.2225F, 0.05F), Climate.Parameter.span(0.05F, 0.45F), Climate.Parameter.span(0.45F, 0.55F), Climate.Parameter.span(0.55F, 1.0F)};
    private final Climate.Parameter FROZEN_RANGE;
    private final Climate.Parameter UNFROZEN_RANGE;
    private final Climate.Parameter mushroomFieldsContinentalness;
    private final Climate.Parameter deepOceanContinentalness;
    private final Climate.Parameter oceanContinentalness;
    private final Climate.Parameter coastContinentalness;
    private final Climate.Parameter inlandContinentalness;
    private final Climate.Parameter nearInlandContinentalness;
    private final Climate.Parameter midInlandContinentalness;
    private final Climate.Parameter farInlandContinentalness;
    private final ResourceKey<Biome>[][] OCEANS;
    private final ResourceKey<Biome>[][] MIDDLE_BIOMES;
    private final ResourceKey<Biome>[][] MIDDLE_BIOMES_VARIANT;
    private final ResourceKey<Biome>[][] PLATEAU_BIOMES;
    private final ResourceKey<Biome>[][] PLATEAU_BIOMES_VARIANT;
    private final ResourceKey<Biome>[][] SHATTERED_BIOMES;

    public OverworldBiomeBuilder() {
        this.FROZEN_RANGE = this.temperatures[0];
        this.UNFROZEN_RANGE = Climate.Parameter.span(this.temperatures[1], this.temperatures[4]);
        this.mushroomFieldsContinentalness = Climate.Parameter.span(-1.2F, -1.05F);
        this.deepOceanContinentalness = Climate.Parameter.span(-1.05F, -0.455F);
        this.oceanContinentalness = Climate.Parameter.span(-0.455F, -0.19F);
        this.coastContinentalness = Climate.Parameter.span(-0.19F, -0.11F);
        this.inlandContinentalness = Climate.Parameter.span(-0.11F, 0.55F);
        this.nearInlandContinentalness = Climate.Parameter.span(-0.11F, 0.03F);
        this.midInlandContinentalness = Climate.Parameter.span(0.03F, 0.3F);
        this.farInlandContinentalness = Climate.Parameter.span(0.3F, 1.0F);
        this.OCEANS = new ResourceKey[][]{{Biomes.DEEP_FROZEN_OCEAN, Biomes.DEEP_COLD_OCEAN, Biomes.DEEP_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN, Biomes.WARM_OCEAN}, {Biomes.FROZEN_OCEAN, Biomes.COLD_OCEAN, Biomes.OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.WARM_OCEAN}};
        this.MIDDLE_BIOMES = new ResourceKey[][]{{Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.TAIGA}, {Biomes.PLAINS, Biomes.PLAINS, Biomes.FOREST, Biomes.TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA}, {Biomes.FLOWER_FOREST, Biomes.PLAINS, Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.DARK_FOREST}, {Biomes.SAVANNA, Biomes.SAVANNA, Biomes.FOREST, Biomes.JUNGLE, Biomes.JUNGLE}, {Biomes.DESERT, Biomes.DESERT, Biomes.DESERT, Biomes.DESERT, Biomes.DESERT}};
        this.MIDDLE_BIOMES_VARIANT = new ResourceKey[][]{{Biomes.ICE_SPIKES, null, Biomes.SNOWY_TAIGA, null, null}, {null, null, null, null, Biomes.OLD_GROWTH_PINE_TAIGA}, {Biomes.SUNFLOWER_PLAINS, null, null, Biomes.OLD_GROWTH_BIRCH_FOREST, null}, {null, null, Biomes.PLAINS, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE}, {null, null, null, null, null}};
        this.PLATEAU_BIOMES = new ResourceKey[][]{{Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.SNOWY_TAIGA}, {Biomes.MEADOW, Biomes.MEADOW, Biomes.FOREST, Biomes.TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA}, {Biomes.MEADOW, Biomes.MEADOW, Biomes.MEADOW, Biomes.MEADOW, Biomes.PALE_GARDEN}, {Biomes.SAVANNA_PLATEAU, Biomes.SAVANNA_PLATEAU, Biomes.FOREST, Biomes.FOREST, Biomes.JUNGLE}, {Biomes.BADLANDS, Biomes.BADLANDS, Biomes.BADLANDS, Biomes.WOODED_BADLANDS, Biomes.WOODED_BADLANDS}};
        this.PLATEAU_BIOMES_VARIANT = new ResourceKey[][]{{Biomes.ICE_SPIKES, null, null, null, null}, {Biomes.CHERRY_GROVE, null, Biomes.MEADOW, Biomes.MEADOW, Biomes.OLD_GROWTH_PINE_TAIGA}, {Biomes.CHERRY_GROVE, Biomes.CHERRY_GROVE, Biomes.FOREST, Biomes.BIRCH_FOREST, null}, {null, null, null, null, null}, {Biomes.ERODED_BADLANDS, Biomes.ERODED_BADLANDS, null, null, null}};
        this.SHATTERED_BIOMES = new ResourceKey[][]{{Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_FOREST}, {Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_FOREST}, {Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_FOREST}, {null, null, null, null, null}, {null, null, null, null, null}};
    }

    public List<Climate.ParameterPoint> spawnTarget() {
        Climate.Parameter climate_parameter = Climate.Parameter.point(0.0F);
        float f = 0.16F;

        return List.of(new Climate.ParameterPoint(this.FULL_RANGE, this.FULL_RANGE, Climate.Parameter.span(this.inlandContinentalness, this.FULL_RANGE), this.FULL_RANGE, climate_parameter, Climate.Parameter.span(-1.0F, -0.16F), 0L), new Climate.ParameterPoint(this.FULL_RANGE, this.FULL_RANGE, Climate.Parameter.span(this.inlandContinentalness, this.FULL_RANGE), this.FULL_RANGE, climate_parameter, Climate.Parameter.span(0.16F, 1.0F), 0L));
    }

    protected void addBiomes(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes) {
        if (SharedConstants.debugGenerateSquareTerrainWithoutNoise) {
            this.addDebugBiomes(biomes);
        } else {
            this.addOffCoastBiomes(biomes);
            this.addInlandBiomes(biomes);
            this.addUndergroundBiomes(biomes);
        }
    }

    private void addDebugBiomes(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes) {
        HolderLookup.Provider holderlookup_provider = (new RegistrySetBuilder()).add(Registries.DENSITY_FUNCTION, NoiseRouterData::bootstrap).add(Registries.NOISE, NoiseData::bootstrap).build(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        HolderGetter<DensityFunction> holdergetter = holderlookup_provider.lookupOrThrow(Registries.DENSITY_FUNCTION);
        DensityFunctions.Spline.Coordinate densityfunctions_spline_coordinate = new DensityFunctions.Spline.Coordinate(holdergetter.getOrThrow(NoiseRouterData.CONTINENTS));
        DensityFunctions.Spline.Coordinate densityfunctions_spline_coordinate1 = new DensityFunctions.Spline.Coordinate(holdergetter.getOrThrow(NoiseRouterData.EROSION));
        DensityFunctions.Spline.Coordinate densityfunctions_spline_coordinate2 = new DensityFunctions.Spline.Coordinate(holdergetter.getOrThrow(NoiseRouterData.RIDGES_FOLDED));

        biomes.accept(Pair.of(Climate.parameters(this.FULL_RANGE, this.FULL_RANGE, this.FULL_RANGE, this.FULL_RANGE, Climate.Parameter.point(0.0F), this.FULL_RANGE, 0.01F), Biomes.PLAINS));
        CubicSpline<?, ?> cubicspline = TerrainProvider.buildErosionOffsetSpline(densityfunctions_spline_coordinate1, densityfunctions_spline_coordinate2, -0.15F, 0.0F, 0.0F, 0.1F, 0.0F, -0.03F, false, false, BoundedFloatFunction.IDENTITY);

        if (cubicspline instanceof CubicSpline.Multipoint<?, ?> cubicspline_multipoint) {
            ResourceKey<Biome> resourcekey = Biomes.DESERT;

            for (float f : cubicspline_multipoint.locations()) {
                biomes.accept(Pair.of(Climate.parameters(this.FULL_RANGE, this.FULL_RANGE, this.FULL_RANGE, Climate.Parameter.point(f), Climate.Parameter.point(0.0F), this.FULL_RANGE, 0.0F), resourcekey));
                resourcekey = resourcekey == Biomes.DESERT ? Biomes.BADLANDS : Biomes.DESERT;
            }
        }

        CubicSpline<?, ?> cubicspline1 = TerrainProvider.overworldOffset(densityfunctions_spline_coordinate, densityfunctions_spline_coordinate1, densityfunctions_spline_coordinate2, false);

        if (cubicspline1 instanceof CubicSpline.Multipoint<?, ?> cubicspline_multipoint1) {
            for (float f1 : cubicspline_multipoint1.locations()) {
                biomes.accept(Pair.of(Climate.parameters(this.FULL_RANGE, this.FULL_RANGE, Climate.Parameter.point(f1), this.FULL_RANGE, Climate.Parameter.point(0.0F), this.FULL_RANGE, 0.0F), Biomes.SNOWY_TAIGA));
            }
        }

    }

    private void addOffCoastBiomes(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes) {
        this.addSurfaceBiome(biomes, this.FULL_RANGE, this.FULL_RANGE, this.mushroomFieldsContinentalness, this.FULL_RANGE, this.FULL_RANGE, 0.0F, Biomes.MUSHROOM_FIELDS);

        for (int i = 0; i < this.temperatures.length; ++i) {
            Climate.Parameter climate_parameter = this.temperatures[i];

            this.addSurfaceBiome(biomes, climate_parameter, this.FULL_RANGE, this.deepOceanContinentalness, this.FULL_RANGE, this.FULL_RANGE, 0.0F, this.OCEANS[0][i]);
            this.addSurfaceBiome(biomes, climate_parameter, this.FULL_RANGE, this.oceanContinentalness, this.FULL_RANGE, this.FULL_RANGE, 0.0F, this.OCEANS[1][i]);
        }

    }

    private void addInlandBiomes(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes) {
        this.addMidSlice(biomes, Climate.Parameter.span(-1.0F, -0.93333334F));
        this.addHighSlice(biomes, Climate.Parameter.span(-0.93333334F, -0.7666667F));
        this.addPeaks(biomes, Climate.Parameter.span(-0.7666667F, -0.56666666F));
        this.addHighSlice(biomes, Climate.Parameter.span(-0.56666666F, -0.4F));
        this.addMidSlice(biomes, Climate.Parameter.span(-0.4F, -0.26666668F));
        this.addLowSlice(biomes, Climate.Parameter.span(-0.26666668F, -0.05F));
        this.addValleys(biomes, Climate.Parameter.span(-0.05F, 0.05F));
        this.addLowSlice(biomes, Climate.Parameter.span(0.05F, 0.26666668F));
        this.addMidSlice(biomes, Climate.Parameter.span(0.26666668F, 0.4F));
        this.addHighSlice(biomes, Climate.Parameter.span(0.4F, 0.56666666F));
        this.addPeaks(biomes, Climate.Parameter.span(0.56666666F, 0.7666667F));
        this.addHighSlice(biomes, Climate.Parameter.span(0.7666667F, 0.93333334F));
        this.addMidSlice(biomes, Climate.Parameter.span(0.93333334F, 1.0F));
    }

    private void addPeaks(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes, Climate.Parameter weirdness) {
        for (int i = 0; i < this.temperatures.length; ++i) {
            Climate.Parameter climate_parameter1 = this.temperatures[i];

            for (int j = 0; j < this.humidities.length; ++j) {
                Climate.Parameter climate_parameter2 = this.humidities[j];
                ResourceKey<Biome> resourcekey = this.pickMiddleBiome(i, j, weirdness);
                ResourceKey<Biome> resourcekey1 = this.pickMiddleBiomeOrBadlandsIfHot(i, j, weirdness);
                ResourceKey<Biome> resourcekey2 = this.pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(i, j, weirdness);
                ResourceKey<Biome> resourcekey3 = this.pickPlateauBiome(i, j, weirdness);
                ResourceKey<Biome> resourcekey4 = this.pickShatteredBiome(i, j, weirdness);
                ResourceKey<Biome> resourcekey5 = this.maybePickWindsweptSavannaBiome(i, j, weirdness, resourcekey4);
                ResourceKey<Biome> resourcekey6 = this.pickPeakBiome(i, j, weirdness);

                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness), this.erosions[0], weirdness, 0.0F, resourcekey6);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness), this.erosions[1], weirdness, 0.0F, resourcekey2);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness), this.erosions[1], weirdness, 0.0F, resourcekey6);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness), Climate.Parameter.span(this.erosions[2], this.erosions[3]), weirdness, 0.0F, resourcekey);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness), this.erosions[2], weirdness, 0.0F, resourcekey3);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.midInlandContinentalness, this.erosions[3], weirdness, 0.0F, resourcekey1);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.farInlandContinentalness, this.erosions[3], weirdness, 0.0F, resourcekey3);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness), this.erosions[4], weirdness, 0.0F, resourcekey);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness), this.erosions[5], weirdness, 0.0F, resourcekey5);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness), this.erosions[5], weirdness, 0.0F, resourcekey4);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness), this.erosions[6], weirdness, 0.0F, resourcekey);
            }
        }

    }

    private void addHighSlice(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes, Climate.Parameter weirdness) {
        for (int i = 0; i < this.temperatures.length; ++i) {
            Climate.Parameter climate_parameter1 = this.temperatures[i];

            for (int j = 0; j < this.humidities.length; ++j) {
                Climate.Parameter climate_parameter2 = this.humidities[j];
                ResourceKey<Biome> resourcekey = this.pickMiddleBiome(i, j, weirdness);
                ResourceKey<Biome> resourcekey1 = this.pickMiddleBiomeOrBadlandsIfHot(i, j, weirdness);
                ResourceKey<Biome> resourcekey2 = this.pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(i, j, weirdness);
                ResourceKey<Biome> resourcekey3 = this.pickPlateauBiome(i, j, weirdness);
                ResourceKey<Biome> resourcekey4 = this.pickShatteredBiome(i, j, weirdness);
                ResourceKey<Biome> resourcekey5 = this.maybePickWindsweptSavannaBiome(i, j, weirdness, resourcekey);
                ResourceKey<Biome> resourcekey6 = this.pickSlopeBiome(i, j, weirdness);
                ResourceKey<Biome> resourcekey7 = this.pickPeakBiome(i, j, weirdness);

                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.coastContinentalness, Climate.Parameter.span(this.erosions[0], this.erosions[1]), weirdness, 0.0F, resourcekey);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.nearInlandContinentalness, this.erosions[0], weirdness, 0.0F, resourcekey6);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness), this.erosions[0], weirdness, 0.0F, resourcekey7);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.nearInlandContinentalness, this.erosions[1], weirdness, 0.0F, resourcekey2);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness), this.erosions[1], weirdness, 0.0F, resourcekey6);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness), Climate.Parameter.span(this.erosions[2], this.erosions[3]), weirdness, 0.0F, resourcekey);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness), this.erosions[2], weirdness, 0.0F, resourcekey3);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.midInlandContinentalness, this.erosions[3], weirdness, 0.0F, resourcekey1);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.farInlandContinentalness, this.erosions[3], weirdness, 0.0F, resourcekey3);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness), this.erosions[4], weirdness, 0.0F, resourcekey);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness), this.erosions[5], weirdness, 0.0F, resourcekey5);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness), this.erosions[5], weirdness, 0.0F, resourcekey4);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness), this.erosions[6], weirdness, 0.0F, resourcekey);
            }
        }

    }

    private void addMidSlice(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes, Climate.Parameter weirdness) {
        this.addSurfaceBiome(biomes, this.FULL_RANGE, this.FULL_RANGE, this.coastContinentalness, Climate.Parameter.span(this.erosions[0], this.erosions[2]), weirdness, 0.0F, Biomes.STONY_SHORE);
        this.addSurfaceBiome(biomes, Climate.Parameter.span(this.temperatures[1], this.temperatures[2]), this.FULL_RANGE, Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness), this.erosions[6], weirdness, 0.0F, Biomes.SWAMP);
        this.addSurfaceBiome(biomes, Climate.Parameter.span(this.temperatures[3], this.temperatures[4]), this.FULL_RANGE, Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness), this.erosions[6], weirdness, 0.0F, Biomes.MANGROVE_SWAMP);

        for (int i = 0; i < this.temperatures.length; ++i) {
            Climate.Parameter climate_parameter1 = this.temperatures[i];

            for (int j = 0; j < this.humidities.length; ++j) {
                Climate.Parameter climate_parameter2 = this.humidities[j];
                ResourceKey<Biome> resourcekey = this.pickMiddleBiome(i, j, weirdness);
                ResourceKey<Biome> resourcekey1 = this.pickMiddleBiomeOrBadlandsIfHot(i, j, weirdness);
                ResourceKey<Biome> resourcekey2 = this.pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(i, j, weirdness);
                ResourceKey<Biome> resourcekey3 = this.pickShatteredBiome(i, j, weirdness);
                ResourceKey<Biome> resourcekey4 = this.pickPlateauBiome(i, j, weirdness);
                ResourceKey<Biome> resourcekey5 = this.pickBeachBiome(i, j);
                ResourceKey<Biome> resourcekey6 = this.maybePickWindsweptSavannaBiome(i, j, weirdness, resourcekey);
                ResourceKey<Biome> resourcekey7 = this.pickShatteredCoastBiome(i, j, weirdness);
                ResourceKey<Biome> resourcekey8 = this.pickSlopeBiome(i, j, weirdness);

                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness), this.erosions[0], weirdness, 0.0F, resourcekey8);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.nearInlandContinentalness, this.midInlandContinentalness), this.erosions[1], weirdness, 0.0F, resourcekey2);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.farInlandContinentalness, this.erosions[1], weirdness, 0.0F, i == 0 ? resourcekey8 : resourcekey4);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.nearInlandContinentalness, this.erosions[2], weirdness, 0.0F, resourcekey);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.midInlandContinentalness, this.erosions[2], weirdness, 0.0F, resourcekey1);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.farInlandContinentalness, this.erosions[2], weirdness, 0.0F, resourcekey4);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness), this.erosions[3], weirdness, 0.0F, resourcekey);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness), this.erosions[3], weirdness, 0.0F, resourcekey1);
                if (weirdness.max() < 0L) {
                    this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.coastContinentalness, this.erosions[4], weirdness, 0.0F, resourcekey5);
                    this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness), this.erosions[4], weirdness, 0.0F, resourcekey);
                } else {
                    this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness), this.erosions[4], weirdness, 0.0F, resourcekey);
                }

                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.coastContinentalness, this.erosions[5], weirdness, 0.0F, resourcekey7);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.nearInlandContinentalness, this.erosions[5], weirdness, 0.0F, resourcekey6);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness), this.erosions[5], weirdness, 0.0F, resourcekey3);
                if (weirdness.max() < 0L) {
                    this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.coastContinentalness, this.erosions[6], weirdness, 0.0F, resourcekey5);
                } else {
                    this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.coastContinentalness, this.erosions[6], weirdness, 0.0F, resourcekey);
                }

                if (i == 0) {
                    this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness), this.erosions[6], weirdness, 0.0F, resourcekey);
                }
            }
        }

    }

    private void addLowSlice(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes, Climate.Parameter weirdness) {
        this.addSurfaceBiome(biomes, this.FULL_RANGE, this.FULL_RANGE, this.coastContinentalness, Climate.Parameter.span(this.erosions[0], this.erosions[2]), weirdness, 0.0F, Biomes.STONY_SHORE);
        this.addSurfaceBiome(biomes, Climate.Parameter.span(this.temperatures[1], this.temperatures[2]), this.FULL_RANGE, Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness), this.erosions[6], weirdness, 0.0F, Biomes.SWAMP);
        this.addSurfaceBiome(biomes, Climate.Parameter.span(this.temperatures[3], this.temperatures[4]), this.FULL_RANGE, Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness), this.erosions[6], weirdness, 0.0F, Biomes.MANGROVE_SWAMP);

        for (int i = 0; i < this.temperatures.length; ++i) {
            Climate.Parameter climate_parameter1 = this.temperatures[i];

            for (int j = 0; j < this.humidities.length; ++j) {
                Climate.Parameter climate_parameter2 = this.humidities[j];
                ResourceKey<Biome> resourcekey = this.pickMiddleBiome(i, j, weirdness);
                ResourceKey<Biome> resourcekey1 = this.pickMiddleBiomeOrBadlandsIfHot(i, j, weirdness);
                ResourceKey<Biome> resourcekey2 = this.pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(i, j, weirdness);
                ResourceKey<Biome> resourcekey3 = this.pickBeachBiome(i, j);
                ResourceKey<Biome> resourcekey4 = this.maybePickWindsweptSavannaBiome(i, j, weirdness, resourcekey);
                ResourceKey<Biome> resourcekey5 = this.pickShatteredCoastBiome(i, j, weirdness);

                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.nearInlandContinentalness, Climate.Parameter.span(this.erosions[0], this.erosions[1]), weirdness, 0.0F, resourcekey1);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness), Climate.Parameter.span(this.erosions[0], this.erosions[1]), weirdness, 0.0F, resourcekey2);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.nearInlandContinentalness, Climate.Parameter.span(this.erosions[2], this.erosions[3]), weirdness, 0.0F, resourcekey);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness), Climate.Parameter.span(this.erosions[2], this.erosions[3]), weirdness, 0.0F, resourcekey1);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.coastContinentalness, Climate.Parameter.span(this.erosions[3], this.erosions[4]), weirdness, 0.0F, resourcekey3);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness), this.erosions[4], weirdness, 0.0F, resourcekey);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.coastContinentalness, this.erosions[5], weirdness, 0.0F, resourcekey5);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.nearInlandContinentalness, this.erosions[5], weirdness, 0.0F, resourcekey4);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness), this.erosions[5], weirdness, 0.0F, resourcekey);
                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, this.coastContinentalness, this.erosions[6], weirdness, 0.0F, resourcekey3);
                if (i == 0) {
                    this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness), this.erosions[6], weirdness, 0.0F, resourcekey);
                }
            }
        }

    }

    private void addValleys(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes, Climate.Parameter weirdness) {
        this.addSurfaceBiome(biomes, this.FROZEN_RANGE, this.FULL_RANGE, this.coastContinentalness, Climate.Parameter.span(this.erosions[0], this.erosions[1]), weirdness, 0.0F, weirdness.max() < 0L ? Biomes.STONY_SHORE : Biomes.FROZEN_RIVER);
        this.addSurfaceBiome(biomes, this.UNFROZEN_RANGE, this.FULL_RANGE, this.coastContinentalness, Climate.Parameter.span(this.erosions[0], this.erosions[1]), weirdness, 0.0F, weirdness.max() < 0L ? Biomes.STONY_SHORE : Biomes.RIVER);
        this.addSurfaceBiome(biomes, this.FROZEN_RANGE, this.FULL_RANGE, this.nearInlandContinentalness, Climate.Parameter.span(this.erosions[0], this.erosions[1]), weirdness, 0.0F, Biomes.FROZEN_RIVER);
        this.addSurfaceBiome(biomes, this.UNFROZEN_RANGE, this.FULL_RANGE, this.nearInlandContinentalness, Climate.Parameter.span(this.erosions[0], this.erosions[1]), weirdness, 0.0F, Biomes.RIVER);
        this.addSurfaceBiome(biomes, this.FROZEN_RANGE, this.FULL_RANGE, Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness), Climate.Parameter.span(this.erosions[2], this.erosions[5]), weirdness, 0.0F, Biomes.FROZEN_RIVER);
        this.addSurfaceBiome(biomes, this.UNFROZEN_RANGE, this.FULL_RANGE, Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness), Climate.Parameter.span(this.erosions[2], this.erosions[5]), weirdness, 0.0F, Biomes.RIVER);
        this.addSurfaceBiome(biomes, this.FROZEN_RANGE, this.FULL_RANGE, this.coastContinentalness, this.erosions[6], weirdness, 0.0F, Biomes.FROZEN_RIVER);
        this.addSurfaceBiome(biomes, this.UNFROZEN_RANGE, this.FULL_RANGE, this.coastContinentalness, this.erosions[6], weirdness, 0.0F, Biomes.RIVER);
        this.addSurfaceBiome(biomes, Climate.Parameter.span(this.temperatures[1], this.temperatures[2]), this.FULL_RANGE, Climate.Parameter.span(this.inlandContinentalness, this.farInlandContinentalness), this.erosions[6], weirdness, 0.0F, Biomes.SWAMP);
        this.addSurfaceBiome(biomes, Climate.Parameter.span(this.temperatures[3], this.temperatures[4]), this.FULL_RANGE, Climate.Parameter.span(this.inlandContinentalness, this.farInlandContinentalness), this.erosions[6], weirdness, 0.0F, Biomes.MANGROVE_SWAMP);
        this.addSurfaceBiome(biomes, this.FROZEN_RANGE, this.FULL_RANGE, Climate.Parameter.span(this.inlandContinentalness, this.farInlandContinentalness), this.erosions[6], weirdness, 0.0F, Biomes.FROZEN_RIVER);

        for (int i = 0; i < this.temperatures.length; ++i) {
            Climate.Parameter climate_parameter1 = this.temperatures[i];

            for (int j = 0; j < this.humidities.length; ++j) {
                Climate.Parameter climate_parameter2 = this.humidities[j];
                ResourceKey<Biome> resourcekey = this.pickMiddleBiomeOrBadlandsIfHot(i, j, weirdness);

                this.addSurfaceBiome(biomes, climate_parameter1, climate_parameter2, Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness), Climate.Parameter.span(this.erosions[0], this.erosions[1]), weirdness, 0.0F, resourcekey);
            }
        }

    }

    private void addUndergroundBiomes(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes) {
        this.addUndergroundBiome(biomes, this.FULL_RANGE, this.FULL_RANGE, Climate.Parameter.span(0.8F, 1.0F), this.FULL_RANGE, this.FULL_RANGE, 0.0F, Biomes.DRIPSTONE_CAVES);
        this.addUndergroundBiome(biomes, this.FULL_RANGE, Climate.Parameter.span(0.7F, 1.0F), this.FULL_RANGE, this.FULL_RANGE, this.FULL_RANGE, 0.0F, Biomes.LUSH_CAVES);
        this.addBottomBiome(biomes, this.FULL_RANGE, this.FULL_RANGE, this.FULL_RANGE, Climate.Parameter.span(this.erosions[0], this.erosions[1]), this.FULL_RANGE, 0.0F, Biomes.DEEP_DARK);
    }

    private ResourceKey<Biome> pickMiddleBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
        if (weirdness.max() < 0L) {
            return this.MIDDLE_BIOMES[temperatureIndex][humidityIndex];
        } else {
            ResourceKey<Biome> resourcekey = this.MIDDLE_BIOMES_VARIANT[temperatureIndex][humidityIndex];

            return resourcekey == null ? this.MIDDLE_BIOMES[temperatureIndex][humidityIndex] : resourcekey;
        }
    }

    private ResourceKey<Biome> pickMiddleBiomeOrBadlandsIfHot(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
        return temperatureIndex == 4 ? this.pickBadlandsBiome(humidityIndex, weirdness) : this.pickMiddleBiome(temperatureIndex, humidityIndex, weirdness);
    }

    private ResourceKey<Biome> pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
        return temperatureIndex == 0 ? this.pickSlopeBiome(temperatureIndex, humidityIndex, weirdness) : this.pickMiddleBiomeOrBadlandsIfHot(temperatureIndex, humidityIndex, weirdness);
    }

    private ResourceKey<Biome> maybePickWindsweptSavannaBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness, ResourceKey<Biome> underlyingBiome) {
        return temperatureIndex > 1 && humidityIndex < 4 && weirdness.max() >= 0L ? Biomes.WINDSWEPT_SAVANNA : underlyingBiome;
    }

    private ResourceKey<Biome> pickShatteredCoastBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
        ResourceKey<Biome> resourcekey = weirdness.max() >= 0L ? this.pickMiddleBiome(temperatureIndex, humidityIndex, weirdness) : this.pickBeachBiome(temperatureIndex, humidityIndex);

        return this.maybePickWindsweptSavannaBiome(temperatureIndex, humidityIndex, weirdness, resourcekey);
    }

    private ResourceKey<Biome> pickBeachBiome(int temperatureIndex, int humidityIndex) {
        return temperatureIndex == 0 ? Biomes.SNOWY_BEACH : (temperatureIndex == 4 ? Biomes.DESERT : Biomes.BEACH);
    }

    private ResourceKey<Biome> pickBadlandsBiome(int humidityIndex, Climate.Parameter weirdness) {
        return humidityIndex < 2 ? (weirdness.max() < 0L ? Biomes.BADLANDS : Biomes.ERODED_BADLANDS) : (humidityIndex < 3 ? Biomes.BADLANDS : Biomes.WOODED_BADLANDS);
    }

    private ResourceKey<Biome> pickPlateauBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
        if (weirdness.max() >= 0L) {
            ResourceKey<Biome> resourcekey = this.PLATEAU_BIOMES_VARIANT[temperatureIndex][humidityIndex];

            if (resourcekey != null) {
                return resourcekey;
            }
        }

        return this.PLATEAU_BIOMES[temperatureIndex][humidityIndex];
    }

    private ResourceKey<Biome> pickPeakBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
        return temperatureIndex <= 2 ? (weirdness.max() < 0L ? Biomes.JAGGED_PEAKS : Biomes.FROZEN_PEAKS) : (temperatureIndex == 3 ? Biomes.STONY_PEAKS : this.pickBadlandsBiome(humidityIndex, weirdness));
    }

    private ResourceKey<Biome> pickSlopeBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
        return temperatureIndex >= 3 ? this.pickPlateauBiome(temperatureIndex, humidityIndex, weirdness) : (humidityIndex <= 1 ? Biomes.SNOWY_SLOPES : Biomes.GROVE);
    }

    private ResourceKey<Biome> pickShatteredBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
        ResourceKey<Biome> resourcekey = this.SHATTERED_BIOMES[temperatureIndex][humidityIndex];

        return resourcekey == null ? this.pickMiddleBiome(temperatureIndex, humidityIndex, weirdness) : resourcekey;
    }

    private void addSurfaceBiome(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes, Climate.Parameter temperature, Climate.Parameter humidity, Climate.Parameter continentalness, Climate.Parameter erosion, Climate.Parameter weirdness, float offset, ResourceKey<Biome> second) {
        biomes.accept(Pair.of(Climate.parameters(temperature, humidity, continentalness, erosion, Climate.Parameter.point(0.0F), weirdness, offset), second));
        biomes.accept(Pair.of(Climate.parameters(temperature, humidity, continentalness, erosion, Climate.Parameter.point(1.0F), weirdness, offset), second));
    }

    private void addUndergroundBiome(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes, Climate.Parameter temperature, Climate.Parameter humidity, Climate.Parameter continentalness, Climate.Parameter erosion, Climate.Parameter weirdness, float offset, ResourceKey<Biome> biome) {
        biomes.accept(Pair.of(Climate.parameters(temperature, humidity, continentalness, erosion, Climate.Parameter.span(0.2F, 0.9F), weirdness, offset), biome));
    }

    private void addBottomBiome(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes, Climate.Parameter temperature, Climate.Parameter humidity, Climate.Parameter continentalness, Climate.Parameter erosion, Climate.Parameter weirdness, float offset, ResourceKey<Biome> biome) {
        biomes.accept(Pair.of(Climate.parameters(temperature, humidity, continentalness, erosion, Climate.Parameter.point(1.1F), weirdness, offset), biome));
    }

    public static boolean isDeepDarkRegion(DensityFunction erosion, DensityFunction depth, DensityFunction.FunctionContext context) {
        return erosion.compute(context) < (double) -0.225F && depth.compute(context) > (double) 0.9F;
    }

    public static String getDebugStringForPeaksAndValleys(double peaksAndValleys) {
        return peaksAndValleys < (double) NoiseRouterData.peaksAndValleys(0.05F) ? "Valley" : (peaksAndValleys < (double) NoiseRouterData.peaksAndValleys(0.26666668F) ? "Low" : (peaksAndValleys < (double) NoiseRouterData.peaksAndValleys(0.4F) ? "Mid" : (peaksAndValleys < (double) NoiseRouterData.peaksAndValleys(0.56666666F) ? "High" : "Peak")));
    }

    public String getDebugStringForContinentalness(double continentalness) {
        double d1 = (double) Climate.quantizeCoord((float) continentalness);

        return d1 < (double) this.mushroomFieldsContinentalness.max() ? "Mushroom fields" : (d1 < (double) this.deepOceanContinentalness.max() ? "Deep ocean" : (d1 < (double) this.oceanContinentalness.max() ? "Ocean" : (d1 < (double) this.coastContinentalness.max() ? "Coast" : (d1 < (double) this.nearInlandContinentalness.max() ? "Near inland" : (d1 < (double) this.midInlandContinentalness.max() ? "Mid inland" : "Far inland")))));
    }

    public String getDebugStringForErosion(double erosion) {
        return getDebugStringForNoiseValue(erosion, this.erosions);
    }

    public String getDebugStringForTemperature(double temperature) {
        return getDebugStringForNoiseValue(temperature, this.temperatures);
    }

    public String getDebugStringForHumidity(double humidity) {
        return getDebugStringForNoiseValue(humidity, this.humidities);
    }

    private static String getDebugStringForNoiseValue(double noiseValue, Climate.Parameter[] array) {
        double d1 = (double) Climate.quantizeCoord((float) noiseValue);

        for (int i = 0; i < array.length; ++i) {
            if (d1 < (double) array[i].max()) {
                return "" + i;
            }
        }

        return "?";
    }

    @VisibleForDebug
    public Climate.Parameter[] getTemperatureThresholds() {
        return this.temperatures;
    }

    @VisibleForDebug
    public Climate.Parameter[] getHumidityThresholds() {
        return this.humidities;
    }

    @VisibleForDebug
    public Climate.Parameter[] getErosionThresholds() {
        return this.erosions;
    }

    @VisibleForDebug
    public Climate.Parameter[] getContinentalnessThresholds() {
        return new Climate.Parameter[]{this.mushroomFieldsContinentalness, this.deepOceanContinentalness, this.oceanContinentalness, this.coastContinentalness, this.nearInlandContinentalness, this.midInlandContinentalness, this.farInlandContinentalness};
    }

    @VisibleForDebug
    public Climate.Parameter[] getPeaksAndValleysThresholds() {
        return new Climate.Parameter[]{Climate.Parameter.span(-2.0F, NoiseRouterData.peaksAndValleys(0.05F)), Climate.Parameter.span(NoiseRouterData.peaksAndValleys(0.05F), NoiseRouterData.peaksAndValleys(0.26666668F)), Climate.Parameter.span(NoiseRouterData.peaksAndValleys(0.26666668F), NoiseRouterData.peaksAndValleys(0.4F)), Climate.Parameter.span(NoiseRouterData.peaksAndValleys(0.4F), NoiseRouterData.peaksAndValleys(0.56666666F)), Climate.Parameter.span(NoiseRouterData.peaksAndValleys(0.56666666F), 2.0F)};
    }

    @VisibleForDebug
    public Climate.Parameter[] getWeirdnessThresholds() {
        return new Climate.Parameter[]{Climate.Parameter.span(-2.0F, 0.0F), Climate.Parameter.span(0.0F, 2.0F)};
    }
}

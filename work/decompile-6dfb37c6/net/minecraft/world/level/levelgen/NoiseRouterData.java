package net.minecraft.world.level.levelgen;

import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.TerrainProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class NoiseRouterData {

    public static final float GLOBAL_OFFSET = -0.50375F;
    private static final float ORE_THICKNESS = 0.08F;
    private static final double VEININESS_FREQUENCY = 1.5D;
    private static final double NOODLE_SPACING_AND_STRAIGHTNESS = 1.5D;
    private static final double SURFACE_DENSITY_THRESHOLD = 1.5625D;
    private static final double CHEESE_NOISE_TARGET = -0.703125D;
    public static final double NOISE_ZERO = 0.390625D;
    public static final int ISLAND_CHUNK_DISTANCE = 64;
    public static final long ISLAND_CHUNK_DISTANCE_SQR = 4096L;
    private static final int DENSITY_Y_ANCHOR_BOTTOM = -64;
    private static final int DENSITY_Y_ANCHOR_TOP = 320;
    private static final double DENSITY_Y_BOTTOM = 1.5D;
    private static final double DENSITY_Y_TOP = -1.5D;
    private static final int OVERWORLD_BOTTOM_SLIDE_HEIGHT = 24;
    private static final double BASE_DENSITY_MULTIPLIER = 4.0D;
    private static final DensityFunction BLENDING_FACTOR = DensityFunctions.constant(10.0D);
    private static final DensityFunction BLENDING_JAGGEDNESS = DensityFunctions.zero();
    private static final ResourceKey<DensityFunction> ZERO = createKey("zero");
    private static final ResourceKey<DensityFunction> Y = createKey("y");
    private static final ResourceKey<DensityFunction> SHIFT_X = createKey("shift_x");
    private static final ResourceKey<DensityFunction> SHIFT_Z = createKey("shift_z");
    private static final ResourceKey<DensityFunction> BASE_3D_NOISE_OVERWORLD = createKey("overworld/base_3d_noise");
    private static final ResourceKey<DensityFunction> BASE_3D_NOISE_NETHER = createKey("nether/base_3d_noise");
    private static final ResourceKey<DensityFunction> BASE_3D_NOISE_END = createKey("end/base_3d_noise");
    public static final ResourceKey<DensityFunction> CONTINENTS = createKey("overworld/continents");
    public static final ResourceKey<DensityFunction> EROSION = createKey("overworld/erosion");
    public static final ResourceKey<DensityFunction> RIDGES = createKey("overworld/ridges");
    public static final ResourceKey<DensityFunction> RIDGES_FOLDED = createKey("overworld/ridges_folded");
    public static final ResourceKey<DensityFunction> OFFSET = createKey("overworld/offset");
    public static final ResourceKey<DensityFunction> FACTOR = createKey("overworld/factor");
    public static final ResourceKey<DensityFunction> JAGGEDNESS = createKey("overworld/jaggedness");
    public static final ResourceKey<DensityFunction> DEPTH = createKey("overworld/depth");
    private static final ResourceKey<DensityFunction> SLOPED_CHEESE = createKey("overworld/sloped_cheese");
    public static final ResourceKey<DensityFunction> CONTINENTS_LARGE = createKey("overworld_large_biomes/continents");
    public static final ResourceKey<DensityFunction> EROSION_LARGE = createKey("overworld_large_biomes/erosion");
    private static final ResourceKey<DensityFunction> OFFSET_LARGE = createKey("overworld_large_biomes/offset");
    private static final ResourceKey<DensityFunction> FACTOR_LARGE = createKey("overworld_large_biomes/factor");
    private static final ResourceKey<DensityFunction> JAGGEDNESS_LARGE = createKey("overworld_large_biomes/jaggedness");
    private static final ResourceKey<DensityFunction> DEPTH_LARGE = createKey("overworld_large_biomes/depth");
    private static final ResourceKey<DensityFunction> SLOPED_CHEESE_LARGE = createKey("overworld_large_biomes/sloped_cheese");
    private static final ResourceKey<DensityFunction> OFFSET_AMPLIFIED = createKey("overworld_amplified/offset");
    private static final ResourceKey<DensityFunction> FACTOR_AMPLIFIED = createKey("overworld_amplified/factor");
    private static final ResourceKey<DensityFunction> JAGGEDNESS_AMPLIFIED = createKey("overworld_amplified/jaggedness");
    private static final ResourceKey<DensityFunction> DEPTH_AMPLIFIED = createKey("overworld_amplified/depth");
    private static final ResourceKey<DensityFunction> SLOPED_CHEESE_AMPLIFIED = createKey("overworld_amplified/sloped_cheese");
    private static final ResourceKey<DensityFunction> SLOPED_CHEESE_END = createKey("end/sloped_cheese");
    private static final ResourceKey<DensityFunction> SPAGHETTI_ROUGHNESS_FUNCTION = createKey("overworld/caves/spaghetti_roughness_function");
    private static final ResourceKey<DensityFunction> ENTRANCES = createKey("overworld/caves/entrances");
    private static final ResourceKey<DensityFunction> NOODLE = createKey("overworld/caves/noodle");
    private static final ResourceKey<DensityFunction> PILLARS = createKey("overworld/caves/pillars");
    private static final ResourceKey<DensityFunction> SPAGHETTI_2D_THICKNESS_MODULATOR = createKey("overworld/caves/spaghetti_2d_thickness_modulator");
    private static final ResourceKey<DensityFunction> SPAGHETTI_2D = createKey("overworld/caves/spaghetti_2d");

    public NoiseRouterData() {}

    private static ResourceKey<DensityFunction> createKey(String name) {
        return ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.withDefaultNamespace(name));
    }

    public static Holder<? extends DensityFunction> bootstrap(BootstrapContext<DensityFunction> context) {
        HolderGetter<NormalNoise.NoiseParameters> holdergetter = context.<NormalNoise.NoiseParameters>lookup(Registries.NOISE);
        HolderGetter<DensityFunction> holdergetter1 = context.<DensityFunction>lookup(Registries.DENSITY_FUNCTION);

        context.register(NoiseRouterData.ZERO, DensityFunctions.zero());
        int i = DimensionType.MIN_Y * 2;
        int j = DimensionType.MAX_Y * 2;

        context.register(NoiseRouterData.Y, DensityFunctions.yClampedGradient(i, j, (double) i, (double) j));
        DensityFunction densityfunction = registerAndWrap(context, NoiseRouterData.SHIFT_X, DensityFunctions.flatCache(DensityFunctions.cache2d(DensityFunctions.shiftA(holdergetter.getOrThrow(Noises.SHIFT)))));
        DensityFunction densityfunction1 = registerAndWrap(context, NoiseRouterData.SHIFT_Z, DensityFunctions.flatCache(DensityFunctions.cache2d(DensityFunctions.shiftB(holdergetter.getOrThrow(Noises.SHIFT)))));

        context.register(NoiseRouterData.BASE_3D_NOISE_OVERWORLD, BlendedNoise.createUnseeded(0.25D, 0.125D, 80.0D, 160.0D, 8.0D));
        context.register(NoiseRouterData.BASE_3D_NOISE_NETHER, BlendedNoise.createUnseeded(0.25D, 0.375D, 80.0D, 60.0D, 8.0D));
        context.register(NoiseRouterData.BASE_3D_NOISE_END, BlendedNoise.createUnseeded(0.25D, 0.25D, 80.0D, 160.0D, 4.0D));
        Holder<DensityFunction> holder = context.register(NoiseRouterData.CONTINENTS, DensityFunctions.flatCache(DensityFunctions.shiftedNoise2d(densityfunction, densityfunction1, 0.25D, holdergetter.getOrThrow(Noises.CONTINENTALNESS))));
        Holder<DensityFunction> holder1 = context.register(NoiseRouterData.EROSION, DensityFunctions.flatCache(DensityFunctions.shiftedNoise2d(densityfunction, densityfunction1, 0.25D, holdergetter.getOrThrow(Noises.EROSION))));
        DensityFunction densityfunction2 = registerAndWrap(context, NoiseRouterData.RIDGES, DensityFunctions.flatCache(DensityFunctions.shiftedNoise2d(densityfunction, densityfunction1, 0.25D, holdergetter.getOrThrow(Noises.RIDGE))));

        context.register(NoiseRouterData.RIDGES_FOLDED, peaksAndValleys(densityfunction2));
        DensityFunction densityfunction3 = DensityFunctions.noise(holdergetter.getOrThrow(Noises.JAGGED), 1500.0D, 0.0D);

        registerTerrainNoises(context, holdergetter1, densityfunction3, holder, holder1, NoiseRouterData.OFFSET, NoiseRouterData.FACTOR, NoiseRouterData.JAGGEDNESS, NoiseRouterData.DEPTH, NoiseRouterData.SLOPED_CHEESE, false);
        Holder<DensityFunction> holder2 = context.register(NoiseRouterData.CONTINENTS_LARGE, DensityFunctions.flatCache(DensityFunctions.shiftedNoise2d(densityfunction, densityfunction1, 0.25D, holdergetter.getOrThrow(Noises.CONTINENTALNESS_LARGE))));
        Holder<DensityFunction> holder3 = context.register(NoiseRouterData.EROSION_LARGE, DensityFunctions.flatCache(DensityFunctions.shiftedNoise2d(densityfunction, densityfunction1, 0.25D, holdergetter.getOrThrow(Noises.EROSION_LARGE))));

        registerTerrainNoises(context, holdergetter1, densityfunction3, holder2, holder3, NoiseRouterData.OFFSET_LARGE, NoiseRouterData.FACTOR_LARGE, NoiseRouterData.JAGGEDNESS_LARGE, NoiseRouterData.DEPTH_LARGE, NoiseRouterData.SLOPED_CHEESE_LARGE, false);
        registerTerrainNoises(context, holdergetter1, densityfunction3, holder, holder1, NoiseRouterData.OFFSET_AMPLIFIED, NoiseRouterData.FACTOR_AMPLIFIED, NoiseRouterData.JAGGEDNESS_AMPLIFIED, NoiseRouterData.DEPTH_AMPLIFIED, NoiseRouterData.SLOPED_CHEESE_AMPLIFIED, true);
        context.register(NoiseRouterData.SLOPED_CHEESE_END, DensityFunctions.add(DensityFunctions.endIslands(0L), getFunction(holdergetter1, NoiseRouterData.BASE_3D_NOISE_END)));
        context.register(NoiseRouterData.SPAGHETTI_ROUGHNESS_FUNCTION, spaghettiRoughnessFunction(holdergetter));
        context.register(NoiseRouterData.SPAGHETTI_2D_THICKNESS_MODULATOR, DensityFunctions.cacheOnce(DensityFunctions.mappedNoise(holdergetter.getOrThrow(Noises.SPAGHETTI_2D_THICKNESS), 2.0D, 1.0D, -0.6D, -1.3D)));
        context.register(NoiseRouterData.SPAGHETTI_2D, spaghetti2D(holdergetter1, holdergetter));
        context.register(NoiseRouterData.ENTRANCES, entrances(holdergetter1, holdergetter));
        context.register(NoiseRouterData.NOODLE, noodle(holdergetter1, holdergetter));
        return context.register(NoiseRouterData.PILLARS, pillars(holdergetter));
    }

    private static void registerTerrainNoises(BootstrapContext<DensityFunction> context, HolderGetter<DensityFunction> functions, DensityFunction jaggedNoise, Holder<DensityFunction> continentsFunction, Holder<DensityFunction> erosionFunction, ResourceKey<DensityFunction> offsetName, ResourceKey<DensityFunction> factorName, ResourceKey<DensityFunction> jaggednessName, ResourceKey<DensityFunction> depthName, ResourceKey<DensityFunction> slopedCheeseName, boolean amplified) {
        DensityFunctions.Spline.Coordinate densityfunctions_spline_coordinate = new DensityFunctions.Spline.Coordinate(continentsFunction);
        DensityFunctions.Spline.Coordinate densityfunctions_spline_coordinate1 = new DensityFunctions.Spline.Coordinate(erosionFunction);
        DensityFunctions.Spline.Coordinate densityfunctions_spline_coordinate2 = new DensityFunctions.Spline.Coordinate(functions.getOrThrow(NoiseRouterData.RIDGES));
        DensityFunctions.Spline.Coordinate densityfunctions_spline_coordinate3 = new DensityFunctions.Spline.Coordinate(functions.getOrThrow(NoiseRouterData.RIDGES_FOLDED));
        DensityFunction densityfunction1 = registerAndWrap(context, offsetName, splineWithBlending(DensityFunctions.add(DensityFunctions.constant((double) -0.50375F), DensityFunctions.spline(TerrainProvider.overworldOffset(densityfunctions_spline_coordinate, densityfunctions_spline_coordinate1, densityfunctions_spline_coordinate3, amplified))), DensityFunctions.blendOffset()));
        DensityFunction densityfunction2 = registerAndWrap(context, factorName, splineWithBlending(DensityFunctions.spline(TerrainProvider.overworldFactor(densityfunctions_spline_coordinate, densityfunctions_spline_coordinate1, densityfunctions_spline_coordinate2, densityfunctions_spline_coordinate3, amplified)), NoiseRouterData.BLENDING_FACTOR));
        DensityFunction densityfunction3 = registerAndWrap(context, depthName, offsetToDepth(densityfunction1));
        DensityFunction densityfunction4 = registerAndWrap(context, jaggednessName, splineWithBlending(DensityFunctions.spline(TerrainProvider.overworldJaggedness(densityfunctions_spline_coordinate, densityfunctions_spline_coordinate1, densityfunctions_spline_coordinate2, densityfunctions_spline_coordinate3, amplified)), NoiseRouterData.BLENDING_JAGGEDNESS));
        DensityFunction densityfunction5 = DensityFunctions.mul(densityfunction4, jaggedNoise.halfNegative());
        DensityFunction densityfunction6 = noiseGradientDensity(densityfunction2, DensityFunctions.add(densityfunction3, densityfunction5));

        context.register(slopedCheeseName, DensityFunctions.add(densityfunction6, getFunction(functions, NoiseRouterData.BASE_3D_NOISE_OVERWORLD)));
    }

    private static DensityFunction offsetToDepth(DensityFunction offset) {
        return DensityFunctions.add(DensityFunctions.yClampedGradient(-64, 320, 1.5D, -1.5D), offset);
    }

    private static DensityFunction registerAndWrap(BootstrapContext<DensityFunction> context, ResourceKey<DensityFunction> name, DensityFunction value) {
        return new DensityFunctions.HolderHolder(context.register(name, value));
    }

    private static DensityFunction getFunction(HolderGetter<DensityFunction> functions, ResourceKey<DensityFunction> name) {
        return new DensityFunctions.HolderHolder(functions.getOrThrow(name));
    }

    private static DensityFunction peaksAndValleys(DensityFunction weirdness) {
        return DensityFunctions.mul(DensityFunctions.add(DensityFunctions.add(weirdness.abs(), DensityFunctions.constant(-0.6666666666666666D)).abs(), DensityFunctions.constant(-0.3333333333333333D)), DensityFunctions.constant(-3.0D));
    }

    public static float peaksAndValleys(float weirdness) {
        return -(Math.abs(Math.abs(weirdness) - 0.6666667F) - 0.33333334F) * 3.0F;
    }

    private static DensityFunction spaghettiRoughnessFunction(HolderGetter<NormalNoise.NoiseParameters> noises) {
        DensityFunction densityfunction = DensityFunctions.noise(noises.getOrThrow(Noises.SPAGHETTI_ROUGHNESS));
        DensityFunction densityfunction1 = DensityFunctions.mappedNoise(noises.getOrThrow(Noises.SPAGHETTI_ROUGHNESS_MODULATOR), 0.0D, -0.1D);

        return DensityFunctions.cacheOnce(DensityFunctions.mul(densityfunction1, DensityFunctions.add(densityfunction.abs(), DensityFunctions.constant(-0.4D))));
    }

    private static DensityFunction entrances(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise.NoiseParameters> noises) {
        DensityFunction densityfunction = DensityFunctions.cacheOnce(DensityFunctions.noise(noises.getOrThrow(Noises.SPAGHETTI_3D_RARITY), 2.0D, 1.0D));
        DensityFunction densityfunction1 = DensityFunctions.mappedNoise(noises.getOrThrow(Noises.SPAGHETTI_3D_THICKNESS), -0.065D, -0.088D);
        DensityFunction densityfunction2 = DensityFunctions.weirdScaledSampler(densityfunction, noises.getOrThrow(Noises.SPAGHETTI_3D_1), DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE1);
        DensityFunction densityfunction3 = DensityFunctions.weirdScaledSampler(densityfunction, noises.getOrThrow(Noises.SPAGHETTI_3D_2), DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE1);
        DensityFunction densityfunction4 = DensityFunctions.add(DensityFunctions.max(densityfunction2, densityfunction3), densityfunction1).clamp(-1.0D, 1.0D);
        DensityFunction densityfunction5 = getFunction(functions, NoiseRouterData.SPAGHETTI_ROUGHNESS_FUNCTION);
        DensityFunction densityfunction6 = DensityFunctions.noise(noises.getOrThrow(Noises.CAVE_ENTRANCE), 0.75D, 0.5D);
        DensityFunction densityfunction7 = DensityFunctions.add(DensityFunctions.add(densityfunction6, DensityFunctions.constant(0.37D)), DensityFunctions.yClampedGradient(-10, 30, 0.3D, 0.0D));

        return DensityFunctions.cacheOnce(DensityFunctions.min(densityfunction7, DensityFunctions.add(densityfunction5, densityfunction4)));
    }

    private static DensityFunction noodle(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise.NoiseParameters> noises) {
        DensityFunction densityfunction = getFunction(functions, NoiseRouterData.Y);
        int i = -64;
        int j = -60;
        int k = 320;
        DensityFunction densityfunction1 = yLimitedInterpolatable(densityfunction, DensityFunctions.noise(noises.getOrThrow(Noises.NOODLE), 1.0D, 1.0D), -60, 320, -1);
        DensityFunction densityfunction2 = yLimitedInterpolatable(densityfunction, DensityFunctions.mappedNoise(noises.getOrThrow(Noises.NOODLE_THICKNESS), 1.0D, 1.0D, -0.05D, -0.1D), -60, 320, 0);
        double d0 = 2.6666666666666665D;
        DensityFunction densityfunction3 = yLimitedInterpolatable(densityfunction, DensityFunctions.noise(noises.getOrThrow(Noises.NOODLE_RIDGE_A), 2.6666666666666665D, 2.6666666666666665D), -60, 320, 0);
        DensityFunction densityfunction4 = yLimitedInterpolatable(densityfunction, DensityFunctions.noise(noises.getOrThrow(Noises.NOODLE_RIDGE_B), 2.6666666666666665D, 2.6666666666666665D), -60, 320, 0);
        DensityFunction densityfunction5 = DensityFunctions.mul(DensityFunctions.constant(1.5D), DensityFunctions.max(densityfunction3.abs(), densityfunction4.abs()));

        return DensityFunctions.rangeChoice(densityfunction1, -1000000.0D, 0.0D, DensityFunctions.constant(64.0D), DensityFunctions.add(densityfunction2, densityfunction5));
    }

    private static DensityFunction pillars(HolderGetter<NormalNoise.NoiseParameters> noises) {
        double d0 = 25.0D;
        double d1 = 0.3D;
        DensityFunction densityfunction = DensityFunctions.noise(noises.getOrThrow(Noises.PILLAR), 25.0D, 0.3D);
        DensityFunction densityfunction1 = DensityFunctions.mappedNoise(noises.getOrThrow(Noises.PILLAR_RARENESS), 0.0D, -2.0D);
        DensityFunction densityfunction2 = DensityFunctions.mappedNoise(noises.getOrThrow(Noises.PILLAR_THICKNESS), 0.0D, 1.1D);
        DensityFunction densityfunction3 = DensityFunctions.add(DensityFunctions.mul(densityfunction, DensityFunctions.constant(2.0D)), densityfunction1);

        return DensityFunctions.cacheOnce(DensityFunctions.mul(densityfunction3, densityfunction2.cube()));
    }

    private static DensityFunction spaghetti2D(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise.NoiseParameters> noises) {
        DensityFunction densityfunction = DensityFunctions.noise(noises.getOrThrow(Noises.SPAGHETTI_2D_MODULATOR), 2.0D, 1.0D);
        DensityFunction densityfunction1 = DensityFunctions.weirdScaledSampler(densityfunction, noises.getOrThrow(Noises.SPAGHETTI_2D), DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE2);
        DensityFunction densityfunction2 = DensityFunctions.mappedNoise(noises.getOrThrow(Noises.SPAGHETTI_2D_ELEVATION), 0.0D, (double) Math.floorDiv(-64, 8), 8.0D);
        DensityFunction densityfunction3 = getFunction(functions, NoiseRouterData.SPAGHETTI_2D_THICKNESS_MODULATOR);
        DensityFunction densityfunction4 = DensityFunctions.add(densityfunction2, DensityFunctions.yClampedGradient(-64, 320, 8.0D, -40.0D)).abs();
        DensityFunction densityfunction5 = DensityFunctions.add(densityfunction4, densityfunction3).cube();
        double d0 = 0.083D;
        DensityFunction densityfunction6 = DensityFunctions.add(densityfunction1, DensityFunctions.mul(DensityFunctions.constant(0.083D), densityfunction3));

        return DensityFunctions.max(densityfunction6, densityfunction5).clamp(-1.0D, 1.0D);
    }

    private static DensityFunction underground(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise.NoiseParameters> noises, DensityFunction slopedCheese) {
        DensityFunction densityfunction1 = getFunction(functions, NoiseRouterData.SPAGHETTI_2D);
        DensityFunction densityfunction2 = getFunction(functions, NoiseRouterData.SPAGHETTI_ROUGHNESS_FUNCTION);
        DensityFunction densityfunction3 = DensityFunctions.noise(noises.getOrThrow(Noises.CAVE_LAYER), 8.0D);
        DensityFunction densityfunction4 = DensityFunctions.mul(DensityFunctions.constant(4.0D), densityfunction3.square());
        DensityFunction densityfunction5 = DensityFunctions.noise(noises.getOrThrow(Noises.CAVE_CHEESE), 0.6666666666666666D);
        DensityFunction densityfunction6 = DensityFunctions.add(DensityFunctions.add(DensityFunctions.constant(0.27D), densityfunction5).clamp(-1.0D, 1.0D), DensityFunctions.add(DensityFunctions.constant(1.5D), DensityFunctions.mul(DensityFunctions.constant(-0.64D), slopedCheese)).clamp(0.0D, 0.5D));
        DensityFunction densityfunction7 = DensityFunctions.add(densityfunction4, densityfunction6);
        DensityFunction densityfunction8 = DensityFunctions.min(DensityFunctions.min(densityfunction7, getFunction(functions, NoiseRouterData.ENTRANCES)), DensityFunctions.add(densityfunction1, densityfunction2));
        DensityFunction densityfunction9 = getFunction(functions, NoiseRouterData.PILLARS);
        DensityFunction densityfunction10 = DensityFunctions.rangeChoice(densityfunction9, -1000000.0D, 0.03D, DensityFunctions.constant(-1000000.0D), densityfunction9);

        return DensityFunctions.max(densityfunction8, densityfunction10);
    }

    private static DensityFunction postProcess(DensityFunction slide) {
        DensityFunction densityfunction1 = DensityFunctions.blendDensity(slide);

        return DensityFunctions.mul(DensityFunctions.interpolated(densityfunction1), DensityFunctions.constant(0.64D)).squeeze();
    }

    private static DensityFunction remap(DensityFunction input, double fromMin, double fromMax, double toMin, double toMax) {
        double d4 = (toMax - toMin) / (fromMax - fromMin);
        double d5 = toMin - fromMin * d4;

        return DensityFunctions.add(DensityFunctions.mul(input, DensityFunctions.constant(d4)), DensityFunctions.constant(d5));
    }

    protected static NoiseRouter overworld(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise.NoiseParameters> noises, boolean largeBiomes, boolean amplified) {
        DensityFunction densityfunction = DensityFunctions.noise(noises.getOrThrow(Noises.AQUIFER_BARRIER), 0.5D);
        DensityFunction densityfunction1 = DensityFunctions.noise(noises.getOrThrow(Noises.AQUIFER_FLUID_LEVEL_FLOODEDNESS), 0.67D);
        DensityFunction densityfunction2 = DensityFunctions.noise(noises.getOrThrow(Noises.AQUIFER_FLUID_LEVEL_SPREAD), 0.7142857142857143D);
        DensityFunction densityfunction3 = DensityFunctions.noise(noises.getOrThrow(Noises.AQUIFER_LAVA));
        DensityFunction densityfunction4 = getFunction(functions, NoiseRouterData.SHIFT_X);
        DensityFunction densityfunction5 = getFunction(functions, NoiseRouterData.SHIFT_Z);
        DensityFunction densityfunction6 = DensityFunctions.shiftedNoise2d(densityfunction4, densityfunction5, 0.25D, noises.getOrThrow(largeBiomes ? Noises.TEMPERATURE_LARGE : Noises.TEMPERATURE));
        DensityFunction densityfunction7 = DensityFunctions.shiftedNoise2d(densityfunction4, densityfunction5, 0.25D, noises.getOrThrow(largeBiomes ? Noises.VEGETATION_LARGE : Noises.VEGETATION));
        DensityFunction densityfunction8 = getFunction(functions, largeBiomes ? NoiseRouterData.OFFSET_LARGE : (amplified ? NoiseRouterData.OFFSET_AMPLIFIED : NoiseRouterData.OFFSET));
        DensityFunction densityfunction9 = getFunction(functions, largeBiomes ? NoiseRouterData.FACTOR_LARGE : (amplified ? NoiseRouterData.FACTOR_AMPLIFIED : NoiseRouterData.FACTOR));
        DensityFunction densityfunction10 = getFunction(functions, largeBiomes ? NoiseRouterData.DEPTH_LARGE : (amplified ? NoiseRouterData.DEPTH_AMPLIFIED : NoiseRouterData.DEPTH));
        DensityFunction densityfunction11 = preliminarySurfaceLevel(densityfunction8, densityfunction9, amplified);
        DensityFunction densityfunction12 = getFunction(functions, largeBiomes ? NoiseRouterData.SLOPED_CHEESE_LARGE : (amplified ? NoiseRouterData.SLOPED_CHEESE_AMPLIFIED : NoiseRouterData.SLOPED_CHEESE));
        DensityFunction densityfunction13 = DensityFunctions.min(densityfunction12, DensityFunctions.mul(DensityFunctions.constant(5.0D), getFunction(functions, NoiseRouterData.ENTRANCES)));
        DensityFunction densityfunction14 = DensityFunctions.rangeChoice(densityfunction12, -1000000.0D, 1.5625D, densityfunction13, underground(functions, noises, densityfunction12));
        DensityFunction densityfunction15 = DensityFunctions.min(postProcess(slideOverworld(amplified, densityfunction14)), getFunction(functions, NoiseRouterData.NOODLE));
        DensityFunction densityfunction16 = getFunction(functions, NoiseRouterData.Y);
        int i = Stream.of(OreVeinifier.VeinType.values()).mapToInt((oreveinifier_veintype) -> {
            return oreveinifier_veintype.minY;
        }).min().orElse(-DimensionType.MIN_Y * 2);
        int j = Stream.of(OreVeinifier.VeinType.values()).mapToInt((oreveinifier_veintype) -> {
            return oreveinifier_veintype.maxY;
        }).max().orElse(-DimensionType.MIN_Y * 2);
        DensityFunction densityfunction17 = yLimitedInterpolatable(densityfunction16, DensityFunctions.noise(noises.getOrThrow(Noises.ORE_VEININESS), 1.5D, 1.5D), i, j, 0);
        float f = 4.0F;
        DensityFunction densityfunction18 = yLimitedInterpolatable(densityfunction16, DensityFunctions.noise(noises.getOrThrow(Noises.ORE_VEIN_A), 4.0D, 4.0D), i, j, 0).abs();
        DensityFunction densityfunction19 = yLimitedInterpolatable(densityfunction16, DensityFunctions.noise(noises.getOrThrow(Noises.ORE_VEIN_B), 4.0D, 4.0D), i, j, 0).abs();
        DensityFunction densityfunction20 = DensityFunctions.add(DensityFunctions.constant((double) -0.08F), DensityFunctions.max(densityfunction18, densityfunction19));
        DensityFunction densityfunction21 = DensityFunctions.noise(noises.getOrThrow(Noises.ORE_GAP));

        return new NoiseRouter(densityfunction, densityfunction1, densityfunction2, densityfunction3, densityfunction6, densityfunction7, getFunction(functions, largeBiomes ? NoiseRouterData.CONTINENTS_LARGE : NoiseRouterData.CONTINENTS), getFunction(functions, largeBiomes ? NoiseRouterData.EROSION_LARGE : NoiseRouterData.EROSION), densityfunction10, getFunction(functions, NoiseRouterData.RIDGES), densityfunction11, densityfunction15, densityfunction17, densityfunction20, densityfunction21);
    }

    private static NoiseRouter noNewCaves(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise.NoiseParameters> noises, DensityFunction slide) {
        DensityFunction densityfunction1 = getFunction(functions, NoiseRouterData.SHIFT_X);
        DensityFunction densityfunction2 = getFunction(functions, NoiseRouterData.SHIFT_Z);
        DensityFunction densityfunction3 = DensityFunctions.shiftedNoise2d(densityfunction1, densityfunction2, 0.25D, noises.getOrThrow(Noises.TEMPERATURE));
        DensityFunction densityfunction4 = DensityFunctions.shiftedNoise2d(densityfunction1, densityfunction2, 0.25D, noises.getOrThrow(Noises.VEGETATION));
        DensityFunction densityfunction5 = postProcess(slide);

        return new NoiseRouter(DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), densityfunction3, densityfunction4, DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), densityfunction5, DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero());
    }

    private static DensityFunction slideOverworld(boolean isAmplified, DensityFunction caves) {
        return slide(caves, -64, 384, isAmplified ? 16 : 80, isAmplified ? 0 : 64, -0.078125D, 0, 24, isAmplified ? 0.4D : 0.1171875D);
    }

    private static DensityFunction slideNetherLike(HolderGetter<DensityFunction> functions, int minY, int height) {
        return slide(getFunction(functions, NoiseRouterData.BASE_3D_NOISE_NETHER), minY, height, 24, 0, 0.9375D, -8, 24, 2.5D);
    }

    private static DensityFunction slideEndLike(DensityFunction caves, int minY, int height) {
        return slide(caves, minY, height, 72, -184, -23.4375D, 4, 32, -0.234375D);
    }

    protected static NoiseRouter nether(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise.NoiseParameters> noises) {
        return noNewCaves(functions, noises, slideNetherLike(functions, 0, 128));
    }

    protected static NoiseRouter caves(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise.NoiseParameters> noises) {
        return noNewCaves(functions, noises, slideNetherLike(functions, -64, 192));
    }

    protected static NoiseRouter floatingIslands(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise.NoiseParameters> noises) {
        return noNewCaves(functions, noises, slideEndLike(getFunction(functions, NoiseRouterData.BASE_3D_NOISE_END), 0, 256));
    }

    private static DensityFunction slideEnd(DensityFunction caves) {
        return slideEndLike(caves, 0, 128);
    }

    protected static NoiseRouter end(HolderGetter<DensityFunction> functions) {
        DensityFunction densityfunction = DensityFunctions.cache2d(DensityFunctions.endIslands(0L));
        DensityFunction densityfunction1 = postProcess(slideEnd(getFunction(functions, NoiseRouterData.SLOPED_CHEESE_END)));

        return new NoiseRouter(DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), densityfunction, DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), densityfunction1, DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero());
    }

    protected static NoiseRouter none() {
        return new NoiseRouter(DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero());
    }

    private static DensityFunction splineWithBlending(DensityFunction spline, DensityFunction blendingTarget) {
        DensityFunction densityfunction2 = DensityFunctions.lerp(DensityFunctions.blendAlpha(), blendingTarget, spline);

        return DensityFunctions.flatCache(DensityFunctions.cache2d(densityfunction2));
    }

    private static DensityFunction noiseGradientDensity(DensityFunction factor, DensityFunction depthWithJaggedness) {
        DensityFunction densityfunction2 = DensityFunctions.mul(depthWithJaggedness, factor);

        return DensityFunctions.mul(DensityFunctions.constant(4.0D), densityfunction2.quarterNegative());
    }

    private static DensityFunction preliminarySurfaceLevel(DensityFunction offset, DensityFunction factor, boolean amplified) {
        DensityFunction densityfunction2 = DensityFunctions.cache2d(factor);
        DensityFunction densityfunction3 = DensityFunctions.cache2d(offset);
        DensityFunction densityfunction4 = remap(DensityFunctions.add(DensityFunctions.mul(DensityFunctions.constant(0.2734375D), densityfunction2.invert()), DensityFunctions.mul(DensityFunctions.constant(-1.0D), densityfunction3)), 1.5D, -1.5D, -64.0D, 320.0D);

        densityfunction4 = densityfunction4.clamp(-40.0D, 320.0D);
        DensityFunction densityfunction5 = DensityFunctions.add(slideOverworld(amplified, DensityFunctions.add(noiseGradientDensity(densityfunction2, offsetToDepth(densityfunction3)), DensityFunctions.constant(-0.703125D)).clamp(-64.0D, 64.0D)), DensityFunctions.constant(-0.390625D));

        return DensityFunctions.findTopSurface(densityfunction5, densityfunction4, -64, NoiseSettings.OVERWORLD_NOISE_SETTINGS.getCellHeight());
    }

    private static DensityFunction yLimitedInterpolatable(DensityFunction y, DensityFunction whenInRange, int minYInclusive, int maxYInclusive, int whenOutOfRange) {
        return DensityFunctions.interpolated(DensityFunctions.rangeChoice(y, (double) minYInclusive, (double) (maxYInclusive + 1), whenInRange, DensityFunctions.constant((double) whenOutOfRange)));
    }

    private static DensityFunction slide(DensityFunction caves, int minY, int height, int topStartY, int topEndY, double topTarget, int bottomStartY, int bottomEndY, double bottomTarget) {
        DensityFunction densityfunction1 = DensityFunctions.yClampedGradient(minY + height - topStartY, minY + height - topEndY, 1.0D, 0.0D);
        DensityFunction densityfunction2 = DensityFunctions.lerp(densityfunction1, topTarget, caves);
        DensityFunction densityfunction3 = DensityFunctions.yClampedGradient(minY + bottomStartY, minY + bottomEndY, 0.0D, 1.0D);

        densityfunction2 = DensityFunctions.lerp(densityfunction3, bottomTarget, densityfunction2);
        return densityfunction2;
    }

    protected static final class QuantizedSpaghettiRarity {

        protected QuantizedSpaghettiRarity() {}

        protected static double getSphaghettiRarity2D(double rarityFactor) {
            return rarityFactor < -0.75D ? 0.5D : (rarityFactor < -0.5D ? 0.75D : (rarityFactor < 0.5D ? 1.0D : (rarityFactor < 0.75D ? 2.0D : 3.0D)));
        }

        protected static double getSpaghettiRarity3D(double rarityFactor) {
            return rarityFactor < -0.5D ? 0.75D : (rarityFactor < 0.0D ? 1.0D : (rarityFactor < 0.5D ? 1.5D : 2.0D));
        }
    }
}

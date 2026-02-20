package net.minecraft.world.level.levelgen;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class RandomState {

    private final PositionalRandomFactory random;
    private final HolderGetter<NormalNoise.NoiseParameters> noises;
    private final NoiseRouter router;
    private final Climate.Sampler sampler;
    private final SurfaceSystem surfaceSystem;
    private final PositionalRandomFactory aquiferRandom;
    private final PositionalRandomFactory oreRandom;
    private final Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise> noiseIntances;
    private final Map<Identifier, PositionalRandomFactory> positionalRandoms;

    public static RandomState create(HolderGetter.Provider holders, ResourceKey<NoiseGeneratorSettings> noiseSettings, long seed) {
        return create((NoiseGeneratorSettings) holders.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(noiseSettings).value(), holders.lookupOrThrow(Registries.NOISE), seed);
    }

    public static RandomState create(NoiseGeneratorSettings settings, HolderGetter<NormalNoise.NoiseParameters> noises, long seed) {
        return new RandomState(settings, noises, seed);
    }

    private RandomState(NoiseGeneratorSettings settings, HolderGetter<NormalNoise.NoiseParameters> noises, final long seed) {
        this.random = settings.getRandomSource().newInstance(seed).forkPositional();
        this.noises = noises;
        this.aquiferRandom = this.random.fromHashOf(Identifier.withDefaultNamespace("aquifer")).forkPositional();
        this.oreRandom = this.random.fromHashOf(Identifier.withDefaultNamespace("ore")).forkPositional();
        this.noiseIntances = new ConcurrentHashMap();
        this.positionalRandoms = new ConcurrentHashMap();
        this.surfaceSystem = new SurfaceSystem(this, settings.defaultBlock(), settings.seaLevel(), this.random);
        final boolean flag = settings.useLegacyRandomSource();

        class 1NoiseWiringHelper implements DensityFunction.Visitor {

            private final Map<DensityFunction, DensityFunction> wrapped = new HashMap();

            _NoiseWiringHelper/* $FF was: 1NoiseWiringHelper*/() {
}

            private RandomSource newLegacyInstance(long seedOffset) {
                return new LegacyRandomSource(seed + seedOffset);
            }

            @Override
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
                Holder<NormalNoise.NoiseParameters> holder = noise.noiseData();

                if (flag) {
                    if (holder.is(Noises.TEMPERATURE)) {
                        NormalNoise normalnoise = NormalNoise.createLegacyNetherBiome(this.newLegacyInstance(0L), new NormalNoise.NoiseParameters(-7, 1.0D, new double[]{1.0D}));

                        return new DensityFunction.NoiseHolder(holder, normalnoise);
                    }

                    if (holder.is(Noises.VEGETATION)) {
                        NormalNoise normalnoise1 = NormalNoise.createLegacyNetherBiome(this.newLegacyInstance(1L), new NormalNoise.NoiseParameters(-7, 1.0D, new double[]{1.0D}));

                        return new DensityFunction.NoiseHolder(holder, normalnoise1);
                    }

                    if (holder.is(Noises.SHIFT)) {
                        NormalNoise normalnoise2 = NormalNoise.create(RandomState.this.random.fromHashOf(Noises.SHIFT.identifier()), new NormalNoise.NoiseParameters(0, 0.0D, new double[0]));

                        return new DensityFunction.NoiseHolder(holder, normalnoise2);
                    }
                }

                NormalNoise normalnoise3 = RandomState.this.getOrCreateNoise((ResourceKey)holder.unwrapKey().orElseThrow());

                return new DensityFunction.NoiseHolder(holder, normalnoise3);
            }

            private DensityFunction wrapNew(DensityFunction function) {
                if (function instanceof BlendedNoise blendednoise) {
                    RandomSource randomsource = flag ? this.newLegacyInstance(0L) : RandomState.this.random.fromHashOf(Identifier.withDefaultNamespace("terrain"));

                    return blendednoise.withNewRandom(randomsource);
                } else {
                    return (DensityFunction)(function instanceof DensityFunctions.EndIslandDensityFunction ? new DensityFunctions.EndIslandDensityFunction(seed) : function);
                }
            }

            @Override
            public DensityFunction apply(DensityFunction function) {
                return (DensityFunction)this.wrapped.computeIfAbsent(function, this::wrapNew);
            }
        }


        this.router = settings.noiseRouter().mapAll(new 1NoiseWiringHelper());
        DensityFunction.Visitor densityfunction_visitor = new DensityFunction.Visitor() {
            private final Map<DensityFunction, DensityFunction> wrapped = new HashMap();

            private DensityFunction wrapNew(DensityFunction function) {
                if (function instanceof DensityFunctions.HolderHolder densityfunctions_holderholder) {
                    return (DensityFunction)densityfunctions_holderholder.function().value();
                } else if (function instanceof DensityFunctions.Marker densityfunctions_marker) {
                    return densityfunctions_marker.wrapped();
                } else {
                    return function;
                }
            }

            @Override
            public DensityFunction apply(DensityFunction input) {
                return (DensityFunction)this.wrapped.computeIfAbsent(input, this::wrapNew);
            }
        };

        this.sampler = new Climate.Sampler(this.router.temperature().mapAll(densityfunction_visitor), this.router.vegetation().mapAll(densityfunction_visitor), this.router.continents().mapAll(densityfunction_visitor), this.router.erosion().mapAll(densityfunction_visitor), this.router.depth().mapAll(densityfunction_visitor), this.router.ridges().mapAll(densityfunction_visitor), settings.spawnTarget());
    }

    public NormalNoise getOrCreateNoise(ResourceKey<NormalNoise.NoiseParameters> noise) {
        return (NormalNoise) this.noiseIntances.computeIfAbsent(noise, (resourcekey1) -> {
            return Noises.instantiate(this.noises, this.random, noise);
        });
    }

    public PositionalRandomFactory getOrCreateRandomFactory(Identifier name) {
        return (PositionalRandomFactory) this.positionalRandoms.computeIfAbsent(name, (identifier1) -> {
            return this.random.fromHashOf(name).forkPositional();
        });
    }

    public NoiseRouter router() {
        return this.router;
    }

    public Climate.Sampler sampler() {
        return this.sampler;
    }

    public SurfaceSystem surfaceSystem() {
        return this.surfaceSystem;
    }

    public PositionalRandomFactory aquiferRandom() {
        return this.aquiferRandom;
    }

    public PositionalRandomFactory oreRandom() {
        return this.oreRandom;
    }
}

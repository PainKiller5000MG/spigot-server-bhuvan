package net.minecraft.world.level.biome;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.levelgen.NoiseRouterData;

public class MultiNoiseBiomeSource extends BiomeSource {

    private static final MapCodec<Holder<Biome>> ENTRY_CODEC = Biome.CODEC.fieldOf("biome");
    public static final MapCodec<Climate.ParameterList<Holder<Biome>>> DIRECT_CODEC = Climate.ParameterList.codec(MultiNoiseBiomeSource.ENTRY_CODEC).fieldOf("biomes");
    private static final MapCodec<Holder<MultiNoiseBiomeSourceParameterList>> PRESET_CODEC = MultiNoiseBiomeSourceParameterList.CODEC.fieldOf("preset").withLifecycle(Lifecycle.stable());
    public static final MapCodec<MultiNoiseBiomeSource> CODEC = Codec.mapEither(MultiNoiseBiomeSource.DIRECT_CODEC, MultiNoiseBiomeSource.PRESET_CODEC).xmap(MultiNoiseBiomeSource::new, (multinoisebiomesource) -> {
        return multinoisebiomesource.parameters;
    });
    private final Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters;

    private MultiNoiseBiomeSource(Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters) {
        this.parameters = parameters;
    }

    public static MultiNoiseBiomeSource createFromList(Climate.ParameterList<Holder<Biome>> parameters) {
        return new MultiNoiseBiomeSource(Either.left(parameters));
    }

    public static MultiNoiseBiomeSource createFromPreset(Holder<MultiNoiseBiomeSourceParameterList> preset) {
        return new MultiNoiseBiomeSource(Either.right(preset));
    }

    private Climate.ParameterList<Holder<Biome>> parameters() {
        return (Climate.ParameterList) this.parameters.map((climate_parameterlist) -> {
            return climate_parameterlist;
        }, (holder) -> {
            return ((MultiNoiseBiomeSourceParameterList) holder.value()).parameters();
        });
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return this.parameters().values().stream().map(Pair::getSecond);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return MultiNoiseBiomeSource.CODEC;
    }

    public boolean stable(ResourceKey<MultiNoiseBiomeSourceParameterList> expected) {
        Optional<Holder<MultiNoiseBiomeSourceParameterList>> optional = this.parameters.right();

        return optional.isPresent() && ((Holder) optional.get()).is(expected);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        return this.getNoiseBiome(sampler.sample(quartX, quartY, quartZ));
    }

    @VisibleForDebug
    public Holder<Biome> getNoiseBiome(Climate.TargetPoint target) {
        return (Holder) this.parameters().findValue(target);
    }

    @Override
    public void addDebugInfo(List<String> result, BlockPos feetPos, Climate.Sampler sampler) {
        int i = QuartPos.fromBlock(feetPos.getX());
        int j = QuartPos.fromBlock(feetPos.getY());
        int k = QuartPos.fromBlock(feetPos.getZ());
        Climate.TargetPoint climate_targetpoint = sampler.sample(i, j, k);
        float f = Climate.unquantizeCoord(climate_targetpoint.continentalness());
        float f1 = Climate.unquantizeCoord(climate_targetpoint.erosion());
        float f2 = Climate.unquantizeCoord(climate_targetpoint.temperature());
        float f3 = Climate.unquantizeCoord(climate_targetpoint.humidity());
        float f4 = Climate.unquantizeCoord(climate_targetpoint.weirdness());
        double d0 = (double) NoiseRouterData.peaksAndValleys(f4);
        OverworldBiomeBuilder overworldbiomebuilder = new OverworldBiomeBuilder();
        String s = OverworldBiomeBuilder.getDebugStringForPeaksAndValleys(d0);

        result.add("Biome builder PV: " + s + " C: " + overworldbiomebuilder.getDebugStringForContinentalness((double) f) + " E: " + overworldbiomebuilder.getDebugStringForErosion((double) f1) + " T: " + overworldbiomebuilder.getDebugStringForTemperature((double) f2) + " H: " + overworldbiomebuilder.getDebugStringForHumidity((double) f3));
    }
}

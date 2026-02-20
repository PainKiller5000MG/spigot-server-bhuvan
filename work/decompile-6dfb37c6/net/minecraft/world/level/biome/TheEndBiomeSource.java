package net.minecraft.world.level.biome;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.levelgen.DensityFunction;

public class TheEndBiomeSource extends BiomeSource {

    public static final MapCodec<TheEndBiomeSource> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(RegistryOps.retrieveElement(Biomes.THE_END), RegistryOps.retrieveElement(Biomes.END_HIGHLANDS), RegistryOps.retrieveElement(Biomes.END_MIDLANDS), RegistryOps.retrieveElement(Biomes.SMALL_END_ISLANDS), RegistryOps.retrieveElement(Biomes.END_BARRENS)).apply(instance, instance.stable(TheEndBiomeSource::new));
    });
    private final Holder<Biome> end;
    private final Holder<Biome> highlands;
    private final Holder<Biome> midlands;
    private final Holder<Biome> islands;
    private final Holder<Biome> barrens;

    public static TheEndBiomeSource create(HolderGetter<Biome> biomes) {
        return new TheEndBiomeSource(biomes.getOrThrow(Biomes.THE_END), biomes.getOrThrow(Biomes.END_HIGHLANDS), biomes.getOrThrow(Biomes.END_MIDLANDS), biomes.getOrThrow(Biomes.SMALL_END_ISLANDS), biomes.getOrThrow(Biomes.END_BARRENS));
    }

    private TheEndBiomeSource(Holder<Biome> end, Holder<Biome> highlands, Holder<Biome> midlands, Holder<Biome> islands, Holder<Biome> barrens) {
        this.end = end;
        this.highlands = highlands;
        this.midlands = midlands;
        this.islands = islands;
        this.barrens = barrens;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(this.end, this.highlands, this.midlands, this.islands, this.barrens);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return TheEndBiomeSource.CODEC;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        int l = QuartPos.toBlock(quartX);
        int i1 = QuartPos.toBlock(quartY);
        int j1 = QuartPos.toBlock(quartZ);
        int k1 = SectionPos.blockToSectionCoord(l);
        int l1 = SectionPos.blockToSectionCoord(j1);

        if ((long) k1 * (long) k1 + (long) l1 * (long) l1 <= 4096L) {
            return this.end;
        } else {
            int i2 = (SectionPos.blockToSectionCoord(l) * 2 + 1) * 8;
            int j2 = (SectionPos.blockToSectionCoord(j1) * 2 + 1) * 8;
            double d0 = sampler.erosion().compute(new DensityFunction.SinglePointContext(i2, i1, j2));

            return d0 > 0.25D ? this.highlands : (d0 >= -0.0625D ? this.midlands : (d0 < -0.21875D ? this.islands : this.barrens));
        }
    }
}

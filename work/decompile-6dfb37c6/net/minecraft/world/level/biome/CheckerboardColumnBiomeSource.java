package net.minecraft.world.level.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;

public class CheckerboardColumnBiomeSource extends BiomeSource {

    public static final MapCodec<CheckerboardColumnBiomeSource> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Biome.LIST_CODEC.fieldOf("biomes").forGetter((checkerboardcolumnbiomesource) -> {
            return checkerboardcolumnbiomesource.allowedBiomes;
        }), Codec.intRange(0, 62).fieldOf("scale").orElse(2).forGetter((checkerboardcolumnbiomesource) -> {
            return checkerboardcolumnbiomesource.size;
        })).apply(instance, CheckerboardColumnBiomeSource::new);
    });
    private final HolderSet<Biome> allowedBiomes;
    private final int bitShift;
    private final int size;

    public CheckerboardColumnBiomeSource(HolderSet<Biome> allowedBiomes, int size) {
        this.allowedBiomes = allowedBiomes;
        this.bitShift = size + 2;
        this.size = size;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return this.allowedBiomes.stream();
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CheckerboardColumnBiomeSource.CODEC;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        return this.allowedBiomes.get(Math.floorMod((quartX >> this.bitShift) + (quartZ >> this.bitShift), this.allowedBiomes.size()));
    }
}

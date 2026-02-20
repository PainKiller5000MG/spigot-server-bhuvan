package net.minecraft.world.level.biome;

import net.minecraft.core.Holder;

public interface BiomeResolver {

    Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler);
}

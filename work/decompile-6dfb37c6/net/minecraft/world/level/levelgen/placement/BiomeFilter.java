package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

public class BiomeFilter extends PlacementFilter {

    private static final BiomeFilter INSTANCE = new BiomeFilter();
    public static MapCodec<BiomeFilter> CODEC = MapCodec.unit(() -> {
        return BiomeFilter.INSTANCE;
    });

    private BiomeFilter() {}

    public static BiomeFilter biome() {
        return BiomeFilter.INSTANCE;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
        PlacedFeature placedfeature = (PlacedFeature) context.topFeature().orElseThrow(() -> {
            return new IllegalStateException("Tried to biome check an unregistered feature, or a feature that should not restrict the biome");
        });
        Holder<Biome> holder = context.getLevel().getBiome(origin);

        return context.generator().getBiomeGenerationSettings(holder).hasFeature(placedfeature);
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.BIOME_FILTER;
    }
}

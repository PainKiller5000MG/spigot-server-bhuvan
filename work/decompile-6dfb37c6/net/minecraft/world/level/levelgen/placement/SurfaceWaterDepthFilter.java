package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

public class SurfaceWaterDepthFilter extends PlacementFilter {

    public static final MapCodec<SurfaceWaterDepthFilter> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.INT.fieldOf("max_water_depth").forGetter((surfacewaterdepthfilter) -> {
            return surfacewaterdepthfilter.maxWaterDepth;
        })).apply(instance, SurfaceWaterDepthFilter::new);
    });
    private final int maxWaterDepth;

    private SurfaceWaterDepthFilter(int maxWaterDepth) {
        this.maxWaterDepth = maxWaterDepth;
    }

    public static SurfaceWaterDepthFilter forMaxDepth(int maxWaterDepth) {
        return new SurfaceWaterDepthFilter(maxWaterDepth);
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
        int i = context.getHeight(Heightmap.Types.OCEAN_FLOOR, origin.getX(), origin.getZ());
        int j = context.getHeight(Heightmap.Types.WORLD_SURFACE, origin.getX(), origin.getZ());

        return j - i <= this.maxWaterDepth;
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.SURFACE_WATER_DEPTH_FILTER;
    }
}

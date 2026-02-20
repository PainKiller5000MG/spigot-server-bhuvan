package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

public class SurfaceRelativeThresholdFilter extends PlacementFilter {

    public static final MapCodec<SurfaceRelativeThresholdFilter> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Heightmap.Types.CODEC.fieldOf("heightmap").forGetter((surfacerelativethresholdfilter) -> {
            return surfacerelativethresholdfilter.heightmap;
        }), Codec.INT.optionalFieldOf("min_inclusive", Integer.MIN_VALUE).forGetter((surfacerelativethresholdfilter) -> {
            return surfacerelativethresholdfilter.minInclusive;
        }), Codec.INT.optionalFieldOf("max_inclusive", Integer.MAX_VALUE).forGetter((surfacerelativethresholdfilter) -> {
            return surfacerelativethresholdfilter.maxInclusive;
        })).apply(instance, SurfaceRelativeThresholdFilter::new);
    });
    private final Heightmap.Types heightmap;
    private final int minInclusive;
    private final int maxInclusive;

    private SurfaceRelativeThresholdFilter(Heightmap.Types heightmap, int minInclusive, int maxInclusive) {
        this.heightmap = heightmap;
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    public static SurfaceRelativeThresholdFilter of(Heightmap.Types heightmap, int minInclusive, int maxInclusive) {
        return new SurfaceRelativeThresholdFilter(heightmap, minInclusive, maxInclusive);
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
        long i = (long) context.getHeight(this.heightmap, origin.getX(), origin.getZ());
        long j = i + (long) this.minInclusive;
        long k = i + (long) this.maxInclusive;

        return j <= (long) origin.getY() && (long) origin.getY() <= k;
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.SURFACE_RELATIVE_THRESHOLD_FILTER;
    }
}

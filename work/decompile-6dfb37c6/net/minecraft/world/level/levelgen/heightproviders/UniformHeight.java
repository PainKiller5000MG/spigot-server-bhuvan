package net.minecraft.world.level.levelgen.heightproviders;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.slf4j.Logger;

public class UniformHeight extends HeightProvider {

    public static final MapCodec<UniformHeight> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(VerticalAnchor.CODEC.fieldOf("min_inclusive").forGetter((uniformheight) -> {
            return uniformheight.minInclusive;
        }), VerticalAnchor.CODEC.fieldOf("max_inclusive").forGetter((uniformheight) -> {
            return uniformheight.maxInclusive;
        })).apply(instance, UniformHeight::new);
    });
    private static final Logger LOGGER = LogUtils.getLogger();
    private final VerticalAnchor minInclusive;
    private final VerticalAnchor maxInclusive;
    private final LongSet warnedFor = new LongOpenHashSet();

    private UniformHeight(VerticalAnchor minInclusive, VerticalAnchor maxInclusive) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    public static UniformHeight of(VerticalAnchor minInclusive, VerticalAnchor maxInclusive) {
        return new UniformHeight(minInclusive, maxInclusive);
    }

    @Override
    public int sample(RandomSource random, WorldGenerationContext context) {
        int i = this.minInclusive.resolveY(context);
        int j = this.maxInclusive.resolveY(context);

        if (i > j) {
            if (this.warnedFor.add((long) i << 32 | (long) j)) {
                UniformHeight.LOGGER.warn("Empty height range: {}", this);
            }

            return i;
        } else {
            return Mth.randomBetweenInclusive(random, i, j);
        }
    }

    @Override
    public HeightProviderType<?> getType() {
        return HeightProviderType.UNIFORM;
    }

    public String toString() {
        String s = String.valueOf(this.minInclusive);

        return "[" + s + "-" + String.valueOf(this.maxInclusive) + "]";
    }
}

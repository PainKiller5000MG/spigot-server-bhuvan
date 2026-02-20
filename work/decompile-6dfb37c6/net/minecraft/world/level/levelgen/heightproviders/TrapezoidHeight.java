package net.minecraft.world.level.levelgen.heightproviders;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.slf4j.Logger;

public class TrapezoidHeight extends HeightProvider {

    public static final MapCodec<TrapezoidHeight> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(VerticalAnchor.CODEC.fieldOf("min_inclusive").forGetter((trapezoidheight) -> {
            return trapezoidheight.minInclusive;
        }), VerticalAnchor.CODEC.fieldOf("max_inclusive").forGetter((trapezoidheight) -> {
            return trapezoidheight.maxInclusive;
        }), Codec.INT.optionalFieldOf("plateau", 0).forGetter((trapezoidheight) -> {
            return trapezoidheight.plateau;
        })).apply(instance, TrapezoidHeight::new);
    });
    private static final Logger LOGGER = LogUtils.getLogger();
    private final VerticalAnchor minInclusive;
    private final VerticalAnchor maxInclusive;
    private final int plateau;

    private TrapezoidHeight(VerticalAnchor minInclusive, VerticalAnchor maxInclusive, int plateau) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
        this.plateau = plateau;
    }

    public static TrapezoidHeight of(VerticalAnchor minInclusive, VerticalAnchor maxInclusive, int plateau) {
        return new TrapezoidHeight(minInclusive, maxInclusive, plateau);
    }

    public static TrapezoidHeight of(VerticalAnchor minInclusive, VerticalAnchor maxInclusive) {
        return of(minInclusive, maxInclusive, 0);
    }

    @Override
    public int sample(RandomSource random, WorldGenerationContext context) {
        int i = this.minInclusive.resolveY(context);
        int j = this.maxInclusive.resolveY(context);

        if (i > j) {
            TrapezoidHeight.LOGGER.warn("Empty height range: {}", this);
            return i;
        } else {
            int k = j - i;

            if (this.plateau >= k) {
                return Mth.randomBetweenInclusive(random, i, j);
            } else {
                int l = (k - this.plateau) / 2;
                int i1 = k - l;

                return i + Mth.randomBetweenInclusive(random, 0, i1) + Mth.randomBetweenInclusive(random, 0, l);
            }
        }
    }

    @Override
    public HeightProviderType<?> getType() {
        return HeightProviderType.TRAPEZOID;
    }

    public String toString() {
        if (this.plateau == 0) {
            String s = String.valueOf(this.minInclusive);

            return "triangle (" + s + "-" + String.valueOf(this.maxInclusive) + ")";
        } else {
            return "trapezoid(" + this.plateau + ") in [" + String.valueOf(this.minInclusive) + "-" + String.valueOf(this.maxInclusive) + "]";
        }
    }
}

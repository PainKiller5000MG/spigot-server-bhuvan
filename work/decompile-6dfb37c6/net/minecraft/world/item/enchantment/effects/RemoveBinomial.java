package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.LevelBasedValue;

public record RemoveBinomial(LevelBasedValue chance) implements EnchantmentValueEffect {

    public static final MapCodec<RemoveBinomial> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(LevelBasedValue.CODEC.fieldOf("chance").forGetter(RemoveBinomial::chance)).apply(instance, RemoveBinomial::new);
    });

    @Override
    public float process(int level, RandomSource random, float n) {
        float f1 = this.chance.calculate(level);
        int j = 0;

        if (n > 128.0F && n * f1 >= 20.0F && n * (1.0F - f1) >= 20.0F) {
            double d0 = Math.floor((double) (n * f1));
            double d1 = Math.sqrt((double) (n * f1 * (1.0F - f1)));

            j = (int) Math.round(d0 + random.nextGaussian() * d1);
            j = Math.clamp((long) j, 0, (int) n);
        } else {
            for (int k = 0; (float) k < n; ++k) {
                if (random.nextFloat() < f1) {
                    ++j;
                }
            }
        }

        return n - (float) j;
    }

    @Override
    public MapCodec<RemoveBinomial> codec() {
        return RemoveBinomial.CODEC;
    }
}

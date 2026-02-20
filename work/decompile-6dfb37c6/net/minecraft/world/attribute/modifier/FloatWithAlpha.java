package net.minecraft.world.attribute.modifier;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record FloatWithAlpha(float value, float alpha) {

    private static final Codec<FloatWithAlpha> FULL_CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Codec.FLOAT.fieldOf("value").forGetter(FloatWithAlpha::value), Codec.floatRange(0.0F, 1.0F).optionalFieldOf("alpha", 1.0F).forGetter(FloatWithAlpha::alpha)).apply(instance, FloatWithAlpha::new);
    });
    public static final Codec<FloatWithAlpha> CODEC = Codec.either(Codec.FLOAT, FloatWithAlpha.FULL_CODEC).xmap((either) -> {
        return (FloatWithAlpha) either.map(FloatWithAlpha::new, (floatwithalpha) -> {
            return floatwithalpha;
        });
    }, (floatwithalpha) -> {
        return floatwithalpha.alpha() == 1.0F ? Either.left(floatwithalpha.value()) : Either.right(floatwithalpha);
    });

    public FloatWithAlpha(float value) {
        this(value, 1.0F);
    }
}

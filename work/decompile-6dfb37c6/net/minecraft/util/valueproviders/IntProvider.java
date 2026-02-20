package net.minecraft.util.valueproviders;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;

public abstract class IntProvider {

    private static final Codec<Either<Integer, IntProvider>> CONSTANT_OR_DISPATCH_CODEC = Codec.either(Codec.INT, BuiltInRegistries.INT_PROVIDER_TYPE.byNameCodec().dispatch(IntProvider::getType, IntProviderType::codec));
    public static final Codec<IntProvider> CODEC = IntProvider.CONSTANT_OR_DISPATCH_CODEC.xmap((either) -> {
        return (IntProvider) either.map(ConstantInt::of, (intprovider) -> {
            return intprovider;
        });
    }, (intprovider) -> {
        return intprovider.getType() == IntProviderType.CONSTANT ? Either.left(((ConstantInt) intprovider).getValue()) : Either.right(intprovider);
    });
    public static final Codec<IntProvider> NON_NEGATIVE_CODEC = codec(0, Integer.MAX_VALUE);
    public static final Codec<IntProvider> POSITIVE_CODEC = codec(1, Integer.MAX_VALUE);

    public IntProvider() {}

    public static Codec<IntProvider> codec(int minValue, int maxValue) {
        return validateCodec(minValue, maxValue, IntProvider.CODEC);
    }

    public static <T extends IntProvider> Codec<T> validateCodec(int minValue, int maxValue, Codec<T> codec) {
        return codec.validate((intprovider) -> {
            return validate(minValue, maxValue, intprovider);
        });
    }

    private static <T extends IntProvider> DataResult<T> validate(int minValue, int maxValue, T value) {
        return value.getMinValue() < minValue ? DataResult.error(() -> {
            return "Value provider too low: " + minValue + " [" + value.getMinValue() + "-" + value.getMaxValue() + "]";
        }) : (value.getMaxValue() > maxValue ? DataResult.error(() -> {
            return "Value provider too high: " + maxValue + " [" + value.getMinValue() + "-" + value.getMaxValue() + "]";
        }) : DataResult.success(value));
    }

    public abstract int sample(RandomSource random);

    public abstract int getMinValue();

    public abstract int getMaxValue();

    public abstract IntProviderType<?> getType();
}

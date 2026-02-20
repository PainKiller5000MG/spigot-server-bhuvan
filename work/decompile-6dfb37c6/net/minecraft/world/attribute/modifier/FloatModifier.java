package net.minecraft.world.attribute.modifier;

import com.mojang.serialization.Codec;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.LerpFunction;

public interface FloatModifier<Argument> extends AttributeModifier<Float, Argument> {

    FloatModifier<FloatWithAlpha> ALPHA_BLEND = new FloatModifier<FloatWithAlpha>() {
        public Float apply(Float subject, FloatWithAlpha argument) {
            return Mth.lerp(argument.alpha(), subject, argument.value());
        }

        @Override
        public Codec<FloatWithAlpha> argumentCodec(EnvironmentAttribute<Float> type) {
            return FloatWithAlpha.CODEC;
        }

        @Override
        public LerpFunction<FloatWithAlpha> argumentKeyframeLerp(EnvironmentAttribute<Float> type) {
            return (f, floatwithalpha, floatwithalpha1) -> {
                return new FloatWithAlpha(Mth.lerp(f, floatwithalpha.value(), floatwithalpha1.value()), Mth.lerp(f, floatwithalpha.alpha(), floatwithalpha1.alpha()));
            };
        }
    };
    FloatModifier<Float> ADD = Float::sum;
    FloatModifier<Float> SUBTRACT = (FloatModifier.Simple) (ofloat, ofloat1) -> {
        return ofloat - ofloat1;
    };
    FloatModifier<Float> MULTIPLY = (FloatModifier.Simple) (ofloat, ofloat1) -> {
        return ofloat * ofloat1;
    };
    FloatModifier<Float> MINIMUM = Math::min;
    FloatModifier<Float> MAXIMUM = Math::max;

    @FunctionalInterface
    public interface Simple extends FloatModifier<Float> {

        @Override
        default Codec<Float> argumentCodec(EnvironmentAttribute<Float> type) {
            return Codec.FLOAT;
        }

        @Override
        default LerpFunction<Float> argumentKeyframeLerp(EnvironmentAttribute<Float> type) {
            return LerpFunction.ofFloat();
        }
    }
}

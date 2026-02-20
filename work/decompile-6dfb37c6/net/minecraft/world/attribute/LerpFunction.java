package net.minecraft.world.attribute;

import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public interface LerpFunction<T> {

    static LerpFunction<Float> ofFloat() {
        return Mth::lerp;
    }

    static LerpFunction<Float> ofDegrees(float maxDelta) {
        return (f1, ofloat, ofloat1) -> {
            float f2 = Mth.wrapDegrees(ofloat1 - ofloat);

            return Math.abs(f2) >= maxDelta ? ofloat1 : ofloat + f1 * f2;
        };
    }

    static <T> LerpFunction<T> ofConstant() {
        return (f, object, object1) -> {
            return object;
        };
    }

    static <T> LerpFunction<T> ofStep(float threshold) {
        return (f1, object, object1) -> {
            return f1 >= threshold ? object1 : object;
        };
    }

    static LerpFunction<Integer> ofColor() {
        return ARGB::srgbLerp;
    }

    T apply(float alpha, T from, T to);
}

package net.minecraft.util;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

public interface EasingType {

    ExtraCodecs.LateBoundIdMapper<String, EasingType> SIMPLE_REGISTRY = new ExtraCodecs.LateBoundIdMapper<String, EasingType>();
    Codec<EasingType> CODEC = Codec.either(EasingType.SIMPLE_REGISTRY.codec(Codec.STRING), EasingType.CubicBezier.CODEC).xmap(Either::unwrap, (easingtype) -> {
        Either either;

        if (easingtype instanceof EasingType.CubicBezier easingtype_cubicbezier) {
            either = Either.right(easingtype_cubicbezier);
        } else {
            either = Either.left(easingtype);
        }

        return either;
    });
    EasingType CONSTANT = registerSimple("constant", (f) -> {
        return 0.0F;
    });
    EasingType LINEAR = registerSimple("linear", (f) -> {
        return f;
    });
    EasingType IN_BACK = registerSimple("in_back", Ease::inBack);
    EasingType IN_BOUNCE = registerSimple("in_bounce", Ease::inBounce);
    EasingType IN_CIRC = registerSimple("in_circ", Ease::inCirc);
    EasingType IN_CUBIC = registerSimple("in_cubic", Ease::inCubic);
    EasingType IN_ELASTIC = registerSimple("in_elastic", Ease::inElastic);
    EasingType IN_EXPO = registerSimple("in_expo", Ease::inExpo);
    EasingType IN_QUAD = registerSimple("in_quad", Ease::inQuad);
    EasingType IN_QUART = registerSimple("in_quart", Ease::inQuart);
    EasingType IN_QUINT = registerSimple("in_quint", Ease::inQuint);
    EasingType IN_SINE = registerSimple("in_sine", Ease::inSine);
    EasingType IN_OUT_BACK = registerSimple("in_out_back", Ease::inOutBack);
    EasingType IN_OUT_BOUNCE = registerSimple("in_out_bounce", Ease::inOutBounce);
    EasingType IN_OUT_CIRC = registerSimple("in_out_circ", Ease::inOutCirc);
    EasingType IN_OUT_CUBIC = registerSimple("in_out_cubic", Ease::inOutCubic);
    EasingType IN_OUT_ELASTIC = registerSimple("in_out_elastic", Ease::inOutElastic);
    EasingType IN_OUT_EXPO = registerSimple("in_out_expo", Ease::inOutExpo);
    EasingType IN_OUT_QUAD = registerSimple("in_out_quad", Ease::inOutQuad);
    EasingType IN_OUT_QUART = registerSimple("in_out_quart", Ease::inOutQuart);
    EasingType IN_OUT_QUINT = registerSimple("in_out_quint", Ease::inOutQuint);
    EasingType IN_OUT_SINE = registerSimple("in_out_sine", Ease::inOutSine);
    EasingType OUT_BACK = registerSimple("out_back", Ease::outBack);
    EasingType OUT_BOUNCE = registerSimple("out_bounce", Ease::outBounce);
    EasingType OUT_CIRC = registerSimple("out_circ", Ease::outCirc);
    EasingType OUT_CUBIC = registerSimple("out_cubic", Ease::outCubic);
    EasingType OUT_ELASTIC = registerSimple("out_elastic", Ease::outElastic);
    EasingType OUT_EXPO = registerSimple("out_expo", Ease::outExpo);
    EasingType OUT_QUAD = registerSimple("out_quad", Ease::outQuad);
    EasingType OUT_QUART = registerSimple("out_quart", Ease::outQuart);
    EasingType OUT_QUINT = registerSimple("out_quint", Ease::outQuint);
    EasingType OUT_SINE = registerSimple("out_sine", Ease::outSine);

    static EasingType registerSimple(String id, EasingType easing) {
        EasingType.SIMPLE_REGISTRY.put(id, easing);
        return easing;
    }

    static EasingType cubicBezier(float x1, float y1, float x2, float y2) {
        return new EasingType.CubicBezier(new EasingType.CubicBezierControls(x1, y1, x2, y2));
    }

    static EasingType symmetricCubicBezier(float x1, float y1) {
        return cubicBezier(x1, y1, 1.0F - x1, 1.0F - y1);
    }

    float apply(float x);

    public static final class CubicBezier implements EasingType {

        public static final Codec<EasingType.CubicBezier> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(EasingType.CubicBezierControls.CODEC.fieldOf("cubic_bezier").forGetter((easingtype_cubicbezier) -> {
                return easingtype_cubicbezier.controls;
            })).apply(instance, EasingType.CubicBezier::new);
        });
        private static final int NEWTON_RAPHSON_ITERATIONS = 4;
        private final EasingType.CubicBezierControls controls;
        private final EasingType.CubicBezier.CubicCurve xCurve;
        private final EasingType.CubicBezier.CubicCurve yCurve;

        public CubicBezier(EasingType.CubicBezierControls controls) {
            this.controls = controls;
            this.xCurve = curveFromControls(controls.x1, controls.x2);
            this.yCurve = curveFromControls(controls.y1, controls.y2);
        }

        private static EasingType.CubicBezier.CubicCurve curveFromControls(float v1, float v2) {
            return new EasingType.CubicBezier.CubicCurve(3.0F * v1 - 3.0F * v2 + 1.0F, -6.0F * v1 + 3.0F * v2, 3.0F * v1);
        }

        @Override
        public float apply(float x) {
            float f1 = x;

            for (int i = 0; i < 4; ++i) {
                float f2 = this.xCurve.sampleGradient(f1);

                if (f2 < 1.0E-5F) {
                    break;
                }

                float f3 = this.xCurve.sample(f1) - x;

                f1 -= f3 / f2;
            }

            return this.yCurve.sample(f1);
        }

        public boolean equals(Object obj) {
            boolean flag;

            if (obj instanceof EasingType.CubicBezier easingtype_cubicbezier) {
                if (this.controls.equals(easingtype_cubicbezier.controls)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }

        public int hashCode() {
            return this.controls.hashCode();
        }

        public String toString() {
            return "CubicBezier(" + this.controls.x1 + ", " + this.controls.y1 + ", " + this.controls.x2 + ", " + this.controls.y2 + ")";
        }

        private static record CubicCurve(float a, float b, float c) {

            public float sample(float t) {
                return ((this.a * t + this.b) * t + this.c) * t;
            }

            public float sampleGradient(float t) {
                return (3.0F * this.a * t + 2.0F * this.b) * t + this.c;
            }
        }
    }

    public static record CubicBezierControls(float x1, float y1, float x2, float y2) {

        public static final Codec<EasingType.CubicBezierControls> CODEC = Codec.FLOAT.listOf(4, 4).xmap((list) -> {
            return new EasingType.CubicBezierControls((Float) list.get(0), (Float) list.get(1), (Float) list.get(2), (Float) list.get(3));
        }, (easingtype_cubicbeziercontrols) -> {
            return List.of(easingtype_cubicbeziercontrols.x1, easingtype_cubicbeziercontrols.y1, easingtype_cubicbeziercontrols.x2, easingtype_cubicbeziercontrols.y2);
        }).validate(EasingType.CubicBezierControls::validate);

        private DataResult<EasingType.CubicBezierControls> validate() {
            return this.x1 >= 0.0F && this.x1 <= 1.0F ? (this.x2 >= 0.0F && this.x2 <= 1.0F ? DataResult.success(this) : DataResult.error(() -> {
                return "x2 must be in range [0; 1]";
            })) : DataResult.error(() -> {
                return "x1 must be in range [0; 1]";
            });
        }
    }
}

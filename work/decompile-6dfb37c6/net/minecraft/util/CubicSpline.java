package net.minecraft.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.mutable.MutableObject;

public interface CubicSpline<C, I extends BoundedFloatFunction<C>> extends BoundedFloatFunction<C> {

    @VisibleForDebug
    String parityString();

    CubicSpline<C, I> mapAll(CubicSpline.CoordinateVisitor<I> visitor);

    static <C, I extends BoundedFloatFunction<C>> Codec<CubicSpline<C, I>> codec(Codec<I> coordinateCodec) {
        MutableObject<Codec<CubicSpline<C, I>>> mutableobject = new MutableObject();
        Codec<1Point<C, I>> codec1 = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.FLOAT.fieldOf("location").forGetter(1Point::location), Codec.lazyInitialized(mutableobject).fieldOf("value").forGetter(1Point::value), Codec.FLOAT.fieldOf("derivative").forGetter(1Point::derivative)).apply(instance, (f, cubicspline, f1) -> {
                record 1Point<C, I extends BoundedFloatFunction<C>>(float location, CubicSpline<C, I> value, float derivative) {

                }


                return new 1Point(f, cubicspline, f1);
            });
        });
        Codec<CubicSpline.Multipoint<C, I>> codec2 = RecordCodecBuilder.create((instance) -> {
            return instance.group(coordinateCodec.fieldOf("coordinate").forGetter(CubicSpline.Multipoint::coordinate), ExtraCodecs.nonEmptyList(codec1.listOf()).fieldOf("points").forGetter((cubicspline_multipoint) -> {
                return IntStream.range(0, cubicspline_multipoint.locations.length).mapToObj((i) -> {
                    return new 1Point(cubicspline_multipoint.locations()[i], (CubicSpline)cubicspline_multipoint.values().get(i), cubicspline_multipoint.derivatives()[i]);
                }).toList();
            })).apply(instance, (boundedfloatfunction, list) -> {
                float[] afloat = new float[list.size()];
                ImmutableList.Builder<CubicSpline<C, I>> immutablelist_builder = ImmutableList.builder();
                float[] afloat1 = new float[list.size()];

                for(int i = 0; i < list.size(); ++i) {
                    1Point<C, I> 1point = (1Point)list.get(i);

                    afloat[i] = 1point.location();
                    immutablelist_builder.add(1point.value());
                    afloat1[i] = 1point.derivative();
                }

                return CubicSpline.Multipoint.create(boundedfloatfunction, afloat, immutablelist_builder.build(), afloat1);
            });
        });

        mutableobject.setValue(Codec.either(Codec.FLOAT, codec2).xmap((either) -> {
            return (CubicSpline)either.map(CubicSpline.Constant::new, (cubicspline_multipoint) -> {
                return cubicspline_multipoint;
            });
        }, (cubicspline) -> {
            Either either;

            if (cubicspline instanceof CubicSpline.Constant<C, I> cubicspline_constant) {
                either = Either.left(cubicspline_constant.value());
            } else {
                either = Either.right((CubicSpline.Multipoint)cubicspline);
            }

            return either;
        }));
        return (Codec)mutableobject.get();
    }

    static <C, I extends BoundedFloatFunction<C>> CubicSpline<C, I> constant(float value) {
        return new CubicSpline.Constant<C, I>(value);
    }

    static <C, I extends BoundedFloatFunction<C>> CubicSpline.Builder<C, I> builder(I coordinate) {
        return new CubicSpline.Builder<C, I>(coordinate);
    }

    static <C, I extends BoundedFloatFunction<C>> CubicSpline.Builder<C, I> builder(I coordinate, BoundedFloatFunction<Float> valueTransformer) {
        return new CubicSpline.Builder<C, I>(coordinate, valueTransformer);
    }

    @VisibleForDebug
    public static record Multipoint<C, I extends BoundedFloatFunction<C>>(I coordinate, float[] locations, List<CubicSpline<C, I>> values, float[] derivatives, float minValue, float maxValue) implements CubicSpline<C, I> {

        public Multipoint {
            validateSizes(locations, values, derivatives);
        }

        private static <C, I extends BoundedFloatFunction<C>> CubicSpline.Multipoint<C, I> create(I coordinate, float[] locations, List<CubicSpline<C, I>> values, float[] derivatives) {
            validateSizes(locations, values, derivatives);
            int i = locations.length - 1;
            float f = Float.POSITIVE_INFINITY;
            float f1 = Float.NEGATIVE_INFINITY;
            float f2 = coordinate.minValue();
            float f3 = coordinate.maxValue();

            if (f2 < locations[0]) {
                float f4 = linearExtend(f2, locations, ((CubicSpline) values.get(0)).minValue(), derivatives, 0);
                float f5 = linearExtend(f2, locations, ((CubicSpline) values.get(0)).maxValue(), derivatives, 0);

                f = Math.min(f, Math.min(f4, f5));
                f1 = Math.max(f1, Math.max(f4, f5));
            }

            if (f3 > locations[i]) {
                float f6 = linearExtend(f3, locations, ((CubicSpline) values.get(i)).minValue(), derivatives, i);
                float f7 = linearExtend(f3, locations, ((CubicSpline) values.get(i)).maxValue(), derivatives, i);

                f = Math.min(f, Math.min(f6, f7));
                f1 = Math.max(f1, Math.max(f6, f7));
            }

            for (CubicSpline<C, I> cubicspline : values) {
                f = Math.min(f, cubicspline.minValue());
                f1 = Math.max(f1, cubicspline.maxValue());
            }

            for (int j = 0; j < i; ++j) {
                float f8 = locations[j];
                float f9 = locations[j + 1];
                float f10 = f9 - f8;
                CubicSpline<C, I> cubicspline1 = (CubicSpline) values.get(j);
                CubicSpline<C, I> cubicspline2 = (CubicSpline) values.get(j + 1);
                float f11 = cubicspline1.minValue();
                float f12 = cubicspline1.maxValue();
                float f13 = cubicspline2.minValue();
                float f14 = cubicspline2.maxValue();
                float f15 = derivatives[j];
                float f16 = derivatives[j + 1];

                if (f15 != 0.0F || f16 != 0.0F) {
                    float f17 = f15 * f10;
                    float f18 = f16 * f10;
                    float f19 = Math.min(f11, f13);
                    float f20 = Math.max(f12, f14);
                    float f21 = f17 - f14 + f11;
                    float f22 = f17 - f13 + f12;
                    float f23 = -f18 + f13 - f12;
                    float f24 = -f18 + f14 - f11;
                    float f25 = Math.min(f21, f23);
                    float f26 = Math.max(f22, f24);

                    f = Math.min(f, f19 + 0.25F * f25);
                    f1 = Math.max(f1, f20 + 0.25F * f26);
                }
            }

            return new CubicSpline.Multipoint<C, I>(coordinate, locations, values, derivatives, f, f1);
        }

        private static float linearExtend(float input, float[] locations, float value, float[] derivatives, int index) {
            float f2 = derivatives[index];

            return f2 == 0.0F ? value : value + f2 * (input - locations[index]);
        }

        private static <C, I extends BoundedFloatFunction<C>> void validateSizes(float[] locations, List<CubicSpline<C, I>> values, float[] derivatives) {
            if (locations.length == values.size() && locations.length == derivatives.length) {
                if (locations.length == 0) {
                    throw new IllegalArgumentException("Cannot create a multipoint spline with no points");
                }
            } else {
                throw new IllegalArgumentException("All lengths must be equal, got: " + locations.length + " " + values.size() + " " + derivatives.length);
            }
        }

        @Override
        public float apply(C c) {
            float f = this.coordinate.apply(c);
            int i = findIntervalStart(this.locations, f);
            int j = this.locations.length - 1;

            if (i < 0) {
                return linearExtend(f, this.locations, ((CubicSpline) this.values.get(0)).apply(c), this.derivatives, 0);
            } else if (i == j) {
                return linearExtend(f, this.locations, ((CubicSpline) this.values.get(j)).apply(c), this.derivatives, j);
            } else {
                float f1 = this.locations[i];
                float f2 = this.locations[i + 1];
                float f3 = (f - f1) / (f2 - f1);
                BoundedFloatFunction<C> boundedfloatfunction = (BoundedFloatFunction) this.values.get(i);
                BoundedFloatFunction<C> boundedfloatfunction1 = (BoundedFloatFunction) this.values.get(i + 1);
                float f4 = this.derivatives[i];
                float f5 = this.derivatives[i + 1];
                float f6 = boundedfloatfunction.apply(c);
                float f7 = boundedfloatfunction1.apply(c);
                float f8 = f4 * (f2 - f1) - (f7 - f6);
                float f9 = -f5 * (f2 - f1) + (f7 - f6);
                float f10 = Mth.lerp(f3, f6, f7) + f3 * (1.0F - f3) * Mth.lerp(f3, f8, f9);

                return f10;
            }
        }

        private static int findIntervalStart(float[] locations, float input) {
            return Mth.binarySearch(0, locations.length, (i) -> {
                return input < locations[i];
            }) - 1;
        }

        @VisibleForTesting
        @Override
        public String parityString() {
            String s = String.valueOf(this.coordinate);

            return "Spline{coordinate=" + s + ", locations=" + this.toString(this.locations) + ", derivatives=" + this.toString(this.derivatives) + ", values=" + (String) this.values.stream().map(CubicSpline::parityString).collect(Collectors.joining(", ", "[", "]")) + "}";
        }

        private String toString(float[] arr) {
            Stream stream = IntStream.range(0, arr.length).mapToDouble((i) -> {
                return (double) arr[i];
            }).mapToObj((d0) -> {
                return String.format(Locale.ROOT, "%.3f", d0);
            });

            return "[" + (String) stream.collect(Collectors.joining(", ")) + "]";
        }

        @Override
        public CubicSpline<C, I> mapAll(CubicSpline.CoordinateVisitor<I> visitor) {
            return create(visitor.visit(this.coordinate), this.locations, this.values().stream().map((cubicspline) -> {
                return cubicspline.mapAll(visitor);
            }).toList(), this.derivatives);
        }
    }

    @VisibleForDebug
    public static record Constant<C, I extends BoundedFloatFunction<C>>(float value) implements CubicSpline<C, I> {

        @Override
        public float apply(C c) {
            return this.value;
        }

        @Override
        public String parityString() {
            return String.format(Locale.ROOT, "k=%.3f", this.value);
        }

        @Override
        public float minValue() {
            return this.value;
        }

        @Override
        public float maxValue() {
            return this.value;
        }

        @Override
        public CubicSpline<C, I> mapAll(CubicSpline.CoordinateVisitor<I> visitor) {
            return this;
        }
    }

    public static final class Builder<C, I extends BoundedFloatFunction<C>> {

        private final I coordinate;
        private final BoundedFloatFunction<Float> valueTransformer;
        private final FloatList locations;
        private final List<CubicSpline<C, I>> values;
        private final FloatList derivatives;

        protected Builder(I coordinate) {
            this(coordinate, BoundedFloatFunction.IDENTITY);
        }

        protected Builder(I coordinate, BoundedFloatFunction<Float> valueTransformer) {
            this.locations = new FloatArrayList();
            this.values = Lists.newArrayList();
            this.derivatives = new FloatArrayList();
            this.coordinate = coordinate;
            this.valueTransformer = valueTransformer;
        }

        public CubicSpline.Builder<C, I> addPoint(float location, float value) {
            return this.addPoint(location, new CubicSpline.Constant(this.valueTransformer.apply(value)), 0.0F);
        }

        public CubicSpline.Builder<C, I> addPoint(float location, float value, float derivative) {
            return this.addPoint(location, new CubicSpline.Constant(this.valueTransformer.apply(value)), derivative);
        }

        public CubicSpline.Builder<C, I> addPoint(float location, CubicSpline<C, I> sampler) {
            return this.addPoint(location, sampler, 0.0F);
        }

        private CubicSpline.Builder<C, I> addPoint(float location, CubicSpline<C, I> sampler, float derivative) {
            if (!this.locations.isEmpty() && location <= this.locations.getFloat(this.locations.size() - 1)) {
                throw new IllegalArgumentException("Please register points in ascending order");
            } else {
                this.locations.add(location);
                this.values.add(sampler);
                this.derivatives.add(derivative);
                return this;
            }
        }

        public CubicSpline<C, I> build() {
            if (this.locations.isEmpty()) {
                throw new IllegalStateException("No elements added");
            } else {
                return CubicSpline.Multipoint.<C, I>create(this.coordinate, this.locations.toFloatArray(), ImmutableList.copyOf(this.values), this.derivatives.toFloatArray());
            }
        }
    }

    public interface CoordinateVisitor<I> {

        I visit(I input);
    }
}

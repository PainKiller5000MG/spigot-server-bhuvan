package net.minecraft.util;

import java.util.Locale;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.math.Fraction;
import org.apache.commons.lang3.math.NumberUtils;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Mth {

    private static final long UUID_VERSION = 61440L;
    private static final long UUID_VERSION_TYPE_4 = 16384L;
    private static final long UUID_VARIANT = -4611686018427387904L;
    private static final long UUID_VARIANT_2 = Long.MIN_VALUE;
    public static final float PI = (float) Math.PI;
    public static final float HALF_PI = ((float) Math.PI / 2F);
    public static final float TWO_PI = ((float) Math.PI * 2F);
    public static final float DEG_TO_RAD = ((float) Math.PI / 180F);
    public static final float RAD_TO_DEG = (180F / (float) Math.PI);
    public static final float EPSILON = 1.0E-5F;
    public static final float SQRT_OF_TWO = sqrt(2.0F);
    public static final Vector3f Y_AXIS = new Vector3f(0.0F, 1.0F, 0.0F);
    public static final Vector3f X_AXIS = new Vector3f(1.0F, 0.0F, 0.0F);
    public static final Vector3f Z_AXIS = new Vector3f(0.0F, 0.0F, 1.0F);
    private static final int SIN_QUANTIZATION = 65536;
    private static final int SIN_MASK = 65535;
    private static final int COS_OFFSET = 16384;
    private static final double SIN_SCALE = 10430.378350470453D;
    private static final float[] SIN = (float[]) Util.make(new float[65536], (afloat) -> {
        for (int i = 0; i < afloat.length; ++i) {
            afloat[i] = (float) Math.sin((double) i / 10430.378350470453D);
        }

    });
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final int[] MULTIPLY_DE_BRUIJN_BIT_POSITION = new int[]{0, 1, 28, 2, 29, 14, 24, 3, 30, 22, 20, 15, 25, 17, 4, 8, 31, 27, 13, 23, 21, 19, 16, 7, 26, 12, 18, 6, 11, 5, 10, 9};
    private static final double ONE_SIXTH = 0.16666666666666666D;
    private static final int FRAC_EXP = 8;
    private static final int LUT_SIZE = 257;
    private static final double FRAC_BIAS = Double.longBitsToDouble(4805340802404319232L);
    private static final double[] ASIN_TAB = new double[257];
    private static final double[] COS_TAB = new double[257];

    public Mth() {}

    public static float sin(double i) {
        return Mth.SIN[(int) ((long) (i * 10430.378350470453D) & 65535L)];
    }

    public static float cos(double i) {
        return Mth.SIN[(int) ((long) (i * 10430.378350470453D + 16384.0D) & 65535L)];
    }

    public static float sqrt(float x) {
        return (float) Math.sqrt((double) x);
    }

    public static int floor(float v) {
        int i = (int) v;

        return v < (float) i ? i - 1 : i;
    }

    public static int floor(double v) {
        int i = (int) v;

        return v < (double) i ? i - 1 : i;
    }

    public static long lfloor(double v) {
        long i = (long) v;

        return v < (double) i ? i - 1L : i;
    }

    public static float abs(float v) {
        return Math.abs(v);
    }

    public static int abs(int v) {
        return Math.abs(v);
    }

    public static int ceil(float v) {
        int i = (int) v;

        return v > (float) i ? i + 1 : i;
    }

    public static int ceil(double v) {
        int i = (int) v;

        return v > (double) i ? i + 1 : i;
    }

    public static long ceilLong(double v) {
        long i = (long) v;

        return v > (double) i ? i + 1L : i;
    }

    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    public static long clamp(long value, long min, long max) {
        return Math.min(Math.max(value, min), max);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clampedLerp(double factor, double min, double max) {
        return factor < 0.0D ? min : (factor > 1.0D ? max : lerp(factor, min, max));
    }

    public static float clampedLerp(float factor, float min, float max) {
        return factor < 0.0F ? min : (factor > 1.0F ? max : lerp(factor, min, max));
    }

    public static int absMax(int a, int b) {
        return Math.max(Math.abs(a), Math.abs(b));
    }

    public static float absMax(float a, float b) {
        return Math.max(Math.abs(a), Math.abs(b));
    }

    public static double absMax(double a, double b) {
        return Math.max(Math.abs(a), Math.abs(b));
    }

    public static int chessboardDistance(int x0, int z0, int x1, int z1) {
        return absMax(x1 - x0, z1 - z0);
    }

    public static int floorDiv(int a, int b) {
        return Math.floorDiv(a, b);
    }

    public static int nextInt(RandomSource random, int minInclusive, int maxInclusive) {
        return minInclusive >= maxInclusive ? minInclusive : random.nextInt(maxInclusive - minInclusive + 1) + minInclusive;
    }

    public static float nextFloat(RandomSource random, float min, float max) {
        return min >= max ? min : random.nextFloat() * (max - min) + min;
    }

    public static double nextDouble(RandomSource random, double min, double max) {
        return min >= max ? min : random.nextDouble() * (max - min) + min;
    }

    public static boolean equal(float a, float b) {
        return Math.abs(b - a) < 1.0E-5F;
    }

    public static boolean equal(double a, double b) {
        return Math.abs(b - a) < (double) 1.0E-5F;
    }

    public static int positiveModulo(int input, int mod) {
        return Math.floorMod(input, mod);
    }

    public static float positiveModulo(float input, float mod) {
        return (input % mod + mod) % mod;
    }

    public static double positiveModulo(double input, double mod) {
        return (input % mod + mod) % mod;
    }

    public static boolean isMultipleOf(int dividend, int divisor) {
        return dividend % divisor == 0;
    }

    public static byte packDegrees(float angle) {
        return (byte) floor(angle * 256.0F / 360.0F);
    }

    public static float unpackDegrees(byte rot) {
        return (float) (rot * 360) / 256.0F;
    }

    public static int wrapDegrees(int angle) {
        int j = angle % 360;

        if (j >= 180) {
            j -= 360;
        }

        if (j < -180) {
            j += 360;
        }

        return j;
    }

    public static float wrapDegrees(long angle) {
        float f = (float) (angle % 360L);

        if (f >= 180.0F) {
            f -= 360.0F;
        }

        if (f < -180.0F) {
            f += 360.0F;
        }

        return f;
    }

    public static float wrapDegrees(float angle) {
        float f1 = angle % 360.0F;

        if (f1 >= 180.0F) {
            f1 -= 360.0F;
        }

        if (f1 < -180.0F) {
            f1 += 360.0F;
        }

        return f1;
    }

    public static double wrapDegrees(double angle) {
        double d1 = angle % 360.0D;

        if (d1 >= 180.0D) {
            d1 -= 360.0D;
        }

        if (d1 < -180.0D) {
            d1 += 360.0D;
        }

        return d1;
    }

    public static float degreesDifference(float fromAngle, float toAngle) {
        return wrapDegrees(toAngle - fromAngle);
    }

    public static float degreesDifferenceAbs(float angleA, float angleB) {
        return abs(degreesDifference(angleA, angleB));
    }

    public static float rotateIfNecessary(float baseAngle, float targetAngle, float maxAngleDiff) {
        float f3 = degreesDifference(baseAngle, targetAngle);
        float f4 = clamp(f3, -maxAngleDiff, maxAngleDiff);

        return targetAngle - f4;
    }

    public static float approach(float current, float target, float increment) {
        increment = abs(increment);
        return current < target ? clamp(current + increment, current, target) : clamp(current - increment, target, current);
    }

    public static float approachDegrees(float current, float target, float increment) {
        float f3 = degreesDifference(current, target);

        return approach(current, current + f3, increment);
    }

    public static int getInt(String input, int def) {
        return NumberUtils.toInt(input, def);
    }

    public static int smallestEncompassingPowerOfTwo(int input) {
        int j = input - 1;

        j |= j >> 1;
        j |= j >> 2;
        j |= j >> 4;
        j |= j >> 8;
        j |= j >> 16;
        return j + 1;
    }

    public static int smallestSquareSide(int itemCount) {
        if (itemCount < 0) {
            throw new IllegalArgumentException("itemCount must be greater than or equal to zero");
        } else {
            return ceil(Math.sqrt((double) itemCount));
        }
    }

    public static boolean isPowerOfTwo(int input) {
        return input != 0 && (input & input - 1) == 0;
    }

    public static int ceillog2(int input) {
        input = isPowerOfTwo(input) ? input : smallestEncompassingPowerOfTwo(input);
        return Mth.MULTIPLY_DE_BRUIJN_BIT_POSITION[(int) ((long) input * 125613361L >> 27) & 31];
    }

    public static int log2(int input) {
        return ceillog2(input) - (isPowerOfTwo(input) ? 0 : 1);
    }

    public static float frac(float num) {
        return num - (float) floor(num);
    }

    public static double frac(double num) {
        return num - (double) lfloor(num);
    }

    /** @deprecated */
    @Deprecated
    public static long getSeed(Vec3i vec) {
        return getSeed(vec.getX(), vec.getY(), vec.getZ());
    }

    /** @deprecated */
    @Deprecated
    public static long getSeed(int x, int y, int z) {
        long l = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;

        l = l * l * 42317861L + l * 11L;
        return l >> 16;
    }

    public static UUID createInsecureUUID(RandomSource random) {
        long i = random.nextLong() & -61441L | 16384L;
        long j = random.nextLong() & 4611686018427387903L | Long.MIN_VALUE;

        return new UUID(i, j);
    }

    public static UUID createInsecureUUID() {
        return createInsecureUUID(Mth.RANDOM);
    }

    public static double inverseLerp(double value, double min, double max) {
        return (value - min) / (max - min);
    }

    public static float inverseLerp(float value, float min, float max) {
        return (value - min) / (max - min);
    }

    public static boolean rayIntersectsAABB(Vec3 rayStart, Vec3 rayDir, AABB aabb) {
        double d0 = (aabb.minX + aabb.maxX) * 0.5D;
        double d1 = (aabb.maxX - aabb.minX) * 0.5D;
        double d2 = rayStart.x - d0;

        if (Math.abs(d2) > d1 && d2 * rayDir.x >= 0.0D) {
            return false;
        } else {
            double d3 = (aabb.minY + aabb.maxY) * 0.5D;
            double d4 = (aabb.maxY - aabb.minY) * 0.5D;
            double d5 = rayStart.y - d3;

            if (Math.abs(d5) > d4 && d5 * rayDir.y >= 0.0D) {
                return false;
            } else {
                double d6 = (aabb.minZ + aabb.maxZ) * 0.5D;
                double d7 = (aabb.maxZ - aabb.minZ) * 0.5D;
                double d8 = rayStart.z - d6;

                if (Math.abs(d8) > d7 && d8 * rayDir.z >= 0.0D) {
                    return false;
                } else {
                    double d9 = Math.abs(rayDir.x);
                    double d10 = Math.abs(rayDir.y);
                    double d11 = Math.abs(rayDir.z);
                    double d12 = rayDir.y * d8 - rayDir.z * d5;

                    if (Math.abs(d12) > d4 * d11 + d7 * d10) {
                        return false;
                    } else {
                        d12 = rayDir.z * d2 - rayDir.x * d8;
                        if (Math.abs(d12) > d1 * d11 + d7 * d9) {
                            return false;
                        } else {
                            d12 = rayDir.x * d5 - rayDir.y * d2;
                            return Math.abs(d12) < d1 * d10 + d4 * d9;
                        }
                    }
                }
            }
        }
    }

    public static double atan2(double y, double x) {
        double d2 = x * x + y * y;

        if (Double.isNaN(d2)) {
            return Double.NaN;
        } else {
            boolean flag = y < 0.0D;

            if (flag) {
                y = -y;
            }

            boolean flag1 = x < 0.0D;

            if (flag1) {
                x = -x;
            }

            boolean flag2 = y > x;

            if (flag2) {
                double d3 = x;

                x = y;
                y = d3;
            }

            double d4 = fastInvSqrt(d2);

            x *= d4;
            y *= d4;
            double d5 = Mth.FRAC_BIAS + y;
            int i = (int) Double.doubleToRawLongBits(d5);
            double d6 = Mth.ASIN_TAB[i];
            double d7 = Mth.COS_TAB[i];
            double d8 = d5 - Mth.FRAC_BIAS;
            double d9 = y * d7 - x * d8;
            double d10 = (6.0D + d9 * d9) * d9 * 0.16666666666666666D;
            double d11 = d6 + d10;

            if (flag2) {
                d11 = (Math.PI / 2D) - d11;
            }

            if (flag1) {
                d11 = Math.PI - d11;
            }

            if (flag) {
                d11 = -d11;
            }

            return d11;
        }
    }

    public static float invSqrt(float x) {
        return org.joml.Math.invsqrt(x);
    }

    public static double invSqrt(double x) {
        return org.joml.Math.invsqrt(x);
    }

    /** @deprecated */
    @Deprecated
    public static double fastInvSqrt(double x) {
        double d1 = 0.5D * x;
        long i = Double.doubleToRawLongBits(x);

        i = 6910469410427058090L - (i >> 1);
        x = Double.longBitsToDouble(i);
        x *= 1.5D - d1 * x * x;
        return x;
    }

    public static float fastInvCubeRoot(float x) {
        int i = Float.floatToIntBits(x);

        i = 1419967116 - i / 3;
        float f1 = Float.intBitsToFloat(i);

        f1 = 0.6666667F * f1 + 1.0F / (3.0F * f1 * f1 * x);
        f1 = 0.6666667F * f1 + 1.0F / (3.0F * f1 * f1 * x);
        return f1;
    }

    public static int hsvToRgb(float hue, float saturation, float value) {
        return hsvToArgb(hue, saturation, value, 0);
    }

    public static int hsvToArgb(float hue, float saturation, float value, int alpha) {
        int j = (int) (hue * 6.0F) % 6;
        float f3 = hue * 6.0F - (float) j;
        float f4 = value * (1.0F - saturation);
        float f5 = value * (1.0F - f3 * saturation);
        float f6 = value * (1.0F - (1.0F - f3) * saturation);
        float f7;
        float f8;
        float f9;

        switch (j) {
            case 0:
                f7 = value;
                f8 = f6;
                f9 = f4;
                break;
            case 1:
                f7 = f5;
                f8 = value;
                f9 = f4;
                break;
            case 2:
                f7 = f4;
                f8 = value;
                f9 = f6;
                break;
            case 3:
                f7 = f4;
                f8 = f5;
                f9 = value;
                break;
            case 4:
                f7 = f6;
                f8 = f4;
                f9 = value;
                break;
            case 5:
                f7 = value;
                f8 = f4;
                f9 = f5;
                break;
            default:
                throw new RuntimeException("Something went wrong when converting from HSV to RGB. Input was " + hue + ", " + saturation + ", " + value);
        }

        return ARGB.color(alpha, clamp((int) (f7 * 255.0F), 0, 255), clamp((int) (f8 * 255.0F), 0, 255), clamp((int) (f9 * 255.0F), 0, 255));
    }

    public static int murmurHash3Mixer(int hash) {
        hash ^= hash >>> 16;
        hash *= -2048144789;
        hash ^= hash >>> 13;
        hash *= -1028477387;
        hash ^= hash >>> 16;
        return hash;
    }

    public static int binarySearch(int from, int to, IntPredicate condition) {
        int k = to - from;

        while (k > 0) {
            int l = k / 2;
            int i1 = from + l;

            if (condition.test(i1)) {
                k = l;
            } else {
                from = i1 + 1;
                k -= l + 1;
            }
        }

        return from;
    }

    public static int lerpInt(float alpha1, int p0, int p1) {
        return p0 + floor(alpha1 * (float) (p1 - p0));
    }

    public static int lerpDiscrete(float alpha1, int p0, int p1) {
        int k = p1 - p0;

        return p0 + floor(alpha1 * (float) (k - 1)) + (alpha1 > 0.0F ? 1 : 0);
    }

    public static float lerp(float alpha1, float p0, float p1) {
        return p0 + alpha1 * (p1 - p0);
    }

    public static Vec3 lerp(double alpha, Vec3 p1, Vec3 p2) {
        return new Vec3(lerp(alpha, p1.x, p2.x), lerp(alpha, p1.y, p2.y), lerp(alpha, p1.z, p2.z));
    }

    public static double lerp(double alpha1, double p0, double p1) {
        return p0 + alpha1 * (p1 - p0);
    }

    public static double lerp2(double alpha1, double alpha2, double x00, double x10, double x01, double x11) {
        return lerp(alpha2, lerp(alpha1, x00, x10), lerp(alpha1, x01, x11));
    }

    public static double lerp3(double alpha1, double alpha2, double alpha3, double x000, double x100, double x010, double x110, double x001, double x101, double x011, double x111) {
        return lerp(alpha3, lerp2(alpha1, alpha2, x000, x100, x010, x110), lerp2(alpha1, alpha2, x001, x101, x011, x111));
    }

    public static float catmullrom(float alpha, float p0, float p1, float p2, float p3) {
        return 0.5F * (2.0F * p1 + (p2 - p0) * alpha + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * alpha * alpha + (3.0F * p1 - p0 - 3.0F * p2 + p3) * alpha * alpha * alpha);
    }

    public static double smoothstep(double x) {
        return x * x * x * (x * (x * 6.0D - 15.0D) + 10.0D);
    }

    public static double smoothstepDerivative(double x) {
        return 30.0D * x * x * (x - 1.0D) * (x - 1.0D);
    }

    public static int sign(double number) {
        return number == 0.0D ? 0 : (number > 0.0D ? 1 : -1);
    }

    public static float rotLerp(float a, float from, float to) {
        return from + a * wrapDegrees(to - from);
    }

    public static double rotLerp(double a, double from, double to) {
        return from + a * wrapDegrees(to - from);
    }

    public static float rotLerpRad(float a, float from, float to) {
        float f3;

        for (f3 = to - from; f3 < -(float) Math.PI; f3 += ((float) Math.PI * 2F)) {
            ;
        }

        while (f3 >= (float) Math.PI) {
            f3 -= ((float) Math.PI * 2F);
        }

        return from + a * f3;
    }

    public static float triangleWave(float index, float period) {
        return (Math.abs(index % period - period * 0.5F) - period * 0.25F) / (period * 0.25F);
    }

    public static float square(float x) {
        return x * x;
    }

    public static float cube(float x) {
        return x * x * x;
    }

    public static double square(double x) {
        return x * x;
    }

    public static int square(int x) {
        return x * x;
    }

    public static long square(long x) {
        return x * x;
    }

    public static double clampedMap(double value, double fromMin, double fromMax, double toMin, double toMax) {
        return clampedLerp(inverseLerp(value, fromMin, fromMax), toMin, toMax);
    }

    public static float clampedMap(float value, float fromMin, float fromMax, float toMin, float toMax) {
        return clampedLerp(inverseLerp(value, fromMin, fromMax), toMin, toMax);
    }

    public static double map(double value, double fromMin, double fromMax, double toMin, double toMax) {
        return lerp(inverseLerp(value, fromMin, fromMax), toMin, toMax);
    }

    public static float map(float value, float fromMin, float fromMax, float toMin, float toMax) {
        return lerp(inverseLerp(value, fromMin, fromMax), toMin, toMax);
    }

    public static double wobble(double coord) {
        return coord + (2.0D * RandomSource.create((long) floor(coord * 3000.0D)).nextDouble() - 1.0D) * 1.0E-7D / 2.0D;
    }

    public static int roundToward(int input, int multiple) {
        return positiveCeilDiv(input, multiple) * multiple;
    }

    public static int positiveCeilDiv(int input, int divisor) {
        return -Math.floorDiv(-input, divisor);
    }

    public static int randomBetweenInclusive(RandomSource random, int min, int maxInclusive) {
        return random.nextInt(maxInclusive - min + 1) + min;
    }

    public static float randomBetween(RandomSource random, float min, float maxExclusive) {
        return random.nextFloat() * (maxExclusive - min) + min;
    }

    public static float normal(RandomSource random, float mean, float deviation) {
        return mean + (float) random.nextGaussian() * deviation;
    }

    public static double lengthSquared(double x, double y) {
        return x * x + y * y;
    }

    public static double length(double x, double y) {
        return Math.sqrt(lengthSquared(x, y));
    }

    public static float length(float x, float y) {
        return (float) Math.sqrt(lengthSquared((double) x, (double) y));
    }

    public static double lengthSquared(double x, double y, double z) {
        return x * x + y * y + z * z;
    }

    public static double length(double x, double y, double z) {
        return Math.sqrt(lengthSquared(x, y, z));
    }

    public static float lengthSquared(float x, float y, float z) {
        return x * x + y * y + z * z;
    }

    public static int quantize(double value, int quantizeResolution) {
        return floor(value / (double) quantizeResolution) * quantizeResolution;
    }

    public static IntStream outFromOrigin(int origin, int lowerBound, int upperBound) {
        return outFromOrigin(origin, lowerBound, upperBound, 1);
    }

    public static IntStream outFromOrigin(int origin, int lowerBound, int upperBound, int stepSize) {
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "upperBound %d expected to be > lowerBound %d", upperBound, lowerBound));
        } else if (stepSize < 1) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "step size expected to be >= 1, was %d", stepSize));
        } else {
            int i1 = clamp(origin, lowerBound, upperBound);

            return IntStream.iterate(i1, (j1) -> {
                int k1 = Math.abs(i1 - j1);

                return i1 - k1 >= lowerBound || i1 + k1 <= upperBound;
            }, (j1) -> {
                boolean flag = j1 <= i1;
                int k1 = Math.abs(i1 - j1);
                boolean flag1 = i1 + k1 + stepSize <= upperBound;

                if (!flag || !flag1) {
                    int l1 = i1 - k1 - (flag ? stepSize : 0);

                    if (l1 >= lowerBound) {
                        return l1;
                    }
                }

                return i1 + k1 + stepSize;
            });
        }
    }

    public static Quaternionf rotationAroundAxis(Vector3f axis, Quaternionf rotation, Quaternionf result) {
        float f = axis.dot(rotation.x, rotation.y, rotation.z);

        return result.set(axis.x * f, axis.y * f, axis.z * f, rotation.w).normalize();
    }

    public static int mulAndTruncate(Fraction fraction, int factor) {
        return fraction.getNumerator() * factor / fraction.getDenominator();
    }

    static {
        for (int i = 0; i < 257; ++i) {
            double d0 = (double) i / 256.0D;
            double d1 = Math.asin(d0);

            Mth.COS_TAB[i] = Math.cos(d1);
            Mth.ASIN_TAB[i] = d1;
        }

    }
}

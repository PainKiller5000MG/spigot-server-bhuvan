package net.minecraft.util;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class ARGB {

    private static final int LINEAR_CHANNEL_DEPTH = 1024;
    private static final short[] SRGB_TO_LINEAR = (short[]) Util.make(new short[256], (ashort) -> {
        for (int i = 0; i < ashort.length; ++i) {
            float f = (float) i / 255.0F;

            ashort[i] = (short) Math.round(computeSrgbToLinear(f) * 1023.0F);
        }

    });
    private static final byte[] LINEAR_TO_SRGB = (byte[]) Util.make(new byte[1024], (abyte) -> {
        for (int i = 0; i < abyte.length; ++i) {
            float f = (float) i / 1023.0F;

            abyte[i] = (byte) Math.round(computeLinearToSrgb(f) * 255.0F);
        }

    });

    public ARGB() {}

    private static float computeSrgbToLinear(float x) {
        return x >= 0.04045F ? (float) Math.pow(((double) x + 0.055D) / 1.055D, 2.4D) : x / 12.92F;
    }

    private static float computeLinearToSrgb(float x) {
        return x >= 0.0031308F ? (float) (1.055D * Math.pow((double) x, 0.4166666666666667D) - 0.055D) : 12.92F * x;
    }

    public static float srgbToLinearChannel(int srgb) {
        return (float) ARGB.SRGB_TO_LINEAR[srgb] / 1023.0F;
    }

    public static int linearToSrgbChannel(float linear) {
        return ARGB.LINEAR_TO_SRGB[Mth.floor(linear * 1023.0F)] & 255;
    }

    public static int meanLinear(int srgb1, int srgb2, int srgb3, int srgb4) {
        return color((alpha(srgb1) + alpha(srgb2) + alpha(srgb3) + alpha(srgb4)) / 4, linearChannelMean(red(srgb1), red(srgb2), red(srgb3), red(srgb4)), linearChannelMean(green(srgb1), green(srgb2), green(srgb3), green(srgb4)), linearChannelMean(blue(srgb1), blue(srgb2), blue(srgb3), blue(srgb4)));
    }

    private static int linearChannelMean(int c1, int c2, int c3, int c4) {
        int i1 = (ARGB.SRGB_TO_LINEAR[c1] + ARGB.SRGB_TO_LINEAR[c2] + ARGB.SRGB_TO_LINEAR[c3] + ARGB.SRGB_TO_LINEAR[c4]) / 4;

        return ARGB.LINEAR_TO_SRGB[i1] & 255;
    }

    public static int alpha(int color) {
        return color >>> 24;
    }

    public static int red(int color) {
        return color >> 16 & 255;
    }

    public static int green(int color) {
        return color >> 8 & 255;
    }

    public static int blue(int color) {
        return color & 255;
    }

    public static int color(int alpha, int red, int green, int blue) {
        return (alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | blue & 255;
    }

    public static int color(int red, int green, int blue) {
        return color(255, red, green, blue);
    }

    public static int color(Vec3 vec) {
        return color(as8BitChannel((float) vec.x()), as8BitChannel((float) vec.y()), as8BitChannel((float) vec.z()));
    }

    public static int multiply(int lhs, int rhs) {
        return lhs == -1 ? rhs : (rhs == -1 ? lhs : color(alpha(lhs) * alpha(rhs) / 255, red(lhs) * red(rhs) / 255, green(lhs) * green(rhs) / 255, blue(lhs) * blue(rhs) / 255));
    }

    public static int addRgb(int lhs, int rhs) {
        return color(alpha(lhs), Math.min(red(lhs) + red(rhs), 255), Math.min(green(lhs) + green(rhs), 255), Math.min(blue(lhs) + blue(rhs), 255));
    }

    public static int subtractRgb(int lhs, int rhs) {
        return color(alpha(lhs), Math.max(red(lhs) - red(rhs), 0), Math.max(green(lhs) - green(rhs), 0), Math.max(blue(lhs) - blue(rhs), 0));
    }

    public static int multiplyAlpha(int color, float alphaMultiplier) {
        return color != 0 && alphaMultiplier > 0.0F ? (alphaMultiplier >= 1.0F ? color : color(alphaFloat(color) * alphaMultiplier, color)) : 0;
    }

    public static int scaleRGB(int color, float scale) {
        return scaleRGB(color, scale, scale, scale);
    }

    public static int scaleRGB(int color, float scaleR, float scaleG, float scaleB) {
        return color(alpha(color), Math.clamp((long) ((int) ((float) red(color) * scaleR)), 0, 255), Math.clamp((long) ((int) ((float) green(color) * scaleG)), 0, 255), Math.clamp((long) ((int) ((float) blue(color) * scaleB)), 0, 255));
    }

    public static int scaleRGB(int color, int scale) {
        return color(alpha(color), Math.clamp((long) red(color) * (long) scale / 255L, 0, 255), Math.clamp((long) green(color) * (long) scale / 255L, 0, 255), Math.clamp((long) blue(color) * (long) scale / 255L, 0, 255));
    }

    public static int greyscale(int color) {
        int j = (int) ((float) red(color) * 0.3F + (float) green(color) * 0.59F + (float) blue(color) * 0.11F);

        return color(alpha(color), j, j, j);
    }

    public static int alphaBlend(int destination, int source) {
        int k = alpha(destination);
        int l = alpha(source);

        if (l == 255) {
            return source;
        } else if (l == 0) {
            return destination;
        } else {
            int i1 = l + k * (255 - l) / 255;

            return color(i1, alphaBlendChannel(i1, l, red(destination), red(source)), alphaBlendChannel(i1, l, green(destination), green(source)), alphaBlendChannel(i1, l, blue(destination), blue(source)));
        }
    }

    private static int alphaBlendChannel(int resultAlpha, int sourceAlpha, int destination, int source) {
        return (source * sourceAlpha + destination * (resultAlpha - sourceAlpha)) / resultAlpha;
    }

    public static int srgbLerp(float alpha, int p0, int p1) {
        int k = Mth.lerpInt(alpha, alpha(p0), alpha(p1));
        int l = Mth.lerpInt(alpha, red(p0), red(p1));
        int i1 = Mth.lerpInt(alpha, green(p0), green(p1));
        int j1 = Mth.lerpInt(alpha, blue(p0), blue(p1));

        return color(k, l, i1, j1);
    }

    public static int linearLerp(float alpha, int p0, int p1) {
        return color(Mth.lerpInt(alpha, alpha(p0), alpha(p1)), ARGB.LINEAR_TO_SRGB[Mth.lerpInt(alpha, ARGB.SRGB_TO_LINEAR[red(p0)], ARGB.SRGB_TO_LINEAR[red(p1)])] & 255, ARGB.LINEAR_TO_SRGB[Mth.lerpInt(alpha, ARGB.SRGB_TO_LINEAR[green(p0)], ARGB.SRGB_TO_LINEAR[green(p1)])] & 255, ARGB.LINEAR_TO_SRGB[Mth.lerpInt(alpha, ARGB.SRGB_TO_LINEAR[blue(p0)], ARGB.SRGB_TO_LINEAR[blue(p1)])] & 255);
    }

    public static int opaque(int color) {
        return color | -16777216;
    }

    public static int transparent(int color) {
        return color & 16777215;
    }

    public static int color(int alpha, int rgb) {
        return alpha << 24 | rgb & 16777215;
    }

    public static int color(float alpha, int rgb) {
        return as8BitChannel(alpha) << 24 | rgb & 16777215;
    }

    public static int white(float alpha) {
        return as8BitChannel(alpha) << 24 | 16777215;
    }

    public static int white(int alpha) {
        return alpha << 24 | 16777215;
    }

    public static int black(float alpha) {
        return as8BitChannel(alpha) << 24;
    }

    public static int black(int alpha) {
        return alpha << 24;
    }

    public static int colorFromFloat(float alpha, float red, float green, float blue) {
        return color(as8BitChannel(alpha), as8BitChannel(red), as8BitChannel(green), as8BitChannel(blue));
    }

    public static Vector3f vector3fFromRGB24(int color) {
        return new Vector3f(redFloat(color), greenFloat(color), blueFloat(color));
    }

    public static Vector4f vector4fFromARGB32(int color) {
        return new Vector4f(redFloat(color), greenFloat(color), blueFloat(color), alphaFloat(color));
    }

    public static int average(int lhs, int rhs) {
        return color((alpha(lhs) + alpha(rhs)) / 2, (red(lhs) + red(rhs)) / 2, (green(lhs) + green(rhs)) / 2, (blue(lhs) + blue(rhs)) / 2);
    }

    public static int as8BitChannel(float value) {
        return Mth.floor(value * 255.0F);
    }

    public static float alphaFloat(int color) {
        return from8BitChannel(alpha(color));
    }

    public static float redFloat(int color) {
        return from8BitChannel(red(color));
    }

    public static float greenFloat(int color) {
        return from8BitChannel(green(color));
    }

    public static float blueFloat(int color) {
        return from8BitChannel(blue(color));
    }

    private static float from8BitChannel(int value) {
        return (float) value / 255.0F;
    }

    public static int toABGR(int color) {
        return color & -16711936 | (color & 16711680) >> 16 | (color & 255) << 16;
    }

    public static int fromABGR(int color) {
        return toABGR(color);
    }

    public static int setBrightness(int color, float brightness) {
        int j = red(color);
        int k = green(color);
        int l = blue(color);
        int i1 = alpha(color);
        int j1 = Math.max(Math.max(j, k), l);
        int k1 = Math.min(Math.min(j, k), l);
        float f1 = (float) (j1 - k1);
        float f2;

        if (j1 != 0) {
            f2 = f1 / (float) j1;
        } else {
            f2 = 0.0F;
        }

        float f3;

        if (f2 == 0.0F) {
            f3 = 0.0F;
        } else {
            float f4 = (float) (j1 - j) / f1;
            float f5 = (float) (j1 - k) / f1;
            float f6 = (float) (j1 - l) / f1;

            if (j == j1) {
                f3 = f6 - f5;
            } else if (k == j1) {
                f3 = 2.0F + f4 - f6;
            } else {
                f3 = 4.0F + f5 - f4;
            }

            f3 /= 6.0F;
            if (f3 < 0.0F) {
                ++f3;
            }
        }

        if (f2 == 0.0F) {
            j = k = l = Math.round(brightness * 255.0F);
            return color(i1, j, k, l);
        } else {
            float f7 = (f3 - (float) Math.floor((double) f3)) * 6.0F;
            float f8 = f7 - (float) Math.floor((double) f7);
            float f9 = brightness * (1.0F - f2);
            float f10 = brightness * (1.0F - f2 * f8);
            float f11 = brightness * (1.0F - f2 * (1.0F - f8));

            switch ((int) f7) {
                case 0:
                    j = Math.round(brightness * 255.0F);
                    k = Math.round(f11 * 255.0F);
                    l = Math.round(f9 * 255.0F);
                    break;
                case 1:
                    j = Math.round(f10 * 255.0F);
                    k = Math.round(brightness * 255.0F);
                    l = Math.round(f9 * 255.0F);
                    break;
                case 2:
                    j = Math.round(f9 * 255.0F);
                    k = Math.round(brightness * 255.0F);
                    l = Math.round(f11 * 255.0F);
                    break;
                case 3:
                    j = Math.round(f9 * 255.0F);
                    k = Math.round(f10 * 255.0F);
                    l = Math.round(brightness * 255.0F);
                    break;
                case 4:
                    j = Math.round(f11 * 255.0F);
                    k = Math.round(f9 * 255.0F);
                    l = Math.round(brightness * 255.0F);
                    break;
                case 5:
                    j = Math.round(brightness * 255.0F);
                    k = Math.round(f9 * 255.0F);
                    l = Math.round(f10 * 255.0F);
            }

            return color(i1, j, k, l);
        }
    }
}

package net.minecraft.util;

public class Ease {

    public Ease() {}

    public static float inBack(float x) {
        float f1 = 1.70158F;
        float f2 = 2.70158F;

        return Mth.square(x) * (2.70158F * x - 1.70158F);
    }

    public static float inBounce(float x) {
        return 1.0F - outBounce(1.0F - x);
    }

    public static float inCubic(float x) {
        return Mth.cube(x);
    }

    public static float inElastic(float x) {
        if (x == 0.0F) {
            return 0.0F;
        } else if (x == 1.0F) {
            return 1.0F;
        } else {
            float f1 = 2.0943952F;

            return (float) (-Math.pow(2.0D, 10.0D * (double) x - 10.0D) * Math.sin(((double) x * 10.0D - 10.75D) * (double) 2.0943952F));
        }
    }

    public static float inExpo(float x) {
        return x == 0.0F ? 0.0F : (float) Math.pow(2.0D, 10.0D * (double) x - 10.0D);
    }

    public static float inQuart(float x) {
        return Mth.square(Mth.square(x));
    }

    public static float inQuint(float x) {
        return Mth.square(Mth.square(x)) * x;
    }

    public static float inSine(float x) {
        return 1.0F - Mth.cos((double) (x * ((float) Math.PI / 2F)));
    }

    public static float inOutBounce(float x) {
        return x < 0.5F ? (1.0F - outBounce(1.0F - 2.0F * x)) / 2.0F : (1.0F + outBounce(2.0F * x - 1.0F)) / 2.0F;
    }

    public static float inOutCirc(float x) {
        return x < 0.5F ? (float) ((1.0D - Math.sqrt(1.0D - Math.pow(2.0D * (double) x, 2.0D))) / 2.0D) : (float) ((Math.sqrt(1.0D - Math.pow(-2.0D * (double) x + 2.0D, 2.0D)) + 1.0D) / 2.0D);
    }

    public static float inOutCubic(float x) {
        return x < 0.5F ? 4.0F * Mth.cube(x) : (float) (1.0D - Math.pow(-2.0D * (double) x + 2.0D, 3.0D) / 2.0D);
    }

    public static float inOutQuad(float x) {
        return x < 0.5F ? 2.0F * Mth.square(x) : (float) (1.0D - Math.pow(-2.0D * (double) x + 2.0D, 2.0D) / 2.0D);
    }

    public static float inOutQuart(float x) {
        return x < 0.5F ? 8.0F * Mth.square(Mth.square(x)) : (float) (1.0D - Math.pow(-2.0D * (double) x + 2.0D, 4.0D) / 2.0D);
    }

    public static float inOutQuint(float x) {
        return (double) x < 0.5D ? 16.0F * x * x * x * x * x : (float) (1.0D - Math.pow(-2.0D * (double) x + 2.0D, 5.0D) / 2.0D);
    }

    public static float outBounce(float x) {
        float f1 = 7.5625F;
        float f2 = 2.75F;

        return x < 0.36363637F ? 7.5625F * Mth.square(x) : (x < 0.72727275F ? 7.5625F * Mth.square(x - 0.54545456F) + 0.75F : ((double) x < 0.9090909090909091D ? 7.5625F * Mth.square(x - 0.8181818F) + 0.9375F : 7.5625F * Mth.square(x - 0.95454544F) + 0.984375F));
    }

    public static float outElastic(float x) {
        float f1 = 2.0943952F;

        return x == 0.0F ? 0.0F : (x == 1.0F ? 1.0F : (float) (Math.pow(2.0D, -10.0D * (double) x) * Math.sin(((double) x * 10.0D - 0.75D) * (double) 2.0943952F) + 1.0D));
    }

    public static float outExpo(float x) {
        return x == 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0D, -10.0D * (double) x);
    }

    public static float outQuad(float x) {
        return 1.0F - Mth.square(1.0F - x);
    }

    public static float outQuint(float x) {
        return 1.0F - (float) Math.pow(1.0D - (double) x, 5.0D);
    }

    public static float outSine(float x) {
        return Mth.sin((double) (x * ((float) Math.PI / 2F)));
    }

    public static float inOutSine(float x) {
        return -(Mth.cos((double) ((float) Math.PI * x)) - 1.0F) / 2.0F;
    }

    public static float outBack(float x) {
        float f1 = 1.70158F;
        float f2 = 2.70158F;

        return 1.0F + 2.70158F * Mth.cube(x - 1.0F) + 1.70158F * Mth.square(x - 1.0F);
    }

    public static float outQuart(float x) {
        return 1.0F - Mth.square(Mth.square(1.0F - x));
    }

    public static float outCubic(float x) {
        return 1.0F - Mth.cube(1.0F - x);
    }

    public static float inOutExpo(float x) {
        return x < 0.5F ? (x == 0.0F ? 0.0F : (float) (Math.pow(2.0D, 20.0D * (double) x - 10.0D) / 2.0D)) : (x == 1.0F ? 1.0F : (float) ((2.0D - Math.pow(2.0D, -20.0D * (double) x + 10.0D)) / 2.0D));
    }

    public static float inQuad(float x) {
        return x * x;
    }

    public static float outCirc(float x) {
        return (float) Math.sqrt((double) (1.0F - Mth.square(x - 1.0F)));
    }

    public static float inOutElastic(float x) {
        float f1 = 1.3962635F;

        if (x == 0.0F) {
            return 0.0F;
        } else if (x == 1.0F) {
            return 1.0F;
        } else {
            double d0 = Math.sin((20.0D * (double) x - 11.125D) * (double) 1.3962635F);

            return x < 0.5F ? (float) (-(Math.pow(2.0D, 20.0D * (double) x - 10.0D) * d0) / 2.0D) : (float) (Math.pow(2.0D, -20.0D * (double) x + 10.0D) * d0 / 2.0D + 1.0D);
        }
    }

    public static float inCirc(float x) {
        return (float) (-Math.sqrt((double) (1.0F - x * x))) + 1.0F;
    }

    public static float inOutBack(float x) {
        float f1 = 1.70158F;
        float f2 = 2.5949094F;

        if (x < 0.5F) {
            return 4.0F * x * x * (7.189819F * x - 2.5949094F) / 2.0F;
        } else {
            float f3 = 2.0F * x - 2.0F;

            return (f3 * f3 * (3.5949094F * f3 + 2.5949094F) + 2.0F) / 2.0F;
        }
    }
}

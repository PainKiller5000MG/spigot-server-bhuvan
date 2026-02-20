package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public final class ImprovedNoise {

    private static final float SHIFT_UP_EPSILON = 1.0E-7F;
    private final byte[] p;
    public final double xo;
    public final double yo;
    public final double zo;

    public ImprovedNoise(RandomSource random) {
        this.xo = random.nextDouble() * 256.0D;
        this.yo = random.nextDouble() * 256.0D;
        this.zo = random.nextDouble() * 256.0D;
        this.p = new byte[256];

        for (int i = 0; i < 256; ++i) {
            this.p[i] = (byte) i;
        }

        for (int j = 0; j < 256; ++j) {
            int k = random.nextInt(256 - j);
            byte b0 = this.p[j];

            this.p[j] = this.p[j + k];
            this.p[j + k] = b0;
        }

    }

    public double noise(double _x, double _y, double _z) {
        return this.noise(_x, _y, _z, 0.0D, 0.0D);
    }

    /** @deprecated */
    @Deprecated
    public double noise(double _x, double _y, double _z, double yScale, double yFudge) {
        double d5 = _x + this.xo;
        double d6 = _y + this.yo;
        double d7 = _z + this.zo;
        int i = Mth.floor(d5);
        int j = Mth.floor(d6);
        int k = Mth.floor(d7);
        double d8 = d5 - (double) i;
        double d9 = d6 - (double) j;
        double d10 = d7 - (double) k;
        double d11;

        if (yScale != 0.0D) {
            double d12;

            if (yFudge >= 0.0D && yFudge < d9) {
                d12 = yFudge;
            } else {
                d12 = d9;
            }

            d11 = (double) Mth.floor(d12 / yScale + (double) 1.0E-7F) * yScale;
        } else {
            d11 = 0.0D;
        }

        return this.sampleAndLerp(i, j, k, d8, d9 - d11, d10, d9);
    }

    public double noiseWithDerivative(double _x, double _y, double _z, double[] derivativeOut) {
        double d3 = _x + this.xo;
        double d4 = _y + this.yo;
        double d5 = _z + this.zo;
        int i = Mth.floor(d3);
        int j = Mth.floor(d4);
        int k = Mth.floor(d5);
        double d6 = d3 - (double) i;
        double d7 = d4 - (double) j;
        double d8 = d5 - (double) k;

        return this.sampleWithDerivative(i, j, k, d6, d7, d8, derivativeOut);
    }

    private static double gradDot(int hash, double x, double y, double z) {
        return SimplexNoise.dot(SimplexNoise.GRADIENT[hash & 15], x, y, z);
    }

    private int p(int x) {
        return this.p[x & 255] & 255;
    }

    private double sampleAndLerp(int x, int y, int z, double xr, double yr, double zr, double yrOriginal) {
        int l = this.p(x);
        int i1 = this.p(x + 1);
        int j1 = this.p(l + y);
        int k1 = this.p(l + y + 1);
        int l1 = this.p(i1 + y);
        int i2 = this.p(i1 + y + 1);
        double d4 = gradDot(this.p(j1 + z), xr, yr, zr);
        double d5 = gradDot(this.p(l1 + z), xr - 1.0D, yr, zr);
        double d6 = gradDot(this.p(k1 + z), xr, yr - 1.0D, zr);
        double d7 = gradDot(this.p(i2 + z), xr - 1.0D, yr - 1.0D, zr);
        double d8 = gradDot(this.p(j1 + z + 1), xr, yr, zr - 1.0D);
        double d9 = gradDot(this.p(l1 + z + 1), xr - 1.0D, yr, zr - 1.0D);
        double d10 = gradDot(this.p(k1 + z + 1), xr, yr - 1.0D, zr - 1.0D);
        double d11 = gradDot(this.p(i2 + z + 1), xr - 1.0D, yr - 1.0D, zr - 1.0D);
        double d12 = Mth.smoothstep(xr);
        double d13 = Mth.smoothstep(yrOriginal);
        double d14 = Mth.smoothstep(zr);

        return Mth.lerp3(d12, d13, d14, d4, d5, d6, d7, d8, d9, d10, d11);
    }

    private double sampleWithDerivative(int x, int y, int z, double xr, double yr, double zr, double[] derivativeOut) {
        int l = this.p(x);
        int i1 = this.p(x + 1);
        int j1 = this.p(l + y);
        int k1 = this.p(l + y + 1);
        int l1 = this.p(i1 + y);
        int i2 = this.p(i1 + y + 1);
        int j2 = this.p(j1 + z);
        int k2 = this.p(l1 + z);
        int l2 = this.p(k1 + z);
        int i3 = this.p(i2 + z);
        int j3 = this.p(j1 + z + 1);
        int k3 = this.p(l1 + z + 1);
        int l3 = this.p(k1 + z + 1);
        int i4 = this.p(i2 + z + 1);
        int[] aint = SimplexNoise.GRADIENT[j2 & 15];
        int[] aint1 = SimplexNoise.GRADIENT[k2 & 15];
        int[] aint2 = SimplexNoise.GRADIENT[l2 & 15];
        int[] aint3 = SimplexNoise.GRADIENT[i3 & 15];
        int[] aint4 = SimplexNoise.GRADIENT[j3 & 15];
        int[] aint5 = SimplexNoise.GRADIENT[k3 & 15];
        int[] aint6 = SimplexNoise.GRADIENT[l3 & 15];
        int[] aint7 = SimplexNoise.GRADIENT[i4 & 15];
        double d3 = SimplexNoise.dot(aint, xr, yr, zr);
        double d4 = SimplexNoise.dot(aint1, xr - 1.0D, yr, zr);
        double d5 = SimplexNoise.dot(aint2, xr, yr - 1.0D, zr);
        double d6 = SimplexNoise.dot(aint3, xr - 1.0D, yr - 1.0D, zr);
        double d7 = SimplexNoise.dot(aint4, xr, yr, zr - 1.0D);
        double d8 = SimplexNoise.dot(aint5, xr - 1.0D, yr, zr - 1.0D);
        double d9 = SimplexNoise.dot(aint6, xr, yr - 1.0D, zr - 1.0D);
        double d10 = SimplexNoise.dot(aint7, xr - 1.0D, yr - 1.0D, zr - 1.0D);
        double d11 = Mth.smoothstep(xr);
        double d12 = Mth.smoothstep(yr);
        double d13 = Mth.smoothstep(zr);
        double d14 = Mth.lerp3(d11, d12, d13, (double) aint[0], (double) aint1[0], (double) aint2[0], (double) aint3[0], (double) aint4[0], (double) aint5[0], (double) aint6[0], (double) aint7[0]);
        double d15 = Mth.lerp3(d11, d12, d13, (double) aint[1], (double) aint1[1], (double) aint2[1], (double) aint3[1], (double) aint4[1], (double) aint5[1], (double) aint6[1], (double) aint7[1]);
        double d16 = Mth.lerp3(d11, d12, d13, (double) aint[2], (double) aint1[2], (double) aint2[2], (double) aint3[2], (double) aint4[2], (double) aint5[2], (double) aint6[2], (double) aint7[2]);
        double d17 = Mth.lerp2(d12, d13, d4 - d3, d6 - d5, d8 - d7, d10 - d9);
        double d18 = Mth.lerp2(d13, d11, d5 - d3, d9 - d7, d6 - d4, d10 - d8);
        double d19 = Mth.lerp2(d11, d12, d7 - d3, d8 - d4, d9 - d5, d10 - d6);
        double d20 = Mth.smoothstepDerivative(xr);
        double d21 = Mth.smoothstepDerivative(yr);
        double d22 = Mth.smoothstepDerivative(zr);
        double d23 = d14 + d20 * d17;
        double d24 = d15 + d21 * d18;
        double d25 = d16 + d22 * d19;

        derivativeOut[0] += d23;
        derivativeOut[1] += d24;
        derivativeOut[2] += d25;
        return Mth.lerp3(d11, d12, d13, d3, d4, d5, d6, d7, d8, d9, d10);
    }

    @VisibleForTesting
    public void parityConfigString(StringBuilder sb) {
        NoiseUtils.parityNoiseOctaveConfigString(sb, this.xo, this.yo, this.zo, this.p);
    }
}

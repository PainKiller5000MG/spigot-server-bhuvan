package net.minecraft.world.attribute;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class GaussianSampler {

    private static final int GAUSSIAN_SAMPLE_RADIUS = 2;
    private static final int GAUSSIAN_SAMPLE_BREADTH = 6;
    private static final double[] GAUSSIAN_SAMPLE_KERNEL = new double[]{0.0D, 1.0D, 4.0D, 6.0D, 4.0D, 1.0D, 0.0D};

    public GaussianSampler() {}

    public static <V> void sample(Vec3 position, GaussianSampler.Sampler<V> sampler, GaussianSampler.Accumulator<V> accumulator) {
        position = position.subtract(0.5D, 0.5D, 0.5D);
        int i = Mth.floor(position.x());
        int j = Mth.floor(position.y());
        int k = Mth.floor(position.z());
        double d0 = position.x() - (double) i;
        double d1 = position.y() - (double) j;
        double d2 = position.z() - (double) k;

        for (int l = 0; l < 6; ++l) {
            double d3 = Mth.lerp(d2, GaussianSampler.GAUSSIAN_SAMPLE_KERNEL[l + 1], GaussianSampler.GAUSSIAN_SAMPLE_KERNEL[l]);
            int i1 = k - 2 + l;

            for (int j1 = 0; j1 < 6; ++j1) {
                double d4 = Mth.lerp(d0, GaussianSampler.GAUSSIAN_SAMPLE_KERNEL[j1 + 1], GaussianSampler.GAUSSIAN_SAMPLE_KERNEL[j1]);
                int k1 = i - 2 + j1;

                for (int l1 = 0; l1 < 6; ++l1) {
                    double d5 = Mth.lerp(d1, GaussianSampler.GAUSSIAN_SAMPLE_KERNEL[l1 + 1], GaussianSampler.GAUSSIAN_SAMPLE_KERNEL[l1]);
                    int i2 = j - 2 + l1;
                    double d6 = d4 * d5 * d3;
                    V v0 = sampler.get(k1, i2, i1);

                    accumulator.accumulate(d6, v0);
                }
            }
        }

    }

    @FunctionalInterface
    public interface Accumulator<V> {

        void accumulate(double weight, V value);
    }

    @FunctionalInterface
    public interface Sampler<V> {

        V get(int x, int y, int z);
    }
}

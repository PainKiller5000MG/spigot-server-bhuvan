package net.minecraft.world.level;

public interface ColorMapColorUtil {

    static int get(double temp, double rain, int[] pixels, int defaultMapColor) {
        rain *= temp;
        int j = (int) ((1.0D - temp) * 255.0D);
        int k = (int) ((1.0D - rain) * 255.0D);
        int l = k << 8 | j;

        return l >= pixels.length ? defaultMapColor : pixels[l];
    }
}

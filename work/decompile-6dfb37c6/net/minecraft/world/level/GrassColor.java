package net.minecraft.world.level;

public class GrassColor {

    private static int[] pixels = new int[65536];

    public GrassColor() {}

    public static void init(int[] pixels) {
        GrassColor.pixels = pixels;
    }

    public static int get(double temp, double rain) {
        return ColorMapColorUtil.get(temp, rain, GrassColor.pixels, -65281);
    }

    public static int getDefaultColor() {
        return get(0.5D, 1.0D);
    }
}

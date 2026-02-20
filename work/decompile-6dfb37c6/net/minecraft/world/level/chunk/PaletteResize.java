package net.minecraft.world.level.chunk;

public interface PaletteResize<T> {

    int onResize(int bits, T lastAddedValue);

    static <T> PaletteResize<T> noResizeExpected() {
        return (i, object) -> {
            throw new IllegalArgumentException("Unexpected palette resize, bits = " + i + ", added value = " + String.valueOf(object));
        };
    }
}

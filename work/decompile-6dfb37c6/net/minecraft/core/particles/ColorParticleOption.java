package net.minecraft.core.particles;

import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;

public class ColorParticleOption implements ParticleOptions {

    private final ParticleType<ColorParticleOption> type;
    private final int color;

    public static MapCodec<ColorParticleOption> codec(ParticleType<ColorParticleOption> type) {
        return ExtraCodecs.ARGB_COLOR_CODEC.xmap((integer) -> {
            return new ColorParticleOption(type, integer);
        }, (colorparticleoption) -> {
            return colorparticleoption.color;
        }).fieldOf("color");
    }

    public static StreamCodec<? super ByteBuf, ColorParticleOption> streamCodec(ParticleType<ColorParticleOption> type) {
        return ByteBufCodecs.INT.map((integer) -> {
            return new ColorParticleOption(type, integer);
        }, (colorparticleoption) -> {
            return colorparticleoption.color;
        });
    }

    private ColorParticleOption(ParticleType<ColorParticleOption> type, int color) {
        this.type = type;
        this.color = color;
    }

    @Override
    public ParticleType<ColorParticleOption> getType() {
        return this.type;
    }

    public float getRed() {
        return (float) ARGB.red(this.color) / 255.0F;
    }

    public float getGreen() {
        return (float) ARGB.green(this.color) / 255.0F;
    }

    public float getBlue() {
        return (float) ARGB.blue(this.color) / 255.0F;
    }

    public float getAlpha() {
        return (float) ARGB.alpha(this.color) / 255.0F;
    }

    public static ColorParticleOption create(ParticleType<ColorParticleOption> type, int color) {
        return new ColorParticleOption(type, color);
    }

    public static ColorParticleOption create(ParticleType<ColorParticleOption> type, float red, float green, float blue) {
        return create(type, ARGB.colorFromFloat(1.0F, red, green, blue));
    }
}

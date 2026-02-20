package net.minecraft.core.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;

public class SpellParticleOption implements ParticleOptions {

    private final ParticleType<SpellParticleOption> type;
    private final int color;
    private final float power;

    public static MapCodec<SpellParticleOption> codec(ParticleType<SpellParticleOption> type) {
        return RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(ExtraCodecs.RGB_COLOR_CODEC.optionalFieldOf("color", -1).forGetter((spellparticleoption) -> {
                return spellparticleoption.color;
            }), Codec.FLOAT.optionalFieldOf("power", 1.0F).forGetter((spellparticleoption) -> {
                return spellparticleoption.power;
            })).apply(instance, (integer, ofloat) -> {
                return new SpellParticleOption(type, integer, ofloat);
            });
        });
    }

    public static StreamCodec<? super ByteBuf, SpellParticleOption> streamCodec(ParticleType<SpellParticleOption> type) {
        return StreamCodec.composite(ByteBufCodecs.INT, (spellparticleoption) -> {
            return spellparticleoption.color;
        }, ByteBufCodecs.FLOAT, (spellparticleoption) -> {
            return spellparticleoption.power;
        }, (integer, ofloat) -> {
            return new SpellParticleOption(type, integer, ofloat);
        });
    }

    private SpellParticleOption(ParticleType<SpellParticleOption> type, int color, float power) {
        this.type = type;
        this.color = color;
        this.power = power;
    }

    @Override
    public ParticleType<SpellParticleOption> getType() {
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

    public float getPower() {
        return this.power;
    }

    public static SpellParticleOption create(ParticleType<SpellParticleOption> type, int color, float power) {
        return new SpellParticleOption(type, color, power);
    }

    public static SpellParticleOption create(ParticleType<SpellParticleOption> type, float red, float green, float blue, float power) {
        return create(type, ARGB.colorFromFloat(1.0F, red, green, blue), power);
    }
}

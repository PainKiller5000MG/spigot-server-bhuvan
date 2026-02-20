package net.minecraft.server.level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.PrimitiveCodec;
import java.util.Objects;
import java.util.function.IntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ByIdMap;

public enum ParticleStatus {

    ALL(0, "options.particles.all"), DECREASED(1, "options.particles.decreased"), MINIMAL(2, "options.particles.minimal");

    private static final IntFunction<ParticleStatus> BY_ID = ByIdMap.<ParticleStatus>continuous((particlestatus) -> {
        return particlestatus.id;
    }, values(), ByIdMap.OutOfBoundsStrategy.WRAP);
    public static final Codec<ParticleStatus> LEGACY_CODEC;
    private final int id;
    private final Component caption;

    private ParticleStatus(int id, String key) {
        this.id = id;
        this.caption = Component.translatable(key);
    }

    public Component caption() {
        return this.caption;
    }

    static {
        PrimitiveCodec primitivecodec = Codec.INT;
        IntFunction intfunction = ParticleStatus.BY_ID;

        Objects.requireNonNull(intfunction);
        LEGACY_CODEC = primitivecodec.xmap(intfunction::apply, (particlestatus) -> {
            return particlestatus.id;
        });
    }
}

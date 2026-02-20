package net.minecraft.world.entity.player;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

public enum PlayerModelType implements StringRepresentable {

    SLIM("slim", "slim"), WIDE("wide", "default");

    public static final Codec<PlayerModelType> CODEC = StringRepresentable.<PlayerModelType>fromEnum(PlayerModelType::values);
    private static final Function<String, PlayerModelType> NAME_LOOKUP = StringRepresentable.createNameLookup(values(), (playermodeltype) -> {
        return playermodeltype.legacyServicesId;
    });
    public static final StreamCodec<ByteBuf, PlayerModelType> STREAM_CODEC = ByteBufCodecs.BOOL.map((obool) -> {
        return obool ? PlayerModelType.SLIM : PlayerModelType.WIDE;
    }, (playermodeltype) -> {
        return playermodeltype == PlayerModelType.SLIM;
    });
    private final String id;
    private final String legacyServicesId;

    private PlayerModelType(String id, String legacyServicesId) {
        this.id = id;
        this.legacyServicesId = legacyServicesId;
    }

    public static PlayerModelType byLegacyServicesName(@Nullable String name) {
        return (PlayerModelType) Objects.requireNonNullElse((PlayerModelType) PlayerModelType.NAME_LOOKUP.apply(name), PlayerModelType.WIDE);
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }
}

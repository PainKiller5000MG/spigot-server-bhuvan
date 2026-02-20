package net.minecraft.world.entity.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.PrimitiveCodec;
import java.util.Objects;
import java.util.function.IntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ByIdMap;

public enum ChatVisiblity {

    FULL(0, "options.chat.visibility.full"), SYSTEM(1, "options.chat.visibility.system"), HIDDEN(2, "options.chat.visibility.hidden");

    private static final IntFunction<ChatVisiblity> BY_ID = ByIdMap.<ChatVisiblity>continuous((chatvisiblity) -> {
        return chatvisiblity.id;
    }, values(), ByIdMap.OutOfBoundsStrategy.WRAP);
    public static final Codec<ChatVisiblity> LEGACY_CODEC;
    private final int id;
    private final Component caption;

    private ChatVisiblity(int id, String key) {
        this.id = id;
        this.caption = Component.translatable(key);
    }

    public Component caption() {
        return this.caption;
    }

    static {
        PrimitiveCodec primitivecodec = Codec.INT;
        IntFunction intfunction = ChatVisiblity.BY_ID;

        Objects.requireNonNull(intfunction);
        LEGACY_CODEC = primitivecodec.xmap(intfunction::apply, (chatvisiblity) -> {
            return chatvisiblity.id;
        });
    }
}

package net.minecraft.util;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import java.util.function.Function;

public enum TriState implements StringRepresentable {

    TRUE("true"), FALSE("false"), DEFAULT("default");

    public static final Codec<TriState> CODEC = Codec.either(Codec.BOOL, StringRepresentable.fromEnum(TriState::values)).xmap((either) -> {
        return (TriState) either.map(TriState::from, Function.identity());
    }, (tristate) -> {
        Either either;

        switch (tristate.ordinal()) {
            case 0:
                either = Either.left(true);
                break;
            case 1:
                either = Either.left(false);
                break;
            case 2:
                either = Either.right(tristate);
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return either;
    });
    private final String name;

    private TriState(String name) {
        this.name = name;
    }

    public static TriState from(boolean value) {
        return value ? TriState.TRUE : TriState.FALSE;
    }

    public boolean toBoolean(boolean defaultValue) {
        boolean flag1;

        switch (this.ordinal()) {
            case 0:
                flag1 = true;
                break;
            case 1:
                flag1 = false;
                break;
            default:
                flag1 = defaultValue;
        }

        return flag1;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}

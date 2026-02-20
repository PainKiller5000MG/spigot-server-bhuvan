package net.minecraft.server.dialog;

import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

public enum DialogAction implements StringRepresentable {

    CLOSE(0, "close"), NONE(1, "none"), WAIT_FOR_RESPONSE(2, "wait_for_response");

    public static final IntFunction<DialogAction> BY_ID = ByIdMap.<DialogAction>continuous((dialogaction) -> {
        return dialogaction.id;
    }, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StringRepresentable.EnumCodec<DialogAction> CODEC = StringRepresentable.<DialogAction>fromEnum(DialogAction::values);
    public static final StreamCodec<ByteBuf, DialogAction> STREAM_CODEC = ByteBufCodecs.idMapper(DialogAction.BY_ID, (dialogaction) -> {
        return dialogaction.id;
    });
    private final int id;
    private final String name;

    private DialogAction(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public boolean willUnpause() {
        return this == DialogAction.CLOSE || this == DialogAction.WAIT_FOR_RESPONSE;
    }
}

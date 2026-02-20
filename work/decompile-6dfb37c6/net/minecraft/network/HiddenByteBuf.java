package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.ReferenceCounted;

public record HiddenByteBuf(ByteBuf contents) implements ReferenceCounted {

    public HiddenByteBuf(ByteBuf contents) {
        this.contents = ByteBufUtil.ensureAccessible(contents);
    }

    public static Object pack(Object msg) {
        if (msg instanceof ByteBuf bytebuf) {
            return new HiddenByteBuf(bytebuf);
        } else {
            return msg;
        }
    }

    public static Object unpack(Object msg) {
        if (msg instanceof HiddenByteBuf hiddenbytebuf) {
            return ByteBufUtil.ensureAccessible(hiddenbytebuf.contents);
        } else {
            return msg;
        }
    }

    public int refCnt() {
        return this.contents.refCnt();
    }

    public HiddenByteBuf retain() {
        this.contents.retain();
        return this;
    }

    public HiddenByteBuf retain(int increment) {
        this.contents.retain(increment);
        return this;
    }

    public HiddenByteBuf touch() {
        this.contents.touch();
        return this;
    }

    public HiddenByteBuf touch(Object hint) {
        this.contents.touch(hint);
        return this;
    }

    public boolean release() {
        return this.contents.release();
    }

    public boolean release(int decrement) {
        return this.contents.release(decrement);
    }
}

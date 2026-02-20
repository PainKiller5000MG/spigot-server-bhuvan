package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToByteEncoder;

@Sharable
public class Varint21LengthFieldPrepender extends MessageToByteEncoder<ByteBuf> {

    public static final int MAX_VARINT21_BYTES = 3;

    public Varint21LengthFieldPrepender() {}

    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        int i = msg.readableBytes();
        int j = VarInt.getByteSize(i);

        if (j > 3) {
            throw new EncoderException("Packet too large: size " + i + " is over 8");
        } else {
            out.ensureWritable(j + i);
            VarInt.write(out, i);
            out.writeBytes(msg, msg.readerIndex(), i);
        }
    }
}

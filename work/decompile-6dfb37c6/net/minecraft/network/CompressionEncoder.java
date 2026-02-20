package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.zip.Deflater;

public class CompressionEncoder extends MessageToByteEncoder<ByteBuf> {

    private final byte[] encodeBuf = new byte[8192];
    private final Deflater deflater;
    private int threshold;

    public CompressionEncoder(int threshold) {
        this.threshold = threshold;
        this.deflater = new Deflater();
    }

    protected void encode(ChannelHandlerContext ctx, ByteBuf uncompressed, ByteBuf out) {
        int i = uncompressed.readableBytes();

        if (i > 8388608) {
            throw new IllegalArgumentException("Packet too big (is " + i + ", should be less than 8388608)");
        } else {
            if (i < this.threshold) {
                VarInt.write(out, 0);
                out.writeBytes(uncompressed);
            } else {
                byte[] abyte = new byte[i];

                uncompressed.readBytes(abyte);
                VarInt.write(out, abyte.length);
                this.deflater.setInput(abyte, 0, i);
                this.deflater.finish();

                while (!this.deflater.finished()) {
                    int j = this.deflater.deflate(this.encodeBuf);

                    out.writeBytes(this.encodeBuf, 0, j);
                }

                this.deflater.reset();
            }

        }
    }

    public int getThreshold() {
        return this.threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }
}

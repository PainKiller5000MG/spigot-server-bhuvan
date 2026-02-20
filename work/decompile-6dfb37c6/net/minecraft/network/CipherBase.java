package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;

public class CipherBase {

    private final Cipher cipher;
    private byte[] heapIn = new byte[0];
    private byte[] heapOut = new byte[0];

    protected CipherBase(Cipher cipher) {
        this.cipher = cipher;
    }

    private byte[] bufToByte(ByteBuf in) {
        int i = in.readableBytes();

        if (this.heapIn.length < i) {
            this.heapIn = new byte[i];
        }

        in.readBytes(this.heapIn, 0, i);
        return this.heapIn;
    }

    protected ByteBuf decipher(ChannelHandlerContext ctx, ByteBuf in) throws ShortBufferException {
        int i = in.readableBytes();
        byte[] abyte = this.bufToByte(in);
        ByteBuf bytebuf1 = ctx.alloc().heapBuffer(this.cipher.getOutputSize(i));

        bytebuf1.writerIndex(this.cipher.update(abyte, 0, i, bytebuf1.array(), bytebuf1.arrayOffset()));
        return bytebuf1;
    }

    protected void encipher(ByteBuf in, ByteBuf out) throws ShortBufferException {
        int i = in.readableBytes();
        byte[] abyte = this.bufToByte(in);
        int j = this.cipher.getOutputSize(i);

        if (this.heapOut.length < j) {
            this.heapOut = new byte[j];
        }

        out.writeBytes(this.heapOut, 0, this.cipher.update(abyte, 0, i, this.heapOut));
    }
}

package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class CompressionDecoder extends ByteToMessageDecoder {

    public static final int MAXIMUM_COMPRESSED_LENGTH = 2097152;
    public static final int MAXIMUM_UNCOMPRESSED_LENGTH = 8388608;
    private final Inflater inflater;
    private int threshold;
    private boolean validateDecompressed;

    public CompressionDecoder(int threshold, boolean validateDecompressed) {
        this.threshold = threshold;
        this.validateDecompressed = validateDecompressed;
        this.inflater = new Inflater();
    }

    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int i = VarInt.read(in);

        if (i == 0) {
            out.add(in.readBytes(in.readableBytes()));
        } else {
            if (this.validateDecompressed) {
                if (i < this.threshold) {
                    throw new DecoderException("Badly compressed packet - size of " + i + " is below server threshold of " + this.threshold);
                }

                if (i > 8388608) {
                    throw new DecoderException("Badly compressed packet - size of " + i + " is larger than protocol maximum of 8388608");
                }
            }

            this.setupInflaterInput(in);
            ByteBuf bytebuf1 = this.inflate(ctx, i);

            this.inflater.reset();
            out.add(bytebuf1);
        }
    }

    private void setupInflaterInput(ByteBuf in) {
        ByteBuffer bytebuffer;

        if (in.nioBufferCount() > 0) {
            bytebuffer = in.nioBuffer();
            in.skipBytes(in.readableBytes());
        } else {
            bytebuffer = ByteBuffer.allocateDirect(in.readableBytes());
            in.readBytes(bytebuffer);
            bytebuffer.flip();
        }

        this.inflater.setInput(bytebuffer);
    }

    private ByteBuf inflate(ChannelHandlerContext ctx, int uncompressedLength) throws DataFormatException {
        ByteBuf bytebuf = ctx.alloc().directBuffer(uncompressedLength);

        try {
            ByteBuffer bytebuffer = bytebuf.internalNioBuffer(0, uncompressedLength);
            int j = bytebuffer.position();

            this.inflater.inflate(bytebuffer);
            int k = bytebuffer.position() - j;

            if (k != uncompressedLength) {
                throw new DecoderException("Badly compressed packet - actual length of uncompressed payload " + k + " is does not match declared size " + uncompressedLength);
            } else {
                bytebuf.writerIndex(bytebuf.writerIndex() + k);
                return bytebuf;
            }
        } catch (Exception exception) {
            bytebuf.release();
            throw exception;
        }
    }

    public void setThreshold(int threshold, boolean validateDecompressed) {
        this.threshold = threshold;
        this.validateDecompressed = validateDecompressed;
    }
}

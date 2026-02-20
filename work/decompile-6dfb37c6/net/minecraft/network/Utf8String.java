package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.nio.charset.StandardCharsets;

public class Utf8String {

    public Utf8String() {}

    public static String read(ByteBuf input, int maxLength) {
        int j = ByteBufUtil.utf8MaxBytes(maxLength);
        int k = VarInt.read(input);

        if (k > j) {
            throw new DecoderException("The received encoded string buffer length is longer than maximum allowed (" + k + " > " + j + ")");
        } else if (k < 0) {
            throw new DecoderException("The received encoded string buffer length is less than zero! Weird string!");
        } else {
            int l = input.readableBytes();

            if (k > l) {
                throw new DecoderException("Not enough bytes in buffer, expected " + k + ", but got " + l);
            } else {
                String s = input.toString(input.readerIndex(), k, StandardCharsets.UTF_8);

                input.readerIndex(input.readerIndex() + k);
                if (s.length() > maxLength) {
                    int i1 = s.length();

                    throw new DecoderException("The received string length is longer than maximum allowed (" + i1 + " > " + maxLength + ")");
                } else {
                    return s;
                }
            }
        }
    }

    public static void write(ByteBuf output, CharSequence value, int maxLength) {
        if (value.length() > maxLength) {
            int j = value.length();

            throw new EncoderException("String too big (was " + j + " characters, max " + maxLength + ")");
        } else {
            int k = ByteBufUtil.utf8MaxBytes(value);
            ByteBuf bytebuf1 = output.alloc().buffer(k);

            try {
                int l = ByteBufUtil.writeUtf8(bytebuf1, value);
                int i1 = ByteBufUtil.utf8MaxBytes(maxLength);

                if (l > i1) {
                    throw new EncoderException("String too big (was " + l + " bytes encoded, max " + i1 + ")");
                }

                VarInt.write(output, l);
                output.writeBytes(bytebuf1);
            } finally {
                bytebuf1.release();
            }

        }
    }
}

package net.minecraft.server.rcon;

import java.nio.charset.StandardCharsets;

public class PktUtils {

    public static final int MAX_PACKET_SIZE = 1460;
    public static final char[] HEX_CHAR = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    public PktUtils() {}

    public static String stringFromByteArray(byte[] b, int offset, int length) {
        int k = length - 1;

        int l;

        for (l = offset > k ? k : offset; 0 != b[l] && l < k; ++l) {
            ;
        }

        return new String(b, offset, l - offset, StandardCharsets.UTF_8);
    }

    public static int intFromByteArray(byte[] b, int offset) {
        return intFromByteArray(b, offset, b.length);
    }

    public static int intFromByteArray(byte[] b, int offset, int length) {
        return 0 > length - offset - 4 ? 0 : b[offset + 3] << 24 | (b[offset + 2] & 255) << 16 | (b[offset + 1] & 255) << 8 | b[offset] & 255;
    }

    public static int intFromNetworkByteArray(byte[] b, int offset, int length) {
        return 0 > length - offset - 4 ? 0 : b[offset] << 24 | (b[offset + 1] & 255) << 16 | (b[offset + 2] & 255) << 8 | b[offset + 3] & 255;
    }

    public static String toHexString(byte b) {
        char c0 = PktUtils.HEX_CHAR[(b & 240) >>> 4];

        return "" + c0 + PktUtils.HEX_CHAR[b & 15];
    }
}

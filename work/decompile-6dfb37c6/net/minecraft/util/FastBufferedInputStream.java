package net.minecraft.util;

import java.io.IOException;
import java.io.InputStream;

public class FastBufferedInputStream extends InputStream {

    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private final InputStream in;
    private final byte[] buffer;
    private int limit;
    private int position;

    public FastBufferedInputStream(InputStream in) {
        this(in, 8192);
    }

    public FastBufferedInputStream(InputStream in, int bufferSize) {
        this.in = in;
        this.buffer = new byte[bufferSize];
    }

    public int read() throws IOException {
        if (this.position >= this.limit) {
            this.fill();
            if (this.position >= this.limit) {
                return -1;
            }
        }

        return Byte.toUnsignedInt(this.buffer[this.position++]);
    }

    public int read(byte[] output, int offset, int length) throws IOException {
        int k = this.bytesInBuffer();

        if (k <= 0) {
            if (length >= this.buffer.length) {
                return this.in.read(output, offset, length);
            }

            this.fill();
            k = this.bytesInBuffer();
            if (k <= 0) {
                return -1;
            }
        }

        if (length > k) {
            length = k;
        }

        System.arraycopy(this.buffer, this.position, output, offset, length);
        this.position += length;
        return length;
    }

    public long skip(long count) throws IOException {
        if (count <= 0L) {
            return 0L;
        } else {
            long j = (long) this.bytesInBuffer();

            if (j <= 0L) {
                return this.in.skip(count);
            } else {
                if (count > j) {
                    count = j;
                }

                this.position = (int) ((long) this.position + count);
                return count;
            }
        }
    }

    public int available() throws IOException {
        return this.bytesInBuffer() + this.in.available();
    }

    public void close() throws IOException {
        this.in.close();
    }

    private int bytesInBuffer() {
        return this.limit - this.position;
    }

    private void fill() throws IOException {
        this.limit = 0;
        this.position = 0;
        int i = this.in.read(this.buffer, 0, this.buffer.length);

        if (i > 0) {
            this.limit = i;
        }

    }
}

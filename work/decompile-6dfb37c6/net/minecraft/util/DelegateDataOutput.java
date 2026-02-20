package net.minecraft.util;

import java.io.DataOutput;
import java.io.IOException;
import net.minecraft.SuppressForbidden;

public class DelegateDataOutput implements DataOutput {

    private final DataOutput parent;

    public DelegateDataOutput(DataOutput parent) {
        this.parent = parent;
    }

    public void write(int b) throws IOException {
        this.parent.write(b);
    }

    public void write(byte[] b) throws IOException {
        this.parent.write(b);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        this.parent.write(b, off, len);
    }

    public void writeBoolean(boolean v) throws IOException {
        this.parent.writeBoolean(v);
    }

    public void writeByte(int v) throws IOException {
        this.parent.writeByte(v);
    }

    public void writeShort(int v) throws IOException {
        this.parent.writeShort(v);
    }

    public void writeChar(int v) throws IOException {
        this.parent.writeChar(v);
    }

    public void writeInt(int v) throws IOException {
        this.parent.writeInt(v);
    }

    public void writeLong(long v) throws IOException {
        this.parent.writeLong(v);
    }

    public void writeFloat(float v) throws IOException {
        this.parent.writeFloat(v);
    }

    public void writeDouble(double v) throws IOException {
        this.parent.writeDouble(v);
    }

    @SuppressForbidden(reason = "Delegation is not use")
    public void writeBytes(String s) throws IOException {
        this.parent.writeBytes(s);
    }

    public void writeChars(String s) throws IOException {
        this.parent.writeChars(s);
    }

    public void writeUTF(String s) throws IOException {
        this.parent.writeUTF(s);
    }
}

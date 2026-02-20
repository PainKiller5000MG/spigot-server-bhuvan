package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public record IntTag(int value) implements NumericTag {

    private static final int SELF_SIZE_IN_BYTES = 12;
    public static final TagType<IntTag> TYPE = new TagType.StaticSize<IntTag>() {
        @Override
        public IntTag load(DataInput input, NbtAccounter accounter) throws IOException {
            return IntTag.valueOf(readAccounted(input, accounter));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor output, NbtAccounter accounter) throws IOException {
            return output.visit(readAccounted(input, accounter));
        }

        private static int readAccounted(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(12L);
            return input.readInt();
        }

        @Override
        public int size() {
            return 4;
        }

        @Override
        public String getName() {
            return "INT";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Int";
        }
    };

    public static IntTag valueOf(int i) {
        return i >= -128 && i <= 1024 ? IntTag.Cache.cache[i - Byte.MIN_VALUE] : new IntTag(i);
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeInt(this.value);
    }

    @Override
    public int sizeInBytes() {
        return 12;
    }

    @Override
    public byte getId() {
        return 3;
    }

    @Override
    public TagType<IntTag> getType() {
        return IntTag.TYPE;
    }

    @Override
    public IntTag copy() {
        return this;
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitInt(this);
    }

    @Override
    public long longValue() {
        return (long) this.value;
    }

    @Override
    public int intValue() {
        return this.value;
    }

    @Override
    public short shortValue() {
        return (short) (this.value & '\uffff');
    }

    @Override
    public byte byteValue() {
        return (byte) (this.value & 255);
    }

    @Override
    public double doubleValue() {
        return (double) this.value;
    }

    @Override
    public float floatValue() {
        return (float) this.value;
    }

    @Override
    public Number box() {
        return this.value;
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        return visitor.visit(this.value);
    }

    @Override
    public String toString() {
        StringTagVisitor stringtagvisitor = new StringTagVisitor();

        stringtagvisitor.visitInt(this);
        return stringtagvisitor.build();
    }

    private static class Cache {

        private static final int HIGH = 1024;
        private static final int LOW = -128;
        static final IntTag[] cache = new IntTag[1153];

        private Cache() {}

        static {
            for (int i = 0; i < IntTag.Cache.cache.length; ++i) {
                IntTag.Cache.cache[i] = new IntTag(Byte.MIN_VALUE + i);
            }

        }
    }
}

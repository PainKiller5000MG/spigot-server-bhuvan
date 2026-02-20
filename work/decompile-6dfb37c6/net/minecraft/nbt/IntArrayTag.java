package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.apache.commons.lang3.ArrayUtils;

public final class IntArrayTag implements CollectionTag {

    private static final int SELF_SIZE_IN_BYTES = 24;
    public static final TagType<IntArrayTag> TYPE = new TagType.VariableSize<IntArrayTag>() {
        @Override
        public IntArrayTag load(DataInput input, NbtAccounter accounter) throws IOException {
            return new IntArrayTag(readAccounted(input, accounter));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor output, NbtAccounter accounter) throws IOException {
            return output.visit(readAccounted(input, accounter));
        }

        private static int[] readAccounted(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(24L);
            int i = input.readInt();

            accounter.accountBytes(4L, (long) i);
            int[] aint = new int[i];

            for (int j = 0; j < i; ++j) {
                aint[j] = input.readInt();
            }

            return aint;
        }

        @Override
        public void skip(DataInput input, NbtAccounter accounter) throws IOException {
            input.skipBytes(input.readInt() * 4);
        }

        @Override
        public String getName() {
            return "INT[]";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Int_Array";
        }
    };
    private int[] data;

    public IntArrayTag(int[] data) {
        this.data = data;
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeInt(this.data.length);

        for (int i : this.data) {
            output.writeInt(i);
        }

    }

    @Override
    public int sizeInBytes() {
        return 24 + 4 * this.data.length;
    }

    @Override
    public byte getId() {
        return 11;
    }

    @Override
    public TagType<IntArrayTag> getType() {
        return IntArrayTag.TYPE;
    }

    @Override
    public String toString() {
        StringTagVisitor stringtagvisitor = new StringTagVisitor();

        stringtagvisitor.visitIntArray(this);
        return stringtagvisitor.build();
    }

    @Override
    public IntArrayTag copy() {
        int[] aint = new int[this.data.length];

        System.arraycopy(this.data, 0, aint, 0, this.data.length);
        return new IntArrayTag(aint);
    }

    public boolean equals(Object obj) {
        return this == obj ? true : obj instanceof IntArrayTag && Arrays.equals(this.data, ((IntArrayTag) obj).data);
    }

    public int hashCode() {
        return Arrays.hashCode(this.data);
    }

    public int[] getAsIntArray() {
        return this.data;
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitIntArray(this);
    }

    @Override
    public int size() {
        return this.data.length;
    }

    @Override
    public IntTag get(int index) {
        return IntTag.valueOf(this.data[index]);
    }

    @Override
    public boolean setTag(int index, Tag tag) {
        if (tag instanceof NumericTag numerictag) {
            this.data[index] = numerictag.intValue();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addTag(int index, Tag tag) {
        if (tag instanceof NumericTag numerictag) {
            this.data = ArrayUtils.add(this.data, index, numerictag.intValue());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public IntTag remove(int index) {
        int j = this.data[index];

        this.data = ArrayUtils.remove(this.data, index);
        return IntTag.valueOf(j);
    }

    @Override
    public void clear() {
        this.data = new int[0];
    }

    @Override
    public Optional<int[]> asIntArray() {
        return Optional.of(this.data);
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        return visitor.visit(this.data);
    }
}

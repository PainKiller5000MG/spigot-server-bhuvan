package net.minecraft.nbt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

public class StringTagVisitor implements TagVisitor {

    private static final Pattern UNQUOTED_KEY_MATCH = Pattern.compile("[A-Za-z._]+[A-Za-z0-9._+-]*");
    private final StringBuilder builder = new StringBuilder();

    public StringTagVisitor() {}

    public String build() {
        return this.builder.toString();
    }

    @Override
    public void visitString(StringTag tag) {
        this.builder.append(StringTag.quoteAndEscape(tag.value()));
    }

    @Override
    public void visitByte(ByteTag tag) {
        this.builder.append(tag.value()).append('b');
    }

    @Override
    public void visitShort(ShortTag tag) {
        this.builder.append(tag.value()).append('s');
    }

    @Override
    public void visitInt(IntTag tag) {
        this.builder.append(tag.value());
    }

    @Override
    public void visitLong(LongTag tag) {
        this.builder.append(tag.value()).append('L');
    }

    @Override
    public void visitFloat(FloatTag tag) {
        this.builder.append(tag.value()).append('f');
    }

    @Override
    public void visitDouble(DoubleTag tag) {
        this.builder.append(tag.value()).append('d');
    }

    @Override
    public void visitByteArray(ByteArrayTag tag) {
        this.builder.append("[B;");
        byte[] abyte = tag.getAsByteArray();

        for (int i = 0; i < abyte.length; ++i) {
            if (i != 0) {
                this.builder.append(',');
            }

            this.builder.append(abyte[i]).append('B');
        }

        this.builder.append(']');
    }

    @Override
    public void visitIntArray(IntArrayTag tag) {
        this.builder.append("[I;");
        int[] aint = tag.getAsIntArray();

        for (int i = 0; i < aint.length; ++i) {
            if (i != 0) {
                this.builder.append(',');
            }

            this.builder.append(aint[i]);
        }

        this.builder.append(']');
    }

    @Override
    public void visitLongArray(LongArrayTag tag) {
        this.builder.append("[L;");
        long[] along = tag.getAsLongArray();

        for (int i = 0; i < along.length; ++i) {
            if (i != 0) {
                this.builder.append(',');
            }

            this.builder.append(along[i]).append('L');
        }

        this.builder.append(']');
    }

    @Override
    public void visitList(ListTag tag) {
        this.builder.append('[');

        for (int i = 0; i < tag.size(); ++i) {
            if (i != 0) {
                this.builder.append(',');
            }

            tag.get(i).accept((TagVisitor) this);
        }

        this.builder.append(']');
    }

    @Override
    public void visitCompound(CompoundTag tag) {
        this.builder.append('{');
        List<Map.Entry<String, Tag>> list = new ArrayList(tag.entrySet());

        list.sort(Entry.comparingByKey());

        for (int i = 0; i < ((List) list).size(); ++i) {
            Map.Entry<String, Tag> map_entry = (Entry) list.get(i);

            if (i != 0) {
                this.builder.append(',');
            }

            this.handleKeyEscape((String) map_entry.getKey());
            this.builder.append(':');
            ((Tag) map_entry.getValue()).accept((TagVisitor) this);
        }

        this.builder.append('}');
    }

    private void handleKeyEscape(String input) {
        if (!input.equalsIgnoreCase("true") && !input.equalsIgnoreCase("false") && StringTagVisitor.UNQUOTED_KEY_MATCH.matcher(input).matches()) {
            this.builder.append(input);
        } else {
            StringTag.quoteAndEscape(input, this.builder);
        }

    }

    @Override
    public void visitEnd(EndTag tag) {
        this.builder.append("END");
    }
}

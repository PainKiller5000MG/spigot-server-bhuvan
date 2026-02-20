package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class ListTag extends AbstractList<Tag> implements CollectionTag {

    private static final String WRAPPER_MARKER = "";
    private static final int SELF_SIZE_IN_BYTES = 36;
    public static final TagType<ListTag> TYPE = new TagType.VariableSize<ListTag>() {
        @Override
        public ListTag load(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            ListTag listtag;

            try {
                listtag = loadList(input, accounter);
            } finally {
                accounter.popDepth();
            }

            return listtag;
        }

        private static ListTag loadList(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(36L);
            byte b0 = input.readByte();
            int i = readListCount(input);

            if (b0 == 0 && i > 0) {
                throw new NbtFormatException("Missing type on ListTag");
            } else {
                accounter.accountBytes(4L, (long) i);
                TagType<?> tagtype = TagTypes.getType(b0);
                ListTag listtag = new ListTag(new ArrayList(i));

                for (int j = 0; j < i; ++j) {
                    listtag.addAndUnwrap(tagtype.load(input, accounter));
                }

                return listtag;
            }
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor output, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            StreamTagVisitor.ValueResult streamtagvisitor_valueresult;

            try {
                streamtagvisitor_valueresult = parseList(input, output, accounter);
            } finally {
                accounter.popDepth();
            }

            return streamtagvisitor_valueresult;
        }

        private static StreamTagVisitor.ValueResult parseList(DataInput input, StreamTagVisitor output, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(36L);
            TagType<?> tagtype = TagTypes.getType(input.readByte());
            int i = readListCount(input);

            switch (output.visitList(tagtype, i)) {
                case HALT:
                    return StreamTagVisitor.ValueResult.HALT;
                case BREAK:
                    tagtype.skip(input, i, accounter);
                    return output.visitContainerEnd();
                default:
                    accounter.accountBytes(4L, (long) i);
                    int j = 0;

                    while (true) {
                        label45:
                        {
                            if (j < i) {
                                switch (output.visitElement(tagtype, j)) {
                                    case HALT:
                                        return StreamTagVisitor.ValueResult.HALT;
                                    case BREAK:
                                        tagtype.skip(input, accounter);
                                        break;
                                    case SKIP:
                                        tagtype.skip(input, accounter);
                                        break label45;
                                    default:
                                        switch (tagtype.parse(input, output, accounter)) {
                                            case HALT:
                                                return StreamTagVisitor.ValueResult.HALT;
                                            case BREAK:
                                                break;
                                            default:
                                                break label45;
                                        }
                                }
                            }

                            int k = i - 1 - j;

                            if (k > 0) {
                                tagtype.skip(input, k, accounter);
                            }

                            return output.visitContainerEnd();
                        }

                        ++j;
                    }
            }
        }

        private static int readListCount(DataInput input) throws IOException {
            int i = input.readInt();

            if (i < 0) {
                throw new NbtFormatException("ListTag length cannot be negative: " + i);
            } else {
                return i;
            }
        }

        @Override
        public void skip(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            try {
                TagType<?> tagtype = TagTypes.getType(input.readByte());
                int i = input.readInt();

                tagtype.skip(input, i, accounter);
            } finally {
                accounter.popDepth();
            }

        }

        @Override
        public String getName() {
            return "LIST";
        }

        @Override
        public String getPrettyName() {
            return "TAG_List";
        }
    };
    private final List<Tag> list;

    public ListTag() {
        this(new ArrayList());
    }

    public ListTag(List<Tag> list) {
        this.list = list;
    }

    private static Tag tryUnwrap(CompoundTag tag) {
        if (tag.size() == 1) {
            Tag tag1 = tag.get("");

            if (tag1 != null) {
                return tag1;
            }
        }

        return tag;
    }

    private static boolean isWrapper(CompoundTag tag) {
        return tag.size() == 1 && tag.contains("");
    }

    private static Tag wrapIfNeeded(byte elementType, Tag tag) {
        if (elementType != 10) {
            return tag;
        } else {
            if (tag instanceof CompoundTag) {
                CompoundTag compoundtag = (CompoundTag) tag;

                if (!isWrapper(compoundtag)) {
                    return compoundtag;
                }
            }

            return wrapElement(tag);
        }
    }

    private static CompoundTag wrapElement(Tag tag) {
        return new CompoundTag(Map.of("", tag));
    }

    @Override
    public void write(DataOutput output) throws IOException {
        byte b0 = this.identifyRawElementType();

        output.writeByte(b0);
        output.writeInt(this.list.size());

        for (Tag tag : this.list) {
            wrapIfNeeded(b0, tag).write(output);
        }

    }

    @VisibleForTesting
    public byte identifyRawElementType() {
        byte b0 = 0;

        for (Tag tag : this.list) {
            byte b1 = tag.getId();

            if (b0 == 0) {
                b0 = b1;
            } else if (b0 != b1) {
                return 10;
            }
        }

        return b0;
    }

    public void addAndUnwrap(Tag tag) {
        if (tag instanceof CompoundTag compoundtag) {
            this.add(tryUnwrap(compoundtag));
        } else {
            this.add(tag);
        }

    }

    @Override
    public int sizeInBytes() {
        int i = 36;

        i += 4 * this.list.size();

        for (Tag tag : this.list) {
            i += tag.sizeInBytes();
        }

        return i;
    }

    @Override
    public byte getId() {
        return 9;
    }

    @Override
    public TagType<ListTag> getType() {
        return ListTag.TYPE;
    }

    @Override
    public String toString() {
        StringTagVisitor stringtagvisitor = new StringTagVisitor();

        stringtagvisitor.visitList(this);
        return stringtagvisitor.build();
    }

    @Override
    public Tag remove(int index) {
        return (Tag) this.list.remove(index);
    }

    @Override
    public boolean isEmpty() {
        return this.list.isEmpty();
    }

    public Optional<CompoundTag> getCompound(int index) {
        Tag tag = this.getNullable(index);

        if (tag instanceof CompoundTag compoundtag) {
            return Optional.of(compoundtag);
        } else {
            return Optional.empty();
        }
    }

    public CompoundTag getCompoundOrEmpty(int index) {
        return (CompoundTag) this.getCompound(index).orElseGet(CompoundTag::new);
    }

    public Optional<ListTag> getList(int index) {
        Tag tag = this.getNullable(index);

        if (tag instanceof ListTag listtag) {
            return Optional.of(listtag);
        } else {
            return Optional.empty();
        }
    }

    public ListTag getListOrEmpty(int index) {
        return (ListTag) this.getList(index).orElseGet(ListTag::new);
    }

    public Optional<Short> getShort(int index) {
        return this.getOptional(index).flatMap(Tag::asShort);
    }

    public short getShortOr(int index, short defaultValue) {
        Tag tag = this.getNullable(index);

        if (tag instanceof NumericTag numerictag) {
            return numerictag.shortValue();
        } else {
            return defaultValue;
        }
    }

    public Optional<Integer> getInt(int index) {
        return this.getOptional(index).flatMap(Tag::asInt);
    }

    public int getIntOr(int index, int defaultValue) {
        Tag tag = this.getNullable(index);

        if (tag instanceof NumericTag numerictag) {
            return numerictag.intValue();
        } else {
            return defaultValue;
        }
    }

    public Optional<int[]> getIntArray(int index) {
        Tag tag = this.getNullable(index);

        if (tag instanceof IntArrayTag intarraytag) {
            return Optional.of(intarraytag.getAsIntArray());
        } else {
            return Optional.empty();
        }
    }

    public Optional<long[]> getLongArray(int index) {
        Tag tag = this.getNullable(index);

        if (tag instanceof LongArrayTag longarraytag) {
            return Optional.of(longarraytag.getAsLongArray());
        } else {
            return Optional.empty();
        }
    }

    public Optional<Double> getDouble(int index) {
        return this.getOptional(index).flatMap(Tag::asDouble);
    }

    public double getDoubleOr(int index, double defaultValue) {
        Tag tag = this.getNullable(index);

        if (tag instanceof NumericTag numerictag) {
            return numerictag.doubleValue();
        } else {
            return defaultValue;
        }
    }

    public Optional<Float> getFloat(int index) {
        return this.getOptional(index).flatMap(Tag::asFloat);
    }

    public float getFloatOr(int index, float defaultValue) {
        Tag tag = this.getNullable(index);

        if (tag instanceof NumericTag numerictag) {
            return numerictag.floatValue();
        } else {
            return defaultValue;
        }
    }

    public Optional<String> getString(int index) {
        return this.getOptional(index).flatMap(Tag::asString);
    }

    public String getStringOr(int index, String defaultValue) {
        Tag tag = this.getNullable(index);

        if (tag instanceof StringTag stringtag) {
            StringTag stringtag1 = stringtag;

            try {
                s1 = stringtag1.value();
            } catch (Throwable throwable) {
                throw new MatchException(throwable.toString(), throwable);
            }

            String s2 = s1;

            return s2;
        } else {
            return defaultValue;
        }
    }

    private @Nullable Tag getNullable(int index) {
        return index >= 0 && index < this.list.size() ? (Tag) this.list.get(index) : null;
    }

    private Optional<Tag> getOptional(int index) {
        return Optional.ofNullable(this.getNullable(index));
    }

    @Override
    public int size() {
        return this.list.size();
    }

    @Override
    public Tag get(int index) {
        return (Tag) this.list.get(index);
    }

    public Tag set(int index, Tag tag) {
        return (Tag) this.list.set(index, tag);
    }

    public void add(int index, Tag tag) {
        this.list.add(index, tag);
    }

    @Override
    public boolean setTag(int index, Tag tag) {
        this.list.set(index, tag);
        return true;
    }

    @Override
    public boolean addTag(int index, Tag tag) {
        this.list.add(index, tag);
        return true;
    }

    @Override
    public ListTag copy() {
        List<Tag> list = new ArrayList(this.list.size());

        for (Tag tag : this.list) {
            list.add(tag.copy());
        }

        return new ListTag(list);
    }

    @Override
    public Optional<ListTag> asList() {
        return Optional.of(this);
    }

    public boolean equals(Object obj) {
        return this == obj ? true : obj instanceof ListTag && Objects.equals(this.list, ((ListTag) obj).list);
    }

    public int hashCode() {
        return this.list.hashCode();
    }

    @Override
    public Stream<Tag> stream() {
        return super.stream();
    }

    public Stream<CompoundTag> compoundStream() {
        return this.stream().mapMulti((tag, consumer) -> {
            if (tag instanceof CompoundTag compoundtag) {
                consumer.accept(compoundtag);
            }

        });
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitList(this);
    }

    @Override
    public void clear() {
        this.list.clear();
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        byte b0 = this.identifyRawElementType();

        switch (visitor.visitList(TagTypes.getType(b0), this.list.size())) {
            case HALT:
                return StreamTagVisitor.ValueResult.HALT;
            case BREAK:
                return visitor.visitContainerEnd();
            default:
                int i = 0;

                while (i < this.list.size()) {
                    Tag tag = wrapIfNeeded(b0, (Tag) this.list.get(i));

                    switch (visitor.visitElement(tag.getType(), i)) {
                        case HALT:
                            return StreamTagVisitor.ValueResult.HALT;
                        case BREAK:
                            return visitor.visitContainerEnd();
                        default:
                            switch (tag.accept(visitor)) {
                                case HALT:
                                    return StreamTagVisitor.ValueResult.HALT;
                                case BREAK:
                                    return visitor.visitContainerEnd();
                            }
                        case SKIP:
                            ++i;
                    }
                }

                return visitor.visitContainerEnd();
        }
    }
}

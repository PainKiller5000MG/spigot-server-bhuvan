package net.minecraft.nbt;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class CompoundTag implements Tag {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<CompoundTag> CODEC = Codec.PASSTHROUGH.comapFlatMap((dynamic) -> {
        Tag tag = (Tag) dynamic.convert(NbtOps.INSTANCE).getValue();

        if (tag instanceof CompoundTag compoundtag) {
            return DataResult.success(compoundtag == dynamic.getValue() ? compoundtag.copy() : compoundtag);
        } else {
            return DataResult.error(() -> {
                return "Not a compound tag: " + String.valueOf(tag);
            });
        }
    }, (compoundtag) -> {
        return new Dynamic(NbtOps.INSTANCE, compoundtag.copy());
    });
    private static final int SELF_SIZE_IN_BYTES = 48;
    private static final int MAP_ENTRY_SIZE_IN_BYTES = 32;
    public static final TagType<CompoundTag> TYPE = new TagType.VariableSize<CompoundTag>() {
        @Override
        public CompoundTag load(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            CompoundTag compoundtag;

            try {
                compoundtag = loadCompound(input, accounter);
            } finally {
                accounter.popDepth();
            }

            return compoundtag;
        }

        private static CompoundTag loadCompound(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(48L);
            Map<String, Tag> map = Maps.newHashMap();

            byte b0;

            while ((b0 = input.readByte()) != 0) {
                String s = readString(input, accounter);
                Tag tag = CompoundTag.readNamedTagData(TagTypes.getType(b0), s, input, accounter);

                if (map.put(s, tag) == null) {
                    accounter.accountBytes(36L);
                }
            }

            return new CompoundTag(map);
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor output, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            StreamTagVisitor.ValueResult streamtagvisitor_valueresult;

            try {
                streamtagvisitor_valueresult = parseCompound(input, output, accounter);
            } finally {
                accounter.popDepth();
            }

            return streamtagvisitor_valueresult;
        }

        private static StreamTagVisitor.ValueResult parseCompound(DataInput input, StreamTagVisitor output, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(48L);

            while (true) {
                byte b0;

                if ((b0 = input.readByte()) != 0) {
                    TagType<?> tagtype = TagTypes.getType(b0);

                    switch (output.visitEntry(tagtype)) {
                        case HALT:
                            return StreamTagVisitor.ValueResult.HALT;
                        case BREAK:
                            StringTag.skipString(input);
                            tagtype.skip(input, accounter);
                            break;
                        case SKIP:
                            StringTag.skipString(input);
                            tagtype.skip(input, accounter);
                            continue;
                        default:
                            String s = readString(input, accounter);

                            switch (output.visitEntry(tagtype, s)) {
                                case HALT:
                                    return StreamTagVisitor.ValueResult.HALT;
                                case BREAK:
                                    tagtype.skip(input, accounter);
                                    break;
                                case SKIP:
                                    tagtype.skip(input, accounter);
                                    continue;
                                default:
                                    accounter.accountBytes(36L);
                                    switch (tagtype.parse(input, output, accounter)) {
                                        case HALT:
                                            return StreamTagVisitor.ValueResult.HALT;
                                        case BREAK:
                                        default:
                                            continue;
                                    }
                            }
                    }
                }

                if (b0 != 0) {
                    while ((b0 = input.readByte()) != 0) {
                        StringTag.skipString(input);
                        TagTypes.getType(b0).skip(input, accounter);
                    }
                }

                return output.visitContainerEnd();
            }
        }

        private static String readString(DataInput input, NbtAccounter accounter) throws IOException {
            String s = input.readUTF();

            accounter.accountBytes(28L);
            accounter.accountBytes(2L, (long) s.length());
            return s;
        }

        @Override
        public void skip(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.pushDepth();

            byte b0;

            try {
                while ((b0 = input.readByte()) != 0) {
                    StringTag.skipString(input);
                    TagTypes.getType(b0).skip(input, accounter);
                }
            } finally {
                accounter.popDepth();
            }

        }

        @Override
        public String getName() {
            return "COMPOUND";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Compound";
        }
    };
    private final Map<String, Tag> tags;

    CompoundTag(Map<String, Tag> tags) {
        this.tags = tags;
    }

    public CompoundTag() {
        this(new HashMap());
    }

    @Override
    public void write(DataOutput output) throws IOException {
        for (String s : this.tags.keySet()) {
            Tag tag = (Tag) this.tags.get(s);

            writeNamedTag(s, tag, output);
        }

        output.writeByte(0);
    }

    @Override
    public int sizeInBytes() {
        int i = 48;

        for (Map.Entry<String, Tag> map_entry : this.tags.entrySet()) {
            i += 28 + 2 * ((String) map_entry.getKey()).length();
            i += 36;
            i += ((Tag) map_entry.getValue()).sizeInBytes();
        }

        return i;
    }

    public Set<String> keySet() {
        return this.tags.keySet();
    }

    public Set<Map.Entry<String, Tag>> entrySet() {
        return this.tags.entrySet();
    }

    public Collection<Tag> values() {
        return this.tags.values();
    }

    public void forEach(BiConsumer<String, Tag> consumer) {
        this.tags.forEach(consumer);
    }

    @Override
    public byte getId() {
        return 10;
    }

    @Override
    public TagType<CompoundTag> getType() {
        return CompoundTag.TYPE;
    }

    public int size() {
        return this.tags.size();
    }

    public @Nullable Tag put(String name, Tag tag) {
        return (Tag) this.tags.put(name, tag);
    }

    public void putByte(String name, byte value) {
        this.tags.put(name, ByteTag.valueOf(value));
    }

    public void putShort(String name, short value) {
        this.tags.put(name, ShortTag.valueOf(value));
    }

    public void putInt(String name, int value) {
        this.tags.put(name, IntTag.valueOf(value));
    }

    public void putLong(String name, long value) {
        this.tags.put(name, LongTag.valueOf(value));
    }

    public void putFloat(String name, float value) {
        this.tags.put(name, FloatTag.valueOf(value));
    }

    public void putDouble(String name, double value) {
        this.tags.put(name, DoubleTag.valueOf(value));
    }

    public void putString(String name, String value) {
        this.tags.put(name, StringTag.valueOf(value));
    }

    public void putByteArray(String name, byte[] value) {
        this.tags.put(name, new ByteArrayTag(value));
    }

    public void putIntArray(String name, int[] value) {
        this.tags.put(name, new IntArrayTag(value));
    }

    public void putLongArray(String name, long[] value) {
        this.tags.put(name, new LongArrayTag(value));
    }

    public void putBoolean(String name, boolean value) {
        this.tags.put(name, ByteTag.valueOf(value));
    }

    public @Nullable Tag get(String name) {
        return (Tag) this.tags.get(name);
    }

    public boolean contains(String name) {
        return this.tags.containsKey(name);
    }

    private Optional<Tag> getOptional(String name) {
        return Optional.ofNullable((Tag) this.tags.get(name));
    }

    public Optional<Byte> getByte(String name) {
        return this.getOptional(name).flatMap(Tag::asByte);
    }

    public byte getByteOr(String name, byte defaultValue) {
        Object object = this.tags.get(name);

        if (object instanceof NumericTag numerictag) {
            return numerictag.byteValue();
        } else {
            return defaultValue;
        }
    }

    public Optional<Short> getShort(String name) {
        return this.getOptional(name).flatMap(Tag::asShort);
    }

    public short getShortOr(String name, short defaultValue) {
        Object object = this.tags.get(name);

        if (object instanceof NumericTag numerictag) {
            return numerictag.shortValue();
        } else {
            return defaultValue;
        }
    }

    public Optional<Integer> getInt(String name) {
        return this.getOptional(name).flatMap(Tag::asInt);
    }

    public int getIntOr(String name, int defaultValue) {
        Object object = this.tags.get(name);

        if (object instanceof NumericTag numerictag) {
            return numerictag.intValue();
        } else {
            return defaultValue;
        }
    }

    public Optional<Long> getLong(String name) {
        return this.getOptional(name).flatMap(Tag::asLong);
    }

    public long getLongOr(String name, long defaultValue) {
        Object object = this.tags.get(name);

        if (object instanceof NumericTag numerictag) {
            return numerictag.longValue();
        } else {
            return defaultValue;
        }
    }

    public Optional<Float> getFloat(String name) {
        return this.getOptional(name).flatMap(Tag::asFloat);
    }

    public float getFloatOr(String name, float defaultValue) {
        Object object = this.tags.get(name);

        if (object instanceof NumericTag numerictag) {
            return numerictag.floatValue();
        } else {
            return defaultValue;
        }
    }

    public Optional<Double> getDouble(String name) {
        return this.getOptional(name).flatMap(Tag::asDouble);
    }

    public double getDoubleOr(String name, double defaultValue) {
        Object object = this.tags.get(name);

        if (object instanceof NumericTag numerictag) {
            return numerictag.doubleValue();
        } else {
            return defaultValue;
        }
    }

    public Optional<String> getString(String name) {
        return this.getOptional(name).flatMap(Tag::asString);
    }

    public String getStringOr(String name, String defaultValue) {
        Object object = this.tags.get(name);

        if (object instanceof StringTag stringtag) {
            StringTag stringtag1 = stringtag;

            try {
                s2 = stringtag1.value();
            } catch (Throwable throwable) {
                throw new MatchException(throwable.toString(), throwable);
            }

            String s3 = s2;

            return s3;
        } else {
            return defaultValue;
        }
    }

    public Optional<byte[]> getByteArray(String name) {
        Object object = this.tags.get(name);

        if (object instanceof ByteArrayTag bytearraytag) {
            return Optional.of(bytearraytag.getAsByteArray());
        } else {
            return Optional.empty();
        }
    }

    public Optional<int[]> getIntArray(String name) {
        Object object = this.tags.get(name);

        if (object instanceof IntArrayTag intarraytag) {
            return Optional.of(intarraytag.getAsIntArray());
        } else {
            return Optional.empty();
        }
    }

    public Optional<long[]> getLongArray(String name) {
        Object object = this.tags.get(name);

        if (object instanceof LongArrayTag longarraytag) {
            return Optional.of(longarraytag.getAsLongArray());
        } else {
            return Optional.empty();
        }
    }

    public Optional<CompoundTag> getCompound(String name) {
        Object object = this.tags.get(name);

        if (object instanceof CompoundTag compoundtag) {
            return Optional.of(compoundtag);
        } else {
            return Optional.empty();
        }
    }

    public CompoundTag getCompoundOrEmpty(String name) {
        return (CompoundTag) this.getCompound(name).orElseGet(CompoundTag::new);
    }

    public Optional<ListTag> getList(String name) {
        Object object = this.tags.get(name);

        if (object instanceof ListTag listtag) {
            return Optional.of(listtag);
        } else {
            return Optional.empty();
        }
    }

    public ListTag getListOrEmpty(String name) {
        return (ListTag) this.getList(name).orElseGet(ListTag::new);
    }

    public Optional<Boolean> getBoolean(String name) {
        return this.getOptional(name).flatMap(Tag::asBoolean);
    }

    public boolean getBooleanOr(String string, boolean defaultValue) {
        return this.getByteOr(string, (byte) (defaultValue ? 1 : 0)) != 0;
    }

    public @Nullable Tag remove(String name) {
        return (Tag) this.tags.remove(name);
    }

    @Override
    public String toString() {
        StringTagVisitor stringtagvisitor = new StringTagVisitor();

        stringtagvisitor.visitCompound(this);
        return stringtagvisitor.build();
    }

    public boolean isEmpty() {
        return this.tags.isEmpty();
    }

    protected CompoundTag shallowCopy() {
        return new CompoundTag(new HashMap(this.tags));
    }

    @Override
    public CompoundTag copy() {
        HashMap<String, Tag> hashmap = new HashMap();

        this.tags.forEach((s, tag) -> {
            hashmap.put(s, tag.copy());
        });
        return new CompoundTag(hashmap);
    }

    @Override
    public Optional<CompoundTag> asCompound() {
        return Optional.of(this);
    }

    public boolean equals(Object obj) {
        return this == obj ? true : obj instanceof CompoundTag && Objects.equals(this.tags, ((CompoundTag) obj).tags);
    }

    public int hashCode() {
        return this.tags.hashCode();
    }

    private static void writeNamedTag(String name, Tag tag, DataOutput output) throws IOException {
        output.writeByte(tag.getId());
        if (tag.getId() != 0) {
            output.writeUTF(name);
            tag.write(output);
        }
    }

    private static Tag readNamedTagData(TagType<?> type, String name, DataInput input, NbtAccounter accounter) {
        try {
            return type.load(input, accounter);
        } catch (IOException ioexception) {
            CrashReport crashreport = CrashReport.forThrowable(ioexception, "Loading NBT data");
            CrashReportCategory crashreportcategory = crashreport.addCategory("NBT Tag");

            crashreportcategory.setDetail("Tag name", name);
            crashreportcategory.setDetail("Tag type", type.getName());
            throw new ReportedNbtException(crashreport);
        }
    }

    public CompoundTag merge(CompoundTag other) {
        for (String s : other.tags.keySet()) {
            Tag tag = (Tag) other.tags.get(s);

            if (tag instanceof CompoundTag compoundtag1) {
                Object object = this.tags.get(s);

                if (object instanceof CompoundTag compoundtag2) {
                    compoundtag2.merge(compoundtag1);
                    continue;
                }
            }

            this.put(s, tag.copy());
        }

        return this;
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitCompound(this);
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        for (Map.Entry<String, Tag> map_entry : this.tags.entrySet()) {
            Tag tag = (Tag) map_entry.getValue();
            TagType<?> tagtype = tag.getType();
            StreamTagVisitor.EntryResult streamtagvisitor_entryresult = visitor.visitEntry(tagtype);

            switch (streamtagvisitor_entryresult) {
                case HALT:
                    return StreamTagVisitor.ValueResult.HALT;
                case BREAK:
                    return visitor.visitContainerEnd();
                case SKIP:
                    break;
                default:
                    streamtagvisitor_entryresult = visitor.visitEntry(tagtype, (String) map_entry.getKey());
                    switch (streamtagvisitor_entryresult) {
                        case HALT:
                            return StreamTagVisitor.ValueResult.HALT;
                        case BREAK:
                            return visitor.visitContainerEnd();
                        case SKIP:
                            break;
                        default:
                            StreamTagVisitor.ValueResult streamtagvisitor_valueresult = tag.accept(visitor);

                            switch (streamtagvisitor_valueresult) {
                                case HALT:
                                    return StreamTagVisitor.ValueResult.HALT;
                                case BREAK:
                                    return visitor.visitContainerEnd();
                            }
                    }
            }
        }

        return visitor.visitContainerEnd();
    }

    public <T> void store(String name, Codec<T> codec, T value) {
        this.store(name, codec, NbtOps.INSTANCE, value);
    }

    public <T> void storeNullable(String name, Codec<T> codec, @Nullable T value) {
        if (value != null) {
            this.store(name, codec, value);
        }

    }

    public <T> void store(String name, Codec<T> codec, DynamicOps<Tag> ops, T value) {
        this.put(name, (Tag) codec.encodeStart(ops, value).getOrThrow());
    }

    public <T> void storeNullable(String name, Codec<T> codec, DynamicOps<Tag> ops, @Nullable T value) {
        if (value != null) {
            this.store(name, codec, ops, value);
        }

    }

    public <T> void store(MapCodec<T> codec, T value) {
        this.store(codec, NbtOps.INSTANCE, value);
    }

    public <T> void store(MapCodec<T> codec, DynamicOps<Tag> ops, T value) {
        this.merge((CompoundTag) codec.encoder().encodeStart(ops, value).getOrThrow());
    }

    public <T> Optional<T> read(String name, Codec<T> codec) {
        return this.<T>read(name, codec, NbtOps.INSTANCE);
    }

    public <T> Optional<T> read(String name, Codec<T> codec, DynamicOps<Tag> ops) {
        Tag tag = this.get(name);

        return tag == null ? Optional.empty() : codec.parse(ops, tag).resultOrPartial((s1) -> {
            CompoundTag.LOGGER.error("Failed to read field ({}={}): {}", new Object[]{name, tag, s1});
        });
    }

    public <T> Optional<T> read(MapCodec<T> codec) {
        return this.read(codec, NbtOps.INSTANCE);
    }

    public <T> Optional<T> read(MapCodec<T> codec, DynamicOps<Tag> ops) {
        return codec.decode(ops, (MapLike) ops.getMap(this).getOrThrow()).resultOrPartial((s) -> {
            CompoundTag.LOGGER.error("Failed to read value ({}): {}", this, s);
        });
    }
}

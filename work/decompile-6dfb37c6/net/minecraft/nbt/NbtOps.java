package net.minecraft.nbt;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class NbtOps implements DynamicOps<Tag> {

    public static final NbtOps INSTANCE = new NbtOps();

    private NbtOps() {}

    public Tag empty() {
        return EndTag.INSTANCE;
    }

    public Tag emptyList() {
        return new ListTag();
    }

    public Tag emptyMap() {
        return new CompoundTag();
    }

    public <U> U convertTo(DynamicOps<U> outOps, Tag input) {
        Objects.requireNonNull(input);
        byte b0 = 0;
        Object object;

        //$FF: b0->value
        //0->net/minecraft/nbt/EndTag
        //1->net/minecraft/nbt/ByteTag
        //2->net/minecraft/nbt/ShortTag
        //3->net/minecraft/nbt/IntTag
        //4->net/minecraft/nbt/LongTag
        //5->net/minecraft/nbt/FloatTag
        //6->net/minecraft/nbt/DoubleTag
        //7->net/minecraft/nbt/ByteArrayTag
        //8->net/minecraft/nbt/StringTag
        //9->net/minecraft/nbt/ListTag
        //10->net/minecraft/nbt/CompoundTag
        //11->net/minecraft/nbt/IntArrayTag
        //12->net/minecraft/nbt/LongArrayTag
        switch (input.typeSwitch<invokedynamic>(input, b0)) {
            case 0:
                EndTag endtag = (EndTag)input;

                object = (StringTag)(outOps.empty());
                break;
            case 1:
                ByteTag bytetag = (ByteTag)input;
                ByteTag bytetag1 = bytetag;

                try {
                    b1 = bytetag1.value();
                } catch (Throwable throwable) {
                    throw new MatchException(throwable.toString(), throwable);
                }

                byte b2 = b1;

                object = (StringTag)(outOps.createByte(b2));
                break;
            case 2:
                ShortTag shorttag = (ShortTag)input;
                ShortTag shorttag1 = shorttag;

                try {
                    short0 = shorttag1.value();
                } catch (Throwable throwable1) {
                    throw new MatchException(throwable1.toString(), throwable1);
                }

                short short1 = short0;

                object = (StringTag)(outOps.createShort(short1));
                break;
            case 3:
                IntTag inttag = (IntTag)input;
                IntTag inttag1 = inttag;

                try {
                    i = inttag1.value();
                } catch (Throwable throwable2) {
                    throw new MatchException(throwable2.toString(), throwable2);
                }

                int j = i;

                object = (StringTag)(outOps.createInt(j));
                break;
            case 4:
                LongTag longtag = (LongTag)input;
                LongTag longtag1 = longtag;

                try {
                    k = longtag1.value();
                } catch (Throwable throwable3) {
                    throw new MatchException(throwable3.toString(), throwable3);
                }

                long l = k;

                object = (StringTag)(outOps.createLong(l));
                break;
            case 5:
                FloatTag floattag = (FloatTag)input;
                FloatTag floattag1 = floattag;

                try {
                    f = floattag1.value();
                } catch (Throwable throwable4) {
                    throw new MatchException(throwable4.toString(), throwable4);
                }

                float f1 = f;

                object = (StringTag)(outOps.createFloat(f1));
                break;
            case 6:
                DoubleTag doubletag = (DoubleTag)input;
                DoubleTag doubletag1 = doubletag;

                try {
                    d0 = doubletag1.value();
                } catch (Throwable throwable5) {
                    throw new MatchException(throwable5.toString(), throwable5);
                }

                double d1 = d0;

                object = (StringTag)(outOps.createDouble(d1));
                break;
            case 7:
                ByteArrayTag bytearraytag = (ByteArrayTag)input;

                object = (StringTag)(outOps.createByteList(ByteBuffer.wrap(bytearraytag.getAsByteArray())));
                break;
            case 8:
                StringTag stringtag = (StringTag)input;

                object = stringtag;

                try {
                    s = object.value();
                } catch (Throwable throwable6) {
                    throw new MatchException(throwable6.toString(), throwable6);
                }

                String s1 = s;

                object = (StringTag)(outOps.createString(s1));
                break;
            case 9:
                ListTag listtag = (ListTag)input;

                object = (StringTag)(this.convertList(outOps, listtag));
                break;
            case 10:
                CompoundTag compoundtag = (CompoundTag)input;

                object = (StringTag)(this.convertMap(outOps, compoundtag));
                break;
            case 11:
                IntArrayTag intarraytag = (IntArrayTag)input;

                object = (StringTag)(outOps.createIntList(Arrays.stream(intarraytag.getAsIntArray())));
                break;
            case 12:
                LongArrayTag longarraytag = (LongArrayTag)input;

                object = (StringTag)(outOps.createLongList(Arrays.stream(longarraytag.getAsLongArray())));
                break;
            default:
                throw new MatchException((String)null, (Throwable)null);
        }

        return (U)object;
    }

    public DataResult<Number> getNumberValue(Tag input) {
        return (DataResult) input.asNumber().map(DataResult::success).orElseGet(() -> {
            return DataResult.error(() -> {
                return "Not a number";
            });
        });
    }

    public Tag createNumeric(Number i) {
        return DoubleTag.valueOf(i.doubleValue());
    }

    public Tag createByte(byte value) {
        return ByteTag.valueOf(value);
    }

    public Tag createShort(short value) {
        return ShortTag.valueOf(value);
    }

    public Tag createInt(int value) {
        return IntTag.valueOf(value);
    }

    public Tag createLong(long value) {
        return LongTag.valueOf(value);
    }

    public Tag createFloat(float value) {
        return FloatTag.valueOf(value);
    }

    public Tag createDouble(double value) {
        return DoubleTag.valueOf(value);
    }

    public Tag createBoolean(boolean value) {
        return ByteTag.valueOf(value);
    }

    public DataResult<String> getStringValue(Tag input) {
        if (input instanceof StringTag stringtag) {
            StringTag stringtag1 = stringtag;

            try {
                s = stringtag1.value();
            } catch (Throwable throwable) {
                throw new MatchException(throwable.toString(), throwable);
            }

            String s1 = s;

            return DataResult.success(s1);
        } else {
            return DataResult.error(() -> {
                return "Not a string";
            });
        }
    }

    public Tag createString(String value) {
        return StringTag.valueOf(value);
    }

    public DataResult<Tag> mergeToList(Tag list, Tag value) {
        return (DataResult) createCollector(list).map((nbtops_listcollector) -> {
            return DataResult.success(nbtops_listcollector.accept(value).result());
        }).orElseGet(() -> {
            return DataResult.error(() -> {
                return "mergeToList called with not a list: " + String.valueOf(list);
            }, list);
        });
    }

    public DataResult<Tag> mergeToList(Tag list, List<Tag> values) {
        return (DataResult) createCollector(list).map((nbtops_listcollector) -> {
            return DataResult.success(nbtops_listcollector.acceptAll(values).result());
        }).orElseGet(() -> {
            return DataResult.error(() -> {
                return "mergeToList called with not a list: " + String.valueOf(list);
            }, list);
        });
    }

    public DataResult<Tag> mergeToMap(Tag map, Tag key, Tag value) {
        if (!(map instanceof CompoundTag) && !(map instanceof EndTag)) {
            return DataResult.error(() -> {
                return "mergeToMap called with not a map: " + String.valueOf(map);
            }, map);
        } else if (key instanceof StringTag) {
            StringTag stringtag = (StringTag) key;
            StringTag stringtag1 = stringtag;

            try {
                s = stringtag1.value();
            } catch (Throwable throwable) {
                throw new MatchException(throwable.toString(), throwable);
            }

            String s1 = s;
            String s2 = s1;
            CompoundTag compoundtag;

            if (map instanceof CompoundTag) {
                CompoundTag compoundtag1 = (CompoundTag) map;

                compoundtag = compoundtag1.shallowCopy();
            } else {
                compoundtag = new CompoundTag();
            }

            CompoundTag compoundtag2 = compoundtag;

            compoundtag2.put(s2, value);
            return DataResult.success(compoundtag2);
        } else {
            return DataResult.error(() -> {
                return "key is not a string: " + String.valueOf(key);
            }, map);
        }
    }

    public DataResult<Tag> mergeToMap(Tag map, MapLike<Tag> values) {
        if (!(map instanceof CompoundTag) && !(map instanceof EndTag)) {
            return DataResult.error(() -> {
                return "mergeToMap called with not a map: " + String.valueOf(map);
            }, map);
        } else {
            Iterator<Pair<Tag, Tag>> iterator = values.entries().iterator();

            if (!iterator.hasNext()) {
                return map == this.empty() ? DataResult.success(this.emptyMap()) : DataResult.success(map);
            } else {
                CompoundTag compoundtag;

                if (map instanceof CompoundTag) {
                    CompoundTag compoundtag1 = (CompoundTag) map;

                    compoundtag = compoundtag1.shallowCopy();
                } else {
                    compoundtag = new CompoundTag();
                }

                CompoundTag compoundtag2 = compoundtag;
                List<Tag> list = new ArrayList();

                iterator.forEachRemaining((pair) -> {
                    Tag tag1 = (Tag) pair.getFirst();

                    if (tag1 instanceof StringTag stringtag) {
                        StringTag stringtag1 = stringtag;

                        try {
                            s = stringtag1.value();
                        } catch (Throwable throwable) {
                            throw new MatchException(throwable.toString(), throwable);
                        }

                        String s1 = s;

                        compoundtag2.put(s1, (Tag) pair.getSecond());
                    } else {
                        list.add(tag1);
                    }
                });
                return !list.isEmpty() ? DataResult.error(() -> {
                    return "some keys are not strings: " + String.valueOf(list);
                }, compoundtag2) : DataResult.success(compoundtag2);
            }
        }
    }

    public DataResult<Tag> mergeToMap(Tag map, Map<Tag, Tag> values) {
        if (!(map instanceof CompoundTag) && !(map instanceof EndTag)) {
            return DataResult.error(() -> {
                return "mergeToMap called with not a map: " + String.valueOf(map);
            }, map);
        } else if (values.isEmpty()) {
            return map == this.empty() ? DataResult.success(this.emptyMap()) : DataResult.success(map);
        } else {
            CompoundTag compoundtag;

            if (map instanceof CompoundTag) {
                CompoundTag compoundtag1 = (CompoundTag) map;

                compoundtag = compoundtag1.shallowCopy();
            } else {
                compoundtag = new CompoundTag();
            }

            CompoundTag compoundtag2 = compoundtag;
            List<Tag> list = new ArrayList();

            for (Map.Entry<Tag, Tag> map_entry : values.entrySet()) {
                Tag tag1 = (Tag) map_entry.getKey();

                if (tag1 instanceof StringTag) {
                    StringTag stringtag = (StringTag) tag1;
                    StringTag stringtag1 = stringtag;

                    try {
                        s = stringtag1.value();
                    } catch (Throwable throwable) {
                        throw new MatchException(throwable.toString(), throwable);
                    }

                    String s1 = s;

                    compoundtag2.put(s1, (Tag) map_entry.getValue());
                } else {
                    list.add(tag1);
                }
            }

            if (!list.isEmpty()) {
                return DataResult.error(() -> {
                    return "some keys are not strings: " + String.valueOf(list);
                }, compoundtag2);
            } else {
                return DataResult.success(compoundtag2);
            }
        }
    }

    public DataResult<Stream<Pair<Tag, Tag>>> getMapValues(Tag input) {
        if (input instanceof CompoundTag compoundtag) {
            return DataResult.success(compoundtag.entrySet().stream().map((entry) -> {
                return Pair.of(this.createString((String) entry.getKey()), (Tag) entry.getValue());
            }));
        } else {
            return DataResult.error(() -> {
                return "Not a map: " + String.valueOf(input);
            });
        }
    }

    public DataResult<Consumer<BiConsumer<Tag, Tag>>> getMapEntries(Tag input) {
        if (input instanceof CompoundTag compoundtag) {
            return DataResult.success((Consumer) (biconsumer) -> {
                for (Map.Entry<String, Tag> map_entry : compoundtag.entrySet()) {
                    biconsumer.accept(this.createString((String) map_entry.getKey()), (Tag) map_entry.getValue());
                }

            });
        } else {
            return DataResult.error(() -> {
                return "Not a map: " + String.valueOf(input);
            });
        }
    }

    public DataResult<MapLike<Tag>> getMap(Tag input) {
        if (input instanceof final CompoundTag compoundtag) {
            return DataResult.success(new MapLike<Tag>() {
                public @Nullable Tag get(Tag key) {
                    if (key instanceof StringTag stringtag) {
                        StringTag stringtag1 = stringtag;

                        try {
                            s = stringtag1.value();
                        } catch (Throwable throwable) {
                            throw new MatchException(throwable.toString(), throwable);
                        }

                        String s1 = s;

                        return compoundtag.get(s1);
                    } else {
                        throw new UnsupportedOperationException("Cannot get map entry with non-string key: " + String.valueOf(key));
                    }
                }

                public @Nullable Tag get(String key) {
                    return compoundtag.get(key);
                }

                public Stream<Pair<Tag, Tag>> entries() {
                    return compoundtag.entrySet().stream().map((entry) -> {
                        return Pair.of(NbtOps.this.createString((String) entry.getKey()), (Tag) entry.getValue());
                    });
                }

                public String toString() {
                    return "MapLike[" + String.valueOf(compoundtag) + "]";
                }
            });
        } else {
            return DataResult.error(() -> {
                return "Not a map: " + String.valueOf(input);
            });
        }
    }

    public Tag createMap(Stream<Pair<Tag, Tag>> map) {
        CompoundTag compoundtag = new CompoundTag();

        map.forEach((pair) -> {
            Tag tag = (Tag) pair.getFirst();
            Tag tag1 = (Tag) pair.getSecond();

            if (tag instanceof StringTag stringtag) {
                StringTag stringtag1 = stringtag;

                try {
                    s = stringtag1.value();
                } catch (Throwable throwable) {
                    throw new MatchException(throwable.toString(), throwable);
                }

                String s1 = s;

                compoundtag.put(s1, tag1);
            } else {
                throw new UnsupportedOperationException("Cannot create map with non-string key: " + String.valueOf(tag));
            }
        });
        return compoundtag;
    }

    public DataResult<Stream<Tag>> getStream(Tag input) {
        if (input instanceof CollectionTag collectiontag) {
            return DataResult.success(collectiontag.stream());
        } else {
            return DataResult.error(() -> {
                return "Not a list";
            });
        }
    }

    public DataResult<Consumer<Consumer<Tag>>> getList(Tag input) {
        if (input instanceof CollectionTag collectiontag) {
            Objects.requireNonNull(collectiontag);
            return DataResult.success(collectiontag::forEach);
        } else {
            return DataResult.error(() -> {
                return "Not a list: " + String.valueOf(input);
            });
        }
    }

    public DataResult<ByteBuffer> getByteBuffer(Tag input) {
        if (input instanceof ByteArrayTag bytearraytag) {
            return DataResult.success(ByteBuffer.wrap(bytearraytag.getAsByteArray()));
        } else {
            return super.getByteBuffer(input);
        }
    }

    public Tag createByteList(ByteBuffer input) {
        ByteBuffer bytebuffer1 = input.duplicate().clear();
        byte[] abyte = new byte[input.capacity()];

        bytebuffer1.get(0, abyte, 0, abyte.length);
        return new ByteArrayTag(abyte);
    }

    public DataResult<IntStream> getIntStream(Tag input) {
        if (input instanceof IntArrayTag intarraytag) {
            return DataResult.success(Arrays.stream(intarraytag.getAsIntArray()));
        } else {
            return super.getIntStream(input);
        }
    }

    public Tag createIntList(IntStream input) {
        return new IntArrayTag(input.toArray());
    }

    public DataResult<LongStream> getLongStream(Tag input) {
        if (input instanceof LongArrayTag longarraytag) {
            return DataResult.success(Arrays.stream(longarraytag.getAsLongArray()));
        } else {
            return super.getLongStream(input);
        }
    }

    public Tag createLongList(LongStream input) {
        return new LongArrayTag(input.toArray());
    }

    public Tag createList(Stream<Tag> input) {
        return new ListTag((List) input.collect(Util.toMutableList()));
    }

    public Tag remove(Tag input, String key) {
        if (input instanceof CompoundTag compoundtag) {
            CompoundTag compoundtag1 = compoundtag.shallowCopy();

            compoundtag1.remove(key);
            return compoundtag1;
        } else {
            return input;
        }
    }

    public String toString() {
        return "NBT";
    }

    public RecordBuilder<Tag> mapBuilder() {
        return new NbtOps.NbtRecordBuilder();
    }

    private static Optional<NbtOps.ListCollector> createCollector(Tag tag) {
        if (tag instanceof EndTag) {
            return Optional.of(new NbtOps.GenericListCollector());
        } else if (tag instanceof CollectionTag) {
            CollectionTag collectiontag = (CollectionTag)tag;

            if (collectiontag.isEmpty()) {
                return Optional.of(new NbtOps.GenericListCollector());
            } else {
                Objects.requireNonNull(collectiontag);
                byte b0 = 0;
                Optional optional;

                //$FF: b0->value
                //0->net/minecraft/nbt/ListTag
                //1->net/minecraft/nbt/ByteArrayTag
                //2->net/minecraft/nbt/IntArrayTag
                //3->net/minecraft/nbt/LongArrayTag
                switch (collectiontag.typeSwitch<invokedynamic>(collectiontag, b0)) {
                    case 0:
                        ListTag listtag = (ListTag)collectiontag;

                        optional = Optional.of(new NbtOps.GenericListCollector(listtag));
                        break;
                    case 1:
                        ByteArrayTag bytearraytag = (ByteArrayTag)collectiontag;

                        optional = Optional.of(new NbtOps.ByteListCollector(bytearraytag.getAsByteArray()));
                        break;
                    case 2:
                        IntArrayTag intarraytag = (IntArrayTag)collectiontag;

                        optional = Optional.of(new NbtOps.IntListCollector(intarraytag.getAsIntArray()));
                        break;
                    case 3:
                        LongArrayTag longarraytag = (LongArrayTag)collectiontag;

                        optional = Optional.of(new NbtOps.LongListCollector(longarraytag.getAsLongArray()));
                        break;
                    default:
                        throw new MatchException((String)null, (Throwable)null);
                }

                return optional;
            }
        } else {
            return Optional.empty();
        }
    }

    private class NbtRecordBuilder extends RecordBuilder.AbstractStringBuilder<Tag, CompoundTag> {

        protected NbtRecordBuilder() {
            super(NbtOps.this);
        }

        protected CompoundTag initBuilder() {
            return new CompoundTag();
        }

        protected CompoundTag append(String key, Tag value, CompoundTag builder) {
            builder.put(key, value);
            return builder;
        }

        protected DataResult<Tag> build(CompoundTag builder, Tag prefix) {
            if (prefix != null && prefix != EndTag.INSTANCE) {
                if (!(prefix instanceof CompoundTag)) {
                    return DataResult.error(() -> {
                        return "mergeToMap called with not a map: " + String.valueOf(prefix);
                    }, prefix);
                } else {
                    CompoundTag compoundtag1 = (CompoundTag) prefix;
                    CompoundTag compoundtag2 = compoundtag1.shallowCopy();

                    for (Map.Entry<String, Tag> map_entry : builder.entrySet()) {
                        compoundtag2.put((String) map_entry.getKey(), (Tag) map_entry.getValue());
                    }

                    return DataResult.success(compoundtag2);
                }
            } else {
                return DataResult.success(builder);
            }
        }
    }

    private interface ListCollector {

        NbtOps.ListCollector accept(Tag t);

        default NbtOps.ListCollector acceptAll(Iterable<Tag> tags) {
            NbtOps.ListCollector nbtops_listcollector = this;

            for (Tag tag : tags) {
                nbtops_listcollector = nbtops_listcollector.accept(tag);
            }

            return nbtops_listcollector;
        }

        default NbtOps.ListCollector acceptAll(Stream<Tag> tags) {
            Objects.requireNonNull(tags);
            return this.acceptAll(tags::iterator);
        }

        Tag result();
    }

    private static class GenericListCollector implements NbtOps.ListCollector {

        private final ListTag result = new ListTag();

        private GenericListCollector() {}

        private GenericListCollector(ListTag initial) {
            this.result.addAll(initial);
        }

        public GenericListCollector(IntArrayList initials) {
            initials.forEach((i) -> {
                this.result.add(IntTag.valueOf(i));
            });
        }

        public GenericListCollector(ByteArrayList initials) {
            initials.forEach((b0) -> {
                this.result.add(ByteTag.valueOf(b0));
            });
        }

        public GenericListCollector(LongArrayList initials) {
            initials.forEach((i) -> {
                this.result.add(LongTag.valueOf(i));
            });
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            this.result.add(tag);
            return this;
        }

        @Override
        public Tag result() {
            return this.result;
        }
    }

    private static class IntListCollector implements NbtOps.ListCollector {

        private final IntArrayList values = new IntArrayList();

        public IntListCollector(int[] initialValues) {
            this.values.addElements(0, initialValues);
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            if (tag instanceof IntTag inttag) {
                this.values.add(inttag.intValue());
                return this;
            } else {
                return (new NbtOps.GenericListCollector(this.values)).accept(tag);
            }
        }

        @Override
        public Tag result() {
            return new IntArrayTag(this.values.toIntArray());
        }
    }

    private static class ByteListCollector implements NbtOps.ListCollector {

        private final ByteArrayList values = new ByteArrayList();

        public ByteListCollector(byte[] initialValues) {
            this.values.addElements(0, initialValues);
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            if (tag instanceof ByteTag bytetag) {
                this.values.add(bytetag.byteValue());
                return this;
            } else {
                return (new NbtOps.GenericListCollector(this.values)).accept(tag);
            }
        }

        @Override
        public Tag result() {
            return new ByteArrayTag(this.values.toByteArray());
        }
    }

    private static class LongListCollector implements NbtOps.ListCollector {

        private final LongArrayList values = new LongArrayList();

        public LongListCollector(long[] initialValues) {
            this.values.addElements(0, initialValues);
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            if (tag instanceof LongTag longtag) {
                this.values.add(longtag.longValue());
                return this;
            } else {
                return (new NbtOps.GenericListCollector(this.values)).accept(tag);
            }
        }

        @Override
        public Tag result() {
            return new LongArrayTag(this.values.toLongArray());
        }
    }
}

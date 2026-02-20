package net.minecraft.util;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.ListBuilder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class HashOps implements DynamicOps<HashCode> {

    private static final byte TAG_EMPTY = 1;
    private static final byte TAG_MAP_START = 2;
    private static final byte TAG_MAP_END = 3;
    private static final byte TAG_LIST_START = 4;
    private static final byte TAG_LIST_END = 5;
    private static final byte TAG_BYTE = 6;
    private static final byte TAG_SHORT = 7;
    private static final byte TAG_INT = 8;
    private static final byte TAG_LONG = 9;
    private static final byte TAG_FLOAT = 10;
    private static final byte TAG_DOUBLE = 11;
    private static final byte TAG_STRING = 12;
    private static final byte TAG_BOOLEAN = 13;
    private static final byte TAG_BYTE_ARRAY_START = 14;
    private static final byte TAG_BYTE_ARRAY_END = 15;
    private static final byte TAG_INT_ARRAY_START = 16;
    private static final byte TAG_INT_ARRAY_END = 17;
    private static final byte TAG_LONG_ARRAY_START = 18;
    private static final byte TAG_LONG_ARRAY_END = 19;
    private static final byte[] EMPTY_PAYLOAD = new byte[]{1};
    private static final byte[] FALSE_PAYLOAD = new byte[]{13, 0};
    private static final byte[] TRUE_PAYLOAD = new byte[]{13, 1};
    public static final byte[] EMPTY_MAP_PAYLOAD = new byte[]{2, 3};
    public static final byte[] EMPTY_LIST_PAYLOAD = new byte[]{4, 5};
    private static final DataResult<Object> UNSUPPORTED_OPERATION_ERROR = DataResult.error(() -> {
        return "Unsupported operation";
    });
    private static final Comparator<HashCode> HASH_COMPARATOR = Comparator.comparingLong(HashCode::padToLong);
    private static final Comparator<Map.Entry<HashCode, HashCode>> MAP_ENTRY_ORDER = Entry.comparingByKey(HashOps.HASH_COMPARATOR).thenComparing(Entry.comparingByValue(HashOps.HASH_COMPARATOR));
    private static final Comparator<Pair<HashCode, HashCode>> MAPLIKE_ENTRY_ORDER = Comparator.comparing(Pair::getFirst, HashOps.HASH_COMPARATOR).thenComparing(Pair::getSecond, HashOps.HASH_COMPARATOR);
    public static final HashOps CRC32C_INSTANCE = new HashOps(Hashing.crc32c());
    private final HashFunction hashFunction;
    private final HashCode empty;
    private final HashCode emptyMap;
    private final HashCode emptyList;
    private final HashCode trueHash;
    private final HashCode falseHash;

    public HashOps(HashFunction hashFunction) {
        this.hashFunction = hashFunction;
        this.empty = hashFunction.hashBytes(HashOps.EMPTY_PAYLOAD);
        this.emptyMap = hashFunction.hashBytes(HashOps.EMPTY_MAP_PAYLOAD);
        this.emptyList = hashFunction.hashBytes(HashOps.EMPTY_LIST_PAYLOAD);
        this.falseHash = hashFunction.hashBytes(HashOps.FALSE_PAYLOAD);
        this.trueHash = hashFunction.hashBytes(HashOps.TRUE_PAYLOAD);
    }

    public HashCode empty() {
        return this.empty;
    }

    public HashCode emptyMap() {
        return this.emptyMap;
    }

    public HashCode emptyList() {
        return this.emptyList;
    }

    public HashCode createNumeric(Number value) {
        Objects.requireNonNull(value);
        byte b0 = 0;
        HashCode hashcode;

        //$FF: b0->value
        //0->java/lang/Byte
        //1->java/lang/Short
        //2->java/lang/Integer
        //3->java/lang/Long
        //4->java/lang/Double
        //5->java/lang/Float
        switch (value.typeSwitch<invokedynamic>(value, b0)) {
            case 0:
                Byte obyte = (Byte)value;

                hashcode = this.createByte(obyte);
                break;
            case 1:
                Short oshort = (Short)value;

                hashcode = this.createShort(oshort);
                break;
            case 2:
                Integer integer = (Integer)value;

                hashcode = this.createInt(integer);
                break;
            case 3:
                Long olong = (Long)value;

                hashcode = this.createLong(olong);
                break;
            case 4:
                Double odouble = (Double)value;

                hashcode = this.createDouble(odouble);
                break;
            case 5:
                Float ofloat = (Float)value;

                hashcode = this.createFloat(ofloat);
                break;
            default:
                hashcode = this.createDouble(value.doubleValue());
        }

        return hashcode;
    }

    public HashCode createByte(byte value) {
        return this.hashFunction.newHasher(2).putByte((byte) 6).putByte(value).hash();
    }

    public HashCode createShort(short value) {
        return this.hashFunction.newHasher(3).putByte((byte) 7).putShort(value).hash();
    }

    public HashCode createInt(int value) {
        return this.hashFunction.newHasher(5).putByte((byte) 8).putInt(value).hash();
    }

    public HashCode createLong(long value) {
        return this.hashFunction.newHasher(9).putByte((byte) 9).putLong(value).hash();
    }

    public HashCode createFloat(float value) {
        return this.hashFunction.newHasher(5).putByte((byte) 10).putFloat(value).hash();
    }

    public HashCode createDouble(double value) {
        return this.hashFunction.newHasher(9).putByte((byte) 11).putDouble(value).hash();
    }

    public HashCode createString(String value) {
        return this.hashFunction.newHasher().putByte((byte) 12).putInt(value.length()).putUnencodedChars(value).hash();
    }

    public HashCode createBoolean(boolean value) {
        return value ? this.trueHash : this.falseHash;
    }

    private static Hasher hashMap(Hasher hasher, Map<HashCode, HashCode> map) {
        hasher.putByte((byte) 2);
        map.entrySet().stream().sorted(HashOps.MAP_ENTRY_ORDER).forEach((entry) -> {
            hasher.putBytes(((HashCode) entry.getKey()).asBytes()).putBytes(((HashCode) entry.getValue()).asBytes());
        });
        hasher.putByte((byte) 3);
        return hasher;
    }

    private static Hasher hashMap(Hasher hasher, Stream<Pair<HashCode, HashCode>> map) {
        hasher.putByte((byte) 2);
        map.sorted(HashOps.MAPLIKE_ENTRY_ORDER).forEach((pair) -> {
            hasher.putBytes(((HashCode) pair.getFirst()).asBytes()).putBytes(((HashCode) pair.getSecond()).asBytes());
        });
        hasher.putByte((byte) 3);
        return hasher;
    }

    public HashCode createMap(Stream<Pair<HashCode, HashCode>> map) {
        return hashMap(this.hashFunction.newHasher(), map).hash();
    }

    public HashCode createMap(Map<HashCode, HashCode> map) {
        return hashMap(this.hashFunction.newHasher(), map).hash();
    }

    public HashCode createList(Stream<HashCode> input) {
        Hasher hasher = this.hashFunction.newHasher();

        hasher.putByte((byte) 4);
        input.forEach((hashcode) -> {
            hasher.putBytes(hashcode.asBytes());
        });
        hasher.putByte((byte) 5);
        return hasher.hash();
    }

    public HashCode createByteList(ByteBuffer input) {
        Hasher hasher = this.hashFunction.newHasher();

        hasher.putByte((byte) 14);
        hasher.putBytes(input);
        hasher.putByte((byte) 15);
        return hasher.hash();
    }

    public HashCode createIntList(IntStream input) {
        Hasher hasher = this.hashFunction.newHasher();

        hasher.putByte((byte) 16);
        Objects.requireNonNull(hasher);
        input.forEach(hasher::putInt);
        hasher.putByte((byte) 17);
        return hasher.hash();
    }

    public HashCode createLongList(LongStream input) {
        Hasher hasher = this.hashFunction.newHasher();

        hasher.putByte((byte) 18);
        Objects.requireNonNull(hasher);
        input.forEach(hasher::putLong);
        hasher.putByte((byte) 19);
        return hasher.hash();
    }

    public HashCode remove(HashCode input, String key) {
        return input;
    }

    public RecordBuilder<HashCode> mapBuilder() {
        return new HashOps.MapHashBuilder();
    }

    public ListBuilder<HashCode> listBuilder() {
        return new HashOps.ListHashBuilder();
    }

    public String toString() {
        return "Hash " + String.valueOf(this.hashFunction);
    }

    public <U> U convertTo(DynamicOps<U> outOps, HashCode input) {
        throw new UnsupportedOperationException("Can't convert from this type");
    }

    public Number getNumberValue(HashCode input, Number defaultValue) {
        return defaultValue;
    }

    public HashCode set(HashCode input, String key, HashCode value) {
        return input;
    }

    public HashCode update(HashCode input, String key, Function<HashCode, HashCode> function) {
        return input;
    }

    public HashCode updateGeneric(HashCode input, HashCode key, Function<HashCode, HashCode> function) {
        return input;
    }

    private static <T> DataResult<T> unsupported() {
        return HashOps.UNSUPPORTED_OPERATION_ERROR;
    }

    public DataResult<HashCode> get(HashCode input, String key) {
        return unsupported();
    }

    public DataResult<HashCode> getGeneric(HashCode input, HashCode key) {
        return unsupported();
    }

    public DataResult<Number> getNumberValue(HashCode input) {
        return unsupported();
    }

    public DataResult<Boolean> getBooleanValue(HashCode input) {
        return unsupported();
    }

    public DataResult<String> getStringValue(HashCode input) {
        return unsupported();
    }

    private boolean isEmpty(HashCode value) {
        return value.equals(this.empty);
    }

    public DataResult<HashCode> mergeToList(HashCode prefix, HashCode value) {
        return this.isEmpty(prefix) ? DataResult.success(this.createList(Stream.of(value))) : unsupported();
    }

    public DataResult<HashCode> mergeToList(HashCode prefix, List<HashCode> values) {
        return this.isEmpty(prefix) ? DataResult.success(this.createList(values.stream())) : unsupported();
    }

    public DataResult<HashCode> mergeToMap(HashCode prefix, HashCode key, HashCode value) {
        return this.isEmpty(prefix) ? DataResult.success(this.createMap(Map.of(key, value))) : unsupported();
    }

    public DataResult<HashCode> mergeToMap(HashCode prefix, Map<HashCode, HashCode> values) {
        return this.isEmpty(prefix) ? DataResult.success(this.createMap(values)) : unsupported();
    }

    public DataResult<HashCode> mergeToMap(HashCode prefix, MapLike<HashCode> values) {
        return this.isEmpty(prefix) ? DataResult.success(this.createMap(values.entries())) : unsupported();
    }

    public DataResult<Stream<Pair<HashCode, HashCode>>> getMapValues(HashCode input) {
        return unsupported();
    }

    public DataResult<Consumer<BiConsumer<HashCode, HashCode>>> getMapEntries(HashCode input) {
        return unsupported();
    }

    public DataResult<Stream<HashCode>> getStream(HashCode input) {
        return unsupported();
    }

    public DataResult<Consumer<Consumer<HashCode>>> getList(HashCode input) {
        return unsupported();
    }

    public DataResult<MapLike<HashCode>> getMap(HashCode input) {
        return unsupported();
    }

    public DataResult<ByteBuffer> getByteBuffer(HashCode input) {
        return unsupported();
    }

    public DataResult<IntStream> getIntStream(HashCode input) {
        return unsupported();
    }

    public DataResult<LongStream> getLongStream(HashCode input) {
        return unsupported();
    }

    private final class MapHashBuilder extends RecordBuilder.AbstractUniversalBuilder<HashCode, List<Pair<HashCode, HashCode>>> {

        public MapHashBuilder() {
            super(HashOps.this);
        }

        protected List<Pair<HashCode, HashCode>> initBuilder() {
            return new ArrayList();
        }

        protected List<Pair<HashCode, HashCode>> append(HashCode key, HashCode value, List<Pair<HashCode, HashCode>> builder) {
            builder.add(Pair.of(key, value));
            return builder;
        }

        protected DataResult<HashCode> build(List<Pair<HashCode, HashCode>> builder, HashCode prefix) {
            assert HashOps.this.isEmpty(prefix);

            return DataResult.success(HashOps.hashMap(HashOps.this.hashFunction.newHasher(), builder.stream()).hash());
        }
    }

    private class ListHashBuilder extends AbstractListBuilder<HashCode, Hasher> {

        public ListHashBuilder() {
            super(HashOps.this);
        }

        @Override
        protected Hasher initBuilder() {
            return HashOps.this.hashFunction.newHasher().putByte((byte) 4);
        }

        protected Hasher append(Hasher hasher, HashCode value) {
            return hasher.putBytes(value.asBytes());
        }

        protected DataResult<HashCode> build(Hasher hasher, HashCode prefix) {
            assert prefix.equals(HashOps.this.empty);

            hasher.putByte((byte) 5);
            return DataResult.success(hasher.hash());
        }
    }
}

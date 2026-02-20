package net.minecraft.world.level.storage;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Streams;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.DataResult.Success;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagType;
import net.minecraft.util.ProblemReporter;
import org.jspecify.annotations.Nullable;

public class TagValueInput implements ValueInput {

    private final ProblemReporter problemReporter;
    private final ValueInputContextHelper context;
    public final CompoundTag input;

    private TagValueInput(ProblemReporter problemReporter, ValueInputContextHelper context, CompoundTag input) {
        this.problemReporter = problemReporter;
        this.context = context;
        this.input = input;
    }

    public static ValueInput create(ProblemReporter problemReporter, HolderLookup.Provider holders, CompoundTag tag) {
        return new TagValueInput(problemReporter, new ValueInputContextHelper(holders, NbtOps.INSTANCE), tag);
    }

    public static ValueInput.ValueInputList create(ProblemReporter problemReporter, HolderLookup.Provider holders, List<CompoundTag> tags) {
        return new TagValueInput.CompoundListWrapper(problemReporter, new ValueInputContextHelper(holders, NbtOps.INSTANCE), tags);
    }

    @Override
    public <T> Optional<T> read(String name, Codec<T> codec) {
        Tag tag = this.input.get(name);

        if (tag == null) {
            return Optional.empty();
        } else {
            DataResult dataresult = codec.parse(this.context.ops(), tag);

            Objects.requireNonNull(dataresult);
            DataResult dataresult1 = dataresult;
            byte b0 = 0;
            Optional optional;

            //$FF: b0->value
            //0->com/mojang/serialization/DataResult$Success
            //1->com/mojang/serialization/DataResult$Error
            switch (dataresult1.typeSwitch<invokedynamic>(dataresult1, b0)) {
                case 0:
                    DataResult.Success<T> dataresult_success = (Success)dataresult1;

                    optional = Optional.of(dataresult_success.value());
                    break;
                case 1:
                    DataResult.Error<T> dataresult_error = (Error)dataresult1;

                    this.problemReporter.report(new TagValueInput.DecodeFromFieldFailedProblem(name, tag, dataresult_error));
                    optional = dataresult_error.partialValue();
                    break;
                default:
                    throw new MatchException((String)null, (Throwable)null);
            }

            return optional;
        }
    }

    @Override
    public <T> Optional<T> read(MapCodec<T> codec) {
        DynamicOps<Tag> dynamicops = this.context.ops();
        DataResult dataresult = dynamicops.getMap(this.input).flatMap((maplike) -> {
            return codec.decode(dynamicops, maplike);
        });

        Objects.requireNonNull(dataresult);
        DataResult dataresult1 = dataresult;
        byte b0 = 0;
        Optional optional;

        //$FF: b0->value
        //0->com/mojang/serialization/DataResult$Success
        //1->com/mojang/serialization/DataResult$Error
        switch (dataresult1.typeSwitch<invokedynamic>(dataresult1, b0)) {
            case 0:
                DataResult.Success<T> dataresult_success = (Success)dataresult1;

                optional = Optional.of(dataresult_success.value());
                break;
            case 1:
                DataResult.Error<T> dataresult_error = (Error)dataresult1;

                this.problemReporter.report(new TagValueInput.DecodeFromMapFailedProblem(dataresult_error));
                optional = dataresult_error.partialValue();
                break;
            default:
                throw new MatchException((String)null, (Throwable)null);
        }

        return optional;
    }

    private <T extends Tag> @Nullable T getOptionalTypedTag(String name, TagType<T> expectedType) {
        Tag tag = this.input.get(name);

        if (tag == null) {
            return null;
        } else {
            TagType<?> tagtype1 = tag.getType();

            if (tagtype1 != expectedType) {
                this.problemReporter.report(new TagValueInput.UnexpectedTypeProblem(name, expectedType, tagtype1));
                return null;
            } else {
                return (T) tag;
            }
        }
    }

    private @Nullable NumericTag getNumericTag(String name) {
        Tag tag = this.input.get(name);

        if (tag == null) {
            return null;
        } else if (tag instanceof NumericTag) {
            NumericTag numerictag = (NumericTag) tag;

            return numerictag;
        } else {
            this.problemReporter.report(new TagValueInput.UnexpectedNonNumberProblem(name, tag.getType()));
            return null;
        }
    }

    @Override
    public Optional<ValueInput> child(String name) {
        CompoundTag compoundtag = (CompoundTag) this.getOptionalTypedTag(name, CompoundTag.TYPE);

        return compoundtag != null ? Optional.of(this.wrapChild(name, compoundtag)) : Optional.empty();
    }

    @Override
    public ValueInput childOrEmpty(String name) {
        CompoundTag compoundtag = (CompoundTag) this.getOptionalTypedTag(name, CompoundTag.TYPE);

        return compoundtag != null ? this.wrapChild(name, compoundtag) : this.context.empty();
    }

    @Override
    public Optional<ValueInput.ValueInputList> childrenList(String name) {
        ListTag listtag = (ListTag) this.getOptionalTypedTag(name, ListTag.TYPE);

        return listtag != null ? Optional.of(this.wrapList(name, this.context, listtag)) : Optional.empty();
    }

    @Override
    public ValueInput.ValueInputList childrenListOrEmpty(String name) {
        ListTag listtag = (ListTag) this.getOptionalTypedTag(name, ListTag.TYPE);

        return listtag != null ? this.wrapList(name, this.context, listtag) : this.context.emptyList();
    }

    @Override
    public <T> Optional<ValueInput.TypedInputList<T>> list(String name, Codec<T> codec) {
        ListTag listtag = (ListTag) this.getOptionalTypedTag(name, ListTag.TYPE);

        return listtag != null ? Optional.of(this.wrapTypedList(name, listtag, codec)) : Optional.empty();
    }

    @Override
    public <T> ValueInput.TypedInputList<T> listOrEmpty(String name, Codec<T> codec) {
        ListTag listtag = (ListTag) this.getOptionalTypedTag(name, ListTag.TYPE);

        return listtag != null ? this.wrapTypedList(name, listtag, codec) : this.context.emptyTypedList();
    }

    @Override
    public boolean getBooleanOr(String name, boolean defaultValue) {
        NumericTag numerictag = this.getNumericTag(name);

        return numerictag != null ? numerictag.byteValue() != 0 : defaultValue;
    }

    @Override
    public byte getByteOr(String name, byte defaultValue) {
        NumericTag numerictag = this.getNumericTag(name);

        return numerictag != null ? numerictag.byteValue() : defaultValue;
    }

    @Override
    public int getShortOr(String name, short defaultValue) {
        NumericTag numerictag = this.getNumericTag(name);

        return numerictag != null ? numerictag.shortValue() : defaultValue;
    }

    @Override
    public Optional<Integer> getInt(String name) {
        NumericTag numerictag = this.getNumericTag(name);

        return numerictag != null ? Optional.of(numerictag.intValue()) : Optional.empty();
    }

    @Override
    public int getIntOr(String name, int defaultValue) {
        NumericTag numerictag = this.getNumericTag(name);

        return numerictag != null ? numerictag.intValue() : defaultValue;
    }

    @Override
    public long getLongOr(String name, long defaultValue) {
        NumericTag numerictag = this.getNumericTag(name);

        return numerictag != null ? numerictag.longValue() : defaultValue;
    }

    @Override
    public Optional<Long> getLong(String name) {
        NumericTag numerictag = this.getNumericTag(name);

        return numerictag != null ? Optional.of(numerictag.longValue()) : Optional.empty();
    }

    @Override
    public float getFloatOr(String name, float defaultValue) {
        NumericTag numerictag = this.getNumericTag(name);

        return numerictag != null ? numerictag.floatValue() : defaultValue;
    }

    @Override
    public double getDoubleOr(String name, double defaultValue) {
        NumericTag numerictag = this.getNumericTag(name);

        return numerictag != null ? numerictag.doubleValue() : defaultValue;
    }

    @Override
    public Optional<String> getString(String name) {
        StringTag stringtag = (StringTag) this.getOptionalTypedTag(name, StringTag.TYPE);

        return stringtag != null ? Optional.of(stringtag.value()) : Optional.empty();
    }

    @Override
    public String getStringOr(String name, String defaultValue) {
        StringTag stringtag = (StringTag) this.getOptionalTypedTag(name, StringTag.TYPE);

        return stringtag != null ? stringtag.value() : defaultValue;
    }

    @Override
    public Optional<int[]> getIntArray(String name) {
        IntArrayTag intarraytag = (IntArrayTag) this.getOptionalTypedTag(name, IntArrayTag.TYPE);

        return intarraytag != null ? Optional.of(intarraytag.getAsIntArray()) : Optional.empty();
    }

    @Override
    public HolderLookup.Provider lookup() {
        return this.context.lookup();
    }

    private ValueInput wrapChild(String name, CompoundTag compoundTag) {
        return (ValueInput) (compoundTag.isEmpty() ? this.context.empty() : new TagValueInput(this.problemReporter.forChild(new ProblemReporter.FieldPathElement(name)), this.context, compoundTag));
    }

    private static ValueInput wrapChild(ProblemReporter problemReporter, ValueInputContextHelper context, CompoundTag compoundTag) {
        return (ValueInput) (compoundTag.isEmpty() ? context.empty() : new TagValueInput(problemReporter, context, compoundTag));
    }

    private ValueInput.ValueInputList wrapList(String name, ValueInputContextHelper context, ListTag list) {
        return (ValueInput.ValueInputList) (list.isEmpty() ? context.emptyList() : new TagValueInput.ListWrapper(this.problemReporter, name, context, list));
    }

    private <T> ValueInput.TypedInputList<T> wrapTypedList(String name, ListTag list, Codec<T> codec) {
        return (ValueInput.TypedInputList<T>) (list.isEmpty() ? this.context.emptyTypedList() : new TagValueInput.TypedListWrapper(this.problemReporter, name, this.context, codec, list));
    }

    private static class ListWrapper implements ValueInput.ValueInputList {

        private final ProblemReporter problemReporter;
        private final String name;
        private final ValueInputContextHelper context;
        private final ListTag list;

        private ListWrapper(ProblemReporter problemReporter, String name, ValueInputContextHelper context, ListTag list) {
            this.problemReporter = problemReporter;
            this.name = name;
            this.context = context;
            this.list = list;
        }

        @Override
        public boolean isEmpty() {
            return this.list.isEmpty();
        }

        private ProblemReporter reporterForChild(int index) {
            return this.problemReporter.forChild(new ProblemReporter.IndexedFieldPathElement(this.name, index));
        }

        private void reportIndexUnwrapProblem(int index, Tag value) {
            this.problemReporter.report(new TagValueInput.UnexpectedListElementTypeProblem(this.name, index, CompoundTag.TYPE, value.getType()));
        }

        @Override
        public Stream<ValueInput> stream() {
            return Streams.mapWithIndex(this.list.stream(), (tag, i) -> {
                if (tag instanceof CompoundTag compoundtag) {
                    return TagValueInput.wrapChild(this.reporterForChild((int) i), this.context, compoundtag);
                } else {
                    this.reportIndexUnwrapProblem((int) i, tag);
                    return null;
                }
            }).filter(Objects::nonNull);
        }

        public Iterator<ValueInput> iterator() {
            final Iterator<Tag> iterator = this.list.iterator();

            return new AbstractIterator<ValueInput>() {
                private int index;

                protected @Nullable ValueInput computeNext() {
                    while (iterator.hasNext()) {
                        Tag tag = (Tag) iterator.next();
                        int i = this.index++;

                        if (tag instanceof CompoundTag compoundtag) {
                            return TagValueInput.wrapChild(ListWrapper.this.reporterForChild(i), ListWrapper.this.context, compoundtag);
                        }

                        ListWrapper.this.reportIndexUnwrapProblem(i, tag);
                    }

                    return (ValueInput) this.endOfData();
                }
            };
        }
    }

    private static class TypedListWrapper<T> implements ValueInput.TypedInputList<T> {

        private final ProblemReporter problemReporter;
        private final String name;
        private final ValueInputContextHelper context;
        private final Codec<T> codec;
        private final ListTag list;

        private TypedListWrapper(ProblemReporter problemReporter, String name, ValueInputContextHelper context, Codec<T> codec, ListTag list) {
            this.problemReporter = problemReporter;
            this.name = name;
            this.context = context;
            this.codec = codec;
            this.list = list;
        }

        @Override
        public boolean isEmpty() {
            return this.list.isEmpty();
        }

        private void reportIndexUnwrapProblem(int index, Tag value, DataResult.Error<?> error) {
            this.problemReporter.report(new TagValueInput.DecodeFromListFailedProblem(this.name, index, value, error));
        }

        @Override
        public Stream<T> stream() {
            return Streams.mapWithIndex(this.list.stream(), (tag, i) -> {
                DataResult dataresult = this.codec.parse(this.context.ops(), tag);

                Objects.requireNonNull(dataresult);
                DataResult dataresult1 = dataresult;
                int j = 0;
                Object object;

                //$FF: j->value
                //0->com/mojang/serialization/DataResult$Success
                //1->com/mojang/serialization/DataResult$Error
                switch (dataresult1.typeSwitch<invokedynamic>(dataresult1, j)) {
                    case 0:
                        DataResult.Success<T> dataresult_success = (Success)dataresult1;

                        object = dataresult_success.value();
                        break;
                    case 1:
                        DataResult.Error<T> dataresult_error = (Error)dataresult1;

                        this.reportIndexUnwrapProblem((int)i, tag, dataresult_error);
                        object = dataresult_error.partialValue().orElse((Object)null);
                        break;
                    default:
                        throw new MatchException((String)null, (Throwable)null);
                }

                return object;
            }).filter(Objects::nonNull);
        }

        public Iterator<T> iterator() {
            final ListIterator<Tag> listiterator = this.list.listIterator();

            return new AbstractIterator<T>() {
                protected @Nullable T computeNext() {
                    while(true) {
                        if (listiterator.hasNext()) {
                            int i = listiterator.nextIndex();
                            Tag tag = (Tag)listiterator.next();
                            DataResult dataresult = TypedListWrapper.this.codec.parse(TypedListWrapper.this.context.ops(), tag);

                            Objects.requireNonNull(dataresult);
                            DataResult dataresult1 = dataresult;
                            byte b0 = 0;

                            //$FF: b0->value
                            //0->com/mojang/serialization/DataResult$Success
                            //1->com/mojang/serialization/DataResult$Error
                            switch (dataresult1.typeSwitch<invokedynamic>(dataresult1, b0)) {
                                case 0:
                                    DataResult.Success<T> dataresult_success = (Success)dataresult1;

                                    return (T)dataresult_success.value();
                                case 1:
                                    DataResult.Error<T> dataresult_error = (Error)dataresult1;

                                    TypedListWrapper.this.reportIndexUnwrapProblem(i, tag, dataresult_error);
                                    if (!dataresult_error.partialValue().isPresent()) {
                                        continue;
                                    }

                                    return (T)dataresult_error.partialValue().get();
                                default:
                                    throw new MatchException((String)null, (Throwable)null);
                            }
                        }

                        return (T)this.endOfData();
                    }
                }
            };
        }
    }

    private static class CompoundListWrapper implements ValueInput.ValueInputList {

        private final ProblemReporter problemReporter;
        private final ValueInputContextHelper context;
        private final List<CompoundTag> list;

        public CompoundListWrapper(ProblemReporter problemReporter, ValueInputContextHelper context, List<CompoundTag> list) {
            this.problemReporter = problemReporter;
            this.context = context;
            this.list = list;
        }

        private ValueInput wrapChild(int index, CompoundTag compoundTag) {
            return TagValueInput.wrapChild(this.problemReporter.forChild(new ProblemReporter.IndexedPathElement(index)), this.context, compoundTag);
        }

        @Override
        public boolean isEmpty() {
            return this.list.isEmpty();
        }

        @Override
        public Stream<ValueInput> stream() {
            return Streams.mapWithIndex(this.list.stream(), (compoundtag, i) -> {
                return this.wrapChild((int) i, compoundtag);
            });
        }

        public Iterator<ValueInput> iterator() {
            final ListIterator<CompoundTag> listiterator = this.list.listIterator();

            return new AbstractIterator<ValueInput>() {
                protected @Nullable ValueInput computeNext() {
                    if (listiterator.hasNext()) {
                        int i = listiterator.nextIndex();
                        CompoundTag compoundtag = (CompoundTag) listiterator.next();

                        return CompoundListWrapper.this.wrapChild(i, compoundtag);
                    } else {
                        return (ValueInput) this.endOfData();
                    }
                }
            };
        }
    }

    public static record DecodeFromFieldFailedProblem(String name, Tag tag, DataResult.Error<?> error) implements ProblemReporter.Problem {

        @Override
        public String description() {
            String s = String.valueOf(this.tag);

            return "Failed to decode value '" + s + "' from field '" + this.name + "': " + this.error.message();
        }
    }

    public static record DecodeFromListFailedProblem(String name, int index, Tag tag, DataResult.Error<?> error) implements ProblemReporter.Problem {

        @Override
        public String description() {
            String s = String.valueOf(this.tag);

            return "Failed to decode value '" + s + "' from field '" + this.name + "' at index " + this.index + "': " + this.error.message();
        }
    }

    public static record DecodeFromMapFailedProblem(DataResult.Error<?> error) implements ProblemReporter.Problem {

        @Override
        public String description() {
            return "Failed to decode from map: " + this.error.message();
        }
    }

    public static record UnexpectedTypeProblem(String name, TagType<?> expected, TagType<?> actual) implements ProblemReporter.Problem {

        @Override
        public String description() {
            return "Expected field '" + this.name + "' to contain value of type " + this.expected.getName() + ", but got " + this.actual.getName();
        }
    }

    public static record UnexpectedNonNumberProblem(String name, TagType<?> actual) implements ProblemReporter.Problem {

        @Override
        public String description() {
            return "Expected field '" + this.name + "' to contain number, but got " + this.actual.getName();
        }
    }

    public static record UnexpectedListElementTypeProblem(String name, int index, TagType<?> expected, TagType<?> actual) implements ProblemReporter.Problem {

        @Override
        public String description() {
            return "Expected list '" + this.name + "' to contain at index " + this.index + " value of type " + this.expected.getName() + ", but got " + this.actual.getName();
        }
    }
}

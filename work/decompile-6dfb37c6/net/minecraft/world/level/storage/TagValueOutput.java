package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.DataResult.Success;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import org.jspecify.annotations.Nullable;

public class TagValueOutput implements ValueOutput {

    private final ProblemReporter problemReporter;
    private final DynamicOps<Tag> ops;
    private final CompoundTag output;

    private TagValueOutput(ProblemReporter problemReporter, DynamicOps<Tag> ops, CompoundTag output) {
        this.problemReporter = problemReporter;
        this.ops = ops;
        this.output = output;
    }

    public static TagValueOutput createWithContext(ProblemReporter problemReporter, HolderLookup.Provider provider) {
        return new TagValueOutput(problemReporter, provider.createSerializationContext(NbtOps.INSTANCE), new CompoundTag());
    }

    public static TagValueOutput createWithoutContext(ProblemReporter problemReporter) {
        return new TagValueOutput(problemReporter, NbtOps.INSTANCE, new CompoundTag());
    }

    @Override
    public <T> void store(String name, Codec<T> codec, T value) {
        DataResult dataresult = codec.encodeStart(this.ops, value);

        Objects.requireNonNull(dataresult);
        DataResult dataresult1 = dataresult;
        byte b0 = 0;

        //$FF: b0->value
        //0->com/mojang/serialization/DataResult$Success
        //1->com/mojang/serialization/DataResult$Error
        switch (dataresult1.typeSwitch<invokedynamic>(dataresult1, b0)) {
            case 0:
                DataResult.Success<Tag> dataresult_success = (Success)dataresult1;

                this.output.put(name, (Tag)dataresult_success.value());
                break;
            case 1:
                DataResult.Error<Tag> dataresult_error = (Error)dataresult1;

                this.problemReporter.report(new TagValueOutput.EncodeToFieldFailedProblem(name, value, dataresult_error));
                dataresult_error.partialValue().ifPresent((tag) -> {
                    this.output.put(name, tag);
                });
                break;
            default:
                throw new MatchException((String)null, (Throwable)null);
        }

    }

    @Override
    public <T> void storeNullable(String name, Codec<T> codec, @Nullable T value) {
        if (value != null) {
            this.store(name, codec, value);
        }

    }

    @Override
    public <T> void store(MapCodec<T> codec, T value) {
        DataResult dataresult = codec.encoder().encodeStart(this.ops, value);

        Objects.requireNonNull(dataresult);
        DataResult dataresult1 = dataresult;
        byte b0 = 0;

        //$FF: b0->value
        //0->com/mojang/serialization/DataResult$Success
        //1->com/mojang/serialization/DataResult$Error
        switch (dataresult1.typeSwitch<invokedynamic>(dataresult1, b0)) {
            case 0:
                DataResult.Success<Tag> dataresult_success = (Success)dataresult1;

                this.output.merge((CompoundTag)dataresult_success.value());
                break;
            case 1:
                DataResult.Error<Tag> dataresult_error = (Error)dataresult1;

                this.problemReporter.report(new TagValueOutput.EncodeToMapFailedProblem(value, dataresult_error));
                dataresult_error.partialValue().ifPresent((tag) -> {
                    this.output.merge((CompoundTag)tag);
                });
                break;
            default:
                throw new MatchException((String)null, (Throwable)null);
        }

    }

    @Override
    public void putBoolean(String name, boolean value) {
        this.output.putBoolean(name, value);
    }

    @Override
    public void putByte(String name, byte value) {
        this.output.putByte(name, value);
    }

    @Override
    public void putShort(String name, short value) {
        this.output.putShort(name, value);
    }

    @Override
    public void putInt(String name, int value) {
        this.output.putInt(name, value);
    }

    @Override
    public void putLong(String name, long value) {
        this.output.putLong(name, value);
    }

    @Override
    public void putFloat(String name, float value) {
        this.output.putFloat(name, value);
    }

    @Override
    public void putDouble(String name, double value) {
        this.output.putDouble(name, value);
    }

    @Override
    public void putString(String name, String value) {
        this.output.putString(name, value);
    }

    @Override
    public void putIntArray(String name, int[] value) {
        this.output.putIntArray(name, value);
    }

    private ProblemReporter reporterForChild(String name) {
        return this.problemReporter.forChild(new ProblemReporter.FieldPathElement(name));
    }

    @Override
    public ValueOutput child(String name) {
        CompoundTag compoundtag = new CompoundTag();

        this.output.put(name, compoundtag);
        return new TagValueOutput(this.reporterForChild(name), this.ops, compoundtag);
    }

    @Override
    public ValueOutput.ValueOutputList childrenList(String name) {
        ListTag listtag = new ListTag();

        this.output.put(name, listtag);
        return new TagValueOutput.ListWrapper(name, this.problemReporter, this.ops, listtag);
    }

    @Override
    public <T> ValueOutput.TypedOutputList<T> list(String name, Codec<T> codec) {
        ListTag listtag = new ListTag();

        this.output.put(name, listtag);
        return new TagValueOutput.TypedListWrapper<T>(this.problemReporter, name, this.ops, codec, listtag);
    }

    @Override
    public void discard(String name) {
        this.output.remove(name);
    }

    @Override
    public boolean isEmpty() {
        return this.output.isEmpty();
    }

    public CompoundTag buildResult() {
        return this.output;
    }

    private static class ListWrapper implements ValueOutput.ValueOutputList {

        private final String fieldName;
        private final ProblemReporter problemReporter;
        private final DynamicOps<Tag> ops;
        private final ListTag output;

        private ListWrapper(String fieldName, ProblemReporter problemReporter, DynamicOps<Tag> ops, ListTag output) {
            this.fieldName = fieldName;
            this.problemReporter = problemReporter;
            this.ops = ops;
            this.output = output;
        }

        @Override
        public ValueOutput addChild() {
            int i = this.output.size();
            CompoundTag compoundtag = new CompoundTag();

            this.output.add(compoundtag);
            return new TagValueOutput(this.problemReporter.forChild(new ProblemReporter.IndexedFieldPathElement(this.fieldName, i)), this.ops, compoundtag);
        }

        @Override
        public void discardLast() {
            this.output.removeLast();
        }

        @Override
        public boolean isEmpty() {
            return this.output.isEmpty();
        }
    }

    private static class TypedListWrapper<T> implements ValueOutput.TypedOutputList<T> {

        private final ProblemReporter problemReporter;
        private final String name;
        private final DynamicOps<Tag> ops;
        private final Codec<T> codec;
        private final ListTag output;

        private TypedListWrapper(ProblemReporter problemReporter, String name, DynamicOps<Tag> ops, Codec<T> codec, ListTag output) {
            this.problemReporter = problemReporter;
            this.name = name;
            this.ops = ops;
            this.codec = codec;
            this.output = output;
        }

        @Override
        public void add(T value) {
            DataResult dataresult = this.codec.encodeStart(this.ops, value);

            Objects.requireNonNull(dataresult);
            DataResult dataresult1 = dataresult;
            byte b0 = 0;

            //$FF: b0->value
            //0->com/mojang/serialization/DataResult$Success
            //1->com/mojang/serialization/DataResult$Error
            switch (dataresult1.typeSwitch<invokedynamic>(dataresult1, b0)) {
                case 0:
                    DataResult.Success<Tag> dataresult_success = (Success)dataresult1;

                    this.output.add((Tag)dataresult_success.value());
                    break;
                case 1:
                    DataResult.Error<Tag> dataresult_error = (Error)dataresult1;

                    this.problemReporter.report(new TagValueOutput.EncodeToListFailedProblem(this.name, value, dataresult_error));
                    Optional optional = dataresult_error.partialValue();
                    ListTag listtag = this.output;

                    Objects.requireNonNull(this.output);
                    optional.ifPresent(listtag::add);
                    break;
                default:
                    throw new MatchException((String)null, (Throwable)null);
            }

        }

        @Override
        public boolean isEmpty() {
            return this.output.isEmpty();
        }
    }

    public static record EncodeToFieldFailedProblem(String name, Object value, DataResult.Error<?> error) implements ProblemReporter.Problem {

        @Override
        public String description() {
            String s = String.valueOf(this.value);

            return "Failed to encode value '" + s + "' to field '" + this.name + "': " + this.error.message();
        }
    }

    public static record EncodeToListFailedProblem(String name, Object value, DataResult.Error<?> error) implements ProblemReporter.Problem {

        @Override
        public String description() {
            String s = String.valueOf(this.value);

            return "Failed to append value '" + s + "' to list '" + this.name + "': " + this.error.message();
        }
    }

    public static record EncodeToMapFailedProblem(Object value, DataResult.Error<?> error) implements ProblemReporter.Problem {

        @Override
        public String description() {
            String s = String.valueOf(this.value);

            return "Failed to merge value '" + s + "' to an object: " + this.error.message();
        }
    }
}

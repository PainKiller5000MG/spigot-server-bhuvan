package net.minecraft.util;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.ListBuilder;
import java.util.function.UnaryOperator;

abstract class AbstractListBuilder<T, B> implements ListBuilder<T> {

    private final DynamicOps<T> ops;
    protected DataResult<B> builder = DataResult.success(this.initBuilder(), Lifecycle.stable());

    protected AbstractListBuilder(DynamicOps<T> ops) {
        this.ops = ops;
    }

    public DynamicOps<T> ops() {
        return this.ops;
    }

    protected abstract B initBuilder();

    protected abstract B append(B builder, T value);

    protected abstract DataResult<T> build(B builder, T prefix);

    public ListBuilder<T> add(T value) {
        this.builder = this.builder.map((object) -> {
            return this.append(object, value);
        });
        return this;
    }

    public ListBuilder<T> add(DataResult<T> value) {
        this.builder = this.builder.apply2stable(this::append, value);
        return this;
    }

    public ListBuilder<T> withErrorsFrom(DataResult<?> result) {
        this.builder = this.builder.flatMap((object) -> {
            return result.map((object1) -> {
                return object;
            });
        });
        return this;
    }

    public ListBuilder<T> mapError(UnaryOperator<String> onError) {
        this.builder = this.builder.mapError(onError);
        return this;
    }

    public DataResult<T> build(T prefix) {
        DataResult<T> dataresult = this.builder.flatMap((object) -> {
            return this.build(object, prefix);
        });

        this.builder = DataResult.success(this.initBuilder(), Lifecycle.stable());
        return dataresult;
    }
}

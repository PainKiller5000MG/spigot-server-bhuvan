package net.minecraft.server.level;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public interface ChunkResult<T> {

    static <T> ChunkResult<T> of(T value) {
        return new ChunkResult.Success<T>(value);
    }

    static <T> ChunkResult<T> error(String error) {
        return error(() -> {
            return error;
        });
    }

    static <T> ChunkResult<T> error(Supplier<String> errorSupplier) {
        return new ChunkResult.Fail<T>(errorSupplier);
    }

    boolean isSuccess();

    @Nullable
    T orElse(@Nullable T orElse);

    static <R> @Nullable R orElse(ChunkResult<? extends R> chunkResult, @Nullable R orElse) {
        R r1 = chunkResult.orElse((Object) null);

        return r1 != null ? r1 : orElse;
    }

    @Nullable
    String getError();

    ChunkResult<T> ifSuccess(Consumer<T> consumer);

    <R> ChunkResult<R> map(Function<T, R> map);

    <E extends Throwable> T orElseThrow(Supplier<E> exceptionSupplier) throws E;

    public static record Success<T>(T value) implements ChunkResult<T> {

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public T orElse(@Nullable T orElse) {
            return this.value;
        }

        @Override
        public @Nullable String getError() {
            return null;
        }

        @Override
        public ChunkResult<T> ifSuccess(Consumer<T> consumer) {
            consumer.accept(this.value);
            return this;
        }

        @Override
        public <R> ChunkResult<R> map(Function<T, R> map) {
            return new ChunkResult.Success<R>(map.apply(this.value));
        }

        @Override
        public <E extends Throwable> T orElseThrow(Supplier<E> exceptionSupplier) throws E {
            return this.value;
        }
    }

    public static record Fail<T>(Supplier<String> error) implements ChunkResult<T> {

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public @Nullable T orElse(@Nullable T orElse) {
            return orElse;
        }

        @Override
        public String getError() {
            return (String) this.error.get();
        }

        @Override
        public ChunkResult<T> ifSuccess(Consumer<T> consumer) {
            return this;
        }

        @Override
        public <R> ChunkResult<R> map(Function<T, R> map) {
            return new ChunkResult.Fail<R>(this.error);
        }

        @Override
        public <E extends Throwable> T orElseThrow(Supplier<E> exceptionSupplier) throws E {
            throw (Throwable) exceptionSupplier.get();
        }
    }
}

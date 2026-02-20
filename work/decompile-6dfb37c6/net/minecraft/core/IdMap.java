package net.minecraft.core;

import org.jspecify.annotations.Nullable;

public interface IdMap<T> extends Iterable<T> {

    int DEFAULT = -1;

    int getId(T thing);

    @Nullable
    T byId(int id);

    default T byIdOrThrow(int id) {
        T t0 = (T) this.byId(id);

        if (t0 == null) {
            throw new IllegalArgumentException("No value with id " + id);
        } else {
            return t0;
        }
    }

    default int getIdOrThrow(T value) {
        int i = this.getId(value);

        if (i == -1) {
            String s = String.valueOf(value);

            throw new IllegalArgumentException("Can't find id for '" + s + "' in map " + String.valueOf(this));
        } else {
            return i;
        }
    }

    int size();
}

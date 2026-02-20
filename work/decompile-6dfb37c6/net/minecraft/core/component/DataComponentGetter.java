package net.minecraft.core.component;

import org.jspecify.annotations.Nullable;

public interface DataComponentGetter {

    <T> @Nullable T get(DataComponentType<? extends T> type);

    default <T> T getOrDefault(DataComponentType<? extends T> type, T defaultValue) {
        T t1 = (T) this.get(type);

        return t1 != null ? t1 : defaultValue;
    }

    default <T> @Nullable TypedDataComponent<T> getTyped(DataComponentType<T> type) {
        T t0 = (T) this.get(type);

        return t0 != null ? new TypedDataComponent(type, t0) : null;
    }
}

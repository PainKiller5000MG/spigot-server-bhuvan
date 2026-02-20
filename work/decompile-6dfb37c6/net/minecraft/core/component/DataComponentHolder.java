package net.minecraft.core.component;

import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public interface DataComponentHolder extends DataComponentGetter {

    DataComponentMap getComponents();

    @Override
    default <T> @Nullable T get(DataComponentType<? extends T> type) {
        return (T) this.getComponents().get(type);
    }

    default <T> Stream<T> getAllOfType(Class<? extends T> valueClass) {
        return this.getComponents().stream().map(TypedDataComponent::value).filter((object) -> {
            return valueClass.isAssignableFrom(object.getClass());
        }).map((object) -> {
            return object;
        });
    }

    @Override
    default <T> T getOrDefault(DataComponentType<? extends T> type, T defaultValue) {
        return (T) this.getComponents().getOrDefault(type, defaultValue);
    }

    default boolean has(DataComponentType<?> type) {
        return this.getComponents().has(type);
    }
}

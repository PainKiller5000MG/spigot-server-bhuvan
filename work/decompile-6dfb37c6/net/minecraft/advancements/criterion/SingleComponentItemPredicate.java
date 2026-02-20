package net.minecraft.advancements.criterion;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.predicates.DataComponentPredicate;

public interface SingleComponentItemPredicate<T> extends DataComponentPredicate {

    @Override
    default boolean matches(DataComponentGetter components) {
        T t0 = (T) components.get(this.componentType());

        return t0 != null && this.matches(t0);
    }

    DataComponentType<T> componentType();

    boolean matches(T value);
}

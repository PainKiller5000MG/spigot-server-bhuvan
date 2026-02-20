package net.minecraft.world.level.storage.loot.functions;

import java.util.Arrays;
import java.util.function.Function;

public interface FunctionUserBuilder<T extends FunctionUserBuilder<T>> {

    T apply(LootItemFunction.Builder builder);

    default <E> T apply(Iterable<E> collection, Function<E, LootItemFunction.Builder> functionProvider) {
        T t0 = this.unwrap();

        for (E e0 : collection) {
            t0 = t0.apply((LootItemFunction.Builder) functionProvider.apply(e0));
        }

        return t0;
    }

    default <E> T apply(E[] collection, Function<E, LootItemFunction.Builder> functionProvider) {
        return (T) this.apply(Arrays.asList(collection), functionProvider);
    }

    T unwrap();
}

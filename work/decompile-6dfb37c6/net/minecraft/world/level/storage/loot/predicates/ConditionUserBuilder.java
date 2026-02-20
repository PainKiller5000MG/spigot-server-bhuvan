package net.minecraft.world.level.storage.loot.predicates;

import java.util.function.Function;

public interface ConditionUserBuilder<T extends ConditionUserBuilder<T>> {

    T when(LootItemCondition.Builder builder);

    default <E> T when(Iterable<E> collection, Function<E, LootItemCondition.Builder> conditionProvider) {
        T t0 = this.unwrap();

        for (E e0 : collection) {
            t0 = t0.when((LootItemCondition.Builder) conditionProvider.apply(e0));
        }

        return t0;
    }

    T unwrap();
}

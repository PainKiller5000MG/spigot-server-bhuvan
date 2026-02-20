package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.Products;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.ConditionUserBuilder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public abstract class LootItemConditionalFunction implements LootItemFunction {

    protected final List<LootItemCondition> predicates;
    private final Predicate<LootContext> compositePredicates;

    protected LootItemConditionalFunction(List<LootItemCondition> predicates) {
        this.predicates = predicates;
        this.compositePredicates = Util.allOf(predicates);
    }

    @Override
    public abstract LootItemFunctionType<? extends LootItemConditionalFunction> getType();

    protected static <T extends LootItemConditionalFunction> Products.P1<RecordCodecBuilder.Mu<T>, List<LootItemCondition>> commonFields(RecordCodecBuilder.Instance<T> i) {
        return i.group(LootItemCondition.DIRECT_CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter((lootitemconditionalfunction) -> {
            return lootitemconditionalfunction.predicates;
        }));
    }

    public final ItemStack apply(ItemStack itemStack, LootContext context) {
        return this.compositePredicates.test(context) ? this.run(itemStack, context) : itemStack;
    }

    protected abstract ItemStack run(ItemStack itemStack, LootContext context);

    @Override
    public void validate(ValidationContext context) {
        LootItemFunction.super.validate(context);

        for (int i = 0; i < this.predicates.size(); ++i) {
            ((LootItemCondition) this.predicates.get(i)).validate(context.forChild(new ProblemReporter.IndexedFieldPathElement("conditions", i)));
        }

    }

    protected static LootItemConditionalFunction.Builder<?> simpleBuilder(Function<List<LootItemCondition>, LootItemFunction> constructor) {
        return new LootItemConditionalFunction.DummyBuilder(constructor);
    }

    public abstract static class Builder<T extends LootItemConditionalFunction.Builder<T>> implements LootItemFunction.Builder, ConditionUserBuilder<T> {

        private final ImmutableList.Builder<LootItemCondition> conditions = ImmutableList.builder();

        public Builder() {}

        @Override
        public T when(LootItemCondition.Builder condition) {
            this.conditions.add(condition.build());
            return (T) this.getThis();
        }

        @Override
        public final T unwrap() {
            return (T) this.getThis();
        }

        protected abstract T getThis();

        protected List<LootItemCondition> getConditions() {
            return this.conditions.build();
        }
    }

    private static final class DummyBuilder extends LootItemConditionalFunction.Builder<LootItemConditionalFunction.DummyBuilder> {

        private final Function<List<LootItemCondition>, LootItemFunction> constructor;

        public DummyBuilder(Function<List<LootItemCondition>, LootItemFunction> constructor) {
            this.constructor = constructor;
        }

        @Override
        protected LootItemConditionalFunction.DummyBuilder getThis() {
            return this;
        }

        @Override
        public LootItemFunction build() {
            return (LootItemFunction) this.constructor.apply(this.getConditions());
        }
    }
}

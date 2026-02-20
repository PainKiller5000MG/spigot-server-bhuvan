package net.minecraft.world.level.storage.loot.entries;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.Products;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.ConditionUserBuilder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public abstract class LootPoolEntryContainer implements ComposableEntryContainer {

    protected final List<LootItemCondition> conditions;
    private final Predicate<LootContext> compositeCondition;

    protected LootPoolEntryContainer(List<LootItemCondition> conditions) {
        this.conditions = conditions;
        this.compositeCondition = Util.allOf(conditions);
    }

    protected static <T extends LootPoolEntryContainer> Products.P1<RecordCodecBuilder.Mu<T>, List<LootItemCondition>> commonFields(RecordCodecBuilder.Instance<T> i) {
        return i.group(LootItemCondition.DIRECT_CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter((lootpoolentrycontainer) -> {
            return lootpoolentrycontainer.conditions;
        }));
    }

    public void validate(ValidationContext output) {
        for (int i = 0; i < this.conditions.size(); ++i) {
            ((LootItemCondition) this.conditions.get(i)).validate(output.forChild(new ProblemReporter.IndexedFieldPathElement("conditions", i)));
        }

    }

    protected final boolean canRun(LootContext context) {
        return this.compositeCondition.test(context);
    }

    public abstract LootPoolEntryType getType();

    public abstract static class Builder<T extends LootPoolEntryContainer.Builder<T>> implements ConditionUserBuilder<T> {

        private final ImmutableList.Builder<LootItemCondition> conditions = ImmutableList.builder();

        public Builder() {}

        protected abstract T getThis();

        @Override
        public T when(LootItemCondition.Builder condition) {
            this.conditions.add(condition.build());
            return (T) this.getThis();
        }

        @Override
        public final T unwrap() {
            return (T) this.getThis();
        }

        protected List<LootItemCondition> getConditions() {
            return this.conditions.build();
        }

        public AlternativesEntry.Builder otherwise(LootPoolEntryContainer.Builder<?> other) {
            return new AlternativesEntry.Builder(new LootPoolEntryContainer.Builder[]{this, other});
        }

        public EntryGroup.Builder append(LootPoolEntryContainer.Builder<?> other) {
            return new EntryGroup.Builder(new LootPoolEntryContainer.Builder[]{this, other});
        }

        public SequentialEntry.Builder then(LootPoolEntryContainer.Builder<?> other) {
            return new SequentialEntry.Builder(new LootPoolEntryContainer.Builder[]{this, other});
        }

        public abstract LootPoolEntryContainer build();
    }
}

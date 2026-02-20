package net.minecraft.world.level.storage.loot.predicates;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;

public abstract class CompositeLootItemCondition implements LootItemCondition {

    protected final List<LootItemCondition> terms;
    private final Predicate<LootContext> composedPredicate;

    protected CompositeLootItemCondition(List<LootItemCondition> terms, Predicate<LootContext> composedPredicate) {
        this.terms = terms;
        this.composedPredicate = composedPredicate;
    }

    protected static <T extends CompositeLootItemCondition> MapCodec<T> createCodec(Function<List<LootItemCondition>, T> factory) {
        return RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(LootItemCondition.DIRECT_CODEC.listOf().fieldOf("terms").forGetter((compositelootitemcondition) -> {
                return compositelootitemcondition.terms;
            })).apply(instance, factory);
        });
    }

    protected static <T extends CompositeLootItemCondition> Codec<T> createInlineCodec(Function<List<LootItemCondition>, T> factory) {
        return LootItemCondition.DIRECT_CODEC.listOf().xmap(factory, (compositelootitemcondition) -> {
            return compositelootitemcondition.terms;
        });
    }

    public final boolean test(LootContext context) {
        return this.composedPredicate.test(context);
    }

    @Override
    public void validate(ValidationContext output) {
        LootItemCondition.super.validate(output);

        for (int i = 0; i < this.terms.size(); ++i) {
            ((LootItemCondition) this.terms.get(i)).validate(output.forChild(new ProblemReporter.IndexedFieldPathElement("terms", i)));
        }

    }

    public abstract static class Builder implements LootItemCondition.Builder {

        private final ImmutableList.Builder<LootItemCondition> terms = ImmutableList.builder();

        protected Builder(LootItemCondition.Builder... terms) {
            for (LootItemCondition.Builder lootitemcondition_builder : terms) {
                this.terms.add(lootitemcondition_builder.build());
            }

        }

        public void addTerm(LootItemCondition.Builder term) {
            this.terms.add(term.build());
        }

        @Override
        public LootItemCondition build() {
            return this.create(this.terms.build());
        }

        protected abstract LootItemCondition create(List<LootItemCondition> terms);
    }
}

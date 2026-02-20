package net.minecraft.world.level.storage.loot.entries;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class AlternativesEntry extends CompositeEntryBase {

    public static final MapCodec<AlternativesEntry> CODEC = createCodec(AlternativesEntry::new);
    public static final ProblemReporter.Problem UNREACHABLE_PROBLEM = new ProblemReporter.Problem() {
        @Override
        public String description() {
            return "Unreachable entry!";
        }
    };

    AlternativesEntry(List<LootPoolEntryContainer> children, List<LootItemCondition> conditions) {
        super(children, conditions);
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.ALTERNATIVES;
    }

    @Override
    protected ComposableEntryContainer compose(List<? extends ComposableEntryContainer> entries) {
        ComposableEntryContainer composableentrycontainer;

        switch (entries.size()) {
            case 0:
                composableentrycontainer = AlternativesEntry.ALWAYS_FALSE;
                break;
            case 1:
                composableentrycontainer = (ComposableEntryContainer) entries.get(0);
                break;
            case 2:
                composableentrycontainer = ((ComposableEntryContainer) entries.get(0)).or((ComposableEntryContainer) entries.get(1));
                break;
            default:
                composableentrycontainer = (lootcontext, consumer) -> {
                    for (ComposableEntryContainer composableentrycontainer1 : entries) {
                        if (composableentrycontainer1.expand(lootcontext, consumer)) {
                            return true;
                        }
                    }

                    return false;
                };
        }

        return composableentrycontainer;
    }

    @Override
    public void validate(ValidationContext context) {
        super.validate(context);

        for (int i = 0; i < this.children.size() - 1; ++i) {
            if (((LootPoolEntryContainer) this.children.get(i)).conditions.isEmpty()) {
                context.reportProblem(AlternativesEntry.UNREACHABLE_PROBLEM);
            }
        }

    }

    public static AlternativesEntry.Builder alternatives(LootPoolEntryContainer.Builder<?>... entries) {
        return new AlternativesEntry.Builder(entries);
    }

    public static <E> AlternativesEntry.Builder alternatives(Collection<E> items, Function<E, LootPoolEntryContainer.Builder<?>> provider) {
        Stream stream = items.stream();

        Objects.requireNonNull(provider);
        return new AlternativesEntry.Builder((LootPoolEntryContainer.Builder[]) stream.map(provider::apply).toArray((i) -> {
            return new LootPoolEntryContainer.Builder[i];
        }));
    }

    public static class Builder extends LootPoolEntryContainer.Builder<AlternativesEntry.Builder> {

        private final ImmutableList.Builder<LootPoolEntryContainer> entries = ImmutableList.builder();

        public Builder(LootPoolEntryContainer.Builder<?>... entries) {
            for (LootPoolEntryContainer.Builder<?> lootpoolentrycontainer_builder : entries) {
                this.entries.add(lootpoolentrycontainer_builder.build());
            }

        }

        @Override
        protected AlternativesEntry.Builder getThis() {
            return this;
        }

        @Override
        public AlternativesEntry.Builder otherwise(LootPoolEntryContainer.Builder<?> other) {
            this.entries.add(other.build());
            return this;
        }

        @Override
        public LootPoolEntryContainer build() {
            return new AlternativesEntry(this.entries.build(), this.getConditions());
        }
    }
}

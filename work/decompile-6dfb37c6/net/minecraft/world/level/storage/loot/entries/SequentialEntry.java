package net.minecraft.world.level.storage.loot.entries;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SequentialEntry extends CompositeEntryBase {

    public static final MapCodec<SequentialEntry> CODEC = createCodec(SequentialEntry::new);

    SequentialEntry(List<LootPoolEntryContainer> children, List<LootItemCondition> conditions) {
        super(children, conditions);
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.SEQUENCE;
    }

    @Override
    protected ComposableEntryContainer compose(List<? extends ComposableEntryContainer> entries) {
        ComposableEntryContainer composableentrycontainer;

        switch (entries.size()) {
            case 0:
                composableentrycontainer = SequentialEntry.ALWAYS_TRUE;
                break;
            case 1:
                composableentrycontainer = (ComposableEntryContainer) entries.get(0);
                break;
            case 2:
                composableentrycontainer = ((ComposableEntryContainer) entries.get(0)).and((ComposableEntryContainer) entries.get(1));
                break;
            default:
                composableentrycontainer = (lootcontext, consumer) -> {
                    for (ComposableEntryContainer composableentrycontainer1 : entries) {
                        if (!composableentrycontainer1.expand(lootcontext, consumer)) {
                            return false;
                        }
                    }

                    return true;
                };
        }

        return composableentrycontainer;
    }

    public static SequentialEntry.Builder sequential(LootPoolEntryContainer.Builder<?>... entries) {
        return new SequentialEntry.Builder(entries);
    }

    public static class Builder extends LootPoolEntryContainer.Builder<SequentialEntry.Builder> {

        private final ImmutableList.Builder<LootPoolEntryContainer> entries = ImmutableList.builder();

        public Builder(LootPoolEntryContainer.Builder<?>... entries) {
            for (LootPoolEntryContainer.Builder<?> lootpoolentrycontainer_builder : entries) {
                this.entries.add(lootpoolentrycontainer_builder.build());
            }

        }

        @Override
        protected SequentialEntry.Builder getThis() {
            return this;
        }

        @Override
        public SequentialEntry.Builder then(LootPoolEntryContainer.Builder<?> other) {
            this.entries.add(other.build());
            return this;
        }

        @Override
        public LootPoolEntryContainer build() {
            return new SequentialEntry(this.entries.build(), this.getConditions());
        }
    }
}

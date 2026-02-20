package net.minecraft.world.level.storage.loot.entries;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class EntryGroup extends CompositeEntryBase {

    public static final MapCodec<EntryGroup> CODEC = createCodec(EntryGroup::new);

    EntryGroup(List<LootPoolEntryContainer> children, List<LootItemCondition> conditions) {
        super(children, conditions);
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.GROUP;
    }

    @Override
    protected ComposableEntryContainer compose(List<? extends ComposableEntryContainer> entries) {
        ComposableEntryContainer composableentrycontainer;

        switch (entries.size()) {
            case 0:
                composableentrycontainer = EntryGroup.ALWAYS_TRUE;
                break;
            case 1:
                composableentrycontainer = (ComposableEntryContainer) entries.get(0);
                break;
            case 2:
                ComposableEntryContainer composableentrycontainer1 = (ComposableEntryContainer) entries.get(0);
                ComposableEntryContainer composableentrycontainer2 = (ComposableEntryContainer) entries.get(1);

                composableentrycontainer = (lootcontext, consumer) -> {
                    composableentrycontainer1.expand(lootcontext, consumer);
                    composableentrycontainer2.expand(lootcontext, consumer);
                    return true;
                };
                break;
            default:
                composableentrycontainer = (lootcontext, consumer) -> {
                    for (ComposableEntryContainer composableentrycontainer3 : entries) {
                        composableentrycontainer3.expand(lootcontext, consumer);
                    }

                    return true;
                };
        }

        return composableentrycontainer;
    }

    public static EntryGroup.Builder list(LootPoolEntryContainer.Builder<?>... entries) {
        return new EntryGroup.Builder(entries);
    }

    public static class Builder extends LootPoolEntryContainer.Builder<EntryGroup.Builder> {

        private final ImmutableList.Builder<LootPoolEntryContainer> entries = ImmutableList.builder();

        public Builder(LootPoolEntryContainer.Builder<?>... entries) {
            for (LootPoolEntryContainer.Builder<?> lootpoolentrycontainer_builder : entries) {
                this.entries.add(lootpoolentrycontainer_builder.build());
            }

        }

        @Override
        protected EntryGroup.Builder getThis() {
            return this;
        }

        @Override
        public EntryGroup.Builder append(LootPoolEntryContainer.Builder<?> other) {
            this.entries.add(other.build());
            return this;
        }

        @Override
        public LootPoolEntryContainer build() {
            return new EntryGroup(this.entries.build(), this.getConditions());
        }
    }
}

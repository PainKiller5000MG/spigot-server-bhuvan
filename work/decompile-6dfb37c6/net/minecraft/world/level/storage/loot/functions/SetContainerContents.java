package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.ContainerComponentManipulator;
import net.minecraft.world.level.storage.loot.ContainerComponentManipulators;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetContainerContents extends LootItemConditionalFunction {

    public static final MapCodec<SetContainerContents> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(instance.group(ContainerComponentManipulators.CODEC.fieldOf("component").forGetter((setcontainercontents) -> {
            return setcontainercontents.component;
        }), LootPoolEntries.CODEC.listOf().fieldOf("entries").forGetter((setcontainercontents) -> {
            return setcontainercontents.entries;
        }))).apply(instance, SetContainerContents::new);
    });
    private final ContainerComponentManipulator<?> component;
    private final List<LootPoolEntryContainer> entries;

    private SetContainerContents(List<LootItemCondition> predicates, ContainerComponentManipulator<?> component, List<LootPoolEntryContainer> entries) {
        super(predicates);
        this.component = component;
        this.entries = List.copyOf(entries);
    }

    @Override
    public LootItemFunctionType<SetContainerContents> getType() {
        return LootItemFunctions.SET_CONTENTS;
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext context) {
        if (itemStack.isEmpty()) {
            return itemStack;
        } else {
            Stream.Builder<ItemStack> stream_builder = Stream.builder();

            this.entries.forEach((lootpoolentrycontainer) -> {
                lootpoolentrycontainer.expand(context, (lootpoolentry) -> {
                    ServerLevel serverlevel = context.getLevel();

                    Objects.requireNonNull(stream_builder);
                    lootpoolentry.createItemStack(LootTable.createStackSplitter(serverlevel, stream_builder::add), context);
                });
            });
            this.component.setContents(itemStack, stream_builder.build());
            return itemStack;
        }
    }

    @Override
    public void validate(ValidationContext context) {
        super.validate(context);

        for (int i = 0; i < this.entries.size(); ++i) {
            ((LootPoolEntryContainer) this.entries.get(i)).validate(context.forChild(new ProblemReporter.IndexedFieldPathElement("entries", i)));
        }

    }

    public static SetContainerContents.Builder setContents(ContainerComponentManipulator<?> component) {
        return new SetContainerContents.Builder(component);
    }

    public static class Builder extends LootItemConditionalFunction.Builder<SetContainerContents.Builder> {

        private final ImmutableList.Builder<LootPoolEntryContainer> entries = ImmutableList.builder();
        private final ContainerComponentManipulator<?> component;

        public Builder(ContainerComponentManipulator<?> component) {
            this.component = component;
        }

        @Override
        protected SetContainerContents.Builder getThis() {
            return this;
        }

        public SetContainerContents.Builder withEntry(LootPoolEntryContainer.Builder<?> entry) {
            this.entries.add(entry.build());
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new SetContainerContents(this.getConditions(), this.component, this.entries.build());
        }
    }
}

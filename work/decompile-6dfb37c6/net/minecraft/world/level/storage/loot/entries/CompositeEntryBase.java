package net.minecraft.world.level.storage.loot.entries;

import com.mojang.datafixers.Products.P2;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public abstract class CompositeEntryBase extends LootPoolEntryContainer {

    public static final ProblemReporter.Problem NO_CHILDREN_PROBLEM = new ProblemReporter.Problem() {
        @Override
        public String description() {
            return "Empty children list";
        }
    };
    protected final List<LootPoolEntryContainer> children;
    private final ComposableEntryContainer composedChildren;

    protected CompositeEntryBase(List<LootPoolEntryContainer> children, List<LootItemCondition> conditions) {
        super(conditions);
        this.children = children;
        this.composedChildren = this.compose(children);
    }

    @Override
    public void validate(ValidationContext context) {
        super.validate(context);
        if (this.children.isEmpty()) {
            context.reportProblem(CompositeEntryBase.NO_CHILDREN_PROBLEM);
        }

        for (int i = 0; i < this.children.size(); ++i) {
            ((LootPoolEntryContainer) this.children.get(i)).validate(context.forChild(new ProblemReporter.IndexedFieldPathElement("children", i)));
        }

    }

    protected abstract ComposableEntryContainer compose(List<? extends ComposableEntryContainer> entries);

    @Override
    public final boolean expand(LootContext context, Consumer<LootPoolEntry> output) {
        return !this.canRun(context) ? false : this.composedChildren.expand(context, output);
    }

    public static <T extends CompositeEntryBase> MapCodec<T> createCodec(CompositeEntryBase.CompositeEntryConstructor<T> constructor) {
        return RecordCodecBuilder.mapCodec((instance) -> {
            P2 p2 = instance.group(LootPoolEntries.CODEC.listOf().optionalFieldOf("children", List.of()).forGetter((compositeentrybase) -> {
                return compositeentrybase.children;
            })).and(commonFields(instance).t1());

            Objects.requireNonNull(constructor);
            return p2.apply(instance, constructor::create);
        });
    }

    @FunctionalInterface
    public interface CompositeEntryConstructor<T extends CompositeEntryBase> {

        T create(List<LootPoolEntryContainer> children, List<LootItemCondition> conditions);
    }
}

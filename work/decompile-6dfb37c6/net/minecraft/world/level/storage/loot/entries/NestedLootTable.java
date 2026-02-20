package net.minecraft.world.level.storage.loot.entries;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class NestedLootTable extends LootPoolSingletonContainer {

    public static final MapCodec<NestedLootTable> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.either(LootTable.KEY_CODEC, LootTable.DIRECT_CODEC).fieldOf("value").forGetter((nestedloottable) -> {
            return nestedloottable.contents;
        })).and(singletonFields(instance)).apply(instance, NestedLootTable::new);
    });
    public static final ProblemReporter.PathElement INLINE_LOOT_TABLE_PATH_ELEMENT = new ProblemReporter.PathElement() {
        @Override
        public String get() {
            return "->{inline}";
        }
    };
    private final Either<ResourceKey<LootTable>, LootTable> contents;

    private NestedLootTable(Either<ResourceKey<LootTable>, LootTable> contents, int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.contents = contents;
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.LOOT_TABLE;
    }

    @Override
    public void createItemStack(Consumer<ItemStack> output, LootContext context) {
        ((LootTable) this.contents.map((resourcekey) -> {
            return (LootTable) context.getResolver().get(resourcekey).map(Holder::value).orElse(LootTable.EMPTY);
        }, (loottable) -> {
            return loottable;
        })).getRandomItemsRaw(context, output);
    }

    @Override
    public void validate(ValidationContext context) {
        Optional<ResourceKey<LootTable>> optional = this.contents.left();

        if (optional.isPresent()) {
            ResourceKey<LootTable> resourcekey = (ResourceKey) optional.get();

            if (!context.allowsReferences()) {
                context.reportProblem(new ValidationContext.ReferenceNotAllowedProblem(resourcekey));
                return;
            }

            if (context.hasVisitedElement(resourcekey)) {
                context.reportProblem(new ValidationContext.RecursiveReferenceProblem(resourcekey));
                return;
            }
        }

        super.validate(context);
        this.contents.ifLeft((resourcekey1) -> {
            context.resolver().get(resourcekey1).ifPresentOrElse((holder_reference) -> {
                ((LootTable) holder_reference.value()).validate(context.enterElement(new ProblemReporter.ElementReferencePathElement(resourcekey1), resourcekey1));
            }, () -> {
                context.reportProblem(new ValidationContext.MissingReferenceProblem(resourcekey1));
            });
        }).ifRight((loottable) -> {
            loottable.validate(context.forChild(NestedLootTable.INLINE_LOOT_TABLE_PATH_ELEMENT));
        });
    }

    public static LootPoolSingletonContainer.Builder<?> lootTableReference(ResourceKey<LootTable> name) {
        return simpleBuilder((i, j, list, list1) -> {
            return new NestedLootTable(Either.left(name), i, j, list, list1);
        });
    }

    public static LootPoolSingletonContainer.Builder<?> inlineLootTable(LootTable table) {
        return simpleBuilder((i, j, list, list1) -> {
            return new NestedLootTable(Either.right(table), i, j, list, list1);
        });
    }
}

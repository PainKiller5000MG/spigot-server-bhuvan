package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class FilteredFunction extends LootItemConditionalFunction {

    public static final MapCodec<FilteredFunction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(instance.group(ItemPredicate.CODEC.fieldOf("item_filter").forGetter((filteredfunction) -> {
            return filteredfunction.filter;
        }), LootItemFunctions.ROOT_CODEC.optionalFieldOf("on_pass").forGetter((filteredfunction) -> {
            return filteredfunction.onPass;
        }), LootItemFunctions.ROOT_CODEC.optionalFieldOf("on_fail").forGetter((filteredfunction) -> {
            return filteredfunction.onFail;
        }))).apply(instance, FilteredFunction::new);
    });
    private final ItemPredicate filter;
    private final Optional<LootItemFunction> onPass;
    private final Optional<LootItemFunction> onFail;

    private FilteredFunction(List<LootItemCondition> predicates, ItemPredicate filter, Optional<LootItemFunction> onPass, Optional<LootItemFunction> onFail) {
        super(predicates);
        this.filter = filter;
        this.onPass = onPass;
        this.onFail = onFail;
    }

    @Override
    public LootItemFunctionType<FilteredFunction> getType() {
        return LootItemFunctions.FILTERED;
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext context) {
        Optional<LootItemFunction> optional = this.filter.test(itemStack) ? this.onPass : this.onFail;

        return optional.isPresent() ? (ItemStack) ((LootItemFunction) optional.get()).apply(itemStack, context) : itemStack;
    }

    @Override
    public void validate(ValidationContext context) {
        super.validate(context);
        this.onPass.ifPresent((lootitemfunction) -> {
            lootitemfunction.validate(context.forChild(new ProblemReporter.FieldPathElement("on_pass")));
        });
        this.onFail.ifPresent((lootitemfunction) -> {
            lootitemfunction.validate(context.forChild(new ProblemReporter.FieldPathElement("on_fail")));
        });
    }

    public static FilteredFunction.Builder filtered(ItemPredicate predicate) {
        return new FilteredFunction.Builder(predicate);
    }

    public static class Builder extends LootItemConditionalFunction.Builder<FilteredFunction.Builder> {

        private final ItemPredicate itemPredicate;
        private Optional<LootItemFunction> onPass = Optional.empty();
        private Optional<LootItemFunction> onFail = Optional.empty();

        private Builder(ItemPredicate itemPredicate) {
            this.itemPredicate = itemPredicate;
        }

        @Override
        protected FilteredFunction.Builder getThis() {
            return this;
        }

        public FilteredFunction.Builder onPass(Optional<LootItemFunction> onPass) {
            this.onPass = onPass;
            return this;
        }

        public FilteredFunction.Builder onFail(Optional<LootItemFunction> onFail) {
            this.onFail = onFail;
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new FilteredFunction(this.getConditions(), this.itemPredicate, this.onPass, this.onFail);
        }
    }
}

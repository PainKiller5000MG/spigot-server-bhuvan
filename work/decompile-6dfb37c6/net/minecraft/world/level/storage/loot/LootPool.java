package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntry;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.ConditionUserBuilder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;
import org.apache.commons.lang3.mutable.MutableInt;

public class LootPool {

    public static final Codec<LootPool> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(LootPoolEntries.CODEC.listOf().fieldOf("entries").forGetter((lootpool) -> {
            return lootpool.entries;
        }), LootItemCondition.DIRECT_CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter((lootpool) -> {
            return lootpool.conditions;
        }), LootItemFunctions.ROOT_CODEC.listOf().optionalFieldOf("functions", List.of()).forGetter((lootpool) -> {
            return lootpool.functions;
        }), NumberProviders.CODEC.fieldOf("rolls").forGetter((lootpool) -> {
            return lootpool.rolls;
        }), NumberProviders.CODEC.fieldOf("bonus_rolls").orElse(ConstantValue.exactly(0.0F)).forGetter((lootpool) -> {
            return lootpool.bonusRolls;
        })).apply(instance, LootPool::new);
    });
    private final List<LootPoolEntryContainer> entries;
    private final List<LootItemCondition> conditions;
    private final Predicate<LootContext> compositeCondition;
    private final List<LootItemFunction> functions;
    private final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;
    private final NumberProvider rolls;
    private final NumberProvider bonusRolls;

    private LootPool(List<LootPoolEntryContainer> entries, List<LootItemCondition> conditions, List<LootItemFunction> functions, NumberProvider rolls, NumberProvider bonusRolls) {
        this.entries = entries;
        this.conditions = conditions;
        this.compositeCondition = Util.allOf(conditions);
        this.functions = functions;
        this.compositeFunction = LootItemFunctions.compose(functions);
        this.rolls = rolls;
        this.bonusRolls = bonusRolls;
    }

    private void addRandomItem(Consumer<ItemStack> result, LootContext context) {
        RandomSource randomsource = context.getRandom();
        List<LootPoolEntry> list = Lists.newArrayList();
        MutableInt mutableint = new MutableInt();

        for (LootPoolEntryContainer lootpoolentrycontainer : this.entries) {
            lootpoolentrycontainer.expand(context, (lootpoolentry) -> {
                int i = lootpoolentry.getWeight(context.getLuck());

                if (i > 0) {
                    list.add(lootpoolentry);
                    mutableint.add(i);
                }

            });
        }

        int i = list.size();

        if (mutableint.intValue() != 0 && i != 0) {
            if (i == 1) {
                ((LootPoolEntry) list.get(0)).createItemStack(result, context);
            } else {
                int j = randomsource.nextInt(mutableint.intValue());

                for (LootPoolEntry lootpoolentry : list) {
                    j -= lootpoolentry.getWeight(context.getLuck());
                    if (j < 0) {
                        lootpoolentry.createItemStack(result, context);
                        return;
                    }
                }

            }
        }
    }

    public void addRandomItems(Consumer<ItemStack> result, LootContext context) {
        if (this.compositeCondition.test(context)) {
            Consumer<ItemStack> consumer1 = LootItemFunction.decorate(this.compositeFunction, result, context);
            int i = this.rolls.getInt(context) + Mth.floor(this.bonusRolls.getFloat(context) * context.getLuck());

            for (int j = 0; j < i; ++j) {
                this.addRandomItem(consumer1, context);
            }

        }
    }

    public void validate(ValidationContext output) {
        for (int i = 0; i < this.conditions.size(); ++i) {
            ((LootItemCondition) this.conditions.get(i)).validate(output.forChild(new ProblemReporter.IndexedFieldPathElement("conditions", i)));
        }

        for (int j = 0; j < this.functions.size(); ++j) {
            ((LootItemFunction) this.functions.get(j)).validate(output.forChild(new ProblemReporter.IndexedFieldPathElement("functions", j)));
        }

        for (int k = 0; k < this.entries.size(); ++k) {
            ((LootPoolEntryContainer) this.entries.get(k)).validate(output.forChild(new ProblemReporter.IndexedFieldPathElement("entries", k)));
        }

        this.rolls.validate(output.forChild(new ProblemReporter.FieldPathElement("rolls")));
        this.bonusRolls.validate(output.forChild(new ProblemReporter.FieldPathElement("bonus_rolls")));
    }

    public static LootPool.Builder lootPool() {
        return new LootPool.Builder();
    }

    public static class Builder implements FunctionUserBuilder<LootPool.Builder>, ConditionUserBuilder<LootPool.Builder> {

        private final ImmutableList.Builder<LootPoolEntryContainer> entries = ImmutableList.builder();
        private final ImmutableList.Builder<LootItemCondition> conditions = ImmutableList.builder();
        private final ImmutableList.Builder<LootItemFunction> functions = ImmutableList.builder();
        private NumberProvider rolls = ConstantValue.exactly(1.0F);
        private NumberProvider bonusRolls = ConstantValue.exactly(0.0F);

        public Builder() {}

        public LootPool.Builder setRolls(NumberProvider rolls) {
            this.rolls = rolls;
            return this;
        }

        @Override
        public LootPool.Builder unwrap() {
            return this;
        }

        public LootPool.Builder setBonusRolls(NumberProvider bonusRolls) {
            this.bonusRolls = bonusRolls;
            return this;
        }

        public LootPool.Builder add(LootPoolEntryContainer.Builder<?> entry) {
            this.entries.add(entry.build());
            return this;
        }

        @Override
        public LootPool.Builder when(LootItemCondition.Builder condition) {
            this.conditions.add(condition.build());
            return this;
        }

        @Override
        public LootPool.Builder apply(LootItemFunction.Builder function) {
            this.functions.add(function.build());
            return this;
        }

        public LootPool build() {
            return new LootPool(this.entries.build(), this.conditions.build(), this.functions.build(), this.rolls, this.bonusRolls);
        }
    }
}

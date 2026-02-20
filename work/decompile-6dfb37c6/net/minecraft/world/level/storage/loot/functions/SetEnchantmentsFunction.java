package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class SetEnchantmentsFunction extends LootItemConditionalFunction {

    public static final MapCodec<SetEnchantmentsFunction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(instance.group(Codec.unboundedMap(Enchantment.CODEC, NumberProviders.CODEC).optionalFieldOf("enchantments", Map.of()).forGetter((setenchantmentsfunction) -> {
            return setenchantmentsfunction.enchantments;
        }), Codec.BOOL.fieldOf("add").orElse(false).forGetter((setenchantmentsfunction) -> {
            return setenchantmentsfunction.add;
        }))).apply(instance, SetEnchantmentsFunction::new);
    });
    private final Map<Holder<Enchantment>, NumberProvider> enchantments;
    private final boolean add;

    private SetEnchantmentsFunction(List<LootItemCondition> predicates, Map<Holder<Enchantment>, NumberProvider> enchantments, boolean add) {
        super(predicates);
        this.enchantments = Map.copyOf(enchantments);
        this.add = add;
    }

    @Override
    public LootItemFunctionType<SetEnchantmentsFunction> getType() {
        return LootItemFunctions.SET_ENCHANTMENTS;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return (Set) this.enchantments.values().stream().flatMap((numberprovider) -> {
            return numberprovider.getReferencedContextParams().stream();
        }).collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext context) {
        if (itemStack.is(Items.BOOK)) {
            itemStack = itemStack.transmuteCopy(Items.ENCHANTED_BOOK);
        }

        EnchantmentHelper.updateEnchantments(itemStack, (itemenchantments_mutable) -> {
            if (this.add) {
                this.enchantments.forEach((holder, numberprovider) -> {
                    itemenchantments_mutable.set(holder, Mth.clamp(itemenchantments_mutable.getLevel(holder) + numberprovider.getInt(context), 0, 255));
                });
            } else {
                this.enchantments.forEach((holder, numberprovider) -> {
                    itemenchantments_mutable.set(holder, Mth.clamp(numberprovider.getInt(context), 0, 255));
                });
            }

        });
        return itemStack;
    }

    public static class Builder extends LootItemConditionalFunction.Builder<SetEnchantmentsFunction.Builder> {

        private final ImmutableMap.Builder<Holder<Enchantment>, NumberProvider> enchantments;
        private final boolean add;

        public Builder() {
            this(false);
        }

        public Builder(boolean add) {
            this.enchantments = ImmutableMap.builder();
            this.add = add;
        }

        @Override
        protected SetEnchantmentsFunction.Builder getThis() {
            return this;
        }

        public SetEnchantmentsFunction.Builder withEnchantment(Holder<Enchantment> enchantment, NumberProvider levelProvider) {
            this.enchantments.put(enchantment, levelProvider);
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new SetEnchantmentsFunction(this.getConditions(), this.enchantments.build(), this.add);
        }
    }
}

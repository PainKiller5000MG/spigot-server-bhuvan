package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class EnchantedCountIncreaseFunction extends LootItemConditionalFunction {

    public static final int NO_LIMIT = 0;
    public static final MapCodec<EnchantedCountIncreaseFunction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(instance.group(Enchantment.CODEC.fieldOf("enchantment").forGetter((enchantedcountincreasefunction) -> {
            return enchantedcountincreasefunction.enchantment;
        }), NumberProviders.CODEC.fieldOf("count").forGetter((enchantedcountincreasefunction) -> {
            return enchantedcountincreasefunction.value;
        }), Codec.INT.optionalFieldOf("limit", 0).forGetter((enchantedcountincreasefunction) -> {
            return enchantedcountincreasefunction.limit;
        }))).apply(instance, EnchantedCountIncreaseFunction::new);
    });
    private final Holder<Enchantment> enchantment;
    private final NumberProvider value;
    private final int limit;

    private EnchantedCountIncreaseFunction(List<LootItemCondition> predicates, Holder<Enchantment> enchantment, NumberProvider value, int limit) {
        super(predicates);
        this.enchantment = enchantment;
        this.value = value;
        this.limit = limit;
    }

    @Override
    public LootItemFunctionType<EnchantedCountIncreaseFunction> getType() {
        return LootItemFunctions.ENCHANTED_COUNT_INCREASE;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Sets.union(ImmutableSet.of(LootContextParams.ATTACKING_ENTITY), this.value.getReferencedContextParams());
    }

    private boolean hasLimit() {
        return this.limit > 0;
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext context) {
        Entity entity = (Entity) context.getOptionalParameter(LootContextParams.ATTACKING_ENTITY);

        if (entity instanceof LivingEntity livingentity) {
            int i = EnchantmentHelper.getEnchantmentLevel(this.enchantment, livingentity);

            if (i == 0) {
                return itemStack;
            }

            float f = (float) i * this.value.getFloat(context);

            itemStack.grow(Math.round(f));
            if (this.hasLimit()) {
                itemStack.limitSize(this.limit);
            }
        }

        return itemStack;
    }

    public static EnchantedCountIncreaseFunction.Builder lootingMultiplier(HolderLookup.Provider registries, NumberProvider count) {
        HolderLookup.RegistryLookup<Enchantment> holderlookup_registrylookup = registries.lookupOrThrow(Registries.ENCHANTMENT);

        return new EnchantedCountIncreaseFunction.Builder(holderlookup_registrylookup.getOrThrow(Enchantments.LOOTING), count);
    }

    public static class Builder extends LootItemConditionalFunction.Builder<EnchantedCountIncreaseFunction.Builder> {

        private final Holder<Enchantment> enchantment;
        private final NumberProvider count;
        private int limit = 0;

        public Builder(Holder<Enchantment> enchantment, NumberProvider count) {
            this.enchantment = enchantment;
            this.count = count;
        }

        @Override
        protected EnchantedCountIncreaseFunction.Builder getThis() {
            return this;
        }

        public EnchantedCountIncreaseFunction.Builder setLimit(int limit) {
            this.limit = limit;
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new EnchantedCountIncreaseFunction(this.getConditions(), this.enchantment, this.count, this.limit);
        }
    }
}

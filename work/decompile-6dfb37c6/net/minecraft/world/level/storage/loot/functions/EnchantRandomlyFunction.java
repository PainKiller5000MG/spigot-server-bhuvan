package net.minecraft.world.level.storage.loot.functions;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.slf4j.Logger;

public class EnchantRandomlyFunction extends LootItemConditionalFunction {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<EnchantRandomlyFunction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(instance.group(RegistryCodecs.homogeneousList(Registries.ENCHANTMENT).optionalFieldOf("options").forGetter((enchantrandomlyfunction) -> {
            return enchantrandomlyfunction.options;
        }), Codec.BOOL.optionalFieldOf("only_compatible", true).forGetter((enchantrandomlyfunction) -> {
            return enchantrandomlyfunction.onlyCompatible;
        }))).apply(instance, EnchantRandomlyFunction::new);
    });
    private final Optional<HolderSet<Enchantment>> options;
    private final boolean onlyCompatible;

    private EnchantRandomlyFunction(List<LootItemCondition> predicates, Optional<HolderSet<Enchantment>> options, boolean onlyCompatible) {
        super(predicates);
        this.options = options;
        this.onlyCompatible = onlyCompatible;
    }

    @Override
    public LootItemFunctionType<EnchantRandomlyFunction> getType() {
        return LootItemFunctions.ENCHANT_RANDOMLY;
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext context) {
        RandomSource randomsource = context.getRandom();
        boolean flag = itemStack.is(Items.BOOK);
        boolean flag1 = !flag && this.onlyCompatible;
        Stream<Holder<Enchantment>> stream = ((Stream) this.options.map(HolderSet::stream).orElseGet(() -> {
            return context.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().map(Function.identity());
        })).filter((holder) -> {
            return !flag1 || ((Enchantment) holder.value()).canEnchant(itemStack);
        });
        List<Holder<Enchantment>> list = stream.toList();
        Optional<Holder<Enchantment>> optional = Util.<Holder<Enchantment>>getRandomSafe(list, randomsource);

        if (optional.isEmpty()) {
            EnchantRandomlyFunction.LOGGER.warn("Couldn't find a compatible enchantment for {}", itemStack);
            return itemStack;
        } else {
            return enchantItem(itemStack, (Holder) optional.get(), randomsource);
        }
    }

    private static ItemStack enchantItem(ItemStack itemStack, Holder<Enchantment> enchantment, RandomSource random) {
        int i = Mth.nextInt(random, ((Enchantment) enchantment.value()).getMinLevel(), ((Enchantment) enchantment.value()).getMaxLevel());

        if (itemStack.is(Items.BOOK)) {
            itemStack = new ItemStack(Items.ENCHANTED_BOOK);
        }

        itemStack.enchant(enchantment, i);
        return itemStack;
    }

    public static EnchantRandomlyFunction.Builder randomEnchantment() {
        return new EnchantRandomlyFunction.Builder();
    }

    public static EnchantRandomlyFunction.Builder randomApplicableEnchantment(HolderLookup.Provider registries) {
        return randomEnchantment().withOneOf(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(EnchantmentTags.ON_RANDOM_LOOT));
    }

    public static class Builder extends LootItemConditionalFunction.Builder<EnchantRandomlyFunction.Builder> {

        private Optional<HolderSet<Enchantment>> options = Optional.empty();
        private boolean onlyCompatible = true;

        public Builder() {}

        @Override
        protected EnchantRandomlyFunction.Builder getThis() {
            return this;
        }

        public EnchantRandomlyFunction.Builder withEnchantment(Holder<Enchantment> enchantment) {
            this.options = Optional.of(HolderSet.direct(enchantment));
            return this;
        }

        public EnchantRandomlyFunction.Builder withOneOf(HolderSet<Enchantment> enchantments) {
            this.options = Optional.of(enchantments);
            return this;
        }

        public EnchantRandomlyFunction.Builder allowingIncompatibleEnchantments() {
            this.onlyCompatible = false;
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new EnchantRandomlyFunction(this.getConditions(), this.options, this.onlyCompatible);
        }
    }
}

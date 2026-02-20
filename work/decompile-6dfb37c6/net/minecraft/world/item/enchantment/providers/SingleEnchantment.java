package net.minecraft.world.item.enchantment.providers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public record SingleEnchantment(Holder<Enchantment> enchantment, IntProvider level) implements EnchantmentProvider {

    public static final MapCodec<SingleEnchantment> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Enchantment.CODEC.fieldOf("enchantment").forGetter(SingleEnchantment::enchantment), IntProvider.CODEC.fieldOf("level").forGetter(SingleEnchantment::level)).apply(instance, SingleEnchantment::new);
    });

    @Override
    public void enchant(ItemStack item, ItemEnchantments.Mutable itemEnchantments, RandomSource random, DifficultyInstance difficulty) {
        itemEnchantments.upgrade(this.enchantment, Mth.clamp(this.level.sample(random), ((Enchantment) this.enchantment.value()).getMinLevel(), ((Enchantment) this.enchantment.value()).getMaxLevel()));
    }

    @Override
    public MapCodec<SingleEnchantment> codec() {
        return SingleEnchantment.CODEC;
    }
}

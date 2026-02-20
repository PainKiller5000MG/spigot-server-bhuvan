package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public record BonusLevelTableCondition(Holder<Enchantment> enchantment, List<Float> values) implements LootItemCondition {

    public static final MapCodec<BonusLevelTableCondition> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Enchantment.CODEC.fieldOf("enchantment").forGetter(BonusLevelTableCondition::enchantment), ExtraCodecs.nonEmptyList(Codec.FLOAT.listOf()).fieldOf("chances").forGetter(BonusLevelTableCondition::values)).apply(instance, BonusLevelTableCondition::new);
    });

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.TABLE_BONUS;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.TOOL);
    }

    public boolean test(LootContext context) {
        ItemStack itemstack = (ItemStack) context.getOptionalParameter(LootContextParams.TOOL);
        int i = itemstack != null ? EnchantmentHelper.getItemEnchantmentLevel(this.enchantment, itemstack) : 0;
        float f = (Float) this.values.get(Math.min(i, this.values.size() - 1));

        return context.getRandom().nextFloat() < f;
    }

    public static LootItemCondition.Builder bonusLevelFlatChance(Holder<Enchantment> enchantment, float... chances) {
        List<Float> list = new ArrayList(chances.length);

        for (float f : chances) {
            list.add(f);
        }

        return () -> {
            return new BonusLevelTableCondition(enchantment, list);
        };
    }
}

package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public record LootItemRandomChanceWithEnchantedBonusCondition(float unenchantedChance, LevelBasedValue enchantedChance, Holder<Enchantment> enchantment) implements LootItemCondition {

    public static final MapCodec<LootItemRandomChanceWithEnchantedBonusCondition> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.floatRange(0.0F, 1.0F).fieldOf("unenchanted_chance").forGetter(LootItemRandomChanceWithEnchantedBonusCondition::unenchantedChance), LevelBasedValue.CODEC.fieldOf("enchanted_chance").forGetter(LootItemRandomChanceWithEnchantedBonusCondition::enchantedChance), Enchantment.CODEC.fieldOf("enchantment").forGetter(LootItemRandomChanceWithEnchantedBonusCondition::enchantment)).apply(instance, LootItemRandomChanceWithEnchantedBonusCondition::new);
    });

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.RANDOM_CHANCE_WITH_ENCHANTED_BONUS;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.ATTACKING_ENTITY);
    }

    public boolean test(LootContext context) {
        Entity entity = (Entity) context.getOptionalParameter(LootContextParams.ATTACKING_ENTITY);
        int i;

        if (entity instanceof LivingEntity livingentity) {
            i = EnchantmentHelper.getEnchantmentLevel(this.enchantment, livingentity);
        } else {
            i = 0;
        }

        int j = i;
        float f = j > 0 ? this.enchantedChance.calculate(j) : this.unenchantedChance;

        return context.getRandom().nextFloat() < f;
    }

    public static LootItemCondition.Builder randomChanceAndLootingBoost(HolderLookup.Provider registries, float chance, float perEnchantmentLevel) {
        HolderLookup.RegistryLookup<Enchantment> holderlookup_registrylookup = registries.lookupOrThrow(Registries.ENCHANTMENT);

        return () -> {
            return new LootItemRandomChanceWithEnchantedBonusCondition(chance, new LevelBasedValue.Linear(chance + perEnchantmentLevel, perEnchantmentLevel), holderlookup_registrylookup.getOrThrow(Enchantments.LOOTING));
        };
    }
}

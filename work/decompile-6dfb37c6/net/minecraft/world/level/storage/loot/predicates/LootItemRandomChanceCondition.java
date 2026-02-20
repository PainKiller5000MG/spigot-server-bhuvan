package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public record LootItemRandomChanceCondition(NumberProvider chance) implements LootItemCondition {

    public static final MapCodec<LootItemRandomChanceCondition> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(NumberProviders.CODEC.fieldOf("chance").forGetter(LootItemRandomChanceCondition::chance)).apply(instance, LootItemRandomChanceCondition::new);
    });

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.RANDOM_CHANCE;
    }

    public boolean test(LootContext context) {
        float f = this.chance.getFloat(context);

        return context.getRandom().nextFloat() < f;
    }

    public static LootItemCondition.Builder randomChance(float probability) {
        return () -> {
            return new LootItemRandomChanceCondition(ConstantValue.exactly(probability));
        };
    }

    public static LootItemCondition.Builder randomChance(NumberProvider probability) {
        return () -> {
            return new LootItemRandomChanceCondition(probability);
        };
    }
}

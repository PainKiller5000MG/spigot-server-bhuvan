package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class ApplyExplosionDecay extends LootItemConditionalFunction {

    public static final MapCodec<ApplyExplosionDecay> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).apply(instance, ApplyExplosionDecay::new);
    });

    private ApplyExplosionDecay(List<LootItemCondition> predicates) {
        super(predicates);
    }

    @Override
    public LootItemFunctionType<ApplyExplosionDecay> getType() {
        return LootItemFunctions.EXPLOSION_DECAY;
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext context) {
        Float ofloat = (Float) context.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS);

        if (ofloat != null) {
            RandomSource randomsource = context.getRandom();
            float f = 1.0F / ofloat;
            int i = itemStack.getCount();
            int j = 0;

            for (int k = 0; k < i; ++k) {
                if (randomsource.nextFloat() <= f) {
                    ++j;
                }
            }

            itemStack.setCount(j);
        }

        return itemStack;
    }

    public static LootItemConditionalFunction.Builder<?> explosionDecay() {
        return simpleBuilder(ApplyExplosionDecay::new);
    }
}

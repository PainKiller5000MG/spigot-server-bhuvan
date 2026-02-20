package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.IntRange;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class LimitCount extends LootItemConditionalFunction {

    public static final MapCodec<LimitCount> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(IntRange.CODEC.fieldOf("limit").forGetter((limitcount) -> {
            return limitcount.limiter;
        })).apply(instance, LimitCount::new);
    });
    private final IntRange limiter;

    private LimitCount(List<LootItemCondition> predicates, IntRange limiter) {
        super(predicates);
        this.limiter = limiter;
    }

    @Override
    public LootItemFunctionType<LimitCount> getType() {
        return LootItemFunctions.LIMIT_COUNT;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return this.limiter.getReferencedContextParams();
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext context) {
        int i = this.limiter.clamp(context, itemStack.getCount());

        itemStack.setCount(i);
        return itemStack;
    }

    public static LootItemConditionalFunction.Builder<?> limitCount(IntRange limiter) {
        return simpleBuilder((list) -> {
            return new LimitCount(list, limiter);
        });
    }
}

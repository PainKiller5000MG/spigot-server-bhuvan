package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetComponentsFunction extends LootItemConditionalFunction {

    public static final MapCodec<SetComponentsFunction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(DataComponentPatch.CODEC.fieldOf("components").forGetter((setcomponentsfunction) -> {
            return setcomponentsfunction.components;
        })).apply(instance, SetComponentsFunction::new);
    });
    private final DataComponentPatch components;

    private SetComponentsFunction(List<LootItemCondition> predicates, DataComponentPatch components) {
        super(predicates);
        this.components = components;
    }

    @Override
    public LootItemFunctionType<SetComponentsFunction> getType() {
        return LootItemFunctions.SET_COMPONENTS;
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext context) {
        itemStack.applyComponentsAndValidate(this.components);
        return itemStack;
    }

    public static <T> LootItemConditionalFunction.Builder<?> setComponent(DataComponentType<T> type, T value) {
        return simpleBuilder((list) -> {
            return new SetComponentsFunction(list, DataComponentPatch.builder().set(type, value).build());
        });
    }
}

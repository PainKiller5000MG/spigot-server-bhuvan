package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetCustomDataFunction extends LootItemConditionalFunction {

    public static final MapCodec<SetCustomDataFunction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(TagParser.LENIENT_CODEC.fieldOf("tag").forGetter((setcustomdatafunction) -> {
            return setcustomdatafunction.tag;
        })).apply(instance, SetCustomDataFunction::new);
    });
    private final CompoundTag tag;

    private SetCustomDataFunction(List<LootItemCondition> predicates, CompoundTag tag) {
        super(predicates);
        this.tag = tag;
    }

    @Override
    public LootItemFunctionType<SetCustomDataFunction> getType() {
        return LootItemFunctions.SET_CUSTOM_DATA;
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext context) {
        CustomData.update(DataComponents.CUSTOM_DATA, itemStack, (compoundtag) -> {
            compoundtag.merge(this.tag);
        });
        return itemStack;
    }

    /** @deprecated */
    @Deprecated
    public static LootItemConditionalFunction.Builder<?> setCustomData(CompoundTag value) {
        return simpleBuilder((list) -> {
            return new SetCustomDataFunction(list, value);
        });
    }
}

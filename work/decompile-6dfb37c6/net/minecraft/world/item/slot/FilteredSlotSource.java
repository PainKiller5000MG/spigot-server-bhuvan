package net.minecraft.world.item.slot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.criterion.ItemPredicate;

public class FilteredSlotSource extends TransformedSlotSource {

    public static final MapCodec<FilteredSlotSource> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(ItemPredicate.CODEC.fieldOf("item_filter").forGetter((filteredslotsource) -> {
            return filteredslotsource.filter;
        })).apply(instance, FilteredSlotSource::new);
    });
    private final ItemPredicate filter;

    private FilteredSlotSource(SlotSource slotSource, ItemPredicate filter) {
        super(slotSource);
        this.filter = filter;
    }

    @Override
    public MapCodec<FilteredSlotSource> codec() {
        return FilteredSlotSource.MAP_CODEC;
    }

    @Override
    protected SlotCollection transform(SlotCollection slots) {
        return slots.filter(this.filter);
    }
}

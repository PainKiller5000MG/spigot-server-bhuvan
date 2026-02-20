package net.minecraft.world.item.slot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

public class LimitSlotSource extends TransformedSlotSource {

    public static final MapCodec<LimitSlotSource> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(ExtraCodecs.POSITIVE_INT.fieldOf("limit").forGetter((limitslotsource) -> {
            return limitslotsource.limit;
        })).apply(instance, LimitSlotSource::new);
    });
    private final int limit;

    private LimitSlotSource(SlotSource slotSource, int limit) {
        super(slotSource);
        this.limit = limit;
    }

    @Override
    public MapCodec<LimitSlotSource> codec() {
        return LimitSlotSource.MAP_CODEC;
    }

    @Override
    protected SlotCollection transform(SlotCollection slots) {
        return slots.limit(this.limit);
    }
}

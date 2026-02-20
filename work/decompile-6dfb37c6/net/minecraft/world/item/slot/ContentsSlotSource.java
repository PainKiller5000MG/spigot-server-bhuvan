package net.minecraft.world.item.slot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.world.level.storage.loot.ContainerComponentManipulator;
import net.minecraft.world.level.storage.loot.ContainerComponentManipulators;

public class ContentsSlotSource extends TransformedSlotSource {

    public static final MapCodec<ContentsSlotSource> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(ContainerComponentManipulators.CODEC.fieldOf("component").forGetter((contentsslotsource) -> {
            return contentsslotsource.component;
        })).apply(instance, ContentsSlotSource::new);
    });
    private final ContainerComponentManipulator<?> component;

    private ContentsSlotSource(SlotSource slotSource, ContainerComponentManipulator<?> component) {
        super(slotSource);
        this.component = component;
    }

    @Override
    public MapCodec<ContentsSlotSource> codec() {
        return ContentsSlotSource.MAP_CODEC;
    }

    @Override
    protected SlotCollection transform(SlotCollection slots) {
        ContainerComponentManipulator containercomponentmanipulator = this.component;

        Objects.requireNonNull(this.component);
        return slots.flatMap(containercomponentmanipulator::getSlots);
    }
}

package net.minecraft.world.item.slot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.storage.loot.LootContext;

public interface SlotSources {

    Codec<SlotSource> TYPED_CODEC = BuiltInRegistries.SLOT_SOURCE_TYPE.byNameCodec().dispatch(SlotSource::codec, (mapcodec) -> {
        return mapcodec;
    });
    Codec<SlotSource> CODEC = Codec.lazyInitialized(() -> {
        return Codec.withAlternative(SlotSources.TYPED_CODEC, GroupSlotSource.INLINE_CODEC);
    });

    static MapCodec<? extends SlotSource> bootstrap(Registry<MapCodec<? extends SlotSource>> registry) {
        Registry.register(registry, "group", GroupSlotSource.MAP_CODEC);
        Registry.register(registry, "filtered", FilteredSlotSource.MAP_CODEC);
        Registry.register(registry, "limit_slots", LimitSlotSource.MAP_CODEC);
        Registry.register(registry, "slot_range", RangeSlotSource.MAP_CODEC);
        Registry.register(registry, "contents", ContentsSlotSource.MAP_CODEC);
        return (MapCodec) Registry.register(registry, "empty", EmptySlotSource.MAP_CODEC);
    }

    static Function<LootContext, SlotCollection> group(Collection<? extends SlotSource> list) {
        List<SlotSource> list1 = List.copyOf(list);
        Function function;

        switch (list1.size()) {
            case 0:
                function = (lootcontext) -> {
                    return SlotCollection.EMPTY;
                };
                break;
            case 1:
                SlotSource slotsource = (SlotSource) list1.getFirst();

                Objects.requireNonNull(slotsource);
                function = slotsource::provide;
                break;
            case 2:
                SlotSource slotsource1 = (SlotSource) list1.get(0);
                SlotSource slotsource2 = (SlotSource) list1.get(1);

                function = (lootcontext) -> {
                    return SlotCollection.concat(slotsource1.provide(lootcontext), slotsource2.provide(lootcontext));
                };
                break;
            default:
                function = (lootcontext) -> {
                    List<SlotCollection> list2 = new ArrayList();

                    for (SlotSource slotsource3 : list1) {
                        list2.add(slotsource3.provide(lootcontext));
                    }

                    return SlotCollection.concat(list2);
                };
        }

        return function;
    }
}

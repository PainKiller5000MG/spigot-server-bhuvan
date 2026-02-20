package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Map;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SlotProvider;
import net.minecraft.world.inventory.SlotRange;
import net.minecraft.world.inventory.SlotRanges;

public record SlotsPredicate(Map<SlotRange, ItemPredicate> slots) {

    public static final Codec<SlotsPredicate> CODEC = Codec.unboundedMap(SlotRanges.CODEC, ItemPredicate.CODEC).xmap(SlotsPredicate::new, SlotsPredicate::slots);

    public boolean matches(SlotProvider slotProvider) {
        for (Map.Entry<SlotRange, ItemPredicate> map_entry : this.slots.entrySet()) {
            if (!matchSlots(slotProvider, (ItemPredicate) map_entry.getValue(), ((SlotRange) map_entry.getKey()).slots())) {
                return false;
            }
        }

        return true;
    }

    private static boolean matchSlots(SlotProvider slotProvider, ItemPredicate test, IntList slots) {
        for (int i = 0; i < slots.size(); ++i) {
            int j = slots.getInt(i);
            SlotAccess slotaccess = slotProvider.getSlot(j);

            if (slotaccess != null && test.test(slotaccess.get())) {
                return true;
            }
        }

        return false;
    }
}

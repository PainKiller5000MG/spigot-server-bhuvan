package net.minecraft.world.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

public class ItemCombinerMenuSlotDefinition {

    private final List<ItemCombinerMenuSlotDefinition.SlotDefinition> slots;
    private final ItemCombinerMenuSlotDefinition.SlotDefinition resultSlot;

    private ItemCombinerMenuSlotDefinition(List<ItemCombinerMenuSlotDefinition.SlotDefinition> inputSlots, ItemCombinerMenuSlotDefinition.SlotDefinition resultSlot) {
        if (!inputSlots.isEmpty() && !resultSlot.equals(ItemCombinerMenuSlotDefinition.SlotDefinition.EMPTY)) {
            this.slots = inputSlots;
            this.resultSlot = resultSlot;
        } else {
            throw new IllegalArgumentException("Need to define both inputSlots and resultSlot");
        }
    }

    public static ItemCombinerMenuSlotDefinition.Builder create() {
        return new ItemCombinerMenuSlotDefinition.Builder();
    }

    public ItemCombinerMenuSlotDefinition.SlotDefinition getSlot(int index) {
        return (ItemCombinerMenuSlotDefinition.SlotDefinition) this.slots.get(index);
    }

    public ItemCombinerMenuSlotDefinition.SlotDefinition getResultSlot() {
        return this.resultSlot;
    }

    public List<ItemCombinerMenuSlotDefinition.SlotDefinition> getSlots() {
        return this.slots;
    }

    public int getNumOfInputSlots() {
        return this.slots.size();
    }

    public int getResultSlotIndex() {
        return this.getNumOfInputSlots();
    }

    public static class Builder {

        private final List<ItemCombinerMenuSlotDefinition.SlotDefinition> inputSlots = new ArrayList();
        private ItemCombinerMenuSlotDefinition.SlotDefinition resultSlot;

        public Builder() {
            this.resultSlot = ItemCombinerMenuSlotDefinition.SlotDefinition.EMPTY;
        }

        public ItemCombinerMenuSlotDefinition.Builder withSlot(int slotIndex, int xPlacement, int yPlacement, Predicate<ItemStack> mayPlace) {
            this.inputSlots.add(new ItemCombinerMenuSlotDefinition.SlotDefinition(slotIndex, xPlacement, yPlacement, mayPlace));
            return this;
        }

        public ItemCombinerMenuSlotDefinition.Builder withResultSlot(int slotIndex, int xPlacement, int yPlacement) {
            this.resultSlot = new ItemCombinerMenuSlotDefinition.SlotDefinition(slotIndex, xPlacement, yPlacement, (itemstack) -> {
                return false;
            });
            return this;
        }

        public ItemCombinerMenuSlotDefinition build() {
            int i = this.inputSlots.size();

            for (int j = 0; j < i; ++j) {
                ItemCombinerMenuSlotDefinition.SlotDefinition itemcombinermenuslotdefinition_slotdefinition = (ItemCombinerMenuSlotDefinition.SlotDefinition) this.inputSlots.get(j);

                if (itemcombinermenuslotdefinition_slotdefinition.slotIndex != j) {
                    throw new IllegalArgumentException("Expected input slots to have continous indexes");
                }
            }

            if (this.resultSlot.slotIndex != i) {
                throw new IllegalArgumentException("Expected result slot index to follow last input slot");
            } else {
                return new ItemCombinerMenuSlotDefinition(this.inputSlots, this.resultSlot);
            }
        }
    }

    public static record SlotDefinition(int slotIndex, int x, int y, Predicate<ItemStack> mayPlace) {

        private static final ItemCombinerMenuSlotDefinition.SlotDefinition EMPTY = new ItemCombinerMenuSlotDefinition.SlotDefinition(0, 0, 0, (itemstack) -> {
            return true;
        });
    }
}

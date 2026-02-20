package net.minecraft.world;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ContainerHelper {

    public static final String TAG_ITEMS = "Items";

    public ContainerHelper() {}

    public static ItemStack removeItem(List<ItemStack> itemStacks, int slot, int count) {
        return slot >= 0 && slot < itemStacks.size() && !((ItemStack) itemStacks.get(slot)).isEmpty() && count > 0 ? ((ItemStack) itemStacks.get(slot)).split(count) : ItemStack.EMPTY;
    }

    public static ItemStack takeItem(List<ItemStack> itemStacks, int slot) {
        return slot >= 0 && slot < itemStacks.size() ? (ItemStack) itemStacks.set(slot, ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    public static void saveAllItems(ValueOutput output, NonNullList<ItemStack> itemStacks) {
        saveAllItems(output, itemStacks, true);
    }

    public static void saveAllItems(ValueOutput output, NonNullList<ItemStack> itemStacks, boolean alsoWhenEmpty) {
        ValueOutput.TypedOutputList<ItemStackWithSlot> valueoutput_typedoutputlist = output.<ItemStackWithSlot>list("Items", ItemStackWithSlot.CODEC);

        for (int i = 0; i < itemStacks.size(); ++i) {
            ItemStack itemstack = itemStacks.get(i);

            if (!itemstack.isEmpty()) {
                valueoutput_typedoutputlist.add(new ItemStackWithSlot(i, itemstack));
            }
        }

        if (valueoutput_typedoutputlist.isEmpty() && !alsoWhenEmpty) {
            output.discard("Items");
        }

    }

    public static void loadAllItems(ValueInput input, NonNullList<ItemStack> itemStacks) {
        for (ItemStackWithSlot itemstackwithslot : input.listOrEmpty("Items", ItemStackWithSlot.CODEC)) {
            if (itemstackwithslot.isValidInContainer(itemStacks.size())) {
                itemStacks.set(itemstackwithslot.slot(), itemstackwithslot.stack());
            }
        }

    }

    public static int clearOrCountMatchingItems(Container container, Predicate<ItemStack> predicate, int amountToRemove, boolean countingOnly) {
        int j = 0;

        for (int k = 0; k < container.getContainerSize(); ++k) {
            ItemStack itemstack = container.getItem(k);
            int l = clearOrCountMatchingItems(itemstack, predicate, amountToRemove - j, countingOnly);

            if (l > 0 && !countingOnly && itemstack.isEmpty()) {
                container.setItem(k, ItemStack.EMPTY);
            }

            j += l;
        }

        return j;
    }

    public static int clearOrCountMatchingItems(ItemStack itemStack, Predicate<ItemStack> predicate, int amountToRemove, boolean countingOnly) {
        if (!itemStack.isEmpty() && predicate.test(itemStack)) {
            if (countingOnly) {
                return itemStack.getCount();
            } else {
                int j = amountToRemove < 0 ? itemStack.getCount() : Math.min(amountToRemove, itemStack.getCount());

                itemStack.shrink(j);
                return j;
            }
        } else {
            return 0;
        }
    }
}

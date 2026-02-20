package net.minecraft.world;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class SimpleContainer implements Container, StackedContentsCompatible {

    private final int size;
    public final NonNullList<ItemStack> items;
    private @Nullable List<ContainerListener> listeners;

    public SimpleContainer(int size) {
        this.size = size;
        this.items = NonNullList.<ItemStack>withSize(size, ItemStack.EMPTY);
    }

    public SimpleContainer(ItemStack... itemstacks) {
        this.size = itemstacks.length;
        this.items = NonNullList.<ItemStack>of(ItemStack.EMPTY, itemstacks);
    }

    public void addListener(ContainerListener listener) {
        if (this.listeners == null) {
            this.listeners = Lists.newArrayList();
        }

        this.listeners.add(listener);
    }

    public void removeListener(ContainerListener listener) {
        if (this.listeners != null) {
            this.listeners.remove(listener);
        }

    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < this.items.size() ? (ItemStack) this.items.get(slot) : ItemStack.EMPTY;
    }

    public List<ItemStack> removeAllItems() {
        List<ItemStack> list = (List) this.items.stream().filter((itemstack) -> {
            return !itemstack.isEmpty();
        }).collect(Collectors.toList());

        this.clearContent();
        return list;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack itemstack = ContainerHelper.removeItem(this.items, slot, count);

        if (!itemstack.isEmpty()) {
            this.setChanged();
        }

        return itemstack;
    }

    public ItemStack removeItemType(Item itemType, int count) {
        ItemStack itemstack = new ItemStack(itemType, 0);

        for (int j = this.size - 1; j >= 0; --j) {
            ItemStack itemstack1 = this.getItem(j);

            if (itemstack1.getItem().equals(itemType)) {
                int k = count - itemstack.getCount();
                ItemStack itemstack2 = itemstack1.split(k);

                itemstack.grow(itemstack2.getCount());
                if (itemstack.getCount() == count) {
                    break;
                }
            }
        }

        if (!itemstack.isEmpty()) {
            this.setChanged();
        }

        return itemstack;
    }

    public ItemStack addItem(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack1 = itemStack.copy();

            this.moveItemToOccupiedSlotsWithSameType(itemstack1);
            if (itemstack1.isEmpty()) {
                return ItemStack.EMPTY;
            } else {
                this.moveItemToEmptySlots(itemstack1);
                return itemstack1.isEmpty() ? ItemStack.EMPTY : itemstack1;
            }
        }
    }

    public boolean canAddItem(ItemStack itemStack) {
        boolean flag = false;

        for (ItemStack itemstack1 : this.items) {
            if (itemstack1.isEmpty() || ItemStack.isSameItemSameComponents(itemstack1, itemStack) && itemstack1.getCount() < itemstack1.getMaxStackSize()) {
                flag = true;
                break;
            }
        }

        return flag;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack itemstack = this.items.get(slot);

        if (itemstack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            this.items.set(slot, ItemStack.EMPTY);
            return itemstack;
        }
    }

    @Override
    public void setItem(int slot, ItemStack itemStack) {
        this.items.set(slot, itemStack);
        itemStack.limitSize(this.getMaxStackSize(itemStack));
        this.setChanged();
    }

    @Override
    public int getContainerSize() {
        return this.size;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemstack : this.items) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void setChanged() {
        if (this.listeners != null) {
            for (ContainerListener containerlistener : this.listeners) {
                containerlistener.containerChanged(this);
            }
        }

    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        this.items.clear();
        this.setChanged();
    }

    @Override
    public void fillStackedContents(StackedItemContents contents) {
        for (ItemStack itemstack : this.items) {
            contents.accountStack(itemstack);
        }

    }

    public String toString() {
        return ((List) this.items.stream().filter((itemstack) -> {
            return !itemstack.isEmpty();
        }).collect(Collectors.toList())).toString();
    }

    private void moveItemToEmptySlots(ItemStack sourceStack) {
        for (int i = 0; i < this.size; ++i) {
            ItemStack itemstack1 = this.getItem(i);

            if (itemstack1.isEmpty()) {
                this.setItem(i, sourceStack.copyAndClear());
                return;
            }
        }

    }

    private void moveItemToOccupiedSlotsWithSameType(ItemStack sourceStack) {
        for (int i = 0; i < this.size; ++i) {
            ItemStack itemstack1 = this.getItem(i);

            if (ItemStack.isSameItemSameComponents(itemstack1, sourceStack)) {
                this.moveItemsBetweenStacks(sourceStack, itemstack1);
                if (sourceStack.isEmpty()) {
                    return;
                }
            }
        }

    }

    private void moveItemsBetweenStacks(ItemStack sourceStack, ItemStack targetStack) {
        int i = this.getMaxStackSize(targetStack);
        int j = Math.min(sourceStack.getCount(), i - targetStack.getCount());

        if (j > 0) {
            targetStack.grow(j);
            sourceStack.shrink(j);
            this.setChanged();
        }

    }

    public void fromItemList(ValueInput.TypedInputList<ItemStack> items) {
        this.clearContent();

        for (ItemStack itemstack : items) {
            this.addItem(itemstack);
        }

    }

    public void storeAsItemList(ValueOutput.TypedOutputList<ItemStack> output) {
        for (int i = 0; i < this.getContainerSize(); ++i) {
            ItemStack itemstack = this.getItem(i);

            if (!itemstack.isEmpty()) {
                output.add(itemstack);
            }
        }

    }

    public NonNullList<ItemStack> getItems() {
        return this.items;
    }
}

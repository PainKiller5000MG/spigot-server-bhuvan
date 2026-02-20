package net.minecraft.world.inventory;

import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class Slot {

    public final int slot;
    public final Container container;
    public int index;
    public final int x;
    public final int y;

    public Slot(Container container, int slot, int x, int y) {
        this.container = container;
        this.slot = slot;
        this.x = x;
        this.y = y;
    }

    public void onQuickCraft(ItemStack picked, ItemStack original) {
        int i = original.getCount() - picked.getCount();

        if (i > 0) {
            this.onQuickCraft(original, i);
        }

    }

    protected void onQuickCraft(ItemStack picked, int count) {}

    protected void onSwapCraft(int count) {}

    protected void checkTakeAchievements(ItemStack carried) {}

    public void onTake(Player player, ItemStack carried) {
        this.setChanged();
    }

    public boolean mayPlace(ItemStack itemStack) {
        return true;
    }

    public ItemStack getItem() {
        return this.container.getItem(this.slot);
    }

    public boolean hasItem() {
        return !this.getItem().isEmpty();
    }

    public void setByPlayer(ItemStack itemStack) {
        this.setByPlayer(itemStack, this.getItem());
    }

    public void setByPlayer(ItemStack itemStack, ItemStack previous) {
        this.set(itemStack);
    }

    public void set(ItemStack itemStack) {
        this.container.setItem(this.slot, itemStack);
        this.setChanged();
    }

    public void setChanged() {
        this.container.setChanged();
    }

    public int getMaxStackSize() {
        return this.container.getMaxStackSize();
    }

    public int getMaxStackSize(ItemStack itemStack) {
        return Math.min(this.getMaxStackSize(), itemStack.getMaxStackSize());
    }

    public @Nullable Identifier getNoItemIcon() {
        return null;
    }

    public ItemStack remove(int amount) {
        return this.container.removeItem(this.slot, amount);
    }

    public boolean mayPickup(Player player) {
        return true;
    }

    public boolean isActive() {
        return true;
    }

    public Optional<ItemStack> tryRemove(int amount, int maxAmount, Player player) {
        if (!this.mayPickup(player)) {
            return Optional.empty();
        } else if (!this.allowModification(player) && maxAmount < this.getItem().getCount()) {
            return Optional.empty();
        } else {
            amount = Math.min(amount, maxAmount);
            ItemStack itemstack = this.remove(amount);

            if (itemstack.isEmpty()) {
                return Optional.empty();
            } else {
                if (this.getItem().isEmpty()) {
                    this.setByPlayer(ItemStack.EMPTY, itemstack);
                }

                return Optional.of(itemstack);
            }
        }
    }

    public ItemStack safeTake(int amount, int maxAmount, Player player) {
        Optional<ItemStack> optional = this.tryRemove(amount, maxAmount, player);

        optional.ifPresent((itemstack) -> {
            this.onTake(player, itemstack);
        });
        return (ItemStack) optional.orElse(ItemStack.EMPTY);
    }

    public ItemStack safeInsert(ItemStack stack) {
        return this.safeInsert(stack, stack.getCount());
    }

    public ItemStack safeInsert(ItemStack inputStack, int inputAmount) {
        if (!inputStack.isEmpty() && this.mayPlace(inputStack)) {
            ItemStack itemstack1 = this.getItem();
            int j = Math.min(Math.min(inputAmount, inputStack.getCount()), this.getMaxStackSize(inputStack) - itemstack1.getCount());

            if (j <= 0) {
                return inputStack;
            } else {
                if (itemstack1.isEmpty()) {
                    this.setByPlayer(inputStack.split(j));
                } else if (ItemStack.isSameItemSameComponents(itemstack1, inputStack)) {
                    inputStack.shrink(j);
                    itemstack1.grow(j);
                    this.setByPlayer(itemstack1);
                }

                return inputStack;
            }
        } else {
            return inputStack;
        }
    }

    public boolean allowModification(Player player) {
        return this.mayPickup(player) && this.mayPlace(this.getItem());
    }

    public int getContainerSlot() {
        return this.slot;
    }

    public boolean isHighlightable() {
        return true;
    }

    public boolean isFake() {
        return false;
    }
}

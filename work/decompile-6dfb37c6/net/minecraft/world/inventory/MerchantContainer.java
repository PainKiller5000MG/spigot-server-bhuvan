package net.minecraft.world.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

public class MerchantContainer implements Container {

    private final Merchant merchant;
    private final NonNullList<ItemStack> itemStacks;
    private @Nullable MerchantOffer activeOffer;
    public int selectionHint;
    private int futureXp;

    public MerchantContainer(Merchant villager) {
        this.itemStacks = NonNullList.<ItemStack>withSize(3, ItemStack.EMPTY);
        this.merchant = villager;
    }

    @Override
    public int getContainerSize() {
        return this.itemStacks.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemstack : this.itemStacks) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.itemStacks.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack itemstack = this.itemStacks.get(slot);

        if (slot == 2 && !itemstack.isEmpty()) {
            return ContainerHelper.removeItem(this.itemStacks, slot, itemstack.getCount());
        } else {
            ItemStack itemstack1 = ContainerHelper.removeItem(this.itemStacks, slot, count);

            if (!itemstack1.isEmpty() && this.isPaymentSlot(slot)) {
                this.updateSellItem();
            }

            return itemstack1;
        }
    }

    private boolean isPaymentSlot(int slot) {
        return slot == 0 || slot == 1;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.itemStacks, slot);
    }

    @Override
    public void setItem(int slot, ItemStack itemStack) {
        this.itemStacks.set(slot, itemStack);
        itemStack.limitSize(this.getMaxStackSize(itemStack));
        if (this.isPaymentSlot(slot)) {
            this.updateSellItem();
        }

    }

    @Override
    public boolean stillValid(Player player) {
        return this.merchant.getTradingPlayer() == player;
    }

    @Override
    public void setChanged() {
        this.updateSellItem();
    }

    public void updateSellItem() {
        this.activeOffer = null;
        ItemStack itemstack;
        ItemStack itemstack1;

        if (((ItemStack) this.itemStacks.get(0)).isEmpty()) {
            itemstack = this.itemStacks.get(1);
            itemstack1 = ItemStack.EMPTY;
        } else {
            itemstack = this.itemStacks.get(0);
            itemstack1 = this.itemStacks.get(1);
        }

        if (itemstack.isEmpty()) {
            this.setItem(2, ItemStack.EMPTY);
            this.futureXp = 0;
        } else {
            MerchantOffers merchantoffers = this.merchant.getOffers();

            if (!merchantoffers.isEmpty()) {
                MerchantOffer merchantoffer = merchantoffers.getRecipeFor(itemstack, itemstack1, this.selectionHint);

                if (merchantoffer == null || merchantoffer.isOutOfStock()) {
                    this.activeOffer = merchantoffer;
                    merchantoffer = merchantoffers.getRecipeFor(itemstack1, itemstack, this.selectionHint);
                }

                if (merchantoffer != null && !merchantoffer.isOutOfStock()) {
                    this.activeOffer = merchantoffer;
                    this.setItem(2, merchantoffer.assemble());
                    this.futureXp = merchantoffer.getXp();
                } else {
                    this.setItem(2, ItemStack.EMPTY);
                    this.futureXp = 0;
                }
            }

            this.merchant.notifyTradeUpdated(this.getItem(2));
        }
    }

    public @Nullable MerchantOffer getActiveOffer() {
        return this.activeOffer;
    }

    public void setSelectionHint(int selectionHint) {
        this.selectionHint = selectionHint;
        this.updateSellItem();
    }

    @Override
    public void clearContent() {
        this.itemStacks.clear();
    }

    public int getFutureXp() {
        return this.futureXp;
    }
}

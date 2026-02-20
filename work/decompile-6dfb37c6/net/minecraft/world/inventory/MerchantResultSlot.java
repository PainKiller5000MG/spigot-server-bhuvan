package net.minecraft.world.inventory;

import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;

public class MerchantResultSlot extends Slot {

    private final MerchantContainer slots;
    private final Player player;
    private int removeCount;
    private final Merchant merchant;

    public MerchantResultSlot(Player player, Merchant merchant, MerchantContainer slots, int id, int x, int y) {
        super(slots, id, x, y);
        this.player = player;
        this.merchant = merchant;
        this.slots = slots;
    }

    @Override
    public boolean mayPlace(ItemStack itemStack) {
        return false;
    }

    @Override
    public ItemStack remove(int amount) {
        if (this.hasItem()) {
            this.removeCount += Math.min(amount, this.getItem().getCount());
        }

        return super.remove(amount);
    }

    @Override
    protected void onQuickCraft(ItemStack picked, int count) {
        this.removeCount += count;
        this.checkTakeAchievements(picked);
    }

    @Override
    protected void checkTakeAchievements(ItemStack carried) {
        carried.onCraftedBy(this.player, this.removeCount);
        this.removeCount = 0;
    }

    @Override
    public void onTake(Player player, ItemStack carried) {
        this.checkTakeAchievements(carried);
        MerchantOffer merchantoffer = this.slots.getActiveOffer();

        if (merchantoffer != null) {
            ItemStack itemstack1 = this.slots.getItem(0);
            ItemStack itemstack2 = this.slots.getItem(1);

            if (merchantoffer.take(itemstack1, itemstack2) || merchantoffer.take(itemstack2, itemstack1)) {
                this.merchant.notifyTrade(merchantoffer);
                player.awardStat(Stats.TRADED_WITH_VILLAGER);
                this.slots.setItem(0, itemstack1);
                this.slots.setItem(1, itemstack2);
            }

            this.merchant.overrideXp(this.merchant.getVillagerXp() + merchantoffer.getXp());
        }

    }
}

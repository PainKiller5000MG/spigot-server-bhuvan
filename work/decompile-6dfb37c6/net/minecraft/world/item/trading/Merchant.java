package net.minecraft.world.item.trading;

import java.util.OptionalInt;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public interface Merchant {

    void setTradingPlayer(@Nullable Player player);

    @Nullable
    Player getTradingPlayer();

    MerchantOffers getOffers();

    void overrideOffers(MerchantOffers offers);

    void notifyTrade(MerchantOffer offer);

    void notifyTradeUpdated(ItemStack itemStack);

    int getVillagerXp();

    void overrideXp(int xp);

    boolean showProgressBar();

    SoundEvent getNotifyTradeSound();

    default boolean canRestock() {
        return false;
    }

    default void openTradingScreen(Player player, Component title, int level) {
        OptionalInt optionalint = player.openMenu(new SimpleMenuProvider((j, inventory, player1) -> {
            return new MerchantMenu(j, inventory, this);
        }, title));

        if (optionalint.isPresent()) {
            MerchantOffers merchantoffers = this.getOffers();

            if (!merchantoffers.isEmpty()) {
                player.sendMerchantOffers(optionalint.getAsInt(), merchantoffers, level, this.getVillagerXp(), this.showProgressBar(), this.canRestock());
            }
        }

    }

    boolean isClientSide();

    boolean stillValid(Player player);
}

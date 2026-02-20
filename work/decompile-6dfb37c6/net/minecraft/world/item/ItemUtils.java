package net.minecraft.world.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class ItemUtils {

    public ItemUtils() {}

    public static InteractionResult startUsingInstantly(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    public static ItemStack createFilledResult(ItemStack itemStack, Player player, ItemStack newItemStack, boolean limitCreativeStackSize) {
        boolean flag1 = player.hasInfiniteMaterials();

        if (limitCreativeStackSize && flag1) {
            if (!player.getInventory().contains(newItemStack)) {
                player.getInventory().add(newItemStack);
            }

            return itemStack;
        } else {
            itemStack.consume(1, player);
            if (itemStack.isEmpty()) {
                return newItemStack;
            } else {
                if (!player.getInventory().add(newItemStack)) {
                    player.drop(newItemStack, false);
                }

                return itemStack;
            }
        }
    }

    public static ItemStack createFilledResult(ItemStack itemStack, Player player, ItemStack newItemStack) {
        return createFilledResult(itemStack, player, newItemStack, true);
    }

    public static void onContainerDestroyed(ItemEntity container, Iterable<ItemStack> contents) {
        Level level = container.level();

        if (!level.isClientSide()) {
            contents.forEach((itemstack) -> {
                level.addFreshEntity(new ItemEntity(level, container.getX(), container.getY(), container.getZ(), itemstack));
            });
        }
    }
}
